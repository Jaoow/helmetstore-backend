package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.usecase.transaction.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

/**
 * Transaction Service - Orchestrates transaction-related operations using use cases
 *
 * This service acts as a facade, delegating business logic to specific use cases.
 * Each use case encapsulates a single business operation with clear responsibilities.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    // Use Cases
    private final CreateManualTransactionUseCase createManualTransactionUseCase;
    private final UpdateTransactionUseCase updateTransactionUseCase;
    private final DeleteTransactionUseCase deleteTransactionUseCase;
    private final RecordTransactionFromSaleUseCase recordTransactionFromSaleUseCase;
    private final RecordTransactionFromPurchaseOrderUseCase recordTransactionFromPurchaseOrderUseCase;
    private final RemoveTransactionLinkedToPurchaseOrderUseCase removeTransactionLinkedToPurchaseOrderUseCase;
    private final RemoveTransactionLinkedToSaleUseCase removeTransactionLinkedToSaleUseCase;
    private final CreateRefundTransactionForCanceledItemUseCase createRefundTransactionForCanceledItemUseCase;
    private final CalculateProfitUseCase calculateProfitUseCase;
    private final CalculateCashFlowUseCase calculateCashFlowUseCase;
    private final GetAvailableMonthsUseCase getAvailableMonthsUseCase;
    private final CalculateFinancialSummaryUseCase calculateFinancialSummaryUseCase;

    public void createManualTransaction(TransactionCreateDTO dto, Principal principal) {
        createManualTransactionUseCase.execute(dto, principal);
    }

    public void updateTransaction(Long transactionId, TransactionCreateDTO dto, Principal principal) {
        updateTransactionUseCase.execute(transactionId, dto, principal);
    }

    public void deleteTransactionById(Long transactionId, Principal principal) {
        deleteTransactionUseCase.execute(transactionId, principal);
    }

    public void recordTransactionFromSale(Sale sale, Principal principal) {
        recordTransactionFromSaleUseCase.execute(sale, principal);
    }

    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        recordTransactionFromPurchaseOrderUseCase.execute(purchaseOrder, principal);
    }

    public void removeTransactionLinkedToPurchaseOrder(PurchaseOrder purchaseOrder) {
        removeTransactionLinkedToPurchaseOrderUseCase.execute(purchaseOrder);
    }

    public void removeTransactionLinkedToSale(Sale sale) {
        removeTransactionLinkedToSaleUseCase.execute(sale);
    }

    public void createRefundTransactionForCanceledItem(PurchaseOrder purchaseOrder, BigDecimal refundAmount,
            String itemDescription, Principal principal) {
        createRefundTransactionForCanceledItemUseCase.execute(purchaseOrder, refundAmount, itemDescription, principal);
    }

    public BigDecimal calculateProfit(Principal principal) {
        return calculateProfitUseCase.execute(principal);
    }

    public BigDecimal calculateCashFlow(Principal principal) {
        return calculateCashFlowUseCase.execute(principal);
    }

    public List<com.jaoow.helmetstore.dto.balance.AvailableMonthDTO> getAvailableMonths(String userEmail) {
        return getAvailableMonthsUseCase.execute(userEmail);
    }

    public FinancialSummary calculateFinancialSummary(Principal principal) {
        CalculateFinancialSummaryUseCase.FinancialSummary summary = calculateFinancialSummaryUseCase.execute(principal);
        return FinancialSummary.builder()
                .profit(summary.getProfit())
                .cashFlow(summary.getCashFlow())
                .build();
    }

    /**
     * Financial summary data class
     */
    @lombok.Data
    @lombok.Builder
    public static class FinancialSummary {
        private BigDecimal profit;
        private BigDecimal cashFlow;
    }
}
