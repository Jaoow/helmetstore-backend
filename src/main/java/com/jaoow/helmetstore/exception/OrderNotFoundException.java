package com.jaoow.helmetstore.exception;

public class OrderNotFoundException extends ResourceNotFoundException {
    public OrderNotFoundException(Long id) {
        super("Pedido não encontrado com ID: " + id);
    }
}
