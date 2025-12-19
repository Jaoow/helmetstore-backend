package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.store.StoreInfoDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.StoreInfo;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.StoreInfoRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
@RequiredArgsConstructor
public class StoreInfoService {

    private final StoreInfoRepository storeInfoRepository;
    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;

    @Transactional(readOnly = true)
    public StoreInfoDTO getStoreInfo(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        StoreInfo storeInfo = storeInfoRepository.findByInventory(inventory)
                .orElse(null);
        
        if (storeInfo == null) {
            return null;
        }
        
        return modelMapper.map(storeInfo, StoreInfoDTO.class);
    }

    @Transactional
    public StoreInfoDTO createOrUpdateStoreInfo(StoreInfoDTO storeInfoDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        StoreInfo storeInfo = storeInfoRepository.findByInventory(inventory)
                .orElseGet(() -> StoreInfo.builder()
                        .inventory(inventory)
                        .build());

        storeInfo.setName(storeInfoDTO.getName());
        storeInfo.setAddress(storeInfoDTO.getAddress());
        storeInfo.setPhone(storeInfoDTO.getPhone());
        storeInfo.setCnpj(storeInfoDTO.getCnpj());
        storeInfo.setEmail(storeInfoDTO.getEmail());
        storeInfo.setWebsite(storeInfoDTO.getWebsite());
        
        StoreInfo savedInfo = storeInfoRepository.save(storeInfo);
        return modelMapper.map(savedInfo, StoreInfoDTO.class);
    }

    @Transactional(readOnly = true)
    public StoreInfo getStoreInfoEntity(Inventory inventory) {
        return storeInfoRepository.findByInventory(inventory).orElse(null);
    }
}
