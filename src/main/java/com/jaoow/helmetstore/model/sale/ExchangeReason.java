package com.jaoow.helmetstore.model.sale;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ExchangeReason {
    DEFEITO("Produto com defeito"),
    TAMANHO("Tamanho inadequado"),
    PREFERENCIA("Mudança de preferência"),
    COR("Cor inadequada"),
    MODELO("Modelo inadequado"),
    OUTROS("Outros motivos");

    private final String description;
}
