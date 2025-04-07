package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.order.*;
import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.exception.OrderAlreadyExistsException;
import com.jaoow.helmetstore.exception.OrderNotFoundException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.PurchaseOrderItem;
import com.jaoow.helmetstore.model.PurchaseOrderStatus;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final ModelMapper modelMapper;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductVariantRepository productVariantRepository;

    public List<PurchaseOrderDTO> findAll() {
        return purchaseOrderRepository.findAllWithItemsAndVariants().stream()
                .map(order -> modelMapper.map(order, PurchaseOrderDTO.class))
                .collect(Collectors.toList());
    }

    public PurchaseOrderHistoryResponse getHistory() {
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findAllWithItemsAndVariants();

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
    public PurchaseOrderDTO save(PurchaseOrderCreateDTO orderCreateDTO) {
        PurchaseOrder purchaseOrder = modelMapper.map(orderCreateDTO, PurchaseOrder.class);

        if (purchaseOrderRepository.existsByOrderNumber(orderCreateDTO.getOrderNumber())) {
            throw new OrderAlreadyExistsException();
        }

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

        purchaseOrder.setTotalAmount(totalAmount);
        purchaseOrder.setItems(items);

        purchaseOrder = purchaseOrderRepository.save(purchaseOrder);
        return modelMapper.map(purchaseOrder, PurchaseOrderDTO.class);
    }

    @Transactional
    public PurchaseOrderDTO update(Long id, PurchaseOrderUpdateDTO orderUpdateDTO) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(id)
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
                Product mainProduct = productVariant.getProduct();

                if (mainProduct.getLastPurchaseDate() == null || mainProduct.getLastPurchaseDate().isBefore(purchaseOrder.getDate())) {
                    mainProduct.setLastPurchaseDate(purchaseOrder.getDate());
                    mainProduct.setLastPurchasePrice(orderItem.getPurchasePrice());
                }

                productVariant.setQuantity(productVariant.getQuantity() + orderItem.getQuantity());
            });

            productVariantRepository.saveAll(purchaseOrder.getItems().stream()
                    .map(PurchaseOrderItem::getProductVariant)
                    .collect(Collectors.toList()));
        }

        purchaseOrder.setStatus(orderUpdateDTO.getStatus());
    }
}
