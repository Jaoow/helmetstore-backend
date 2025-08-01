package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.model.Category;
import com.jaoow.helmetstore.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public Category findOrCreateCategory(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }

        return categoryRepository.findByName(categoryName.trim())
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name(categoryName.trim())
                            .build();
                    return categoryRepository.save(newCategory);
                });
    }

    @Transactional(readOnly = true)
    public Category findByName(String name) {
        return categoryRepository.findByName(name).orElse(null);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public Category save(Category category) {
        return categoryRepository.save(category);
    }
}