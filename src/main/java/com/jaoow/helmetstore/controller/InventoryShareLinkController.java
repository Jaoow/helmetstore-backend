package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.inventory.ShareLinkDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkCreateDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkStoreViewDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkUpdateDTO;
import com.jaoow.helmetstore.service.InventoryShareLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/sharing")
@RequiredArgsConstructor
public class InventoryShareLinkController {

    private final InventoryShareLinkService shareLinkService;

    @GetMapping
    public ShareLinkDTO getOwnLink(Principal principal) {
        return shareLinkService.getByUser(principal);
    }

    @GetMapping("/public/{slug}/store")
    public ShareLinkStoreViewDTO getStoreViewByToken(@PathVariable String slug) {
        return shareLinkService.getShareLinkStoreView(slug);
    }

    @PostMapping
    public ShareLinkDTO createLink(@RequestBody @Valid ShareLinkCreateDTO request, Principal principal) {
        return shareLinkService.createShareLink(principal, request);
    }

    @PatchMapping
    public ShareLinkDTO updateLink(Principal principal, @RequestBody @Valid ShareLinkUpdateDTO dto) {
        return shareLinkService.updateShareLink(principal, dto);
    }
}
