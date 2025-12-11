package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.info.ProductStockDto;
import com.jaoow.helmetstore.dto.summary.ProductSalesAndStockSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/product-stock")
    public List<ProductStockDto> getStockSummary(Principal principal) {
        return reportService.getProductStock(principal);
    }

    @GetMapping("/product-indicators")
    public List<ProductVariantSalesAndStockSummary> getProductIndicators(Principal principal) {
        return reportService.getProductIndicators(principal);
    }

    @GetMapping("/product-indicators-grouped")
    public List<ProductSalesAndStockSummary> getProductIndicatorsGrouped(Principal principal) {
        return reportService.getProductIndicatorsGrouped(principal);
    }

    @GetMapping("/most-sold-products")
    public List<ProductVariantSaleSummary> getMostSoldProducts(Principal principal) {
        return reportService.getMostSoldProducts(principal);
    }
}
