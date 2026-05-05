# P1-036: Content Generation Backend-Only Surfaces Inventory

**Purpose**: This document inventories all content generation surfaces that are backend-only (no direct UI exposure). These capabilities are marketed but may lack frontend controls or feature gates.

## Backend-Only Content Generation Services

### 1. Ad Copy Generation
- **Backend Service**: `AdCopyGeneratorService` / `AdCopyGeneratorServiceImpl`
- **API Endpoint**: `POST /v1/workspaces/:workspaceId/content-items/:itemId/ad-copy/generate`
- **Frontend Status**: **NO DIRECT UI** - No dedicated page in `ui/src/pages/`
- **Feature Flag**: Not explicitly gated in frontend
- **Capability Key**: `dmos.ad_copy_generation` (proposed)
- **Marketing Claim**: Google Search ad copy draft generation
- **Access Pattern**: Backend API only, called from external systems or workflows

### 2. Landing Page Generation
- **Backend Service**: `LandingPageGeneratorService` / `LandingPageGeneratorServiceImpl`
- **API Endpoint**: (Check DmosLandingPageServlet for exact route)
- **Frontend Status**: **NO DIRECT UI** - No dedicated page in `ui/src/pages/`
- **Feature Flag**: Not explicitly gated in frontend
- **Capability Key**: `dmos.landing_page_generation` (proposed)
- **Marketing Claim**: Landing page content generation
- **Access Pattern**: Backend API only, called from external systems or workflows

### 3. Email Follow-Up Draft Generation
- **Backend Service**: `EmailFollowUpDraftService` / `EmailFollowUpDraftServiceImpl`
- **API Endpoint**: (Check for servlet)
- **Frontend Status**: **NO DIRECT UI** - No dedicated page in `ui/src/pages/`
- **Feature Flag**: Not explicitly gated in frontend
- **Capability Key**: `dmos.email_draft_generation` (proposed)
- **Marketing Claim**: Email follow-up draft generation
- **Access Pattern**: Backend API only, called from external systems or workflows

### 4. Statement of Work (SOW) Generation
- **Backend Service**: `SowServiceImpl`
- **API Endpoint**: (Check for servlet)
- **Frontend Status**: **NO DIRECT UI** - No dedicated page in `ui/src/pages/`
- **Feature Flag**: Not explicitly gated in frontend
- **Capability Key**: `dmos.sow_generation` (proposed)
- **Marketing Claim**: Statement of Work document generation
- **Access Pattern**: Backend API only, called from external systems or workflows

## Frontend-Exposed Content Generation

### Campaigns
- **Frontend Page**: `CampaignsPage.tsx`
- **Backend API**: `DmosCampaignServlet`
- **Status**: **HAS UI** - Full frontend exposure
- **Capability Key**: `dmos.campaigns` (implemented in P1-016)

### Strategy
- **Frontend Page**: `StrategyPage.tsx`
- **Backend API**: Strategy endpoints
- **Status**: **HAS UI** - Full frontend exposure
- **Capability Key**: `dmos.strategy` (implemented in P1-016)

### Budget
- **Frontend Page**: `BudgetPage.tsx`
- **Backend API**: Budget endpoints
- **Status**: **HAS UI** - Full frontend exposure
- **Capability Key**: `dmos.budget` (implemented in P1-016)

## Recommendations (P1-037)

1. **Add capability keys** to backend-only services for runtime gating
2. **Add backend capability endpoints** to `/v1/workspaces/:workspaceId/capabilities`
3. **Document access patterns** for each backend-only surface
4. **Consider adding admin UI** for content generation if marketed to end users
5. **Add feature flags** to disable backend-only surfaces if not needed

## Risk Assessment

| Surface | Marketing Exposure | UI Exposure | Risk Level |
|---------|-------------------|-------------|------------|
| Ad Copy Generation | High | None | **HIGH** - Marketed without UI |
| Landing Page Generation | High | None | **HIGH** - Marketed without UI |
| Email Draft Generation | Medium | None | **MEDIUM** - Marketed without UI |
| SOW Generation | Medium | None | **MEDIUM** - Marketed without UI |
| Campaigns | High | Yes | **LOW** - Has UI |
| Strategy | High | Yes | **LOW** - Has UI |
| Budget | High | Yes | **LOW** - Has UI |

## Next Steps (P1-037)

- Implement UI or feature gates for backend-only marketed capabilities
- Add capability-driven routing for content generation endpoints
- Ensure proper authorization checks on backend-only surfaces
