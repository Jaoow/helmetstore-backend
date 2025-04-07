package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.reference.SimpleProductDTO;
import com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleHistoryResponse;
import com.jaoow.helmetstore.dto.sale.SaleResponseDTO;
import com.jaoow.helmetstore.exception.InsufficientStockException;
import com.jaoow.helmetstore.exception.ProductNotFoundException;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ModelMapper modelMapper;
    private final ProductVariantRepository productVariantRepository;

    @Transactional(readOnly = true)
    public List<SaleResponseDTO> findAll() {
        return saleRepository.findAll().stream()
                .map(sale -> modelMapper.map(sale, SaleResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Transactional
    public SaleResponseDTO save(SaleCreateDTO saleCreateDTO) {
        Long variantId = saleCreateDTO.getVariantId();
        ProductVariant productVariant = productVariantRepository.findById(variantId).orElseThrow(() -> new ProductNotFoundException(variantId));

        if (productVariant.getQuantity() < saleCreateDTO.getQuantity()) {
            throw new InsufficientStockException("Estoque insuficiente para venda.");
        }

        Sale sale = modelMapper.map(saleCreateDTO, Sale.class);
        sale.setId(null);
        sale.setProductVariant(productVariant);

        BigDecimal lastPurchasePrice = sale.getProductVariant().getProduct().getLastPurchasePrice();
        BigDecimal unitPrice = sale.getUnitPrice();
        BigDecimal quantity = BigDecimal.valueOf(sale.getQuantity());

        BigDecimal profitPerUnit = unitPrice.subtract(lastPurchasePrice);
        BigDecimal totalProfit = profitPerUnit.multiply(quantity);

        sale.setTotalProfit(totalProfit);
        saleRepository.save(sale);

        productVariant.setQuantity(productVariant.getQuantity() - saleCreateDTO.getQuantity());
        productVariantRepository.save(productVariant);

        return modelMapper.map(sale, SaleResponseDTO.class);
    }

    @Transactional(readOnly = true)
    public SaleHistoryResponse getHistory() {
        List<Sale> sales = saleRepository.findAllWithProductVariantsAndProducts();

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
