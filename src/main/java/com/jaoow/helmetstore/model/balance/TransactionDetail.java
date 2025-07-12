package com.jaoow.helmetstore.model.balance;

public enum TransactionDetail {
    // Income transactions
    SALE(true),                    // Adds to profit
    OWNER_INVESTMENT(false),       // Adds cash but doesn't affect profit
    
    // Expense transactions
    PRODUCT_PURCHASE(false),       // Stock purchase - doesn't affect profit directly
    RENT(true),                    // Deducts from profit
    ELECTRICITY(true),             // Deducts from profit
    MACHINE_PURCHASE(true),        // Deducts from profit
    PROFIT_WITHDRAWAL(false),      // Reduces cash but doesn't deduct from profit
    MONEY_INVESTMENT(false),       // Investment - doesn't affect profit
    OTHER(true);                   // Default for other expenses
    
    private final boolean deductsFromProfit;
    
    TransactionDetail(boolean deductsFromProfit) {
        this.deductsFromProfit = deductsFromProfit;
    }
    
    public boolean deductsFromProfit() {
        return deductsFromProfit;
    }
}