package com.example.registration.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.CartCountResponse;
import com.example.registration.entity.CartItem;
import com.example.registration.entity.Product;
import com.example.registration.entity.User;
import com.example.registration.repository.CartItemRepository;
import com.example.registration.repository.UserRepository;

@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;

    public CartService(CartItemRepository cartItemRepository, UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void addToCart(Integer productId) {
        User user = currentUser();
        CartItem cartItem = cartItemRepository
                .findByUser_IdAndProduct_ProductId(user.getId(), productId)
                .orElseGet(() -> newCartItem(user, productId));
        cartItem.setQuantity(cartItem.getQuantity() + 1);
        cartItemRepository.save(cartItem);
    }

    @Transactional(readOnly = true)
    public CartCountResponse getCartCount() {
        User user = currentUser();
        Long count = cartItemRepository.sumQuantityByUserId(user.getId());
        return new CartCountResponse(count == null ? 0 : count.intValue());
    }

    private CartItem newCartItem(User user, Integer productId) {
        Product product = new Product();
        product.setProductId(productId);
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
