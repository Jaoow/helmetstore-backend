package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SalePayment;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.CacheInvalidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

/**
 * Use Case: Record transactions from a sale
 * 
 * Responsibilities:
 * - Process sale payments and create revenue transactions
 * - Skip payments that already have transactions (duplicate prevention)
 * - Calculate and record Cost of Goods Sold (COGS)
 * - Snapshot cost basis at moment of sale for historical accuracy
 * - Set proper ledger flags for double-entry accounting
 * - Invalidate financial caches
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * DOUBLE-ENTRY ACCOUNTING RULES:
 * 
 * Revenue Transaction:
 * - affectsProfit = true  (increases profit)
 * - affectsCash = true    (increases cash/bank balance)
 * - walletDestination = CASH or BANK (where money goes)
 * 
 * COGS Transaction:
 * - affectsProfit = true  (decreases profit)
 * - affectsCash = false   (no cash movement, already spent)
 * - walletDestination = null (no wallet involved)
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Component
@RequiredArgsConstructor
public class RecordTransactionFromSaleUseCase {

    private static final String SALE_REFERENCE_PREFIX = "SALE#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CacheInvalidationService cacheInvalidationService;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public void execute(Sale sale, Principal principal) {
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
                    .getOrCreateAccountByPaymentMethod(payment.getPaymentMethod(), principal);

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
        // Skip if COGS already recorded for this sale (duplicate prevention)
        if (totalCostOfGoods.compareTo(BigDecimal.ZERO) > 0) {
            String saleReference = SALE_REFERENCE_PREFIX + sale.getId();
            
            // Check if COGS transaction already exists for this sale
            boolean cogsExists = transactionRepository.findAllByReference(saleReference).stream()
                    .anyMatch(tx -> tx.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD);
            
            if (!cogsExists) {
                // Use the primary account for linking (COGS doesn't belong to a specific wallet)
                Account systemAccount = accountService
                        .getOrCreateAccountByPaymentMethod(PaymentMethod.CASH, principal);

                Transaction cogsTx = Transaction.builder()
                        .date(date)
                        .type(TransactionType.EXPENSE)
                        .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                        .description("Custo de Mercadoria - Venda #" + sale.getId())
                        .amount(totalCostOfGoods.negate()) // Negative value (expense)
                        .paymentMethod(PaymentMethod.CASH) // Placeholder
                        .reference(saleReference)
                        .account(systemAccount)
                        // DOUBLE-ENTRY LEDGER FLAGS
                        .affectsProfit(true)  // COGS REDUCES profit
                        .affectsCash(false)   // COGS does NOT change cash (already spent)
                        .walletDestination(null) // No wallet involved
                        .build();

                transactionRepository.save(cogsTx);
            }
        }

        // Invalidate financial caches after recording transaction from sale
        cacheInvalidationService.invalidateFinancialCaches();
    }

    private String formatProductVariantName(ProductVariant productVariant) {
        Product product = productVariant.getProduct();
        return "%s#%s#%s".formatted(product.getModel(), product.getColor(), productVariant.getSize());
    }
}
