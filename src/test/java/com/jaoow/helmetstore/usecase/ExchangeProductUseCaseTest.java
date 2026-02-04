package com.jaoow.helmetstore.usecase;

import com.jaoow.helmetstore.dto.sale.ProductExchangeRequestDTO;
import com.jaoow.helmetstore.dto.sale.ProductExchangeResponseDTO;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleItemCreateDTO;
import com.jaoow.helmetstore.dto.sale.SalePaymentCreateDTO;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.ExchangeReason;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.*;
import com.jaoow.helmetstore.repository.user.UserRepository;
import com.jaoow.helmetstore.usecase.sale.CreateSaleUseCase;
import com.jaoow.helmetstore.usecase.sale.ExchangeProductUseCase;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jaoow.helmetstore.exception.BusinessException;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.model.sale.SaleStatus;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ExchangeProductUseCaseTest {

    @Autowired
    private ExchangeProductUseCase exchangeProductUseCase;

    @Autowired
    private CreateSaleUseCase createSaleUseCase;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private Principal testPrincipal;
    private Inventory testInventory;
    private User testUser;
    private Account cashAccount;
    private Product productX;
    private Product productZ;
    private ProductVariant variantX;
    private ProductVariant variantZ;

    @BeforeEach
    public void setup() {
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

        // Create principal
        testPrincipal = new UsernamePasswordAuthenticationToken(
                testUser.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // Create test category
        var category = com.jaoow.helmetstore.model.Category.builder()
                .name("Test Category")
                .inventory(testInventory)
                .build();
        category = categoryRepository.save(category);

        productX = Product.builder()
                .model("Capacete X")
                .color("Preto")
                .salePrice(BigDecimal.valueOf(100.00))
                .inventory(testInventory)
                .category(category)
                .build();
        productX = productRepository.save(productX);

        productZ = Product.builder()
                .model("Capacete Z")
                .color("Vermelho")
                .salePrice(BigDecimal.valueOf(150.00))
                .inventory(testInventory)
                .category(category)
                .build();
        productZ = productRepository.save(productZ);

        // Create variants
        variantX = ProductVariant.builder()
                .product(productX)
                .size("M")
                .sku("CAP-X-M")
                .build();
        variantX = productVariantRepository.save(variantX);

        variantZ = ProductVariant.builder()
                .product(productZ)
                .size("M")
                .sku("CAP-Z-M")
                .build();
        variantZ = productVariantRepository.save(variantZ);

        // Create inventory items with stock
        InventoryItem itemX = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variantX)
                .quantity(10)
                .averageCost(BigDecimal.valueOf(50.00))
                .build();
        inventoryItemRepository.save(itemX);

        InventoryItem itemZ = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variantZ)
                .quantity(10)
                .averageCost(BigDecimal.valueOf(80.00))
                .build();
        inventoryItemRepository.save(itemZ);
    }

    @AfterEach
    public void tearDown() {
        // Clean up test data in correct order (respecting foreign keys)
        if (testUser != null && testUser.getId() != null) {
            // Delete transactions first
            transactionRepository.deleteAll();
            
            // Delete sales and related entities
            saleRepository.deleteAll();
            
            // Delete inventory items
            inventoryItemRepository.deleteAll();
            
            // Delete product variants and products
            productVariantRepository.deleteAll();
            productRepository.deleteAll();
            
            // Delete category
            categoryRepository.deleteAll();
            
            // Delete accounts
            accountRepository.deleteAll();
            
            // Delete inventory
            if (testInventory != null) {
                inventoryRepository.delete(testInventory);
            }
            
            // Finally delete user
            userRepository.delete(testUser);
        }
    }

    @Test
    @DisplayName("Troca por produto mais caro - Valida todas as transações e estoque")
    public void testExchangeForMoreExpensiveProduct() {
        // ========================================================================
        // SETUP: Criar venda original (Produto X - R$ 100)
        // ========================================================================
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);
        Long originalSaleId = originalSaleResponse.getId();

        // Verificar estado inicial
        InventoryItem itemXBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        InventoryItem itemZBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();

        assertThat(itemXBefore.getQuantity()).isEqualTo(9); // 10 - 1 vendido
        assertThat(itemZBefore.getQuantity()).isEqualTo(10);

        // Contar transações antes da troca
        List<Transaction> transactionsBeforeExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        long revenueTransactionsBeforeCount = transactionsBeforeExchange.stream()
                .filter(t -> t.getType() == TransactionType.INCOME && t.getDetail() == TransactionDetail.SALE)
                .count();
        long cogsTransactionsBeforeCount = transactionsBeforeExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .count();

        // ========================================================================
        // AÇÃO: Realizar troca por produto mais caro (Produto Z - R$ 150)
        // ========================================================================
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleId)
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(50.00)) // Diferença
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .notes("Teste de troca por produto mais caro")
                .build();

        ProductExchangeResponseDTO exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // ========================================================================
        // VALIDAÇÕES
        // ========================================================================

        // 1. NÃO deve gerar reembolso
        assertThat(exchangeResponse.getHasRefund())
                .as("Não deve ter reembolso ao trocar por produto mais caro")
                .isFalse();
        assertThat(exchangeResponse.getRefundAmount())
                .as("Valor de reembolso deve ser null")
                .isNull();

        // 2. Produto original deve voltar ao estoque
        InventoryItem itemXAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXAfter.getQuantity())
                .as("Produto X deve voltar ao estoque (10)")
                .isEqualTo(10);

        // 3. Produto novo deve sair do estoque
        InventoryItem itemZAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        assertThat(itemZAfter.getQuantity())
                .as("Produto Z deve sair do estoque (9)")
                .isEqualTo(9);

        // 4. Deve gerar transação complementar da diferença (R$ 50)
        List<Transaction> transactionsAfterExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        List<Transaction> revenueTransactions = transactionsAfterExchange.stream()
                .filter(t -> t.getType() == TransactionType.INCOME && t.getDetail() == TransactionDetail.SALE)
                .toList();

        assertThat(revenueTransactions.size())
                .as("Deve ter 2 transações de receita (original + complementar)")
                .isEqualTo((int) revenueTransactionsBeforeCount + 1);

        // Verificar transação complementar
        Transaction complementTransaction = revenueTransactions.stream()
                .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(50.00)) == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transação complementar de R$ 50 não encontrada"));

        assertThat(complementTransaction.getPaymentMethod())
                .as("Transação complementar deve ser em CASH")
                .isEqualTo(PaymentMethod.CASH);

        // 5. Deve gerar reembolso de COGS do produto original (usando COGS_REVERSAL)
        List<Transaction> cogsTransactions = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD ||
                            t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .toList();

        // Deve ter exatamente: 1 COGS original (-50), 1 reversão COGS (+50), 1 novo COGS (-80)
        assertThat(cogsTransactions.size())
                .as("Deve ter exatamente 3 transações COGS")
                .isEqualTo((int) cogsTransactionsBeforeCount + 2);

        // Verificar COGS reversão (positivo) usando o TransactionDetail específico
        Transaction cogsReversal = cogsTransactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transação de reversão COGS não encontrada"));

        assertThat(cogsReversal.getAmount())
                .as("COGS reversal deve ser positivo (R$ 50)")
                .isEqualByComparingTo(BigDecimal.valueOf(50.00));

        // ⚠️ VALIDAÇÃO CRÍTICA: A transação COGS_REVERSAL deve ter referência de EXCHANGE
        // Isso garante que veio do fluxo de troca e não de uma venda normal reaproveitada
        assertThat(cogsReversal.getReference())
                .as("COGS reversal DEVE ter referência de EXCHANGE")
                .contains("EXCHANGE");

        // 6. Deve gerar saída de COGS do novo produto
        Transaction newCogs = cogsTransactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD &&
                            t.getAmount().compareTo(BigDecimal.valueOf(-80.00)) == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("COGS do novo produto não encontrado"));

        assertThat(newCogs.getAmount())
                .as("COGS do novo produto deve ser R$ -80")
                .isEqualByComparingTo(BigDecimal.valueOf(-80.00));

        // 7. Validar resposta da troca
        assertThat(exchangeResponse.getAmountDifference())
                .as("Diferença deve ser R$ 50")
                .isEqualByComparingTo(BigDecimal.valueOf(50.00));

        assertThat(exchangeResponse.getHasAdditionalCharge())
                .as("Deve ter cobrança adicional")
                .isTrue();

        assertThat(exchangeResponse.getAdditionalChargeAmount())
                .as("Valor da cobrança adicional deve ser R$ 50")
                .isEqualByComparingTo(BigDecimal.valueOf(50.00));

        // 8. Verificar que venda original foi marcada como EXCHANGED (não CANCELLED)
        Sale originalSaleAfter = saleRepository.findById(originalSaleId).orElseThrow();
        assertThat(originalSaleAfter.getStatus())
                .as("Venda original deve estar marcada como EXCHANGED (parte de uma troca)")
                .isEqualTo(com.jaoow.helmetstore.model.sale.SaleStatus.EXCHANGED);

        // ========================================================================
        // VALIDAÇÕES CRÍTICAS DE INTEGRIDADE CONTÁBIL
        // ========================================================================

        // 9. Nova venda derivada de troca NÃO pode ter lucro próprio
        Sale newSale = saleRepository.findById(exchangeResponse.getNewSaleId()).orElseThrow();
        assertThat(newSale.getTotalProfit())
                .as("Venda derivada de troca NÃO pode ter lucro próprio - lucro vem apenas da diferença")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // 10. Nova venda deve estar marcada como derivada de troca
        assertThat(newSale.getIsDerivedFromExchange())
                .as("Nova venda deve estar marcada como isDerivedFromExchange = true")
                .isTrue();

        // 11. Não deve haver duplicação de receita - apenas 1 transação SALE adicional (a diferença)
        long saleTransactionsAfter = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .count();

        assertThat(saleTransactionsAfter)
                .as("Não deve existir duplicação de receita em troca - apenas original + diferença")
                .isEqualTo(revenueTransactionsBeforeCount + 1);
    }

    @Test
    @DisplayName("Troca por produto de mesmo valor - Não deve exigir pagamentos")
    public void testExchangeForSameValueProduct() {
        // ========================================================================
        // SETUP: Criar venda original (Produto X - R$ 100)
        // ========================================================================
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);
        Long originalSaleId = originalSaleResponse.getId();

        // Contar transações antes da troca
        List<Transaction> transactionsBeforeExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        long saleTransactionsBeforeCount = transactionsBeforeExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .count();

        // Criar segundo produto com mesmo valor
        Product productY = Product.builder()
                .model("Capacete Y")
                .color("Azul")
                .salePrice(BigDecimal.valueOf(100.00))
                .inventory(testInventory)
                .category(productX.getCategory())
                .build();
        productY = productRepository.save(productY);

        ProductVariant variantY = ProductVariant.builder()
                .product(productY)
                .size("M")
                .sku("CAP-Y-M")
                .build();
        variantY = productVariantRepository.save(variantY);

        InventoryItem itemY = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variantY)
                .quantity(10)
                .averageCost(BigDecimal.valueOf(50.00))
                .build();
        inventoryItemRepository.save(itemY);

        // ========================================================================
        // AÇÃO: Realizar troca por produto de mesmo valor (Produto Y - R$ 100)
        // ========================================================================
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleId)
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantY.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .newSalePayments(List.of()) // Sem pagamentos - valores iguais
                .reason(ExchangeReason.DEFEITO)
                .notes("Teste de troca por produto de mesmo valor")
                .build();

        // ========================================================================
        // VALIDAÇÕES
        // ========================================================================
        ProductExchangeResponseDTO exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // 1. Não deve ter reembolso nem cobrança adicional
        assertThat(exchangeResponse.getHasRefund())
                .as("Não deve ter reembolso em troca de mesmo valor")
                .isFalse();
        assertThat(exchangeResponse.getHasAdditionalCharge())
                .as("Não deve ter cobrança adicional em troca de mesmo valor")
                .isFalse();
        assertThat(exchangeResponse.getAmountDifference())
                .as("Diferença deve ser zero")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // 2. Produto original deve voltar ao estoque
        InventoryItem itemXAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXAfter.getQuantity())
                .as("Produto X deve voltar ao estoque (10)")
                .isEqualTo(10);

        // 3. Produto novo deve sair do estoque
        InventoryItem itemYAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantY)
                .orElseThrow();
        assertThat(itemYAfter.getQuantity())
                .as("Produto Y deve sair do estoque (9)")
                .isEqualTo(9);

        // 4. Nova venda deve ter sido criada
        Sale newSale = saleRepository.findById(exchangeResponse.getNewSaleId()).orElseThrow();

        // ⚠️ VALIDAÇÃO CRÍTICA: Venda derivada de troca NÃO pode ter lucro próprio
        assertThat(newSale.getTotalProfit())
                .as("Venda derivada de troca NÃO pode ter lucro próprio")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // ⚠️ Nova venda deve estar marcada como derivada de troca
        assertThat(newSale.getIsDerivedFromExchange())
                .as("Nova venda deve estar marcada como isDerivedFromExchange = true")
                .isTrue();

        // 5. Verificar que venda original foi marcada como EXCHANGED
        Sale originalSaleAfter = saleRepository.findById(originalSaleId).orElseThrow();
        assertThat(originalSaleAfter.getStatus())
                .as("Venda original deve estar marcada como EXCHANGED")
                .isEqualTo(com.jaoow.helmetstore.model.sale.SaleStatus.EXCHANGED);

        // ========================================================================
        // VALIDAÇÕES CRÍTICAS DE INTEGRIDADE CONTÁBIL (TROCA DE MESMO VALOR)
        // ========================================================================

        // 6. Em troca de mesmo valor, NÃO deve haver nenhuma transação SALE adicional
        // (nenhum dinheiro novo entrou)
        List<Transaction> transactionsAfterExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        long saleTransactionsAfterCount = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .count();

        assertThat(saleTransactionsAfterCount)
                .as("Em troca de mesmo valor, NÃO deve criar transação SALE adicional")
                .isEqualTo(saleTransactionsBeforeCount);

        // 7. Deve ter COGS reversal com referência EXCHANGE
        Transaction cogsReversal = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transação de reversão COGS não encontrada"));

        assertThat(cogsReversal.getReference())
                .as("COGS reversal deve ter referência de EXCHANGE")
                .contains("EXCHANGE");
    }

    // ========================================================================
    // 🧪 TESTES ESSENCIAIS ADICIONAIS
    // ========================================================================

    @Test
    @DisplayName("Troca por produto MAIS BARATO - Valida reembolso e transações")
    public void testExchangeForCheaperProduct() {
        // ========================================================================
        // SETUP: Criar venda original (Produto Z - R$ 150, COGS R$ 80)
        // ========================================================================
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);
        Long originalSaleId = originalSaleResponse.getId();

        // Verificar estado inicial
        InventoryItem itemZBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        InventoryItem itemXBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();

        assertThat(itemZBefore.getQuantity()).isEqualTo(9); // 10 - 1 vendido
        assertThat(itemXBefore.getQuantity()).isEqualTo(10);

        // Contar transações antes da troca
        List<Transaction> transactionsBeforeExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        long cogsTransactionsBeforeCount = transactionsBeforeExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .count();

        // ========================================================================
        // AÇÃO: Realizar troca por produto MAIS BARATO (Produto X - R$ 100, COGS R$ 50)
        // ========================================================================
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleId)
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .newSalePayments(List.of()) // Sem pagamentos - cliente recebe reembolso
                .refundPaymentMethod(PaymentMethod.CASH) // Método do reembolso
                .reason(ExchangeReason.PREFERENCIA)
                .notes("Teste de troca por produto mais barato")
                .build();

        ProductExchangeResponseDTO exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // ========================================================================
        // VALIDAÇÕES
        // ========================================================================

        // 1. DEVE gerar reembolso de R$ 50
        assertThat(exchangeResponse.getHasRefund())
                .as("Deve ter reembolso ao trocar por produto mais barato")
                .isTrue();
        assertThat(exchangeResponse.getRefundAmount())
                .as("Valor de reembolso deve ser R$ 50")
                .isEqualByComparingTo(BigDecimal.valueOf(50.00));
        assertThat(exchangeResponse.getAmountDifference())
                .as("Diferença deve ser R$ -50 (negativa)")
                .isEqualByComparingTo(BigDecimal.valueOf(-50.00));

        // 2. Produto original (Z) deve voltar ao estoque
        InventoryItem itemZAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        assertThat(itemZAfter.getQuantity())
                .as("Produto Z deve voltar ao estoque (10)")
                .isEqualTo(10);

        // 3. Produto novo (X) deve sair do estoque
        InventoryItem itemXAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXAfter.getQuantity())
                .as("Produto X deve sair do estoque (9)")
                .isEqualTo(9);

        // 4. Verificar transação de REFUND (SALE_REFUND)
        List<Transaction> transactionsAfterExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        Transaction refundTransaction = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.SALE_REFUND)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transação de REFUND não encontrada"));

        assertThat(refundTransaction.getAmount())
                .as("Refund deve ser R$ -50 (saída de caixa)")
                .isEqualByComparingTo(BigDecimal.valueOf(-50.00));
        assertThat(refundTransaction.getPaymentMethod())
                .as("Refund deve usar método CASH")
                .isEqualTo(PaymentMethod.CASH);

        // 5. Verificar COGS: reversão do antigo (+80) e novo COGS (-50)
        List<Transaction> cogsTransactions = transactionsAfterExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD ||
                            t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .toList();

        // Deve ter: 1 COGS original (-80), 1 reversão (+80), 1 novo COGS (-50)
        assertThat(cogsTransactions.size())
                .as("Deve ter 3 transações COGS (original + reversão + novo)")
                .isEqualTo((int) cogsTransactionsBeforeCount + 2);

        // Verificar COGS reversal (+80)
        Transaction cogsReversal = cogsTransactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("COGS reversal não encontrado"));

        assertThat(cogsReversal.getAmount())
                .as("COGS reversal deve ser +80 (devolve custo do produto caro)")
                .isEqualByComparingTo(BigDecimal.valueOf(80.00));

        // Verificar novo COGS (-50)
        Transaction newCogs = cogsTransactions.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD &&
                            t.getAmount().compareTo(BigDecimal.valueOf(-50.00)) == 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Novo COGS (-50) não encontrado"));

        assertThat(newCogs.getAmount())
                .as("Novo COGS deve ser -50")
                .isEqualByComparingTo(BigDecimal.valueOf(-50.00));

        // 6. Venda original marcada como EXCHANGED
        Sale originalSaleAfter = saleRepository.findById(originalSaleId).orElseThrow();
        assertThat(originalSaleAfter.getStatus())
                .as("Venda original deve estar EXCHANGED")
                .isEqualTo(SaleStatus.EXCHANGED);

        // 7. Nova venda deve ter lucro ZERO
        Sale newSale = saleRepository.findById(exchangeResponse.getNewSaleId()).orElseThrow();
        assertThat(newSale.getTotalProfit())
                .as("Nova venda derivada deve ter lucro ZERO")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(newSale.getIsDerivedFromExchange())
                .as("Nova venda deve estar marcada como derivada de troca")
                .isTrue();
    }

    @Test
    @DisplayName("Troca SEM pagamento quando deveria exigir - Deve lançar exceção")
    public void testExchangeWithoutRequiredPayment() {
        // SETUP: Criar venda original (Produto X - R$ 100)
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);

        // Guardar estado do estoque antes da tentativa
        InventoryItem itemZBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        int stockZBefore = itemZBefore.getQuantity();

        // AÇÃO: Tentar trocar por produto MAIS CARO sem enviar pagamento
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00)) // Mais caro!
                                .build()
                ))
                .newSalePayments(List.of()) // ❌ SEM PAGAMENTO - erro!
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // VALIDAÇÃO: Deve lançar exceção
        assertThatThrownBy(() -> exchangeProductUseCase.execute(exchangeRequest, testPrincipal))
                .as("Deve lançar exceção quando pagamento insuficiente")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pagamentos");

        // Estoque NÃO deve ter sido alterado
        InventoryItem itemZAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        assertThat(itemZAfter.getQuantity())
                .as("Estoque não deve ter sido alterado após erro")
                .isEqualTo(stockZBefore);
    }

    @Test
    @DisplayName("Pagamento MAIOR que a diferença - Deve lançar exceção")
    public void testExchangeWithExcessivePayment() {
        // SETUP: Criar venda original (Produto X - R$ 100)
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);

        // AÇÃO: Tentar trocar enviando pagamento MAIOR que a diferença
        // Diferença: R$ 150 - R$ 100 = R$ 50, mas pagamento é R$ 60
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(60.00)) // ❌ Maior que R$ 50!
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // VALIDAÇÃO: Deve lançar exceção
        assertThatThrownBy(() -> exchangeProductUseCase.execute(exchangeRequest, testPrincipal))
                .as("Deve lançar exceção quando pagamento excede a diferença")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pagamentos");
    }

    @Test
    @DisplayName("Troca parcial - Apenas 1 de 2 unidades")
    public void testPartialExchange() {
        // ========================================================================
        // SETUP: Criar venda original com 2 unidades (Produto X - R$ 100 cada)
        // ========================================================================
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(2) // ⚠️ 2 unidades
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(200.00)) // 2 x R$ 100
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);
        Long originalSaleId = originalSaleResponse.getId();

        // Verificar estoque inicial
        InventoryItem itemXBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXBefore.getQuantity()).isEqualTo(8); // 10 - 2 vendidos

        // Contar transações antes
        List<Transaction> transactionsBeforeExchange = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        long cogsBeforeCount = transactionsBeforeExchange.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .count();

        // ========================================================================
        // AÇÃO: Trocar APENAS 1 unidade por produto Z (R$ 150)
        // ========================================================================
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleId)
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1) // ⚠️ Apenas 1 unidade
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(50.00)) // Diferença R$ 150 - R$ 100
                                .build()
                ))
                .reason(ExchangeReason.TAMANHO)
                .notes("Troca parcial - apenas 1 unidade")
                .build();

        ProductExchangeResponseDTO exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // ========================================================================
        // VALIDAÇÕES
        // ========================================================================

        // 1. Estoque do produto X: volta apenas 1 unidade
        InventoryItem itemXAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXAfter.getQuantity())
                .as("Produto X deve ter voltado 1 unidade ao estoque (8 + 1 = 9)")
                .isEqualTo(9);

        // 2. Estoque do produto Z: sai 1 unidade
        InventoryItem itemZAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        assertThat(itemZAfter.getQuantity())
                .as("Produto Z deve ter saído 1 unidade (10 - 1 = 9)")
                .isEqualTo(9);

        // 3. COGS proporcional: reversal apenas do custo de 1 unidade (R$ 50)
        List<Transaction> transactionsAfter = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        Transaction cogsReversal = transactionsAfter.stream()
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("COGS reversal não encontrado"));

        assertThat(cogsReversal.getAmount())
                .as("COGS reversal deve ser proporcional a 1 unidade (R$ 50)")
                .isEqualByComparingTo(BigDecimal.valueOf(50.00));

        // 4. Venda original permanece válida (EXCHANGED, não totalmente cancelada)
        Sale originalSaleAfter = saleRepository.findById(originalSaleId).orElseThrow();
        assertThat(originalSaleAfter.getStatus())
                .as("Venda original deve estar EXCHANGED")
                .isEqualTo(SaleStatus.EXCHANGED);

        // 5. O item original deve ter cancelledQuantity = 1
        var originalItem = originalSaleAfter.getItems().get(0);
        assertThat(originalItem.getCancelledQuantity())
                .as("Item original deve ter 1 unidade cancelada")
                .isEqualTo(1);
        assertThat(originalItem.getIsCancelled())
                .as("Item NÃO deve estar totalmente cancelado (ainda tem 1 unidade ativa)")
                .isFalse();

        // 6. Nova venda criada apenas para o item trocado
        Sale newSale = saleRepository.findById(exchangeResponse.getNewSaleId()).orElseThrow();
        assertThat(newSale.getItems()).hasSize(1);
        assertThat(newSale.getItems().get(0).getQuantity())
                .as("Nova venda deve ter apenas 1 unidade")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Tentativa de trocar venda CANCELLED - Deve lançar exceção")
    public void testExchangeCancelledSale() {
        // SETUP: Criar venda original
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);

        // Cancelar a venda completamente
        Sale sale = saleRepository.findById(originalSaleResponse.getId()).orElseThrow();
        sale.setStatus(SaleStatus.CANCELLED);
        saleRepository.saveAndFlush(sale);

        // Guardar estado do estoque
        InventoryItem itemZBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        int stockZBefore = itemZBefore.getQuantity();

        // Contar transações antes
        long transactionCountBefore = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();

        // AÇÃO: Tentar trocar venda cancelada
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(50.00))
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // VALIDAÇÃO: Deve lançar exceção
        assertThatThrownBy(() -> exchangeProductUseCase.execute(exchangeRequest, testPrincipal))
                .as("Deve lançar exceção ao tentar trocar venda cancelada")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cancelada");

        // Nenhuma alteração de estoque
        InventoryItem itemZAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantZ)
                .orElseThrow();
        assertThat(itemZAfter.getQuantity())
                .as("Estoque não deve ter sido alterado")
                .isEqualTo(stockZBefore);

        // Nenhuma transação criada
        long transactionCountAfter = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();
        assertThat(transactionCountAfter)
                .as("Nenhuma transação deve ter sido criada")
                .isEqualTo(transactionCountBefore);
    }

    @Test
    @DisplayName("Tentativa de trocar venda já EXCHANGED - Deve lançar exceção")
    public void testExchangeAlreadyExchangedSale() {
        // SETUP: Criar e realizar primeira troca
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);

        // Primeira troca (legítima)
        ProductExchangeRequestDTO firstExchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantZ.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(150.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(50.00))
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // Executar primeira troca
        exchangeProductUseCase.execute(firstExchangeRequest, testPrincipal);

        // Verificar que venda original está EXCHANGED
        Sale originalSaleAfterFirst = saleRepository.findById(originalSaleResponse.getId()).orElseThrow();
        assertThat(originalSaleAfterFirst.getStatus()).isEqualTo(SaleStatus.EXCHANGED);

        // Contar transações antes da segunda tentativa
        long transactionCountBefore = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();

        // AÇÃO: Tentar segunda troca na mesma venda
        ProductExchangeRequestDTO secondExchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1) // Tentando devolver item já devolvido
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .newSalePayments(List.of())
                .refundPaymentMethod(PaymentMethod.CASH)
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // VALIDAÇÃO: Deve lançar exceção (item já cancelado)
        assertThatThrownBy(() -> exchangeProductUseCase.execute(secondExchangeRequest, testPrincipal))
                .as("Deve lançar exceção ao tentar trocar item já trocado")
                .isInstanceOf(BusinessException.class);

        // Nenhuma transação adicional criada
        long transactionCountAfter = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();
        assertThat(transactionCountAfter)
                .as("Nenhuma transação adicional deve ter sido criada na segunda tentativa")
                .isEqualTo(transactionCountBefore);
    }

    @Test
    @DisplayName("Estoque insuficiente do novo produto - Deve lançar exceção")
    public void testExchangeWithInsufficientStock() {
        // SETUP: Criar produto com estoque baixo
        Product productLowStock = Product.builder()
                .model("Capacete Raro")
                .color("Dourado")
                .salePrice(BigDecimal.valueOf(200.00))
                .inventory(testInventory)
                .category(productX.getCategory())
                .build();
        productLowStock = productRepository.save(productLowStock);

        ProductVariant variantLowStock = ProductVariant.builder()
                .product(productLowStock)
                .size("M")
                .sku("CAP-RARO-M")
                .build();
        variantLowStock = productVariantRepository.save(variantLowStock);

        // Criar com apenas 1 unidade em estoque
        InventoryItem itemLowStock = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variantLowStock)
                .quantity(1) // ⚠️ Apenas 1 unidade
                .averageCost(BigDecimal.valueOf(100.00))
                .build();
        inventoryItemRepository.save(itemLowStock);

        // Criar venda original
        SaleCreateDTO originalSaleDTO = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantX.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(100.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSaleDTO, testPrincipal);

        // Guardar estado antes
        InventoryItem itemXBefore = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        int stockXBefore = itemXBefore.getQuantity();

        long transactionCountBefore = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();

        // AÇÃO: Tentar trocar por 2 unidades do produto com estoque insuficiente
        final ProductVariant finalVariantLowStock = variantLowStock;
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(finalVariantLowStock.getId())
                                .quantity(2) // ❌ Quer 2, mas só tem 1!
                                .unitPrice(BigDecimal.valueOf(200.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(300.00)) // 2 x 200 - 100 = 300
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        // VALIDAÇÃO: Deve lançar exceção de estoque insuficiente
        assertThatThrownBy(() -> exchangeProductUseCase.execute(exchangeRequest, testPrincipal))
                .as("Deve lançar exceção quando estoque insuficiente")
                .isInstanceOf(RuntimeException.class); // Pode ser InsufficientStockException ou BusinessException

        // Nenhuma reversão de estoque
        InventoryItem itemXAfter = inventoryItemRepository
                .findByInventoryAndProductVariant(testInventory, variantX)
                .orElseThrow();
        assertThat(itemXAfter.getQuantity())
                .as("Estoque original não deve ter sido alterado")
                .isEqualTo(stockXBefore);

        // Nenhuma transação criada
        long transactionCountAfter = transactionRepository
                .findByAccountUserEmail(testUser.getEmail()).size();
        assertThat(transactionCountAfter)
                .as("Nenhuma transação deve ter sido criada")
                .isEqualTo(transactionCountBefore);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // 🧪 TESTES DE RECEITA E LUCRO
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Cenário 1: Troca Produto A → Produto B (custo maior) - Lucro ajustado de 40 → 30")
    public void testScenario1_ExchangeForHigherCostProduct() {
        // SETUP: Produto A (custo 80, venda 120) → Produto B (custo 90, venda 120)
        // Produto A
        ProductVariant variantA = createProductWithVariant("Produto A", "A-1", BigDecimal.valueOf(80.00), 10);
        
        // Produto B
        ProductVariant variantB = createProductWithVariant("Produto B", "B-1", BigDecimal.valueOf(90.00), 10);

        // Venda Original: 1x Produto A por 120
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // Verificar lucro inicial: 120 - 80 = 40
        List<Transaction> originalTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());
        
        BigDecimal originalRevenue = originalTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal originalCOGS = originalTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(originalRevenue).isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(originalCOGS).isEqualByComparingTo(BigDecimal.valueOf(-80.00));
        BigDecimal originalProfit = originalRevenue.add(originalCOGS);
        assertThat(originalProfit).isEqualByComparingTo(BigDecimal.valueOf(40.00));

        // AÇÃO: Trocar Produto A por Produto B (mesma venda 120, mas custo 90)
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantB.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .newSalePayments(List.of()) // Sem pagamento adicional
                .reason(ExchangeReason.DEFEITO)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO: Verificar lucro após troca
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        // Receita total (deve permanecer 120)
        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // COGS total
        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        // Receita: 120, COGS líquido: -90 (reverso +80, novo -90)
        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-90.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro deve ser ajustado de 40 → 30")
                .isEqualByComparingTo(BigDecimal.valueOf(30.00));
    }

    @Test
    @DisplayName("Cenário 2: Troca Produto A → Produto C (custo maior, venda maior com complemento)")
    public void testScenario2_ExchangeWithAdditionalCharge() {
        // SETUP: Produto A (custo 80, venda 120) → Produto C (custo 100, venda 140)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-2", BigDecimal.valueOf(80.00), 10);
        ProductVariant variantC = createProductWithVariant("Produto C", "C-1", BigDecimal.valueOf(100.00), 10);

        // Venda Original: 1x Produto A por 120
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar por Produto C com complemento de 20 (140 - 120)
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantC.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(140.00))
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(20.00)) // Complemento
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        // Receita total = 120 (original) + 20 (complemento) = 140
        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // COGS: reverso +80, novo -100 = -100
        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(140.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-100.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro deve ser 140 - 100 = 40")
                .isEqualByComparingTo(BigDecimal.valueOf(40.00));
    }

    @Test
    @DisplayName("Cenário 3: Troca por produto de custo menor - Lucro aumenta")
    public void testScenario3_ExchangeForLowerCostProduct() {
        // SETUP: Produto A (custo 80, venda 120) → Produto D (custo 60, venda 120)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-3", BigDecimal.valueOf(80.00), 10);
        ProductVariant variantD = createProductWithVariant("Produto D", "D-1", BigDecimal.valueOf(60.00), 10);

        // Venda Original
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar por produto de custo menor
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantD.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .newSalePayments(List.of())
                .reason(ExchangeReason.DEFEITO)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-60.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro aumenta para 120 - 60 = 60")
                .isEqualByComparingTo(BigDecimal.valueOf(60.00));
    }

    @Test
    @DisplayName("Cenário 4: Troca sem alteração de custo - Lucro permanece")
    public void testScenario4_ExchangeNeutralCost() {
        // SETUP: Produto A (custo 80, venda 120) → Produto E (custo 80, venda 120)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-4", BigDecimal.valueOf(80.00), 10);
        ProductVariant variantE = createProductWithVariant("Produto E", "E-1", BigDecimal.valueOf(80.00), 10);

        // Venda Original
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar por produto de mesmo custo
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantE.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .newSalePayments(List.of())
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-80.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro permanece em 40 (troca neutra)")
                .isEqualByComparingTo(BigDecimal.valueOf(40.00));
    }

    @Test
    @DisplayName("Cenário 5: Troca gera prejuízo - Sistema registra corretamente")
    public void testScenario5_ExchangeGeneratesLoss() {
        // SETUP: Produto A (custo 80, venda 120) → Produto F (custo 130, venda 120)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-5", BigDecimal.valueOf(80.00), 10);
        ProductVariant variantF = createProductWithVariant("Produto F", "F-1", BigDecimal.valueOf(130.00), 10);

        // Venda Original
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar por produto com custo muito alto
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantF.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .newSalePayments(List.of())
                .reason(ExchangeReason.DEFEITO)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(120.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-130.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Sistema permite prejuízo: 120 - 130 = -10")
                .isEqualByComparingTo(BigDecimal.valueOf(-10.00));
    }

    @Test
    @DisplayName("Cenário 6: Troca com complemento parcial")
    public void testScenario6_ExchangeWithPartialComplement() {
        // SETUP: Produto A (custo 80, venda 120) → Produto G (custo 100, venda 140)
        // Cliente paga complemento de apenas 10 (não cobre todo aumento)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-6", BigDecimal.valueOf(80.00), 10);
        ProductVariant variantG = createProductWithVariant("Produto G", "G-1", BigDecimal.valueOf(100.00), 10);

        // Venda Original
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(120.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar com complemento parcial de 10 (preço seria 140, mas cliente paga só 10 a mais)
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantG.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(130.00)) // Preço ajustado: 120 + 10
                                .build()
                ))
                .newSalePayments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(10.00)) // Complemento parcial
                                .build()
                ))
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        // Receita: 120 + 10 = 130
        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(130.00));
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-100.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro com complemento parcial: 130 - 100 = 30")
                .isEqualByComparingTo(BigDecimal.valueOf(30.00));
    }

    @Test
    @DisplayName("Cenário 7: Troca com múltiplos itens")
    public void testScenario7_ExchangeMultipleItems() {
        // SETUP: Produto A (custo 150, venda 220) → Produto B (custo 70) + Produto C (custo 90)
        ProductVariant variantA = createProductWithVariant("Produto A", "A-7", BigDecimal.valueOf(150.00), 10);
        ProductVariant variantB = createProductWithVariant("Produto B", "B-2", BigDecimal.valueOf(70.00), 10);
        ProductVariant variantC = createProductWithVariant("Produto C", "C-2", BigDecimal.valueOf(90.00), 10);

        // Venda Original
        SaleCreateDTO originalSale = SaleCreateDTO.builder()
                .date(LocalDateTime.now())
                .items(List.of(
                        SaleItemCreateDTO.builder()
                                .variantId(variantA.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(220.00))
                                .build()
                ))
                .payments(List.of(
                        SalePaymentCreateDTO.builder()
                                .paymentMethod(PaymentMethod.CASH)
                                .amount(BigDecimal.valueOf(220.00))
                                .build()
                ))
                .build();

        var originalSaleResponse = createSaleUseCase.execute(originalSale, testPrincipal);

        // AÇÃO: Trocar por 2 produtos diferentes
        ProductExchangeRequestDTO exchangeRequest = ProductExchangeRequestDTO.builder()
                .originalSaleId(originalSaleResponse.getId())
                .itemsToReturn(List.of(
                        ProductExchangeRequestDTO.ItemToReturnDTO.builder()
                                .saleItemId(originalSaleResponse.getItems().get(0).getId())
                                .quantityToReturn(1)
                                .build()
                ))
                .newItems(List.of(
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantB.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(110.00))
                                .build(),
                        ProductExchangeRequestDTO.NewItemDTO.builder()
                                .variantId(variantC.getId())
                                .quantity(1)
                                .unitPrice(BigDecimal.valueOf(110.00))
                                .build()
                ))
                .newSalePayments(List.of())
                .reason(ExchangeReason.PREFERENCIA)
                .build();

        var exchangeResponse = exchangeProductUseCase.execute(exchangeRequest, testPrincipal);

        // VALIDAÇÃO
        List<Transaction> allTransactions = transactionRepository
                .findByAccountUserEmail(testUser.getEmail());

        BigDecimal totalRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.SALE)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsReversals = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.INCOME)
                .filter(t -> t.getDetail() == TransactionDetail.COGS_REVERSAL)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal cogsExpenses = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getDetail() == TransactionDetail.COST_OF_GOODS_SOLD)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCOGS = cogsExpenses.add(cogsReversals);

        // Receita: 220 (permanece)
        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.valueOf(220.00));
        // COGS: reverso +150, novos -(70+90) = -160
        assertThat(netCOGS).isEqualByComparingTo(BigDecimal.valueOf(-160.00));
        
        BigDecimal finalProfit = totalRevenue.add(netCOGS);
        assertThat(finalProfit)
                .as("Lucro com múltiplos itens: 220 - 160 = 60")
                .isEqualByComparingTo(BigDecimal.valueOf(60.00));
    }

    // Helper method para criar produtos com variantes para os testes
    private ProductVariant createProductWithVariant(String productName, String sku, BigDecimal cost, int stock) {
        var category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        com.jaoow.helmetstore.model.Category.builder()
                                .name("Test Category")
                                .inventory(testInventory)
                                .build()
                ));

        Product product = Product.builder()
                .model(productName)
                .color("Standard")
                .category(category)
                .inventory(testInventory)
                .imgUrl(null)
                .build();
        product = productRepository.save(product);

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .size("M")
                .build();
        variant = productVariantRepository.save(variant);

        InventoryItem item = InventoryItem.builder()
                .inventory(testInventory)
                .productVariant(variant)
                .quantity(stock)
                .averageCost(cost)
                .build();
        inventoryItemRepository.save(item);

        return variant;
    }
}
