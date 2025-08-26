package com.jaoow.helmetstore.exception;

public class CatalogNotFoundException extends ResourceNotFoundException {
    public CatalogNotFoundException() {
        super("Catálogo não encontrado.");
    }
}
