package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.dto.sale.SaleHistoryResponse;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use Case: Get sale history with filters
 *
 * Responsibilities:
 * - Fetch sales for given period (year/month or all)
 * - Eagerly load items, variants and products to avoid N+1
 * - Extract unique products and variants for frontend reference
 * - Return optimized DTO structure
 * - Cache results for performance
 */
@Component
@RequiredArgsConstructor
public class GetSaleHistoryUseCase {

    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;

    @Cacheable(value = CacheNames.SALES_HISTORY,
            key = "#principal.name + '-' + (#year != null ? #year : 'all') + '-' + (#month != null ? #month : 'all')")
    @Transactional(readOnly = true)
    public SaleHistoryResponse execute(Integer year, Integer month, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // Fetch sales based on filters
        List<Sale> sales = fetchSales(inventory, year, month);

        // Convert to DTOs
        List<SaleResponseDTO> saleDTOs = sales.stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());

        // Extract unique variants and products for frontend reference
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

    private List<Sale> fetchSales(Inventory inventory, Integer year, Integer month) {
        List<Sale> sales;

        if (year != null && month != null) {
            // Fetch sales for specific month
            LocalDateTime startDate = LocalDateTime.of(year, month, 1, 0, 0, 0);
            LocalDateTime endDate = startDate.plusMonths(1);
            sales = saleRepository.findByInventoryAndDateRange(inventory, startDate, endDate);
        } else {
            // Fetch all sales
            sales = saleRepository.findAllByInventoryWithProductVariantsAndProducts(inventory);
        }

        // Fetch payments in batch to avoid N+1 (cannot use single query due to MultipleBagFetchException)
        if (!sales.isEmpty()) {
            saleRepository.loadPaymentsForSales(sales);
        }

        return sales;
    }
}
