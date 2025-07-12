package com.jaoow.helmetstore.example;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Example usage of the Financial Transaction System
 * 
 * This class demonstrates how to use the enhanced transaction system
 * with proper profit calculation and cash flow tracking.
 */
@Component
@RequiredArgsConstructor
public class FinancialTransactionExample {

    private final TransactionService transactionService;

    /**
     * Example: Record a sale transaction
     * SALE adds to profit
     */
    public void recordSale(String userEmail, BigDecimal amount) {
        TransactionCreateDTO saleTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Sale of helmet #123")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("SALE#123")
                .build();

        // In a real application, you would use Principal instead of userEmail
        // transactionService.createManualTransaction(saleTransaction, principal);
        System.out.println("Sale recorded: " + amount + " - Adds to profit");
    }

    /**
     * Example: Record rent payment
     * RENT deducts from profit
     */
    public void recordRent(String userEmail, BigDecimal amount) {
        TransactionCreateDTO rentTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.RENT)
                .description("Monthly rent payment")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("RENT#20241201")
                .build();

        // transactionService.createManualTransaction(rentTransaction, principal);
        System.out.println("Rent recorded: " + amount + " - Deducts from profit");
    }

    /**
     * Example: Record electricity bill
     * ELECTRICITY deducts from profit
     */
    public void recordElectricity(String userEmail, BigDecimal amount) {
        TransactionCreateDTO electricityTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.ELECTRICITY)
                .description("Electricity bill")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("ELECTRICITY#20241201")
                .build();

        // transactionService.createManualTransaction(electricityTransaction, principal);
        System.out.println("Electricity recorded: " + amount + " - Deducts from profit");
    }

    /**
     * Example: Record product purchase (stock)
     * PRODUCT_PURCHASE does not affect profit directly
     */
    public void recordProductPurchase(String userEmail, BigDecimal amount) {
        TransactionCreateDTO purchaseTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.PRODUCT_PURCHASE)
                .description("Purchase of helmets for inventory")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("PURCHASE#456")
                .build();

        // transactionService.createManualTransaction(purchaseTransaction, principal);
        System.out.println("Product purchase recorded: " + amount + " - Does not affect profit directly");
    }

    /**
     * Example: Record machine purchase
     * MACHINE_PURCHASE deducts from profit
     */
    public void recordMachinePurchase(String userEmail, BigDecimal amount) {
        TransactionCreateDTO machineTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.MACHINE_PURCHASE)
                .description("Purchase of new equipment")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("MACHINE#789")
                .build();

        // transactionService.createManualTransaction(machineTransaction, principal);
        System.out.println("Machine purchase recorded: " + amount + " - Deducts from profit");
    }

    /**
     * Example: Record owner investment
     * OWNER_INVESTMENT adds cash but does not affect profit
     */
    public void recordOwnerInvestment(String userEmail, BigDecimal amount) {
        TransactionCreateDTO investmentTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.OWNER_INVESTMENT)
                .description("Owner investment in business")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("INVESTMENT#001")
                .build();

        // transactionService.createManualTransaction(investmentTransaction, principal);
        System.out.println("Owner investment recorded: " + amount + " - Adds cash but does not affect profit");
    }

    /**
     * Example: Record profit withdrawal
     * PROFIT_WITHDRAWAL reduces cash but does not deduct from profit
     */
    public void recordProfitWithdrawal(String userEmail, BigDecimal amount) {
        TransactionCreateDTO withdrawalTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.PROFIT_WITHDRAWAL)
                .description("Profit withdrawal by owner")
                .amount(amount)
                .paymentMethod(PaymentMethod.PIX)
                .reference("WITHDRAWAL#002")
                .build();

        // transactionService.createManualTransaction(withdrawalTransaction, principal);
        System.out.println("Profit withdrawal recorded: " + amount + " - Reduces cash but does not deduct from profit");
    }

    /**
     * Example: Demonstrate profit calculation
     * 
     * Business Rules:
     * - SALE adds to profit
     * - RENT, ELECTRICITY, MACHINE_PURCHASE deduct from profit
     * - PRODUCT_PURCHASE does not affect profit directly
     * - OWNER_INVESTMENT adds cash but does not affect profit
     * - PROFIT_WITHDRAWAL reduces cash but does not deduct from profit
     */
    public void demonstrateProfitCalculation() {
        System.out.println("\n=== Financial Transaction System Example ===");
        System.out.println("Business Rules:");
        System.out.println("- SALE adds to profit");
        System.out.println("- RENT, ELECTRICITY, MACHINE_PURCHASE deduct from profit");
        System.out.println("- PRODUCT_PURCHASE does not affect profit directly");
        System.out.println("- OWNER_INVESTMENT adds cash but does not affect profit");
        System.out.println("- PROFIT_WITHDRAWAL reduces cash but does not deduct from profit");
        
        System.out.println("\nProfit Calculation Formula:");
        System.out.println("profit = sum(SALE) - sum(all transactions where deductsFromProfit = true)");
        
        System.out.println("\nCash Flow Calculation:");
        System.out.println("cashFlow = sum(INCOME) - sum(EXPENSE)");
        
        System.out.println("\nTransactionDetail enum values with deductsFromProfit flag:");
        for (TransactionDetail detail : TransactionDetail.values()) {
            System.out.printf("- %s: deductsFromProfit = %s%n", 
                detail.name(), detail.deductsFromProfit());
        }
    }

    /**
     * Example: Show how to use the API endpoints
     */
    public void demonstrateApiUsage() {
        System.out.println("\n=== API Usage Examples ===");
        System.out.println("GET /account/financial-summary - Get both profit and cash flow");
        System.out.println("GET /account/profit - Get profit calculation only");
        System.out.println("GET /account/cash-flow - Get cash flow calculation only");
        System.out.println("POST /account/transaction - Create a new transaction");
        System.out.println("PUT /account/transaction/{id} - Update an existing transaction");
        System.out.println("DELETE /account/transaction/{id} - Delete a transaction");
    }
} 