package com.jaoow.helmetstore.model.balance;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime date;    // Data e hora da transação

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;  // INCOME ou EXPENSE

    private String description;    // Ex: "Venda #123", "Retirada Pró-labore"

    @Column(nullable = false)
    private BigDecimal amount;     // Valor que entra ou sai

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod; // Forma de pagamento: CASH, PIX, CARD

    private String reference;      // Ex: "SALE#123", "ORDER#45", "RETIRADA#20250601"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
}
