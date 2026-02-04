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
import com.jaoow.helmetstore.model.sale.SalePayment;
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
                .orElseThrow(() -> new AccountNotFoundException(dto.getPaymentMethod()));

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

    /**
     * Records transactions from a sale.
     * Only creates transactions for payments that haven't been registered yet.
     * Uses Transaction.salePaymentId for efficient duplicate prevention.
     *
     * @param sale The sale to record transactions for
     * @param principal The user principal
     */
    @Transactional
    public void recordTransactionFromSale(Sale sale, Principal principal) {
        LocalDateTime date = sale.getDate();

        // Process only payments that haven't generated a transaction yet
        for (SalePayment payment : sale.getPayments()) {
            // Skip payments that already have a transaction registered
            if (transactionRepository.existsByReferenceSubId(payment.getId())) {
                continue;
            }

            // Determine which wallet receives the money (Cash drawer or Bank account)
            AccountType walletDest = (payment.getPaymentMethod() == PaymentMethod.CASH)
                    ? AccountType.CASH
                    : AccountType.BANK;

            Account account = accountService
                    .findAccountByPaymentMethodAndUser(payment.getPaymentMethod(), principal)
                    .orElseThrow(() -> new AccountNotFoundException(payment.getPaymentMethod()));

            Transaction revenueTx = Transaction.builder()
                    .date(date)
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.SALE)
                    .description("Venda #" + sale.getId()
                            + " - " + formatProductVariantName(
                                    sale.getItems().getFirst().getProductVariant())
                            + " (" + payment.getPaymentMethod() + ")")
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getPaymentMethod())
                    .reference(SALE_REFERENCE_PREFIX + sale.getId())
                    .referenceSubId(payment.getId())  // Prevents duplicates in exchanges
                    .account(account)
                    // DOUBLE-ENTRY LEDGER FLAGS
                    .affectsProfit(true)  // Revenue increases profit
                    .affectsCash(true)    // Revenue increases cash/bank balance
                    .walletDestination(walletDest)
                    .build();

            transactionRepository.save(revenueTx);
        }

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
                    .orElseThrow(() -> new AccountNotFoundException(PaymentMethod.CASH));

            Transaction cogsTx = Transaction.builder()
                    .date(date)
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                    .description("Custo de Mercadoria - Venda #" + sale.getId())
                    .amount(totalCostOfGoods.negate()) // Negative value (expense)
                    .paymentMethod(PaymentMethod.CASH) // Placeholder
                    .reference(SALE_REFERENCE_PREFIX + sale.getId())
                    .account(systemAccount)
                    // DOUBLE-ENTRY LEDGER FLAGS
                    .affectsProfit(true)  // COGS REDUCES profit
                    .affectsCash(false)   // COGS does NOT change cash (already spent)
                    .walletDestination(null) // No wallet involved
                    .build();

            transactionRepository.save(cogsTx);
        }

        // Invalidate financial caches after recording transaction from sale
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(purchaseOrder.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(purchaseOrder.getPaymentMethod()));

        // Determine wallet destination based on payment method
        AccountType walletDest = (purchaseOrder.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Transaction transaction = Transaction.builder()
                .date(purchaseOrder.getDate().atStartOfDay())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description("Pedido de Compra #" + purchaseOrder.getOrderNumber())
                .amount(purchaseOrder.getTotalAmount().negate())
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
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
                .orElseThrow(() -> new AccountNotFoundException(dto.getPaymentMethod()));

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
        List<Transaction> transactions = transactionRepository.findAllByReference(reference);

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Transaction not found for purchase order ID: " + purchaseOrder.getId());
        }

        transactionRepository.deleteAll(transactions);
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void createRefundTransactionForCanceledItem(PurchaseOrder purchaseOrder, BigDecimal refundAmount,
            String itemDescription, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(purchaseOrder.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(purchaseOrder.getPaymentMethod()));

        // Determine wallet destination based on payment method
        AccountType walletDest = (purchaseOrder.getPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Transaction refundTransaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.REFUND)
                .description("Reembolso cancelamento: " + itemDescription + " - Pedido #" + purchaseOrder.getOrderNumber())
                .amount(refundAmount)
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference("REFUND_" + PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
                .affectsProfit(false)  // Refund doesn't affect profit
                .affectsCash(true)     // Refund increases cash/bank balance
                .walletDestination(walletDest)
                .build();

        transactionRepository.save(refundTransaction);

        // Invalidate financial caches after creating refund transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void removeTransactionLinkedToSale(Sale sale) {
        String reference = SALE_REFERENCE_PREFIX + sale.getId();
        List<Transaction> transactions = transactionRepository.findAllByReference(reference);

        if (transactions.isEmpty()) {
            throw new IllegalArgumentException("Transação não encontrada para venda ID: " + sale.getId());
        }

        transactionRepository.deleteAll(transactions);
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
     * Get available months with transaction counts (lightweight for UI month selectors).
     * This avoids loading full transaction data when populating month selection dropdowns.
     */
    public List<com.jaoow.helmetstore.dto.balance.AvailableMonthDTO> getAvailableMonths(String userEmail) {
        List<Object[]> results = transactionRepository.findAvailableMonthsWithCount(userEmail);

        return results.stream()
                .map(row -> com.jaoow.helmetstore.dto.balance.AvailableMonthDTO.builder()
                        .month(java.time.YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                        .transactionCount(((Number) row[2]).intValue())
                        .build())
                .toList();
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
