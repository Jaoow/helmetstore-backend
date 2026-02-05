package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.AvailableMonthDTO;
import com.jaoow.helmetstore.dto.balance.FinancialSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.usecase.transaction.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.time.YearMonth;

/**
 * Transaction Service - Orchestrates transaction-related operations using use cases
 *
 * This service acts as a facade, delegating business logic to specific use cases.
 * Each use case encapsulates a single business operation with clear responsibilities.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    // Use Cases
    private final CreateManualTransactionUseCase createManualTransactionUseCase;
    private final RecordSaleTransactionUseCase recordSaleTransactionUseCase;
    private final RecordPurchaseOrderTransactionUseCase recordPurchaseOrderTransactionUseCase;
    private final UpdateTransactionUseCase updateTransactionUseCase;
    private final DeleteTransactionUseCase deleteTransactionUseCase;
    private final CreateRefundTransactionUseCase createRefundTransactionUseCase;
    private final RemoveSaleTransactionsUseCase removeSaleTransactionsUseCase;
    private final RemovePurchaseOrderTransactionsUseCase removePurchaseOrderTransactionsUseCase;
    private final CalculateFinancialMetricsUseCase calculateFinancialMetricsUseCase;

    public void createManualTransaction(TransactionCreateDTO dto, Principal principal) {
        createManualTransactionUseCase.execute(dto, principal);
    }

    public void recordTransactionFromSale(Sale sale, Principal principal) {
        recordSaleTransactionUseCase.execute(sale, principal);
    }

    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        recordPurchaseOrderTransactionUseCase.execute(purchaseOrder, principal);
    }

    public void updateTransaction(Long transactionId, TransactionCreateDTO dto, Principal principal) {
        updateTransactionUseCase.execute(transactionId, dto, principal);
    }

    public void deleteTransactionById(Long transactionId, Principal principal) {
        deleteTransactionUseCase.execute(transactionId, principal);
    }

    public void createRefundTransactionForCanceledItem(PurchaseOrder purchaseOrder, BigDecimal refundAmount,
                                                        String itemDescription, Principal principal) {
        createRefundTransactionUseCase.execute(purchaseOrder, refundAmount, itemDescription, principal);
    }

    public void removeTransactionLinkedToSale(Sale sale) {
        removeSaleTransactionsUseCase.execute(sale);
    }

    public void removeTransactionLinkedToPurchaseOrder(PurchaseOrder purchaseOrder) {
        removePurchaseOrderTransactionsUseCase.execute(purchaseOrder);
    }

    public BigDecimal calculateProfit(Principal principal) {
        return calculateFinancialMetricsUseCase.calculateProfit(principal);
    }

    public BigDecimal calculateCashFlow(Principal principal) {
        return calculateFinancialMetricsUseCase.calculateCashFlow(principal);
    }

    public FinancialSummaryDTO calculateFinancialSummary(Principal principal) {
        return calculateFinancialMetricsUseCase.calculateFinancialSummary(principal);
    }

    /**
     * Get available months with transaction counts (lightweight for UI month selectors).
     * This avoids loading full transaction data when populating month selection dropdowns.
     */
    public List<AvailableMonthDTO> getAvailableMonths(String userEmail) {
        List<Object[]> results = transactionRepository.findAvailableMonthsWithCount(userEmail);

        return results.stream()
                .map(row -> AvailableMonthDTO.builder()
                        .month(YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                        .transactionCount(((Number) row[2]).intValue())
                        .build())
                .toList();
    }
}
