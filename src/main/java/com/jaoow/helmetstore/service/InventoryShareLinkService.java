package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.info.BaseProductStockVariantDto;
import com.jaoow.helmetstore.dto.info.PublicProductStockDto;
import com.jaoow.helmetstore.dto.info.PublicProductStockVariantDto;
import com.jaoow.helmetstore.dto.inventory.ShareLinkDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkCreateDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkStoreViewDTO;
import com.jaoow.helmetstore.dto.inventory.ShareLinkUpdateDTO;
import com.jaoow.helmetstore.dto.summary.ProductVariantStockSummary;
import com.jaoow.helmetstore.exception.ShareLinkNotFoundException;
import com.jaoow.helmetstore.exception.TokenAlreadyInUseException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryShareLink;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.InventoryShareLinkRepository;
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
public class InventoryShareLinkService {

    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;
    private final InventoryShareLinkRepository linkRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public ShareLinkStoreViewDTO getShareLinkStoreView(String token) {
        InventoryShareLink shareLink = linkRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(ShareLinkNotFoundException::new);

        ShareLinkStoreViewDTO storeViewDTO = modelMapper.map(shareLink, ShareLinkStoreViewDTO.class);
        storeViewDTO.setProducts(getPublicProductStockDtos(shareLink));

        if (!shareLink.isShowWhatsappButton()) {
            // prevent from showing WhatsApp details if the button is not enabled
            storeViewDTO.setWhatsappNumber(null);
            storeViewDTO.setWhatsappMessage(null);
        }

        return storeViewDTO;
    }

    private ArrayList<PublicProductStockDto> getPublicProductStockDtos(InventoryShareLink link) {
        Inventory inventory = link.getInventory();
        List<ProductVariantStockSummary> projections = inventoryItemRepository
                .findAllWithStockDetailsByInventory(EXCLUDED_STATUSES, inventory);

        Map<Long, PublicProductStockDto> productMap = new LinkedHashMap<>();

        for (ProductVariantStockSummary summary : projections) {
            PublicProductStockDto productStock = productMap.computeIfAbsent(
                    summary.getProductId(),
                    id -> mapToPublicProductStockDto(summary));

            productStock.getVariants().add(mapToPublicProductStockVariantDto(summary));
        }

        if (!link.isShowStockQuantity()) {
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
    public ShareLinkDTO createShareLink(Principal principal, ShareLinkCreateDTO dto) {
        if (linkRepository.existsByToken(dto.getToken())) {
            throw new TokenAlreadyInUseException();
        }

        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        if (linkRepository.existsByInventory(inventory)) {
            throw new IllegalStateException("Este inventário já possui um link de compartilhamento.");
        }

        InventoryShareLink link = InventoryShareLink.builder()
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

        link = linkRepository.save(link);
        return modelMapper.map(link, ShareLinkDTO.class);
    }

    @Transactional
    public ShareLinkDTO updateShareLink(Principal principal, ShareLinkUpdateDTO dto) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        InventoryShareLink link = linkRepository.findByInventory(inventory)
                .orElseThrow(ShareLinkNotFoundException::new);

        if (dto.getToken() != null && !dto.getToken().equals(link.getToken())) {
            if (linkRepository.existsByToken(dto.getToken())) {
                throw new TokenAlreadyInUseException();
            }
            link.setToken(dto.getToken());
        }

        if (dto.getStoreName() != null) {
            link.setStoreName(dto.getStoreName());
        }

        if (dto.getActive() != null) {
            link.setActive(dto.getActive());
        }

        if (dto.getShowStockQuantity() != null) {
            link.setShowStockQuantity(dto.getShowStockQuantity());
        }

        if (dto.getShowPrice() != null) {
            link.setShowPrice(dto.getShowPrice());
        }

        if (dto.getShowWhatsappButton() != null) {
            link.setShowWhatsappButton(dto.getShowWhatsappButton());
        }

        if (dto.getShowSizeSelector() != null) {
            link.setShowSizeSelector(dto.getShowSizeSelector());
        }

        if (dto.getWhatsappNumber() != null) {
            link.setWhatsappNumber(dto.getWhatsappNumber());
        }

        if (dto.getWhatsappMessage() != null) {
            link.setWhatsappMessage(dto.getWhatsappMessage());
        }

        linkRepository.save(link);
        return modelMapper.map(link, ShareLinkDTO.class);
    }

    public ShareLinkDTO getByUser(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return linkRepository.findByInventory(inventory)
                .map(link -> modelMapper.map(link, ShareLinkDTO.class))
                .orElseThrow(ShareLinkNotFoundException::new);
    }
}
