package com.jaoow.helmetstore.exception;

public class InvalidReinvestmentException extends RuntimeException {

    public InvalidReinvestmentException(String message) {
        super(message);
    }

    public InvalidReinvestmentException(String message, Throwable cause) {
        super(message, cause);
    }
}

