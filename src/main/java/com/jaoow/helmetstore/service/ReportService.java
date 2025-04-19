package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.info.ProductStockDto;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<PurchaseOrderStatus> EXCLUDED_STATUSES = List.of(PurchaseOrderStatus.DELIVERED, PurchaseOrderStatus.CANCELED);

    private final SaleRepository saleRepository;
    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;
    private final InventoryItemRepository inventoryItemRepository;

    public List<ProductVariantSalesAndStockSummary> getProductIndicators(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return inventoryItemRepository.findAllWithSalesAndPurchaseDataByInventory(EXCLUDED_STATUSES, inventory);
    }

    public List<ProductVariantSaleSummary> getMostSoldProducts(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<ProductVariantSaleSummary> saleSummaries = inventoryItemRepository.findAllWithSalesDataByInventory(inventory);
        return saleSummaries.stream()
                .filter(summary -> summary.getTotalSold() > 0)
                .sorted(Comparator.comparingInt(ProductVariantSaleSummary::getTotalSold).reversed())
                .toList();
    }

    public List<ProductStockDto> getProductStock(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<ProductVariantStockSummary> projections = inventoryItemRepository.findAllWithStockDetailsByInventory(EXCLUDED_STATUSES, inventory);

        Map<Long, ProductStockDto> productMap = projections.stream()
                .collect(Collectors.toMap(
                        ProductVariantStockSummary::getProductId,
                        this::mapToProductStockDto,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        projections.forEach(projection -> {
            ProductStockDto productStock = productMap.get(projection.getProductId());
            productStock.getVariants().add(mapToProductStockVariantDto(projection));
        });

        return new ArrayList<>(productMap.values());
    }

    private ProductStockDto mapToProductStockDto(ProductVariantStockSummary projection) {
        return modelMapper.map(projection, ProductStockDto.class);
    }

    private ProductStockDto.ProductStockVariantDto mapToProductStockVariantDto(ProductVariantStockSummary projection) {
        return modelMapper.map(projection, ProductStockDto.ProductStockVariantDto.class);
    }

    public FinancialSummaryDTO getRevenueAndProfit(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return saleRepository.getFinancialSummary(inventory)
                .orElse(new FinancialSummaryDTO(BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
