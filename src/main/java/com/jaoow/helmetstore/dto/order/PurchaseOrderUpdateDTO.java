package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.PurchaseCategory;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderUpdateDTO {

    @Min(value = 3, message = "Order number must have at least 3 characters")
    private String orderNumber;

    private LocalDate date;

    @NotNull
    private PurchaseOrderStatus status;

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
