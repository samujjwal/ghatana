# ADR: PHR Route Entitlement Canonicalization

## Status

Accepted

## Context

The PHR product requires consistent route entitlement enforcement across web frontend and backend API. Previously, route definitions and entitlement rules were maintained separately:

- **Web frontend:** `phrRouteContracts.ts` (TypeScript) defined routes with `minimumRole`, `actions`, `cards`, and lifecycle metadata
- **Backend:** `PhrEntitlementRoutes.java` (Java) defined route entitlements with role-based access rules
- **Mobile:** Route definitions embedded in navigation components

This separation led to several issues:
- **Drift risk:** Changes to one layer could go unnoticed in other layers
- **Manual synchronization:** Developers had to update multiple files for a single route change
- **Inconsistent validation:** No automated check that web and backend agreed on route entitlements
- **Maintenance burden:** Adding a new route required updates in 3+ places

## Decision

The PHR route entitlement system will be canonicalized through a single source of truth:

### 1. Canonical Route Contract JSON

**File:** `products/phr/config/phr-route-contract.json`

This JSON file serves as the single source of truth for PHR route definitions, containing:
- Route paths and labels
- Minimum role requirements
- Allowed actions and cards
- Lifecycle metadata (introducedAt, stability, deprecatedAt)
- Feature flag status
- Persona visibility

### 2. Web Frontend Generation

**File:** `products/phr/apps/web/src/phrRouteContracts.ts`

The web route contracts are generated from the canonical JSON:
- A build-time script reads `phr-route-contract.json`
- Generates TypeScript interfaces and the `phrRouteContracts` array
- Includes type-safe accessors for role-based route evaluation

**Fallback:** If the JSON file is missing, the TypeScript file can still be used directly (graceful degradation).

### 3. Backend Entitlement Loading

**File:** `PhrEntitlementRoutes.java`

The backend loads route entitlements from the canonical JSON:
- On startup, `PhrEntitlementRoutes` reads `phr-route-contract.json`
- Parses role order and route definitions
- Builds in-memory route entitlement map for fast lookup
- Falls back to hardcoded routes if JSON is missing (graceful degradation)

### 4. Parity Validation

**File:** `products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts`

An automated test ensures web and backend remain in sync:
- Compares web route contracts against backend route list
- Verifies route counts match
- Validates minimumRole consistency for common routes
- Checks that all routes have lifecycle metadata
- Ensures feature-flagged routes have experimental stability

### 5. CI Integration

The CI pipeline includes parity checks:
- `pnpm test products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts` runs on every PR
- Fails the build if web and backend routes drift
- Provides clear error messages indicating which routes are missing or mismatched

## Consequences

### Positive

- **Single source of truth:** One JSON file defines all route entitlements
- **Automated synchronization:** Changes propagate to web and backend automatically
- **Drift detection:** CI fails if layers become inconsistent
- **Type safety:** Generated TypeScript contracts are fully typed
- **Graceful degradation:** System continues to work if canonical file is missing
- **Easier maintenance:** Adding a route requires updating only the JSON file

### Negative

- **Additional build step:** Route contracts must be generated from JSON
- **Tooling dependency:** Requires the generation script to be maintained
- **Learning curve:** Developers must understand the canonicalization model
- **Initial effort:** Migration from separate definitions to canonical JSON requires upfront work

### Risks

- **Generation script bugs:** If the generation script has bugs, it could produce incorrect contracts
- **JSON format changes:** Changes to JSON schema require updating generation scripts
- **Merge conflicts:** Multiple developers editing the JSON file could cause conflicts
- **Fallback code paths:** Fallback code paths (when JSON is missing) may not be tested as frequently

## Alternatives Considered

### Alternative 1: Keep Separate Definitions with Manual Sync

- **Pros:** No additional tooling, familiar pattern
- **Cons:** High drift risk, manual synchronization error-prone
- **Rejected:** Insufficient for production-grade governance

### Alternative 2: Use Database for Route Definitions

- **Pros:** Queryable, can build UI for management
- **Cons:** Adds infrastructure complexity, harder to version control
- **Rejected:** Overkill for current needs, JSON files are sufficient

### Alternative 3: Backend as Single Source of Truth

- **Pros:** Backend already has all routes defined
- **Cons:** Frontend would need to query backend at build time, coupling concerns
- **Rejected:** Frontend should be able to build without backend dependency

## Implementation Notes

### Phase 1 (Complete)

- Created `phr-route-contract.json` with all route definitions
- Updated `PhrEntitlementRoutes.java` to load from JSON
- Created route entitlement parity test
- Added generation script to package.json
- Integrated parity test into CI

### Phase 2 (Future)

- Consider adding YAPPC integration for generating route contracts from IA baseline
- Add UI for viewing and editing route contracts (if needed)
- Add automated generation on file change (watch mode)

### Maintenance

- Update `phr-route-contract.json` when adding/removing routes
- Run parity test before committing route changes
- Review generation script if JSON schema changes
- Keep fallback code paths in sync with JSON schema

## References

- `products/phr/config/phr-route-contract.json` - Canonical route contract
- `products/phr/apps/web/src/phrRouteContracts.ts` - Generated web contracts
- `PhrEntitlementRoutes.java` - Backend entitlement loader
- `products/phr/apps/web/src/__tests__/route-entitlement-parity.test.ts` - Parity validation
- `docs/adr/ADR-phr-kernel-ia-contract.md` - Related IA contract ADR
