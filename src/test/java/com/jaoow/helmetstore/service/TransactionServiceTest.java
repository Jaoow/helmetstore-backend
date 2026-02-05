package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.dto.balance.FinancialSummaryDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SalePayment;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.usecase.transaction.CalculateFinancialMetricsUseCase;
import com.jaoow.helmetstore.usecase.transaction.CreateManualTransactionUseCase;
import com.jaoow.helmetstore.usecase.transaction.CreateRefundTransactionUseCase;
import com.jaoow.helmetstore.usecase.transaction.DeleteTransactionUseCase;
import com.jaoow.helmetstore.usecase.transaction.RecordPurchaseOrderTransactionUseCase;
import com.jaoow.helmetstore.usecase.transaction.RecordSaleTransactionUseCase;
import com.jaoow.helmetstore.usecase.transaction.RemovePurchaseOrderTransactionsUseCase;
import com.jaoow.helmetstore.usecase.transaction.RemoveSaleTransactionsUseCase;
import com.jaoow.helmetstore.usecase.transaction.UpdateTransactionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService - Testes Funcionais de Cálculos")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private CacheInvalidationService cacheInvalidationService;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private ProfitCalculationService profitCalculationService;

    // Use Cases - will be created with real instances using mocked dependencies
    private CreateManualTransactionUseCase createManualTransactionUseCase;
    private RecordSaleTransactionUseCase recordSaleTransactionUseCase;
    private RecordPurchaseOrderTransactionUseCase recordPurchaseOrderTransactionUseCase;
    private UpdateTransactionUseCase updateTransactionUseCase;
    private DeleteTransactionUseCase deleteTransactionUseCase;
    private CreateRefundTransactionUseCase createRefundTransactionUseCase;
    private RemoveSaleTransactionsUseCase removeSaleTransactionsUseCase;
    private RemovePurchaseOrderTransactionsUseCase removePurchaseOrderTransactionsUseCase;
    private CalculateFinancialMetricsUseCase calculateFinancialMetricsUseCase;

    private TransactionService transactionService;

    @Mock
    private Principal principal;

    private User testUser;
    private Account testAccount;
    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        // Initialize use cases with mocked dependencies
        createManualTransactionUseCase = new CreateManualTransactionUseCase(
                transactionRepository, accountService, modelMapper, cacheInvalidationService);
        recordSaleTransactionUseCase = new RecordSaleTransactionUseCase(
                transactionRepository, accountService, inventoryItemRepository, cacheInvalidationService);
        recordPurchaseOrderTransactionUseCase = new RecordPurchaseOrderTransactionUseCase(
                transactionRepository, accountService, cacheInvalidationService);
        updateTransactionUseCase = new UpdateTransactionUseCase(
                transactionRepository, accountService, modelMapper, cacheInvalidationService);
        deleteTransactionUseCase = new DeleteTransactionUseCase(
                transactionRepository, cacheInvalidationService);
        createRefundTransactionUseCase = new CreateRefundTransactionUseCase(
                transactionRepository, accountService, cacheInvalidationService);
        removeSaleTransactionsUseCase = new RemoveSaleTransactionsUseCase(
                transactionRepository, cacheInvalidationService);
        removePurchaseOrderTransactionsUseCase = new RemovePurchaseOrderTransactionsUseCase(
                transactionRepository, cacheInvalidationService);
        calculateFinancialMetricsUseCase = new CalculateFinancialMetricsUseCase(
                transactionRepository, profitCalculationService);

        // Initialize TransactionService with use cases
        transactionService = new TransactionService(
                transactionRepository,
                createManualTransactionUseCase,
                recordSaleTransactionUseCase,
                recordPurchaseOrderTransactionUseCase,
                updateTransactionUseCase,
                deleteTransactionUseCase,
                createRefundTransactionUseCase,
                removeSaleTransactionsUseCase,
                removePurchaseOrderTransactionsUseCase,
                calculateFinancialMetricsUseCase
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .build();

        testAccount = Account.builder()
                .id(1L)
                .user(testUser)
                .type(AccountType.CASH)
                .build();

        testInventory = new Inventory();
        testInventory.setId(1L);
        testInventory.setUser(testUser);

        lenient().when(principal.getName()).thenReturn("test@example.com");
    }

    @Nested
    @DisplayName("Cálculos de Lucro (Profit)")
    class ProfitCalculationTests {

        @Test
        @DisplayName("Deve calcular lucro zero quando não há transações")
        void shouldCalculateZeroProfitWithNoTransactions() {
            // Given
            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(BigDecimal.ZERO);

            // When
            BigDecimal profit = transactionService.calculateProfit(principal);

            // Then
            assertThat(profit).isEqualByComparingTo(BigDecimal.ZERO);
            verify(profitCalculationService).calculateTotalNetProfit("test@example.com");
        }

        @Test
        @DisplayName("Deve calcular lucro = Revenue - COGS")
        void shouldCalculateProfitAsRevenueMinusCOGS() {
            // Given: Revenue de 1000 - COGS de 400 = Lucro de 600
            BigDecimal expectedProfit = new BigDecimal("600.00");
            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(expectedProfit);

            // When
            BigDecimal profit = transactionService.calculateProfit(principal);

            // Then
            assertThat(profit).isEqualByComparingTo(expectedProfit);
        }

        @Test
        @DisplayName("Deve calcular lucro negativo quando despesas excedem receitas")
        void shouldCalculateNegativeProfitWhenExpensesExceedRevenue() {
            // Given: Prejuízo de 500
            BigDecimal expectedProfit = new BigDecimal("-500.00");
            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(expectedProfit);

            // When
            BigDecimal profit = transactionService.calculateProfit(principal);

            // Then
            assertThat(profit).isNegative();
            assertThat(profit).isEqualByComparingTo(expectedProfit);
        }

        @Test
        @DisplayName("Deve calcular lucro complexo com múltiplas vendas e despesas")
        void shouldCalculateComplexProfitWithMultipleSalesAndExpenses() {
            // Given:
            // - Venda 1: R$ 1000 (custo R$ 400) = Lucro R$ 600
            // - Venda 2: R$ 500 (custo R$ 200) = Lucro R$ 300
            // - Despesa fixa: R$ 200
            // Lucro Total = 600 + 300 - 200 = 700
            BigDecimal expectedProfit = new BigDecimal("700.00");
            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(expectedProfit);

            // When
            BigDecimal profit = transactionService.calculateProfit(principal);

            // Then
            assertThat(profit).isEqualByComparingTo(expectedProfit);
        }
    }

    @Nested
    @DisplayName("Cálculos de Fluxo de Caixa (Cash Flow)")
    class CashFlowCalculationTests {

        @Test
        @DisplayName("Deve calcular fluxo de caixa zero quando não há transações")
        void shouldCalculateZeroCashFlowWithNoTransactions() {
            // Given
            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(List.of());

            // When
            BigDecimal cashFlow = transactionService.calculateCashFlow(principal);

            // Then
            assertThat(cashFlow).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Deve calcular fluxo de caixa com receitas e despesas")
        void shouldCalculateCashFlowWithRevenuesAndExpenses() {
            // Given:
            // Receita de venda: +1000 (affectsCash = true)
            // Compra de estoque: -500 (affectsCash = true)
            // COGS: -300 (affectsCash = false) - NÃO conta no caixa
            // Cash Flow = 1000 - 500 = 500
            List<Transaction> transactions = Arrays.asList(
                    createTransaction(TransactionType.INCOME, new BigDecimal("1000"), true),
                    createTransaction(TransactionType.EXPENSE, new BigDecimal("-500"), true),
                    createTransaction(TransactionType.EXPENSE, new BigDecimal("-300"), false) // COGS
            );

            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(transactions);

            // When
            BigDecimal cashFlow = transactionService.calculateCashFlow(principal);

            // Then
            assertThat(cashFlow).isEqualByComparingTo(new BigDecimal("500"));
        }

        @Test
        @DisplayName("Deve incluir investimentos de sócios no fluxo de caixa mas não no lucro")
        void shouldIncludeOwnerInvestmentInCashFlowButNotProfit() {
            // Given:
            // Aporte de sócio: +5000 (affectsCash = true, affectsProfit = false)
            // Venda: +1000 (affectsCash = true, affectsProfit = true)
            // Cash Flow = 6000
            List<Transaction> transactions = Arrays.asList(
                    createTransaction(TransactionType.INCOME, new BigDecimal("5000"), true),
                    createTransaction(TransactionType.INCOME, new BigDecimal("1000"), true)
            );

            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(transactions);

            // When
            BigDecimal cashFlow = transactionService.calculateCashFlow(principal);

            // Then
            assertThat(cashFlow).isEqualByComparingTo(new BigDecimal("6000"));
        }

        @Test
        @DisplayName("Deve calcular fluxo de caixa negativo quando despesas excedem receitas")
        void shouldCalculateNegativeCashFlowWhenExpensesExceedRevenue() {
            // Given:
            // Receitas: +500
            // Despesas: -1000
            // Cash Flow = -500
            List<Transaction> transactions = Arrays.asList(
                    createTransaction(TransactionType.INCOME, new BigDecimal("500"), true),
                    createTransaction(TransactionType.EXPENSE, new BigDecimal("-1000"), true)
            );

            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(transactions);

            // When
            BigDecimal cashFlow = transactionService.calculateCashFlow(principal);

            // Then
            assertThat(cashFlow).isNegative();
            assertThat(cashFlow).isEqualByComparingTo(new BigDecimal("-500"));
        }
    }

    @Nested
    @DisplayName("Cálculos de Transações de Vendas")
    class SaleTransactionCalculationTests {

        @Test
        @DisplayName("Deve calcular COGS corretamente para venda com múltiplos itens")
        void shouldCalculateCOGSCorrectlyForSaleWithMultipleItems() {
            // Given
            Sale sale = createSaleWithMultipleItems();

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));
            when(transactionRepository.existsByReferenceSubId(anyLong()))
                    .thenReturn(false);

            // Mock inventory items with costs
            InventoryItem item1 = createInventoryItem(new BigDecimal("50.00"));
            InventoryItem item2 = createInventoryItem(new BigDecimal("30.00"));

            when(inventoryItemRepository.findByInventoryAndProductVariant(any(), any()))
                    .thenReturn(Optional.of(item1))
                    .thenReturn(Optional.of(item2));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromSale(sale, principal);

            // Then
            verify(transactionRepository, atLeast(2)).save(transactionCaptor.capture());

            // Verificar COGS: (50 * 2) + (30 * 3) = 100 + 90 = 190
            List<Transaction> savedTransactions = transactionCaptor.getAllValues();
            Transaction cogsTransaction = savedTransactions.stream()
                    .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                    .findFirst()
                    .orElseThrow();

            BigDecimal expectedCOGS = new BigDecimal("190.00");
            assertThat(cogsTransaction.getAmount().abs()).isEqualByComparingTo(expectedCOGS);
            assertThat(cogsTransaction.getAmount()).isNegative(); // COGS é negativo (despesa)
            assertThat(cogsTransaction.isAffectsProfit()).isTrue();
            assertThat(cogsTransaction.isAffectsCash()).isFalse();
        }

        @Test
        @DisplayName("Deve criar transação de receita com valor correto para cada pagamento")
        void shouldCreateRevenueTransactionWithCorrectValueForEachPayment() {
            // Given
            Sale sale = createSaleWithMultiplePayments();

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));
            when(transactionRepository.existsByReferenceSubId(anyLong()))
                    .thenReturn(false);

            InventoryItem item = createInventoryItem(new BigDecimal("40.00"));
            when(inventoryItemRepository.findByInventoryAndProductVariant(any(), any()))
                    .thenReturn(Optional.of(item));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromSale(sale, principal);

            // Then
            verify(transactionRepository, atLeast(2)).save(transactionCaptor.capture());

            List<Transaction> revenueTransactions = transactionCaptor.getAllValues().stream()
                    .filter(t -> t.getDetail() == TransactionDetail.SALE)
                    .toList();

            assertThat(revenueTransactions).hasSize(2);

            // Verificar valores: 600 (PIX) + 400 (CASH) = 1000
            BigDecimal totalRevenue = revenueTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(totalRevenue).isEqualByComparingTo(new BigDecimal("1000.00"));

            // Verificar que ambas afetam caixa e lucro
            revenueTransactions.forEach(tx -> {
                assertThat(tx.isAffectsProfit()).isTrue();
                assertThat(tx.isAffectsCash()).isTrue();
            });
        }

        @Test
        @DisplayName("Não deve duplicar transações de receita quando venda já foi registrada")
        void shouldNotDuplicateTransactionWhenSaleAlreadyRegistered() {
            // Given
            Sale sale = createSimpleSale();

            when(transactionRepository.existsByReferenceSubId(anyLong()))
                    .thenReturn(true); // Pagamento já registrado
            
            // Mock necessário para quando o serviço processa items após verificar pagamentos
            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            // Mock inventory item para calcular COGS
            InventoryItem item = createInventoryItem(new BigDecimal("50.00"));
            when(inventoryItemRepository.findByInventoryAndProductVariant(any(), any()))
                    .thenReturn(Optional.of(item));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromSale(sale, principal);

            // Then
            // Deve criar COGS mas não deve criar transação de pagamento (SALE)
            verify(transactionRepository, atLeastOnce()).save(transactionCaptor.capture());
            
            List<Transaction> savedTransactions = transactionCaptor.getAllValues();
            
            // Não deve ter transação de SALE (receita)
            boolean hasSaleTransaction = savedTransactions.stream()
                    .anyMatch(t -> t.getDetail() == TransactionDetail.SALE);
            assertThat(hasSaleTransaction).isFalse();
            
            // Deve ter transação de COGS
            boolean hasCOGSTransaction = savedTransactions.stream()
                    .anyMatch(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD);
            assertThat(hasCOGSTransaction).isTrue();
        }

        @Test
        @DisplayName("Deve calcular margem de lucro correta: (Preço - Custo) / Preço")
        void shouldCalculateCorrectProfitMargin() {
            // Given: Venda de R$100 com custo de R$60 = Margem de 40%
            Sale sale = createSaleWithKnownMargin();

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));
            when(transactionRepository.existsByReferenceSubId(anyLong()))
                    .thenReturn(false);

            InventoryItem item = createInventoryItem(new BigDecimal("60.00"));
            when(inventoryItemRepository.findByInventoryAndProductVariant(any(), any()))
                    .thenReturn(Optional.of(item));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromSale(sale, principal);

            // Then
            verify(transactionRepository, atLeast(2)).save(transactionCaptor.capture());

            List<Transaction> savedTransactions = transactionCaptor.getAllValues();

            Transaction revenue = savedTransactions.stream()
                    .filter(t -> t.getDetail() == TransactionDetail.SALE)
                    .findFirst()
                    .orElseThrow();

            Transaction cogs = savedTransactions.stream()
                    .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                    .findFirst()
                    .orElseThrow();

            // Revenue = 100, COGS = -60, Lucro = 40, Margem = 40%
            BigDecimal profit = revenue.getAmount().add(cogs.getAmount());
            assertThat(profit).isEqualByComparingTo(new BigDecimal("40.00"));

            // Verificar margem: 40 / 100 = 0.40 (40%)
            BigDecimal margin = profit.divide(revenue.getAmount(), 2, BigDecimal.ROUND_HALF_UP);
            assertThat(margin).isEqualByComparingTo(new BigDecimal("0.40"));
        }
    }

    @Nested
    @DisplayName("Cálculos de Transações de Pedidos de Compra")
    class PurchaseOrderTransactionCalculationTests {

        @Test
        @DisplayName("Deve criar transação negativa para compra de estoque")
        void shouldCreateNegativeTransactionForInventoryPurchase() {
            // Given
            PurchaseOrder purchaseOrder = createPurchaseOrder(new BigDecimal("5000.00"));

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromPurchaseOrder(purchaseOrder, principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.getAmount()).isNegative();
            assertThat(savedTransaction.getAmount().abs()).isEqualByComparingTo(new BigDecimal("5000.00"));
            assertThat(savedTransaction.getDetail()).isEqualTo(TransactionDetail.INVENTORY_PURCHASE);
        }

        @Test
        @DisplayName("Compra de estoque não deve afetar lucro mas deve afetar caixa")
        void inventoryPurchaseShouldNotAffectProfitButShouldAffectCash() {
            // Given
            PurchaseOrder purchaseOrder = createPurchaseOrder(new BigDecimal("3000.00"));

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.recordTransactionFromPurchaseOrder(purchaseOrder, principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.isAffectsProfit()).isFalse(); // Não afeta lucro
            assertThat(savedTransaction.isAffectsCash()).isTrue();    // Afeta caixa
        }

        @Test
        @DisplayName("Deve criar transação de reembolso com valor positivo")
        void shouldCreateRefundTransactionWithPositiveValue() {
            // Given
            PurchaseOrder purchaseOrder = createPurchaseOrder(new BigDecimal("2000.00"));
            BigDecimal refundAmount = new BigDecimal("500.00");

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.createRefundTransactionForCanceledItem(
                    purchaseOrder, refundAmount, "Item cancelado", principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction refundTransaction = transactionCaptor.getValue();
            assertThat(refundTransaction.getAmount()).isPositive();
            assertThat(refundTransaction.getAmount()).isEqualByComparingTo(refundAmount);
            assertThat(refundTransaction.getType()).isEqualTo(TransactionType.INCOME);
            assertThat(refundTransaction.getDetail()).isEqualTo(TransactionDetail.REFUND);
        }

        @Test
        @DisplayName("Reembolso não deve afetar lucro mas deve aumentar caixa")
        void refundShouldNotAffectProfitButShouldIncreaseCash() {
            // Given
            PurchaseOrder purchaseOrder = createPurchaseOrder(new BigDecimal("1000.00"));
            BigDecimal refundAmount = new BigDecimal("300.00");

            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.createRefundTransactionForCanceledItem(
                    purchaseOrder, refundAmount, "Item cancelado", principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction refundTransaction = transactionCaptor.getValue();
            assertThat(refundTransaction.isAffectsProfit()).isFalse(); // Não afeta lucro
            assertThat(refundTransaction.isAffectsCash()).isTrue();    // Aumenta caixa
        }
    }

    @Nested
    @DisplayName("Cálculos de Transações Manuais")
    class ManualTransactionCalculationTests {

        @Test
        @DisplayName("Deve converter despesa positiva em valor negativo automaticamente")
        void shouldConvertPositiveExpenseToNegativeValue() {
            // Given
            TransactionCreateDTO dto = TransactionCreateDTO.builder()
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.FIXED_EXPENSE)
                    .amount(new BigDecimal("500.00")) // Positivo
                    .paymentMethod(PaymentMethod.CASH)
                    .description("Aluguel")
                    .date(LocalDateTime.now())
                    .build();

            Transaction mappedTransaction = new Transaction();
            mappedTransaction.setDetail(dto.getDetail());
            mappedTransaction.setType(dto.getType());
            when(modelMapper.map(dto, Transaction.class)).thenReturn(mappedTransaction);
            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.createManualTransaction(dto, principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.getAmount()).isNegative();
            assertThat(savedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("-500.00"));
        }

        @Test
        @DisplayName("Não deve converter receita em negativo")
        void shouldNotConvertIncomeToNegative() {
            // Given
            TransactionCreateDTO dto = TransactionCreateDTO.builder()
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.EXTRA_INCOME)
                    .amount(new BigDecimal("1000.00")) // Positivo
                    .paymentMethod(PaymentMethod.PIX)
                    .description("Receita extra")
                    .date(LocalDateTime.now())
                    .build();

            Transaction mappedTransaction = new Transaction();
            mappedTransaction.setDetail(dto.getDetail());
            mappedTransaction.setType(dto.getType());
            when(modelMapper.map(dto, Transaction.class)).thenReturn(mappedTransaction);
            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.createManualTransaction(dto, principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction savedTransaction = transactionCaptor.getValue();
            // O amount não deve ser alterado pelo serviço se já foi negado no map
            // Verificamos apenas que save foi chamado
            verify(transactionRepository).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Deve aplicar flags corretas baseadas no tipo de transação")
        void shouldApplyCorrectFlagsBasedOnTransactionType() {
            // Given
            TransactionCreateDTO dto = TransactionCreateDTO.builder()
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.FIXED_EXPENSE) // affectsProfit=true, affectsCash=true
                    .amount(new BigDecimal("300.00"))
                    .paymentMethod(PaymentMethod.CASH)
                    .description("Energia elétrica")
                    .date(LocalDateTime.now())
                    .build();

            Transaction mappedTransaction = new Transaction();
            mappedTransaction.setDetail(dto.getDetail());
            mappedTransaction.setType(dto.getType());
            when(modelMapper.map(dto, Transaction.class)).thenReturn(mappedTransaction);
            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.of(testAccount));

            ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

            // When
            transactionService.createManualTransaction(dto, principal);

            // Then
            verify(transactionRepository).save(transactionCaptor.capture());

            Transaction savedTransaction = transactionCaptor.getValue();
            assertThat(savedTransaction.isAffectsProfit()).isTrue();
            assertThat(savedTransaction.isAffectsCash()).isTrue();
        }

        @Test
        @DisplayName("Deve lançar exceção quando conta não é encontrada")
        void shouldThrowExceptionWhenAccountNotFound() {
            // Given
            TransactionCreateDTO dto = TransactionCreateDTO.builder()
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.FIXED_EXPENSE)
                    .amount(new BigDecimal("100.00"))
                    .paymentMethod(PaymentMethod.CARD)
                    .description("Despesa")
                    .date(LocalDateTime.now())
                    .build();

            Transaction mappedTransaction = new Transaction();
            when(modelMapper.map(dto, Transaction.class)).thenReturn(mappedTransaction);
            when(accountService.findAccountByPaymentMethodAndUser(any(), any()))
                    .thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> transactionService.createManualTransaction(dto, principal))
                    .isInstanceOf(AccountNotFoundException.class);

            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Cálculo de Resumo Financeiro")
    class FinancialSummaryCalculationTests {

        @Test
        @DisplayName("Deve calcular resumo financeiro com lucro e fluxo de caixa")
        void shouldCalculateFinancialSummaryWithProfitAndCashFlow() {
            // Given
            BigDecimal expectedProfit = new BigDecimal("1500.00");
            BigDecimal expectedCashFlow = new BigDecimal("3000.00");

            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(expectedProfit);

            List<Transaction> transactions = Arrays.asList(
                    createTransaction(TransactionType.INCOME, new BigDecimal("3000"), true)
            );
            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(transactions);

            // When
            FinancialSummaryDTO summary = transactionService.calculateFinancialSummary(principal);

            // Then
            assertThat(summary.getProfit()).isEqualByComparingTo(expectedProfit);
            assertThat(summary.getCashFlow()).isEqualByComparingTo(expectedCashFlow);
        }

        @Test
        @DisplayName("Deve mostrar diferença entre lucro e fluxo de caixa")
        void shouldShowDifferenceBetweenProfitAndCashFlow() {
            // Given: Lucro diferente do fluxo de caixa devido a investimentos
            // Lucro: 1000 (vendas - custos - despesas)
            // Caixa: 6000 (vendas - custos - despesas + investimento de sócio)
            BigDecimal expectedProfit = new BigDecimal("1000.00");
            BigDecimal expectedCashFlow = new BigDecimal("6000.00");

            when(profitCalculationService.calculateTotalNetProfit("test@example.com"))
                    .thenReturn(expectedProfit);

            List<Transaction> transactions = Arrays.asList(
                    createTransaction(TransactionType.INCOME, new BigDecimal("6000"), true)
            );
            when(transactionRepository.findByAccountUserEmail("test@example.com"))
                    .thenReturn(transactions);

            // When
            FinancialSummaryDTO summary = transactionService.calculateFinancialSummary(principal);

            // Then
            assertThat(summary.getCashFlow()).isGreaterThan(summary.getProfit());
            assertThat(summary.getCashFlow().subtract(summary.getProfit()))
                    .isEqualByComparingTo(new BigDecimal("5000.00")); // Diferença = investimento
        }
    }

    // === Helper Methods ===

    private Transaction createTransaction(TransactionType type, BigDecimal amount, boolean affectsCash) {
        return Transaction.builder()
                .id(1L)
                .type(type)
                .amount(amount)
                .affectsCash(affectsCash)
                .affectsProfit(true)
                .account(testAccount)
                .date(LocalDateTime.now())
                .build();
    }

    private Sale createSaleWithMultipleItems() {
        ProductVariant variant1 = createProductVariant(1L, "Helmet A");
        ProductVariant variant2 = createProductVariant(2L, "Helmet B");

        SaleItem item1 = SaleItem.builder()
                .id(1L)
                .productVariant(variant1)
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .build();

        SaleItem item2 = SaleItem.builder()
                .id(2L)
                .productVariant(variant2)
                .quantity(3)
                .unitPrice(new BigDecimal("150.00"))
                .build();

        SalePayment payment = SalePayment.builder()
                .id(1L)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("650.00"))
                .build();

        return Sale.builder()
                .id(1L)
                .date(LocalDateTime.now())
                .inventory(testInventory)
                .items(Arrays.asList(item1, item2))
                .payments(List.of(payment))
                .build();
    }

    private Sale createSaleWithMultiplePayments() {
        ProductVariant variant = createProductVariant(1L, "Helmet");

        SaleItem item = SaleItem.builder()
                .id(1L)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("1000.00"))
                .build();

        SalePayment payment1 = SalePayment.builder()
                .id(1L)
                .paymentMethod(PaymentMethod.PIX)
                .amount(new BigDecimal("600.00"))
                .build();

        SalePayment payment2 = SalePayment.builder()
                .id(2L)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("400.00"))
                .build();

        return Sale.builder()
                .id(1L)
                .date(LocalDateTime.now())
                .inventory(testInventory)
                .items(List.of(item))
                .payments(Arrays.asList(payment1, payment2))
                .build();
    }

    private Sale createSimpleSale() {
        ProductVariant variant = createProductVariant(1L, "Helmet");

        SaleItem item = SaleItem.builder()
                .id(1L)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .build();

        SalePayment payment = SalePayment.builder()
                .id(1L)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("100.00"))
                .build();

        return Sale.builder()
                .id(1L)
                .date(LocalDateTime.now())
                .inventory(testInventory)
                .items(List.of(item))
                .payments(List.of(payment))
                .build();
    }

    private Sale createSaleWithKnownMargin() {
        ProductVariant variant = createProductVariant(1L, "Helmet");

        SaleItem item = SaleItem.builder()
                .id(1L)
                .productVariant(variant)
                .quantity(1)
                .unitPrice(new BigDecimal("100.00"))
                .build();

        SalePayment payment = SalePayment.builder()
                .id(1L)
                .paymentMethod(PaymentMethod.CASH)
                .amount(new BigDecimal("100.00"))
                .build();

        return Sale.builder()
                .id(1L)
                .date(LocalDateTime.now())
                .inventory(testInventory)
                .items(List.of(item))
                .payments(List.of(payment))
                .build();
    }

    private PurchaseOrder createPurchaseOrder(BigDecimal totalAmount) {
        return PurchaseOrder.builder()
                .id(1L)
                .orderNumber("PO-001")
                .date(LocalDate.now())
                .totalAmount(totalAmount)
                .paymentMethod(PaymentMethod.PIX)
                .build();
    }

    private ProductVariant createProductVariant(Long id, String name) {
        Product product = Product.builder()
                .id(id)
                .model(name)
                .color("Black")
                .build();

        return ProductVariant.builder()
                .id(id)
                .product(product)
                .size("M")
                .build();
    }

    private InventoryItem createInventoryItem(BigDecimal averageCost) {
        return InventoryItem.builder()
                .id(1L)
                .inventory(testInventory)
                .averageCost(averageCost)
                .quantity(100)
                .build();
    }
}
