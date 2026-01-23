package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.item.VariantPriceUpdateDTO;
import com.jaoow.helmetstore.dto.item.VariantStockUpdateDTO;
import com.jaoow.helmetstore.service.InventoryItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryItemController {

    private final InventoryItemService inventoryItemService;

    @PostMapping("/adjust-stock")
    public void updateInventoryItem(@RequestBody @Valid List<VariantStockUpdateDTO> variantStockUpdateDTOs,
            Principal principal) {
        inventoryItemService.updateItemStock(variantStockUpdateDTOs, principal);
    }

    @PostMapping("/update-variant-average-cost")
    public void updateVariantAverageCost(@RequestBody @Valid List<VariantPriceUpdateDTO> variantPriceUpdateDTOs,
            Principal principal) {
        inventoryItemService.updateVariantAverageCost(variantPriceUpdateDTOs, principal);
    }

    // Backward compatibility - deprecated, use /update-variant-average-cost
    @PostMapping("/adjust-purchase-price")
    @Deprecated
    public void adjustPurchasePrice(@RequestBody @Valid List<VariantPriceUpdateDTO> variantPriceUpdateDTOs,
            Principal principal) {
        inventoryItemService.updateVariantAverageCost(variantPriceUpdateDTOs, principal);
    }

    @PostMapping("/update-product-average-cost")
    public void updateProductAverageCost(@RequestParam Long productId, @RequestParam BigDecimal averageCost, Principal principal) {
        inventoryItemService.updateProductAverageCost(productId, averageCost, principal);
    }

    @DeleteMapping("/product/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductFromInventory(@PathVariable Long productId, Principal principal) {
        inventoryItemService.deleteProductFromInventory(productId, principal);
    }
}
