package com.jaoow.helmetstore.cache;

public class CacheNames {
    public static final String PRODUCT_INDICATORS = "productIndicators";
    public static final String MOST_SOLD_PRODUCTS = "mostSoldProducts";
    public static final String PRODUCT_STOCK = "productStock";
    public static final String REVENUE_AND_PROFIT = "revenueAndProfit";

    public static final String SALES_HISTORY = "salesHistory";
    public static final String PURCHASE_ORDER_HISTORY = "purchaseOrderHistory";

    public static final String PRODUCT = "product";
    
    // Cash Flow Cache Names
    public static final String CASH_FLOW_SUMMARY = "cashFlowSummary";
    public static final String MONTHLY_CASH_FLOW = "monthlyCashFlow";
    public static final String FINANCIAL_SUMMARY = "financialSummary";
    public static final String PROFIT_CALCULATION = "profitCalculation";
    public static final String CASH_FLOW_CALCULATION = "cashFlowCalculation";
    
    // Profit Tracking Cache Names
    public static final String PROFIT_SUMMARY = "profitSummary";
    public static final String MONTHLY_PROFIT = "monthlyProfit";

    public static final String[] ALL_CACHE_NAMES = {
            PRODUCT_INDICATORS,
            MOST_SOLD_PRODUCTS,
            PRODUCT_STOCK,
            REVENUE_AND_PROFIT,
            SALES_HISTORY,
            PURCHASE_ORDER_HISTORY,
            PRODUCT,
            CASH_FLOW_SUMMARY,
            MONTHLY_CASH_FLOW,
            FINANCIAL_SUMMARY,
            PROFIT_CALCULATION,
            CASH_FLOW_CALCULATION,
            PROFIT_SUMMARY,
            MONTHLY_PROFIT
    };
}
