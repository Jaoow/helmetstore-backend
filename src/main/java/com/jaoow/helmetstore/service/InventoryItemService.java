package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.item.VariantStockUpdateDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryItemService {

    private final InventoryHelper inventoryHelper;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
    })
    public void updateItemStock(List<VariantStockUpdateDTO> dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        for (VariantStockUpdateDTO stockUpdateDTO : dto) {

            // TODO: Implement history tracking for stock updates
            inventoryItemRepository.updateStock(stockUpdateDTO.getVariantId(), stockUpdateDTO.getStock(), inventory);
        }
    }
}
