package com.jaoow.helmetstore.nf.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderSummaryDTO {
    private String invoiceNumber;
    private String invoiceDate;
    private String purchaseOrderNumber;
    private double totalPrice;
    private List<OrderItemDTO> items;
    private List<String> itemsNotFound;
    
    // Campos adicionais da NF-e
    private String invoiceSeries;
    private String accessKey;
    private String supplierName;
    private String supplierTaxId;
}