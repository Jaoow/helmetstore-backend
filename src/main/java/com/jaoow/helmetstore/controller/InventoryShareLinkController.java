package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.info.PublicProductStockDto;
import com.jaoow.helmetstore.dto.inventory.InventoryShareLinkDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkCreateDTO;
import com.jaoow.helmetstore.service.InventoryShareLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sharing")
@RequiredArgsConstructor
public class InventoryShareLinkController {

    private final InventoryShareLinkService shareLinkService;

    @GetMapping
    public InventoryShareLinkDTO getOwnLink(Principal principal) {
        return shareLinkService.getByUser(principal);
    }

    @GetMapping("/public/{token}")
    public List<PublicProductStockDto> getStockByToken(@PathVariable String token) {
        return shareLinkService.getProductStockByToken(token);
    }

    @PostMapping
    public InventoryShareLinkDTO createLink(@RequestBody @Valid ShareLinkCreateDTO request, Principal principal) {
        return shareLinkService.createShareLink(principal, request.getToken());
    }

    @PutMapping("/rename")
    public Map<String, Object>  renameToken(@RequestBody @Valid ShareLinkCreateDTO request, Principal principal) {
        shareLinkService.renameToken(principal, request.getToken());
        return Map.of("success", true, "newToken", request.getToken());
    }

    @PutMapping("/activate")
    public Map<String, Object> toggleActivation(@RequestParam boolean active, Principal principal) {
        shareLinkService.toggleActivation(principal, active);
        return Map.of("success", true, "active", active);
    }

    @PutMapping("/show-stock")
    public Map<String, Object> toggleShowStock(@RequestParam boolean showStockQuantity, Principal principal) {
        shareLinkService.toggleShowStock(principal, showStockQuantity);
        return Map.of("success", true, "showStockQuantity", showStockQuantity);
    }
}
