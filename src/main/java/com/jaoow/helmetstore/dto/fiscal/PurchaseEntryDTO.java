package com.jaoow.helmetstore.dto.fiscal;

import com.jaoow.helmetstore.model.PurchaseCategory;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para entrada (compra) no relatório fiscal
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseEntryDTO {
    private LocalDate date;
    private String orderNumber;
    private String invoiceNumber;
    private String invoiceSeries;
    private String accessKey;
    private String supplierName;
    private String supplierTaxId;
    private PurchaseCategory purchaseCategory;
    private BigDecimal totalAmount;
    private PurchaseOrderStatus status;
}