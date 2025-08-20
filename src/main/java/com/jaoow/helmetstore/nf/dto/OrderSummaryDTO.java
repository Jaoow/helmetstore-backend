package com.jaoow.helmetstore.nf.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {
    private String invoiceNumber;
    private String invoiceDate;
    private String purchaseOrderNumber;
    private double totalPrice;
    private List<OrderItemDTO> items;
    private List<String> itemsNotFound;
}