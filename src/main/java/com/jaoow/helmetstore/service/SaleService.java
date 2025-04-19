package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleHistoryResponse;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ModelMapper modelMapper;
    private final InventoryHelper inventoryHelper;
    private final SaleRepository saleRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<SaleResponseDTO> findAll() {
        return saleRepository.findAll().stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Transactional
    public SaleResponseDTO save(SaleCreateDTO saleCreateDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        Long variantId = saleCreateDTO.getVariantId();
        ProductVariant productVariant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ProductNotFoundException(variantId));

        InventoryItem inventoryItem = inventoryItemRepository.findByInventoryAndProductVariant(inventory, productVariant)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for variant ID: " + variantId));

        if (inventoryItem.getQuantity() < saleCreateDTO.getQuantity()) {
            throw new InsufficientStockException("Insufficient stock for variant ID: " + variantId);
        }

        Sale sale = modelMapper.map(saleCreateDTO, Sale.class);
        sale.setId(null);
        sale.setInventory(inventory);
        sale.setProductVariant(productVariant);

        BigDecimal lastPurchasePrice = inventoryItem.getLastPurchasePrice();
        BigDecimal unitPrice = sale.getUnitPrice();
        BigDecimal quantity = BigDecimal.valueOf(sale.getQuantity());

        BigDecimal profitPerUnit = unitPrice.subtract(lastPurchasePrice);
        BigDecimal totalProfit = profitPerUnit.multiply(quantity);

        sale.setTotalProfit(totalProfit);
        saleRepository.save(sale);

        inventoryItem.setQuantity(inventoryItem.getQuantity() - saleCreateDTO.getQuantity());
        inventoryItemRepository.save(inventoryItem);

        return modelMapper.map(sale, SaleResponseDTO.class);
    }

    @Transactional(readOnly = true)
    public SaleHistoryResponse getHistory(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<Sale> sales = saleRepository.findAllByInventoryWithProductVariantsAndProducts(inventory);

        List<SaleResponseDTO> saleDTOs = sales.stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductVariantDTO> productVariants = sales.stream()
                .map(Sale::getProductVariant)
                .distinct()
                .map(variant -> modelMapper.map(variant, SimpleProductVariantDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductDTO> products = sales.stream()
                .map(sale -> sale.getProductVariant().getProduct())
                .distinct()
                .map(product -> modelMapper.map(product, SimpleProductDTO.class))
                .collect(Collectors.toList());

        return new SaleHistoryResponse(saleDTOs, products, productVariants);
    }
}
