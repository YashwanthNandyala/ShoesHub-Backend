package com.example.registration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.registration.dto.AddToCartRequest;
import com.example.registration.dto.CartCountResponse;
import com.example.registration.service.CartService;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ResponseEntity<Void> addToCart(@RequestBody @Valid AddToCartRequest request) {
        cartService.addToCart(request.productId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/count")
    public ResponseEntity<CartCountResponse> getCartCount() {
        return ResponseEntity.ok(cartService.getCartCount());
    }
}
