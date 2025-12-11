package com.jaoow.helmetstore.exception;

import com.jaoow.helmetstore.model.balance.PaymentMethod;

public class AccountNotFoundException extends ResourceNotFoundException {
    public AccountNotFoundException(Long id) {
        super("Conta não encontrada com ID: " + id);
    }

    public AccountNotFoundException(PaymentMethod paymentMethod) {
        super("Conta não encontrada para o método de pagamento: " + paymentMethod);
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
}
