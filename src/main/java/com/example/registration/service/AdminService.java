package com.example.registration.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.AdminDashboardResponse;
import com.example.registration.dto.AdminRecentOrderResponse;
import com.example.registration.dto.CategoryRequest;
import com.example.registration.dto.CategoryResponse;
import com.example.registration.dto.OverallRevenueResponse;
import com.example.registration.dto.ProductRequest;
import com.example.registration.dto.ProductResponse;
import com.example.registration.dto.RevenuePoint;
import com.example.registration.dto.RevenueResponse;
import com.example.registration.dto.UpdateUserRoleRequest;
import com.example.registration.dto.UserAdminResponse;
import com.example.registration.entity.Category;
import com.example.registration.entity.Order;
import com.example.registration.entity.OrderStatus;
import com.example.registration.entity.PaymentStatus;
import com.example.registration.entity.Product;
import com.example.registration.entity.ProductImage;
import com.example.registration.entity.Role;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.DuplicateResourceException;
import com.example.registration.exception.ResourceNotFoundException;
import com.example.registration.exception.UnauthorizedException;
import com.example.registration.repository.CategoryRepository;
import com.example.registration.repository.OrderItemRepository;
import com.example.registration.repository.OrderRepository;
import com.example.registration.repository.PaymentRepository;
import com.example.registration.repository.ProductImageRepository;
import com.example.registration.repository.ProductRepository;
import com.example.registration.repository.UserRepository;

@Service
public class AdminService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;

    public AdminService(UserRepository userRepository,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardSummary() {
        long userCount = userRepository.count();
        long productCount = productRepository.count();
        long orderCount = orderRepository.count();
        long paidOrderCount = orderRepository.countByStatus(OrderStatus.SUCCESS);
        BigDecimal totalRevenue = paymentRepository.sumSuccessfulRevenue();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        BigDecimal todayRevenue = paymentRepository.sumSuccessfulRevenueBetween(todayStart, todayStart.plusDays(1));

        List<AdminRecentOrderResponse> recentOrders = orderRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(this::toRecentOrderResponse)
                .toList();

        return new AdminDashboardResponse(userCount, productCount, orderCount, paidOrderCount,
                totalRevenue, todayRevenue, recentOrders);
    }

    @Transactional(readOnly = true)
    public List<UserAdminResponse> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public UserAdminResponse updateUserRole(Integer userId, UpdateUserRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User admin = currentUser();
        if (user.getId().equals(admin.getId())) {
            throw new BadRequestException("You cannot change your own role");
        }

        Role newRole = request.role();
        if (user.getRole() == newRole) {
            return toUserResponse(user);
        }

        if (user.getRole() == Role.ADMIN && newRole == Role.CUSTOMER
                && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot demote the last admin account");
        }

        user.setRole(newRole);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(this::toProductResponse)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        ensureCategoryExists(request.categoryId());
        String name = request.name().trim();
        if (!productRepository.findByNameIgnoreCase(name).isEmpty()) {
            throw new DuplicateResourceException("A product with this name already exists");
        }

        Product product = new Product();
        product.setName(name);
        product.setDescription(request.description() == null ? null : request.description().trim());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategoryId(request.categoryId());

        Product saved = productRepository.save(product);
        attachImage(saved, request.imageUrl());
        return toProductResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Integer productId, ProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ensureCategoryExists(request.categoryId());

        String name = request.name().trim();
        boolean duplicateName = productRepository.findByNameIgnoreCase(name).stream()
                .anyMatch(existing -> !existing.getProductId().equals(productId));
        if (duplicateName) {
            throw new DuplicateResourceException("A product with this name already exists");
        }

        product.setName(name);
        product.setDescription(request.description() == null ? null : request.description().trim());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategoryId(request.categoryId());
        productRepository.save(product);

        replaceImage(product, request.imageUrl());
        return toProductResponse(product);
    }

    @Transactional
    public void deleteProduct(Integer productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (orderItemRepository.existsByProduct_ProductId(productId)) {
            throw new BadRequestException(
                    "Cannot delete this product because it is part of existing orders");
        }

        if (product.getImage() != null) {
            productImageRepository.delete(product.getImage());
        }
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderByCategoryNameAsc().stream()
                .map(category -> new CategoryResponse(category.getCategoryId(), category.getCategoryName()))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.categoryName().trim();
        if (categoryRepository.existsByCategoryNameIgnoreCase(name)) {
            throw new DuplicateResourceException("A category with this name already exists");
        }

        Category category = new Category();
        category.setCategoryName(name);
        Category saved = categoryRepository.save(category);
        return new CategoryResponse(saved.getCategoryId(), saved.getCategoryName());
    }

    @Transactional(readOnly = true)
    public RevenueResponse getDailyRevenue(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDate startDate = endDate.minusDays(safeDays);

        List<String> labels = new ArrayList<>();
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            labels.add(date.format(DAY_FORMAT));
        }

        List<PaymentRepository.RevenueRow> rows = paymentRepository
                .sumDailyRevenueBetween(startDate.atStartOfDay(), endDate.atStartOfDay());
        return buildRevenue(rows, labels);
    }

    @Transactional(readOnly = true)
    public RevenueResponse getMonthlyRevenue(int months) {
        int safeMonths = Math.max(1, Math.min(months, 24));
        YearMonth endMonth = YearMonth.now().plusMonths(1);
        YearMonth startMonth = endMonth.minusMonths(safeMonths);

        List<String> labels = new ArrayList<>();
        for (YearMonth month = startMonth; month.isBefore(endMonth); month = month.plusMonths(1)) {
            labels.add(month.format(MONTH_FORMAT));
        }

        List<PaymentRepository.RevenueRow> rows = paymentRepository
                .sumMonthlyRevenueBetween(startMonth.atDay(1).atStartOfDay(),
                        endMonth.atDay(1).atStartOfDay());
        return buildRevenue(rows, labels);
    }

    @Transactional(readOnly = true)
    public RevenueResponse getYearlyRevenue() {
        int currentYear = LocalDate.now().getYear();
        int startYear = currentYear - 5;

        List<String> labels = new ArrayList<>();
        for (int year = startYear; year <= currentYear; year++) {
            labels.add(String.valueOf(year));
        }

        List<PaymentRepository.RevenueRow> rows = paymentRepository
                .sumYearlyRevenueBetween(LocalDate.of(startYear, 1, 1).atStartOfDay(),
                        LocalDate.of(currentYear + 1, 1, 1).atStartOfDay());
        return buildRevenue(rows, labels);
    }

    @Transactional(readOnly = true)
    public OverallRevenueResponse getOverallRevenue() {
        BigDecimal totalRevenue = paymentRepository.sumSuccessfulRevenue();
        long totalOrders = orderRepository.count();
        long paidOrders = paymentRepository.countByPaymentStatus(PaymentStatus.SUCCESS);

        BigDecimal averageOrderValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (paidOrders > 0) {
            averageOrderValue = totalRevenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);
        }

        return new OverallRevenueResponse(totalRevenue, totalOrders, paidOrders, averageOrderValue);
    }

    private RevenueResponse buildRevenue(List<PaymentRepository.RevenueRow> rows, List<String> labels) {
        Map<String, BigDecimal> byLabel = new LinkedHashMap<>();
        for (PaymentRepository.RevenueRow row : rows) {
            byLabel.put(row.getRevenuePeriod(), row.getRevenue());
        }

        List<RevenuePoint> points = labels.stream()
                .map(label -> new RevenuePoint(label,
                        byLabel.getOrDefault(label, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))))
                .toList();

        BigDecimal total = points.stream()
                .map(RevenuePoint::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueResponse(points, total);
    }

    private AdminRecentOrderResponse toRecentOrderResponse(Order order) {
        return new AdminRecentOrderResponse(
                order.getId(),
                order.getUser().getFullName(),
                order.getTotalAmount(),
                order.getStatus().name(),
                order.getCreatedAt());
    }

    private UserAdminResponse toUserResponse(User user) {
        return new UserAdminResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getCreatedAt());
    }

    private ProductResponse toProductResponse(Product product) {
        String imageUrl = null;
        if (product.getImage() != null) {
            imageUrl = product.getImage().getImageUrl();
        }
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategoryId(),
                imageUrl);
    }

    private void ensureCategoryExists(Integer categoryId) {
        if (categoryId == null || !categoryRepository.existsById(categoryId)) {
            throw new BadRequestException("Category does not exist");
        }
    }

    private void attachImage(Product product, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(imageUrl.trim());
        productImageRepository.save(image);
        product.setImage(image);
    }

    private void replaceImage(Product product, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            if (product.getImage() != null) {
                productImageRepository.delete(product.getImage());
                product.setImage(null);
            }
            return;
        }

        String trimmedUrl = imageUrl.trim();
        if (product.getImage() != null) {
            product.getImage().setImageUrl(trimmedUrl);
            productImageRepository.save(product.getImage());
        } else {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(trimmedUrl);
            productImageRepository.save(image);
            product.setImage(image);
        }
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
    }
}
