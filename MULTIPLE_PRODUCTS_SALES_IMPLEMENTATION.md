# Implementação de Suporte para Múltiplos Produtos em Vendas

## Resumo das Mudanças

Esta implementação adiciona suporte para que uma venda possa conter múltiplos produtos, mantendo compatibilidade com as funcionalidades já existentes.

## Backend - Mudanças Implementadas

### 1. Novos Modelos de Dados

#### SaleItem Entity

- **Arquivo**: `src/main/java/com/jaoow/helmetstore/model/sale/SaleItem.java`
- **Descrição**: Nova entidade que representa cada produto individual em uma venda
- **Campos**:
  - `productVariant`: Variante do produto
  - `quantity`: Quantidade vendida
  - `unitPrice`: Preço unitário de venda
  - `unitProfit`: Lucro unitário
  - `totalItemPrice`: Preço total do item (unitPrice \* quantity)
  - `totalItemProfit`: Lucro total do item (unitProfit \* quantity)

#### Sale Entity Atualizada

- **Arquivo**: `src/main/java/com/jaoow/helmetstore/model/Sale.java`
- **Mudanças**:
  - Adicionado relacionamento `@OneToMany` com `SaleItem`
  - Adicionados campos `totalAmount` e `totalProfit` calculados
  - Campos antigos marcados como `@Deprecated` para compatibilidade

### 2. DTOs Atualizados

#### Novos DTOs

- `SaleItemCreateDTO`: Para criação de itens de venda
- `SaleItemDTO`: Para resposta de itens de venda
- `SaleItemFormData`: Para dados de formulário no frontend

#### DTOs Atualizados

- `SaleCreateDTO`: Suporte para lista de itens + campos legacy
- `SaleDetailDTO`: Incluído lista de itens + campos legacy
- `SaleResponseDTO`: Incluído lista de itens + campos legacy

### 3. Helper de Cálculos

- **Arquivo**: `src/main/java/com/jaoow/helmetstore/helper/SaleCalculationHelper.java`
- **Funcionalidades**:
  - Cálculo de lucro unitário e total
  - Validação de soma de pagamentos
  - Cálculo de totais de venda

### 4. Service Atualizado

- **Arquivo**: `src/main/java/com/jaoow/helmetstore/service/SaleService.java`
- **Mudanças**:
  - Métodos refatorados para suportar múltiplos produtos
  - Compatibilidade com formato legacy mantida
  - Validações atualizadas para múltiplos itens

### 5. Consultas Atualizadas

- **Arquivos**:
  - `src/main/java/com/jaoow/helmetstore/repository/InventoryItemRepository.java`
  - `src/main/java/com/jaoow/helmetstore/repository/SaleRepository.java`
- **Mudanças**:
  - Consultas de produtos mais vendidos atualizadas
  - Consultas de resumo financeiro atualizadas
  - Suporte para ambos os formatos (novo e legacy)

### 6. Script de Migração

- **Arquivo**: `src/main/resources/db/migration/V1_0_1__Add_Multiple_Products_Support_To_Sales.sql`
- **Funcionalidades**:
  - Criação da tabela `sale_item`
  - Migração de vendas existentes para o novo formato
  - Manutenção de integridade dos dados

## Frontend - Mudanças Implementadas

### 1. Formulário de Registro de Venda

- **Arquivo**: `src/pages/sale/RegisterSaleForm.jsx`
- **Mudanças**:
  - Interface para adicionar múltiplos produtos
  - Validação de produtos e quantidades
  - Cálculo automático de totais
  - Modal para seleção de produtos

### 2. Histórico de Vendas

- **Arquivo**: `src/pages/sale/SaleHistory.jsx`
- **Mudanças**:
  - Exibição de vendas com múltiplos produtos
  - Compatibilidade com vendas antigas
  - Totais calculados corretamente

### 3. Edição de Vendas

- **Arquivo**: `src/components/modal/EditSaleModal.tsx`
- **Mudanças**:
  - Suporte para editar vendas com múltiplos produtos
  - Interface atualizada para gerenciar itens
  - Validações mantidas

### 4. Tipos TypeScript

- **Arquivo**: `src/types/index.ts`
- **Mudanças**:
  - Novos tipos para itens de venda
  - Compatibilidade com formato legacy

## Compatibilidade com Dados Existentes

### Backend

- Campos antigos mantidos como `@Deprecated`
- Lógica de detecção de formato (novo vs legacy)
- Conversão automática de formato legacy para novo

### Frontend

- Detecção automática de formato de dados
- Exibição adequada para ambos os formatos
- Validações mantidas

## Validações Implementadas

### Backend

1. Pelo menos um item por venda
2. Quantidade e preço válidos para todos os itens
3. Soma dos pagamentos igual ao total da venda
4. Estoque suficiente para todos os itens

### Frontend

1. Pelo menos um produto adicionado
2. Quantidade maior que zero para todos os produtos
3. Preço válido para todos os produtos
4. Verificação de estoque disponível

## Regras de Negócio Mantidas

1. ✅ Cada produto em uma venda tem um preço de venda individual
2. ✅ O lucro é registrado tanto unitário quanto total
3. ✅ Soma dos pagamentos deve ser igual ao valor total da venda
4. ✅ Uma venda sempre gera uma transação
5. ✅ Estoque é atualizado corretamente para todos os produtos

## Testes Recomendados

### Funcionalidades Básicas

1. Criar venda com um produto (formato legacy)
2. Criar venda com múltiplos produtos
3. Editar venda existente (legacy → novo formato)
4. Visualizar histórico com vendas mistas
5. Verificar produtos mais vendidos

### Validações

1. Tentar criar venda sem produtos
2. Criar venda com soma de pagamentos incorreta
3. Tentar vender mais que o estoque disponível
4. Verificar cálculos de lucro unitário e total

### Migração

1. Executar migração em base com dados existentes
2. Verificar integridade dos dados após migração
3. Confirmar que consultas retornam dados corretos

## Observações Importantes

1. **Compatibilidade**: A implementação mantém total compatibilidade com vendas existentes
2. **Performance**: Consultas otimizadas para trabalhar com ambos os formatos
3. **Escalabilidade**: Arquitetura preparada para futuras expansões
4. **Manutenibilidade**: Código bem documentado e estruturado

## Próximos Passos Recomendados

1. Testes extensivos em ambiente de desenvolvimento
2. Validação com dados reais em ambiente de staging
3. Documentação para usuários finais
4. Eventual remoção dos campos deprecated (em versão futura)
