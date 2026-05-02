# dm-application

**Package:** `com.ghatana.digitalmarketing.application`

DMOS Application module. Implements use-case-level application services for the Digital Marketing Operating System.

## Services

### CampaignService / CampaignServiceImpl

- `createCampaign(ctx, command)` — Create a campaign in DRAFT status, enforce auth, persist, record audit.
- `launchCampaign(ctx, campaignId)` — Launch a campaign; enforce auth + compliance preflight (`DM_CAMPAIGN_PREFLIGHT`) + audit.
- `pauseCampaign(ctx, campaignId)` — Pause an active campaign; enforce auth + audit.
- `getCampaign(ctx, campaignId)` — Read a campaign; enforce auth.

## Design

All services:
1. Check authorization via `DigitalMarketingKernelAdapter.isAuthorized()`
2. Resolve the domain entity from the repository
3. Evaluate compliance where required
4. Apply domain state transition
5. Persist and record audit

## Dependencies

- `dm-core-contracts` — `DmOperationContext`, typed IDs
- `dm-domain` — `Campaign`, `CampaignStatus`
- `dm-domain-packs` — `DmComplianceRuleSetIds`
- `dm-kernel-bridge` — `DigitalMarketingKernelAdapter`
- `plugin-compliance` — `CompliancePlugin`
