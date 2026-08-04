package com.example.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

class PaymentServiceTest {

    private static final String TEST_EMAIL = "logintest@example.com";
    private static final String TEST_KEY_ID = "rzp_test_abcdefghijklmn";
    private static final String TEST_KEY_SECRET = "test-secret";

    private final OrderService orderService = mock(OrderService.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CartService cartService = mock(CartService.class);
    private final RazorpayClient razorpayClient = mock(RazorpayClient.class);

    private final User user = new User();

    private PaymentService service;

    @BeforeEach
    void setUp() {
        user.setId(1);
        user.setEmail(TEST_EMAIL);
        service = new PaymentService(razorpayClient, TEST_KEY_ID, TEST_KEY_SECRET,
                orderService, orderRepository, paymentRepository, userRepository, cartService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private Order orderFor(User owner, OrderStatus status) {
        Order order = new Order();
        order.setId("app-order-123");
        order.setUser(owner);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("1299.00"));
        return order;
    }

    private Payment paymentFor(Order order, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setRazorpayOrderId("order_1");
        payment.setPaymentStatus(status);
        return payment;
    }

    private String validSignature(String orderId, String paymentId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(TEST_KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void toPaise_convertsRupeesToPaise() {
        assertThat(service.toPaise(new BigDecimal("1299.00"))).isEqualTo(129900L);
        assertThat(service.toPaise(new BigDecimal("5056.00"))).isEqualTo(505600L);
        assertThat(service.toPaise(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(service.toPaise(new BigDecimal("100.99"))).isEqualTo(10099L);
    }

    @Test
    void createOrder_missingConfiguration_throwsPaymentProcessing() {
        PaymentService unconfigured = new PaymentService(razorpayClient, "", "",
                orderService, orderRepository, paymentRepository, userRepository, cartService);
        assertThatThrownBy(unconfigured::createOrder)
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void createOrder_emptyCart_throwsBadRequest() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(orderRepository.findByUser_IdAndStatus(user.getId(), OrderStatus.PENDING))
                .thenReturn(Optional.empty());
        when(orderService.createOrderFromCart(user))
                .thenThrow(new BadRequestException(
                        "Your cart is empty. Add items before proceeding to payment."));

        assertThatThrownBy(service::createOrder)
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void createOrder_persistsPaymentAndReturnsResponse() throws RazorpayException {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = orderFor(user, OrderStatus.PENDING);
        when(orderRepository.findByUser_IdAndStatus(user.getId(), OrderStatus.PENDING))
                .thenReturn(Optional.empty());
        when(orderService.createOrderFromCart(user)).thenReturn(order);
        when(paymentRepository.findFirstByOrder_IdAndPaymentStatus(order.getId(), PaymentStatus.CREATED))
                .thenReturn(Optional.empty());

        JSONObject razorpayJson = new JSONObject();
        razorpayJson.put("id", "order_MockOrder123456");
        razorpayJson.put("amount", 129900);
        razorpayJson.put("currency", "INR");
        OrderClient orderClient = mock(OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", orderClient);
        when(orderClient.create(any(JSONObject.class)))
                .thenReturn(new com.razorpay.Order(razorpayJson));

        CreateOrderResponse response = service.createOrder();

        assertThat(response.razorpayOrderId()).isEqualTo("order_MockOrder123456");
        assertThat(response.applicationOrderId()).isEqualTo("app-order-123");
        assertThat(response.amount()).isEqualTo(129900);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.keyId()).isEqualTo(TEST_KEY_ID);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void createOrder_reusesExistingCreatedPayment() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = orderFor(user, OrderStatus.PENDING);
        when(orderRepository.findByUser_IdAndStatus(user.getId(), OrderStatus.PENDING))
                .thenReturn(Optional.of(order));

        Payment existing = new Payment();
        existing.setOrder(order);
        existing.setRazorpayOrderId("order_existing");
        existing.setAmount(order.getTotalAmount());
        existing.setCurrency("INR");
        existing.setPaymentStatus(PaymentStatus.CREATED);
        when(paymentRepository.findFirstByOrder_IdAndPaymentStatus(order.getId(), PaymentStatus.CREATED))
                .thenReturn(Optional.of(existing));

        CreateOrderResponse response = service.createOrder();

        assertThat(response.razorpayOrderId()).isEqualTo("order_existing");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void verify_missingConfiguration_throwsPaymentProcessing() {
        PaymentService unconfigured = new PaymentService(razorpayClient, "", "",
                orderService, orderRepository, paymentRepository, userRepository, cartService);
        assertThatThrownBy(() -> unconfigured.verify(
                new VerifyPaymentRequest("pay_abc", "order_1", "sig")))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void verify_unknownRazorpayOrder_throwsBadRequest() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayOrderId("order_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(
                new VerifyPaymentRequest("pay_x", "order_unknown", "sig")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void verify_otherUsersPayment_throwsBadRequest() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        User other = new User();
        other.setId(99);
        Payment payment = paymentFor(orderFor(other, OrderStatus.PENDING), PaymentStatus.CREATED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.verify(
                new VerifyPaymentRequest("pay_abc", "order_1", validSignature("order_1", "pay_abc"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No payment record found");
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(cartService, never()).clearCart();
    }

    @Test
    void verify_validSignature_marksSuccessAndClearsCart() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = orderFor(user, OrderStatus.PENDING);
        Payment payment = paymentFor(order, PaymentStatus.CREATED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByRazorpayPaymentId("pay_abc")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        VerifyPaymentResponse response = service.verify(
                new VerifyPaymentRequest("pay_abc", "order_1", validSignature("order_1", "pay_abc")));

        assertThat(response.success()).isTrue();
        assertThat(response.message()).contains("verified");
        assertThat(response.orderId()).isEqualTo("app-order-123");
        assertThat(response.paymentId()).isEqualTo("pay_abc");
        assertThat(response.paymentStatus()).isEqualTo("SUCCESS");
        assertThat(response.orderStatus()).isEqualTo("SUCCESS");

        assertThat(payment.getRazorpayPaymentId()).isEqualTo("pay_abc");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SUCCESS);
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
        verify(cartService).clearCart();
    }

    @Test
    void verify_invalidSignature_marksFailedAndDoesNotComplete() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = orderFor(user, OrderStatus.PENDING);
        Payment payment = paymentFor(order, PaymentStatus.CREATED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByRazorpayPaymentId("pay_abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(
                new VerifyPaymentRequest("pay_abc", "order_1", "invalid-signature")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("signature");

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getRazorpayPaymentId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(paymentRepository).save(payment);
        verify(orderRepository, never()).save(any(Order.class));
        verify(cartService, never()).clearCart();
    }

    @Test
    void verify_alreadySuccess_samePaymentId_returnsIdempotentSuccess() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = orderFor(user, OrderStatus.SUCCESS);
        Payment payment = paymentFor(order, PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId("pay_abc");
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        VerifyPaymentResponse response = service.verify(
                new VerifyPaymentRequest("pay_abc", "order_1", "any-signature"));

        assertThat(response.success()).isTrue();
        assertThat(response.paymentStatus()).isEqualTo("SUCCESS");
        assertThat(response.orderStatus()).isEqualTo("SUCCESS");
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(cartService, never()).clearCart();
    }

    @Test
    void verify_alreadySuccess_differentPaymentId_throwsBadRequest() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Payment payment = paymentFor(orderFor(user, OrderStatus.SUCCESS), PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId("pay_abc");
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.verify(
                new VerifyPaymentRequest("pay_xyz", "order_1", "any-signature")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been paid");
        verify(cartService, never()).clearCart();
    }

    @Test
    void verify_duplicatePaymentIdOnDifferentPayment_throwsDuplicateResource() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Payment payment = paymentFor(orderFor(user, OrderStatus.PENDING), PaymentStatus.CREATED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        Payment other = new Payment();
        other.setId(99);
        other.setRazorpayPaymentId("pay_dup");
        when(paymentRepository.findByRazorpayPaymentId("pay_dup")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.verify(
                new VerifyPaymentRequest("pay_dup", "order_1", "sig")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(cartService, never()).clearCart();
    }
}
