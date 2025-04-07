package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.info.ProductStockDto;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.repository.ProductRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final List<PurchaseOrderStatus> EXCLUDED_STATUSES = List.of(PurchaseOrderStatus.DELIVERED, PurchaseOrderStatus.CANCELED);

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;

    public List<ProductVariantSalesAndStockSummary> getProductIndicators() {
        return productRepository.findAllWithSalesAndPurchaseData(EXCLUDED_STATUSES);
    }

    public List<ProductVariantSaleSummary> getMostSoldProducts() {
        List<ProductVariantSaleSummary> saleSummaries = productRepository.findAllWithSalesData();
        return saleSummaries.stream()
                .filter(summary -> summary.getTotalSold() > 0)
                .sorted(Comparator.comparingInt(ProductVariantSaleSummary::getTotalSold).reversed())
                .toList();
    }

    public List<ProductStockDto> getProductStock() {
        List<ProductVariantStockSummary> projections = productRepository.findAllWithStockDetails(EXCLUDED_STATUSES);

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

    public FinancialSummaryDTO getRevenueAndProfit() {
        return saleRepository.getFinancialSummary()
                .orElse(new FinancialSummaryDTO(BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
