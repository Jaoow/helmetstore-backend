package com.jaoow.helmetstore.exception;

public class OrderAlreadyExistsException extends RuntimeException {
    public OrderAlreadyExistsException() {
        super("Número de pedido já existe");
    }
}
