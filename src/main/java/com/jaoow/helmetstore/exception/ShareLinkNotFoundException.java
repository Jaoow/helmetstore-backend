package com.jaoow.helmetstore.exception;

public class ShareLinkNotFoundException extends ResourceNotFoundException {
    public ShareLinkNotFoundException() {
        super("Link de compartilhamento não encontrado.");
    }
}
