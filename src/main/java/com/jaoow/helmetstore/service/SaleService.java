package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.dto.sale.*;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
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
import com.jaoow.helmetstore.service.pdf.SaleReceiptPDFService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;
    private final SaleCalculationHelper saleCalculationHelper;
    private final SaleRepository saleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionService transactionService;
    private final SaleReceiptPDFService saleReceiptPDFService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<SaleResponseDTO> findAll(Pageable pageable, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Page<Sale> salesPage = saleRepository.findAllByInventoryPaginated(inventory, pageable);
        return salesPage.map(this::convertToSaleResponseDTO);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public SaleResponseDTO save(SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return saveMultiProductSale(dto, principal, inventory);
    }

    private SaleResponseDTO saveMultiProductSale(SaleCreateDTO dto, Principal principal, Inventory inventory) {
        // Validate and prepare sale items
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
        for (SaleItemCreateDTO itemDTO : dto.getItems()) {
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

            // Calculate values
            saleCalculationHelper.populateSaleItemCalculations(saleItem, inventoryItem);
            saleItems.add(saleItem);

            // Update stock
            inventoryItem.setQuantity(inventoryItem.getQuantity() - itemDTO.getQuantity());
            inventoryItemRepository.save(inventoryItem);

            // Accumulate totals
            totalAmount = totalAmount.add(saleItem.getTotalItemPrice());
            totalProfit = totalProfit.add(saleItem.getTotalItemProfit());
        }

        // Set sale totals
        sale.setTotalAmount(totalAmount);
        sale.setTotalProfit(totalProfit);

        // Validate payments sum equals total to be received
        if (!saleCalculationHelper.validatePaymentsSum(totalAmount, dto.getPayments())) {
            BigDecimal paymentsSum = saleCalculationHelper.calculatePaymentsSum(dto.getPayments());
            throw new IllegalArgumentException("A soma dos pagamentos (" + paymentsSum
                    + ") deve ser igual ao total da venda (" + totalAmount + ").");
        }

        // Map payments
        List<SalePayment> payments = dto.getPayments().stream()
                .filter(Objects::nonNull)
                .map(p -> SalePayment.builder()
                        .sale(sale)
                        .paymentMethod(p.getPaymentMethod())
                        .amount(p.getAmount())
                        .build())
                .collect(Collectors.toList());
        sale.setPayments(payments);

        // Save sale
        Sale savedSale = saleRepository.save(sale);
        transactionService.recordTransactionFromSale(savedSale, principal);

        return convertToSaleResponseDTO(savedSale);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public SaleResponseDTO update(Long saleId, SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        Sale sale = saleRepository.findByIdAndInventory(saleId, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));

        return updateMultiProductSale(sale, dto, principal, inventory);
    }

    private SaleResponseDTO updateMultiProductSale(Sale sale, SaleCreateDTO dto, Principal principal,
            Inventory inventory) {
        // Restore stock from old items
        if (sale.getItems() != null) {
            for (SaleItem oldItem : sale.getItems()) {
                InventoryItem inventoryItem = getInventoryItemOrThrow(inventory,
                        oldItem.getProductVariant());
                inventoryItem.setQuantity(inventoryItem.getQuantity() + oldItem.getQuantity());
                inventoryItemRepository.save(inventoryItem);
            }
        }

        // Clear old items
        if (sale.getItems() != null) {
            sale.getItems().clear();
        } else {
            sale.setItems(new ArrayList<>());
        }

        // Process new items
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (SaleItemCreateDTO itemDTO : dto.getItems()) {
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
            inventoryItem.setQuantity(inventoryItem.getQuantity() - itemDTO.getQuantity());
            inventoryItemRepository.save(inventoryItem);

            // Accumulate totals
            totalAmount = totalAmount.add(saleItem.getTotalItemPrice());
            totalProfit = totalProfit.add(saleItem.getTotalItemProfit());
        }

        // Update sale
        sale.setDate(dto.getDate());
        sale.setTotalAmount(totalAmount);
        sale.setTotalProfit(totalProfit);

        // Validate payments sum equals total to be received
        if (!saleCalculationHelper.validatePaymentsSum(totalAmount, dto.getPayments())) {
            BigDecimal paymentsSum = saleCalculationHelper.calculatePaymentsSum(dto.getPayments());
            throw new IllegalArgumentException("A soma dos pagamentos (" + paymentsSum
                    + ") deve ser igual ao total da venda (" + totalAmount + ").");
        }

        // Replace payments without changing the collection reference
        // (orphanRemoval-safe)
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

        // Update transactions linked to sale by removing and creating new ones
        transactionService.removeTransactionLinkedToSale(sale);
        Sale updatedSale = saleRepository.save(sale);
        transactionService.recordTransactionFromSale(updatedSale, principal);

        return convertToSaleResponseDTO(updatedSale);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public void delete(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = saleRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + id));

        // Restore stock from all items
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            for (SaleItem saleItem : sale.getItems()) {
                InventoryItem inventoryItem = inventoryItemRepository
                        .findByInventoryAndProductVariant(sale.getInventory(),
                                saleItem.getProductVariant())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Inventory item not found for variant ID: "
                                        + saleItem.getProductVariant()
                                                .getId()));

                inventoryItem.setQuantity(inventoryItem.getQuantity() + saleItem.getQuantity());
                inventoryItemRepository.save(inventoryItem);
            }
        }

        transactionService.removeTransactionLinkedToSale(sale);
        saleRepository.delete(sale);
    }

    @Transactional(readOnly = true)
    public byte[] generateReceipt(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = saleRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + id));
        
        try {
            return saleReceiptPDFService.generateSaleReceipt(sale);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar recibo: " + e.getMessage(), e);
        }
    }

    @Cacheable(value = CacheNames.SALES_HISTORY, 
            key = "#principal.name + '-' + (#year != null ? #year : 'all') + '-' + (#month != null ? #month : 'all')")
    @Transactional(readOnly = true)
    public SaleHistoryResponse getHistory(Integer year, Integer month, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        List<Sale> sales;
        if (year != null && month != null) {
            // Optimized: Filter sales by year and month directly in the database
            java.time.LocalDateTime startDate = java.time.LocalDateTime.of(year, month, 1, 0, 0, 0);
            java.time.LocalDateTime endDate = startDate.plusMonths(1);
            sales = saleRepository.findByInventoryAndDateRange(inventory, startDate, endDate);
        } else {
            // Return all sales if no filter specified (backward compatibility)
            sales = saleRepository.findAllByInventoryWithProductVariantsAndProducts(inventory);
        }

        List<SaleResponseDTO> saleDTOs = sales.stream()
                .map(this::convertToSaleResponseDTO)
                .collect(Collectors.toList());

        // Optimized: Collect variants and products in a single stream iteration
        Set<ProductVariant> variantSet = new HashSet<>();
        Set<com.jaoow.helmetstore.model.Product> productSet = new HashSet<>();
        
        sales.forEach(sale -> {
            if (sale.getItems() != null && !sale.getItems().isEmpty()) {
                sale.getItems().forEach(item -> {
                    ProductVariant variant = item.getProductVariant();
                    variantSet.add(variant);
                    productSet.add(variant.getProduct());
                });
            }
        });

        List<SimpleProductVariantDTO> productVariants = variantSet.stream()
                .map(variant -> modelMapper.map(variant, SimpleProductVariantDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductDTO> products = productSet.stream()
                .map(product -> modelMapper.map(product, SimpleProductDTO.class))
                .collect(Collectors.toList());

        return new SaleHistoryResponse(saleDTOs, products, productVariants);
    }

    @Transactional(readOnly = true)
    public SaleDetailDTO getById(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = saleRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + id));

        return convertToSaleDetailDTO(sale);
    }

    private ProductVariant getProductVariantOrThrow(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ProductNotFoundException(variantId));
    }

    private InventoryItem getInventoryItemOrThrow(Inventory inventory, ProductVariant variant) {
        return inventoryItemRepository.findByInventoryAndProductVariant(inventory, variant)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory item not found for variant ID: " + variant.getId()));
    }

    private void validateStock(InventoryItem item, int requiredQuantity) {
        if (item.getQuantity() < requiredQuantity) {
            throw new InsufficientStockException(
                    item.getProductVariant().getId(), item.getQuantity(), requiredQuantity);
        }
    }

    /**
     * Converts a Sale entity to SaleResponseDTO
     */
    private SaleResponseDTO convertToSaleResponseDTO(Sale sale) {
        SaleResponseDTO dto = SaleResponseDTO.builder()
                .id(sale.getId())
                .date(sale.getDate())
                .totalAmount(sale.getTotalAmount())
                .totalProfit(sale.getTotalProfit())
                .build();

        // Convert sale items
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            List<SaleItemDTO> itemDTOs = sale.getItems().stream()
                    .map(this::convertToSaleItemDTO)
                    .collect(Collectors.toList());
            dto.setItems(itemDTOs);
        }

        // Convert sale payments
        if (sale.getPayments() != null && !sale.getPayments().isEmpty()) {
            List<SalePaymentDTO> paymentDTOs = sale.getPayments().stream()
                    .map(this::convertToSalePaymentDTO)
                    .collect(Collectors.toList());
            dto.setPayments(paymentDTOs);
        }

        return dto;
    }

    /**
     * Converts a SaleItem entity to SaleItemDTO
     */
    private SaleItemDTO convertToSaleItemDTO(SaleItem saleItem) {
        return SaleItemDTO.builder()
                .id(saleItem.getId())
                .productVariantId(saleItem.getProductVariant().getId())
                .quantity(saleItem.getQuantity())
                .unitPrice(saleItem.getUnitPrice())
                .unitProfit(saleItem.getUnitProfit())
                .totalItemPrice(saleItem.getTotalItemPrice())
                .totalItemProfit(saleItem.getTotalItemProfit())
                .build();
    }

    /**
     * Converts a Sale entity to SaleDetailDTO
     */
    private SaleDetailDTO convertToSaleDetailDTO(Sale sale) {
        SaleDetailDTO dto = SaleDetailDTO.builder()
                .id(sale.getId())
                .date(sale.getDate())
                .totalAmount(sale.getTotalAmount())
                .totalProfit(sale.getTotalProfit())
                .build();

        // Convert sale items
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            List<SaleItemDTO> itemDTOs = sale.getItems().stream()
                    .map(this::convertToSaleItemDTO)
                    .collect(Collectors.toList());
            dto.setItems(itemDTOs);

            // Collect all product variants from sale items
            List<SimpleProductVariantDTO> productVariants = sale.getItems().stream()
                    .map(SaleItem::getProductVariant)
                    .distinct()
                    .map(variant -> modelMapper.map(variant, SimpleProductVariantDTO.class))
                    .collect(Collectors.toList());
            dto.setProductVariants(productVariants);

            // Collect all products from sale items
            List<SimpleProductDTO> products = sale.getItems().stream()
                    .map(item -> item.getProductVariant().getProduct())
                    .distinct()
                    .map(product -> modelMapper.map(product, SimpleProductDTO.class))
                    .collect(Collectors.toList());
            dto.setProducts(products);
        }

        // Map payments explicitly
        List<SalePaymentDTO> paymentDTOs = sale.getPayments() == null ? java.util.Collections.emptyList()
                : sale.getPayments().stream()
                        .map(p -> SalePaymentDTO.builder()
                                .paymentMethod(p.getPaymentMethod())
                                .amount(p.getAmount())
                                .build())
                        .collect(Collectors.toList());
        dto.setPayments(paymentDTOs);

        return dto;
    }
    
    private SalePaymentDTO convertToSalePaymentDTO(SalePayment salePayment) {
        return SalePaymentDTO.builder()
                .paymentMethod(salePayment.getPaymentMethod())
                .amount(salePayment.getAmount())
                .build();
    }
}
