package com.jaoow.helmetstore.model.balance;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TransactionDetail {

    // === INCOME (ENTRADAS) ===

    /** Venda (aumenta caixa e aumenta lucro do mês) */
    SALE(true, true),

    /** Aporte dos sócios (entra no caixa, mas NÃO afeta lucro) */
    OWNER_INVESTMENT(false, true),

    /** Receitas financeiras (juros, bônus) — lucro e caixa */
    EXTRA_INCOME(true, true),

    /** Reembolso de compras canceladas — NÃO afeta lucro (apenas devolve despesa), mas aumenta caixa */
    REFUND(false, true),


    // === EXPENSES (SAÍDAS) ===

    /** Compra de estoque — NÃO afeta lucro, mas reduz caixa */
    INVENTORY_PURCHASE(false, true),

    /** Custo do produto vendido (CPV) — diminui lucro e caixa */
    COST_OF_GOODS_SOLD(true, true),

    /** Reversão de COGS em trocas/devoluções — restaura lucro */
    COGS_REVERSAL(true, false),

    /** Despesas fixas — diminuem lucro e caixa */
    FIXED_EXPENSE(true, true),

    /** Despesas variáveis — diminuem lucro e caixa */
    VARIABLE_EXPENSE(true, true),

    /** Pró-labore — diminuem lucro e caixa */
    PRO_LABORE(true, true),

    /** Distribuição de lucros — NÃO afeta lucro, mas reduz caixa */
    PROFIT_DISTRIBUTION(false, true),

    /** Compra de bens — NÃO afeta lucro, mas reduz caixa */
    INVESTMENT(false, true),

    /** Impostos do período (DAS etc) — afetam lucro e caixa */
    TAX(true, true),

    /** Despesas pessoais pagas pelo caixa — afetam lucro e caixa */
    PERSONAL_EXPENSE(true, true),

    /** Outras despesas — afetam lucro e caixa */
    OTHER_EXPENSE(true, true),


    // === TRANSFERS ===

    /** Saída entre contas internas — só mexe no caixa, não lucro */
    INTERNAL_TRANSFER_OUT(false, true),

    /** Entrada entre contas internas — só mexe no caixa, não lucro */
    INTERNAL_TRANSFER_IN(false, true);


    private final boolean affectsProfit;
    private final boolean affectsCash;

}
