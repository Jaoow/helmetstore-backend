package com.jaoow.helmetstore.exception;

public class ProductNotFoundException extends ResourceNotFoundException {
    public ProductNotFoundException(Long id) {
        super("Produto não encontrado com ID: " + id);
    }
}
