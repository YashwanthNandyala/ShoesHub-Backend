package com.example.registration.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.registration.dto.AdminDashboardResponse;
import com.example.registration.dto.CategoryRequest;
import com.example.registration.dto.CategoryResponse;
import com.example.registration.dto.OverallRevenueResponse;
import com.example.registration.dto.ProductRequest;
import com.example.registration.dto.ProductResponse;
import com.example.registration.dto.RevenueResponse;
import com.example.registration.dto.UpdateUserRoleRequest;
import com.example.registration.dto.UserAdminResponse;
import com.example.registration.service.AdminService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<AdminDashboardResponse> getDashboardSummary() {
        return ResponseEntity.ok(adminService.getDashboardSummary());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminResponse>> getUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserAdminResponse> updateUserRole(@PathVariable Integer userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(adminService.updateUserRole(userId, request));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(adminService.getAllProducts());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(adminService.createProduct(request));
    }

    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Integer productId,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(adminService.updateProduct(productId, request));
    }

    @DeleteMapping("/products/{productId}")
    public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable Integer productId) {
        adminService.deleteProduct(productId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Product deleted successfully");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        return ResponseEntity.ok(adminService.getAllCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(adminService.createCategory(request));
    }

    @GetMapping("/revenue/daily")
    public ResponseEntity<RevenueResponse> getDailyRevenue(
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return ResponseEntity.ok(adminService.getDailyRevenue(days));
    }

    @GetMapping("/revenue/monthly")
    public ResponseEntity<RevenueResponse> getMonthlyRevenue(
            @RequestParam(name = "months", defaultValue = "12") int months) {
        return ResponseEntity.ok(adminService.getMonthlyRevenue(months));
    }

    @GetMapping("/revenue/yearly")
    public ResponseEntity<RevenueResponse> getYearlyRevenue() {
        return ResponseEntity.ok(adminService.getYearlyRevenue());
    }

    @GetMapping("/revenue/overall")
    public ResponseEntity<OverallRevenueResponse> getOverallRevenue() {
        return ResponseEntity.ok(adminService.getOverallRevenue());
    }
}
