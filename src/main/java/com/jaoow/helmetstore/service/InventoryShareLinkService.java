package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.info.PublicProductStockDto;
import com.jaoow.helmetstore.dto.inventory.InventoryShareLinkDTO;
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

    public List<PublicProductStockDto> getProductStockByToken(String token) {
        InventoryShareLink link = linkRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(ShareLinkNotFoundException::new);

        Inventory inventory = link.getInventory();
        List<ProductVariantStockSummary> projections = inventoryItemRepository
                .findAllWithStockDetailsByInventory(EXCLUDED_STATUSES, inventory);

        Map<Long, PublicProductStockDto> productMap = new LinkedHashMap<>();

        for (ProductVariantStockSummary summary : projections) {
            PublicProductStockDto productStock = productMap.computeIfAbsent(
                    summary.getProductId(),
                    id -> mapToPublicProductStockDto(summary)
            );

            productStock.getVariants().add(mapToPublicProductStockVariantDto(summary));
        }

        if (!link.isShowStockQuantity()) {
            // If hide the stock quantity is enabled, remove the stock information and show only the variants
            // with stock > 0
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
            for (PublicProductStockDto.PublicProductStockVariantDto variant : product.getVariants()) {
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

    private PublicProductStockDto.PublicProductStockVariantDto mapToPublicProductStockVariantDto(ProductVariantStockSummary projection) {
        return modelMapper.map(projection, PublicProductStockDto.PublicProductStockVariantDto.class);
    }

    @Transactional
    public InventoryShareLinkDTO createShareLink(Principal principal, String customToken) {
        if (linkRepository.existsByToken(customToken)) {
            throw new TokenAlreadyInUseException();
        }

        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        if (linkRepository.existsByInventory(inventory)) {
            throw new IllegalStateException("Este inventário já possui um link de compartilhamento.");
        }

        InventoryShareLink link = InventoryShareLink.builder()
                .inventory(inventory)
                .token(customToken)
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();

        link = linkRepository.save(link);
        return modelMapper.map(link, InventoryShareLinkDTO.class);
    }

    @Transactional
    public void toggleShowStock(Principal principal, boolean showStock) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        InventoryShareLink link = linkRepository.findByInventory(inventory)
                .orElseThrow(ShareLinkNotFoundException::new);

        link.setShowStockQuantity(showStock);
        linkRepository.save(link);
    }

    @Transactional
    public void toggleActivation(Principal principal, boolean activate) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        InventoryShareLink link = linkRepository.findByInventory(inventory)
                .orElseThrow(ShareLinkNotFoundException::new);

        link.setActive(activate);
        linkRepository.save(link);
    }

    @Transactional
    public void renameToken(Principal principal, String newToken) {
        if (linkRepository.existsByToken(newToken)) {
            throw new TokenAlreadyInUseException();
        }

        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        InventoryShareLink link = linkRepository.findByInventory(inventory)
                .orElseThrow(ShareLinkNotFoundException::new);

        link.setToken(newToken);
        linkRepository.save(link);
    }

    public InventoryShareLinkDTO getByUser(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        return linkRepository.findByInventory(inventory)
                .map(link -> modelMapper.map(link, InventoryShareLinkDTO.class))
                .orElseThrow(ShareLinkNotFoundException::new);
    }
}
