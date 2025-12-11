package com.jaoow.helmetstore.exception;

public class InsufficientProfitException extends RuntimeException {

    public InsufficientProfitException() {
        super("Lucro insuficiente para realizar a operação.");
    }

    public InsufficientProfitException(String message) {
        super(message);
    }

    public InsufficientProfitException(String message, Throwable cause) {
        super(message, cause);
    }
}

