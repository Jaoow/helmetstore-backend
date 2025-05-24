package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.item.VariantStockUpdateDTO;
import com.jaoow.helmetstore.service.InventoryItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    @RequestMapping("/adjust-stock")
    public void updateInventoryItem(@RequestBody @Valid List<VariantStockUpdateDTO> variantStockUpdateDTOs, Principal principal) {
        inventoryItemService.updateItemStock(variantStockUpdateDTOs, principal);
    }
}
