package com.jaoow.helmetstore.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Token inválido.");
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}
