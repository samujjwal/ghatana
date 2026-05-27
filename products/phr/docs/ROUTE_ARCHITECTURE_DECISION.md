# PHR Route Architecture Decision

## Decision: Flat Route Structure

**Status:** Accepted  
**Date:** 2026-05-26  
**Context:** PHR Web Application Routing

## Problem Statement

The PHR web application uses a flat route structure in `phrRouteContracts.ts` rather than nested route grouping. This decision needs to be documented to explain why this approach is acceptable and when nested routing might be considered.

## Decision

**Use a flat route structure for PHR web application routing.**

## Rationale

### 1. **Simplicity and Maintainability**
- PHR has a relatively shallow navigation hierarchy (mostly 2-3 levels deep)
- Flat routes are easier to search, navigate, and understand
- No complex nested route configuration reduces cognitive load for developers

### 2. **Persona-Based Segmentation**
- PHR routes are primarily segmented by persona (patient, provider, caregiver, admin, FCHV)
- Each persona has a distinct set of routes with minimal overlap
- Flat structure with clear naming conventions (`/provider/dashboard`, `/caregiver/dependents`) provides clear ownership

### 3. **Feature Flagging Simplicity**
- Feature flags are applied at the individual route level
- Flat structure makes it straightforward to toggle specific features without affecting nested hierarchies
- `feature-visibility.json` maps directly to route paths without complex traversal logic

### 4. **Mobile Parity**
- Mobile app uses a screen-based navigation model that maps naturally to flat routes
- Consistent route naming between web and mobile simplifies cross-platform development
- No need to translate nested web routes to mobile screen identifiers

### 5. **Route Entitlement Alignment**
- Backend route contracts use flat path structure (`/consents/grants/:grantId/revoke`)
- Direct mapping between frontend routes and backend API endpoints
- Entitlement evaluation is simpler with predictable path patterns

### 6. **Current Scale**
- PHR has ~26 IA-declared routes across 4 personas
- This scale does not warrant the complexity of nested routing
- Future growth can be accommodated by extending the flat structure with clear naming conventions

## When to Consider Nested Routing

Nested routing should be considered if:

1. **Deep Navigation Hierarchy**: If navigation depth exceeds 3-4 levels consistently
2. **Shared Layout Requirements**: If multiple routes require complex shared layouts with nested outlets
3. **Route Parameter Complexity**: If routes require multiple levels of dynamic parameters
4. **Module Boundaries**: If distinct product modules need isolated routing contexts

## Naming Conventions

To maintain clarity with flat routing:

- **Persona Prefix**: Use persona prefix for persona-specific routes
  - `/provider/dashboard`, `/caregiver/dependents`, `/fchv/dashboard`
- **Resource-Centric**: Group routes by resource for patient-facing features
  - `/records`, `/documents`, `/timeline`, `/conditions`
- **Action Suffix**: Use action suffix for specific operations
  - `/documents/upload`, `/documents/:docId/ocr`
- **Consistent Separators**: Use hyphens for multi-word route segments
  - `/release-readiness`, `/emergency-access`

## Examples

### Current Flat Structure
```
/dashboard
/records
/records/:recordId
/documents
/documents/upload
/documents/:docId/ocr
/provider/dashboard
/provider/patients
/caregiver/dependents
/fchv/dashboard
```

### Equivalent Nested Structure (Not Adopted)
```
/
  /dashboard
  /records/
    /:recordId
  /documents/
    /upload
    /:docId/ocr
  /provider/
    /dashboard
    /patients
  /caregiver/
    /dependents
  /fchv/
    /dashboard
```

## Trade-offs

### Advantages of Flat Structure
- ✅ Simpler to understand and maintain
- ✅ Easier to search and grep
- ✅ Direct mapping to backend routes
- ✅ Simpler feature flagging
- ✅ Better mobile parity

### Disadvantages of Flat Structure
- ❌ Less explicit hierarchy visualization
- ❌ Potential naming collisions as route count grows
- ❌ Harder to enforce module boundaries at route level

### Mitigations
- Use clear naming conventions with persona/resource prefixes
- Maintain `feature-visibility.json` as source of truth for grouping
- Consider module-based code organization even with flat routes
- Add route metadata (`category`, `module`) for programmatic grouping

## References

- `products/phr/apps/web/src/phrRouteContracts.ts` - Route contracts
- `products/phr/config/feature-visibility.json` - Feature visibility configuration
- `products/phr/config/phr-usecase-baseline.json` - Use case baseline
- `docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md` - Implementation tracker (C1-06)

## Review Date

This decision should be reviewed if:
- Route count exceeds 50 routes
- Navigation depth consistently exceeds 3 levels
- New module boundaries require isolated routing contexts
