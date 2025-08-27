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

    // Campos essenciais da NF-e para controle fiscal MEI
    
    /**
     * Chave de acesso da NF-e (44 dígitos) - Identificação fiscal obrigatória
     */
    @Length(min = 44, max = 44, message = "Access key must have exactly 44 digits")
    private String accessKey;
    
    /**
     * Nome do fornecedor - Para controle de estoque
     */
    private String supplierName;
    
    /**
     * CNPJ ou CPF do fornecedor - Para identificação fiscal
     */
    private String supplierTaxId;
    
    /**
     * Categoria da compra (CNPJ MEI vs CPF Pessoal)
     */
    private PurchaseCategory purchaseCategory;
}
