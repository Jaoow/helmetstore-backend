package com.jaoow.helmetstore.service.pdf;

import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.StoreInfo;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.service.StoreInfoService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaleReceiptPDFService {

    private final StoreInfoService storeInfoService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 10;
    private static final float TITLE_SIZE = 16;
    private static final float SUBTITLE_SIZE = 12;

    // Classe auxiliar para gerenciar o estado do PDF entre páginas
    @Getter @Setter @AllArgsConstructor
    private static class PDFState {
        private PDPageContentStream contentStream;
        private float yPosition;
        private PDDocument document;
    }

    public byte[] generateSaleReceipt(Sale sale) throws IOException {
        StoreInfo storeInfo = storeInfoService.getStoreInfoEntity(sale.getInventory());

        if (storeInfo == null) {
            throw new IllegalStateException("As informações da loja não foram configuradas.");
        }

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // Inicializa o estado com a primeira página
            PDFState state = new PDFState(
                new PDPageContentStream(document, page),
                page.getMediaBox().getHeight() - MARGIN,
                document
            );

            // 1. Cabeçalho da Loja
            drawStoreHeader(state, storeInfo);
            drawLine(state);

            // 2. Título e Informações da Venda
            state.setYPosition(state.getYPosition() - 20);
            drawTitle(state, "RECIBO DE VENDA");

            state.setYPosition(state.getYPosition() - 20);
            drawSaleInfo(state, sale);

            drawLine(state);

            // 3. Tabela de Itens
            state.setYPosition(state.getYPosition() - 20);
            drawTableHeader(state);

            state.setYPosition(state.getYPosition() - 15);
            drawSaleItems(state, sale.getItems());

            // 4. Rodapé e Total
            drawLine(state);

            state.setYPosition(state.getYPosition() - 20);
            drawTotal(state, sale.getTotalAmount());

            state.setYPosition(state.getYPosition() - 30);
            drawFooter(state);

            // Fecha o stream final e salva
            state.getContentStream().close();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private void drawStoreHeader(PDFState state, StoreInfo storeInfo) throws IOException {
        PDPageContentStream cs = state.getContentStream();

        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_SIZE);
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText(storeInfo.getName());
        cs.endText();

        state.setYPosition(state.getYPosition() - 15);
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText("Endereco: " + storeInfo.getAddress());
        cs.endText();

        if (storeInfo.getPhone() != null) {
            state.setYPosition(state.getYPosition() - 12);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, state.getYPosition());
            cs.showText("Telefone: " + storeInfo.getPhone());
            cs.endText();
        }

        if (storeInfo.getCnpj() != null && !storeInfo.getCnpj().isEmpty()) {
            state.setYPosition(state.getYPosition() - 12);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, state.getYPosition());
            cs.showText("CNPJ: " + storeInfo.getCnpj());
            cs.endText();
        }

        state.setYPosition(state.getYPosition() - 10);
    }

    private void drawLine(PDFState state) throws IOException {
        float width = PDRectangle.A4.getWidth();
        state.setYPosition(state.getYPosition() - 10);
        state.getContentStream().moveTo(MARGIN, state.getYPosition());
        state.getContentStream().lineTo(width - MARGIN, state.getYPosition());
        state.getContentStream().stroke();
    }

    private void drawTitle(PDFState state, String title) throws IOException {
        state.getContentStream().beginText();
        state.getContentStream().setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), TITLE_SIZE);
        state.getContentStream().newLineAtOffset(MARGIN, state.getYPosition());
        state.getContentStream().showText(title);
        state.getContentStream().endText();
    }

    private void drawSaleInfo(PDFState state, Sale sale) throws IOException {
        PDPageContentStream cs = state.getContentStream();
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText("Venda #" + sale.getId());
        cs.endText();

        state.setYPosition(state.getYPosition() - 12);
        cs.beginText();
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText("Data: " + sale.getDate().format(DATE_FORMATTER));
        cs.endText();
    }

    private void drawTableHeader(PDFState state) throws IOException {
        PDPageContentStream cs = state.getContentStream();
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), FONT_SIZE);
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText("Produto");
        cs.newLineAtOffset(250, 0); // Ajustado offset para não sobrepor
        cs.showText("Qtd");
        cs.newLineAtOffset(50, 0);
        cs.showText("Preco Unit.");
        cs.newLineAtOffset(100, 0);
        cs.showText("Subtotal");
        cs.endText();
    }

    private void drawSaleItems(PDFState state, List<SaleItem> items) throws IOException {
        for (SaleItem item : items) {
            // Verifica quebra de página
            if (state.getYPosition() < 80) {
                state.getContentStream().close();
                PDPage newPage = new PDPage(PDRectangle.A4);
                state.getDocument().addPage(newPage);
                state.setContentStream(new PDPageContentStream(state.getDocument(), newPage));
                state.setYPosition(newPage.getMediaBox().getHeight() - MARGIN);
                drawTableHeader(state);
                state.setYPosition(state.getYPosition() - 15);
            }

            Product product = item.getProductVariant().getProduct();
            ProductVariant variant = item.getProductVariant();

            String name = String.format("%s - %s - %s",
                product.getModel(), product.getColor(), variant.getSize());

            if (name.length() > 35) name = name.substring(0, 32) + "...";

            PDPageContentStream cs = state.getContentStream();
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
            cs.newLineAtOffset(MARGIN, state.getYPosition());
            cs.showText(name);
            cs.newLineAtOffset(250, 0);
            cs.showText(String.valueOf(item.getQuantity()));
            cs.newLineAtOffset(50, 0);
            cs.showText("R$ " + String.format("%.2f", item.getUnitPrice()));
            cs.newLineAtOffset(100, 0);
            BigDecimal subtotal = item.getUnitPrice().multiply(new BigDecimal(item.getQuantity()));
            cs.showText("R$ " + String.format("%.2f", subtotal));
            cs.endText();

            state.setYPosition(state.getYPosition() - 15);
        }
    }

    private void drawTotal(PDFState state, BigDecimal totalAmount) throws IOException {
        PDPageContentStream cs = state.getContentStream();
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), SUBTITLE_SIZE);
        cs.newLineAtOffset(350, state.getYPosition());
        cs.showText("TOTAL:");
        cs.newLineAtOffset(100, 0);
        cs.showText("R$ " + String.format("%.2f", totalAmount));
        cs.endText();
    }

    private void drawFooter(PDFState state) throws IOException {
        PDPageContentStream cs = state.getContentStream();
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
        cs.newLineAtOffset(MARGIN, state.getYPosition());
        cs.showText("Obrigado pela preferencia!");
        cs.newLineAtOffset(0, -12);
        cs.showText("Este documento nao tem valor fiscal.");
        cs.endText();
    }
}
