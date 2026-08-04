package com.example.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.registration.dto.OrderItemResponse;
import com.example.registration.dto.OrderResponse;
import com.example.registration.entity.CartItem;
import com.example.registration.entity.Category;
import com.example.registration.entity.Order;
import com.example.registration.entity.OrderItem;
import com.example.registration.entity.OrderStatus;
import com.example.registration.entity.Product;
import com.example.registration.entity.ProductImage;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.repository.CartItemRepository;
import com.example.registration.repository.CategoryRepository;
import com.example.registration.repository.OrderRepository;
import com.example.registration.repository.ProductRepository;
import com.example.registration.repository.UserRepository;

class OrderServiceTest {

    private static final String TEST_EMAIL = "orders@example.com";

    private final CartItemRepository cartItemRepository = mock(CartItemRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);

    private final OrderService orderService =
            new OrderService(cartItemRepository, productRepository, orderRepository,
                    userRepository, categoryRepository);

    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(1);
        user.setEmail(TEST_EMAIL);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private CartItem cartItem(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private Product product(int id, String name, BigDecimal price, int stock) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "productId", id);
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        return product;
    }

    @Test
    void createOrderFromCart_emptyCart_throwsBadRequest() {
        User user = new User();
        user.setId(1);
        when(cartItemRepository.findAllByUser_IdOrderByIdAsc(1)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.createOrderFromCart(user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderFromCart_insufficientStock_throwsBadRequest() {
        User user = new User();
        user.setId(1);
        Product product = product(1, "Running Shoe", new BigDecimal("500.00"), 2);
        when(cartItemRepository.findAllByUser_IdOrderByIdAsc(1))
                .thenReturn(List.of(cartItem(product, 5)));

        assertThatThrownBy(() -> orderService.createOrderFromCart(user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient stock");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrderFromCart_createsOrderItemsAndDecrementsStock() {
        User user = new User();
        user.setId(1);
        Product p1 = product(1, "Aivin Wave Pro", new BigDecimal("1299.00"), 50);
        Product p2 = product(2, "Skechers", new BigDecimal("2499.50"), 8);
        when(cartItemRepository.findAllByUser_IdOrderByIdAsc(1))
                .thenReturn(List.of(cartItem(p1, 2), cartItem(p2, 1)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.createOrderFromCart(user);

        assertThat(order.getId()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("5097.50"));
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems().get(0).getTotalPrice()).isEqualByComparingTo(new BigDecimal("2598.00"));
        assertThat(order.getItems().get(1).getTotalPrice()).isEqualByComparingTo(new BigDecimal("2499.50"));
        assertThat(p1.getStock()).isEqualTo(48);
        assertThat(p2.getStock()).isEqualTo(7);
        verify(productRepository, times(2)).save(any(Product.class));
        verify(orderRepository).save(order);
    }

    @Test
    void getSuccessfulOrders_returnsOnlySuccessOrdersForCurrentUser() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));

        Order order = new Order();
        order.setId("order-app-1");
        order.setUser(user);
        order.setStatus(OrderStatus.SUCCESS);
        order.setTotalAmount(new BigDecimal("1299.00"));
        order.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 1, 12, 0));

        Product product = product(1, "Aivin Wave Pro", new BigDecimal("1299.00"), 50);
        product.setDescription("Comfortable running shoe");
        product.setCategoryId(2);
        ProductImage image = new ProductImage();
        image.setImageUrl("https://example.com/shoe.jpg");
        product.setImage(image);

        OrderItem orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setQuantity(1);
        orderItem.setPricePerUnit(new BigDecimal("1299.00"));
        orderItem.setTotalPrice(new BigDecimal("1299.00"));
        order.setItems(List.of(orderItem));

        Category category = new Category();
        category.setCategoryId(2);
        category.setCategoryName("Shoes");
        when(orderRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(1, OrderStatus.SUCCESS))
                .thenReturn(List.of(order));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(category));

        List<OrderResponse> orders = orderService.getSuccessfulOrders();

        assertThat(orders).hasSize(1);
        OrderResponse response = orders.get(0);
        assertThat(response.orderId()).isEqualTo("order-app-1");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.grandTotal()).isEqualByComparingTo(new BigDecimal("1299.00"));
        OrderItemResponse item = response.items().get(0);
        assertThat(item.name()).isEqualTo("Aivin Wave Pro");
        assertThat(item.category()).isEqualTo("Shoes");
        assertThat(item.imageUrl()).isEqualTo("https://example.com/shoe.jpg");
        assertThat(item.quantity()).isEqualTo(1);
        assertThat(item.totalPrice()).isEqualByComparingTo(new BigDecimal("1299.00"));
    }

    @Test
    void getSuccessfulOrders_ignoresPendingAndFailedOrders() {
        authenticateAs(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(user));
        when(orderRepository.findAllByUser_IdAndStatusOrderByCreatedAtDesc(1, OrderStatus.SUCCESS))
                .thenReturn(List.of());

        List<OrderResponse> orders = orderService.getSuccessfulOrders();

        assertThat(orders).isEmpty();
    }
}
