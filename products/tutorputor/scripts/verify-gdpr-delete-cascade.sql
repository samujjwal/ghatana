-- Verify GDPR deletion request metadata and token state.
-- Replace :tenant_id and :user_id with concrete values before execution.

SELECT id, tenant_id, user_id, status, scheduled_deletion_at, retention_days, created_at
FROM data_deletion_requests
WHERE tenant_id = :tenant_id
  AND user_id = :user_id
ORDER BY created_at DESC
LIMIT 10;

SELECT id, user_id, token, expires_at, created_at
FROM deletion_verifications
WHERE user_id = :user_id
ORDER BY created_at DESC
LIMIT 10;

-- Spot check dependent user rows that should be deleted or anonymized.
SELECT
  (SELECT COUNT(*) FROM learner_profiles WHERE user_id = :user_id) AS learner_profiles,
  (SELECT COUNT(*) FROM learner_masteries WHERE profile_id IN (SELECT id FROM learner_profiles WHERE user_id = :user_id)) AS learner_masteries,
  (SELECT COUNT(*) FROM preference_changes WHERE user_id = :user_id) AS preference_changes,
  (SELECT COUNT(*) FROM sso_user_links WHERE user_id = :user_id) AS sso_links;
