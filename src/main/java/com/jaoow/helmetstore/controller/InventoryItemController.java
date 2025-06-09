package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.item.VariantStockUpdateDTO;
import com.jaoow.helmetstore.dto.product.ProductDataResponseDTO;
import com.jaoow.helmetstore.dto.product.ProductDataUpsertDTO;
import com.jaoow.helmetstore.service.InventoryItemService;
import com.jaoow.helmetstore.service.ProductDataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final ProductDataService productDataService;

    @PostMapping("/adjust-stock")
    public void updateInventoryItem(@RequestBody @Valid List<VariantStockUpdateDTO> variantStockUpdateDTOs, Principal principal) {
        inventoryItemService.updateItemStock(variantStockUpdateDTOs, principal);
    }

    @PostMapping("/adjust-price")
    public ProductDataResponseDTO updateProductPrice(@RequestBody @Valid ProductDataUpsertDTO upsertDTO, Principal principal) {
        return productDataService.upsert(upsertDTO, principal);
    }
}
