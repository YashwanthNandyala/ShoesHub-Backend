package com.example.registration.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
}
