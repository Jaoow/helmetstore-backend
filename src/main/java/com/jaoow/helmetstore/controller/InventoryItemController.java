package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.item.VariantPriceUpdateDTO;
import com.jaoow.helmetstore.dto.item.VariantStockUpdateDTO;
import com.jaoow.helmetstore.dto.product.ProductDataResponseDTO;
import com.jaoow.helmetstore.dto.product.ProductDataUpsertDTO;
import com.jaoow.helmetstore.service.InventoryItemService;
import com.jaoow.helmetstore.service.ProductDataService;
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
    private final ProductDataService productDataService;

    @PostMapping("/adjust-stock")
    public void updateInventoryItem(@RequestBody @Valid List<VariantStockUpdateDTO> variantStockUpdateDTOs,
            Principal principal) {
        inventoryItemService.updateItemStock(variantStockUpdateDTOs, principal);
    }

    @PostMapping("/adjust-price")
    public ProductDataResponseDTO updateProductPrice(@RequestBody @Valid ProductDataUpsertDTO upsertDTO,
            Principal principal) {
        return productDataService.upsert(upsertDTO, principal);
    }

    @PostMapping("/adjust-purchase-price")
    public void updateInventoryItemPrice(@RequestBody @Valid List<VariantPriceUpdateDTO> variantPriceUpdateDTOs,
            Principal principal) {
        inventoryItemService.updateItemPrice(variantPriceUpdateDTOs, principal);
    }

    @PostMapping("/adjust-product-purchase-price")
    public void updateProductPrice(@RequestParam Long productId, @RequestParam BigDecimal price, Principal principal) {
        inventoryItemService.updateProductPrice(productId, price, principal);
    }

    @DeleteMapping("/product/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductFromInventory(@PathVariable Long productId, Principal principal) {
        inventoryItemService.deleteProductFromInventory(productId, principal);
    }
}
