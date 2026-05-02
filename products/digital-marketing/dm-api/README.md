# dm-api

**Package:** `com.ghatana.digitalmarketing.api`

DMOS API module. Exposes DMOS application services via ActiveJ HTTP servlets.

## Endpoints

### Campaign API (`DmosCampaignServlet`)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/v1/workspaces/:workspaceId/campaigns` | Create campaign |
| `GET` | `/v1/workspaces/:workspaceId/campaigns/:id` | Get campaign |
| `POST` | `/v1/workspaces/:workspaceId/campaigns/:id/launch` | Launch campaign |
| `POST` | `/v1/workspaces/:workspaceId/campaigns/:id/pause` | Pause campaign |

## Required Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Tenant-ID` | Always | Tenant identifier |
| `X-Idempotency-Key` | Write operations | Idempotency key |
| `X-Principal-ID` | Optional | Defaults to `anonymous` |
| `X-Correlation-ID` | Optional | Generated if absent |

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success (update/read) |
| 201 | Created |
| 400 | Bad request / missing required header |
| 403 | Not authorized |
| 404 | Not found |
| 409 | State conflict (invalid transition) |
| 422 | Compliance violation |
| 500 | Internal error |

## Dependencies

- `dm-core-contracts` — `DmOperationContext`, typed IDs
- `dm-application` — `CampaignService`, `CampaignServiceImpl`
- `platform:java:http` — `JsonServlet`, `RoutingServlet`
