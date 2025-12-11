package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String SALE_REFERENCE_PREFIX = "SALE#";
    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ModelMapper modelMapper;
    private final CacheInvalidationService cacheInvalidationService;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProfitCalculationService profitCalculationService;

    @Transactional
    public void createManualTransaction(TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = modelMapper.map(dto, Transaction.class);
        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for the given payment method."));

        transaction.setAccount(account);

        // Ensure expenses are stored as negative values
        if (dto.getType() == TransactionType.EXPENSE && dto.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transaction.setAmount(dto.getAmount().negate());
        }

        // Set ledger flags based on transaction type and detail
        AccountType walletDest = (dto.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;


        // All manual transactions affect cash (they represent real money movement)
        TransactionDetail detail = transaction.getDetail();

        transaction.setAffectsProfit(detail.isAffectsProfit());
        transaction.setAffectsCash(detail.isAffectsCash());
        transaction.setWalletDestination(walletDest);

        transactionRepository.save(transaction);

        // Invalidate financial caches after creating a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void recordTransactionFromSale(Sale sale, Principal principal) {
        LocalDateTime date = sale.getDate();

        // =================================================================================
        // PART 1: REVENUE (Money In) - Creates 1 Transaction per Payment Method
        // =================================================================================
        // IMPORTANT: Revenue transactions use PROPORTIONAL PROFIT, not payment amount!
        //
        // Why? Because:
        //   - Payment amount = Cash received (affects_cash tracking)
        //   - Profit amount = Revenue for P&L (affects_profit tracking)
        //   - COGS is subtracted separately, so we need profit not revenue
        //
        // Example:
        //   Sale: total_amount=125.00, total_profit=36.01 (profit margin ~29%)
        //   Payment: CASH=100 (80%), PIX=25 (20%)
        //   Revenue Tx 1: amount=28.81 (36.01 * 0.80) affects_profit=TRUE, affects_cash=TRUE
        //   Revenue Tx 2: amount=7.20 (36.01 * 0.20) affects_profit=TRUE, affects_cash=TRUE
        //   Result: Net Profit = 36.01 - Expenses (correct)
        // =================================================================================

        sale.getPayments().forEach(payment -> {
            // Calculate proportional profit for this payment
            // Formula: (payment_amount / total_amount) * total_profit
            BigDecimal paymentPercentage = payment.getAmount().divide(sale.getTotalAmount(), 10, BigDecimal.ROUND_HALF_UP);
            BigDecimal proportionalProfit = sale.getTotalProfit()
                    .multiply(paymentPercentage);

            // Determine which wallet receives the money (Cash drawer or Bank account)
            AccountType walletDest = (payment.getPaymentMethod() == PaymentMethod.CASH)
                    ? AccountType.CASH
                    : AccountType.BANK;

            Account account = accountService
                    .findAccountByPaymentMethodAndUser(payment.getPaymentMethod(), principal)
                    .orElseThrow(() -> new AccountNotFoundException(
                            "No account found for the given payment method."));

            Transaction revenueTx = Transaction.builder()
                    .date(date)
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.SALE)
                    .description(SALE_REFERENCE_PREFIX
                            + formatProductVariantName(
                                    sale.getItems().getFirst().getProductVariant())
                            + " (" + payment.getPaymentMethod() + ")")
                    .amount(proportionalProfit) // ✅ FIXED: Use proportional profit, not payment amount
                    .paymentMethod(payment.getPaymentMethod())
                    .reference(SALE_REFERENCE_PREFIX + sale.getId())
                    .account(account)
                    // DOUBLE-ENTRY LEDGER FLAGS
                    .affectsProfit(true)  // Revenue increases profit
                    .affectsCash(true)    // Revenue increases cash/bank balance (tracked separately in cash flow)
                    .walletDestination(walletDest)
                    .build();

            transactionRepository.save(revenueTx);
        });

        // =================================================================================
        // PART 2: COGS (Cost of Goods Sold) - Value Out
        // =================================================================================
        // This represents the inventory cost being "consumed" by the sale.
        // It's an accounting entry that reduces profit but does NOT affect cash
        // (the cash was already spent when we purchased the stock).
        // =================================================================================

        BigDecimal totalCostOfGoods = BigDecimal.ZERO;

        for (SaleItem item : sale.getItems()) {
            // 1. Fetch the current average cost from inventory
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByInventoryAndProductVariant(sale.getInventory(), item.getProductVariant())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory item not found for variant ID: " + item.getProductVariant().getId()));

            // 2. Snapshot the cost at this exact moment for historical accuracy
            // This prevents future stock purchases from retroactively changing past profits
            BigDecimal costAtMomentOfSale = inventoryItem.getAverageCost();
            item.setCostBasisAtSale(costAtMomentOfSale);

            // 3. Accumulate the total cost for all items in this sale
            BigDecimal itemTotalCost = costAtMomentOfSale
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            totalCostOfGoods = totalCostOfGoods.add(itemTotalCost);
        }

        // 4. Create ONE aggregated COGS transaction for the entire sale
        if (totalCostOfGoods.compareTo(BigDecimal.ZERO) > 0) {
            // Use the primary account for linking (COGS doesn't belong to a specific wallet)
            Account systemAccount = accountService
                    .findAccountByPaymentMethodAndUser(PaymentMethod.CASH, principal)
                    .orElseThrow(() -> new AccountNotFoundException(
                            "No default account found for COGS recording."));

            Transaction cogsTx = Transaction.builder()
                    .date(date)
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                    .description("Cost of Goods Sold - Sale #" + sale.getId())
                    .amount(totalCostOfGoods.negate()) // Negative value (expense)
                    .paymentMethod(PaymentMethod.CASH) // Placeholder (irrelevant for COGS)
                    .reference(SALE_REFERENCE_PREFIX + sale.getId())
                    .account(systemAccount)
                    // DOUBLE-ENTRY LEDGER FLAGS
                    .affectsProfit(true)  // COGS REDUCES profit
                    .affectsCash(false)   // COGS does NOT change cash (already spent)
                    .walletDestination(null) // No wallet involved (accounting entry only)
                    .build();

            transactionRepository.save(cogsTx);
        }

        // Invalidate financial caches after recording transaction from sale
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(purchaseOrder.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for the given payment method."));

        // Determine wallet destination based on payment method
        AccountType walletDest = (purchaseOrder.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Transaction transaction = Transaction.builder()
                .date(purchaseOrder.getDate().atStartOfDay())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getOrderNumber())
                .amount(purchaseOrder.getTotalAmount().negate()) // FIX: Negate expense amount
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
                // DOUBLE-ENTRY LEDGER FLAGS
                // Stock purchase affects cash (money out) but NOT profit (it's an asset transfer)
                .affectsProfit(false)  // Buying stock doesn't reduce profit
                .affectsCash(true)     // But it does reduce available cash
                .walletDestination(walletDest)
                .build();

        transactionRepository.save(transaction);

        // Invalidate financial caches after recording transaction from purchase order
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void updateTransaction(Long transactionId, TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = transactionRepository
                .findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transação não encontrada com ID: " + transactionId));

        if (transaction.getDetail() == TransactionDetail.SALE
                || transaction.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD) {
            throw new IllegalArgumentException(
                    "Você não pode editar transações vinculadas a vendas ou pedidos de compra.");
        }

        // Update the existing transaction with new data
        modelMapper.map(dto, transaction);

        // Ensure expenses are stored as negative values
        if (dto.getType() == TransactionType.EXPENSE && dto.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            transaction.setAmount(dto.getAmount().negate());
        }

        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Nenhuma conta encontrada para o método de pagamento informado."));

        transaction.setAccount(account);
        transactionRepository.save(transaction);

        // Invalidate financial caches after updating a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void deleteTransactionById(Long transactionId, Principal principal) {
        Transaction transaction = transactionRepository
                .findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found with ID: " + transactionId));

        if (transaction.getDetail() == TransactionDetail.SALE
                || transaction.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD) {
            throw new IllegalArgumentException(
                    "Você não pode excluir transações vinculadas a vendas ou pedidos de compra.");
        }

        transactionRepository.delete(transaction);
        // Invalidate financial caches after deleting a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void removeTransactionLinkedToPurchaseOrder(PurchaseOrder purchaseOrder) {
        String reference = PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId();
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction not found for purchase order ID: "
                                + purchaseOrder.getId()));

        transactionRepository.delete(transaction);
        // Invalidate financial caches after removing transaction linked to purchase order
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void removeTransactionLinkedToSale(Sale sale) {
        String reference = SALE_REFERENCE_PREFIX + sale.getId();
        List<Transaction> transactions = transactionRepository.findAllByReference(reference);

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("Transaction not found for sale ID: " + sale.getId());
        }

        transactionRepository.deleteAll(transactions);
        // Invalidate financial caches after removing transaction linked to sale
        cacheInvalidationService.invalidateFinancialCaches();
    }

    /**
     * Calculate total Net Profit (TRUE business profitability).
     * <p>
     * Formula: Net Profit = Revenue - COGS - Operational Expenses
     * <p>
     * Uses the UNIFIED PROFIT CALCULATION SERVICE to ensure consistency
     * across all profit calculations in the system.
     * <p>
     * This includes ALL profit-affecting transactions with affectsProfit = true:
     * - Revenue from Sales (+)
     * - Cost of Goods Sold (-)
     * - Operational Expenses (Rent, Energy, etc.) (-)
     * <p>
     * Excludes non-profit transactions:
     * - Stock Purchases (asset transfer, not an expense)
     * - Owner Investments (capital, not revenue)
     * - Internal Transfers (wallet movement, not profit-affecting)
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_CALCULATION, key = "#principal.name")
    public BigDecimal calculateProfit(Principal principal) {
        return profitCalculationService.calculateTotalNetProfit(principal.getName());
    }

    /**
     * Calculate total Cash Flow (liquidity available).
     * <p>
     * Formula: Cash Flow = SUM(all transactions where affectsCash = true)
     * <p>
     * This includes ALL physical money movements:
     * - Revenue from Sales (+)
     * - Stock Purchases (-)
     * - Operational Expenses (-)
     * - Owner Investments (+)
     * <p>
     * Excludes:
     * - Cost of Goods Sold (accounting entry only, no cash movement)
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.CASH_FLOW_CALCULATION, key = "#principal.name")
    public BigDecimal calculateCashFlow(Principal principal) {
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(principal.getName());

        return transactions.stream()
                .filter(Transaction::isAffectsCash) // Only cash-affecting transactions
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get financial summary with both profit and cash flow
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.FINANCIAL_SUMMARY, key = "#principal.name")
    public FinancialSummary calculateFinancialSummary(Principal principal) {
        BigDecimal profit = calculateProfit(principal);
        BigDecimal cashFlow = calculateCashFlow(principal);

        return FinancialSummary.builder()
                .profit(profit)
                .cashFlow(cashFlow)
                .build();
    }

    private String formatProductVariantName(ProductVariant productVariant) {
        Product product = productVariant.getProduct();
        return "%s#%s#%s".formatted(product.getModel(), product.getColor(), productVariant.getSize());
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
