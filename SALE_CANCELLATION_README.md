# Sistema de Cancelamento de Vendas - HelmetStore

## 📌 Visão Geral

O sistema de cancelamento de vendas permite **cancelar vendas de forma controlada**, preservando o histórico e garantindo integridade operacional, de estoque e financeira, ao invés de simplesmente excluir registros.

## 🎯 Funcionalidades

### Status da Venda
- **FINALIZADA** - Venda confirmada e ativa
- **CANCELADA** - Venda totalmente cancelada
- **CANCELADA_PARCIAL** - Venda parcialmente cancelada (alguns itens foram cancelados)

> **Importante:** O status indica a situação comercial. O reembolso financeiro é controlado por **flag de estorno** e **valor reembolsado**, e não pelo status.

### Tipos de Cancelamento

#### 1. Cancelamento Total
- Cancela todos os itens da venda
- Reverte todo o estoque
- Status muda para `CANCELADA`

#### 2. Cancelamento Parcial
- Cancela apenas itens específicos
- Reverte estoque proporcionalmente
- Status muda para `CANCELADA_PARCIAL`
- Permite cancelar quantidade parcial de um item

### Estorno / Reembolso

O sistema suporta estorno financeiro independente do cancelamento:

- **Flag de estorno** (`hasRefund`) indica se houve reembolso
- **Valor do estorno** (`refundAmount`) pode ser total ou parcial
- **Método de reembolso** (`refundPaymentMethod`) registra como foi feito
- **Transação vinculada** cria automaticamente uma transação de saída

#### Regras de Estorno
- ✅ Estorno só pode ocorrer para vendas pagas
- ✅ Valor do estorno não pode ser maior que o valor pago
- ✅ Valor do estorno deve ser maior que zero
- ✅ Quando há estorno, é gerada uma **Transação de Saída**
- ✅ `CANCELADA` → pode ou não gerar estorno (opcional)
- ⚠️ `CANCELADA_PARCIAL` → **estorno é obrigatório** (regra de negócio)

## 📋 Metadados de Cancelamento

O sistema registra:
- **Data do cancelamento** (`cancelledAt`)
- **Usuário responsável** (`cancelledBy`)
- **Motivo do cancelamento** (`cancellationReason`)
- **Observações** (`cancellationNotes`)

### Motivos de Cancelamento Disponíveis
- `DESISTENCIA` - Desistência do cliente
- `DEFEITO` - Produto com defeito
- `ERRO_LANCAMENTO` - Erro no lançamento
- `FALTA_ESTOQUE` - Falta de estoque
- `PAGAMENTO_NAO_CONFIRMADO` - Pagamento não confirmado
- `DEVOLUCAO` - Devolução
- `OUTROS` - Outros motivos

## 🔌 API - Endpoint de Cancelamento

### POST `/sales/{id}/cancel`

Cancela uma venda (total ou parcialmente) com possibilidade de estorno.

#### Request Body

```json
{
  "reason": "DESISTENCIA",
  "notes": "Cliente solicitou cancelamento por telefone",
  "cancelEntireSale": true,
  "generateRefund": true,
  "refundAmount": 150.00,
  "refundPaymentMethod": "PIX"
}
```

#### Parâmetros

- `reason` (obrigatório) - Motivo do cancelamento
- `notes` (opcional) - Observações adicionais
- `cancelEntireSale` (booleano, default: `true`) - Se `true`, cancela toda a venda; se `false`, cancela apenas os itens especificados
- `itemsToCancel` (array, obrigatório se `cancelEntireSale=false`) - Lista de itens a cancelar
- `generateRefund` (booleano, default: `false`) - Se deve gerar estorno
- `refundAmount` (decimal, obrigatório se `generateRefund=true`) - Valor do estorno
- `refundPaymentMethod` (string, obrigatório se `generateRefund=true`) - Método de reembolso (`CASH`, `PIX`, `CARD`)

#### Exemplo: Cancelamento Parcial

```json
{
  "reason": "DEFEITO",
  "notes": "Apenas o capacete tamanho M estava com defeito",
  "cancelEntireSale": false,
  "itemsToCancel": [
    {
      "itemId": 123,
      "quantityToCancel": 1
    }
  ],
  "generateRefund": true,
  "refundAmount": 75.00,
  "refundPaymentMethod": "PIX"
}
```

#### Response

```json
{
  "saleId": 456,
  "status": "CANCELADA",
  "cancelledAt": "2026-01-28T14:30:00",
  "cancelledBy": "user@example.com",
  "cancellationReason": "DESISTENCIA",
  "cancellationNotes": "Cliente solicitou cancelamento por telefone",
  "hasRefund": true,
  "refundAmount": 150.00,
  "refundPaymentMethod": "PIX",
  "refundTransactionId": 789,
  "message": "Venda cancelada com sucesso"
}
```

## 🔒 Regras de Validação

O sistema valida automaticamente:

1. ❌ Não permitir cancelar venda já totalmente cancelada
2. ❌ Não permitir estorno se a venda não foi paga (sem `payments`)
3. ❌ Não permitir estorno com valor zero
4. ❌ Não permitir valor de estorno maior que o valor pago
5. ❌ Não permitir cancelar quantidade maior que a disponível no item
6. ❌ Não permitir cancelar item já cancelado
7. ⚠️ **Cancelamento parcial exige estorno obrigatório** (regra de negócio)

## 🔄 Reversão de Estoque

Quando uma venda é cancelada:

1. **Estoque é automaticamente devolvido** ao inventário
2. A quantidade é adicionada de volta ao `InventoryItem` correspondente
3. Para cancelamento parcial, apenas a quantidade cancelada é devolvida
4. Histórico de movimentação é preservado

## 💰 Impacto Financeiro

### Transação de Estorno

Quando `generateRefund=true`, o sistema:

1. Cria uma **Transação de Saída** (`EXPENSE`)
2. Define o detail como `REFUND`
3. Valor é negativo (saída de caixa)
4. Vincula à conta do método de pagamento escolhido
5. **Flags do Ledger**:
   - `affectsProfit = false` (não afeta lucro, apenas devolve despesa anterior)
   - `affectsCash = true` (reduz caixa disponível)
   - `walletDestination` = `CASH` ou `BANK` conforme o método

### Cache Invalidation

O cancelamento invalida automaticamente os seguintes caches:
- `PRODUCT_INDICATORS`
- `MOST_SOLD_PRODUCTS`
- `PRODUCT_STOCK`
- `REVENUE_AND_PROFIT`
- `SALES_HISTORY`

## 📊 Banco de Dados

### Novas Colunas na Tabela `sale`

```sql
status VARCHAR(30) NOT NULL DEFAULT 'FINALIZADA'
cancelled_at TIMESTAMP
cancelled_by VARCHAR(255)
cancellation_reason VARCHAR(50)
cancellation_notes TEXT
has_refund BOOLEAN NOT NULL DEFAULT FALSE
refund_amount DECIMAL(12,2)
refund_payment_method VARCHAR(20)
refund_transaction_id BIGINT
```

### Novas Colunas na Tabela `sale_item`

```sql
is_cancelled BOOLEAN NOT NULL DEFAULT FALSE
cancelled_quantity INT
```

### Migration

A migration `V4_0_0__Add_Sale_Cancellation_System.sql` cria:
- Todas as colunas necessárias
- Índices de performance
- Constraints de validação
- Comentários de documentação

## 🧪 Exemplos de Uso

### 1. Cancelamento Total com Estorno

```bash
POST /sales/456/cancel
```

```json
{
  "reason": "ERRO_LANCAMENTO",
  "notes": "Venda lançada na conta errada",
  "cancelEntireSale": true,
  "generateRefund": true,
  "refundAmount": 300.00,
  "refundPaymentMethod": "CASH"
}
```
com Estorno Proporcional

```bash
POST /sales/456/cancel
```

```json
{
  "reason": "FALTA_ESTOQUE",
  "notes": "Tamanho G não disponível",
  "cancelEntireSale": false,
  "itemsToCancel": [
    {
      "itemId": 789,
      "quantityToCancel": 2
    }
  ],
  "generateRefund": true,
  "refundAmount": 150.00,
  "refundPaymentMethod": "PIX"
}
```

> ⚠️ **Nota:** Cancelamento parcial **sempre exige estorno** do valor proporcional.
```

### 3. Cancelamento Total sem Estorno

```bash
POST /sales/456/cancel
```

```json
{
  "reason": "PAGAMENTO_NAO_CONFIRMADO",
  "notes": "PIX não foi confirmado após 24h",
  "cancelEntireSale": true,
  "generateRefund": false
}
```

## 🔐 Segurança

- O usuário deve estar autenticado
- Apenas vendas do inventário do usuário podem ser canceladas
- Usuário responsável é automaticamente registrado
- Todas as operações são transacionais (rollback em caso de erro)

## 📈 Monitoramento

O sistema invalida caches automaticame

## 🏗️ Arquitetura e Design Decisions

### Separação de Responsabilidades

A lógica está dividida claramente:
- **`validateCancellation`** - Valida regras de negócio antes de executar
- **`reverseTotalInventory` / `reversePartialInventory`** - Apenas devolve estoque
- **`updateSaleStatus`** - Apenas atualiza status e marca itens como cancelados
- **`generateRefundTransaction`** - Apenas cria transação financeira

Isso evita duplicação e facilita manutenção futura.

### Flag `hasRefund` é Derivável?

Tecnicamente, `hasRefund` poderia ser calculado como:
```java
hasRefund = refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0
```

**Por que mantemos a flag:**
- ✅ Performance em queries (sem JOIN ou cálculo)
- ✅ Clareza semântica no domínio
- ✅ Facilita índices e filtros no banco
- ⚠️ Requer cuidado para manter consistênciante para garantir que:
- Relatórios de vendas sejam atualizados
- Indicadores de produto reflitam o cancelamento
- Estoque seja atualizado em tempo real
- Métricas de lucro sejam recalculadas

## ⚠️ Limitações Atuais

- Não há reversão de cancelamento (uma vez cancelado, não pode ser reativado)
- Não há limite de prazo para cancelamento (pode ser implementado futuramente)
- Múltiplos estornos para a mesma venda não possuem regra definida
- Sistema não suporta cancelamento parcial de quantidade já cancelada

## 🚀 Próximos Passos Sugeridos

1. Implementar prazo limite para cancelamento
2. Adicionar reversão de cancelamento
3. Criar relatório de vendas canceladas
4. Implementar notificações de cancelamento
5. Adicionar auditoria detalhada de mudanças
6. Criar dashboard de cancelamentos

---

**Versão:** 4.0.0  
**Data:** 28 de janeiro de 2026
