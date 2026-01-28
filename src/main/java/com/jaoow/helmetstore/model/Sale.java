package com.jaoow.helmetstore.model;

import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SalePayment;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(indexes = {
    @Index(name = "idx_sale_date", columnList = "date"),
    @Index(name = "idx_sale_inventory_date", columnList = "inventory_id, date"),
    @Index(name = "idx_sale_status", columnList = "status")
})
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime date;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 5)
    private List<SaleItem> items;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalProfit;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 3)
    private List<SalePayment> payments;

    @ManyToOne(optional = false)
    private Inventory inventory;

    // ============================================================================
    // CANCELLATION FIELDS
    // ============================================================================

    /**
     * Status da venda (FINALIZADA, CANCELADA, CANCELADA_PARCIAL)
     * O status indica a situação comercial da operação
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    /**
     * Data em que a venda foi cancelada (total ou parcialmente)
     */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * Usuário responsável pelo cancelamento (nome ou email)
     */
    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    /**
     * Motivo do cancelamento
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 50)
    private CancellationReason cancellationReason;

    /**
     * Observações adicionais sobre o cancelamento
     */
    @Column(name = "cancellation_notes", columnDefinition = "TEXT")
    private String cancellationNotes;

    // ============================================================================
    // REFUND FIELDS (ESTORNO)
    // ============================================================================

    /**
     * Flag indicando se houve estorno (reembolso)
     */
    @Column(name = "has_refund", nullable = false)
    @Builder.Default
    private Boolean hasRefund = false;

    /**
     * Valor do estorno (pode ser total ou parcial)
     */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /**
     * Método de reembolso
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_payment_method", length = 20)
    private PaymentMethod refundPaymentMethod;

    /**
     * ID da transação de reembolso (referência para a Transaction de saída)
     */
    @Column(name = "refund_transaction_id")
    private Long refundTransactionId;

}
