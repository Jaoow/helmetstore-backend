# Financial Ecosystem Functional Specification

## Overview

This document describes the end-to-end financial ecosystem of the HelmetStore application, detailing how products flow through the system from purchase to sale, and how these activities are tracked and reported in the financial system.

## 1. Inbound (Purchase & Inventory): Stock Acquisition and Cost Basis

### Process Flow

The inbound process handles the acquisition of inventory and establishes the cost basis for future sales calculations.

#### 1.1 Purchase Order Creation
- **Trigger**: Business owner initiates purchase of products from suppliers
- **Data Captured**:
  - Order number and date
  - Supplier information (implied through order context)
  - Payment method (CASH, PIX, CARD)
  - List of products with quantities and purchase prices

#### 1.2 Inventory Update Process
- **Automatic Processing**:
  - System updates `InventoryItem` records for each product variant
  - Updates `quantity` (adds purchased amount)
  - Sets `averageCost` to the purchase cost per unit
  - Updates `lastPurchaseDate` to current date

#### 1.3 Cost Basis Establishment
- **Cost Basis Calculation**:
  - Each `InventoryItem` maintains a weighted average cost basis
  - **Average Cost Formula**:
    ```
    new_average_cost = ((current_quantity × current_average_cost) + (purchase_quantity × purchase_price)) ÷ (current_quantity + purchase_quantity)
    ```
  - Cost basis = `averageCost` per unit (weighted average of all purchases)
  - Used for profit calculations on future sales

#### 1.4 Financial Transaction Recording
- **Automatic Transaction Creation**:
  - Creates `PRODUCT_PURCHASE` transaction in the financial system
  - Transaction Type: `EXPENSE`
  - `deductsFromProfit`: `false` (inventory purchases don't affect profit directly)
  - Amount: Total purchase order value
  - Updates cash flow (reduces available cash)

### Data Models Involved

```java
// Purchase Order captures the acquisition
@Entity
public class PurchaseOrder {
    private Long id;
    private String orderNumber;
    private LocalDate date;
    private List<PurchaseOrderItem> items;
    private BigDecimal totalAmount;
    private PaymentMethod paymentMethod;
}

// Individual items with purchase prices
@Entity
public class PurchaseOrderItem {
    private ProductVariant productVariant;
    private int quantity;
    private BigDecimal purchasePrice; // Cost basis per unit
}

// Inventory tracking with current cost basis
@Entity
public class InventoryItem {
    private ProductVariant productVariant;
    private int quantity; // Current stock level
    private BigDecimal averageCost; // Current cost basis
    private LocalDate lastPurchaseDate;
}
```

### Business Rules

- **Cost Basis Updates**: Each purchase recalculates the weighted average cost for that product variant
- **Stock Accumulation**: Quantities are additive across multiple purchases
- **Financial Impact**: Purchase reduces cash but doesn't affect profit until products are sold

## 2. Outbound (Sales & Transactions): Product Sales Journey

### Process Flow

The outbound process converts inventory into revenue and tracks profitability.

#### 2.1 Sale Initiation
- **Trigger**: Customer purchase through the application interface
- **Data Captured**:
  - Sale date and time
  - Multiple products with quantities and sale prices
  - Payment methods and amounts
  - Customer information (if applicable)

#### 2.2 Profit Calculation Process
- **Per-Item Profit Calculation**:
  - Retrieves cost basis from `InventoryItem.averageCost`
  - Calculates unit profit = `salePrice - purchasePrice`
  - Calculates total item profit = `unitProfit × quantity`

#### 2.3 Inventory Reduction
- **Automatic Stock Update**:
  - Reduces `InventoryItem.quantity` by sold amounts
  - Ensures sufficient stock before allowing sale
  - Prevents overselling

#### 2.4 Financial Transaction Creation
- **Automatic SALE Transaction**:
  - Creates `SALE` transaction in financial system
  - Transaction Type: `INCOME`
  - `deductsFromProfit`: `true` (sales increase profit)
  - Amount: Total sale revenue
  - Profit Impact: Total sale profit (revenue - cost of goods sold)
  - Updates both profit and cash flow

### Data Models Involved

```java
// Sale aggregates multiple products
@Entity
public class Sale {
    private Long id;
    private LocalDateTime date;
    private List<SaleItem> items;
    private BigDecimal totalAmount; // Sum of all item prices
    private BigDecimal totalProfit; // Sum of all item profits
    private List<SalePayment> payments;
}

// Individual sale items with profit tracking
@Entity
public class SaleItem {
    private ProductVariant productVariant;
    private int quantity;
    private BigDecimal unitPrice; // Sale price per unit
    private BigDecimal unitProfit; // Profit per unit (sale - purchase)
    private BigDecimal totalItemPrice; // unitPrice × quantity
    private BigDecimal totalItemProfit; // unitProfit × quantity
}
```

### Business Rules

- **Profit Calculation**: Profit = Sale Price - Cost Basis (per unit)
- **Stock Validation**: Sale cannot exceed available inventory
- **Payment Validation**: Sum of payments must equal total sale amount
- **Atomic Operations**: Sale, inventory reduction, and transaction creation are atomic

## 3. Financial Reporting: Aggregation and Analytics

### Core Metrics

The system calculates three primary financial metrics:

#### 3.1 Total Sales
- **Definition**: Sum of all `SALE` transaction amounts
- **Calculation**: `Σ(Transaction.amount WHERE detail = 'SALE')`
- **Purpose**: Revenue tracking and business performance measurement

#### 3.2 Net Profit
- **Definition**: Business profitability after expenses
- **Calculation**:
  ```
  profit = Σ(profit impact of all transactions)
  ```
- **Components**:
  - **Positive Impact**: Sales profit (revenue - cost of goods sold)
  - **Negative Impact**: Rent, utilities, equipment purchases, other expenses
  - **Neutral Impact**: Product purchases, owner investments, profit withdrawals

#### 3.3 Cash Flow
- **Definition**: Net movement of money in/out of business
- **Calculation**:
  ```
  cashFlow = Σ(INCOME transactions) - Σ(EXPENSE transactions)
  ```
- **Purpose**: Liquidity and cash position tracking

### Reporting Features

#### 3.1 Real-time Financial Summary
- **Endpoint**: `GET /account/financial-summary`
- **Data Provided**:
  - Current profit
  - Current cash flow
  - Account balances (Bank + Cash)

#### 3.2 Monthly Cash Flow Analysis
- **Features**:
  - Month-by-month breakdown
  - Cumulative balance tracking
  - Transaction details per month
  - Account-specific balances (Bank vs Cash)

#### 3.3 Transaction History
- **Comprehensive Audit Trail**:
  - All financial transactions with details
  - Categorized by type and impact
  - Searchable and filterable

### Transaction Types and Profit Impact

| Transaction Type | Category | Profit Impact | Cash Flow Impact | Description |
|------------------|----------|---------------|------------------|-------------|
| `SALE` | INCOME | +profit | +amount | Product sales revenue (profit = revenue - COGS) |
| `PRODUCT_PURCHASE` | EXPENSE | unchanged | -amount | Inventory acquisition |
| `RENT` | EXPENSE | -amount | -amount | Monthly rent payments |
| `ELECTRICITY` | EXPENSE | -amount | -amount | Utility bills |
| `MACHINE_PURCHASE` | EXPENSE | -amount | -amount | Equipment purchases |
| `OWNER_INVESTMENT` | INCOME | unchanged | +amount | Capital injections |
| `PROFIT_WITHDRAWAL` | EXPENSE | unchanged | -amount | Owner profit distributions |

## 4. Integration: Automatic Financial Reflection

### Inventory-to-Financial Synchronization

#### 4.1 Purchase Integration
- **Trigger**: Purchase order completion
- **Automatic Actions**:
  1. Update inventory quantities and cost basis
  2. Create `PRODUCT_PURCHASE` transaction
  3. Update cash flow metrics
  4. Maintain audit trail

#### 4.2 Sales Integration
- **Trigger**: Sale completion
- **Automatic Actions**:
  1. Calculate per-item profits using current cost basis
  2. Reduce inventory quantities
  3. Create `SALE` transaction with profit amount
  4. Update profit and cash flow metrics
  5. Generate financial reports

#### 4.3 Real-time Financial Updates
- **Immediate Reflection**:
  - All inventory changes instantly update financial calculations
  - No manual reconciliation required
  - Real-time dashboard updates

### Data Consistency Mechanisms

#### 4.1 Transaction Atomicity
- **Database Transactions**: All related operations (inventory, sales, financial) occur atomically
- **Rollback Protection**: Failed operations don't leave inconsistent state

#### 4.2 Audit Trail
- **Complete History**: Every financial-impacting action is recorded
- **Reference Linking**: Sales and purchases link to financial transactions
- **Change Tracking**: Modification history maintained

#### 4.3 Validation Rules
- **Stock Validation**: Prevents sales exceeding inventory
- **Payment Validation**: Ensures payment sums match sale totals
- **Cost Basis Validation**: Ensures purchase prices are properly set

## End-to-End Workflow Example

### Scenario: Complete Product Lifecycle

1. **Purchase Phase**:
   - Purchase 100 helmets at $50 each ($5000 total)
   - Inventory updated: quantity = 100, averageCost = $50
   - Transaction: PRODUCT_PURCHASE $5000 (expense, no profit impact)
   - Cash flow: -$5000

   *Note: If a second purchase of 50 helmets at $55 each occurs:*
   *New average cost = ((100 × $50) + (50 × $55)) ÷ (100 + 50) = ($5000 + $2750) ÷ 150 = $7750 ÷ 150 = $51.67*

2. **Sales Phase**:
   - Sell 10 helmets at $80 each ($800 total)
   - Profit calculation: ($80 - $50) × 10 = $300
   - Inventory updated: quantity = 90
   - Transaction: SALE $800 (income, +$300 profit impact)
   - Cash flow: +$800

3. **Financial Impact**:
   - **Profit**: +$300 (from sale) - $0 (from purchase) = +$300
   - **Cash Flow**: +$800 (from sale) - $5000 (from purchase) = -$4200
   - **Inventory Value**: 90 units × $50 = $4500 (remaining cost basis)

4. **Reporting**:
   - Total Sales: $800
   - Net Profit: $300
   - Cash Flow: -$4200
   - Inventory: 90 units available

## System Architecture Benefits

### 1. Automated Financial Tracking
- No manual bookkeeping required
- Real-time financial visibility
- Accurate profit calculations

### 2. Inventory-Finance Integration
- Cost basis automatically maintained using weighted average costing
- Profit calculations use current average cost basis
- Stock levels drive financial projections

### 3. Comprehensive Reporting
- Multiple financial views (profit, cash flow, sales)
- Historical analysis capabilities
- Account-specific tracking

### 4. Business Intelligence
- Product profitability analysis
- Cash flow forecasting
- Inventory turnover insights

## Future Enhancements

- **Advanced Costing**: FIFO/LIFO inventory costing methods
- **Multi-currency**: Support for different currencies
- **Tax Integration**: Automatic tax calculations
- **Budgeting**: Expense budgeting and variance analysis
- **Forecasting**: Sales and cash flow projections</content>
<parameter name="filePath">c:\Users\Jaoow\WebProjects\helmetstore-backend\FINANCIAL_ECOSYSTEM_SPECIFICATION.md