package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.fiscal.FiscalReportDTO;
import com.jaoow.helmetstore.dto.fiscal.PurchaseEntryDTO;
import com.jaoow.helmetstore.dto.fiscal.SaleExitDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.PurchaseOrderRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Serviço para relatórios fiscais MEI
 * Gera relatórios de entradas e saídas conforme exigências da Receita Federal para MEI
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalReportService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;

    /**
     * Gera relatório de entradas e saídas para um período específico
     * Este relatório serve como Livro Caixa / Livro de Registro de Entradas e Saídas para MEI
     */
    public FiscalReportDTO generateEntryExitReport(LocalDate startDate, LocalDate endDate, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        
        // Buscar compras (entradas) no período
        List<PurchaseOrder> purchases = purchaseOrderRepository.findByInventoryAndDateBetweenOrderByDate(
                inventory, startDate, endDate);
        List<PurchaseEntryDTO> entries = purchases.stream()
                .map(this::mapToPurchaseEntryDTO)
                .collect(Collectors.toList());
        
        // Buscar vendas (saídas) no período
        List<Sale> sales = saleRepository.findByInventoryAndDateBetweenOrderByDate(
                inventory, startDate, endDate);
        List<SaleExitDTO> exits = sales.stream()
                .map(this::mapToSaleExitDTO)
                .collect(Collectors.toList());
        
        // Calcular totais
        BigDecimal totalEntries = entries.stream()
                .map(PurchaseEntryDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalExits = exits.stream()
                .map(SaleExitDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal balance = totalExits.subtract(totalEntries);
        
        return FiscalReportDTO.builder()
                .reportPeriodStart(startDate)
                .reportPeriodEnd(endDate)
                .entries(entries)
                .exits(exits)
                .totalEntries(totalEntries)
                .totalExits(totalExits)
                .balance(balance)
                .build();
    }

    private PurchaseEntryDTO mapToPurchaseEntryDTO(PurchaseOrder purchase) {
        return PurchaseEntryDTO.builder()
                .date(purchase.getDate())
                .orderNumber(purchase.getOrderNumber())
                .invoiceNumber(purchase.getInvoiceNumber())
                .invoiceSeries(purchase.getInvoiceSeries())
                .accessKey(purchase.getAccessKey())
                .supplierName(purchase.getSupplierName())
                .supplierTaxId(purchase.getSupplierTaxId())
                .purchaseCategory(purchase.getPurchaseCategory())
                .totalAmount(purchase.getTotalAmount())
                .status(purchase.getStatus())
                .build();
    }

    private SaleExitDTO mapToSaleExitDTO(Sale sale) {
        return SaleExitDTO.builder()
                .date(sale.getDate().toLocalDate()) // Convert LocalDateTime to LocalDate
                .saleId(sale.getId())
                .customerName("N/A") // Not available in current Sale model
                .customerPhone("N/A") // Not available in current Sale model
                .totalAmount(sale.getTotalAmount())
                .paymentMethod(sale.getPayments() != null && !sale.getPayments().isEmpty() 
                    ? sale.getPayments().get(0).getPaymentMethod() 
                    : null) // Get first payment method
                .build();
    }
}