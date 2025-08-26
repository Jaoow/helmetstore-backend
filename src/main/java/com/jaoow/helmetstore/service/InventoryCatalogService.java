package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.info.BaseProductStockVariantDto;
import com.jaoow.helmetstore.dto.info.PublicProductStockDto;
import com.jaoow.helmetstore.dto.info.PublicProductStockVariantDto;
import com.jaoow.helmetstore.dto.inventory.CatalogDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogCreateDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogStoreViewDTO;
import com.jaoow.helmetstore.dto.inventory.CatalogUpdateDTO;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.exception.CatalogNotFoundException;
import com.jaoow.helmetstore.exception.TokenAlreadyInUseException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryCatalog;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.InventoryCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static com.jaoow.helmetstore.service.ReportService.EXCLUDED_STATUSES;

@Service
@RequiredArgsConstructor
public class InventoryCatalogService {

    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;
    private final InventoryCatalogRepository catalogRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public CatalogStoreViewDTO getCatalogView(String token) {
        InventoryCatalog catalog = catalogRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(CatalogNotFoundException::new);

        CatalogStoreViewDTO catalogViewDTO = modelMapper.map(catalog, CatalogStoreViewDTO.class);
        catalogViewDTO.setProducts(getPublicProductStockDtos(catalog));

        if (!catalog.isShowWhatsappButton()) {
            // prevent from showing WhatsApp details if the button is not enabled
            catalogViewDTO.setWhatsappNumber(null);
            catalogViewDTO.setWhatsappMessage(null);
        }

        return catalogViewDTO;
    }

    private ArrayList<PublicProductStockDto> getPublicProductStockDtos(InventoryCatalog catalog) {
        Inventory inventory = catalog.getInventory();
        List<ProductVariantStockSummary> projections = inventoryItemRepository
                .findAllWithStockDetailsByInventory(EXCLUDED_STATUSES, inventory);

        Map<Long, PublicProductStockDto> productMap = new LinkedHashMap<>();

        for (ProductVariantStockSummary summary : projections) {
            PublicProductStockDto productStock = productMap.computeIfAbsent(
                    summary.getProductId(),
                    id -> mapToPublicProductStockDto(summary));

            productStock.getVariants().add(mapToPublicProductStockVariantDto(summary));
        }

        if (!catalog.isShowStockQuantity()) {
            hideStockQuantities(productMap);
        }

        return new ArrayList<>(productMap.values());
    }

    private void hideStockQuantities(Map<Long, PublicProductStockDto> productMap) {
        Iterator<Map.Entry<Long, PublicProductStockDto>> iterator = productMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, PublicProductStockDto> entry = iterator.next();
            PublicProductStockDto product = entry.getValue();

            product.getVariants().removeIf(variant -> variant.getCurrentStock() == 0);
            for (BaseProductStockVariantDto variant : product.getVariants()) {
                variant.setCurrentStock(null);
            }

            if (product.getVariants().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private PublicProductStockDto mapToPublicProductStockDto(ProductVariantStockSummary projection) {
        return modelMapper.map(projection, PublicProductStockDto.class);
    }

    private PublicProductStockVariantDto mapToPublicProductStockVariantDto(ProductVariantStockSummary projection) {
        return modelMapper.map(projection, PublicProductStockVariantDto.class);
    }

    @Transactional
    public CatalogDTO createCatalog(Principal principal, CatalogCreateDTO dto) {
        if (catalogRepository.existsByToken(dto.getToken())) {
            throw new TokenAlreadyInUseException();
        }

        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        if (catalogRepository.existsByInventory(inventory)) {
            throw new IllegalStateException("Este inventário já possui um catálogo.");
        }

        InventoryCatalog catalog = InventoryCatalog.builder()
                .inventory(inventory)
                .token(dto.getToken())
                .storeName(dto.getStoreName())
                .createdAt(LocalDateTime.now())
                .active(true)
                .showStockQuantity(true)
                .showPrice(true)
                .showWhatsappButton(true)
                .showSizeSelector(true)
                .build();

        catalog = catalogRepository.save(catalog);
        return modelMapper.map(catalog, CatalogDTO.class);
    }

    @Transactional
    public CatalogDTO updateCatalog(Principal principal, CatalogUpdateDTO dto) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        InventoryCatalog catalog = catalogRepository.findByInventory(inventory)
                .orElseThrow(CatalogNotFoundException::new);

        if (dto.getToken() != null && !dto.getToken().equals(catalog.getToken())) {
            if (catalogRepository.existsByToken(dto.getToken())) {
                throw new TokenAlreadyInUseException();
            }
            catalog.setToken(dto.getToken());
        }

        if (dto.getStoreName() != null) {
            catalog.setStoreName(dto.getStoreName());
        }

        if (dto.getActive() != null) {
            catalog.setActive(dto.getActive());
        }

        if (dto.getShowStockQuantity() != null) {
            catalog.setShowStockQuantity(dto.getShowStockQuantity());
        }

        if (dto.getShowPrice() != null) {
            catalog.setShowPrice(dto.getShowPrice());
        }

        if (dto.getShowWhatsappButton() != null) {
            catalog.setShowWhatsappButton(dto.getShowWhatsappButton());
        }

        if (dto.getShowSizeSelector() != null) {
            catalog.setShowSizeSelector(dto.getShowSizeSelector());
        }

        if (dto.getWhatsappNumber() != null) {
            catalog.setWhatsappNumber(dto.getWhatsappNumber());
        }

        if (dto.getWhatsappMessage() != null) {
            catalog.setWhatsappMessage(dto.getWhatsappMessage());
        }

        catalogRepository.save(catalog);
        return modelMapper.map(catalog, CatalogDTO.class);
    }

    public CatalogDTO getByUser(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return catalogRepository.findByInventory(inventory)
                .map(catalog -> modelMapper.map(catalog, CatalogDTO.class))
                .orElseThrow(CatalogNotFoundException::new);
    }
}
