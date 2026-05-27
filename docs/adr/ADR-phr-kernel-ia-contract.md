# ADR: PHR IA as Kernel-Managed Product Contract

## Status

Accepted

## Context

The PHR (Personal Health Record) product requires a comprehensive Information Architecture (IA) that maps personas, use cases, routes, screens, APIs, and implementation status. Previously, this IA was maintained in separate documentation files without a formal contract with the Kernel runtime.

This separation led to several issues:
- Documentation drift between IA docs and actual implementation
- No automated validation that implemented features match documented IA
- Difficulty tracking feature flags and visibility rules
- No clear mechanism for Kernel to understand PHR product surface
- Release readiness checks required manual verification

## Decision

The PHR IA will be formalized as a Kernel-managed product contract through the following mechanisms:

### 1. Canonical Baseline File

**File:** `products/phr/config/phr-usecase-baseline.json`

This JSON file serves as the single source of truth for PHR IA, containing:
- All use cases by persona (patient, clinician, caregiver, admin, FCHV)
- Web routes and mobile screen mappings
- Backend API endpoints
- Kernel capability dependencies
- Implementation status (implemented, partial, feature_flagged, etc.)
- Phase assignments (mvp-current, mvp-next, phase-2, deferred, removed)
- Offline support flags
- Traceability notes

### 2. Feature Visibility Configuration

**File:** `products/phr/config/phr-feature-visibility.json`

This file controls which IA items are visible per persona and which feature flags gate their visibility. It includes:
- Feature flag definitions with defaults
- Visibility rules for IA items by persona
- Route-level visibility rules
- Integration with Kernel feature flag system

### 3. Automated Evidence Generation

**Scripts:**
- `scripts/generate-phr-ia-coverage-doc.mjs` - Generates human-readable IA coverage report
- `scripts/generate-phr-doc-code-mismatch-evidence.mjs` - Validates IA against actual code
- `scripts/check-phr-ia-coverage.mjs` - CI gate for IA coverage

These scripts ensure that:
- Documentation matches implementation
- Status claims are validated against code
- Mismatches are detected before release
- Evidence is generated for Kernel consumption

### 4. Kernel Runtime Integration

The PHR product integrates with Kernel runtime APIs for:
- Release readiness evidence (migrating from file parsing to Kernel API)
- Feature flag management through Kernel feature flag service
- Product unit lifecycle management
- Evidence outbox health checks

### 5. Release Governance

**File:** `products/phr/docs/01_governance/release-checklist.md`

A comprehensive release checklist that requires:
- Zero doc/code mismatches
- All implemented features have corresponding tests
- Security and privacy gates pass
- Feature flags properly configured
- Sign-off from engineering, product, security, and QA

## Consequences

### Positive

- **Single Source of Truth:** The baseline file eliminates ambiguity about what is implemented
- **Automated Validation:** Scripts detect mismatches between docs and code automatically
- **Kernel Alignment:** PHR IA is now a first-class Kernel-managed contract
- **Release Safety:** Release checklist prevents releases with documentation drift
- **Feature Flag Clarity:** Visibility rules are explicitly defined and validated
- **Traceability:** Every use case maps to routes, screens, APIs, and capabilities

### Negative

- **Additional Configuration:** Requires maintaining JSON baseline and visibility files
- **Script Maintenance:** Evidence generation scripts need updates as PHR evolves
- **Learning Curve:** Team must understand the IA contract model
- **Initial Effort:** Migration from ad-hoc documentation to formal contract requires upfront work

### Risks

- **Configuration Drift:** If baseline file is not updated with code changes, validation will fail
- **Over-Engineering:** May be seen as too process-heavy for a small team
- **Tooling Dependency:** Reliance on scripts for validation introduces tooling dependency

## Alternatives Considered

### Alternative 1: Keep IA in Markdown Only
- **Pros:** Simple, no additional tooling
- **Cons:** No automated validation, prone to drift, no Kernel integration
- **Rejected:** Insufficient for production-grade governance

### Alternative 2: Use Database for IA
- **Pros:** Queryable, can build UI for management
- **Cons:** Adds infrastructure complexity, harder to version control
- **Rejected:** Overkill for current needs, JSON files are sufficient

### Alternative 3: Code-Only IA (No Separate Docs)
- **Pros:** Always in sync with code
- **Cons:** Harder for non-technical stakeholders, loses narrative documentation
- **Rejected:** Need human-readable documentation for product management

## Implementation Notes

### Phase 1 (Complete)
- Created `phr-usecase-baseline.json` with all use cases
- Created `phr-feature-visibility.json` with feature flags
- Created evidence generation scripts
- Created release checklist
- Updated product vision to reflect current implementation status

### Phase 2 (Future)
- Integrate baseline with Kernel product unit contract
- Migrate release readiness to Kernel runtime API
- Add Kernel evidence outbox health checks
- Build UI for IA management (if needed)

### Maintenance

- Update baseline when adding/removing use cases
- Update visibility when changing feature flags
- Run evidence generation scripts before each release
- Review checklist items quarterly for relevance

## References

- `products/phr/config/phr-usecase-baseline.json` - Canonical IA baseline
- `products/phr/config/phr-feature-visibility.json` - Feature visibility rules
- `scripts/generate-phr-ia-coverage-doc.mjs` - Coverage documentation generator
- `scripts/generate-phr-doc-code-mismatch-evidence.mjs` - Mismatch evidence generator
- `scripts/check-phr-ia-coverage.mjs` - CI coverage check
- `products/phr/docs/01_governance/release-checklist.md` - Release checklist
- Kernel product unit contract specification
