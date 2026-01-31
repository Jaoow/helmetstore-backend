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
import com.jaoow.helmetstore.repository.*;
import com.jaoow.helmetstore.usecase.sale.CreateSaleUseCase;
import com.jaoow.helmetstore.usecase.sale.ExchangeProductUseCase;
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

    private Principal testPrincipal;
    private Inventory testInventory;
    private Account cashAccount;
    private Product productX;
    private Product productZ;
    private ProductVariant variantX;
    private ProductVariant variantZ;

    @BeforeEach
    public void setup() {
        // Create test principal
        testPrincipal = new UsernamePasswordAuthenticationToken(
                "test@test.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // Create test inventory
        testInventory = inventoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    return inventoryRepository.save(inv);
                });

        // Create or get accounts
        cashAccount = accountRepository.findByUserEmailAndType("test@test.com", AccountType.CASH)
                .orElseGet(() -> {
                    Account account = Account.builder()
                            .type(AccountType.CASH)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return accountRepository.save(account);
                });

        // Create products
        var category = categoryRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> categoryRepository.save(
                        com.jaoow.helmetstore.model.Category.builder()
                                .name("Test Category")
                                .inventory(testInventory)
                                .build()
                ));

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
                .findByAccountUserEmail("test@test.com");
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
                .findByAccountUserEmail("test@test.com");

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
    }
}
