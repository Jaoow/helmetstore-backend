package com.jaoow.helmetstore.nf.controller;

import com.jaoow.helmetstore.nf.OrderPDFExtractorService;
import com.jaoow.helmetstore.nf.dto.OrderSummaryDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/orders")
public class OrderPDFExtractorController {
    private final OrderPDFExtractorService orderPDFExtractorService;

    public OrderPDFExtractorController(OrderPDFExtractorService orderPDFExtractorService) {
        this.orderPDFExtractorService = orderPDFExtractorService;
    }

    @PostMapping("/extract")
    public OrderSummaryDTO extractOrderSummary(@RequestParam("file") MultipartFile file) {
        try {
            File tempFile = File.createTempFile("upload", ".pdf");
            file.transferTo(tempFile);
            OrderSummaryDTO orderSummary = orderPDFExtractorService.extractOrderSummary(tempFile);
            if (!tempFile.delete()) {
                throw new RuntimeException("Error deleting temporary file");
            }
            return orderSummary;
        } catch (IOException e) {
            throw new RuntimeException("Error processing PDF file", e);
        }
    }
}