package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.order.PurchaseOrderCreateDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderHistoryResponse;
import com.jaoow.helmetstore.dto.order.PurchaseOrderUpdateDTO;
import com.jaoow.helmetstore.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public List<PurchaseOrderDTO> getAll() {
        return purchaseOrderService.findAll();
    }

    @GetMapping("/history")
    public PurchaseOrderHistoryResponse getAllHistory(Principal principal) {
        return purchaseOrderService.getHistory(principal);
    }

    @PostMapping
    public PurchaseOrderDTO create(@RequestBody @Valid PurchaseOrderCreateDTO purchaseOrderDTO, Principal principal) {
        return purchaseOrderService.save(purchaseOrderDTO, principal);
    }

    @PutMapping("/{id}")
    public PurchaseOrderDTO update(@PathVariable Long id, @RequestBody @Valid PurchaseOrderUpdateDTO purchaseOrderUpdateDTO, Principal principal) {
        return purchaseOrderService.update(id, purchaseOrderUpdateDTO, principal);
    }

    /**
     * Upload do arquivo PDF (DANFE) para uma ordem de compra
     */
    @PostMapping("/{id}/upload-pdf")
    public ResponseEntity<String> uploadPdf(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        try {
            String filePath = purchaseOrderService.uploadPdfFile(id, file, principal);
            return ResponseEntity.ok("PDF uploaded successfully: " + filePath);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error uploading PDF: " + e.getMessage());
        }
    }

    /**
     * Upload do arquivo XML da NF-e para uma ordem de compra
     */
    @PostMapping("/{id}/upload-xml")
    public ResponseEntity<String> uploadXml(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        try {
            String filePath = purchaseOrderService.uploadXmlFile(id, file, principal);
            return ResponseEntity.ok("XML uploaded successfully: " + filePath);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Error uploading XML: " + e.getMessage());
        }
    }
}
