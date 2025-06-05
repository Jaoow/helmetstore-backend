package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleHistoryResponse;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;
    private final SaleRepository saleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<SaleResponseDTO> findAll() {
        return saleRepository.findAll().stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, key = "#principal.name")
    })
    @Transactional
    public SaleResponseDTO save(SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        ProductVariant variant = getProductVariantOrThrow(dto.getVariantId());
        InventoryItem item = getInventoryItemOrThrow(inventory, variant);
        validateStock(item, dto.getQuantity());

        Sale sale = modelMapper.map(dto, Sale.class);
        sale.setId(null);
        sale.setInventory(inventory);
        sale.setProductVariant(variant);

        BigDecimal profit = calculateTotalProfit(dto.getUnitPrice(), item.getLastPurchasePrice(), dto.getQuantity());
        sale.setTotalProfit(profit);

        item.setQuantity(item.getQuantity() - dto.getQuantity());
        inventoryItemRepository.save(item);

        sale = saleRepository.save(sale);
        transactionService.recordTransactionFromSale(sale, principal); // must be called after sale is saved to ensure transaction ID is set

        return modelMapper.map(sale, SaleResponseDTO.class);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, key = "#principal.name")
    })
    @Transactional
    public SaleResponseDTO update(Long saleId, SaleCreateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        Sale sale = saleRepository.findByIdAndInventory(saleId, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));

        ProductVariant oldVariant = sale.getProductVariant();
        InventoryItem oldItem = getInventoryItemOrThrow(inventory, oldVariant);

        int oldQty = sale.getQuantity();
        int newQty = dto.getQuantity();

        if (!dto.getVariantId().equals(oldVariant.getId())) {
            oldItem.setQuantity(oldItem.getQuantity() + oldQty);
            inventoryItemRepository.save(oldItem);

            ProductVariant newVariant = getProductVariantOrThrow(dto.getVariantId());
            InventoryItem newItem = getInventoryItemOrThrow(inventory, newVariant);
            validateStock(newItem, newQty);

            newItem.setQuantity(newItem.getQuantity() - newQty);
            inventoryItemRepository.save(newItem);

            sale.setProductVariant(newVariant);

            BigDecimal profit = calculateTotalProfit(dto.getUnitPrice(), newItem.getLastPurchasePrice(), newQty);
            sale.setTotalProfit(profit);
        } else {
            int diff = newQty - oldQty;
            if (diff > 0) {
                validateStock(oldItem, diff);
            }

            oldItem.setQuantity(oldItem.getQuantity() - diff);
            inventoryItemRepository.save(oldItem);

            BigDecimal profit = calculateTotalProfit(dto.getUnitPrice(), oldItem.getLastPurchasePrice(), newQty);
            sale.setTotalProfit(profit);
        }

        sale.setQuantity(newQty);
        sale.setUnitPrice(dto.getUnitPrice());
        sale.setDate(dto.getDate());

        sale = saleRepository.save(sale);
        return modelMapper.map(sale, SaleResponseDTO.class);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.SALES_HISTORY, key = "#principal.name")
    })
    @Transactional
    public void delete(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = saleRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + id));

        InventoryItem item = inventoryItemRepository.findByInventoryAndProductVariant(sale.getInventory(), sale.getProductVariant())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for variant ID: " + sale.getProductVariant().getId()));

        item.setQuantity(item.getQuantity() + sale.getQuantity());
        inventoryItemRepository.save(item);

        transactionService.removeTransactionLinkedToSale(sale);
        saleRepository.delete(sale);
    }

    @Cacheable(value = CacheNames.SALES_HISTORY, key = "#principal.name")
    @Transactional(readOnly = true)
    public SaleHistoryResponse getHistory(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<Sale> sales = saleRepository.findAllByInventoryWithProductVariantsAndProducts(inventory);

        List<SaleResponseDTO> saleDTOs = sales.stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductVariantDTO> productVariants = sales.stream()
                .map(Sale::getProductVariant)
                .distinct()
                .map(variant -> modelMapper.map(variant, SimpleProductVariantDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductDTO> products = sales.stream()
                .map(sale -> sale.getProductVariant().getProduct())
                .distinct()
                .map(product -> modelMapper.map(product, SimpleProductDTO.class))
                .collect(Collectors.toList());

        return new SaleHistoryResponse(saleDTOs, products, productVariants);
    }

    private ProductVariant getProductVariantOrThrow(Long variantId) {
        return productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ProductNotFoundException(variantId));
    }

    private InventoryItem getInventoryItemOrThrow(Inventory inventory, ProductVariant variant) {
        return inventoryItemRepository.findByInventoryAndProductVariant(inventory, variant)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for variant ID: " + variant.getId()));
    }

    private void validateStock(InventoryItem item, int requiredQuantity) {
        if (item.getQuantity() < requiredQuantity) {
            throw new InsufficientStockException("Insufficient stock for variant ID: " + item.getProductVariant().getId());
        }
    }

    private BigDecimal calculateTotalProfit(BigDecimal unitPrice, BigDecimal lastPurchasePrice, int quantity) {
        return unitPrice.subtract(lastPurchasePrice).multiply(BigDecimal.valueOf(quantity));
    }
}
