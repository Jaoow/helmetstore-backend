package com.jaoow.helmetstore.helper;

import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class InventoryHelper {

    private final InventoryRepository inventoryRepository;

    public Inventory getInventoryFromPrincipal(Principal principal) {
        return inventoryRepository.findByUserEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for user: " + principal.getName()));
    }
}
