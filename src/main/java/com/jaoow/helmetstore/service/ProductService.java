package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.product.ProductCreateDTO;
import com.jaoow.helmetstore.dto.product.ProductDto;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.ProductRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryService categoryService;
    private final InventoryHelper inventoryHelper;

    @Transactional(readOnly = true)
    public List<ProductDto> findAll(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return productRepository.findAllByInventoryWithVariants(inventory)
                .stream().map((product) -> modelMapper.map(product, ProductDto.class))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductDto findById(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return productRepository.findByIdAndInventory(id, inventory)
                .map(product -> modelMapper.map(product, ProductDto.class))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public ProductDto save(ProductCreateDTO productDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        // Validate that at least one variant is provided
        if (productDTO.getVariants() == null || productDTO.getVariants().isEmpty()) {
            throw new IllegalArgumentException("Pelo menos uma variante (SKU e Tamanho) é obrigatória");
        }
        
        Product product = modelMapper.map(productDTO, Product.class);
        product.setInventory(inventory);

        // Handle category assignment
        if (productDTO.getCategoryName() != null && !productDTO.getCategoryName().trim().isEmpty()) {
            product.setCategory(categoryService.findOrCreateCategory(productDTO.getCategoryName(), inventory));
        }

        if (productDTO.getVariants() != null) {
            final Product finalProduct = product;
            List<ProductVariant> variants = productDTO.getVariants().stream().map(variantDto -> {
                ProductVariant variant = modelMapper.map(variantDto, ProductVariant.class);
                variant.setProduct(finalProduct);
                return variant;
            }).collect(Collectors.toList());

            product.setVariants(variants);
        }

        product = productRepository.save(product);
        return modelMapper.map(product, ProductDto.class);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public ProductDto update(Long id, ProductDto productDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        // Busca o produto validando que pertence ao inventory do usuário
        Product product = productRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setModel(productDTO.getModel());
        product.setColor(productDTO.getColor());
        product.setImgUrl(productDTO.getImgUrl());
        product.setSalePrice(productDTO.getSalePrice());

        // Handle category update
        if (productDTO.getCategoryName() != null && !productDTO.getCategoryName().trim().isEmpty()) {
            product.setCategory(categoryService.findOrCreateCategory(productDTO.getCategoryName(), inventory));
        } else {
            product.setCategory(null);
        }

        updateProductVariants(product, productDTO.getVariants());

        product = productRepository.save(product);
        return modelMapper.map(product, ProductDto.class);
    }

    private void updateProductVariants(Product product, List<ProductDto.ProductVariantDto> variantDtos) {
        if (variantDtos == null)
            return;

        Map<Long, ProductVariant> existingVariants = product.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        List<ProductVariant> updatedVariants = new ArrayList<>();
        Set<Long> receivedVariantIds = new HashSet<>();

        for (ProductDto.ProductVariantDto variantDto : variantDtos) {
            if (variantDto.getId() == null) {
                ProductVariant newVariant = modelMapper.map(variantDto, ProductVariant.class);
                newVariant.setProduct(product);
                updatedVariants.add(newVariant);
            } else {
                ProductVariant existingVariant = existingVariants.get(variantDto.getId());
                if (existingVariant != null) {
                    existingVariant.setSku(variantDto.getSku());
                    existingVariant.setSize(variantDto.getSize());
                    updatedVariants.add(existingVariant);
                    receivedVariantIds.add(variantDto.getId());
                }
            }
        }

        List<ProductVariant> variantsToRemove = product.getVariants().stream()
                .filter(variant -> !receivedVariantIds.contains(variant.getId()))
                .collect(Collectors.toList());

        productVariantRepository.deleteAll(variantsToRemove);

        product.getVariants().clear();
        product.getVariants().addAll(updatedVariants);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public void delete(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        // Valida que o produto pertence ao inventory do usuário
        if (!productRepository.existsByIdAndInventory(id, inventory)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
