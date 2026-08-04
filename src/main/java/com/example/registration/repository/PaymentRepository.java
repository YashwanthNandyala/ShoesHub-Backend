package com.example.registration.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.registration.entity.Payment;
import com.example.registration.entity.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findFirstByOrder_IdAndPaymentStatus(String orderId, PaymentStatus paymentStatus);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE payment_status = 'SUCCESS'",
            nativeQuery = true)
    BigDecimal sumSuccessfulRevenue();

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payments "
            + "WHERE payment_status = 'SUCCESS' AND created_at >= :start AND created_at < :end",
            nativeQuery = true)
    BigDecimal sumSuccessfulRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS revenuePeriod, "
            + "COALESCE(SUM(amount), 0) AS revenue FROM payments "
            + "WHERE payment_status = 'SUCCESS' AND created_at >= :start AND created_at < :end "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY revenuePeriod ASC",
            nativeQuery = true)
    List<RevenueRow> sumDailyRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS revenuePeriod, "
            + "COALESCE(SUM(amount), 0) AS revenue FROM payments "
            + "WHERE payment_status = 'SUCCESS' AND created_at >= :start AND created_at < :end "
            + "GROUP BY DATE_FORMAT(created_at, '%Y-%m') ORDER BY revenuePeriod ASC",
            nativeQuery = true)
    List<RevenueRow> sumMonthlyRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y') AS revenuePeriod, "
            + "COALESCE(SUM(amount), 0) AS revenue FROM payments "
            + "WHERE payment_status = 'SUCCESS' AND created_at >= :start AND created_at < :end "
            + "GROUP BY DATE_FORMAT(created_at, '%Y') ORDER BY revenuePeriod ASC",
            nativeQuery = true)
    List<RevenueRow> sumYearlyRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    interface RevenueRow {
        String getRevenuePeriod();

        BigDecimal getRevenue();
    }
}
