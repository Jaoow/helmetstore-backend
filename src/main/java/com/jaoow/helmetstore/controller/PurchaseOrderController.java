package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.order.CancelOrderItemDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderCreateDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderHistoryResponse;
import com.jaoow.helmetstore.dto.order.PurchaseOrderUpdateDTO;
import com.jaoow.helmetstore.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public List<PurchaseOrderDTO> getAll() {
        return purchaseOrderService.findAll();
    }

    @GetMapping("/history")
    public PurchaseOrderHistoryResponse getAllHistory(Principal principal) {
        return purchaseOrderService.getHistory(principal);
    }

    @GetMapping("/{id}")
    public PurchaseOrderDTO getById(@PathVariable Long id, Principal principal) {
        return purchaseOrderService.findByIdAndUser(id, principal);
    }

    @PostMapping
    public PurchaseOrderDTO create(@RequestBody @Valid PurchaseOrderCreateDTO purchaseOrderDTO, Principal principal) {
        return purchaseOrderService.save(purchaseOrderDTO, principal);
    }

    @PutMapping("/{id}")
    public PurchaseOrderDTO update(@PathVariable Long id,
            @RequestBody @Valid PurchaseOrderUpdateDTO purchaseOrderUpdateDTO, Principal principal) {
        return purchaseOrderService.update(id, purchaseOrderUpdateDTO, principal);
    }

    @GetMapping("/check-availability/{orderNumber}")
    public boolean checkOrderNumberAvailability(@PathVariable String orderNumber, Principal principal) {
        return purchaseOrderService.isOrderNumberAvailable(orderNumber, principal);
    }

    @PostMapping("/{orderId}/items/{itemId}/cancel")
    public PurchaseOrderDTO cancelOrderItem(@PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestBody @Valid CancelOrderItemDTO cancelDTO, 
            Principal principal) {
        return purchaseOrderService.cancelOrderItem(orderId, itemId, cancelDTO, principal);
    }
}
