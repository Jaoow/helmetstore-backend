package com.jaoow.helmetstore.helper;

import com.jaoow.helmetstore.dto.sale.SaleItemCreateDTO;
import com.jaoow.helmetstore.dto.sale.SalePaymentCreateDTO;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class SaleCalculationHelper {

    /**
     * Calcula o lucro unitário de um item
     */
    public BigDecimal calculateUnitProfit(BigDecimal salePrice, BigDecimal purchasePrice) {
        if (salePrice == null || purchasePrice == null) {
            return BigDecimal.ZERO;
        }
        return salePrice.subtract(purchasePrice);
    }

    /**
     * Calcula o preço total de um item (preço unitário * quantidade)
     */
    public BigDecimal calculateTotalItemPrice(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Calcula o lucro total de um item (lucro unitário * quantidade)
     */
    public BigDecimal calculateTotalItemProfit(BigDecimal unitProfit, int quantity) {
        if (unitProfit == null) {
            return BigDecimal.ZERO;
        }
        return unitProfit.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Calcula o valor total da venda (soma de todos os itens)
     */
    public BigDecimal calculateSaleTotalAmount(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(SaleItem::getTotalItemPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula o lucro total da venda (soma de todos os lucros dos itens)
     */
    public BigDecimal calculateSaleTotalProfit(List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(SaleItem::getTotalItemProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula o valor total da venda baseado nos DTOs de criação
     */
    public BigDecimal calculateSaleTotalAmountFromDTO(List<SaleItemCreateDTO> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(item -> calculateTotalItemPrice(item.getUnitPrice(), item.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula a soma dos pagamentos
     */
    public BigDecimal calculatePaymentsSum(List<SalePaymentCreateDTO> payments) {
        if (payments == null || payments.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return payments.stream()
                .map(SalePaymentCreateDTO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Valida se a soma dos pagamentos é igual ao total da venda
     */
    public boolean validatePaymentsSum(BigDecimal saleTotal, List<SalePaymentCreateDTO> payments) {
        BigDecimal paymentsSum = calculatePaymentsSum(payments);
        return saleTotal.compareTo(paymentsSum) == 0;
    }

    /**
     * Popula os valores calculados de um SaleItem
     */
    public void populateSaleItemCalculations(SaleItem saleItem, InventoryItem inventoryItem) {
        BigDecimal unitProfit = calculateUnitProfit(saleItem.getUnitPrice(), inventoryItem.getAverageCost());
        BigDecimal totalItemPrice = calculateTotalItemPrice(saleItem.getUnitPrice(), saleItem.getQuantity());
        BigDecimal totalItemProfit = calculateTotalItemProfit(unitProfit, saleItem.getQuantity());

        saleItem.setUnitProfit(unitProfit);
        saleItem.setTotalItemPrice(totalItemPrice);
        saleItem.setTotalItemProfit(totalItemProfit);
        saleItem.setCostBasisAtSale(inventoryItem.getAverageCost()); // Snapshot do custo no momento da venda
    }
}
