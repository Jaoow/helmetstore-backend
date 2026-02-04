package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.PurchaseOrderItem;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SalePayment;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.*;
import com.jaoow.helmetstore.repository.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Transaction UseCases Tests")
public class TransactionUseCasesTest {

    @Autowired
    private CreateManualTransactionUseCase createManualTransactionUseCase;

    @Autowired
    private UpdateTransactionUseCase updateTransactionUseCase;

    @Autowired
    private DeleteTransactionUseCase deleteTransactionUseCase;

    @Autowired
    private RecordTransactionFromSaleUseCase recordTransactionFromSaleUseCase;

    @Autowired
    private RecordTransactionFromPurchaseOrderUseCase recordTransactionFromPurchaseOrderUseCase;

    @Autowired
    private RemoveTransactionLinkedToPurchaseOrderUseCase removeTransactionLinkedToPurchaseOrderUseCase;

    @Autowired
    private RemoveTransactionLinkedToSaleUseCase removeTransactionLinkedToSaleUseCase;

    @Autowired
    private CreateRefundTransactionForCanceledItemUseCase createRefundTransactionForCanceledItemUseCase;

    @Autowired
    private CalculateProfitUseCase calculateProfitUseCase;

    @Autowired
    private CalculateCashFlowUseCase calculateCashFlowUseCase;

    @Autowired
    private GetAvailableMonthsUseCase getAvailableMonthsUseCase;

    @Autowired
    private CalculateFinancialSummaryUseCase calculateFinancialSummaryUseCase;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Principal testPrincipal;
    private User testUser;
    private Inventory testInventory;
    private Account cashAccount;
    private Account bankAccount;

    @BeforeEach
    public void setup() {
        // Clean up previous test data
        transactionRepository.deleteAll();

        // Use existing user from test database to avoid schema incompatibility
        testUser = userRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No users found in test database"));

        // Get user's inventory and accounts
        testInventory = testUser.getInventory();
        if (testInventory == null) {
            throw new RuntimeException("Test user has no inventory");
        }

        // Get or create CASH account
        cashAccount = testUser.getAccounts().stream()
                .filter(acc -> acc.getType() == AccountType.CASH)
                .findFirst()
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(AccountType.CASH)
                            .user(testUser)
                            .build();
                    return accountRepository.save(newAccount);
                });

        // Get or create BANK account
        bankAccount = testUser.getAccounts().stream()
                .filter(acc -> acc.getType() == AccountType.BANK)
                .findFirst()
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(AccountType.BANK)
                            .user(testUser)
                            .build();
                    return accountRepository.save(newAccount);
                });

        // Create principal
        testPrincipal = new UsernamePasswordAuthenticationToken(
                testUser.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @AfterEach
    public void tearDown() {
        // Clean up test data in correct order (respecting foreign keys)
        if (testUser != null && testUser.getId() != null) {
            // Delete transactions first (they reference accounts)
            transactionRepository.deleteAll();
            
            // Delete accounts (they reference user)
            accountRepository.deleteAll();
            
            // Delete inventory and its items
            if (testInventory != null) {
                inventoryItemRepository.deleteAll();
                productVariantRepository.deleteAll();
                productRepository.deleteAll();
            }
            
            // Finally delete user
            userRepository.delete(testUser);
        }
    }

    // ========== CREATE MANUAL TRANSACTION TESTS ==========

    @Test
    @DisplayName("Should create manual income transaction successfully")
    public void shouldCreateManualIncomeTransaction() {
        // Arrange
        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setDate(LocalDateTime.now());
        dto.setType(TransactionType.INCOME);
        dto.setDetail(TransactionDetail.OWNER_INVESTMENT);
        dto.setDescription("Investimento inicial");
        dto.setAmount(new BigDecimal("1000.00"));
        dto.setPaymentMethod(PaymentMethod.CASH);

        // Act
        createManualTransactionUseCase.execute(dto, testPrincipal);

        // Assert
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(testUser.getEmail());
        assertThat(transactions).hasSize(1);

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(transaction.getDetail()).isEqualTo(TransactionDetail.OWNER_INVESTMENT);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(transaction.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(transaction.isAffectsProfit()).isFalse(); // Owner investment doesn't affect profit
        assertThat(transaction.isAffectsCash()).isTrue();
        assertThat(transaction.getWalletDestination()).isEqualTo(AccountType.CASH);
    }

    @Test
    @DisplayName("Should create manual expense transaction with negative amount")
    public void shouldCreateManualExpenseTransactionWithNegativeAmount() {
        // Arrange
        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setDate(LocalDateTime.now());
        dto.setType(TransactionType.EXPENSE);
        dto.setDetail(TransactionDetail.FIXED_EXPENSE);
        dto.setDescription("Aluguel do mês");
        dto.setAmount(new BigDecimal("500.00")); // Positive amount
        dto.setPaymentMethod(PaymentMethod.PIX);

        // Act
        createManualTransactionUseCase.execute(dto, testPrincipal);

        // Assert
        List<Transaction> transactions = transactionRepository.findByAccountUserEmail(testUser.getEmail());
        assertThat(transactions).hasSize(1);

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("-500.00")); // Should be negative
        assertThat(transaction.isAffectsProfit()).isTrue(); // Rent affects profit
        assertThat(transaction.isAffectsCash()).isTrue();
        assertThat(transaction.getWalletDestination()).isEqualTo(AccountType.BANK);
    }

    @Test
    @DisplayName("Should automatically create account when not found")
    public void shouldThrowExceptionWhenAccountNotFound() {
        // Arrange
        TransactionCreateDTO dto = new TransactionCreateDTO();
        dto.setDate(LocalDateTime.now());
        dto.setType(TransactionType.INCOME);
        dto.setDetail(TransactionDetail.OWNER_INVESTMENT);
        dto.setDescription("Test");
        dto.setAmount(new BigDecimal("100.00"));
        dto.setPaymentMethod(PaymentMethod.PIX);

        // Remove BANK account if exists (PIX uses BANK account type)
        accountRepository.findByUserEmailAndType(testUser.getEmail(), AccountType.BANK)
                .ifPresent(accountRepository::delete);

        // Act - Should create account automatically
        createManualTransactionUseCase.execute(dto, testPrincipal);

        // Assert - Account should be created automatically
        Account createdAccount = accountRepository
                .findByUserEmailAndType(testUser.getEmail(), AccountType.BANK)
                .orElseThrow(() -> new AssertionError("Conta BANK deveria ter sido criada automaticamente"));

        assertThat(createdAccount)
                .as("Conta BANK deve ser criada automaticamente")
                .isNotNull();
        assertThat(createdAccount.getType())
                .as("Tipo da conta deve ser BANK")
                .isEqualTo(AccountType.BANK);
    }

    // ========== UPDATE TRANSACTION TESTS ==========

    @Test
    @DisplayName("Should update manual transaction successfully")
    public void shouldUpdateManualTransaction() {
        // Arrange - Create initial transaction
        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.FIXED_EXPENSE)
                .description("Aluguel original")
                .amount(new BigDecimal("-500.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transaction = transactionRepository.save(transaction);

        // Update DTO
        TransactionCreateDTO updateDTO = new TransactionCreateDTO();
        updateDTO.setDate(LocalDateTime.now());
        updateDTO.setType(TransactionType.EXPENSE);
        updateDTO.setDetail(TransactionDetail.VARIABLE_EXPENSE);
        updateDTO.setDescription("Energia elétrica");
        updateDTO.setAmount(new BigDecimal("300.00"));
        updateDTO.setPaymentMethod(PaymentMethod.PIX);

        // Act
        updateTransactionUseCase.execute(transaction.getId(), updateDTO, testPrincipal);

        // Assert
        Transaction updated = transactionRepository.findById(transaction.getId()).orElseThrow();
        assertThat(updated.getDescription()).isEqualTo("Energia elétrica");
        assertThat(updated.getDetail()).isEqualTo(TransactionDetail.VARIABLE_EXPENSE);
        assertThat(updated.getAmount()).isEqualByComparingTo(new BigDecimal("-300.00"));
        assertThat(updated.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    @DisplayName("Should not allow updating sale-linked transaction")
    public void shouldNotAllowUpdatingSaleLinkedTransaction() {
        // Arrange - Create sale-linked transaction
        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda #1")
                .amount(new BigDecimal("100.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .reference("SALE#1")
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transaction = transactionRepository.save(transaction);

        TransactionCreateDTO updateDTO = new TransactionCreateDTO();
        updateDTO.setDate(LocalDateTime.now());
        updateDTO.setType(TransactionType.INCOME);
        updateDTO.setDetail(TransactionDetail.SALE);
        updateDTO.setDescription("Updated");
        updateDTO.setAmount(new BigDecimal("200.00"));
        updateDTO.setPaymentMethod(PaymentMethod.CASH);

        // Act & Assert
        Long transactionId = transaction.getId();
        assertThatThrownBy(() -> updateTransactionUseCase.execute(transactionId, updateDTO, testPrincipal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Você não pode editar transações vinculadas a vendas");
    }

    // ========== DELETE TRANSACTION TESTS ==========

    @Test
    @DisplayName("Should delete manual transaction successfully")
    public void shouldDeleteManualTransaction() {
        // Arrange
        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.FIXED_EXPENSE)
                .description("Aluguel")
                .amount(new BigDecimal("-500.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transaction = transactionRepository.save(transaction);

        // Act
        deleteTransactionUseCase.execute(transaction.getId(), testPrincipal);

        // Assert
        assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should not allow deleting sale-linked transaction")
    public void shouldNotAllowDeletingSaleLinkedTransaction() {
        // Arrange
        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda #1")
                .amount(new BigDecimal("100.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .reference("SALE#1")
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transaction = transactionRepository.save(transaction);

        // Act & Assert
        Long transactionId = transaction.getId();
        assertThatThrownBy(() -> deleteTransactionUseCase.execute(transactionId, testPrincipal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Você não pode excluir transações vinculadas a vendas");
    }

    @Test
    @DisplayName("Should not allow deleting COGS transaction")
    public void shouldNotAllowDeletingCOGSTransaction() {
        // Arrange
        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                .description("COGS - Venda #1")
                .amount(new BigDecimal("-50.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .reference("SALE#1")
                .affectsProfit(true)
                .affectsCash(false)
                .build();
        transaction = transactionRepository.save(transaction);

        // Act & Assert
        Long transactionId = transaction.getId();
        assertThatThrownBy(() -> deleteTransactionUseCase.execute(transactionId, testPrincipal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Você não pode excluir transações vinculadas a vendas");
    }

    // ========== RECORD TRANSACTION FROM SALE TESTS ==========

    @Test
    @DisplayName("Should record transactions from sale with revenue and COGS")
    public void shouldRecordTransactionsFromSale() {
        // Arrange - Create product and variant
        Product product = createTestProduct("Capacete", "Preto");
        ProductVariant variant = product.getVariants().get(0);

        // Create inventory item with cost
        InventoryItem inventoryItem = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variant)
                .quantity(10)
                .averageCost(new BigDecimal("50.00"))
                .build();
        inventoryItemRepository.save(inventoryItem);

        // Create sale
        Sale sale = new Sale();
        sale.setDate(LocalDateTime.now());
        sale.setInventory(testInventory);

        SaleItem saleItem = SaleItem.builder()
                .sale(sale)
                .productVariant(variant)
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .unitProfit(new BigDecimal("50.00"))
                .totalItemPrice(new BigDecimal("200.00"))
                .totalItemProfit(new BigDecimal("100.00"))
                .costBasisAtSale(new BigDecimal("50.00"))
                .build();
        sale.setItems(List.of(saleItem));

        SalePayment payment = SalePayment.builder()
                .sale(sale)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("200.00"))
                .build();
        sale.setPayments(List.of(payment));
        sale.setTotalAmount(new BigDecimal("200.00"));
        sale.setTotalProfit(new BigDecimal("100.00"));

        sale = saleRepository.save(sale);

        // Act
        recordTransactionFromSaleUseCase.execute(sale, testPrincipal);

        // Assert
        List<Transaction> transactions = transactionRepository.findAllByReference("SALE#" + sale.getId());
        assertThat(transactions).hasSize(2); // Revenue + COGS

        // Verify revenue transaction
        Transaction revenueTx = transactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .findFirst()
                .orElseThrow();
        assertThat(revenueTx.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(revenueTx.getAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(revenueTx.isAffectsProfit()).isTrue();
        assertThat(revenueTx.isAffectsCash()).isTrue();
        assertThat(revenueTx.getWalletDestination()).isEqualTo(AccountType.CASH);

        // Verify COGS transaction
        Transaction cogsTx = transactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .findFirst()
                .orElseThrow();
        assertThat(cogsTx.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(cogsTx.getAmount()).isEqualByComparingTo(new BigDecimal("-100.00")); // 2 items * 50.00 cost
        assertThat(cogsTx.isAffectsProfit()).isTrue();
        assertThat(cogsTx.isAffectsCash()).isFalse(); // COGS doesn't affect cash
        assertThat(cogsTx.getWalletDestination()).isNull();

        // Verify cost basis was captured
        SaleItem savedItem = saleRepository.findById(sale.getId()).orElseThrow().getItems().get(0);
        assertThat(savedItem.getCostBasisAtSale()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should not create duplicate transactions for same payment")
    public void shouldNotCreateDuplicateTransactionsForSamePayment() {
        // Arrange
        Product product = createTestProduct("Capacete", "Azul");
        ProductVariant variant = product.getVariants().get(0);

        InventoryItem inventoryItem = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variant)
                .quantity(10)
                .averageCost(new BigDecimal("50.00"))
                .build();
        inventoryItemRepository.save(inventoryItem);

        Sale sale = new Sale();
        sale.setDate(LocalDateTime.now());
        sale.setInventory(testInventory);

        SaleItem saleItem = SaleItem.builder()
                .sale(sale)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .unitProfit(new BigDecimal("50.00"))
                .totalItemPrice(new BigDecimal("100.00"))
                .totalItemProfit(new BigDecimal("50.00"))
                .costBasisAtSale(new BigDecimal("50.00"))
                .build();
        sale.setItems(List.of(saleItem));

        SalePayment payment = SalePayment.builder()
                .sale(sale)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("100.00"))
                .build();
        sale.setPayments(List.of(payment));
        sale.setTotalAmount(new BigDecimal("100.00"));
        sale.setTotalProfit(new BigDecimal("50.00"));

        sale = saleRepository.save(sale);

        // Act - Record twice
        recordTransactionFromSaleUseCase.execute(sale, testPrincipal);
        recordTransactionFromSaleUseCase.execute(sale, testPrincipal);

        // Assert - Should still have only 2 transactions (1 revenue + 1 COGS)
        List<Transaction> transactions = transactionRepository.findAllByReference("SALE#" + sale.getId());
        assertThat(transactions.size())
                .as("Não deve criar transações duplicadas ao executar o UseCase múltiplas vezes - deve ser idempotente")
                .isEqualTo(2);
    }

    // ========== RECORD TRANSACTION FROM PURCHASE ORDER TESTS ==========

    @Test
    @DisplayName("Should record transaction from purchase order")
    public void shouldRecordTransactionFromPurchaseOrder() {
        // Arrange
        Product product = createTestProduct("Capacete", "Verde");
        ProductVariant variant = product.getVariants().get(0);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setInventory(testInventory);
        purchaseOrder.setOrderNumber("PO-001");
        purchaseOrder.setDate(LocalDate.now());
        purchaseOrder.setPaymentMethod(PaymentMethod.CASH);
        purchaseOrder.setStatus(PurchaseOrderStatus.DELIVERED);
        purchaseOrder.setTotalAmount(new BigDecimal("500.00"));

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .productVariant(variant)
                .quantity(10)
                .purchasePrice(new BigDecimal("50.00"))
                .build();
        purchaseOrder.setItems(List.of(item));

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        // Act
        recordTransactionFromPurchaseOrderUseCase.execute(purchaseOrder, testPrincipal);

        // Assert
        List<Transaction> transactions = transactionRepository.findAllByReference("PURCHASE_ORDER#" + purchaseOrder.getId());
        assertThat(transactions).hasSize(1);

        Transaction transaction = transactions.get(0);
        assertThat(transaction.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transaction.getDetail()).isEqualTo(TransactionDetail.INVENTORY_PURCHASE);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal("-500.00"));
        assertThat(transaction.isAffectsProfit()).isFalse(); // Inventory purchase doesn't affect profit
        assertThat(transaction.isAffectsCash()).isTrue();
        assertThat(transaction.getWalletDestination()).isEqualTo(AccountType.CASH);
    }

    // ========== REMOVE TRANSACTION LINKED TO PURCHASE ORDER TESTS ==========

    @Test
    @DisplayName("Should remove transactions linked to purchase order")
    public void shouldRemoveTransactionsLinkedToPurchaseOrder() {
        // Arrange
        Product product = createTestProduct("Capacete", "Amarelo");
        ProductVariant variant = product.getVariants().get(0);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setInventory(testInventory);
        purchaseOrder.setOrderNumber("PO-002");
        purchaseOrder.setDate(LocalDate.now());
        purchaseOrder.setPaymentMethod(PaymentMethod.PIX);
        purchaseOrder.setStatus(PurchaseOrderStatus.DELIVERED);
        purchaseOrder.setTotalAmount(new BigDecimal("300.00"));

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .productVariant(variant)
                .quantity(6)
                .purchasePrice(new BigDecimal("50.00"))
                .build();
        purchaseOrder.setItems(List.of(item));

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        // Create transaction
        recordTransactionFromPurchaseOrderUseCase.execute(purchaseOrder, testPrincipal);

        // Verify transaction exists
        List<Transaction> beforeRemoval = transactionRepository.findAllByReference("PURCHASE_ORDER#" + purchaseOrder.getId());
        assertThat(beforeRemoval).hasSize(1);

        // Act
        removeTransactionLinkedToPurchaseOrderUseCase.execute(purchaseOrder);

        // Assert
        List<Transaction> afterRemoval = transactionRepository.findAllByReference("PURCHASE_ORDER#" + purchaseOrder.getId());
        assertThat(afterRemoval).isEmpty();
    }

    @Test
    @DisplayName("Should throw exception when no transaction found for purchase order")
    public void shouldThrowExceptionWhenNoPurchaseOrderTransactionFound() {
        // Arrange
        Product product = createTestProduct("Capacete", "Roxo");
        ProductVariant variant = product.getVariants().get(0);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setInventory(testInventory);
        purchaseOrder.setOrderNumber("PO-003");
        purchaseOrder.setDate(LocalDate.now());
        purchaseOrder.setPaymentMethod(PaymentMethod.CASH);
        purchaseOrder.setStatus(PurchaseOrderStatus.DELIVERED);
        purchaseOrder.setTotalAmount(new BigDecimal("100.00"));

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .productVariant(variant)
                .quantity(2)
                .purchasePrice(new BigDecimal("50.00"))
                .build();
        purchaseOrder.setItems(List.of(item));

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        // Act & Assert
        PurchaseOrder finalPurchaseOrder = purchaseOrder;
        assertThatThrownBy(() -> removeTransactionLinkedToPurchaseOrderUseCase.execute(finalPurchaseOrder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found for purchase order ID");
    }

    // ========== REMOVE TRANSACTION LINKED TO SALE TESTS ==========

    @Test
    @DisplayName("Should remove all transactions linked to sale")
    public void shouldRemoveAllTransactionsLinkedToSale() {
        // Arrange
        Product product = createTestProduct("Capacete", "Rosa");
        ProductVariant variant = product.getVariants().get(0);

        InventoryItem inventoryItem = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variant)
                .quantity(10)
                .averageCost(new BigDecimal("50.00"))
                .build();
        inventoryItemRepository.save(inventoryItem);

        Sale sale = new Sale();
        sale.setDate(LocalDateTime.now());
        sale.setInventory(testInventory);

        SaleItem saleItem = SaleItem.builder()
                .sale(sale)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .unitProfit(new BigDecimal("50.00"))
                .totalItemPrice(new BigDecimal("100.00"))
                .totalItemProfit(new BigDecimal("50.00"))
                .costBasisAtSale(new BigDecimal("50.00"))
                .build();
        sale.setItems(List.of(saleItem));

        SalePayment payment = SalePayment.builder()
                .sale(sale)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("100.00"))
                .build();
        sale.setPayments(List.of(payment));
        sale.setTotalAmount(new BigDecimal("100.00"));
        sale.setTotalProfit(new BigDecimal("50.00"));

        sale = saleRepository.save(sale);

        // Create transactions
        recordTransactionFromSaleUseCase.execute(sale, testPrincipal);

        // Verify transactions exist
        List<Transaction> beforeRemoval = transactionRepository.findAllByReference("SALE#" + sale.getId());
        assertThat(beforeRemoval).hasSize(2); // Revenue + COGS

        // Act
        removeTransactionLinkedToSaleUseCase.execute(sale);

        // Assert
        List<Transaction> afterRemoval = transactionRepository.findAllByReference("SALE#" + sale.getId());
        assertThat(afterRemoval).isEmpty();
    }

    // ========== CREATE REFUND TRANSACTION TESTS ==========

    @Test
    @DisplayName("Should create refund transaction for canceled item")
    public void shouldCreateRefundTransactionForCanceledItem() {
        // Arrange
        Product product = createTestProduct("Capacete", "Laranja");
        ProductVariant variant = product.getVariants().get(0);

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setInventory(testInventory);
        purchaseOrder.setOrderNumber("PO-004");
        purchaseOrder.setDate(LocalDate.now());
        purchaseOrder.setPaymentMethod(PaymentMethod.PIX);
        purchaseOrder.setStatus(PurchaseOrderStatus.DELIVERED);
        purchaseOrder.setTotalAmount(new BigDecimal("500.00"));

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrder(purchaseOrder)
                .productVariant(variant)
                .quantity(10)
                .purchasePrice(new BigDecimal("50.00"))
                .build();
        purchaseOrder.setItems(List.of(item));

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);

        BigDecimal refundAmount = new BigDecimal("150.00");
        String itemDescription = "Capacete Laranja M - 3 unidades";

        // Act
        createRefundTransactionForCanceledItemUseCase.execute(
                purchaseOrder,
                refundAmount,
                itemDescription,
                testPrincipal
        );

        // Assert
        List<Transaction> transactions = transactionRepository.findAllByReference("REFUND_PURCHASE_ORDER#" + purchaseOrder.getId());
        assertThat(transactions).hasSize(1);

        Transaction refund = transactions.get(0);
        assertThat(refund.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(refund.getDetail()).isEqualTo(TransactionDetail.REFUND);
        assertThat(refund.getAmount()).isEqualByComparingTo(refundAmount);
        assertThat(refund.getDescription()).contains(itemDescription);
        assertThat(refund.getDescription()).contains("Reembolso cancelamento");
        assertThat(refund.isAffectsProfit()).isFalse(); // Refund doesn't affect profit
        assertThat(refund.isAffectsCash()).isTrue(); // Refund increases cash
        assertThat(refund.getWalletDestination()).isEqualTo(AccountType.BANK);
    }

    // ========== CALCULATE PROFIT TESTS ==========

    @Test
    @DisplayName("Should calculate profit correctly with revenue and expenses")
    public void shouldCalculateProfitCorrectly() {
        // Arrange - Create revenue transaction
        Transaction revenue = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda")
                .amount(new BigDecimal("1000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(revenue);

        // Create COGS
        Transaction cogs = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                .description("COGS")
                .amount(new BigDecimal("-400.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(false)
                .build();
        transactionRepository.save(cogs);

        // Create operational expense
        Transaction expense = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.FIXED_EXPENSE)
                .description("Aluguel")
                .amount(new BigDecimal("-200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(expense);

        // Act
        BigDecimal profit = calculateProfitUseCase.execute(testPrincipal);

        // Assert - Profit = 1000 - 400 - 200 = 400
        assertThat(profit).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    @DisplayName("Should exclude non-profit transactions from profit calculation")
    public void shouldExcludeNonProfitTransactionsFromProfitCalculation() {
        // Arrange - Create revenue
        Transaction revenue = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda")
                .amount(new BigDecimal("500.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(revenue);

        // Create inventory purchase (doesn't affect profit)
        Transaction inventoryPurchase = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description("Compra de estoque")
                .amount(new BigDecimal("-300.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(false) // Should not affect profit
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(inventoryPurchase);

        // Act
        BigDecimal profit = calculateProfitUseCase.execute(testPrincipal);

        // Assert - Profit = 500 (inventory purchase doesn't affect profit)
        assertThat(profit).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // ========== CALCULATE CASH FLOW TESTS ==========

    @Test
    @DisplayName("Should calculate cash flow correctly with all cash-affecting transactions")
    public void shouldCalculateCashFlowCorrectly() {
        // Arrange - Create revenue
        Transaction revenue = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda")
                .amount(new BigDecimal("1000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(revenue);

        // Create inventory purchase
        Transaction inventoryPurchase = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description("Compra de estoque")
                .amount(new BigDecimal("-400.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(false)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(inventoryPurchase);

        // Create operational expense
        Transaction expense = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.FIXED_EXPENSE)
                .description("Aluguel")
                .amount(new BigDecimal("-200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(expense);

        // Act
        BigDecimal cashFlow = calculateCashFlowUseCase.execute(testPrincipal);

        // Assert - Cash Flow = 1000 - 400 - 200 = 400
        assertThat(cashFlow).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    @DisplayName("Should exclude non-cash transactions from cash flow calculation")
    public void shouldExcludeNonCashTransactionsFromCashFlowCalculation() {
        // Arrange - Create revenue
        Transaction revenue = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda")
                .amount(new BigDecimal("1000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(revenue);

        // Create COGS (doesn't affect cash)
        Transaction cogs = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                .description("COGS")
                .amount(new BigDecimal("-600.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(false) // Should not affect cash flow
                .build();
        transactionRepository.save(cogs);

        // Act
        BigDecimal cashFlow = calculateCashFlowUseCase.execute(testPrincipal);

        // Assert - Cash Flow = 1000 (COGS doesn't affect cash)
        assertThat(cashFlow).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ========== GET AVAILABLE MONTHS TESTS ==========

    @Test
    @DisplayName("Should return available months with transaction counts")
    public void shouldReturnAvailableMonthsWithTransactionCounts() {
        // Arrange - Create transactions in different months
        Transaction jan2024 = Transaction.builder()
                .date(LocalDateTime.of(2024, 1, 15, 10, 0))
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda Janeiro")
                .amount(new BigDecimal("100.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(jan2024);

        Transaction jan2024_2 = Transaction.builder()
                .date(LocalDateTime.of(2024, 1, 20, 10, 0))
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.FIXED_EXPENSE)
                .description("Aluguel Janeiro")
                .amount(new BigDecimal("-200.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(jan2024_2);

        Transaction feb2024 = Transaction.builder()
                .date(LocalDateTime.of(2024, 2, 10, 10, 0))
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda Fevereiro")
                .amount(new BigDecimal("150.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(feb2024);

        // Act
        List<com.jaoow.helmetstore.dto.balance.AvailableMonthDTO> availableMonths =
                getAvailableMonthsUseCase.execute(testUser.getEmail());

        // Assert
        assertThat(availableMonths).hasSizeGreaterThanOrEqualTo(2);

        // Verify January has 2 transactions
        var janMonth = availableMonths.stream()
                .filter(m -> m.getMonth().getYear() == 2024 && m.getMonth().getMonthValue() == 1)
                .findFirst();
        assertThat(janMonth).isPresent();
        assertThat(janMonth.get().getTransactionCount()).isEqualTo(2);

        // Verify February has 1 transaction
        var febMonth = availableMonths.stream()
                .filter(m -> m.getMonth().getYear() == 2024 && m.getMonth().getMonthValue() == 2)
                .findFirst();
        assertThat(febMonth).isPresent();
        assertThat(febMonth.get().getTransactionCount()).isEqualTo(1);
    }

    // ========== CALCULATE FINANCIAL SUMMARY TESTS ==========

    @Test
    @DisplayName("Should calculate financial summary with profit and cash flow")
    public void shouldCalculateFinancialSummary() {
        // Arrange
        // Revenue
        Transaction revenue = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.SALE)
                .description("Venda")
                .amount(new BigDecimal("1000.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(revenue);

        // COGS (affects profit, not cash)
        Transaction cogs = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                .description("COGS")
                .amount(new BigDecimal("-300.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(true)
                .affectsCash(false)
                .build();
        transactionRepository.save(cogs);

        // Inventory purchase (affects cash, not profit)
        Transaction inventoryPurchase = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INVENTORY_PURCHASE)
                .description("Compra")
                .amount(new BigDecimal("-400.00"))
                .paymentMethod(PaymentMethod.CASH)
                .account(cashAccount)
                .affectsProfit(false)
                .affectsCash(true)
                .walletDestination(AccountType.CASH)
                .build();
        transactionRepository.save(inventoryPurchase);

        // Act
        CalculateFinancialSummaryUseCase.FinancialSummary summary =
                calculateFinancialSummaryUseCase.execute(testPrincipal);

        // Assert
        // Profit = 1000 - 300 = 700
        assertThat(summary.getProfit()).isEqualByComparingTo(new BigDecimal("700.00"));
        // Cash Flow = 1000 - 400 = 600
        assertThat(summary.getCashFlow()).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    // ========== HELPER METHODS ==========

    private Product createTestProduct(String model, String color) {
        Product product = new Product();
        product.setModel(model);
        product.setColor(color);
        product.setCategory(categoryRepository.findAll().stream().findFirst().orElseThrow());
        product.setInventory(testInventory);

        ProductVariant variant = new ProductVariant();
        variant.setSize("M");
        variant.setSku("TEST-" + System.currentTimeMillis());
        variant.setProduct(product);

        product.setVariants(new ArrayList<>(List.of(variant)));
        return productRepository.save(product);
    }
}
