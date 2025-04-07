package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.order.PurchaseOrderCreateDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderDTO;
import com.jaoow.helmetstore.dto.order.PurchaseOrderHistoryResponse;
import com.jaoow.helmetstore.dto.order.PurchaseOrderUpdateDTO;
import com.jaoow.helmetstore.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public PurchaseOrderHistoryResponse getAllHistory() {
        return purchaseOrderService.getHistory();
    }

    @PostMapping
    public PurchaseOrderDTO create(@RequestBody @Valid PurchaseOrderCreateDTO purchaseOrderDTO) {
        return purchaseOrderService.save(purchaseOrderDTO);
    }

    @PutMapping("/{id}")
    public PurchaseOrderDTO update(@PathVariable Long id, @RequestBody @Valid PurchaseOrderUpdateDTO purchaseOrderUpdateDTO) {
        return purchaseOrderService.update(id, purchaseOrderUpdateDTO);
    }
}
