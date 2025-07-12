package com.jaoow.helmetstore.example;

import com.jaoow.helmetstore.dto.balance.CashFlowSummaryDTO;
import com.jaoow.helmetstore.dto.balance.MonthlyCashFlowDTO;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.service.CashFlowService;
import com.jaoow.helmetstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Enhanced Cash Flow System Example
 * 
 * This class demonstrates the improved cash flow system with:
 * - Monthly segmentation
 * - Bank and Cash account balances
 * - Detailed transaction information
 * - Comprehensive financial reporting
 * - Cumulative balance tracking
 */
@Component
@RequiredArgsConstructor
public class EnhancedCashFlowExample {

    private final CashFlowService cashFlowService;
    private final TransactionService transactionService;

    /**
     * Example: Demonstrate the enhanced cash flow system
     */
    public void demonstrateEnhancedCashFlow() {
        System.out.println("\n=== Enhanced Cash Flow System ===");
        System.out.println("Features:");
        System.out.println("- Monthly segmentation");
        System.out.println("- Bank account balance tracking");
        System.out.println("- Cash account balance tracking");
        System.out.println("- All transactions for each month");
        System.out.println("- Comprehensive financial reporting");
        System.out.println("- Separate CashFlowService for better organization");
        System.out.println("- Cumulative balance tracking month by month");
    }

    /**
     * Example: Create sample transactions for demonstration
     */
    public void createSampleTransactions(String userEmail) {
        System.out.println("\n=== Creating Sample Transactions ===");
        
        // January 2024 transactions
        createTransaction(userEmail, "2024-01-15", TransactionType.INCOME, TransactionDetail.SALE, 
                "Sale of helmet #123", new BigDecimal("150.00"), PaymentMethod.PIX, "SALE#123");
        
        createTransaction(userEmail, "2024-01-20", TransactionType.EXPENSE, TransactionDetail.RENT, 
                "Monthly rent payment", new BigDecimal("800.00"), PaymentMethod.PIX, "RENT#202401");
        
        createTransaction(userEmail, "2024-01-25", TransactionType.EXPENSE, TransactionDetail.ELECTRICITY, 
                "Electricity bill", new BigDecimal("200.00"), PaymentMethod.PIX, "ELECTRICITY#202401");
        
        // February 2024 transactions
        createTransaction(userEmail, "2024-02-10", TransactionType.INCOME, TransactionDetail.SALE, 
                "Sale of helmet #124", new BigDecimal("200.00"), PaymentMethod.CASH, "SALE#124");
        
        createTransaction(userEmail, "2024-02-15", TransactionType.INCOME, TransactionDetail.OWNER_INVESTMENT, 
                "Owner investment", new BigDecimal("5000.00"), PaymentMethod.PIX, "INVESTMENT#001");
        
        createTransaction(userEmail, "2024-02-20", TransactionType.EXPENSE, TransactionDetail.PRODUCT_PURCHASE, 
                "Purchase inventory", new BigDecimal("1000.00"), PaymentMethod.PIX, "PURCHASE#456");
        
        // March 2024 transactions
        createTransaction(userEmail, "2024-03-05", TransactionType.INCOME, TransactionDetail.SALE, 
                "Sale of helmet #125", new BigDecimal("180.00"), PaymentMethod.CARD, "SALE#125");
        
        createTransaction(userEmail, "2024-03-10", TransactionType.EXPENSE, TransactionDetail.MACHINE_PURCHASE, 
                "Purchase equipment", new BigDecimal("1500.00"), PaymentMethod.PIX, "MACHINE#789");
        
        createTransaction(userEmail, "2024-03-25", TransactionType.EXPENSE, TransactionDetail.PROFIT_WITHDRAWAL, 
                "Profit withdrawal", new BigDecimal("500.00"), PaymentMethod.PIX, "WITHDRAWAL#002");
        
        System.out.println("Sample transactions created successfully!");
    }

    /**
     * Example: Demonstrate cumulative balance behavior
     */
    public void demonstrateCumulativeBalanceBehavior() {
        System.out.println("\n=== Cumulative Balance Behavior ===");
        System.out.println("Each month shows the correct balance at the end of that month:");
        System.out.println();
        
        System.out.println("January 2024:");
        System.out.println("  Income: $150.00 (Sale via PIX) → Bank: +$150.00");
        System.out.println("  Expenses: $800.00 (Rent via PIX) + $200.00 (Electricity via PIX) = $1000.00 → Bank: -$1000.00");
        System.out.println("  Monthly Cash Flow: $150.00 - $1000.00 = -$850.00");
        System.out.println("  End of January Balance: Bank: -$850.00, Cash: $0.00");
        System.out.println();
        
        System.out.println("February 2024:");
        System.out.println("  Starting Balance: Bank: -$850.00, Cash: $0.00");
        System.out.println("  Income: $200.00 (Sale via CASH) → Cash: +$200.00");
        System.out.println("  Income: $5000.00 (Investment via PIX) → Bank: +$5000.00");
        System.out.println("  Expenses: $1000.00 (Inventory via PIX) → Bank: -$1000.00");
        System.out.println("  Monthly Cash Flow: $5200.00 - $1000.00 = $4200.00");
        System.out.println("  End of February Balance: Bank: $3150.00 (-$850 + $5000 - $1000), Cash: $200.00");
        System.out.println();
        
        System.out.println("March 2024:");
        System.out.println("  Starting Balance: Bank: $3150.00, Cash: $200.00");
        System.out.println("  Income: $180.00 (Sale via CARD) → Bank: +$180.00");
        System.out.println("  Expenses: $1500.00 (Equipment via PIX) → Bank: -$1500.00");
        System.out.println("  Expenses: $500.00 (Withdrawal via PIX) → Bank: -$500.00");
        System.out.println("  Monthly Cash Flow: $180.00 - $2000.00 = -$1820.00");
        System.out.println("  End of March Balance: Bank: $1330.00 ($3150 + $180 - $1500 - $500), Cash: $200.00");
        System.out.println();
        
        System.out.println("This ensures each month shows the cumulative balance");
        System.out.println("at the end of that month, with proper separation of bank and cash accounts.");
    }

    /**
     * Example: Show payment method to account mapping
     */
    public void demonstratePaymentMethodToAccountMapping() {
        System.out.println("\n=== Payment Method to Account Mapping ===");
        System.out.println("CASH transactions → Cash Account Balance");
        System.out.println("PIX transactions → Bank Account Balance");
        System.out.println("CARD transactions → Bank Account Balance");
        System.out.println();
        
        System.out.println("Example transactions and their account impact:");
        System.out.println("  Sale via CASH: +$200.00 → Cash Account: +$200.00");
        System.out.println("  Sale via PIX: +$150.00 → Bank Account: +$150.00");
        System.out.println("  Sale via CARD: +$180.00 → Bank Account: +$180.00");
        System.out.println("  Rent via PIX: -$800.00 → Bank Account: -$800.00");
        System.out.println("  Equipment via PIX: -$1500.00 → Bank Account: -$1500.00");
        System.out.println();
        
        System.out.println("This provides accurate tracking of:");
        System.out.println("  - Bank account liquidity");
        System.out.println("  - Cash on hand");
        System.out.println("  - Total financial position");
    }

    /**
     * Example: Show how to get comprehensive cash flow summary
     */
    public void demonstrateCashFlowSummary() {
        System.out.println("\n=== Cash Flow Summary Example ===");
        System.out.println("API Endpoint: GET /account/cash-flow-summary");
        System.out.println("Service: CashFlowService.getCashFlowSummary()");
        System.out.println("Returns:");
        System.out.println("- Total bank balance");
        System.out.println("- Total cash balance");
        System.out.println("- Total balance");
        System.out.println("- Total income");
        System.out.println("- Total expense");
        System.out.println("- Total cash flow");
        System.out.println("- Monthly breakdown with cumulative balances");
    }

    /**
     * Example: Show how to get monthly cash flow breakdown
     */
    public void demonstrateMonthlyBreakdown() {
        System.out.println("\n=== Monthly Cash Flow Breakdown ===");
        System.out.println("API Endpoint: GET /account/cash-flow/monthly");
        System.out.println("Service: CashFlowService.getMonthlyCashFlowBreakdown()");
        System.out.println("Returns list of MonthlyCashFlowDTO with:");
        System.out.println("- Month (YearMonth)");
        System.out.println("- Bank account balance (cumulative)");
        System.out.println("- Cash account balance (cumulative)");
        System.out.println("- Total balance (cumulative)");
        System.out.println("- Monthly income");
        System.out.println("- Monthly expense");
        System.out.println("- Monthly cash flow");
        System.out.println("- All transactions for the month");
        System.out.println("- Cumulative balance at end of month");
    }

    /**
     * Example: Show how to get specific month cash flow
     */
    public void demonstrateSpecificMonth() {
        System.out.println("\n=== Specific Month Cash Flow ===");
        System.out.println("API Endpoint: GET /account/cash-flow/monthly/{year}/{month}");
        System.out.println("Service: CashFlowService.getMonthlyCashFlow()");
        System.out.println("Example: GET /account/cash-flow/monthly/2024/1");
        System.out.println("Returns MonthlyCashFlowDTO for January 2024");
        System.out.println("with cumulative balance at end of that month");
    }

    /**
     * Example: Show the structure of MonthlyCashFlowDTO
     */
    public void showMonthlyCashFlowStructure() {
        System.out.println("\n=== MonthlyCashFlowDTO Structure ===");
        System.out.println("MonthlyCashFlowDTO {");
        System.out.println("  month: YearMonth (e.g., 2024-01)");
        System.out.println("  bankAccountBalance: BigDecimal (cumulative at end of month)");
        System.out.println("  cashAccountBalance: BigDecimal (cumulative at end of month)");
        System.out.println("  totalBalance: BigDecimal (cumulative at end of month)");
        System.out.println("  monthlyIncome: BigDecimal");
        System.out.println("  monthlyExpense: BigDecimal");
        System.out.println("  monthlyCashFlow: BigDecimal");
        System.out.println("  transactions: List<TransactionInfo>");
        System.out.println("}");
    }

    /**
     * Example: Show the structure of CashFlowSummaryDTO
     */
    public void showCashFlowSummaryStructure() {
        System.out.println("\n=== CashFlowSummaryDTO Structure ===");
        System.out.println("CashFlowSummaryDTO {");
        System.out.println("  totalBankBalance: BigDecimal");
        System.out.println("  totalCashBalance: BigDecimal");
        System.out.println("  totalBalance: BigDecimal");
        System.out.println("  totalIncome: BigDecimal");
        System.out.println("  totalExpense: BigDecimal");
        System.out.println("  totalCashFlow: BigDecimal");
        System.out.println("  monthlyBreakdown: List<MonthlyCashFlowDTO> (with cumulative balances)");
        System.out.println("}");
    }

    /**
     * Example: Show sample API responses with cumulative balances
     */
    public void showSampleApiResponses() {
        System.out.println("\n=== Sample API Responses with Cumulative Balances ===");
        
        System.out.println("GET /account/cash-flow-summary");
        System.out.println("Response:");
        System.out.println("{");
        System.out.println("  \"totalBankBalance\": 1330.00,");
        System.out.println("  \"totalCashBalance\": 200.00,");
        System.out.println("  \"totalBalance\": 1530.00,");
        System.out.println("  \"totalIncome\": 5530.00,");
        System.out.println("  \"totalExpense\": 4000.00,");
        System.out.println("  \"totalCashFlow\": 1530.00,");
        System.out.println("  \"monthlyBreakdown\": [");
        System.out.println("    {");
        System.out.println("      \"month\": \"2024-01\",");
        System.out.println("      \"bankAccountBalance\": -850.00,  // Cumulative at end of January");
        System.out.println("      \"cashAccountBalance\": 0.00,");
        System.out.println("      \"totalBalance\": -850.00,");
        System.out.println("      \"monthlyIncome\": 150.00,");
        System.out.println("      \"monthlyExpense\": 1000.00,");
        System.out.println("      \"monthlyCashFlow\": -850.00,");
        System.out.println("      \"transactions\": [...]");
        System.out.println("    },");
        System.out.println("    {");
        System.out.println("      \"month\": \"2024-02\",");
        System.out.println("      \"bankAccountBalance\": 3150.00,  // Cumulative at end of February");
        System.out.println("      \"cashAccountBalance\": 200.00,   // Cash from CASH transactions");
        System.out.println("      \"totalBalance\": 3350.00,");
        System.out.println("      \"monthlyIncome\": 5200.00,");
        System.out.println("      \"monthlyExpense\": 1000.00,");
        System.out.println("      \"monthlyCashFlow\": 4200.00,");
        System.out.println("      \"transactions\": [...]");
        System.out.println("    },");
        System.out.println("    {");
        System.out.println("      \"month\": \"2024-03\",");
        System.out.println("      \"bankAccountBalance\": 1330.00,  // Cumulative at end of March");
        System.out.println("      \"cashAccountBalance\": 200.00,   // Cash balance unchanged");
        System.out.println("      \"totalBalance\": 1530.00,");
        System.out.println("      \"monthlyIncome\": 180.00,");
        System.out.println("      \"monthlyExpense\": 2000.00,");
        System.out.println("      \"monthlyCashFlow\": -1820.00,");
        System.out.println("      \"transactions\": [...]");
        System.out.println("    }");
        System.out.println("  ]");
        System.out.println("}");
        System.out.println();
        System.out.println("Note: Bank and cash balances are properly separated based on payment methods.");
    }

    /**
     * Example: Show business benefits
     */
    public void showBusinessBenefits() {
        System.out.println("\n=== Business Benefits ===");
        System.out.println("1. Monthly Financial Tracking");
        System.out.println("   - See cash flow trends over time");
        System.out.println("   - Identify seasonal patterns");
        System.out.println("   - Plan for future expenses");
        
        System.out.println("\n2. Account Balance Management");
        System.out.println("   - Track bank vs cash balances");
        System.out.println("   - Optimize cash management");
        System.out.println("   - Monitor liquidity");
        
        System.out.println("\n3. Detailed Transaction Analysis");
        System.out.println("   - Review all transactions by month");
        System.out.println("   - Identify spending patterns");
        System.out.println("   - Track revenue sources");
        
        System.out.println("\n4. Comprehensive Reporting");
        System.out.println("   - Total financial position");
        System.out.println("   - Monthly breakdowns");
        System.out.println("   - Historical analysis");
        
        System.out.println("\n5. Better Code Organization");
        System.out.println("   - Separate CashFlowService");
        System.out.println("   - ModelMapper for DTO conversion");
        System.out.println("   - Clean separation of concerns");
        
        System.out.println("\n6. Cumulative Balance Tracking");
        System.out.println("   - Accurate month-end balances");
        System.out.println("   - Historical balance progression");
        System.out.println("   - Proper financial reporting");
    }

    /**
     * Example: Show service separation benefits
     */
    public void showServiceSeparationBenefits() {
        System.out.println("\n=== Service Separation Benefits ===");
        System.out.println("TransactionService:");
        System.out.println("  - Create, update, delete transactions");
        System.out.println("  - Record transactions from sales/purchases");
        System.out.println("  - Calculate profit");
        System.out.println("  - Basic cash flow calculation");
        
        System.out.println("\nCashFlowService:");
        System.out.println("  - Monthly cash flow analysis");
        System.out.println("  - Account balance tracking");
        System.out.println("  - Comprehensive financial reporting");
        System.out.println("  - Historical data analysis");
        System.out.println("  - Cumulative balance calculations");
        
        System.out.println("\nBenefits:");
        System.out.println("  - Single Responsibility Principle");
        System.out.println("  - Better maintainability");
        System.out.println("  - Easier testing");
        System.out.println("  - Clear separation of concerns");
        System.out.println("  - Accurate financial reporting");
    }

    /**
     * Helper method to create a transaction
     */
    private void createTransaction(String userEmail, String dateStr, TransactionType type, 
                                 TransactionDetail detail, String description, BigDecimal amount, 
                                 PaymentMethod paymentMethod, String reference) {
        LocalDateTime date = LocalDateTime.parse(dateStr + "T10:00:00");
        
        TransactionCreateDTO transaction = TransactionCreateDTO.builder()
                .date(date)
                .type(type)
                .detail(detail)
                .description(description)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .reference(reference)
                .build();

        // In a real application, you would use Principal instead of userEmail
        // transactionService.createManualTransaction(transaction, principal);
        System.out.printf("Created transaction: %s - %s - %s%n", 
                dateStr, type.name(), amount);
    }
} 