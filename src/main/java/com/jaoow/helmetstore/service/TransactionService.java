package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
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

    @Transactional
    public void createManualTransaction(TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = modelMapper.map(dto, Transaction.class);
        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for the given payment method."));

        transaction.setAccount(account);
        transactionRepository.save(transaction);

        // Invalidate financial caches after creating a transaction
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void recordTransactionFromSale(Sale sale, Principal principal) {
        LocalDateTime date = sale.getDate();

        sale.getPayments().forEach(payment -> {
            Account account = accountService
                    .findAccountByPaymentMethodAndUser(payment.getPaymentMethod(), principal)
                    .orElseThrow(() -> new AccountNotFoundException(
                            "No account found for the given payment method."));

            Transaction transaction = Transaction.builder()
                    .date(date)
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.SALE)
                    .description(SALE_REFERENCE_PREFIX
                            + formatProductVariantName(
                                    sale.getItems().getFirst().getProductVariant()))
                    .amount(payment.getAmount())
                    .paymentMethod(payment.getPaymentMethod())
                    .reference(SALE_REFERENCE_PREFIX + sale.getId())
                    .account(account)
                    .build();

            transactionRepository.save(transaction);
        });

        // Invalidate financial caches after recording transaction from sale
        cacheInvalidationService.invalidateFinancialCaches();
    }

    @Transactional
    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(purchaseOrder.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException(
                        "No account found for the given payment method."));

        Transaction transaction = Transaction.builder()
                .date(purchaseOrder.getDate().atStartOfDay())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                .description(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getOrderNumber())
                .amount(purchaseOrder.getTotalAmount())
                .paymentMethod(purchaseOrder.getPaymentMethod())
                .reference(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
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
        // Invalidate financial caches after removing transaction linked to purchase
        // order
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
     * Calculate profit using the formula:
     * profit = sum(SALE) - sum(all transactions where deductsFromProfit = true)
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.PROFIT_CALCULATION, key = "#principal.name")
    public BigDecimal calculateProfit(Principal principal) {
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(principal.getName());

        BigDecimal salesTotal = transactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // TODO: Refactor this to use a more specific method for profit-deducting
        // transactions
        // Currently, it assumes all transactions that affect withdrawable profit are
        // profit-deducting
        BigDecimal expensesTotal = transactions.stream()
                .filter(Transaction::affectsWithdrawableProfit)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return salesTotal.subtract(expensesTotal);
    }

    /**
     * Calculate total cash flow (all income minus all expenses)
     */
    @Cacheable(value = com.jaoow.helmetstore.cache.CacheNames.CASH_FLOW_CALCULATION, key = "#principal.name")
    public BigDecimal calculateCashFlow(Principal principal) {
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(principal.getName());

        BigDecimal incomeTotal = transactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenseTotal = transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return incomeTotal.subtract(expenseTotal);
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
