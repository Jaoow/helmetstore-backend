package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.product.ProductCreateDTO;
import com.jaoow.helmetstore.dto.product.ProductDto;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.repository.ProductRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;
    private final ProductVariantRepository productVariantRepository;
    private final CategoryService categoryService;

    @Cacheable(value = CacheNames.PRODUCT)
    @Transactional(readOnly = true)
    public List<ProductDto> findAll() {
        return productRepository.findAllWithVariants()
                .stream().map((product) -> modelMapper.map(product, ProductDto.class))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductDto findById(Long id) {
        return productRepository.findById(id)
                .map(product -> modelMapper.map(product, ProductDto.class))
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true)
    })
    public ProductDto save(ProductCreateDTO productDTO) {
        Product product = modelMapper.map(productDTO, Product.class);

        // Handle category assignment
        if (productDTO.getCategoryName() != null && !productDTO.getCategoryName().trim().isEmpty()) {
            product.setCategory(categoryService.findOrCreateCategory(productDTO.getCategoryName()));
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
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheNames.PRODUCT, allEntries = true)
    public ProductDto update(Long id, ProductDto productDTO) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        product.setModel(productDTO.getModel());
        product.setColor(productDTO.getColor());
        product.setImgUrl(productDTO.getImgUrl());

        // Handle category update
        if (productDTO.getCategoryName() != null && !productDTO.getCategoryName().trim().isEmpty()) {
            product.setCategory(categoryService.findOrCreateCategory(productDTO.getCategoryName()));
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
    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = CacheNames.PRODUCT, allEntries = true)
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
