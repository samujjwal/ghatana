# Dashboard Actions Endpoint Decision

## Canonical Endpoint

**Decision**: `/api/v1/dashboard/actions` is the canonical endpoint for dashboard actions.

## Rationale

The endpoint `/api/v1/dashboard/actions` is already consistent across all layers:
- **Route Manifest**: Defines POST and GET methods on `/api/v1/dashboard/actions`
- **OpenAPI Spec**: Defines POST and GET methods on `/api/v1/dashboard/actions`
- **Backend**: YappcHttpServer routes to this path
- **Frontend**: Uses this path for dashboard action requests

## Methods

- **GET**: Retrieve dashboard actions for a workspace
  - Query parameters: `workspaceId` (required), `correlationId` (optional)
  - Returns: Dashboard actions classification with primary action, blocked actions, review required actions, safe to continue actions

- **POST**: Request dashboard actions with full request body
  - Request body: DashboardActionsRequest with workspaceId, correlationId
  - Returns: Same dashboard actions classification as GET

## Authorization

- **Auth Mode**: required
- **Scopes**: `workspace:read`
- **Owner**: yappc-services
- **Boundary**: YAPPC
- **Privacy Classification**: CONFIDENTIAL

## Audit Events

- GET: `DASHBOARD_ACTIONS_GET`
- POST: `DASHBOARD_ACTIONS_REQUEST`

## Future Considerations

The dashboard actions endpoint is designed to be backend-derived, with actions generated based on:
- Current phase state
- Project blockers
- Governance requirements
- User role and tier capabilities

All dashboard actions should route through this canonical endpoint to ensure consistent audit trails and authorization enforcement.

## Date

2026-03-27
