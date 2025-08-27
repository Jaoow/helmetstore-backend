package com.jaoow.helmetstore.service;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.jaoow.helmetstore.dto.fiscal.FiscalReportDTO;
import com.jaoow.helmetstore.dto.fiscal.PurchaseEntryDTO;
import com.jaoow.helmetstore.dto.fiscal.SaleExitDTO;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.inventory.Inventory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.Principal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Serviço para exportação fiscal consolidada
 * Gera relatórios completos para apresentação à Receita Federal (MEI)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalExportService {

    private final FiscalReportService fiscalReportService;
    private final InventoryHelper inventoryHelper;

    @Value("${app.company.name:MEI - Microempreendedor Individual}")
    private String companyName;

    @Value("${app.company.cnpj:00.000.000/0000-00}")
    private String companyCnpj;

    /**
     * Exporta relatório fiscal consolidado em PDF
     * Contém todas as compras, vendas e resumo fiscal para MEI
     */
    public byte[] exportConsolidatedFiscalReport(LocalDate startDate, LocalDate endDate, Principal principal) throws Exception {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        FiscalReportDTO fiscalReport = fiscalReportService.generateEntryExitReport(startDate, endDate, principal);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, baos);

        document.open();

        // Título
        addTitle(document, startDate, endDate);

        // Dados da empresa
        addCompanyData(document);

        // Resumo executivo
        addExecutiveSummary(document, fiscalReport);

        // Tabela de compras (entradas)
        addPurchasesTable(document, fiscalReport);

        // Nova página para vendas
        document.newPage();

        // Tabela de vendas (saídas)
        addSalesTable(document, fiscalReport);

        // Resumo final
        addFinalSummary(document, fiscalReport);

        document.close();
        log.info("Consolidated fiscal report exported for period: {} to {}", startDate, endDate);

        return baos.toByteArray();
    }

    /**
     * Exporta dados em formato CSV para planilhas
     */
    public String exportToCSV(LocalDate startDate, LocalDate endDate, Principal principal) {
        FiscalReportDTO fiscalReport = fiscalReportService.generateEntryExitReport(startDate, endDate, principal);
        StringBuilder csv = new StringBuilder();
        
        // Cabeçalho
        csv.append("RELATÓRIO FISCAL MEI - PERÍODO: ")
           .append(startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
           .append(" a ")
           .append(endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
           .append("\n\n");

        // Compras
        csv.append("COMPRAS (ENTRADAS)\n");
        csv.append("Data,Pedido,NF,Série,Fornecedor,CNPJ/CPF,Categoria,Valor\n");
        
        for (PurchaseEntryDTO entry : fiscalReport.getEntries()) {
            csv.append(entry.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append(",")
               .append(entry.getOrderNumber() != null ? entry.getOrderNumber() : "").append(",")
               .append(entry.getInvoiceNumber() != null ? entry.getInvoiceNumber() : "").append(",")
               .append(entry.getInvoiceSeries() != null ? entry.getInvoiceSeries() : "").append(",")
               .append(entry.getSupplierName() != null ? entry.getSupplierName() : "").append(",")
               .append(entry.getSupplierTaxId() != null ? entry.getSupplierTaxId() : "").append(",")
               .append(entry.getPurchaseCategory() != null ? entry.getPurchaseCategory() : "").append(",")
               .append(entry.getTotalAmount()).append("\n");
        }

        csv.append("\nVENDAS (SAÍDAS)\n");
        csv.append("Data,ID Venda,Cliente,Telefone,Forma Pagamento,Valor\n");
        
        for (SaleExitDTO exit : fiscalReport.getExits()) {
            csv.append(exit.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append(",")
               .append(exit.getSaleId()).append(",")
               .append(exit.getCustomerName() != null ? exit.getCustomerName() : "").append(",")
               .append(exit.getCustomerPhone() != null ? exit.getCustomerPhone() : "").append(",")
               .append(exit.getPaymentMethod() != null ? exit.getPaymentMethod() : "").append(",")
               .append(exit.getTotalAmount()).append("\n");
        }

        // Resumo
        csv.append("\nRESUMO\n");
        csv.append("Total Compras,").append(fiscalReport.getTotalEntries()).append("\n");
        csv.append("Total Vendas,").append(fiscalReport.getTotalExits()).append("\n");
        csv.append("Saldo,").append(fiscalReport.getBalance()).append("\n");

        return csv.toString();
    }

    private void addTitle(Document document, LocalDate startDate, LocalDate endDate) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
        Paragraph title = new Paragraph("RELATÓRIO FISCAL CONSOLIDADO - MEI", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Font periodFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.GRAY);
        Paragraph period = new Paragraph("Período: " + 
            startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " +
            endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);
    }

    private void addCompanyData(Document document) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);

        Paragraph companyTitle = new Paragraph("DADOS DA EMPRESA", labelFont);
        companyTitle.setSpacingBefore(10);
        companyTitle.setSpacingAfter(10);
        document.add(companyTitle);

        document.add(new Paragraph("Razão Social: " + companyName, dataFont));
        document.add(new Paragraph("CNPJ: " + companyCnpj, dataFont));
        document.add(new Paragraph(" ")); // Espaço
    }

    private void addExecutiveSummary(Document document, FiscalReportDTO fiscalReport) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        Paragraph summaryTitle = new Paragraph("RESUMO EXECUTIVO", labelFont);
        summaryTitle.setSpacingBefore(10);
        summaryTitle.setSpacingAfter(10);
        document.add(summaryTitle);

        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(60);
        summaryTable.setWidths(new float[]{3, 2});

        addSummaryRow(summaryTable, "Total de Compras:", currencyFormat.format(fiscalReport.getTotalEntries()));
        addSummaryRow(summaryTable, "Total de Vendas:", currencyFormat.format(fiscalReport.getTotalExits()));
        addSummaryRow(summaryTable, "Saldo Período:", currencyFormat.format(fiscalReport.getBalance()));
        addSummaryRow(summaryTable, "Quantidade Compras:", String.valueOf(fiscalReport.getEntries().size()));
        addSummaryRow(summaryTable, "Quantidade Vendas:", String.valueOf(fiscalReport.getExits().size()));

        document.add(summaryTable);
        document.add(new Paragraph(" ")); // Espaço
    }

    private void addPurchasesTable(Document document, FiscalReportDTO fiscalReport) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE);
        Font tableDataFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);

        Paragraph purchasesTitle = new Paragraph("COMPRAS (ENTRADAS)", labelFont);
        purchasesTitle.setSpacingBefore(15);
        purchasesTitle.setSpacingAfter(10);
        document.add(purchasesTitle);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1.5f, 1f, 2f, 2f, 1.5f, 1.5f});

        // Cabeçalho
        addTableHeader(table, "Data", tableHeaderFont);
        addTableHeader(table, "Pedido", tableHeaderFont);
        addTableHeader(table, "NF", tableHeaderFont);
        addTableHeader(table, "Fornecedor", tableHeaderFont);
        addTableHeader(table, "CNPJ/CPF", tableHeaderFont);
        addTableHeader(table, "Categoria", tableHeaderFont);
        addTableHeader(table, "Valor", tableHeaderFont);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        for (PurchaseEntryDTO entry : fiscalReport.getEntries()) {
            table.addCell(new PdfPCell(new Phrase(entry.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yy")), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(entry.getOrderNumber() != null ? entry.getOrderNumber() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(entry.getInvoiceNumber() != null ? entry.getInvoiceNumber() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(entry.getSupplierName() != null ? entry.getSupplierName() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(entry.getSupplierTaxId() != null ? entry.getSupplierTaxId() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(entry.getPurchaseCategory() != null ? entry.getPurchaseCategory().toString() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(currencyFormat.format(entry.getTotalAmount()), tableDataFont)));
        }

        document.add(table);
    }

    private void addSalesTable(Document document, FiscalReportDTO fiscalReport) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font tableHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BaseColor.WHITE);
        Font tableDataFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.BLACK);

        Paragraph salesTitle = new Paragraph("VENDAS (SAÍDAS)", labelFont);
        salesTitle.setSpacingBefore(15);
        salesTitle.setSpacingAfter(10);
        document.add(salesTitle);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1f, 2.5f, 2f, 2f});

        // Cabeçalho
        addTableHeader(table, "Data", tableHeaderFont);
        addTableHeader(table, "ID", tableHeaderFont);
        addTableHeader(table, "Cliente", tableHeaderFont);
        addTableHeader(table, "Pagamento", tableHeaderFont);
        addTableHeader(table, "Valor", tableHeaderFont);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        for (SaleExitDTO exit : fiscalReport.getExits()) {
            table.addCell(new PdfPCell(new Phrase(exit.getDate().format(DateTimeFormatter.ofPattern("dd/MM/yy")), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(exit.getSaleId().toString(), tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(exit.getCustomerName() != null ? exit.getCustomerName() : "N/A", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(exit.getPaymentMethod() != null ? exit.getPaymentMethod().toString() : "-", tableDataFont)));
            table.addCell(new PdfPCell(new Phrase(currencyFormat.format(exit.getTotalAmount()), tableDataFont)));
        }

        document.add(table);
    }

    private void addFinalSummary(Document document, FiscalReportDTO fiscalReport) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        Paragraph finalTitle = new Paragraph("RESUMO FINAL", labelFont);
        finalTitle.setSpacingBefore(20);
        finalTitle.setSpacingAfter(15);
        finalTitle.setAlignment(Element.ALIGN_CENTER);
        document.add(finalTitle);

        PdfPTable finalTable = new PdfPTable(2);
        finalTable.setWidthPercentage(50);
        finalTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        finalTable.setWidths(new float[]{2, 1});

        addSummaryRow(finalTable, "TOTAL ENTRADAS:", currencyFormat.format(fiscalReport.getTotalEntries()));
        addSummaryRow(finalTable, "TOTAL SAÍDAS:", currencyFormat.format(fiscalReport.getTotalExits()));
        
        PdfPCell balanceLabel = new PdfPCell(new Phrase("SALDO LÍQUIDO:", labelFont));
        PdfPCell balanceValue = new PdfPCell(new Phrase(currencyFormat.format(fiscalReport.getBalance()), labelFont));
        balanceLabel.setBackgroundColor(BaseColor.LIGHT_GRAY);
        balanceValue.setBackgroundColor(BaseColor.LIGHT_GRAY);
        finalTable.addCell(balanceLabel);
        finalTable.addCell(balanceValue);

        document.add(finalTable);

        // Nota legal
        Font noteFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);
        Paragraph legalNote = new Paragraph("\n\nEste relatório foi gerado automaticamente pelo sistema de gestão MEI " +
                "e contém todas as movimentações fiscais do período especificado.", noteFont);
        legalNote.setAlignment(Element.ALIGN_CENTER);
        document.add(legalNote);
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(BaseColor.DARK_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value) {
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 11, BaseColor.BLACK);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BaseColor.BLACK);
        
        table.addCell(new PdfPCell(new Phrase(label, dataFont)));
        table.addCell(new PdfPCell(new Phrase(value, valueFont)));
    }
}