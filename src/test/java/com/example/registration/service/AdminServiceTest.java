package com.example.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.registration.dto.AdminDashboardResponse;
import com.example.registration.dto.CategoryRequest;
import com.example.registration.dto.CategoryResponse;
import com.example.registration.dto.OverallRevenueResponse;
import com.example.registration.dto.ProductRequest;
import com.example.registration.dto.ProductResponse;
import com.example.registration.dto.RevenueResponse;
import com.example.registration.dto.UpdateUserRoleRequest;
import com.example.registration.dto.UserAdminResponse;
import com.example.registration.entity.Category;
import com.example.registration.entity.Order;
import com.example.registration.entity.OrderStatus;
import com.example.registration.entity.Product;
import com.example.registration.entity.ProductImage;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.DuplicateResourceException;
import com.example.registration.exception.ResourceNotFoundException;
import com.example.registration.repository.CategoryRepository;
import com.example.registration.repository.OrderItemRepository;
import com.example.registration.repository.OrderRepository;
import com.example.registration.repository.PaymentRepository;
import com.example.registration.repository.PaymentRepository.RevenueRow;
import com.example.registration.repository.ProductImageRepository;
import com.example.registration.repository.ProductRepository;
import com.example.registration.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, productRepository, productImageRepository,
                categoryRepository, orderRepository, orderItemRepository, paymentRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@shop.com", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User adminUser() {
        User user = new User();
        user.setId(1);
        user.setFullName("Store Admin");
        user.setEmail("admin@shop.com");
        user.setPhone("9000000000");
        user.setRole(Role.ADMIN);
        return user;
    }

    private User customerUser() {
        User user = new User();
        user.setId(2);
        user.setFullName("Asha Rao");
        user.setEmail("asha@example.com");
        user.setPhone("8000000001");
        user.setRole(Role.CUSTOMER);
        return user;
    }

    @Test
    void getDashboardSummary_returnsCountsTotalsAndRecentOrders() {
        User customer = customerUser();
        Order order = new Order();
        order.setId("order-1");
        order.setUser(customer);
        order.setTotalAmount(new BigDecimal("199.00"));
        order.setStatus(OrderStatus.SUCCESS);
        order.setCreatedAt(LocalDateTime.of(2026, 8, 3, 10, 0));

        when(userRepository.count()).thenReturn(3L);
        when(productRepository.count()).thenReturn(5L);
        when(orderRepository.count()).thenReturn(2L);
        when(orderRepository.countByStatus(OrderStatus.SUCCESS)).thenReturn(1L);
        when(paymentRepository.sumSuccessfulRevenue()).thenReturn(new BigDecimal("199.00"));
        when(paymentRepository.sumSuccessfulRevenueBetween(any(), any()))
                .thenReturn(new BigDecimal("99.00"));
        when(orderRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(order));

        AdminDashboardResponse response = adminService.getDashboardSummary();

        assertThat(response.userCount()).isEqualTo(3);
        assertThat(response.productCount()).isEqualTo(5);
        assertThat(response.orderCount()).isEqualTo(2);
        assertThat(response.paidOrderCount()).isEqualTo(1);
        assertThat(response.totalRevenue()).isEqualByComparingTo("199.00");
        assertThat(response.todayRevenue()).isEqualByComparingTo("99.00");
        assertThat(response.recentOrders()).hasSize(1);
        assertThat(response.recentOrders().get(0).orderId()).isEqualTo("order-1");
        assertThat(response.recentOrders().get(0).customerName()).isEqualTo("Asha Rao");
    }

    @Test
    void getAllUsers_returnsUsersOrderedByCreation() {
        when(userRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(customerUser(), adminUser()));

        List<UserAdminResponse> response = adminService.getAllUsers();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).email()).isEqualTo("asha@example.com");
        assertThat(response.get(1).email()).isEqualTo("admin@shop.com");
    }

    @Test
    void updateUserRole_promotesCustomerToAdmin() {
        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser()));
        when(userRepository.findById(2)).thenReturn(Optional.of(customerUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAdminResponse response = adminService.updateUserRole(2,
                new UpdateUserRoleRequest(Role.ADMIN));

        assertThat(response.role()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void updateUserRole_sameRole_doesNotSave() {
        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser()));
        when(userRepository.findById(2)).thenReturn(Optional.of(customerUser()));

        UserAdminResponse response = adminService.updateUserRole(2,
                new UpdateUserRoleRequest(Role.CUSTOMER));

        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUserRole_selfChange_throwsBadRequest() {
        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser()));
        when(userRepository.findById(1)).thenReturn(Optional.of(adminUser()));

        assertThatThrownBy(() -> adminService.updateUserRole(1,
                new UpdateUserRoleRequest(Role.CUSTOMER)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You cannot change your own role");
    }

    @Test
    void updateUserRole_demotingLastAdmin_throwsBadRequest() {
        User otherAdmin = new User();
        otherAdmin.setId(2);
        otherAdmin.setEmail("other@example.com");
        otherAdmin.setRole(Role.ADMIN);

        when(userRepository.findByEmail("admin@shop.com")).thenReturn(Optional.of(adminUser()));
        when(userRepository.findById(2)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.updateUserRole(2,
                new UpdateUserRoleRequest(Role.CUSTOMER)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot demote the last admin account");
    }

    @Test
    void updateUserRole_unknownUser_throwsNotFound() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateUserRole(999,
                new UpdateUserRoleRequest(Role.CUSTOMER)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void createProduct_createsProductAndImage() {
        when(categoryRepository.existsById(1)).thenReturn(true);
        when(productRepository.findByNameIgnoreCase("Wireless Mouse")).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = adminService.createProduct(new ProductRequest(
                "Wireless Mouse", "A wireless mouse", new BigDecimal("799.00"),
                10, 1, "https://img.example.com/mouse.png"));

        assertThat(response.name()).isEqualTo("Wireless Mouse");
        assertThat(response.price()).isEqualByComparingTo("799.00");
        assertThat(response.imageUrl()).isEqualTo("https://img.example.com/mouse.png");
        verify(productImageRepository).save(any(ProductImage.class));
    }

    @Test
    void createProduct_duplicateName_throwsDuplicate() {
        Product existing = new Product();
        existing.setProductId(1);
        existing.setName("Wireless Mouse");

        when(categoryRepository.existsById(1)).thenReturn(true);
        when(productRepository.findByNameIgnoreCase("Wireless Mouse"))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> adminService.createProduct(new ProductRequest(
                "Wireless Mouse", "A wireless mouse", new BigDecimal("799.00"),
                10, 1, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("A product with this name already exists");
    }

    @Test
    void createProduct_unknownCategory_throwsBadRequest() {
        when(categoryRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> adminService.createProduct(new ProductRequest(
                "Keyboard", "A keyboard", new BigDecimal("1200.00"), 5, 999, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Category does not exist");
    }

    @Test
    void updateProduct_updatesFieldsAndImage() {
        Product product = new Product();
        product.setProductId(1);
        product.setName("Old Name");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(1);
        product.setCategoryId(1);

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(categoryRepository.existsById(2)).thenReturn(true);
        when(productRepository.findByNameIgnoreCase("New Name")).thenReturn(List.of());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = adminService.updateProduct(1, new ProductRequest(
                "New Name", "Updated", new BigDecimal("150.00"), 7, 2, "https://img.example.com/new.png"));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.stock()).isEqualTo(7);
        assertThat(response.categoryId()).isEqualTo(2);
        assertThat(response.imageUrl()).isEqualTo("https://img.example.com/new.png");
    }

    @Test
    void updateProduct_unknownProduct_throwsNotFound() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateProduct(99, new ProductRequest(
                "Keyboard", null, new BigDecimal("1200.00"), 5, 1, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void deleteProduct_deletesImageAndProduct() {
        Product product = new Product();
        product.setProductId(1);
        ProductImage image = new ProductImage();
        product.setImage(image);

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProduct_ProductId(1)).thenReturn(false);

        adminService.deleteProduct(1);

        verify(productImageRepository).delete(image);
        verify(productRepository).delete(product);
    }

    @Test
    void deleteProduct_productInOrders_throwsBadRequest() {
        Product product = new Product();
        product.setProductId(1);

        when(productRepository.findById(1)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProduct_ProductId(1)).thenReturn(true);

        assertThatThrownBy(() -> adminService.deleteProduct(1))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot delete this product because it is part of existing orders");
    }

    @Test
    void deleteProduct_unknownProduct_throwsNotFound() {
        when(productRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.deleteProduct(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void createCategory_createsCategory() {
        when(categoryRepository.existsByCategoryNameIgnoreCase("Electronics")).thenReturn(false);
        Category saved = new Category();
        saved.setCategoryId(1);
        saved.setCategoryName("Electronics");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = adminService.createCategory(new CategoryRequest(" Electronics "));

        assertThat(response.categoryId()).isEqualTo(1);
        assertThat(response.categoryName()).isEqualTo("Electronics");
    }

    @Test
    void createCategory_duplicateName_throwsDuplicate() {
        when(categoryRepository.existsByCategoryNameIgnoreCase("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> adminService.createCategory(new CategoryRequest("Electronics")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("A category with this name already exists");
    }

    @Test
    void getDailyRevenue_fillsMissingDaysWithZero() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(2).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        RevenueRow row = new RevenueRow() {
            @Override
            public String getRevenuePeriod() {
                return today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }

            @Override
            public BigDecimal getRevenue() {
                return new BigDecimal("500.00");
            }
        };

        when(paymentRepository.sumDailyRevenueBetween(start, end)).thenReturn(List.of(row));

        RevenueResponse response = adminService.getDailyRevenue(3);

        assertThat(response.points()).hasSize(3);
        assertThat(response.points().stream()
                .filter(point -> point.amount().compareTo(BigDecimal.ZERO) > 0)
                .map(point -> point.amount()))
                .containsExactly(new BigDecimal("500.00"));
        assertThat(response.total()).isEqualByComparingTo("500.00");
    }

    @Test
    void getDailyRevenue_invalidDays_isClamped() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        when(paymentRepository.sumDailyRevenueBetween(start, end)).thenReturn(List.of());

        RevenueResponse response = adminService.getDailyRevenue(0);

        assertThat(response.points()).hasSize(1);
        assertThat(response.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void getOverallRevenue_computesAverageOrderValue() {
        when(paymentRepository.sumSuccessfulRevenue()).thenReturn(new BigDecimal("1000.00"));
        when(orderRepository.count()).thenReturn(10L);
        when(paymentRepository.countByPaymentStatus(any())).thenReturn(4L);

        OverallRevenueResponse response = adminService.getOverallRevenue();

        assertThat(response.totalRevenue()).isEqualByComparingTo("1000.00");
        assertThat(response.totalOrders()).isEqualTo(10);
        assertThat(response.paidOrders()).isEqualTo(4);
        assertThat(response.averageOrderValue()).isEqualByComparingTo("250.00");
    }
}
