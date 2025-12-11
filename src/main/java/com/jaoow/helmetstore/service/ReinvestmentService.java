package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.ReinvestmentRequestDTO;
import com.jaoow.helmetstore.dto.balance.ReinvestmentResponseDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.exception.InsufficientProfitException;
import com.jaoow.helmetstore.exception.InvalidReinvestmentException;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.helper.InventoryHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReinvestmentService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final AccountService accountService;
    private final ProfitCalculationService profitCalculationService; // NEW: Unified profit calculations

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal MAX_REINVESTMENT_PERCENTAGE = new BigDecimal("100");
    private static final BigDecimal MIN_REINVESTMENT_PERCENTAGE = new BigDecimal("0.01");

    /**
     * Executa um reinvestimento do lucro líquido para o caixa
     *
     * @param request   Dados do reinvestimento
     * @param principal Usuário autenticado
     * @return Resposta com detalhes do reinvestimento
     * @throws InvalidReinvestmentException Se os dados do reinvestimento forem
     *                                      inválidos
     * @throws InsufficientProfitException  Se não houver lucro suficiente
     * @throws AccountNotFoundException     Se as contas não forem encontradas
     */
    @Transactional
    public ReinvestmentResponseDTO executeReinvestment(ReinvestmentRequestDTO request, Principal principal) {
        log.info("Iniciando reinvestimento para usuário: {}", principal.getName());
        log.info("Request: tipo={}, valor={}, mês={}", request.getReinvestmentType(), request.getValue(),
            request.getReferenceMonth());

        // Validar e parsear o mês de referência
        YearMonth referenceMonth = validateAndParseReferenceMonth(request.getReferenceMonth());

        // Calcular o lucro líquido disponível para o mês
        BigDecimal availableProfit = calculateAvailableProfitForMonth(principal, referenceMonth);
        log.info("Lucro líquido disponível para o mês {}: R$ {}", referenceMonth, availableProfit);

        // Validar o valor do reinvestimento
        BigDecimal reinvestmentAmount = validateReinvestmentAmount(request, availableProfit);
        log.info("Valor de reinvestimento calculado: R$ {}", reinvestmentAmount);

        // Encontrar as contas (BANK e CASH)
        Account bankAccount = findAccount(principal, AccountType.BANK);
        Account cashAccount = findAccount(principal, AccountType.CASH);

        // Executar o reinvestimento com estratégia inteligente
        List<Transaction> transactions = executeIntelligentReinvestment(
            request,
            reinvestmentAmount,
            bankAccount,
            cashAccount,
            availableProfit);

        // Salvar todas as transações
        transactionRepository.saveAll(transactions);

        // Invalidar TODOS os caches financeiros e de contas para refletir as mudanças
        // IMPORTANTE: O reinvestimento cria múltiplas transações que afetam:
        // - Saldos das contas BANK e CASH
        // - Cálculos de lucro mensal e acumulado
        // - Fluxo de caixa mensal e geral
        // - Resumos financeiros
        // - Todos os dados que o frontend consome via React Query
        log.info("Invalidando TODOS os caches após reinvestimento (mudança financeira major)");
        cacheInvalidationService.invalidateAllCaches();

        log.info("Reinvestimento executado com sucesso: R$ {} para usuário {} ({} transações criadas)",
            reinvestmentAmount, principal.getName(), transactions.size());

        return buildReinvestmentResponse(transactions, reinvestmentAmount, availableProfit, request);
    }

    /**
     * Executa reinvestimento inteligente priorizando conta BANK e usando CASH como
     * fallback. O reinvestimento sempre permanece na conta BANK (PIX).
     */
    private List<Transaction> executeIntelligentReinvestment(
        ReinvestmentRequestDTO request,
        BigDecimal reinvestmentAmount,
        Account bankAccount,
        Account cashAccount,
        BigDecimal availableProfit) {

        // Obter saldos atuais das contas usando o novo método
        BigDecimal bankBalance = accountService.calculateAccountBalanceByType(
            bankAccount.getUser().getEmail(), AccountType.BANK);
        BigDecimal cashBalance = accountService.calculateAccountBalanceByType(
            cashAccount.getUser().getEmail(), AccountType.CASH);

        log.info("Saldos das contas - BANK: R$ {}, CASH: R$ {}, Valor a reinvestir: R$ {}",
            bankBalance, cashBalance, reinvestmentAmount);

        List<Transaction> transactions = new java.util.ArrayList<>();

        if (bankBalance.compareTo(reinvestmentAmount) >= 0) {
            // Caso 1: Conta BANK tem saldo suficiente - apenas 2 transações
            log.info("Conta BANK tem saldo suficiente (R$ {}), criando 2 transações", bankBalance);

            // 1. Retirada da conta BANK (EXPENSE)
            Transaction bankWithdrawal = createReinvestmentTransaction(
                request, reinvestmentAmount, bankAccount, availableProfit,
                TransactionType.EXPENSE, "Retirada para reinvestimento");
            transactions.add(bankWithdrawal);

            // 2. Entrada na conta BANK (INCOME) - Reinvestimento permanece na mesma conta
            Transaction bankIncome = createReinvestmentTransaction(
                request, reinvestmentAmount, bankAccount, availableProfit,
                TransactionType.INCOME, "Reinvestimento recebido");
            transactions.add(bankIncome);

        } else {
            // Caso 2: Conta BANK insuficiente - usar CASH como fallback - 4 transações
            // O reinvestimento sempre permanece na conta BANK (PIX)
            BigDecimal bankAvailable = bankBalance;
            BigDecimal cashNeeded = reinvestmentAmount.subtract(bankAvailable);

            log.info(
                "Conta BANK insuficiente (R$ {}), usando CASH como fallback. BANK: R$ {}, CASH: R$ {}, Necessário: R$ {}",
                bankBalance, bankAvailable, cashNeeded, reinvestmentAmount);

            if (cashBalance.compareTo(cashNeeded) < 0) {
                log.error("Saldo insuficiente nas contas. BANK: R$ {}, CASH: R$ {}, Necessário: R$ {}",
                    bankBalance, cashBalance, reinvestmentAmount);
                throw new InsufficientProfitException(
                    "Saldo insuficiente nas contas para reinvestimento. " +
                        "BANK: R$ " + bankBalance + ", CASH: R$ " + cashBalance
                        +
                        ", Necessário: R$ " + reinvestmentAmount);
            }

            // 1. Retirada da conta BANK (EXPENSE) - se houver saldo disponível
            if (bankAvailable.compareTo(BigDecimal.ZERO) > 0) {
                Transaction bankWithdrawal = createReinvestmentTransaction(
                    request, bankAvailable, bankAccount, availableProfit,
                    TransactionType.EXPENSE, "Retirada parcial para reinvestimento");
                transactions.add(bankWithdrawal);
            }

            // 2. Retirada da conta CASH (EXPENSE) - complemento necessário
            Transaction cashWithdrawal = createReinvestmentTransaction(
                request, cashNeeded, cashAccount, availableProfit,
                TransactionType.EXPENSE, "Retirada para reinvestimento");
            transactions.add(cashWithdrawal);

            // 3. Entrada na conta BANK (INCOME) - valor total (reinvestimento permanece na
            // conta principal)
            Transaction bankIncome = createReinvestmentTransaction(
                request, reinvestmentAmount, bankAccount, availableProfit,
                TransactionType.INCOME, "Reinvestimento recebido");
            transactions.add(bankIncome);

            // 4. Transferência interna da conta CASH para BANK (compensação do valor usado
            // do CASH)
            if (cashNeeded.compareTo(BigDecimal.ZERO) > 0) {
                Transaction internalTransfer = createReinvestmentTransaction(
                    request, cashNeeded, bankAccount, availableProfit,
                    TransactionType.INCOME, "Transferência interna para reinvestimento");
                transactions.add(internalTransfer);
            }
        }

        return transactions;
    }

    /**
     * Valida e parseia o mês de referência
     */
    private YearMonth validateAndParseReferenceMonth(String referenceMonth) {
        try {
            return YearMonth.parse(referenceMonth, MONTH_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidReinvestmentException(
                "Formato de mês inválido. Use o formato 'yyyy-MM' (ex: '2024-01')");
        }
    }

    /**
     * Calcula o lucro líquido disponível para o mês especificado
     */
    private BigDecimal calculateAvailableProfitForMonth(Principal principal, YearMonth month) {
        // Buscar transações APENAS do mês especificado
        LocalDateTime startOfMonth = month.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = month.plusMonths(1).atDay(1).atStartOfDay();

        log.info("Calculando lucro para mês {} - De: {} até: {}", month, startOfMonth, startOfNextMonth);

        // ============================================================================
        // USE UNIFIED PROFIT CALCULATION SERVICE
        // ============================================================================
        String userEmail = principal.getName();
        BigDecimal netProfit = profitCalculationService.calculateNetProfitByDateRange(userEmail, startOfMonth,
            startOfNextMonth);

        // Log for debugging (optional - can calculate gross profit for comparison)
        var inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        BigDecimal monthlyGrossProfit = profitCalculationService.calculateGrossProfitByDateRange(inventory,
            startOfMonth, startOfNextMonth);
        BigDecimal monthlyExpenseTransactions = profitCalculationService
            .calculateOperationalExpensesByDateRange(userEmail, startOfMonth, startOfNextMonth);

        log.info("Cálculo do lucro para mês {} - Lucro bruto: R$ {}, Despesas operacionais: R$ {}, Lucro líquido: R$ {}",
            month, monthlyGrossProfit, monthlyExpenseTransactions, netProfit);

        if (netProfit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InsufficientProfitException(
                "Não há lucro líquido disponível para o mês " + month.format(MONTH_FORMATTER));
        }

        return netProfit;
    }

    /**
     * Valida o valor do reinvestimento baseado no tipo e lucro disponível
     */
    private BigDecimal validateReinvestmentAmount(ReinvestmentRequestDTO request, BigDecimal availableProfit) {
        BigDecimal amount = request.getValue();
        log.info("Validando reinvestimento - Tipo: {}, Valor original: {}, Lucro disponível: R$ {}",
            request.getReinvestmentType(), amount, availableProfit);

        if (request.getReinvestmentType() == ReinvestmentRequestDTO.ReinvestmentType.PERCENTAGE) {
            // Validar porcentagem
            if (amount.compareTo(MIN_REINVESTMENT_PERCENTAGE) < 0 ||
                amount.compareTo(MAX_REINVESTMENT_PERCENTAGE) > 0) {
                throw new InvalidReinvestmentException(
                    "Porcentagem deve estar entre 0.01% e 100%");
            }

            // Calcular valor baseado na porcentagem
            amount = availableProfit.multiply(amount)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            log.info("Valor calculado para {}%: R$ {}", request.getValue(), amount);
        }

        // Validar se o valor não excede o lucro disponível
        if (amount.compareTo(availableProfit) > 0) {
            throw new InvalidReinvestmentException(
                "Valor de reinvestimento (R$ " + amount +
                    ") não pode exceder o lucro líquido disponível (R$ "
                    + availableProfit + ")");
        }

        // Validar valor mínimo
        if (amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new InvalidReinvestmentException("Valor mínimo para reinvestimento é R$ 0,01");
        }

        log.info("Valor final validado: R$ {}", amount);
        return amount;
    }

    /**
     * Encontra a conta do usuário pelo tipo (reutilizando método existente)
     */
    private Account findAccount(Principal principal, AccountType accountType) {
        return accountRepository.findByUserEmailAndType(principal.getName(), accountType)
            .orElseThrow(() -> new AccountNotFoundException(
                "Conta " + accountType + " não encontrada para o usuário "
                    + principal.getName()));
    }

    /**
     * Cria uma transação de reinvestimento
     */
    private Transaction createReinvestmentTransaction(
        ReinvestmentRequestDTO request,
        BigDecimal amount,
        Account account,
        BigDecimal availableProfit,
        TransactionType type,
        String descriptionSuffix) {

        String description = buildReinvestmentDescription(request, amount, availableProfit, descriptionSuffix);

        // ============================================================================
        // FIX: Ensure expenses are stored as negative values
        // ============================================================================
        BigDecimal transactionAmount = (type == TransactionType.EXPENSE) ? amount.negate() : amount;

        // Determine wallet destination based on account type
        AccountType walletDest = account.getType();

        return Transaction.builder()
            .date(LocalDateTime.now())
            .type(type)
            .detail(TransactionDetail.OWNER_INVESTMENT)
            .description(description)
            .amount(transactionAmount)
            .paymentMethod(account.getType() == AccountType.CASH ? PaymentMethod.CASH
                : PaymentMethod.PIX)
            .reference("REINVESTMENT")
            .account(account)
            // DOUBLE-ENTRY LEDGER FLAGS FOR REINVESTMENT
            // EXPENSE (withdrawal): reduces profit | INCOME (deposit): only increases cash
            .affectsProfit(type == TransactionType.EXPENSE)  // Only withdrawal reduces profit
            .affectsCash(true)                               // Both withdrawal and deposit affect cash
            .walletDestination(walletDest)                   // Target wallet (CASH or BANK)
            .build();
    }

    /**
     * Constrói a descrição da transação de reinvestimento
     */
    private String buildReinvestmentDescription(
        ReinvestmentRequestDTO request,
        BigDecimal amount,
        BigDecimal availableProfit,
        String descriptionSuffix) {

        BigDecimal divide = amount.multiply(new BigDecimal("100"))
            .divide(availableProfit, 2, RoundingMode.HALF_UP);
        if (request.getReinvestmentType() == ReinvestmentRequestDTO.ReinvestmentType.PERCENTAGE) {
            return String.format("Reinvestimento - %.2f%% do lucro líquido (R$ %.2f) - %s",
                divide, amount, descriptionSuffix);
        } else {
            return String.format("Reinvestimento - R$ %.2f (%.2f%% do lucro líquido) - %s",
                amount, divide, descriptionSuffix);
        }
    }

    /**
     * Constrói a resposta do reinvestimento
     */
    private ReinvestmentResponseDTO buildReinvestmentResponse(
        List<Transaction> transactions,
        BigDecimal reinvestedAmount,
        BigDecimal availableProfit,
        ReinvestmentRequestDTO request) {

        BigDecimal percentageOfProfit = reinvestedAmount.multiply(new BigDecimal("100"))
            .divide(availableProfit, 2, RoundingMode.HALF_UP);

        BigDecimal remainingProfit = availableProfit.subtract(reinvestedAmount);

        return ReinvestmentResponseDTO.builder()
            .transactionId(transactions.get(0).getId()) // ID da primeira transação
            .reinvestedAmount(reinvestedAmount)
            .percentageOfProfit(percentageOfProfit)
            .description("Reinvestimento executado com " + transactions.size() + " transações")
            .reinvestmentDate(LocalDateTime.now())
            .remainingProfit(remainingProfit)
            .message("Reinvestimento realizado com sucesso! " + transactions.size()
                + " transações criadas.")
            .build();
    }
}
