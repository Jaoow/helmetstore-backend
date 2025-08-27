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
        
        // Novos campos da NF-e
        String invoiceSeries = "";
        String accessKey = "";
        String supplierName = "";
        String supplierTaxId = "";

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document).replaceAll("\\r?\\n\\s*", " ");

            // Extrair número da NF-e
            Pattern invoiceNumberPattern = Pattern.compile("NF-e\\s*Nº\\.\\s*([\\d\\.]+)");
            Matcher invoiceNumberMatcher = invoiceNumberPattern.matcher(text);
            if (invoiceNumberMatcher.find()) {
                invoiceNumber = invoiceNumberMatcher.group(1);
            }
            
            // Extrair série da NF-e
            Pattern invoiceSeriesPattern = Pattern.compile("Série\\s*(\\d+)");
            Matcher invoiceSeriesMatcher = invoiceSeriesPattern.matcher(text);
            if (invoiceSeriesMatcher.find()) {
                invoiceSeries = invoiceSeriesMatcher.group(1);
            }
            
            // Extrair chave de acesso (44 dígitos)
            Pattern accessKeyPattern = Pattern.compile("(\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4}\\s+\\d{4})");
            Matcher accessKeyMatcher = accessKeyPattern.matcher(text);
            if (accessKeyMatcher.find()) {
                accessKey = accessKeyMatcher.group(1).replaceAll("\\s+", "");
            }
            
            // Extrair nome do emitente/fornecedor
            Pattern supplierNamePattern = Pattern.compile("EMITENTE[\\s\\S]*?Nome/Razão Social\\s*([\\w\\s]+?)\\s*(?:Nome Fantasia|Inscrição)");
            Matcher supplierNameMatcher = supplierNamePattern.matcher(text);
            if (supplierNameMatcher.find()) {
                supplierName = supplierNameMatcher.group(1).trim();
            }
            
            // Extrair CNPJ do emitente
            Pattern supplierTaxIdPattern = Pattern.compile("CNPJ\\s*([\\d\\./\\-]+)");
            Matcher supplierTaxIdMatcher = supplierTaxIdPattern.matcher(text);
            if (supplierTaxIdMatcher.find()) {
                supplierTaxId = supplierTaxIdMatcher.group(1);
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
        return OrderSummaryDTO.builder()
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .purchaseOrderNumber(purchaseOrderNumber)
                .totalPrice(totalPrice)
                .items(items)
                .itemsNotFound(itemsNotFound)
                .invoiceSeries(invoiceSeries)
                .accessKey(accessKey)
                .supplierName(supplierName)
                .supplierTaxId(supplierTaxId)
                .build();
    }
}
