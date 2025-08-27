package com.jaoow.helmetstore.dto.order;

import com.jaoow.helmetstore.model.PurchaseCategory;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDTO {

    private Long id;
    private String orderNumber;
    @Builder.Default
    private LocalDate date = LocalDate.now();
    private PurchaseOrderStatus status;
    private List<PurchaseOrderItemDTO> items;
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
    
    /**
     * Caminho do arquivo PDF (DANFE)
     */
    private String pdfFilePath;
    
    /**
     * Caminho do arquivo XML da NF-e
     */
    private String xmlFilePath;
}
