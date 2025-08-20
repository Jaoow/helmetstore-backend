package com.jaoow.helmetstore.nf;

import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.nf.dto.OrderItemDTO;
import com.jaoow.helmetstore.nf.dto.OrderSummaryDTO;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OrderPDFExtractorService {
    private final ModelMapper modelMapper;
    private final ProductVariantRepository productVariantRepository;

    public OrderPDFExtractorService(ModelMapper modelMapper, ProductVariantRepository productVariantRepository) {
        this.modelMapper = modelMapper;
        this.productVariantRepository = productVariantRepository;
    }

    public OrderSummaryDTO extractOrderSummary(File pdfFile) throws IOException {
        String invoiceNumber = "";
        String invoiceDate = "";
        String purchaseOrderNumber = "";
        double totalPrice = 0.0;
        List<OrderItemDTO> items = new ArrayList<>();
        List<String> itemsNotFound = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document).replaceAll("\\r?\\n\\s*", " ");

            Pattern invoiceNumberPattern = Pattern.compile("NF-e\\s*Nº\\.\\s*([\\d\\.]+)");
            Matcher invoiceNumberMatcher = invoiceNumberPattern.matcher(text);
            if (invoiceNumberMatcher.find()) {
                invoiceNumber = invoiceNumberMatcher.group(1);
            }

            Pattern purchaseOrderPattern = Pattern
                    .compile("Inf\\.\\s*fisco:\\s*PEDIDO\\s*DE\\s*COMPRA:\\s*([A-Z0-9-]+)");
            Matcher purchaseOrderMatcher = purchaseOrderPattern.matcher(text);
            if (purchaseOrderMatcher.find()) {
                purchaseOrderNumber = purchaseOrderMatcher.group(1);
            }

            Pattern issueDatePattern = Pattern.compile("DATA DA EMISSÃO\\s*(\\d{2}/\\d{2}/\\d{4})");
            Matcher issueDateMatcher = issueDatePattern.matcher(text);
            if (issueDateMatcher.find()) {
                invoiceDate = issueDateMatcher.group(1);
            }

            Pattern totalPattern = Pattern.compile("VALOR TOTAL:\\s*R\\$\\s*([\\d.,]+)");
            Matcher totalMatcher = totalPattern.matcher(text);
            if (totalMatcher.find()) {
                totalPrice = Double.parseDouble(totalMatcher.group(1).replace(".", "").replace(",", "."));
            }

            Pattern itemPattern = Pattern.compile("(CAP-\\d+[A-Za-z]+)\\s+.*?\\s*(UN \\d{1,3},\\d{4})");
            Matcher itemMatcher = itemPattern.matcher(text);

            while (itemMatcher.find()) {
                String sku = itemMatcher.group(1).trim();
                int quantity = (int) Double.parseDouble(itemMatcher.group(2).replace(",", ".").split(" ")[1]);

                Optional<ProductVariant> product = productVariantRepository.findBySku(sku);
                if (product.isPresent()) {
                    OrderItemDTO itemDTO = modelMapper.map(product.get(), OrderItemDTO.class);
                    itemDTO.setQuantity(quantity);

                    items.add(itemDTO);
                } else {
                    itemsNotFound.add(sku);
                }
            }
        }
        return new OrderSummaryDTO(invoiceNumber, invoiceDate, purchaseOrderNumber, totalPrice, items, itemsNotFound);
    }
}
