package com.example.registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.Payment;
import com.example.registration.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findFirstByOrder_IdAndPaymentStatus(String orderId, PaymentStatus paymentStatus);
}
