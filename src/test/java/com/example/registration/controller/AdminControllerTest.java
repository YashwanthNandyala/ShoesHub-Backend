package com.example.registration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.registration.config.WebConfig;
import com.example.registration.dto.AdminDashboardResponse;
import com.example.registration.dto.AdminRecentOrderResponse;
import com.example.registration.dto.CategoryResponse;
import com.example.registration.dto.OverallRevenueResponse;
import com.example.registration.dto.ProductResponse;
import com.example.registration.dto.RevenuePoint;
import com.example.registration.dto.RevenueResponse;
import com.example.registration.dto.UserAdminResponse;
import com.example.registration.entity.Role;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.ResourceNotFoundException;
import com.example.registration.service.AdminService;

@WebMvcTest(AdminController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Test
    void getDashboardSummary_returns200() throws Exception {
        AdminDashboardResponse summary = new AdminDashboardResponse(3, 5, 2, 1,
                new BigDecimal("199.00"), new BigDecimal("99.00"),
                List.of(new AdminRecentOrderResponse("order-1", "Asha Rao",
                        new BigDecimal("199.00"), "SUCCESS", LocalDateTime.of(2026, 8, 3, 10, 0))));

        when(adminService.getDashboardSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").value(3))
                .andExpect(jsonPath("$.productCount").value(5))
                .andExpect(jsonPath("$.totalRevenue").value(199.00))
                .andExpect(jsonPath("$.recentOrders[0].orderId").value("order-1"));
    }

    @Test
    void getUsers_returns200() throws Exception {
        when(adminService.getAllUsers()).thenReturn(List.of(
                new UserAdminResponse(2, "Asha Rao", "asha@example.com", "8000000001",
                        Role.CUSTOMER, LocalDateTime.of(2026, 8, 1, 9, 0))));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("asha@example.com"))
                .andExpect(jsonPath("$[0].role").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void updateUserRole_returns200() throws Exception {
        when(adminService.updateUserRole(any(), any())).thenReturn(
                new UserAdminResponse(2, "Asha Rao", "asha@example.com", "8000000001",
                        Role.ADMIN, LocalDateTime.of(2026, 8, 1, 9, 0)));

        mockMvc.perform(put("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void updateUserRole_missingRole_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": null
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUserRole_unknownUser_returns404() throws Exception {
        when(adminService.updateUserRole(any(), any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(put("/api/admin/users/999/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "CUSTOMER"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void getProducts_returns200() throws Exception {
        when(adminService.getAllProducts()).thenReturn(List.of(
                new ProductResponse(1, "Wireless Mouse", "A wireless mouse",
                        new BigDecimal("799.00"), 10, 1, null)));

        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Wireless Mouse"))
                .andExpect(jsonPath("$[0].price").value(799.00));
    }

    @Test
    void createProduct_returns200() throws Exception {
        when(adminService.createProduct(any())).thenReturn(
                new ProductResponse(1, "Wireless Mouse", "A wireless mouse",
                        new BigDecimal("799.00"), 10, 1, null));

        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "description": "A wireless mouse",
                                  "price": 799.00,
                                  "stock": 10,
                                  "categoryId": 1,
                                  "imageUrl": "https://img.example.com/mouse.png"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void createProduct_missingPrice_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "stock": 10,
                                  "categoryId": 1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_negativeStock_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Wireless Mouse",
                                  "price": 799.00,
                                  "stock": -1,
                                  "categoryId": 1
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_returns200() throws Exception {
        when(adminService.updateProduct(any(), any())).thenReturn(
                new ProductResponse(1, "New Name", "Updated",
                        new BigDecimal("150.00"), 7, 2, null));

        mockMvc.perform(put("/api/admin/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Name",
                                  "price": 150.00,
                                  "stock": 7,
                                  "categoryId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void updateProduct_unknownProduct_returns404() throws Exception {
        when(adminService.updateProduct(any(), any()))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(put("/api/admin/products/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Keyboard",
                                  "price": 1200.00,
                                  "stock": 5,
                                  "categoryId": 1
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void deleteProduct_returns200() throws Exception {
        mockMvc.perform(delete("/api/admin/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product deleted successfully"));

        verify(adminService).deleteProduct(1);
    }

    @Test
    void deleteProduct_inOrders_returns400() throws Exception {
        doThrow(new BadRequestException("Cannot delete this product because it is part of existing orders"))
                .when(adminService).deleteProduct(1);

        mockMvc.perform(delete("/api/admin/products/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Cannot delete this product because it is part of existing orders"));
    }

    @Test
    void getCategories_returns200() throws Exception {
        when(adminService.getAllCategories()).thenReturn(
                List.of(new CategoryResponse(1, "Electronics")));

        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Electronics"));
    }

    @Test
    void createCategory_returns200() throws Exception {
        when(adminService.createCategory(any())).thenReturn(new CategoryResponse(1, "Electronics"));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryName": "Electronics"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Electronics"));
    }

    @Test
    void getDailyRevenue_returns200() throws Exception {
        RevenueResponse response = new RevenueResponse(
                List.of(new RevenuePoint("2026-08-03", new BigDecimal("500.00"))),
                new BigDecimal("500.00"));
        when(adminService.getDailyRevenue(7)).thenReturn(response);

        mockMvc.perform(get("/api/admin/revenue/daily").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].label").value("2026-08-03"))
                .andExpect(jsonPath("$.total").value(500.00));
    }

    @Test
    void getMonthlyRevenue_returns200() throws Exception {
        when(adminService.getMonthlyRevenue(12)).thenReturn(
                new RevenueResponse(List.of(), new BigDecimal("0.00")));

        mockMvc.perform(get("/api/admin/revenue/monthly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void getYearlyRevenue_returns200() throws Exception {
        when(adminService.getYearlyRevenue()).thenReturn(
                new RevenueResponse(List.of(), new BigDecimal("0.00")));

        mockMvc.perform(get("/api/admin/revenue/yearly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0.00));
    }

    @Test
    void getOverallRevenue_returns200() throws Exception {
        when(adminService.getOverallRevenue()).thenReturn(
                new OverallRevenueResponse(new BigDecimal("1000.00"), 10, 4, new BigDecimal("250.00")));

        mockMvc.perform(get("/api/admin/revenue/overall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1000.00))
                .andExpect(jsonPath("$.averageOrderValue").value(250.00));
    }
}
