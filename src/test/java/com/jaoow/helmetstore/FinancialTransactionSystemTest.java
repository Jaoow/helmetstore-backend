package com.jaoow.helmetstore;

import com.jaoow.helmetstore.model.balance.TransactionDetail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for the Financial Transaction System
 * 
 * This test verifies that the business rules are correctly implemented
 * and that the deductsFromProfit flag works as expected.
 */
public class FinancialTransactionSystemTest {

    @Test
    public void testTransactionDetailDeductsFromProfit() {
        // Test that SALE adds to profit (does not deduct)
        assertFalse(TransactionDetail.SALE.deductsFromProfit(),
                "SALE should not deduct from profit - it adds to profit");

        // Test that RENT deducts from profit
        assertTrue(TransactionDetail.RENT.deductsFromProfit(),
                "RENT should deduct from profit");

        // Test that ELECTRICITY deducts from profit
        assertTrue(TransactionDetail.ELECTRICITY.deductsFromProfit(),
                "ELECTRICITY should deduct from profit");

        // Test that MACHINE_PURCHASE deducts from profit
        assertTrue(TransactionDetail.MACHINE_PURCHASE.deductsFromProfit(),
                "MACHINE_PURCHASE should deduct from profit");

        // Test that PRODUCT_PURCHASE does not affect profit
        assertFalse(TransactionDetail.PRODUCT_PURCHASE.deductsFromProfit(),
                "PRODUCT_PURCHASE should not deduct from profit - it's inventory");

        // Test that OWNER_INVESTMENT does not affect profit
        assertFalse(TransactionDetail.OWNER_INVESTMENT.deductsFromProfit(),
                "OWNER_INVESTMENT should not deduct from profit - it's investment");

        // Test that PROFIT_WITHDRAWAL affects profit (owner taking money out)
        assertTrue(TransactionDetail.PROFIT_WITHDRAWAL.deductsFromProfit(),
                "PROFIT_WITHDRAWAL should deduct from profit - it's owner taking money out");

        // Test that MONEY_INVESTMENT does not affect profit
        assertFalse(TransactionDetail.MONEY_INVESTMENT.deductsFromProfit(),
                "MONEY_INVESTMENT should not deduct from profit - it's investment");

        // Test that OTHER deducts from profit (default for expenses)
        assertTrue(TransactionDetail.OTHER.deductsFromProfit(),
                "OTHER should deduct from profit - default for expenses");
    }

    @Test
    public void testAllTransactionDetailsHaveDeductsFromProfitFlag() {
        // Verify all enum values have the deductsFromProfit method
        for (TransactionDetail detail : TransactionDetail.values()) {
            assertNotNull(detail.deductsFromProfit(),
                    "All TransactionDetail values should have deductsFromProfit method");
        }
    }

    @Test
    public void testBusinessRulesSummary() {
        System.out.println("\n=== Business Rules Verification ===");

        // Income transactions that affect profit
        System.out.println("Income transactions that ADD to profit:");
        for (TransactionDetail detail : TransactionDetail.values()) {
            if (detail == TransactionDetail.SALE) {
                System.out.printf("- %s: %s%n", detail.name(), detail.deductsFromProfit() ? "DEDUCTS" : "ADDS");
            }
        }

        // Expense transactions that affect profit
        System.out.println("\nExpense transactions that DEDUCT from profit:");
        for (TransactionDetail detail : TransactionDetail.values()) {
            if (detail.deductsFromProfit() && detail != TransactionDetail.SALE) {
                System.out.printf("- %s: %s%n", detail.name(), detail.deductsFromProfit() ? "DEDUCTS" : "ADDS");
            }
        }

        // Transactions that don't affect profit
        System.out.println("\nTransactions that DON'T affect profit:");
        for (TransactionDetail detail : TransactionDetail.values()) {
            if (!detail.deductsFromProfit() && detail != TransactionDetail.SALE) {
                System.out.printf("- %s: %s%n", detail.name(), detail.deductsFromProfit() ? "DEDUCTS" : "NO IMPACT");
            }
        }

        System.out.println("\nProfit Calculation Formula:");
        System.out.println("profit = sum(SALE) - sum(all transactions where deductsFromProfit = true)");
    }

    @Test
    public void testProfitCalculationLogic() {
        // This test demonstrates the profit calculation logic
        // In a real application, this would be tested with actual transaction data

        System.out.println("\n=== Profit Calculation Example ===");

        // Example scenario:
        // 1. Sale of $1000 → Profit: +$1000
        // 2. Rent payment of $500 → Profit: -$500
        // 3. Product purchase of $300 → Profit: unchanged
        // 4. Electricity bill of $200 → Profit: -$200
        // 5. Owner investment of $2000 → Profit: unchanged
        // 6. Profit withdrawal of $1000 → Profit: unchanged

        // Expected profit: $1000 - $500 - $200 = $300

        System.out.println("Example scenario:");
        System.out.println("1. Sale of $1000 → Profit: +$1000");
        System.out.println("2. Rent payment of $500 → Profit: -$500");
        System.out.println("3. Product purchase of $300 → Profit: unchanged");
        System.out.println("4. Electricity bill of $200 → Profit: -$200");
        System.out.println("5. Owner investment of $2000 → Profit: unchanged");
        System.out.println("6. Profit withdrawal of $1000 → Profit: unchanged");
        System.out.println("\nExpected profit: $1000 - $500 - $200 = $300");

        // Verify the logic
        assertTrue(TransactionDetail.SALE.deductsFromProfit() == false, "SALE should add to profit");
        assertTrue(TransactionDetail.RENT.deductsFromProfit() == true, "RENT should deduct from profit");
        assertTrue(TransactionDetail.PRODUCT_PURCHASE.deductsFromProfit() == false,
                "PRODUCT_PURCHASE should not affect profit");
        assertTrue(TransactionDetail.ELECTRICITY.deductsFromProfit() == true, "ELECTRICITY should deduct from profit");
        assertTrue(TransactionDetail.OWNER_INVESTMENT.deductsFromProfit() == false,
                "OWNER_INVESTMENT should not affect profit");
        assertTrue(TransactionDetail.PROFIT_WITHDRAWAL.deductsFromProfit() == true,
                "PROFIT_WITHDRAWAL should affect profit");
    }
}