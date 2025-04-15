package com.jaoow.helmetstore.service;

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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
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

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<PurchaseOrderDTO> findAll() {
        return purchaseOrderRepository.findAllByInventoryWithItemsAndVariants().stream()
                .map(order -> modelMapper.map(order, PurchaseOrderDTO.class))
                .collect(Collectors.toList());
    }

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

    @Transactional
    public PurchaseOrderDTO save(PurchaseOrderCreateDTO orderCreateDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        if (purchaseOrderRepository.existsByInventoryAndOrderNumber(inventory, orderCreateDTO.getOrderNumber())) {
            throw new OrderAlreadyExistsException();
        }

        PurchaseOrder purchaseOrder = modelMapper.map(orderCreateDTO, PurchaseOrder.class);
        PurchaseOrder finalPurchaseOrder = purchaseOrder;
        List<PurchaseOrderItem> items = orderCreateDTO.getItems().stream()
                .map(itemDTO -> {
                    ProductVariant variant = productVariantRepository.findById(itemDTO.getProductVariantId())
                            .orElseThrow(() -> new ProductNotFoundException(itemDTO.getProductVariantId()));

                    return PurchaseOrderItem.builder()
                            .productVariant(variant)
                            .quantity(itemDTO.getQuantity())
                            .purchasePrice(itemDTO.getPurchasePrice())
                            .purchaseOrder(finalPurchaseOrder)
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal totalAmount = items.stream()
                .map(item -> item.getPurchasePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        purchaseOrder.setInventory(inventory);
        purchaseOrder.setTotalAmount(totalAmount);
        purchaseOrder.setItems(items);

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
        return modelMapper.map(purchaseOrder, PurchaseOrderDTO.class);
    }

    @Transactional
    public PurchaseOrderDTO update(Long id, PurchaseOrderUpdateDTO orderUpdateDTO, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdAndInventory(id, inventory)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (orderUpdateDTO.getStatus() != null) {
            handleStatusUpdate(orderUpdateDTO, purchaseOrder);
        }

        if (orderUpdateDTO.getOrderNumber() != null) {
            purchaseOrder.setOrderNumber(orderUpdateDTO.getOrderNumber());
        }
        if (orderUpdateDTO.getDate() != null) {
            purchaseOrder.setDate(orderUpdateDTO.getDate());
        }

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
        return modelMapper.map(purchaseOrder, PurchaseOrderDTO.class);
    }

    private void handleStatusUpdate(PurchaseOrderUpdateDTO orderUpdateDTO, PurchaseOrder purchaseOrder) {
        if (purchaseOrder.getStatus().equals(PurchaseOrderStatus.DELIVERED) &&
                orderUpdateDTO.getStatus().equals(PurchaseOrderStatus.CANCELED)) {
            throw new IllegalStateException("Não é possível cancelar um pedido entregue");
        }

        if (orderUpdateDTO.getStatus().equals(PurchaseOrderStatus.DELIVERED)) {
            purchaseOrder.getItems().forEach(orderItem -> {
                ProductVariant productVariant = orderItem.getProductVariant();
                Inventory inventory = purchaseOrder.getInventory();

                Optional<InventoryItem> inventoryItem = inventoryItemRepository.findByInventoryAndProductVariant(inventory, productVariant);
                if (inventoryItem.isPresent()) {
                    InventoryItem item = inventoryItem.get();

                    if (item.getLastPurchaseDate() == null || item.getLastPurchaseDate().isBefore(purchaseOrder.getDate())) {
                        item.setLastPurchaseDate(purchaseOrder.getDate());
                        item.setLastPurchasePrice(orderItem.getPurchasePrice());
                    }

                    item.setQuantity(item.getQuantity() + orderItem.getQuantity());
                    inventoryItemRepository.save(item);
                } else {
                    InventoryItem newInventoryItem = InventoryItem.builder()
                            .inventory(inventory)
                            .productVariant(productVariant)
                            .quantity(orderItem.getQuantity())
                            .lastPurchasePrice(orderItem.getPurchasePrice())
                            .lastPurchaseDate(purchaseOrder.getDate())
                            .build();
                    inventoryItemRepository.save(newInventoryItem);
                }
            });

            productVariantRepository.saveAll(purchaseOrder.getItems().stream()
                    .map(PurchaseOrderItem::getProductVariant)
                    .collect(Collectors.toList()));
        }

        purchaseOrder.setStatus(orderUpdateDTO.getStatus());
    }
}
