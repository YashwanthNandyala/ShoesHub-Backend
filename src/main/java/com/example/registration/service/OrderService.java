package com.example.registration.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.OrderItemResponse;
import com.example.registration.dto.OrderResponse;
import com.example.registration.entity.CartItem;
import com.example.registration.entity.Category;
import com.example.registration.entity.Order;
import com.example.registration.entity.OrderItem;
import com.example.registration.entity.OrderStatus;
import com.example.registration.entity.Product;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.repository.CartItemRepository;
import com.example.registration.repository.CategoryRepository;
import com.example.registration.repository.OrderRepository;
import com.example.registration.repository.ProductRepository;
import com.example.registration.repository.UserRepository;

@Service
public class OrderService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public OrderService(CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Order createOrderFromCart(User user) {
        List<CartItem> cartItems = cartItemRepository.findAllByUser_IdOrderByIdAsc(user.getId());
        if (cartItems.isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add items before proceeding to payment.");
        }

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();
            if (product.getStock() < quantity) {
                throw new BadRequestException(
                        "Insufficient stock for \"" + product.getName()
                                + "\". Available: " + product.getStock());
            }
            BigDecimal pricePerUnit = product.getPrice();
            BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(quantity));
            totalAmount = totalAmount.add(totalPrice);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(quantity);
            orderItem.setPricePerUnit(pricePerUnit);
            orderItem.setTotalPrice(totalPrice);
            orderItems.add(orderItem);

            product.setStock(product.getStock() - quantity);
            productRepository.save(product);
        }

        order.setTotalAmount(totalAmount);
        order.setItems(orderItems);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getSuccessfulOrders() {
        User user = currentUser();
        return orderRepository
                .findAllByUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), OrderStatus.SUCCESS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getCreatedAt(),
                items.size(),
                order.getTotalAmount(),
                items);
    }

    private OrderItemResponse toItemResponse(OrderItem orderItem) {
        Product product = orderItem.getProduct();
        String imageUrl = null;
        if (product.getImage() != null) {
            imageUrl = product.getImage().getImageUrl();
        }
        String categoryName = categoryRepository.findById(product.getCategoryId())
                .map(Category::getCategoryName)
                .orElse(null);
        return new OrderItemResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                imageUrl,
                categoryName,
                orderItem.getQuantity(),
                orderItem.getPricePerUnit(),
                orderItem.getTotalPrice());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
