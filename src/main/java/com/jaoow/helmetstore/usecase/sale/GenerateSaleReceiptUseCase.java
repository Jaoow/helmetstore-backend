package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.service.pdf.SaleReceiptPDFService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.Principal;

/**
 * Use Case: Generate PDF receipt for a sale
 * 
 * Responsibilities:
 * - Find and validate sale exists
 * - Load sale with all items and payments
 * - Generate PDF receipt
 * - Return PDF as byte array
 */
@Component
@RequiredArgsConstructor
public class GenerateSaleReceiptUseCase {

    private final SaleRepository saleRepository;
    private final SaleReceiptPDFService saleReceiptPDFService;
    private final InventoryHelper inventoryHelper;

    @Transactional(readOnly = true)
    public byte[] execute(Long saleId, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        // Fetch sale with items
        Sale sale = saleRepository.findByIdAndInventoryWithItems(saleId, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + saleId));

        // Fetch payments (separate query to avoid MultipleBagFetchException)
        saleRepository.findByIdAndInventoryWithPayments(saleId, inventory);

        try {
            return saleReceiptPDFService.generateSaleReceipt(sale);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate receipt PDF for sale ID: " + saleId, e);
        }
    }
}
