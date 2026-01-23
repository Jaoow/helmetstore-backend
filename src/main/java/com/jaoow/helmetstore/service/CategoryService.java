package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.model.Category;
import com.jaoow.helmetstore.model.inventory.Inventory;
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
    public Category findOrCreateCategory(String categoryName, Inventory inventory) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }

        return categoryRepository.findByNameAndInventory(categoryName.trim(), inventory)
                .orElseGet(() -> {
                    Category newCategory = Category.builder()
                            .name(categoryName.trim())
                            .inventory(inventory)
                            .build();
                    return categoryRepository.save(newCategory);
                });
    }

    @Transactional(readOnly = true)
    public Category findByNameAndInventory(String name, Inventory inventory) {
        return categoryRepository.findByNameAndInventory(name, inventory).orElse(null);
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

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public void delete(Long categoryId, Inventory inventory) {
        Category category = categoryRepository.findByIdAndInventory(categoryId, inventory)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada"));

        // Verifica se há produtos na categoria
        if (category.getProducts() != null && !category.getProducts().isEmpty()) {
            throw new IllegalStateException(
                "Não é possível deletar a categoria porque há " + category.getProducts().size() + 
                " produto(s) vinculado(s). Remova ou altere a categoria dos produtos primeiro."
            );
        }

        categoryRepository.deleteById(categoryId);
    }
}