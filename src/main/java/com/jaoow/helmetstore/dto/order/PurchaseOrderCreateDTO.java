package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.PurchaseCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderCreateDTO {

    @Length(min = 3, message = "Order number must have at least 3 characters")
    private String orderNumber;

    @NotNull(message = "Order date is required")
    private LocalDate date;

    @NotEmpty(message = "Order items are required")
    private List<PurchaseOrderItemDTO> items;

    @DecimalMin(value = "0.0", message = "Total amount must be greater than 0")
    private BigDecimal totalAmount;

    // Campos da NF-e (Nota Fiscal Eletrônica)
    
    /**
     * Número da NF-e
     */
    private String invoiceNumber;
    
    /**
     * Série da NF-e
     */
    private String invoiceSeries;
    
    /**
     * Chave de acesso da NF-e (44 dígitos)
     */
    @Length(min = 44, max = 44, message = "Access key must have exactly 44 digits")
    private String accessKey;
    
    /**
     * Nome do emitente/fornecedor
     */
    private String supplierName;
    
    /**
     * CNPJ ou CPF do emitente
     */
    private String supplierTaxId;
    
    /**
     * Categoria da compra (CNPJ MEI vs CPF Pessoal)
     */
    private PurchaseCategory purchaseCategory;
}
