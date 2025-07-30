package com.jaoow.helmetstore.model.balance;

public enum TransactionDetail {
    // Income transactions
    SALE(false), // Adds to profit
    OWNER_INVESTMENT(false), // Adds cash but doesn't affect profit
    MONEY_INVESTMENT(false), // Investment - doesn't affect profit

    // Expense transactions
    PRODUCT_PURCHASE(false), // Stock purchase - doesn't affect profit directly
    RENT(true), // Deducts from profit
    ELECTRICITY(true), // Deducts from profit
    MACHINE_PURCHASE(true), // Deducts from profit
    PROFIT_WITHDRAWAL(true), // Deducts from profit - owner taking money out
    OTHER(true); // Default for other expenses

    private final boolean affectsWithdrawableProfit;

    TransactionDetail(boolean affectsWithdrawableProfit) {
        this.affectsWithdrawableProfit = affectsWithdrawableProfit;
    }

    public boolean affectsWithdrawableProfit() {
        return affectsWithdrawableProfit;
    }
}