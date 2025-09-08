package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.order.*;
import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.exception.OrderAlreadyExistsException;
import com.jaoow.helmetstore.exception.OrderNotFoundException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.PurchaseOrderItem;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final ModelMapper modelMapper;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryHelper inventoryHelper;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionService transactionService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<PurchaseOrderDTO> findAll() {
        return purchaseOrderRepository.findAllByInventoryWithItemsAndVariants().stream()
                .map(order -> modelMapper.map(order, PurchaseOrderDTO.class))
                .collect(Collectors.toList());
    }

    @Cacheable(value = CacheNames.PURCHASE_ORDER_HISTORY, key = "#principal.name")
    @Transactional(readOnly = true)
    public PurchaseOrderHistoryResponse getHistory(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findAllByInventoryWithItemsAndVariants(inventory);

        List<OrderDetailDTO> orders = purchaseOrders.stream()
                .map(order -> modelMapper.map(order, OrderDetailDTO.class))
                .collect(Collectors.toList());

        List<SimpleProductVariantDTO> productVariants = purchaseOrders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(item -> modelMapper.map(item.getProductVariant(), SimpleProductVariantDTO.class))
                .distinct()
                .collect(Collectors.toList());

        List<SimpleProductDTO> products = purchaseOrders.stream()
                .flatMap(p -> p.getItems().stream())
                .map(i -> i.getProductVariant().getProduct())
                .distinct()
                .map(p -> modelMapper.map(p, SimpleProductDTO.class))
                .collect(Collectors.toList());

        return new PurchaseOrderHistoryResponse(orders, products, productVariants);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PURCHASE_ORDER_HISTORY, key = "#principal.name")
    })
    @Transactional
    public PurchaseOrderDTO save(PurchaseOrderCreateDTO orderCreateDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        if (purchaseOrderRepository.existsByInventoryAndOrderNumber(inventory, orderCreateDTO.getOrderNumber())) {
            throw new OrderAlreadyExistsException();
        }

        PurchaseOrder purchaseOrder = modelMapper.map(orderCreateDTO, PurchaseOrder.class);
        purchaseOrder.setInventory(inventory);
        purchaseOrder.setPaymentMethod(orderCreateDTO.getPaymentMethod());

        List<PurchaseOrderItem> items = createPurchaseOrderItems(orderCreateDTO, purchaseOrder, inventory);
        BigDecimal totalAmount = calculateTotalAmount(items);

        purchaseOrder.setItems(items);
        purchaseOrder.setTotalAmount(totalAmount);

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
        transactionService.recordTransactionFromPurchaseOrder(purchaseOrder, principal);

        return modelMapper.map(purchaseOrder, PurchaseOrderDTO.class);
    }

    private List<PurchaseOrderItem> createPurchaseOrderItems(PurchaseOrderCreateDTO orderCreateDTO, PurchaseOrder purchaseOrder, Inventory inventory) {
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (PurchaseOrderItemDTO itemDTO : orderCreateDTO.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemDTO.getProductVariantId())
                    .orElseThrow(() -> new ProductNotFoundException(itemDTO.getProductVariantId()));

            PurchaseOrderItem orderItem = PurchaseOrderItem.builder()
                    .productVariant(variant)
                    .quantity(itemDTO.getQuantity())
                    .purchasePrice(itemDTO.getPurchasePrice())
                    .purchaseOrder(purchaseOrder)
                    .build();

            ensureInventoryItemExists(inventory, variant, itemDTO.getPurchasePrice(), purchaseOrder.getDate());
            items.add(orderItem);
        }

        return items;
    }

    private void ensureInventoryItemExists(Inventory inventory, ProductVariant variant, BigDecimal price, LocalDate date) {
        Optional<InventoryItem> inventoryItem = inventoryItemRepository.findByInventoryAndProductVariant(inventory, variant);
        if (inventoryItem.isEmpty()) {
            inventoryItemRepository.save(
                    InventoryItem.builder()
                            .inventory(inventory)
                            .productVariant(variant)
                            .quantity(0)
                            .lastPurchasePrice(price)
                            .lastPurchaseDate(date)
                            .build()
            );
        }
    }

    private BigDecimal calculateTotalAmount(List<PurchaseOrderItem> items) {
        return items.stream()
                .map(item -> item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, key = "#principal.name"),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, key = "#principal.name"),
            @CacheEvict(value = CacheNames.PURCHASE_ORDER_HISTORY, key = "#principal.name")

    })
    @Transactional
    public PurchaseOrderDTO update(Long id, PurchaseOrderUpdateDTO dto, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        PurchaseOrder order = purchaseOrderRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (dto.getStatus() != null) {
            updateStatus(order, dto.getStatus());
        }

        Optional.ofNullable(dto.getOrderNumber()).ifPresent(order::setOrderNumber);
        Optional.ofNullable(dto.getDate()).ifPresent(order::setDate);

        order = purchaseOrderRepository.save(order);
        return modelMapper.map(order, PurchaseOrderDTO.class);
    }

    private void updateStatus(PurchaseOrder order, PurchaseOrderStatus newStatus) {
        if (order.getStatus() == PurchaseOrderStatus.DELIVERED && newStatus == PurchaseOrderStatus.CANCELED) {
            throw new IllegalStateException("Não é possível cancelar um pedido entregue");
        }

        if (newStatus == PurchaseOrderStatus.DELIVERED) {
            processDelivery(order);
        }

        if (newStatus == PurchaseOrderStatus.CANCELED) {
            transactionService.removeTransactionLinkedToPurchaseOrder(order);
        }

        order.setStatus(newStatus);
    }

    private void processDelivery(PurchaseOrder order) {
        Inventory inventory = order.getInventory();

        for (PurchaseOrderItem item : order.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByInventoryAndProductVariant(inventory, item.getProductVariant())
                    .orElseGet(() -> InventoryItem.builder()
                            .inventory(inventory)
                            .productVariant(item.getProductVariant())
                            .quantity(0)
                            .build());

            if (inventoryItem.getLastPurchaseDate() == null || inventoryItem.getLastPurchaseDate().isBefore(order.getDate())) {
                inventoryItem.setLastPurchaseDate(order.getDate());
                inventoryItem.setLastPurchasePrice(item.getPurchasePrice());
            }

            inventoryItem.setQuantity(inventoryItem.getQuantity() + item.getQuantity());
            inventoryItemRepository.save(inventoryItem);
        }
    }
}
