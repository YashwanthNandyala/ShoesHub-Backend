package com.example.registration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@Configuration
public class RazorpayConfig {

    @Bean
    public RazorpayClient razorpayClient(
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret) {
        try {
            return new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException ex) {
            throw new IllegalStateException("Failed to initialize Razorpay client", ex);
        }
    }
}
