# Financial Transaction System

This document describes the enhanced financial transaction system for the HelmetStore application, designed to track business transactions with proper profit calculation and cash flow management.

## Overview

The financial transaction system allows small businesses to:

- Track different types of transactions (income, expenses, investments, withdrawals)
- Calculate profit based on business rules
- Track cash flow separately from profit
- Maintain accurate financial records

## Core Components

### 1. TransactionType Enum

Defines the basic transaction categories:

- `INCOME` - Money coming into the business
- `EXPENSE` - Money going out of the business

### 2. TransactionDetail Enum

Defines specific transaction types with profit impact flags:

| Transaction Type    | deductsFromProfit | Description                                                           |
| ------------------- | ----------------- | --------------------------------------------------------------------- |
| `SALE`              | `true`            | Revenue from sales - adds to profit                                   |
| `OWNER_INVESTMENT`  | `false`           | Owner investment - adds cash but doesn't affect profit                |
| `PRODUCT_PURCHASE`  | `false`           | Stock/inventory purchase - doesn't affect profit directly             |
| `RENT`              | `true`            | Rent payments - deducts from profit                                   |
| `ELECTRICITY`       | `true`            | Utility bills - deducts from profit                                   |
| `MACHINE_PURCHASE`  | `true`            | Equipment purchases - deducts from profit                             |
| `PROFIT_WITHDRAWAL` | `false`           | Owner profit withdrawal - reduces cash but doesn't deduct from profit |
| `MONEY_INVESTMENT`  | `false`           | Investment activities - doesn't affect profit                         |
| `OTHER`             | `true`            | Default for other expenses - deducts from profit                      |

### 3. Transaction Entity

The main transaction model with fields:

- `id` - Unique identifier
- `date` - Transaction date and time
- `type` - TransactionType (INCOME/EXPENSE)
- `detail` - TransactionDetail (specific transaction type)
- `description` - Human-readable description
- `amount` - Transaction amount
- `paymentMethod` - How the transaction was processed
- `reference` - External reference (optional)
- `account` - Associated account

## Business Rules

### Profit Calculation

```
profit = sum(SALE) - sum(all transactions where deductsFromProfit = true)
```

**Examples:**

- Sale of $1000 → Profit: +$1000
- Rent payment of $500 → Profit: -$500
- Product purchase of $300 → Profit: unchanged (doesn't affect profit)
- Owner investment of $2000 → Profit: unchanged (doesn't affect profit)

### Cash Flow Calculation

```
cashFlow = sum(INCOME) - sum(EXPENSE)
```

Cash flow tracks all money movements regardless of profit impact.

## API Endpoints

### Financial Calculations

- `GET /account/financial-summary` - Get both profit and cash flow
- `GET /account/profit` - Get profit calculation only
- `GET /account/cash-flow` - Get cash flow calculation only

### Transaction Management

- `GET /account` - Get account information with transactions
- `POST /account/transaction` - Create a new transaction
- `PUT /account/transaction/{id}` - Update an existing transaction
- `DELETE /account/transaction/{id}` - Delete a transaction

## Example Usage

### Creating a Sale Transaction

```java
TransactionCreateDTO saleTransaction = TransactionCreateDTO.builder()
    .date(LocalDateTime.now())
    .type(TransactionType.INCOME)
    .detail(TransactionDetail.SALE)
    .description("Sale of helmet #123")
    .amount(new BigDecimal("150.00"))
    .paymentMethod(PaymentMethod.PIX)
    .reference("SALE#123")
    .build();

transactionService.createManualTransaction(saleTransaction, principal);
```

### Creating a Rent Payment

```java
TransactionCreateDTO rentTransaction = TransactionCreateDTO.builder()
    .date(LocalDateTime.now())
    .type(TransactionType.EXPENSE)
    .detail(TransactionDetail.RENT)
    .description("Monthly rent payment")
    .amount(new BigDecimal("800.00"))
    .paymentMethod(PaymentMethod.PIX)
    .reference("RENT#20241201")
    .build();

transactionService.createManualTransaction(rentTransaction, principal);
```

### Getting Financial Summary

```java
TransactionService.FinancialSummary summary =
    transactionService.calculateFinancialSummary(principal);

BigDecimal profit = summary.getProfit();
BigDecimal cashFlow = summary.getCashFlow();
```

## Database Schema

The system uses the following database tables:

- `transaction` - Stores all transaction records
- `account` - Stores account information
- `user` - Stores user information

## Key Features

1. **Automatic Profit Calculation** - System automatically calculates profit based on business rules
2. **Cash Flow Tracking** - Separate tracking of all money movements
3. **Flexible Transaction Types** - Easy to add new transaction types
4. **Business Rule Enforcement** - Built-in logic for profit impact
5. **Audit Trail** - Complete transaction history with references
6. **Multi-account Support** - Support for different payment methods and accounts

## Integration with Existing System

The financial transaction system integrates with:

- **Sale System** - Automatically creates SALE transactions
- **Purchase Order System** - Automatically creates PRODUCT_PURCHASE transactions
- **Account System** - Updates account balances
- **User System** - User-specific transaction tracking

## Example Scenarios

### Scenario 1: Monthly Business Operations

1. Owner invests $5000 → Cash: +$5000, Profit: unchanged
2. Purchase inventory for $2000 → Cash: -$2000, Profit: unchanged
3. Pay rent $800 → Cash: -$800, Profit: -$800
4. Pay electricity $200 → Cash: -$200, Profit: -$200
5. Make sales $3000 → Cash: +$3000, Profit: +$3000
6. Withdraw profit $1000 → Cash: -$1000, Profit: unchanged

**Final Result:**

- Cash Flow: $5000 - $2000 - $800 - $200 + $3000 - $1000 = $4000
- Profit: $3000 - $800 - $200 = $2000

### Scenario 2: Equipment Purchase

1. Purchase machine for $1500 → Cash: -$1500, Profit: -$1500
2. Make sales $2000 → Cash: +$2000, Profit: +$2000

**Final Result:**

- Cash Flow: -$1500 + $2000 = $500
- Profit: $2000 - $1500 = $500

## Best Practices

1. **Always use appropriate TransactionDetail** - Don't use OTHER unless necessary
2. **Provide meaningful descriptions** - Helps with reporting and auditing
3. **Use consistent references** - Helps track related transactions
4. **Regular profit monitoring** - Check profit calculations regularly
5. **Separate business and personal** - Don't mix personal expenses with business transactions

## Future Enhancements

Potential improvements for the system:

- Date range filtering for calculations
- Export functionality for reports
- Budget tracking and alerts
- Integration with accounting software
- Multi-currency support
- Tax calculation features
