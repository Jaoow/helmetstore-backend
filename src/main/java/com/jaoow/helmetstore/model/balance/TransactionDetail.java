package com.jaoow.helmetstore.model.balance;

public enum TransactionDetail {
    // === INCOME (ENTRADAS) ===

    /** Receita de venda de produtos ou serviços */
    SALE,

    /** Aporte dos sócios (não entra como lucro) */
    OWNER_INVESTMENT,

    /** Recebimento de juros ou outros rendimentos */
    EXTRA_INCOME,

    // === EXPENSES (SAÍDAS) ===

    /** Custo direto do produto vendido (CPV / CMV) */
    COST_OF_GOODS_SOLD,

    /** Despesas fixas da operação (aluguel, contador, internet, etc.) */
    FIXED_EXPENSE,

    /** Despesas variáveis (marketing, comissões, tarifas, fretes por venda) */
    VARIABLE_EXPENSE,

    /** Pro-labore — retirada de sócios como salário */
    PRO_LABORE,

    /** Distribuição de lucro — retirada além do pro-labore */
    PROFIT_DISTRIBUTION,

    /** Compra de bens ou melhorias (máquinas, móveis, etc.) */
    INVESTMENT,

    /** Pagamento de impostos diversos (DAS, IRPJ, etc.) */
    TAX,

    /** Gastos pessoais pagos pelo caixa da empresa (não recomendados) */
    PERSONAL_EXPENSE,

    /** Outras despesas que não se encaixam nas anteriores */
    OTHER_EXPENSE,

    // === TRANSFERS (TRANSFERÊNCIAS) ===

    /** Transferência entre contas (genérica) */
    INTER_ACCOUNT_TRANSFER
}