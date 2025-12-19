package com.jaoow.helmetstore.helper;

import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Component
@RequestScope
@RequiredArgsConstructor
public class InventoryHelper {

    private final InventoryRepository inventoryRepository;
    private final Map<String, Inventory> requestCache = new HashMap<>();

    public Inventory getInventoryFromPrincipal(Principal principal) {
        String email = principal.getName();
        
        // Cache no nível do request para evitar múltiplas queries na mesma requisição
        return requestCache.computeIfAbsent(email, key -> 
            inventoryRepository.findByUserEmail(key)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for user: " + key))
        );
    }
}
