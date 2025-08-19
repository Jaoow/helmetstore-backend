package com.jaoow.helmetstore.dto.inventory;

import lombok.Data;

@Data
public class ShareLinkDTO {
    private String storeName;
    private String token;
    private Boolean active;
    private Boolean showStockQuantity;
    private Boolean showPrice;
    private Boolean showWhatsappButton;
    private Boolean showSizeSelector;
    private String whatsappNumber;
    private String whatsappMessage;
}
