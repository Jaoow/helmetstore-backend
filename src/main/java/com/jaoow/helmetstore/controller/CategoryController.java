package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.CategoryDTO;
import com.jaoow.helmetstore.model.Category;
import com.jaoow.helmetstore.repository.CategoryRepository;
import com.jaoow.helmetstore.service.CategoryService;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.inventory.Inventory;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final CategoryService categoryService;
    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getAllCategories(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<CategoryDTO> categories = categoryRepository.findAllByInventory(inventory)
                .stream()
                .map(category -> modelMapper.map(category, CategoryDTO.class))
                .collect(Collectors.toList());
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> getCategoryById(@PathVariable Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return categoryRepository.findByIdAndInventory(id, inventory)
                .map(category -> modelMapper.map(category, CategoryDTO.class))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(@RequestBody CategoryDTO categoryDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Category category = categoryService.findOrCreateCategory(categoryDTO.getName(), inventory);
        return ResponseEntity.ok(modelMapper.map(category, CategoryDTO.class));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO categoryDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return categoryRepository.findByIdAndInventory(id, inventory)
                .map(existingCategory -> {
                    existingCategory.setName(categoryDTO.getName());
                    Category updatedCategory = categoryService.save(existingCategory);
                    return ResponseEntity.ok(modelMapper.map(updatedCategory, CategoryDTO.class));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id, Principal principal) {
        try {
            Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
            categoryService.delete(id, inventory);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Categoria tem produtos vinculados
            return ResponseEntity.badRequest().build();
        }
    }
}