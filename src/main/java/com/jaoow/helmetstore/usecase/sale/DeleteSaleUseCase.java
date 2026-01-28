package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

/**
 * Use Case: Delete a sale
 * 
 * Responsibilities:
 * - Find and validate sale exists
 * - Restore stock for all items
 * - Remove associated transactions
 * - Delete sale record
 * - Invalidate related caches
 */
@Component
@RequiredArgsConstructor
public class DeleteSaleUseCase {

    private final SaleRepository saleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionService transactionService;
    private final InventoryHelper inventoryHelper;

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public void execute(Long saleId, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = findSaleOrThrow(saleId, inventory);

        // Restore stock for all items
        restoreStockFromSale(sale, inventory);

        // Remove associated transactions
        transactionService.removeTransactionLinkedToSale(sale);

        // Delete sale
        saleRepository.delete(sale);
    }

    private Sale findSaleOrThrow(Long saleId, Inventory inventory) {
        return saleRepository.findByIdAndInventory(saleId, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));
    }

    private void restoreStockFromSale(Sale sale, Inventory inventory) {
        if (sale.getItems() == null || sale.getItems().isEmpty()) {
            return;
        }

        for (SaleItem saleItem : sale.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByInventoryAndProductVariant(inventory, saleItem.getProductVariant())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Inventory item not found for product variant: " + saleItem.getProductVariant()));

            // Restore stock
            inventoryItem.setQuantity(inventoryItem.getQuantity() + saleItem.getQuantity());
            inventoryItemRepository.save(inventoryItem);
        }
    }
}
