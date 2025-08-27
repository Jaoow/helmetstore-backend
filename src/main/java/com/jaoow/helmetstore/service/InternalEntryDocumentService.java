package com.jaoow.helmetstore.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.PurchaseOrderItem;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serviço para gerar documentos internos de entrada para MEI
 * Gera PDF da "Nota de Entrada Interna – Estoque MEI"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalEntryDocumentService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryHelper inventoryHelper;

    @Value("${app.company.name:MEI - Microempreendedor Individual}")
    private String companyName;

    @Value("${app.company.cnpj:00.000.000/0000-00}")
    private String companyCnpj;

    @Value("${app.company.address:Endereço não cadastrado}")
    private String companyAddress;

    /**
     * Gera documento interno de entrada em PDF
     */
    public byte[] generateInternalEntryDocument(Long purchaseOrderId, Principal principal) throws Exception {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdAndInventory(purchaseOrderId, inventory)
                .orElseThrow(() -> new RuntimeException("Purchase order not found"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, baos);

        document.open();

        // Título
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Paragraph title = new Paragraph("NOTA DE ENTRADA INTERNA – ESTOQUE MEI", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Dados do MEI
        addMeiData(document);

        // Dados da NF do fornecedor
        addSupplierInvoiceData(document, purchaseOrder);

        // Lista de produtos
        addProductList(document, purchaseOrder);

        // Observação
        addObservation(document);

        document.close();
        log.info("Internal entry document generated for purchase order: {}", purchaseOrderId);

        return baos.toByteArray();
    }

    private void addMeiData(Document document) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        Paragraph meiTitle = new Paragraph("DADOS DO MEI", labelFont);
        meiTitle.setSpacingBefore(10);
        meiTitle.setSpacingAfter(10);
        document.add(meiTitle);

        document.add(new Paragraph("Nome/Razão Social: " + companyName, dataFont));
        document.add(new Paragraph("CNPJ: " + companyCnpj, dataFont));
        document.add(new Paragraph("Endereço: " + companyAddress, dataFont));
        document.add(new Paragraph(" ")); // Espaço
    }

    private void addSupplierInvoiceData(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        Paragraph supplierTitle = new Paragraph("DADOS DA NF DO FORNECEDOR", labelFont);
        supplierTitle.setSpacingBefore(10);
        supplierTitle.setSpacingAfter(10);
        document.add(supplierTitle);

        document.add(new Paragraph("Fornecedor: " + 
            (purchaseOrder.getSupplierName() != null ? purchaseOrder.getSupplierName() : "N/A"), dataFont));
        document.add(new Paragraph("CNPJ/CPF: " + 
            (purchaseOrder.getSupplierTaxId() != null ? purchaseOrder.getSupplierTaxId() : "N/A"), dataFont));
        document.add(new Paragraph("Número da NF: " + 
            (purchaseOrder.getInvoiceNumber() != null ? purchaseOrder.getInvoiceNumber() : "N/A"), dataFont));
        document.add(new Paragraph("Série: " + 
            (purchaseOrder.getInvoiceSeries() != null ? purchaseOrder.getInvoiceSeries() : "N/A"), dataFont));
        document.add(new Paragraph("Chave de Acesso: " + 
            (purchaseOrder.getAccessKey() != null ? purchaseOrder.getAccessKey() : "N/A"), dataFont));
        document.add(new Paragraph("Data: " + 
            (purchaseOrder.getDate() != null ? purchaseOrder.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "N/A"), dataFont));
        document.add(new Paragraph(" ")); // Espaço
    }

    private void addProductList(Document document, PurchaseOrder purchaseOrder) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE);
        Font tableDataFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);

        Paragraph productsTitle = new Paragraph("LISTA DOS PRODUTOS", labelFont);
        productsTitle.setSpacingBefore(10);
        productsTitle.setSpacingAfter(10);
        document.add(productsTitle);

        // Tabela de produtos
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 1, 2, 2});

        // Cabeçalho
        addTableHeader(table, "Produto", tableHeaderFont);
        addTableHeader(table, "Qtd", tableHeaderFont);
        addTableHeader(table, "Preço Unit.", tableHeaderFont);
        addTableHeader(table, "Total", tableHeaderFont);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        // Itens
        for (PurchaseOrderItem item : purchaseOrder.getItems()) {
            table.addCell(new PdfPCell(new Phrase(item.getProductVariant().getProduct().getModel() + 
                " - " + item.getProductVariant().getSize(), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(currencyFormat.format(item.getPurchasePrice()), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(currencyFormat.format(
                item.getPurchasePrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity()))), tableDataFont)));
        }

        // Total
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL GERAL", labelFont));
        totalLabelCell.setColspan(3);
        totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabelCell);
        
        PdfPCell totalValueCell = new PdfPCell(new Phrase(currencyFormat.format(purchaseOrder.getTotalAmount()), labelFont));
        table.addCell(totalValueCell);

        document.add(table);
        document.add(new Paragraph(" ")); // Espaço
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BaseColor.GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addObservation(Document document) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        Paragraph obsTitle = new Paragraph("OBSERVAÇÃO", labelFont);
        obsTitle.setSpacingBefore(10);
        obsTitle.setSpacingAfter(10);
        document.add(obsTitle);

        Paragraph observation = new Paragraph(
            "Mercadoria adquirida no CPF e destinada integralmente ao estoque do MEI", dataFont);
        document.add(observation);

        // Data e local
        Paragraph datePlace = new Paragraph("\n\nData: ___/___/______    Local: ________________", dataFont);
        document.add(datePlace);

        // Assinatura
        Paragraph signature = new Paragraph("\n\n\n_____________________________________\n" +
            "Assinatura do Responsável", dataFont);
        signature.setAlignment(Element.ALIGN_CENTER);
        document.add(signature);
    }
}