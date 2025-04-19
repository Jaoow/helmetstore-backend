package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleHistoryResponse;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SaleController {
    private final SaleService saleService;

    @GetMapping
    public List<SaleResponseDTO> getAll() {
        return saleService.findAll();
    }

    @GetMapping("/history")
    public SaleHistoryResponse getHistory(Principal principal) {
        return saleService.getHistory(principal);
    }

    @PostMapping
    public SaleResponseDTO create(@RequestBody @Valid SaleCreateDTO saleCreateDTO, Principal principal) {
        return saleService.save(saleCreateDTO, principal);
    }
}
