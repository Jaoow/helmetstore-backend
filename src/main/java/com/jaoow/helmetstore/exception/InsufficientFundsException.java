package com.jaoow.helmetstore.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException() {
        super("Saldo insuficiente para realizar a operação.");
    }

    public InsufficientFundsException(String message) {
        super(message);
    }
}
