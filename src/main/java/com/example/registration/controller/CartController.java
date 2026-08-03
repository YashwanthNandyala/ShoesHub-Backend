package com.example.registration.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.registration.dto.AddToCartRequest;
import com.example.registration.dto.CartCountResponse;
import com.example.registration.dto.CartItemResponse;
import com.example.registration.dto.CartResponse;
import com.example.registration.dto.DeleteCartRequest;
import com.example.registration.dto.UpdateCartRequest;
import com.example.registration.service.CartService;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public ResponseEntity<Void> addToCart(@RequestBody @Valid AddToCartRequest request) {
        cartService.addToCart(request.productId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/items/count")
    public ResponseEntity<CartCountResponse> getCartCount() {
        return ResponseEntity.ok(cartService.getCartCount());
    }

    @GetMapping("/items")
    public ResponseEntity<CartResponse> getCart() {
        return ResponseEntity.ok(cartService.getCart());
    }

    @PutMapping("/update")
    public ResponseEntity<Void> updateCartItem(@RequestBody @Valid UpdateCartRequest request) {
        cartService.updateCartItem(request.productId(), request.action());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Void> deleteCartItem(@RequestBody @Valid DeleteCartRequest request) {
        cartService.deleteCartItem(request.productId());
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<Void> addToCartLegacy(@RequestBody @Valid AddToCartRequest request) {
        cartService.addToCart(request.productId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCartLegacy() {
        return ResponseEntity.ok(cartService.getCart().items());
    }

    @GetMapping("/count")
    public ResponseEntity<CartCountResponse> getCartCountLegacy() {
        return ResponseEntity.ok(cartService.getCartCount());
    }
}
