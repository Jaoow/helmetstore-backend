# Enhanced Cash Flow System

This document describes the enhanced cash flow system for the HelmetStore application, providing detailed monthly segmentation, account balance tracking, and comprehensive transaction analysis with proper service separation and cumulative balance tracking.

## Overview

The enhanced cash flow system provides:

- **Monthly segmentation** of all financial data
- **Bank and Cash account balance** tracking
- **Detailed transaction information** for each month
- **Comprehensive financial reporting** with historical analysis
- **Real-time balance calculations** across all accounts
- **Proper service separation** with dedicated CashFlowService
- **ModelMapper integration** for clean DTO conversions
- **Cumulative balance tracking** month by month

## Architecture

### Service Separation

The system follows the Single Responsibility Principle with separate services:

#### TransactionService

- **Purpose**: Handle transaction CRUD operations
- **Responsibilities**:
  - Create, update, delete transactions
  - Record transactions from sales and purchases
  - Calculate profit and basic cash flow
  - Manage transaction lifecycle

#### CashFlowService

- **Purpose**: Handle cash flow analysis and reporting
- **Responsibilities**:
  - Monthly cash flow analysis
  - Account balance tracking
  - Comprehensive financial reporting
  - Historical data analysis
  - DTO conversions using ModelMapper
  - Cumulative balance calculations

## Core Features

### 1. Monthly Cash Flow Segmentation

- Automatically groups transactions by month
- Calculates monthly income, expenses, and cash flow
- Provides account balances at the end of each month
- Includes all transactions that occurred during the month

### 2. Account Balance Tracking

- **Bank Account Balance** - Tracks all PIX and CARD transactions
- **Cash Account Balance** - Tracks all CASH transactions
- **Total Balance** - Combined balance across all accounts
- **Real-time updates** as transactions are processed

### 3. Comprehensive Transaction Analysis

- All transactions for each month with full details
- Transaction types and categories
- Payment methods used
- References and descriptions
- Date and time information

### 4. ModelMapper Integration

- Clean DTO conversions from entities
- Automatic mapping of Transaction to TransactionInfo
- Consistent data transformation across the system
- Reduced boilerplate code

### 5. Cumulative Balance Tracking

- **Accurate month-end balances** - Each month shows the correct balance at the end of that month
- **Historical progression** - Balances build cumulatively month by month
- **Proper financial reporting** - Follows standard accounting practices

## Cumulative Balance Behavior

### How It Works

Each month shows the cumulative balance at the end of that month, not just the current balance. This ensures proper financial reporting:

**Example:**

- **January**: Income $150, Expenses $1000 → Monthly Cash Flow -$850 → End Balance -$850
- **February**: Income $5200, Expenses $1000 → Monthly Cash Flow $4200 → End Balance $3350 (-$850 + $4200)
- **March**: Income $180, Expenses $2000 → Monthly Cash Flow -$1820 → End Balance $1530 ($3350 - $1820)

### Benefits

- **Accurate financial reporting** - Each month shows the true financial position
- **Historical analysis** - See how balances evolved over time
- **Proper accounting** - Follows standard accounting principles
- **Business insights** - Understand cash flow patterns and trends

## API Endpoints

### 1. Comprehensive Cash Flow Summary

```
GET /account/cash-flow-summary
```

**Service**: `CashFlowService.getCashFlowSummary()`

**Response:**

```json
{
  "totalBankBalance": 3500.00,
  "totalCashBalance": 200.00,
  "totalBalance": 3700.00,
  "totalIncome": 5530.00,
  "totalExpense": 4000.00,
  "totalCashFlow": 1530.00,
  "monthlyBreakdown": [
    {
      "month": "2024-01",
      "bankAccountBalance": -850.00,
      "cashAccountBalance": 0.00,
      "totalBalance": -850.00,
      "monthlyIncome": 150.00,
      "monthlyExpense": 1000.00,
      "monthlyCashFlow": -850.00,
      "transactions": [...]
    },
    {
      "month": "2024-02",
      "bankAccountBalance": 3350.00,
      "cashAccountBalance": 0.00,
      "totalBalance": 3350.00,
      "monthlyIncome": 5200.00,
      "monthlyExpense": 1000.00,
      "monthlyCashFlow": 4200.00,
      "transactions": [...]
    }
  ]
}
```

### 2. Monthly Cash Flow Breakdown

```
GET /account/cash-flow/monthly
```

**Service**: `CashFlowService.getMonthlyCashFlowBreakdown()`

**Response:**

```json
[
  {
    "month": "2024-01",
    "bankAccountBalance": -850.0,
    "cashAccountBalance": 0.0,
    "totalBalance": -850.0,
    "monthlyIncome": 150.0,
    "monthlyExpense": 1000.0,
    "monthlyCashFlow": -850.0,
    "transactions": [
      {
        "id": 1,
        "date": "2024-01-15T10:00:00",
        "description": "Sale of helmet #123",
        "amount": 150.0,
        "paymentMethod": "PIX",
        "type": "INCOME",
        "detail": "SALE",
        "reference": "SALE#123"
      }
    ]
  }
]
```

### 3. Specific Month Cash Flow

```
GET /account/cash-flow/monthly/{year}/{month}
```

**Service**: `CashFlowService.getMonthlyCashFlow()`

**Example:** `GET /account/cash-flow/monthly/2024/1`

**Response:** Same as individual month in the monthly breakdown

## Data Models

### MonthlyCashFlowDTO

```java
@Data
@Builder
public class MonthlyCashFlowDTO {
    private YearMonth month;
    private BigDecimal bankAccountBalance;  // Cumulative at end of month
    private BigDecimal cashAccountBalance;  // Cumulative at end of month
    private BigDecimal totalBalance;        // Cumulative at end of month
    private BigDecimal monthlyIncome;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlyCashFlow;
    private List<TransactionInfo> transactions;
}
```

### CashFlowSummaryDTO

```java
@Data
@Builder
public class CashFlowSummaryDTO {
    private BigDecimal totalBankBalance;
    private BigDecimal totalCashBalance;
    private BigDecimal totalBalance;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal totalCashFlow;
    private List<MonthlyCashFlowDTO> monthlyBreakdown;  // With cumulative balances
}
```

## Service Implementation

### CashFlowService

```java
@Service
@RequiredArgsConstructor
public class CashFlowService {
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ModelMapper modelMapper;

    public CashFlowSummaryDTO getCashFlowSummary(Principal principal) {
        // Implementation with cumulative balance calculation
    }

    public List<MonthlyCashFlowDTO> getMonthlyCashFlowBreakdown(String userEmail) {
        // Implementation with proper cumulative balance tracking
    }

    private BigDecimal calculateCumulativeBalanceUpToMonth(String userEmail, YearMonth targetMonth) {
        // Calculate cumulative balance up to end of target month
    }

    private List<TransactionInfo> convertToTransactionInfo(List<Transaction> transactions) {
        return transactions.stream()
                .map(transaction -> modelMapper.map(transaction, TransactionInfo.class))
                .collect(Collectors.toList());
    }
}
```

## Business Benefits

### 1. Monthly Financial Tracking

- **Trend Analysis** - See cash flow patterns over time
- **Seasonal Patterns** - Identify recurring monthly expenses
- **Future Planning** - Plan for upcoming expenses and investments
- **Performance Monitoring** - Track business growth month by month

### 2. Account Balance Management

- **Liquidity Monitoring** - Track available cash vs bank balances
- **Cash Optimization** - Optimize cash management strategies
- **Risk Assessment** - Monitor financial health across accounts
- **Investment Planning** - Plan for equipment and inventory purchases

### 3. Detailed Transaction Analysis

- **Spending Patterns** - Identify where money is being spent
- **Revenue Sources** - Track different income streams
- **Expense Categories** - Categorize and analyze expenses
- **Payment Methods** - Understand payment preferences

### 4. Comprehensive Reporting

- **Total Financial Position** - Complete overview of business finances
- **Historical Analysis** - Track performance over time
- **Monthly Comparisons** - Compare performance across months
- **Audit Trail** - Complete transaction history for compliance

### 5. Better Code Organization

- **Single Responsibility Principle** - Each service has a clear purpose
- **Maintainability** - Easier to modify and extend
- **Testability** - Services can be tested independently
- **Separation of Concerns** - Clear boundaries between functionality

### 6. Cumulative Balance Tracking

- **Accurate month-end balances** - Each month shows the correct balance at the end of that month
- **Historical balance progression** - See how balances evolved over time
- **Proper financial reporting** - Follows standard accounting practices
- **Business insights** - Understand cash flow patterns and trends

## Example Usage Scenarios

### Scenario 1: Monthly Business Review

1. **Get comprehensive summary** - `GET /account/cash-flow-summary`
2. **Review monthly breakdown** - Analyze each month's performance with cumulative balances
3. **Check account balances** - Monitor bank vs cash positions at month-end
4. **Analyze transactions** - Review all transactions for the month

### Scenario 2: Financial Planning

1. **Identify trends** - Look at monthly cash flow patterns and balance progression
2. **Plan expenses** - Use historical data to plan future expenses
3. **Optimize cash flow** - Adjust payment methods and timing
4. **Investment decisions** - Use cumulative balance data for investment planning

### Scenario 3: Tax Preparation

1. **Monthly summaries** - Get organized financial data by month with accurate balances
2. **Transaction details** - Complete transaction history for tax filing
3. **Account balances** - Year-end balance information with proper progression
4. **Income/expense totals** - Annual totals for tax reporting

## Implementation Details

### Database Queries

The system uses optimized queries to:

- Group transactions by month
- Calculate cumulative balances
- Aggregate income and expenses
- Retrieve account balances

### Performance Optimizations

- **Indexed queries** for date ranges
- **Efficient aggregation** for monthly calculations
- **Lazy loading** for transaction details
- **Caching** for frequently accessed data
- **ModelMapper** for efficient DTO conversion
- **Cumulative balance calculation** for accurate reporting

### Security Features

- **User-specific data** - All data is filtered by user
- **Transaction isolation** - Secure transaction processing
- **Audit logging** - Complete transaction history
- **Access control** - Role-based access to financial data

## Integration with Existing System

The enhanced cash flow system integrates seamlessly with:

- **Transaction System** - Uses existing transaction processing
- **Account System** - Leverages current account management
- **User System** - Maintains user-specific data isolation
- **Profit Calculation** - Works alongside profit tracking
- **ModelMapper** - Consistent data transformation

## Future Enhancements

Potential improvements for the system:

- **Date range filtering** - Custom date ranges for analysis
- **Export functionality** - PDF/Excel reports
- **Budget tracking** - Monthly budget vs actual comparisons
- **Forecasting** - Predictive cash flow analysis
- **Multi-currency support** - International business support
- **Real-time notifications** - Balance and cash flow alerts
- **Advanced caching** - Redis integration for performance
- **Audit logging** - Enhanced transaction tracking
- **Running balance calculation** - More sophisticated balance tracking
- **Account-specific cumulative balances** - Separate tracking for bank vs cash
