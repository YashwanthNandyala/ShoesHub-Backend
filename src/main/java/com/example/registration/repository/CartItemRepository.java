package com.example.registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.registration.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    Optional<CartItem> findByUser_IdAndProduct_ProductId(Integer userId, Integer productId);

    List<CartItem> findAllByUser_IdOrderByIdAsc(Integer userId);

    @Query("select coalesce(sum(c.quantity), 0) from CartItem c where c.user.id = :userId")
    Long sumQuantityByUserId(@Param("userId") Integer userId);
}
