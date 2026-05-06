-- Add strategy_id column to dmos_budget_recommendations
-- Required for BudgetRecommendation domain object which requires a non-null strategyId
ALTER TABLE dmos_budget_recommendations
    ADD COLUMN IF NOT EXISTS strategy_id TEXT;
