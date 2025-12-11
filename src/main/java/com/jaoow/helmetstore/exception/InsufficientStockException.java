package com.jaoow.helmetstore.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException() {
        super("Estoque insuficiente para realizar a operação.");
    }

    public InsufficientStockException(Long variantId, int available, int required) {
        super("Estoque insuficiente para variante ID: " + variantId +
                ". Disponível: " + available + ", Necessário: " + required);
    }

    public InsufficientStockException(String message) {
        super(message);
    }
}
