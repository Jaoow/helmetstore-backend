package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.product.ProductDataResponseDTO;
import com.jaoow.helmetstore.dto.product.ProductDataUpsertDTO;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.ProductData;
import com.jaoow.helmetstore.repository.ProductDataRepository;
import com.jaoow.helmetstore.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class ProductDataService {

        private final ProductRepository productRepository;
        private final ProductDataRepository productDataRepository;
        private final InventoryHelper inventoryHelper;
        private final ModelMapper modelMapper;

        @Transactional
        @Caching(evict = {
                        @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
                        @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
        })
        public ProductDataResponseDTO upsert(ProductDataUpsertDTO dto, Principal principal) {
                Product product = productRepository.findById(dto.getProductId())
                                .orElseThrow(() -> new ProductNotFoundException(dto.getProductId()));

                Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

                ProductData productData = productDataRepository.findByInventoryAndProduct(
                                inventory, product).orElse(
                                                ProductData.builder()
                                                                .product(product)
                                                                .inventory(inventory)
                                                                .build());

                productData.setSalePrice(dto.getSalePrice());
                ProductData savedProductData = productDataRepository.save(productData);

                ProductDataResponseDTO response = modelMapper.map(savedProductData, ProductDataResponseDTO.class);
                response.setCategoryName(product.getCategory() != null ? product.getCategory().getName() : null);

                return response;
        }

}
