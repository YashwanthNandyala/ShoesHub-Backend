package com.example.registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.Order;
import com.example.registration.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByUser_IdAndStatus(Integer userId, OrderStatus status);

    List<Order> findAllByUser_IdAndStatusOrderByCreatedAtDesc(Integer userId, OrderStatus status);
}
