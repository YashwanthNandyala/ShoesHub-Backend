package com.example.registration.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.CartCountResponse;
import com.example.registration.dto.CartItemResponse;
import com.example.registration.dto.CartResponse;
import com.example.registration.entity.CartItem;
import com.example.registration.entity.Product;
import com.example.registration.entity.User;
import com.example.registration.exception.BadRequestException;
import com.example.registration.exception.ResourceNotFoundException;
import com.example.registration.repository.CartItemRepository;
import com.example.registration.repository.ProductRepository;
import com.example.registration.repository.UserRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartItemRepository cartItemRepository,
            UserRepository userRepository,
            ProductRepository productRepository) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void addToCart(Integer productId) {
        User user = currentUser();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        CartItem cartItem = cartItemRepository
                .findByUser_IdAndProduct_ProductId(user.getId(), productId)
                .orElseGet(() -> newCartItem(user, product));

        int newQuantity = cartItem.getQuantity() + 1;
        if (newQuantity > product.getStock()) {
            throw new BadRequestException(
                    "Insufficient stock for \"" + product.getName()
                            + "\". Available: " + product.getStock());
        }
        cartItem.setQuantity(newQuantity);
        cartItemRepository.save(cartItem);
    }

    @Transactional
    public void updateCartItem(Integer productId, String action) {
        User user = currentUser();
        CartItem cartItem = cartItemRepository
                .findByUser_IdAndProduct_ProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if ("INCREMENT".equals(action)) {
            Product product = cartItem.getProduct();
            int newQuantity = cartItem.getQuantity() + 1;
            if (newQuantity > product.getStock()) {
                throw new BadRequestException(
                        "Cannot increase quantity for \"" + product.getName()
                                + "\". Stock limit reached: " + product.getStock());
            }
            cartItem.setQuantity(newQuantity);
            cartItemRepository.save(cartItem);
        } else {
            int newQuantity = cartItem.getQuantity() - 1;
            if (newQuantity <= 0) {
                cartItemRepository.delete(cartItem);
            } else {
                cartItem.setQuantity(newQuantity);
                cartItemRepository.save(cartItem);
            }
        }
    }

    @Transactional
    public void deleteCartItem(Integer productId) {
        User user = currentUser();
        CartItem cartItem = cartItemRepository
                .findByUser_IdAndProduct_ProductId(user.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
        cartItemRepository.delete(cartItem);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart() {
        User user = currentUser();
        List<CartItemResponse> items = cartItemRepository
                .findAllByUser_IdOrderByIdAsc(user.getId()).stream()
                .map(this::toResponse)
                .toList();
        BigDecimal overallTotalPrice = items.stream()
                .map(CartItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, overallTotalPrice);
    }

    @Transactional(readOnly = true)
    public CartCountResponse getCartCount() {
        User user = currentUser();
        Long count = cartItemRepository.sumQuantityByUserId(user.getId());
        return new CartCountResponse(count == null ? 0 : count.intValue());
    }

    private CartItemResponse toResponse(CartItem cartItem) {
        Product product = cartItem.getProduct();
        BigDecimal pricePerUnit = product.getPrice();
        BigDecimal totalPrice = pricePerUnit.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
        String imageUrl = null;
        if (product.getImage() != null) {
            imageUrl = product.getImage().getImageUrl();
        }
        return new CartItemResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                imageUrl,
                pricePerUnit,
                cartItem.getQuantity(),
                totalPrice);
    }

    private CartItem newCartItem(User user, Product product) {
        CartItem cartItem = new CartItem();
        cartItem.setUser(user);
        cartItem.setProduct(product);
        cartItem.setQuantity(0);
        return cartItem;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }
}
