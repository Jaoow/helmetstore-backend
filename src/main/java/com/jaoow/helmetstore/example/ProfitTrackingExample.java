package com.jaoow.helmetstore.example;

import com.jaoow.helmetstore.dto.balance.MonthlyProfitDTO;
import com.jaoow.helmetstore.dto.balance.ProfitSummaryDTO;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.service.ProfitTrackingService;
import com.jaoow.helmetstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Profit Tracking System Example
 * 
 * This class demonstrates the profit tracking system with:
 * - Monthly profit segmentation
 * - Accumulated profit tracking
 * - Profit-deducting transactions
 * - Comprehensive profit reporting
 */
@Component
@RequiredArgsConstructor
public class ProfitTrackingExample {

    private final ProfitTrackingService profitTrackingService;
    private final TransactionService transactionService;

    /**
     * Example: Show how to get comprehensive profit summary
     */
    public void demonstrateProfitSummary() {
        System.out.println("\n=== Profit Summary Example ===");
        System.out.println("API Endpoint: GET /account/profit-summary");
        System.out.println("Service: ProfitTrackingService.getProfitSummary()");
        System.out.println("Returns:");
        System.out.println("- Total bank balance");
        System.out.println("- Total cash balance");
        System.out.println("- Total balance");
        System.out.println("- Total profit from sales");
        System.out.println("- Total profit available for withdrawal");
        System.out.println("- Total profit-deducting transactions");
        System.out.println("- Monthly breakdown with accumulated profit");
    }

    /**
     * Example: Show how to get monthly profit breakdown
     */
    public void demonstrateMonthlyProfitBreakdown() {
        System.out.println("\n=== Monthly Profit Breakdown ===");
        System.out.println("API Endpoint: GET /account/profit/monthly");
        System.out.println("Service: ProfitTrackingService.getMonthlyProfitBreakdown()");
        System.out.println("Returns list of MonthlyProfitDTO with:");
        System.out.println("- Month (YearMonth)");
        System.out.println("- Bank account balance (cumulative)");
        System.out.println("- Cash account balance (cumulative)");
        System.out.println("- Total balance (cumulative)");
        System.out.println("- Monthly profit from sales");
        System.out.println("- Accumulated profit available for withdrawal");
        System.out.println("- Monthly profit-deducting transactions");
        System.out.println("- All profit-deducting transactions for the month");
    }

    /**
     * Example: Show how to get specific month profit
     */
    public void demonstrateSpecificMonthProfit() {
        System.out.println("\n=== Specific Month Profit ===");
        System.out.println("API Endpoint: GET /account/profit/monthly/{year}/{month}");
        System.out.println("Service: ProfitTrackingService.getMonthlyProfit()");
        System.out.println("Example: GET /account/profit/monthly/2024/1");
        System.out.println("Returns MonthlyProfitDTO with:");
        System.out.println("- Month details");
        System.out.println("- Account balances");
        System.out.println("- Monthly profit");
        System.out.println("- Profit-deducting transactions");
    }

    /**
     * Example: Show how to create profit-deducting transactions
     */
    public void demonstrateProfitDeductingTransactions() {
        System.out.println("\n=== Profit-Deducting Transactions ===");
        System.out.println("These transactions reduce the profit available for withdrawal:");

        // Rent payment example
        TransactionCreateDTO rentTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.RENT)
                .description("Monthly rent payment")
                .amount(new BigDecimal("800.00"))
                .paymentMethod(PaymentMethod.PIX)
                .reference("RENT#20241201")
                .build();

        System.out.println("1. Rent Payment:");
        System.out.println("   - Type: EXPENSE");
        System.out.println("   - Detail: RENT");
        System.out.println("   - Amount: $800.00");
        System.out.println("   - Effect: Deducts from profit");

        // Electricity bill example
        TransactionCreateDTO electricityTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.ELECTRICITY)
                .description("Monthly electricity bill")
                .amount(new BigDecimal("150.00"))
                .paymentMethod(PaymentMethod.PIX)
                .reference("ELECTRICITY#20241201")
                .build();

        System.out.println("2. Electricity Bill:");
        System.out.println("   - Type: EXPENSE");
        System.out.println("   - Detail: ELECTRICITY");
        System.out.println("   - Amount: $150.00");
        System.out.println("   - Effect: Deducts from profit");

        // Machine purchase example
        TransactionCreateDTO machineTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.MACHINE_PURCHASE)
                .description("New equipment purchase")
                .amount(new BigDecimal("2000.00"))
                .paymentMethod(PaymentMethod.PIX)
                .reference("MACHINE#20241201")
                .build();

        System.out.println("3. Machine Purchase:");
        System.out.println("   - Type: EXPENSE");
        System.out.println("   - Detail: MACHINE_PURCHASE");
        System.out.println("   - Amount: $2000.00");
        System.out.println("   - Effect: Deducts from profit");

        // Profit withdrawal example
        TransactionCreateDTO withdrawalTransaction = TransactionCreateDTO.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.PROFIT_WITHDRAWAL)
                .description("Owner profit withdrawal")
                .amount(new BigDecimal("1000.00"))
                .paymentMethod(PaymentMethod.PIX)
                .reference("WITHDRAWAL#20241201")
                .build();

        System.out.println("4. Profit Withdrawal:");
        System.out.println("   - Type: EXPENSE");
        System.out.println("   - Detail: PROFIT_WITHDRAWAL");
        System.out.println("   - Amount: $1000.00");
        System.out.println("   - Effect: Deducts from profit (owner taking money out)");
    }

    /**
     * Example: Show profit calculation logic
     */
    public void demonstrateProfitCalculation() {
        System.out.println("\n=== Profit Calculation Logic ===");
        System.out.println("Monthly Profit = Sales Profit (totalProfit from sales for the month)");
        System.out.println("Available Profit = Monthly Profit - All Withdrawals (except product purchases)");
        System.out.println();
        System.out.println("Example Scenario:");
        System.out.println("- Monthly sales profit: $5000.00");
        System.out.println("- Rent payment: $800.00");
        System.out.println("- Electricity bill: $150.00");
        System.out.println("- Machine purchase: $2000.00");
        System.out.println("- Owner withdrawal: $1000.00");
        System.out.println("- Product purchase: $300.00 (doesn't affect profit)");
        System.out.println();
        System.out.println("Calculation:");
        System.out.println("Monthly Profit = $5000.00");
        System.out.println("Deducting Expenses = $800.00 + $150.00 + $2000.00 + $1000.00 = $3950.00");
        System.out.println("Available Profit = $5000.00 - $3950.00 = $1050.00");
        System.out.println();
        System.out.println("Note: Product purchase ($300.00) doesn't affect profit calculation.");
        System.out.println("This means the owner can withdraw $1050.00 as profit.");
    }

    /**
     * Example: Show accumulated profit tracking
     */
    public void demonstrateAccumulatedProfitTracking() {
        System.out.println("\n=== Accumulated Profit Tracking ===");
        System.out.println("The system tracks accumulated profit month by month:");
        System.out.println();
        System.out.println("January:");
        System.out.println("- Monthly profit: $2000.00");
        System.out.println("- Monthly expenses: $800.00");
        System.out.println("- Available for withdrawal: $1200.00");
        System.out.println("- Accumulated balance: $1200.00");
        System.out.println();
        System.out.println("February:");
        System.out.println("- Monthly profit: $1500.00");
        System.out.println("- Monthly expenses: $600.00");
        System.out.println("- Available for withdrawal: $900.00");
        System.out.println("- Accumulated balance: $2100.00 ($1200 + $900)");
        System.out.println();
        System.out.println("March:");
        System.out.println("- Monthly profit: $3000.00");
        System.out.println("- Monthly expenses: $1200.00");
        System.out.println("- Available for withdrawal: $1800.00");
        System.out.println("- Accumulated balance: $3900.00 ($2100 + $1800)");
        System.out.println();
        System.out.println("If owner withdraws $2000.00 in March:");
        System.out.println("- Remaining accumulated balance: $1900.00");
        System.out.println("- This balance carries over to April");
    }

    /**
     * Example: Show API usage
     */
    public void demonstrateAPIUsage() {
        System.out.println("\n=== API Usage Examples ===");
        System.out.println();
        System.out.println("1. Get comprehensive profit summary:");
        System.out.println("   GET /account/profit-summary");
        System.out.println();
        System.out.println("2. Get monthly profit breakdown:");
        System.out.println("   GET /account/profit/monthly");
        System.out.println();
        System.out.println("3. Get specific month profit:");
        System.out.println("   GET /account/profit/monthly/2024/1");
        System.out.println();
        System.out.println("4. Create profit-deducting transaction:");
        System.out.println("   POST /account/transaction");
        System.out.println("   Body: {");
        System.out.println("     \"date\": \"2024-12-01T10:00:00\",");
        System.out.println("     \"type\": \"EXPENSE\",");
        System.out.println("     \"detail\": \"RENT\",");
        System.out.println("     \"description\": \"Monthly rent payment\",");
        System.out.println("     \"amount\": 800.00,");
        System.out.println("     \"paymentMethod\": \"PIX\",");
        System.out.println("     \"reference\": \"RENT#20241201\"");
        System.out.println("   }");
    }
}