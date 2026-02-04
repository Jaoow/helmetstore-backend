package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.exception.InsufficientStockException;
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
import com.jaoow.helmetstore.usecase.transaction.RecordTransactionFromSaleUseCase;
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
 * Use Case: Create a new sale
 *
 * Responsibilities:
 * - Validate stock availability for all items
 * - Calculate sale totals (amount and profit)
 * - Validate payments sum matches sale total
 * - Update inventory stock
 * - Create sale record with items and payments
 * - Record financial transactions
 * - Invalidate related caches
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * ⚠️ RULE OF GOLD:
 * - Sale is HISTORICAL (never rewrite profit history)
 * - Transaction is FINANCIAL TRUTH (source of accounting)
 * - SalePayment is just a RECORD (never generates transactions)
 * - Exchange-derived sales have ZERO profit (profit comes from difference)
 * - isDerivedFromExchange prevents duplicate transaction recording
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
public class CreateSaleUseCase {

    private final SaleRepository saleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final RecordTransactionFromSaleUseCase recordTransactionFromSaleUseCase;
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
    public SaleResponseDTO execute(SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // Initialize collections
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        // Create sale entity
        Sale sale = Sale.builder()
                .date(dto.getDate())
                .inventory(inventory)
                .items(saleItems)
                .build();

        // Process each item
        for (var itemDTO : dto.getItems()) {
            // Validate and get entities
            ProductVariant variant = getProductVariantOrThrow(itemDTO.getVariantId());
            InventoryItem inventoryItem = getInventoryItemOrThrow(inventory, variant);
            validateStock(inventoryItem, itemDTO.getQuantity());

            // Create sale item
            SaleItem saleItem = SaleItem.builder()
                    .sale(sale)
                    .productVariant(variant)
                    .quantity(itemDTO.getQuantity())
                    .unitPrice(itemDTO.getUnitPrice())
                    .build();

            // Calculate profit and pricing
            saleCalculationHelper.populateSaleItemCalculations(saleItem, inventoryItem);
            saleItems.add(saleItem);

            // Update stock
            updateInventoryStock(inventoryItem, -itemDTO.getQuantity());

            // Accumulate totals
            totalAmount = totalAmount.add(saleItem.getTotalItemPrice());
            totalProfit = totalProfit.add(saleItem.getTotalItemProfit());
        }

        // Set sale totals
        sale.setTotalAmount(totalAmount);
        
        // ⚠️ CRITICAL: Derived sales from exchanges must have ZERO profit
        // Profit comes ONLY from the difference transaction, not from the sale itself
        // This prevents profit duplication in financial reports
        if (Boolean.TRUE.equals(dto.getIsDerivedFromExchange())) {
            sale.setTotalProfit(BigDecimal.ZERO);
        } else {
            sale.setTotalProfit(totalProfit);
        }
        
        sale.setIsDerivedFromExchange(dto.getIsDerivedFromExchange());

        // Validate and create payments
        validatePayments(dto, totalAmount);
        List<SalePayment> payments = createPayments(dto, sale);
        sale.setPayments(payments);

        // Save and record transaction
        Sale savedSale = saleRepository.save(sale);

        // ⚠️ CRITICAL: Only record financial transactions if NOT derived from exchange
        //
        // isDerivedFromExchange MUST only be true when sale is created by ExchangeProductUseCase.
        // This prevents duplicate financial transactions since the original sale already has them.
        //
        // DO NOT set this flag manually unless you understand the financial implications.
        if (!Boolean.TRUE.equals(dto.getIsDerivedFromExchange())) {
            recordTransactionFromSaleUseCase.execute(savedSale, principal);
        }

        return convertToDTO(savedSale);
    }

    private ProductVariant getProductVariantOrThrow(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new com.jaoow.helmetstore.exception.ProductNotFoundException(variantId));
    }

    private InventoryItem getInventoryItemOrThrow(Inventory inventory, ProductVariant variant) {
        return inventoryItemRepository.findByInventoryAndProductVariant(inventory, variant)
                .orElseThrow(() -> new com.jaoow.helmetstore.exception.ResourceNotFoundException(
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

    private List<SalePayment> createPayments(SaleCreateDTO dto, Sale sale) {
        return dto.getPayments().stream()
                .filter(Objects::nonNull)
                .map(p -> SalePayment.builder()
                        .sale(sale)
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .build())
                .collect(Collectors.toList());
    }

    private SaleResponseDTO convertToDTO(Sale sale) {
        return modelMapper.map(sale, SaleResponseDTO.class);
    }
}
