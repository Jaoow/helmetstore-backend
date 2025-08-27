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
    
    // Campos essenciais para controle de estoque e fiscal MEI
    private String accessKey;        // Identificação fiscal obrigatória
    private String supplierName;     // Nome do fornecedor
    private String supplierTaxId;    // CNPJ/CPF do fornecedor
}