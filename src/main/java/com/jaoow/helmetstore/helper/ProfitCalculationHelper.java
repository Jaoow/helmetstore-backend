package com.jaoow.helmetstore.helper;

import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Unified Profit Calculation Helper
 * <p>
 * This is the SINGLE SOURCE OF TRUTH for all profit calculations in the system.
 * All other services should delegate to this helper rather than calculating profit themselves.
 * <p>
 * Key Principles:
 * - Net Profit is calculated from the LEDGER (transactions with affectsProfit=true)
 * - Gross Profit is calculated from SALES (revenue - COGS only)
 * - The ledger is the authoritative source for business profitability
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfitCalculationHelper {

    private final TransactionRepository transactionRepository;
    private final SaleRepository saleRepository;

    // ============================================================================
    // NET PROFIT CALCULATIONS (from Ledger)
    // ============================================================================

    /**
     * Calculate TOTAL Net Profit from all time.
     * <p>
     * This is the TRUE profit of the business, including:
     * - Revenue from Sales (+)
     * - Cost of Goods Sold (-)
     * - ALL Operational Expenses (-) [Rent, Energy, Broken Stock, etc.]
     * <p>
     * Excludes:
     * - Stock Purchases (asset transfer, not an expense)
     * - Owner Investments (capital, not revenue)
     * - Internal Transfers (wallet movement)
     *
     * @param userEmail User's email
     * @return Net Profit (can be negative if business is losing money)
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_CALCULATION, key = "#userEmail")
    public BigDecimal calculateTotalNetProfit(String userEmail) {
        log.debug("Calculating total net profit for user: {}", userEmail);

        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(userEmail);

        BigDecimal netProfit = transactions.stream()
            .filter(Transaction::isAffectsProfit) // All profit-affecting transactions
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Total net profit for {}: {}", userEmail, netProfit);
        return netProfit;
    }

    /**
     * Calculate Net Profit from a list of transactions.
     * <p>
     * Useful when you already have the transactions loaded in memory.
     *
     * @param transactions List of transactions
     * @return Net Profit
     */
    public BigDecimal calculateNetProfitFromTransactions(List<Transaction> transactions) {
        log.debug("Calculating net profit from {} transactions", transactions.size());

        return transactions.stream()
            .filter(Transaction::isAffectsProfit)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ============================================================================
    // GROSS PROFIT CALCULATIONS (from Sales)
    // ============================================================================

    /**
     * Calculate TOTAL Gross Profit from all sales.
     * <p>
     * Gross Profit = Revenue - Cost of Goods Sold (COGS)
     * <p>
     * This DOES NOT include operational expenses like:
     * - Rent
     * - Energy
     * - Broken Stock
     * - Taxes
     * <p>
     * Use this ONLY for product margin analysis.
     * For business profitability, use calculateTotalNetProfit().
     *
     * @param inventory User's inventory
     * @return Gross Profit (Revenue - COGS only)
     */
    public BigDecimal calculateTotalGrossProfit(Inventory inventory) {
        log.debug("Calculating total gross profit for inventory: {}", inventory.getId());

        return saleRepository.getTotalGrossProfit(inventory);
    }

    /**
     * Calculate Gross Profit for a specific date range.
     *
     * @param inventory User's inventory
     * @param startDate Start of period
     * @param endDate   End of period
     * @return Gross Profit for the period
     */
    public BigDecimal calculateGrossProfitByDateRange(Inventory inventory, LocalDateTime startDate,
                                                      LocalDateTime endDate) {
        log.debug("Calculating gross profit for inventory {} from {} to {}", inventory.getId(), startDate, endDate);

        return saleRepository.getTotalProfitByDateRange(inventory, startDate, endDate);
    }

    // ============================================================================
    // OPERATIONAL EXPENSES CALCULATIONS
    // ============================================================================

    /**
     * Calculate TOTAL Operational Expenses (excluding COGS).
     * <p>
     * This includes:
     * - Rent (FIXED_EXPENSE)
     * - Energy (FIXED_EXPENSE)
     * - Marketing (VARIABLE_EXPENSE)
     * - Broken Stock (OTHER_EXPENSE)
     * - Taxes (TAX)
     * <p>
     * This EXCLUDES:
     * - Cost of Goods Sold (COGS) - already included in Gross Profit calculation
     * - Stock Purchases - not an expense, it's an asset transfer
     *
     * @param userEmail User's email
     * @return Total operational expenses (as negative number)
     */
    public BigDecimal calculateTotalOperationalExpenses(String userEmail) {
        log.debug("Calculating total operational expenses for user: {}", userEmail);

        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(userEmail);

        BigDecimal expenses = transactions.stream()
            .filter(Transaction::isAffectsProfit) // Affects profit
            .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0) // Negative = expense
            .filter(t -> t.getDetail() != TransactionDetail.COST_OF_GOODS_SOLD) // Exclude COGS
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Total operational expenses for {}: {}", userEmail, expenses);
        return expenses;
    }

    /**
     * Calculate Operational Expenses from a list of transactions.
     *
     * @param transactions List of transactions
     * @return Operational expenses (as negative number)
     */
    public BigDecimal calculateOperationalExpensesFromTransactions(List<Transaction> transactions) {
        return transactions.stream()
            .filter(Transaction::isAffectsProfit)
            .filter(t -> t.getAmount().compareTo(BigDecimal.ZERO) < 0)
            .filter(t -> t.getDetail() != TransactionDetail.COST_OF_GOODS_SOLD)
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ============================================================================
    // CONVENIENCE METHODS WITH PRINCIPAL
    // ============================================================================

    /**
     * Calculate Total Net Profit using Principal.
     *
     * @param principal Authenticated user
     * @return Net Profit
     */
    public BigDecimal calculateTotalNetProfit(Principal principal) {
        return calculateTotalNetProfit(principal.getName());
    }

    /**
     * Calculate Total Operational Expenses using Principal.
     *
     * @param principal Authenticated user
     * @return Operational expenses (as negative number)
     */
    public BigDecimal calculateTotalOperationalExpenses(Principal principal) {
        return calculateTotalOperationalExpenses(principal.getName());
    }

}
