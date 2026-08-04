package com.example.registration.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.CreateOrderResponse;
import com.example.registration.dto.VerifyPaymentRequest;
import com.example.registration.dto.VerifyPaymentResponse;
import com.example.registration.entity.Order;
import com.example.registration.entity.OrderStatus;
import com.example.registration.entity.Payment;
import com.example.registration.entity.PaymentStatus;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.DuplicateResourceException;
import com.example.registration.exception.PaymentProcessingException;
import com.example.registration.repository.OrderRepository;
import com.example.registration.repository.PaymentRepository;
import com.example.registration.repository.UserRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String CURRENCY_INR = "INR";
    private static final BigDecimal PAISE_PER_RUPEE = BigDecimal.valueOf(100);

    private final RazorpayClient razorpayClient;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final String keyId;
    private final String keySecret;

    public PaymentService(RazorpayClient razorpayClient,
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret,
            OrderService orderService,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            CartService cartService) {
        this.razorpayClient = razorpayClient;
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
    }

    @Transactional
    public CreateOrderResponse createOrder() {
        validateConfiguration();
        User user = currentUser();

        Order order = orderRepository.findByUser_IdAndStatus(user.getId(), OrderStatus.PENDING)
                .orElseGet(() -> orderService.createOrderFromCart(user));

        Payment existingPayment = paymentRepository
                .findFirstByOrder_IdAndPaymentStatus(order.getId(), PaymentStatus.CREATED)
                .orElse(null);
        if (existingPayment != null) {
            return toCreateOrderResponse(existingPayment);
        }

        long amountPaise = toPaise(order.getTotalAmount());
        if (amountPaise <= 0) {
            throw new BadRequestException("Your order total is invalid. Please review your cart before proceeding.");
        }

        com.razorpay.Order razorpayOrder;
        try {
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", CURRENCY_INR);
            request.put("receipt", generateReceipt());
            request.put("notes", new JSONObject().put("userEmail", user.getEmail()));

            razorpayOrder = razorpayClient.orders.create(request);
        } catch (RazorpayException ex) {
            log.error("Razorpay order creation failed", ex);
            throw new PaymentProcessingException("Payment could not be initiated. Please try again later.");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setRazorpayOrderId(razorpayOrder.get("id"));
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency(CURRENCY_INR);
        payment.setPaymentStatus(PaymentStatus.CREATED);
        paymentRepository.save(payment);

        return toCreateOrderResponse(payment);
    }

    @Transactional
    public VerifyPaymentResponse verify(VerifyPaymentRequest request) {
        validateConfiguration();
        Payment payment = paymentRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .orElseThrow(() -> new BadRequestException(
                        "No payment record found for the given Razorpay order id."));

        User user = currentUser();
        if (!payment.getOrder().getUser().getId().equals(user.getId())) {
            throw new BadRequestException("No payment record found for the given Razorpay order id.");
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            if (request.razorpayPaymentId().equals(payment.getRazorpayPaymentId())) {
                return toVerifyResponse(payment, "Payment already verified successfully.");
            }
            throw new BadRequestException("This order has already been paid.");
        }

        paymentRepository.findByRazorpayPaymentId(request.razorpayPaymentId())
                .filter(other -> !other.getId().equals(payment.getId()))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("This payment has already been processed.");
                });

        if (!isSignatureValid(payment, request)) {
            payment.setRazorpaySignature(request.razorpaySignature());
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new BadRequestException(
                    "Payment signature verification failed. The payment could not be confirmed.");
        }

        payment.setRazorpayPaymentId(request.razorpayPaymentId());
        payment.setRazorpaySignature(request.razorpaySignature());
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        Order order = payment.getOrder();
        order.setStatus(OrderStatus.SUCCESS);
        orderRepository.save(order);

        cartService.clearCart();

        return toVerifyResponse(payment, "Payment verified successfully");
    }

    private boolean isSignatureValid(Payment payment, VerifyPaymentRequest request) {
        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", payment.getRazorpayOrderId());
        attributes.put("razorpay_payment_id", request.razorpayPaymentId());
        attributes.put("razorpay_signature", request.razorpaySignature());
        try {
            return Utils.verifyPaymentSignature(attributes, keySecret);
        } catch (RazorpayException ex) {
            return false;
        }
    }

    private void validateConfiguration() {
        if (isBlank(keyId) || isBlank(keySecret)) {
            throw new PaymentProcessingException(
                    "Razorpay is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }
    }

    long toPaise(BigDecimal rupees) {
        return rupees.multiply(PAISE_PER_RUPEE).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private CreateOrderResponse toCreateOrderResponse(Payment payment) {
        return new CreateOrderResponse(
                payment.getRazorpayOrderId(),
                payment.getOrder().getId(),
                (int) toPaise(payment.getAmount()),
                payment.getCurrency(),
                keyId);
    }

    private VerifyPaymentResponse toVerifyResponse(Payment payment, String message) {
        return new VerifyPaymentResponse(
                payment.getPaymentStatus() == PaymentStatus.SUCCESS,
                message,
                payment.getOrder().getId(),
                payment.getRazorpayPaymentId(),
                payment.getPaymentStatus().name(),
                payment.getOrder().getStatus().name());
    }

    private String generateReceipt() {
        return "receipt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
