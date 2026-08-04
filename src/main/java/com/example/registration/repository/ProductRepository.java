package com.example.registration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findAllByOrderByNameAsc();

    List<Product> findByNameIgnoreCase(String name);
}
