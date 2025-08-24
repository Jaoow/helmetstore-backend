package com.jaoow.helmetstore.exception;

public class InsufficientProfitException extends RuntimeException {

    public InsufficientProfitException(String message) {
        super(message);
    }

    public InsufficientProfitException(String message, Throwable cause) {
        super(message, cause);
    }
}

