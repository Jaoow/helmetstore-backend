package com.jaoow.helmetstore.model;

/**
 * Categorias de compra para diferenciação fiscal em MEI
 */
public enum PurchaseCategory {
    /**
     * Compra feita diretamente no CNPJ do MEI
     */
    CNPJ_MEI,
    
    /**
     * Compra pessoal no CPF destinada ao estoque do MEI
     */
    CPF_PERSONAL
}