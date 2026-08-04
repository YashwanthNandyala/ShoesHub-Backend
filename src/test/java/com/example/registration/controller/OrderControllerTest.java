package com.example.registration.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

import com.example.registration.config.WebConfig;
import com.example.registration.dto.OrderItemResponse;
import com.example.registration.dto.OrderResponse;
import com.example.registration.service.OrderService;

@WebMvcTest(OrderController.class)
@Import(WebConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void getOrders_returnsSuccessOrders() throws Exception {
        OrderItemResponse item = new OrderItemResponse(
                1, "Aivin Wave Pro", "Comfortable running shoe",
                "https://example.com/shoe.jpg", "Shoes",
                2, new BigDecimal("1299.00"), new BigDecimal("2598.00"));
        OrderResponse order = new OrderResponse(
                "order-app-1", "SUCCESS",
                LocalDateTime.of(2026, 8, 1, 12, 0),
                1, new BigDecimal("2598.00"), List.of(item));
        when(orderService.getSuccessfulOrders()).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value("order-app-1"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].itemCount").value(1))
                .andExpect(jsonPath("$[0].grandTotal").value(2598.00))
                .andExpect(jsonPath("$[0].items[0].category").value("Shoes"))
                .andExpect(jsonPath("$[0].items[0].quantity").value(2));
    }
}
