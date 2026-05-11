-- DMOS P0: explicit governed campaign launch states.

ALTER TABLE dmos_campaigns
    DROP CONSTRAINT IF EXISTS dmos_campaigns_status_check;

ALTER TABLE dmos_campaigns
    ADD CONSTRAINT dmos_campaigns_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING_APPROVAL',
        'APPROVED',
        'PENDING_LAUNCH',
        'LAUNCH_RUNNING',
        'LAUNCH_FAILED',
        'EXTERNAL_EXECUTION_BLOCKED',
        'LAUNCHED',
        'PAUSED',
        'COMPLETED',
        'ARCHIVED',
        'ROLLED_BACK'
    ));

COMMENT ON CONSTRAINT dmos_campaigns_status_check ON dmos_campaigns IS
    'P0: Enforces explicit governed campaign lifecycle statuses, including pre-launch and rollback states';
