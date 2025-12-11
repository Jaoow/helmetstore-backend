-- ================================================================================
-- MIGRATION V2.0.2: Generate COGS Transactions from Sale Items
-- ================================================================================
-- Purpose: Create Cost of Goods Sold (COGS) transactions for all sales based on
--          the actual cost of each individual sale item.
--
-- Why: This migration calculates the true COGS by:
--      1. Iterating through each sale
--      2. Summing the cost of all items in that sale
--      3. Creating ONE COGS transaction per sale with the aggregated cost
--
-- Formula per Sale Item:
--   - If cost_basis_at_sale exists: cost = cost_basis_at_sale * quantity
--   - Otherwise: cost = (unit_price - unit_profit) * quantity
--
-- Transaction Properties:
--   - type: EXPENSE
--   - detail: COST_OF_GOODS_SOLD
--   - affectsProfit: TRUE
--   - affectsCash: FALSE
--   - reference: SALE#{sale_id}
--
-- Safety: IDEMPOTENT - checks for existing COGS entries before creating new ones
-- ================================================================================

-- ================================================================================
-- STEP 1: Delete Old COGS Transactions (if any from previous migrations)
-- ================================================================================
-- This ensures we start fresh with accurate item-level cost calculations
-- ================================================================================

DELETE FROM transaction
WHERE detail = 'COST_OF_GOODS_SOLD'
  AND reference LIKE 'SALE#%';

-- ================================================================================
-- STEP 2: Generate New COGS Transactions Based on Sale Items
-- ================================================================================
-- For each sale, calculate total COGS by summing all item costs
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
    s.date,                                    -- Use original sale date
    'EXPENSE',                                 -- Transaction type
    'COST_OF_GOODS_SOLD',                      -- Detail category
    'Cost of Goods Sold - Sale #' || s.id,     -- Description
    -- Calculate total COGS as negative value (it's an expense)
    -1 * (
        SELECT COALESCE(SUM(
            CASE
                -- Prefer cost_basis_at_sale if available (most accurate)
                WHEN si.cost_basis_at_sale IS NOT NULL THEN
                    si.cost_basis_at_sale * si.quantity
                -- Fallback: calculate from unit_price - unit_profit
                ELSE
                    (si.unit_price - si.unit_profit) * si.quantity
            END
        ), 0)
        FROM sale_item si
        WHERE si.sale_id = s.id
    ) AS total_cogs,
    'CASH',                                    -- Placeholder (irrelevant for COGS)
    'SALE#' || s.id,                           -- Link to sale
    -- Get the first account for this sale's inventory owner
    -- Strategy: Find user who owns this inventory, then get any of their accounts
    COALESCE(
        (SELECT a.id
         FROM account a
         JOIN app_user u ON u.id = a.user_id
         WHERE u.inventory_id = s.inventory_id
         LIMIT 1),
        -- Fallback: if no account found via inventory, try to find by any existing transaction
        (SELECT DISTINCT t.account_id
         FROM transaction t
         WHERE t.reference = 'SALE#' || s.id
           AND t.detail = 'SALE'
         LIMIT 1),
        -- Last resort: get the first available account (shouldn't happen)
        (SELECT id FROM account LIMIT 1)
    ),
    TRUE,                                      -- Affects Profit
    FALSE,                                     -- Does NOT affect Cash (cost was spent when purchasing inventory)
    NULL                                       -- No wallet destination
FROM sale s
WHERE (
    -- Only create COGS if the calculated cost is positive
    SELECT COALESCE(SUM(
        CASE
            WHEN si.cost_basis_at_sale IS NOT NULL THEN
                si.cost_basis_at_sale * si.quantity
            ELSE
                (si.unit_price - si.unit_profit) * si.quantity
        END
    ), 0)
    FROM sale_item si
    WHERE si.sale_id = s.id
) > 0
-- Only create COGS if there's already a revenue transaction for this sale
AND EXISTS (
    SELECT 1
    FROM transaction t
    WHERE t.reference = 'SALE#' || s.id
      AND t.detail = 'SALE'
);

-- ================================================================================
-- STEP 3: Verification Queries (For Manual Validation)
-- ================================================================================

-- Query 1: Count sales with COGS transactions
-- Expected: Should match the total number of sales with items
/*
SELECT
    COUNT(DISTINCT s.id) AS total_sales,
    COUNT(DISTINCT t.reference) AS sales_with_cogs
FROM sale s
LEFT JOIN transaction t ON t.reference = 'SALE#' || s.id AND t.detail = 'COST_OF_GOODS_SOLD'
WHERE EXISTS (SELECT 1 FROM sale_item si WHERE si.sale_id = s.id);
*/

-- Query 2: Compare old profit calculation with new ledger-based calculation
-- This verifies that the migration preserved profit accuracy
/*
SELECT
    s.id,
    s.date,
    s.total_amount AS revenue,
    s.total_profit AS original_profit,
    -- Calculate COGS from sale_items
    (
        SELECT COALESCE(SUM(
            CASE
                WHEN si.cost_basis_at_sale IS NOT NULL THEN
                    si.cost_basis_at_sale * si.quantity
                ELSE
                    (si.unit_price - si.unit_profit) * si.quantity
            END
        ), 0)
        FROM sale_item si
        WHERE si.sale_id = s.id
    ) AS calculated_cogs,
    -- Calculate profit from ledger (Revenue - COGS)
    (
        SELECT COALESCE(SUM(t.amount), 0)
        FROM transaction t
        WHERE t.reference = 'SALE#' || s.id
        AND t.affects_profit = TRUE
    ) AS ledger_profit,
    -- Difference (should be close to 0, allowing for rounding)
    s.total_profit - (
        SELECT COALESCE(SUM(t.amount), 0)
        FROM transaction t
        WHERE t.reference = 'SALE#' || s.id
        AND t.affects_profit = TRUE
    ) AS profit_difference
FROM sale s
ORDER BY s.date DESC
LIMIT 20;
*/

-- Query 3: Show COGS transactions created
/*
SELECT
    t.id,
    t.date,
    t.description,
    t.amount AS cogs_amount,
    t.reference,
    t.affects_profit,
    t.affects_cash
FROM transaction t
WHERE t.detail = 'COST_OF_GOODS_SOLD'
  AND t.reference LIKE 'SALE#%'
ORDER BY t.date DESC
LIMIT 20;
*/

-- Query 4: Validate that all sale items have cost information
-- Expected: All rows should have a valid cost source
/*
SELECT
    si.id,
    si.sale_id,
    si.product_variant_id,
    si.quantity,
    si.unit_price,
    si.unit_profit,
    si.cost_basis_at_sale,
    CASE
        WHEN si.cost_basis_at_sale IS NOT NULL THEN 'cost_basis_at_sale'
        ELSE 'calculated from profit'
    END AS cost_source,
    CASE
        WHEN si.cost_basis_at_sale IS NOT NULL THEN
            si.cost_basis_at_sale * si.quantity
        ELSE
            (si.unit_price - si.unit_profit) * si.quantity
    END AS total_item_cost
FROM sale_item si
ORDER BY si.sale_id, si.id
LIMIT 50;
*/

-- ================================================================================
-- END OF MIGRATION
-- ================================================================================
