package com.jaoow.helmetstore.dto.inventory;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryShareLinkDTO {
    private String token;
    private boolean active;
    private boolean showStockQuantity;
    private LocalDateTime createdAt;
}
