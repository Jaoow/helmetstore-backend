# NF-e Management System for MEI - API Documentation

Este documento descreve as novas funcionalidades implementadas para gerenciamento de NF-e (Nota Fiscal Eletrônica) especificamente para MEI (Microempreendedor Individual).

## Novos Endpoints

### 1. Upload de Arquivos da NF-e

#### Upload PDF (DANFE)
```
POST /orders/{id}/upload-pdf
Content-Type: multipart/form-data
```
- **Parâmetro**: `file` (arquivo PDF)
- **Resposta**: Confirmação de upload com caminho do arquivo

#### Upload XML da NF-e
```
POST /orders/{id}/upload-xml
Content-Type: multipart/form-data
```
- **Parâmetro**: `file` (arquivo XML)
- **Resposta**: Confirmação de upload com caminho do arquivo

### 2. Documento Interno de Entrada

#### Gerar "Nota de Entrada Interna – Estoque MEI"
```
GET /orders/{id}/internal-entry-document
```
- **Resposta**: PDF gerado automaticamente
- **Conteúdo**: Dados do MEI, dados da NF do fornecedor, lista de produtos, observações legais

### 3. Relatórios Fiscais

#### Relatório de Entradas e Saídas (JSON)
```
GET /reports/fiscal/entry-exit?startDate=2024-01-01&endDate=2024-12-31
```
- **Parâmetros**: 
  - `startDate`: Data inicial (formato: YYYY-MM-DD)
  - `endDate`: Data final (formato: YYYY-MM-DD)
- **Resposta**: JSON com compras, vendas e totalizadores

#### Exportação Fiscal em PDF
```
GET /reports/fiscal/export/pdf?startDate=2024-01-01&endDate=2024-12-31
```
- **Resposta**: PDF consolidado com todas as movimentações fiscais
- **Conteúdo**: Relatório completo para apresentação à Receita Federal

#### Exportação Fiscal em CSV
```
GET /reports/fiscal/export/csv?startDate=2024-01-01&endDate=2024-12-31
```
- **Resposta**: Arquivo CSV para importação em planilhas
- **Conteúdo**: Dados tabulares de compras e vendas

## Campos Adicionados ao Modelo PurchaseOrder

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `invoiceNumber` | String | Número da NF-e |
| `invoiceSeries` | String | Série da NF-e |
| `accessKey` | String | Chave de acesso (44 dígitos) |
| `supplierName` | String | Nome do emitente/fornecedor |
| `supplierTaxId` | String | CNPJ ou CPF do emitente |
| `purchaseCategory` | Enum | CNPJ_MEI ou CPF_PERSONAL |
| `pdfFilePath` | String | Caminho do arquivo PDF |
| `xmlFilePath` | String | Caminho do arquivo XML |

## Enum PurchaseCategory

```java
public enum PurchaseCategory {
    CNPJ_MEI,      // Compra feita diretamente no CNPJ do MEI
    CPF_PERSONAL   // Compra pessoal no CPF destinada ao MEI
}
```

## Extração Aprimorada de PDF

O serviço `OrderPDFExtractorService` agora extrai:
- Número e série da NF-e
- Chave de acesso (44 dígitos)
- Nome e CNPJ/CPF do fornecedor
- Data de emissão
- Produtos e valores

## Configurações de Aplicação

```properties
# Configurações específicas da aplicação
app.upload.dir=${UPLOAD_DIR:uploads}
app.company.name=${COMPANY_NAME:MEI - Microempreendedor Individual}
app.company.cnpj=${COMPANY_CNPJ:00.000.000/0000-00}
app.company.address=${COMPANY_ADDRESS:Endereço não cadastrado}

# Upload de arquivos
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

## Funcionalidades Implementadas

### ✅ Registro de Dados da NF de Entrada
- Campos obrigatórios: número, série, chave de acesso, emitente, CNPJ/CPF
- Validação de chave de acesso (44 dígitos)
- Categoria de compra (CNPJ vs CPF)

### ✅ Upload da NF (PDF e XML)
- Upload seguro de arquivos
- Validação de tipos de arquivo
- Armazenamento organizado por ordem de compra

### ✅ Documento Interno de Entrada
- Geração automática de PDF
- Formatação profissional
- Informações legais para MEI

### ✅ Relatório de Entradas e Saídas
- Livro Caixa digital
- Filtros por período
- Totalizadores automáticos

### ✅ Controle de Estoque Vinculado à NF
- Link entre itens de estoque e ordem de compra original
- Rastreabilidade completa

### ✅ Exportação Fiscal
- PDF consolidado para Receita Federal
- CSV para planilhas
- Relatórios detalhados por período

## Exemplo de Uso

### 1. Criar Ordem de Compra com Dados da NF-e
```json
POST /orders
{
  "orderNumber": "PED-2024-001",
  "date": "2024-01-15",
  "invoiceNumber": "123456",
  "invoiceSeries": "1",
  "accessKey": "12345678901234567890123456789012345678901234",
  "supplierName": "Fornecedor LTDA",
  "supplierTaxId": "12.345.678/0001-90",
  "purchaseCategory": "CNPJ_MEI",
  "items": [...],
  "totalAmount": 1500.00
}
```

### 2. Upload de Arquivos
```bash
# Upload PDF
curl -X POST -F "file=@danfe.pdf" /orders/1/upload-pdf

# Upload XML
curl -X POST -F "file=@nfe.xml" /orders/1/upload-xml
```

### 3. Gerar Relatório Fiscal
```bash
# Relatório em PDF
curl "/reports/fiscal/export/pdf?startDate=2024-01-01&endDate=2024-12-31" -o relatorio-fiscal.pdf

# Dados em CSV
curl "/reports/fiscal/export/csv?startDate=2024-01-01&endDate=2024-12-31" -o dados-fiscais.csv
```

## Conformidade MEI

O sistema atende às exigências da Receita Federal para MEI:
- Livro Caixa digital
- Controle de entradas e saídas
- Documentação fiscal organizada
- Relatórios para prestação de contas
- Rastreabilidade de mercadorias

## Tecnologias Utilizadas

- **Spring Boot**: Framework base
- **JPA/Hibernate**: Persistência de dados
- **iText PDF**: Geração de documentos PDF
- **Apache PDFBox**: Extração de dados de PDF
- **PostgreSQL**: Banco de dados
- **ModelMapper**: Conversão entre DTOs e entidades

## Segurança

- Validação de tipos de arquivo
- Controle de acesso por usuário
- Armazenamento seguro de arquivos
- Validação de dados fiscais

Todas as funcionalidades foram implementadas seguindo as melhores práticas de desenvolvimento e atendem às necessidades específicas de um MEI brasileiro.