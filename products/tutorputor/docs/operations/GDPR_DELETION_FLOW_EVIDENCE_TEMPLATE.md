# GDPR Deletion Flow Evidence Template

Date:
Environment:
Tenant ID:
User ID:
Correlation ID:

## Request Evidence

- Deletion request payload:
- Deletion request response:
- Verification token response:

## SQL Evidence

- DataDeletionRequest query output:
- DeletionVerification query output:
- User-related cascade query output:

## Assertions

- [ ] retentionDays is set to expected policy value
- [ ] scheduledDeletionAt is populated
- [ ] verification token has expiry
- [ ] user-linked data is removed or anonymized according to policy

## Reviewer Notes

