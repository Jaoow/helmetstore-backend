package com.jaoow.helmetstore.model;

import com.jaoow.helmetstore.model.inventory.Inventory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.validator.constraints.Length;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.INVOICED;

    @Column(columnDefinition = "DATE")
    private LocalDate date;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "purchaseOrder")
    private List<PurchaseOrderItem> items;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(optional = false)
    private Inventory inventory;

    // Campos da NF-e (Nota Fiscal Eletrônica)
    
    /**
     * Número da NF-e
     */
    @Column(name = "invoice_number")
    private String invoiceNumber;
    
    /**
     * Série da NF-e
     */
    @Column(name = "invoice_series")
    private String invoiceSeries;
    
    /**
     * Chave de acesso da NF-e (44 dígitos)
     */
    @Length(min = 44, max = 44, message = "Access key must have exactly 44 digits")
    @Column(name = "access_key", length = 44)
    private String accessKey;
    
    /**
     * Nome do emitente/fornecedor
     */
    @Column(name = "supplier_name")
    private String supplierName;
    
    /**
     * CNPJ ou CPF do emitente
     */
    @Column(name = "supplier_tax_id")
    private String supplierTaxId;
    
    /**
     * Categoria da compra (CNPJ MEI vs CPF Pessoal)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_category")
    @Builder.Default
    private PurchaseCategory purchaseCategory = PurchaseCategory.CNPJ_MEI;
    
    /**
     * Caminho do arquivo PDF (DANFE)
     */
    @Column(name = "pdf_file_path")
    private String pdfFilePath;
    
    /**
     * Caminho do arquivo XML da NF-e
     */
    @Column(name = "xml_file_path")
    private String xmlFilePath;
}
