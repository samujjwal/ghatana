-- P0-2: Add optional campaign detail fields to support full campaign creation contract
-- These fields allow the UI to send objective, budget, dates, audience, and landing page URL
-- when creating campaigns, eliminating the contract drift between UI and backend.

ALTER TABLE dmos_campaigns
    ADD COLUMN IF NOT EXISTS objective VARCHAR(100),
    ADD COLUMN IF NOT EXISTS budget_cents BIGINT,
    ADD COLUMN IF NOT EXISTS start_date VARCHAR(10),
    ADD COLUMN IF NOT EXISTS end_date VARCHAR(10),
    ADD COLUMN IF NOT EXISTS audience TEXT,
    ADD COLUMN IF NOT EXISTS landing_page_url TEXT;

-- Add comments for documentation
COMMENT ON COLUMN dmos_campaigns.objective IS 'Campaign objective (e.g., AWARENESS, LEADS, CONVERSIONS)';
COMMENT ON COLUMN dmos_campaigns.budget_cents IS 'Campaign budget in minor currency units (cents)';
COMMENT ON COLUMN dmos_campaigns.start_date IS 'Campaign start date in ISO 8601 format (YYYY-MM-DD)';
COMMENT ON COLUMN dmos_campaigns.end_date IS 'Campaign end date in ISO 8601 format (YYYY-MM-DD)';
COMMENT ON COLUMN dmos_campaigns.audience IS 'Target audience segment description or identifier';
COMMENT ON COLUMN dmos_campaigns.landing_page_url IS 'Campaign landing page URL';
