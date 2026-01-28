package com.jaoow.helmetstore.model.sale;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Motivo do cancelamento da venda
 */
@Getter
@AllArgsConstructor
public enum CancellationReason {
    DESISTENCIA("Desistência do cliente"),
    DEFEITO("Produto com defeito"),
    ERRO_LANCAMENTO("Erro no lançamento"),
    FALTA_ESTOQUE("Falta de estoque"),
    PAGAMENTO_NAO_CONFIRMADO("Pagamento não confirmado"),
    DEVOLUCAO("Devolução"),
    OUTROS("Outros motivos");

    private final String description;
}
