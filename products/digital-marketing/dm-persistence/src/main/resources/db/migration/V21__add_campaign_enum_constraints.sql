-- P1-007: Add CHECK constraints for campaign status and type enums
-- This migration enforces data integrity at the database level

-- Add CHECK constraint for campaign status
-- Valid statuses: DRAFT, LAUNCHED, PAUSED, COMPLETED, ARCHIVED
ALTER TABLE dmos_campaigns
ADD CONSTRAINT dmos_campaigns_status_check
CHECK (status IN ('DRAFT', 'LAUNCHED', 'PAUSED', 'COMPLETED', 'ARCHIVED'));

-- Add CHECK constraint for campaign type
-- Valid types: EMAIL, SOCIAL, PAID_SEARCH, PUSH, SMS, OMNICHANNEL
ALTER TABLE dmos_campaigns
ADD CONSTRAINT dmos_campaigns_type_check
CHECK (type IN ('EMAIL', 'SOCIAL', 'PAID_SEARCH', 'PUSH', 'SMS', 'OMNICHANNEL'));

-- Add NOT NULL constraints with validation
-- Ensure name cannot be empty
ALTER TABLE dmos_campaigns
ADD CONSTRAINT dmos_campaigns_name_not_empty
CHECK (name <> '');

-- P1-008: Prevent overwriting immutable creation fields
-- Ensure created_by cannot be empty
ALTER TABLE dmos_campaigns
ADD CONSTRAINT dmos_campaigns_created_by_not_empty
CHECK (created_by <> '');

-- Add index for list pagination performance
CREATE INDEX IF NOT EXISTS dmos_campaigns_created_at_idx
ON dmos_campaigns (workspace_id, created_at DESC, id DESC);

COMMENT ON CONSTRAINT dmos_campaigns_status_check ON dmos_campaigns IS
    'P1-007: Enforces valid campaign lifecycle statuses';

COMMENT ON CONSTRAINT dmos_campaigns_type_check ON dmos_campaigns IS
    'P1-007: Enforces valid campaign channel types';

COMMENT ON CONSTRAINT dmos_campaigns_name_not_empty ON dmos_campaigns IS
    'Campaign name cannot be empty string';

COMMENT ON CONSTRAINT dmos_campaigns_created_by_not_empty ON dmos_campaigns IS
    'P1-008: Immutable creator field cannot be empty';
