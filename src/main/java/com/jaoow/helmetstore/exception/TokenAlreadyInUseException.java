package com.jaoow.helmetstore.exception;

public class TokenAlreadyInUseException extends RuntimeException {
    public TokenAlreadyInUseException() {
        super("Token já está em uso.");
    }
}
