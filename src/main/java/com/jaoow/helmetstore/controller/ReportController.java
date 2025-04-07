package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.info.ProductStockDto;
import com.jaoow.helmetstore.dto.summary.ProductVariantSaleSummary;
import com.jaoow.helmetstore.dto.summary.ProductVariantSalesAndStockSummary;
import com.jaoow.helmetstore.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping("/product-stock")
    public List<ProductStockDto> getStockSummary() {
        return reportService.getProductStock();
    }

    @GetMapping("/product-indicators")
    public List<ProductVariantSalesAndStockSummary> getProductIndicators() {
        return reportService.getProductIndicators();
    }

    @GetMapping("/most-sold-products")
    public List<ProductVariantSaleSummary> getMostSoldProducts() {
        return reportService.getMostSoldProducts();
    }

    @GetMapping("/revenue-profit")
    public FinancialSummaryDTO getRevenueAndProfit() {
        return reportService.getRevenueAndProfit();
    }
}
