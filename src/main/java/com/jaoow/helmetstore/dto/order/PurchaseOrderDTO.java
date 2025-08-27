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

    // Campos essenciais da NF-e para controle fiscal MEI
    
    /**
     * Chave de acesso da NF-e (44 dígitos) - Identificação fiscal obrigatória
     */
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
    
    /**
     * Caminho do arquivo PDF (DANFE) - Documento fiscal oficial
     */
    private String pdfFilePath;
    
    /**
     * Caminho do arquivo XML da NF-e - Contém todos os dados fiscais
     */
    private String xmlFilePath;
}
