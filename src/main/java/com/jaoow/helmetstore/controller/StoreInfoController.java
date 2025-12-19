package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.store.StoreInfoDTO;
import com.jaoow.helmetstore.service.StoreInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/store-info")
@RequiredArgsConstructor
public class StoreInfoController {

    private final StoreInfoService storeInfoService;

    @GetMapping
    public ResponseEntity<StoreInfoDTO> getStoreInfo(Principal principal) {
        StoreInfoDTO storeInfo = storeInfoService.getStoreInfo(principal);
        if (storeInfo == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(storeInfo);
    }

    @PutMapping
    public ResponseEntity<StoreInfoDTO> updateStoreInfo(@Valid @RequestBody StoreInfoDTO storeInfoDTO, Principal principal) {
        return ResponseEntity.ok(storeInfoService.createOrUpdateStoreInfo(storeInfoDTO, principal));
    }
}
