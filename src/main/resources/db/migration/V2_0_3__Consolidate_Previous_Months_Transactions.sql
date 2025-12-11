-- ================================================================================
-- MIGRATION V2.0.3: Consolidate Previous Months Transactions
-- ================================================================================
-- Purpose: Clean up all transactions from previous months (before December 2025)
--          and consolidate them into a single opening balance entry per account.
--
-- Why: Keep the database lean by archiving historical transactions while
--      preserving accurate account balances for reporting.
--
-- Strategy:
--   1. Calculate the cumulative balance for each account up to Nov 30, 2025
--   2. Delete all transactions before December 1, 2025
--   3. Create a single "Opening Balance" transaction per account with the calculated balance
--
-- Safety: This migration is PERMANENT - deleted transactions cannot be recovered.
--         Ensure you have a backup before running this migration.
-- ================================================================================

-- ================================================================================
-- STEP 1: Create Opening Balance Transactions (Consolidated Balances)
-- ================================================================================
-- For each account, calculate total balance from all transactions before Dec 2025
-- and create a single transaction representing that opening balance.
-- ================================================================================

INSERT INTO transaction (
    date,
    type,
    detail,
    description,
    amount,
    payment_method,
    reference,
    account_id,
    affects_profit,
    affects_cash,
    wallet_destination
)
SELECT
    '2025-12-01 00:00:00'::timestamp AS date,                    -- First day of December
    -- Always use INCOME for opening balance (the amount already has the correct sign)
    'INCOME' AS type,
    NULL AS detail,                                               -- No specific detail
    'Opening Balance - Consolidated from previous months' AS description,
    total_balance AS amount,
    'CASH' AS payment_method,                                     -- Placeholder
    'OPENING_BALANCE_2025_12' AS reference,                       -- Unique reference
    account_id,
    FALSE AS affects_profit,                                      -- Opening balance doesn't affect profit
    TRUE AS affects_cash,                                         -- It does represent cash position
    wallet_destination                                            -- Preserve wallet destination
FROM (
    -- Calculate cumulative balance per account from all transactions before December
    -- Sum all amounts considering they already have correct signs (+ for income, - for expenses)
    SELECT
        t.account_id,
        COALESCE(SUM(
            CASE
                -- Only sum transactions that affect cash to get accurate cash balance
                WHEN t.affects_cash = TRUE THEN t.amount
                ELSE 0
            END
        ), 0) AS total_balance,
        -- Get wallet destination from the account's most recent cash transaction
        (SELECT wallet_destination
         FROM transaction
         WHERE account_id = t.account_id
           AND date < '2025-12-01'
           AND wallet_destination IS NOT NULL
           AND affects_cash = TRUE
         ORDER BY date DESC
         LIMIT 1) AS wallet_destination
    FROM transaction t
    WHERE t.date < '2025-12-01 00:00:00'
    GROUP BY t.account_id
) AS account_balances
WHERE total_balance != 0;  -- Only create opening balance if there's a non-zero balance

-- ================================================================================
-- STEP 2: Delete All Transactions Before December 2025
-- ================================================================================
-- Remove all historical transactions now that we have the consolidated balance
-- ================================================================================

DELETE FROM transaction
WHERE date < '2025-12-01 00:00:00';

-- ================================================================================
-- STEP 3: Verification Queries (For Manual Validation)
-- ================================================================================

-- Query 1: Count transactions and verify only December data remains
/*
SELECT
    DATE_TRUNC('month', date) AS month,
    COUNT(*) AS transaction_count,
    SUM(amount) AS total_amount
FROM transaction
GROUP BY DATE_TRUNC('month', date)
ORDER BY month;
*/

-- Query 2: Verify opening balances were created correctly
/*
SELECT
    t.account_id,
    a.type AS account_type,
    t.date,
    t.description,
    t.amount AS opening_balance,
    t.reference
FROM transaction t
JOIN account a ON a.id = t.account_id
WHERE t.reference = 'OPENING_BALANCE_2025_12'
ORDER BY t.account_id;
*/

-- Query 3: Calculate current balance per account (should match expected values)
/*
SELECT
    a.id AS account_id,
    a.type AS account_type,
    COALESCE(SUM(t.amount), 0) AS current_balance
FROM account a
LEFT JOIN transaction t ON t.account_id = a.id
GROUP BY a.id, a.type
ORDER BY a.id;
*/

-- Query 4: Verify no transactions exist before December 2025
/*
SELECT COUNT(*) AS transactions_before_december
FROM transaction
WHERE date < '2025-12-01 00:00:00';
-- Expected: 0
*/

-- ================================================================================
-- END OF MIGRATION
-- ================================================================================
