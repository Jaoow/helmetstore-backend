package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.inventory.CatalogDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogCreateDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogStoreViewDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogUpdateDTO;
import com.jaoow.helmetstore.service.InventoryCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class InventoryCatalogController {

    private final InventoryCatalogService catalogService;

    @GetMapping
    public CatalogDTO getOwnCatalog(Principal principal) {
        return catalogService.getByUser(principal);
    }

    @GetMapping("/public/{slug}/catalog")
    public CatalogStoreViewDTO getCatalogViewByToken(@PathVariable String slug) {
        return catalogService.getCatalogView(slug);
    }

    @PostMapping
    public CatalogDTO createCatalog(@RequestBody @Valid CatalogCreateDTO request, Principal principal) {
        return catalogService.createCatalog(principal, request);
    }

    @PatchMapping
    public CatalogDTO updateCatalog(Principal principal, @RequestBody @Valid CatalogUpdateDTO dto) {
        return catalogService.updateCatalog(principal, dto);
    }
}
