package com.jaoow.helmetstore.dto.inventory;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
public class CatalogUpdateDTO {

    @Size(min = 1, max = 100, message = "O nome do catálogo deve ter no máximo 100 caracteres.")
    @Pattern(regexp = ".*(?:[a-zA-Z].*){4,}", message = "O nome do catálogo deve conter pelo menos 4 letras.")
    private String storeName;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{4,50}$", message = "O token deve conter entre 4 e 50 caracteres alfanuméricos, hífens ou underscores.")
    private String token;

    private Boolean active;

    private Boolean showStockQuantity;

    private Boolean showPrice;

    private Boolean showWhatsappButton;

    private Boolean showSizeSelector;

    @Pattern(regexp = "^\\+55\\d{11}$", message = "O número do WhatsApp deve estar no formato +55DDDDDDDDDD, onde D é um dígito.")
    private String whatsappNumber;

    @Size(max = 300, message = "A mensagem do WhatsApp deve ter no máximo 300 caracteres.")
    private String whatsappMessage;
}
