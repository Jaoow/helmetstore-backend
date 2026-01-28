package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.helper.SaleCalculationHelper;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SalePayment;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Use Case: Update an existing sale
 * 
 * Responsibilities:
 * - Restore stock from old sale items
 * - Validate new items and stock availability
 * - Recalculate sale totals
 * - Update inventory stock
 * - Update sale record with new items and payments
 * - Update financial transactions
 * - Invalidate related caches
 */
@Component
@RequiredArgsConstructor
public class UpdateSaleUseCase {

    private final SaleRepository saleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionService transactionService;
    private final InventoryHelper inventoryHelper;
    private final SaleCalculationHelper saleCalculationHelper;
    private final ModelMapper modelMapper;

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public SaleResponseDTO execute(Long saleId, SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = findSaleOrThrow(saleId, inventory);

        // Step 1: Restore stock from old items
        restoreStockFromOldItems(sale, inventory);

        // Step 2: Clear old items and prepare for new ones
        clearOldItems(sale);

        // Step 3: Process new items
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (var itemDTO : dto.getItems()) {
            ProductVariant variant = getProductVariantOrThrow(itemDTO.getVariantId());
            InventoryItem inventoryItem = getInventoryItemOrThrow(inventory, variant);
            validateStock(inventoryItem, itemDTO.getQuantity());

            // Create new sale item
            SaleItem saleItem = SaleItem.builder()
                    .sale(sale)
                    .productVariant(variant)
                    .quantity(itemDTO.getQuantity())
                    .unitPrice(itemDTO.getUnitPrice())
                    .build();

            // Calculate values
            saleCalculationHelper.populateSaleItemCalculations(saleItem, inventoryItem);
            sale.getItems().add(saleItem);

            // Update stock
            updateInventoryStock(inventoryItem, -itemDTO.getQuantity());

            // Accumulate totals
            totalAmount = totalAmount.add(saleItem.getTotalItemPrice());
            totalProfit = totalProfit.add(saleItem.getTotalItemProfit());
        }

        // Step 4: Update sale data
        sale.setDate(dto.getDate());
        sale.setTotalAmount(totalAmount);
        sale.setTotalProfit(totalProfit);

        // Step 5: Validate and update payments
        validatePayments(dto, totalAmount);
        updatePayments(sale, dto);

        // Step 6: Update transactions
        transactionService.removeTransactionLinkedToSale(sale);
        Sale updatedSale = saleRepository.save(sale);
        transactionService.recordTransactionFromSale(updatedSale, principal);

        return convertToDTO(updatedSale);
    }

    private Sale findSaleOrThrow(Long saleId, Inventory inventory) {
        return saleRepository.findByIdAndInventory(saleId, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));
    }

    private void restoreStockFromOldItems(Sale sale, Inventory inventory) {
        if (sale.getItems() != null) {
            for (SaleItem oldItem : sale.getItems()) {
                InventoryItem inventoryItem = getInventoryItemOrThrow(inventory, oldItem.getProductVariant());
                updateInventoryStock(inventoryItem, oldItem.getQuantity());
            }
        }
    }

    private void clearOldItems(Sale sale) {
        if (sale.getItems() != null) {
            sale.getItems().clear();
        } else {
            sale.setItems(new ArrayList<>());
        }
    }

    private ProductVariant getProductVariantOrThrow(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new com.jaoow.helmetstore.exception.ProductNotFoundException(variantId));
    }

    private InventoryItem getInventoryItemOrThrow(Inventory inventory, ProductVariant variant) {
        return inventoryItemRepository.findByInventoryAndProductVariant(inventory, variant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory item not found for variant ID: " + variant.getId()));
    }

    private void validateStock(InventoryItem item, int requiredQuantity) {
        if (item.getQuantity() < requiredQuantity) {
            throw new InsufficientStockException(
                    item.getProductVariant().getId(),
                    item.getQuantity(),
                    requiredQuantity);
        }
    }

    private void updateInventoryStock(InventoryItem inventoryItem, int quantityChange) {
        inventoryItem.setQuantity(inventoryItem.getQuantity() + quantityChange);
        inventoryItemRepository.save(inventoryItem);
    }

    private void validatePayments(SaleCreateDTO dto, BigDecimal totalAmount) {
        if (!saleCalculationHelper.validatePaymentsSum(totalAmount, dto.getPayments())) {
            BigDecimal paymentsSum = saleCalculationHelper.calculatePaymentsSum(dto.getPayments());
            throw new IllegalArgumentException(
                    String.format("A soma dos pagamentos (%s) deve ser igual ao total da venda (%s).",
                            paymentsSum, totalAmount));
        }
    }

    private void updatePayments(Sale sale, SaleCreateDTO dto) {
        List<SalePayment> newPayments = dto.getPayments().stream()
                .filter(Objects::nonNull)
                .map(p -> SalePayment.builder()
                        .sale(sale)
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .build())
                .collect(Collectors.toList());

        if (sale.getPayments() == null) {
            sale.setPayments(new ArrayList<>());
        }
        sale.getPayments().clear();
        sale.getPayments().addAll(newPayments);
    }

    private SaleResponseDTO convertToDTO(Sale sale) {
        return modelMapper.map(sale, SaleResponseDTO.class);
    }
}
