# REST and GraphQL Domain Ownership

**@doc.type documentation**
**@doc.purpose Define and document REST-owned vs GraphQL-owned API surfaces**
**@doc.layer product**

## Overview

This document defines the clear separation between REST-owned and GraphQL-owned API surfaces in the YAPPC platform. Each domain is explicitly owned by either REST or GraphQL to avoid duplication and ensure a single source of truth.

## REST-Owned Domains

The following domains are REST-owned and should use REST endpoints:

### Core Platform APIs
- **Health & Readiness Probes**: `/health`, `/ready`, `/metrics`
- **Authentication**: Token generation, refresh, validation
- **Workspaces**: Workspace CRUD, membership, recommendations
- **Projects**: Project CRUD, inclusion, recommendations
- **Lifecycle Phases**: Phase transitions, state management
- **Artifacts**: Artifact CRUD, versioning, sync
- **Generation Runs**: Run lifecycle, review decisions, status
- **Preview Sessions**: Session creation, validation, expiration
- **Export**: Artifact export, download, format conversion
- **Governance**: Audit trails, compliance checks, policies

### External Integrations
- **Webhook Endpoints**: External system callbacks
- **File Upload/Download**: Binary asset handling
- **Streaming**: Real-time data streams (WebSocket)

### Canonical REST Specification
- **OpenAPI Spec**: `docs/api/openapi.yaml`
- **Generated Client**: `frontend/web/src/clients/generated/openapi.ts`
- **Generation Script**: `scripts/generate-rest-client.sh`

## GraphQL-Owned Domains

The following domains are GraphQL-owned and should use GraphQL queries/mutations:

### Dashboard & UI Composition
- **Dashboard Configuration**: Widget layout, data bindings
- **Dashboard Actions**: User actions, execution context
- **UI State**: Client-side state synchronization
- **Real-time Updates**: Live dashboard data via subscriptions

### Complex Data Relationships
- **Nested Queries**: Multi-level data fetching
- **Aggregations**: Computed metrics and summaries
- **Graph Traversal**: Relationship-based queries

### Client-Side Optimizations
- **Field Selection**: Partial data fetching
- **Batch Queries**: Multiple operations in single request
- **Introspection**: Schema discovery for tooling

## Canonical GraphQL Schema
- **Schema Location**: `frontend/apps/api/src/graphql/schema.graphql`
- **Resolvers**: `frontend/apps/api/src/graphql/resolvers/`
- **Gateway**: `frontend/apps/api/src/services/DashboardService.ts` (Node-to-Java proxy)

## Ownership Rules

### REST-Only Rules
1. All new state-changing operations must use REST
2. File upload/download must use REST
3. Webhook endpoints must be REST
4. External system integrations must use REST

### GraphQL-Only Rules
1. Dashboard composition must use GraphQL
2. Complex nested queries must use GraphQL
3. Real-time subscriptions must use GraphQL
4. Client-driven field selection must use GraphQL

### Shared Domains (Prohibited)
The following domains must NOT be split between REST and GraphQL:
- Artifact CRUD (REST-only)
- Project lifecycle (REST-only)
- User authentication (REST-only)
- Dashboard configuration (GraphQL-only)

## Enforcement

### CI Validation
- Run `scripts/verify-api-parity.sh` to validate REST client parity
- GraphQL schema must be validated against resolver implementations
- No duplicate endpoints across REST and GraphQL for the same domain

### Documentation Requirements
- All new REST endpoints must be documented in OpenAPI spec
- All new GraphQL fields must be documented in schema
- Ownership must be explicitly declared in this document

## Migration Path

### Existing Dual-Surface Endpoints
If a domain currently has both REST and GraphQL endpoints:
1. Determine canonical ownership based on domain rules above
2. Deprecate the non-canonical surface with migration notice
3. Update clients to use canonical surface
4. Remove deprecated surface after migration period

### Example: Dashboard
- **Canonical**: GraphQL (owned by DashboardService)
- **Non-canonical**: REST (if exists)
- **Action**: Deprecate REST dashboard endpoints, migrate to GraphQL

## References

- OpenAPI Specification: `docs/api/openapi.yaml`
- GraphQL Schema: `frontend/apps/api/src/graphql/schema.graphql`
- REST Client Generation: `scripts/generate-rest-client.sh`
- API Parity Validation: `scripts/verify-api-parity.sh`
