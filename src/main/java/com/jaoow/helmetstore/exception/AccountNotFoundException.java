package com.jaoow.helmetstore.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Conta não encontrado com ID: " + id);
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
}
