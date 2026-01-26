package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

       Optional<Transaction> findByIdAndAccountUserEmail(Long id, String accountUserEmail);

       Optional<Transaction> findByReference(String reference);

       List<Transaction> findAllByReference(String reference);

       // Versão otimizada com hints e FETCH JOIN
       @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
       @Query("SELECT t FROM Transaction t JOIN FETCH t.account a WHERE a.user.email = :userEmail ORDER BY t.date DESC")
       List<Transaction> findByAccountUserEmail(@Param("userEmail") String userEmail);

       // Versão paginada para queries com grande volume
       @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
       @Query(value = "SELECT t FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail ORDER BY t.date DESC",
              countQuery = "SELECT COUNT(t) FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail")
       Page<Transaction> findByAccountUserEmailPaginated(@Param("userEmail") String userEmail, Pageable pageable);

       @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
       @Query("SELECT t FROM Transaction t JOIN FETCH t.account a WHERE a.user.email = :userEmail " +
                     "AND t.date >= :startDate AND t.date < :endDate ORDER BY t.date DESC")
       List<Transaction> findByAccountUserEmailAndDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT DISTINCT YEAR(t.date) as year, MONTH(t.date) as month " +
                     "FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail " +
                     "ORDER BY year DESC, month DESC")
       List<Object[]> findDistinctMonthsByUserEmail(@Param("userEmail") String userEmail);

       /**
        * Get available months with transaction counts (lightweight for UI month selectors).
        * Returns: [year, month, count] ordered by most recent first
        */
       @Query("SELECT YEAR(t.date) as year, MONTH(t.date) as month, COUNT(t) as count " +
                     "FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail " +
                     "GROUP BY YEAR(t.date), MONTH(t.date) " +
                     "ORDER BY year DESC, month DESC")
       List<Object[]> findAvailableMonthsWithCount(@Param("userEmail") String userEmail);

       // ============================================================================
       // DOUBLE-ENTRY LEDGER QUERIES
       // ============================================================================
       // These queries use the new ledger flags (affectsProfit, affectsCash,
       // walletDestination) to provide accurate financial reporting without
       // hardcoded business logic.
       // ============================================================================

       /**
        * Calculate Real Net Profit for a user within a date range.
        * <p>
        * Formula: SUM(amount) WHERE affectsProfit = true
        * <p>
        * Includes: Revenue, COGS, Operational Expenses
        * Excludes: Stock Purchases, Owner Investments, Internal Transfers
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsProfit = true " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateNetProfitByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate Cash Balance for a specific wallet (CASH or BANK).
        * <p>
        * Formula: SUM(amount) WHERE walletDestination = :walletType
        * <p>
        * Example: Cash Balance = SUM(amount WHERE walletDestination = 'CASH')
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.walletDestination = :walletType")
       BigDecimal calculateWalletBalance(
                     @Param("userEmail") String userEmail,
                     @Param("walletType") AccountType walletType);

       /**
        * Calculate Wallet Balance up to a specific date (inclusive).
        * <p>
        * Formula: SUM(amount) WHERE walletDestination = :walletType AND date <= :endDate
        * <p>
        * Use this for historical balance queries (e.g., balance at end of month)
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.walletDestination = :walletType " +
                     "AND t.date <= :endDate")
       BigDecimal calculateWalletBalanceUpToDate(
                     @Param("userEmail") String userEmail,
                     @Param("walletType") AccountType walletType,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate Total Cash Flow (all liquidity movement) within a date range.
        * <p>
        * Formula: SUM(amount) WHERE affectsCash = true
        * <p>
        * Includes: All physical money in/out (Sales, Expenses, Investments)
        * Excludes: COGS (accounting entry only)
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateCashFlowByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Get all profit-affecting transactions for detailed reporting.
        * Useful for drilling down into what contributes to Net Profit.
        */
       @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
       @Query("SELECT t FROM Transaction t " +
                     "JOIN FETCH t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsProfit = true " +
                     "AND t.date >= :startDate AND t.date < :endDate " +
                     "ORDER BY t.date DESC")
       List<Transaction> findProfitAffectingTransactionsByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Get all transactions for a specific wallet within a date range.
        * Useful for generating Cash/Bank statements.
        */
       @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
       @Query("SELECT t FROM Transaction t " +
                     "JOIN FETCH t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.walletDestination = :walletType " +
                     "AND t.date >= :startDate AND t.date < :endDate " +
                     "ORDER BY t.date DESC")
       List<Transaction> findWalletTransactionsByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("walletType") AccountType walletType,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate Gross Profit (Revenue - COGS only) for all time.
        * <p>
        * Formula: SUM(amount) WHERE detail IN ('SALE', 'COST_OF_GOODS_SOLD') AND reference LIKE 'SALE#%'
        * <p>
        * Includes: Sales Revenue (+), COGS from sales (-)
        * Excludes: Operational expenses, Purchase Orders
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND (t.detail = 'SALE' OR " +
                     "(t.detail = 'COST_OF_GOODS_SOLD' AND t.reference LIKE 'SALE#%'))")
       BigDecimal calculateGrossProfit(@Param("userEmail") String userEmail);

       /**
        * Calculate Gross Profit (Revenue - COGS only) for a date range.
        * <p>
        * Formula: SUM(amount) WHERE detail IN ('SALE', 'COST_OF_GOODS_SOLD') AND reference LIKE 'SALE#%'
        * <p>
        * Use this for monthly/quarterly gross profit reports.
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND (t.detail = 'SALE' OR " +
                     "(t.detail = 'COST_OF_GOODS_SOLD' AND t.reference LIKE 'SALE#%')) " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateGrossProfitByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate total cash INCOME (positive cash flows only).
        * <p>
        * Formula: SUM(amount) WHERE affectsCash = true AND amount > 0
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true " +
                     "AND t.amount > 0")
       BigDecimal calculateTotalCashIncome(@Param("userEmail") String userEmail);

       /**
        * Calculate total cash EXPENSES (negative cash flows only, returned as positive).
        * <p>
        * Formula: ABS(SUM(amount)) WHERE affectsCash = true AND amount < 0
        */
       @Query("SELECT COALESCE(ABS(SUM(t.amount)), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true " +
                     "AND t.amount < 0")
       BigDecimal calculateTotalCashExpense(@Param("userEmail") String userEmail);

       /**
        * Calculate total cash FLOW (net of all cash movements).
        * <p>
        * Formula: SUM(amount) WHERE affectsCash = true
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true")
       BigDecimal calculateTotalCashFlow(@Param("userEmail") String userEmail);

       /**
        * Calculate cash INCOME (positive cash flows only) for a date range.
        * <p>
        * Formula: SUM(amount) WHERE affectsCash = true AND amount > 0 AND date BETWEEN startDate AND endDate
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true " +
                     "AND t.amount > 0 " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateCashIncomeByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate sales revenue (SALE transactions only) for a date range.
        * Used for calculating profit margins (gross margin, net margin).
        * <p>
        * Formula: SUM(amount) WHERE detail = 'SALE' AND date BETWEEN startDate AND endDate
        */
       @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.detail = 'SALE' " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateSalesRevenueByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       /**
        * Calculate cash EXPENSES (negative cash flows only, returned as positive) for a date range.
        * <p>
        * Formula: ABS(SUM(amount)) WHERE affectsCash = true AND amount < 0 AND date BETWEEN startDate AND endDate
        */
       @Query("SELECT COALESCE(ABS(SUM(t.amount)), 0) FROM Transaction t " +
                     "JOIN t.account a " +
                     "WHERE a.user.email = :userEmail " +
                     "AND t.affectsCash = true " +
                     "AND t.amount < 0 " +
                     "AND t.date >= :startDate AND t.date < :endDate")
       BigDecimal calculateCashExpenseByDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);
}
