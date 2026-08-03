package com.example.registration.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.registration.dto.CategoryResponse;
import com.example.registration.entity.Category;
import com.example.registration.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderByCategoryNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getCategoryId(), category.getCategoryName());
    }
}
