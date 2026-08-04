package com.example.registration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.registration.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findAllByOrderByCategoryNameAsc();

    boolean existsByCategoryNameIgnoreCase(String categoryName);
}
