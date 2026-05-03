# DMOS Consolidated Implementation Plan
**Consolidated from Code Review Analysis (PDF 1) and Kernel Enhancement Audit (PDF 2)**  
**Commit Reference:** 4431520ab590  
**Date:** 2026-05-03  
**Status:** NOT PRODUCTION READY - P0 blockers must be resolved

---

## Executive Summary

This plan consolidates findings from two independent audits:
1. **End-to-End Digital-Marketing Product Audit** - Comprehensive DMOS product review
2. **Kernel Enhancement Audit & Hardening Plan** - Kernel integration and boundary hardening review

**Overall Verdict:** DMOS shows substantial architectural progress but is **not production-ready**. Critical P0 issues in security, privacy, API contracts, and connector implementation must be resolved before any production deployment.

**Key Consolidated Ratings:**
- **Correctness:** 🚧 Partial - Core domain logic structured but flows miswired/stubbed
- **Completeness:** 🚧 Partial - Many MVP capabilities unimplemented or stubbed
- **Production Readiness:** ❌ Not yet - Build gates violated, PII storage unsafe, contracts mismatched
- **UI/UX:** ⚠️ Needs work - Hook violations, missing headers, brittle UX
- **Security & Privacy:** ❌ High risk - Raw PII storage, inconsistent auth, missing consent
- **Kernel Alignment:** 🚧 Partial - Plugins used inconsistently, boundaries not consolidated
- **Observability:** ⚠️ Baseline only - Logs exist but no OTel, metrics not exported

---

## Consolidated P0 Blockers (Must Fix Before Any Release)

### 1. Security & Privacy - Raw PII Storage
**Source:** Both audits identify this as critical
- **Issue:** `ContactEntity` and `SuppressionEntryEntity` store raw email addresses without hashing
- **Risk:** Data breach, GDPR/CCPA violations, non-compliance
- **Required Fix:**
  - Add `EncryptionPort` and `HashingPort` to kernel (from kernel hardening plan)
  - Implement AES encryption and HMAC hashing with secure salt
  - Update entities to store hashed identifiers; encrypt raw PII
  - Update queries to use hashed keys
  - Implement DSAR deletion endpoints
- **Acceptance Criteria:**
  - All contact points stored hashed with salt
  - Raw values encrypted at rest
  - DSAR endpoints exist and functional
  - Suppression queries work on hashes
- **Required Tests:**
  - DB integration test verifying hashed storage
  - API test verifying hashed input
  - DSAR workflow test
  - Encryption key not logged test

### 2. API Contract Mismatch - Approval Flow
**Source:** PDF 1 (Product Audit)
- **Issue:** Backend returns `{ items: [...] }` while UI expects array of `ApprovalRequest`; enum definitions differ
- **Impact:** UI fails to render approvals, approval flow broken
- **Required Fix:**
  - Align DTO definitions across backend and UI
  - Implement `PendingListResponse` in UI
  - Unify enum definitions in shared package
  - Generate TypeScript enums from Java
- **Acceptance Criteria:**
  - `listPendingApprovals` returns expected JSON shape
  - UI renders approval list correctly
  - No enum drift between UI/backend
- **Required Tests:**
  - API integration test verifying shape
  - UI test verifying list rendering
  - Contract test ensuring no mismatch

### 3. Header Propagation & Authorization
**Source:** Both audits
- **Issue:** UI does not send required headers (X-Tenant-ID, X-Principal-ID, X-Roles, X-Permissions, correlation ID, idempotency key); backend rejects requests
- **Risk:** Cross-tenant data access, unauthorized actions
- **Required Fix:**
  - Implement request interceptor in UI that reads AuthContext and attaches all required headers
  - Generate correlation ID per request
  - Enforce mandatory headers in backend (return 400 when missing)
  - Treat missing/empty roles as no privileges (deny by default)
- **Acceptance Criteria:**
  - All requests include required headers
  - Missing headers produce 400 error
  - Unauthorized users cannot see or act on approvals
- **Required Tests:**
  - Test verifying requests include required headers
  - Test ensuring missing headers produce 400
  - Cross-tenant data retrieval denied test
  - Unauthorized approval actions fail test

### 4. Production-Reachable Stubs - Google Ads Connector
**Source:** PDF 1 (Product Audit)
- **Issue:** `DmGoogleAdsCampaignConnectorServiceImpl` throws `UnsupportedOperationException` when feature flag disabled, violating quality gates
- **Impact:** Campaign launch fails unexpectedly in production
- **Required Fix:**
  - Replace exception with proper gating: if disabled, return error response with 503
  - When enabled, implement actual call using asynchronous executor with retry/backoff
  - Integrate with outbox/inbox pattern
  - Use dedicated worker pool (not event loop blocking)
- **Acceptance Criteria:**
  - Launching campaign returns proper error when disabled
  - When enabled, creates campaign in Google Ads
  - No event loop blocking
  - Metrics recorded
- **Required Tests:**
  - Integration test enabling/disabling connector
  - Test call to Google Ads stub
  - Test event loop remains responsive

### 5. UI Hook Ordering Violation
**Source:** PDF 1 (Product Audit)
- **Issue:** `DashboardPage` conditionally calls hooks after early returns, violating React rules
- **Impact:** Runtime error, unpredictable state
- **Required Fix:**
  - Refactor to call hooks unconditionally
  - Extract subcomponents if necessary
  - Handle loading state separately
- **Acceptance Criteria:**
  - Dashboard loads without React hook error
  - Logs show correct sequence
- **Required Tests:**
  - UI unit test verifying no hook errors
  - E2E test navigating dashboard

### 6. Idempotency & Concurrency
**Source:** PDF 1 (Product Audit)
- **Issue:** Commands and workflows lack idempotency tokens; duplicate actions possible
- **Impact:** Duplicate external actions, race conditions
- **Required Fix:**
  - Add idempotency tokens to API
  - Store tokens in DB with unique constraints
  - Reject duplicate requests (409)
  - Implement concurrency locks on workflow state changes
- **Acceptance Criteria:**
  - Multiple identical requests do not produce duplicate commands
  - Concurrent transitions are safe
  - Errors return 409
- **Required Tests:**
  - Integration test sending duplicate requests
  - Concurrency test with race conditions

---

## Consolidated P1 High Priority Tasks

### 7. Observability & OpenTelemetry Integration
**Source:** Both audits (overlapping)
- **Issue:** Metrics logged but not exported; no OpenTelemetry instrumentation; correlation IDs not propagated
- **Required Fix:**
  - Implement `MetricCollectorPort` and `TracingPort` via kernel plugin
  - Export metrics to monitoring system
  - Instrument spans using OpenTelemetry
  - Propagate correlation IDs across all calls
  - Update `DmosMetricsCollector` to delegate to kernel
- **Acceptance Criteria:**
  - Metrics exported via kernel
  - OTel traces captured across modules
  - Correlation IDs appear in all logs
  - Dashboards reflect metrics
- **Required Tests:**
  - Integration test capturing metrics via test collector
  - Instrumentation test verifying spans
  - Correlation propagation test

### 8. Generic Risk Engine (Kernel Hardening)
**Source:** PDF 2 (Kernel Audit)
- **Issue:** `DmRiskPlugin` is product-specific and returns constant LOW; not generalized
- **Required Fix:**
  - Refactor into generic `RiskPlugin` in kernel
  - Use rule sets defined in domain packs
  - Return dynamic risk scores based on attributes
  - Remove product-specific logic from DMOS
- **Acceptance Criteria:**
  - DMOS supplies rule set
  - Kernel computes risk dynamically
  - Risk scores vary based on attributes
  - DMOS does not code risk logic
- **Required Tests:**
  - Unit test for risk engine returning correct scores
  - Integration test verifying risk gating in DMOS functions

### 9. Generic Compliance Engine (Kernel Hardening)
**Source:** PDF 2 (Kernel Audit)
- **Issue:** Compliance logic is product-specific; duplicates kernel capabilities
- **Required Fix:**
  - Move compliance logic into kernel
  - DMOS contributes `DmComplianceRules` as policy definitions
  - Unify error codes and severity classifications
  - `CompliancePort` returns rule failures with codes/severity
- **Acceptance Criteria:**
  - CompliancePort returns rule failures with codes/severity
  - DMOS handles errors uniformly
- **Required Tests:**
  - Tests verifying rule failure detection
  - Integration test verifying preflight blocking

### 10. Duplicate Boundary Resolvers (Kernel Hardening)
**Source:** PDF 2 (Kernel Audit)
- **Issue:** Multiple classes resolve domain boundaries (e.g., `DmosBoundaryResolver`, `GenericBoundaryResolver`)
- **Required Fix:**
  - Consolidate into single canonical `PolicyResolver`
  - Remove duplication from DMOS
  - Ensure kernel supplies rule-based DSL for policy resolution
- **Acceptance Criteria:**
  - Resolvers unified
  - DMOS imports and uses kernel resolver
  - No duplicate classes remain
- **Required Tests:**
  - Unit test ensuring rules map correctly to policies
  - Integration test verifying policies executed via kernel without DMOS override

### 11. Documentation & API Specifications
**Source:** Both audits
- **Issue:** Stale README.md and API docs; mismatch with code; missing OpenAPI spec
- **Required Fix:**
  - Update docs to reflect new modules (dm-infra, dm-persistence, dm-connector-google-ads)
  - Generate OpenAPI spec
  - Update UI API client to use generated types
- **Acceptance Criteria:**
  - README lists all modules and capabilities
  - OpenAPI spec published
  - UI uses generated types
  - Contract tests pass
- **Required Tests:**
  - Doc update check
  - OpenAPI diff test
  - Codegen integration test

### 12. UI Role Handling & Validation
**Source:** PDF 1 (Product Audit)
- **Issue:** Approver list defaults to allow when roles array is empty; missing validation and error states
- **Required Fix:**
  - If roles undefined/empty, treat user as least privilege
  - Enforce role checks server-side and in UI
  - Add client-side validation
  - Display field-level errors
  - Catch backend errors and show actionable messages
- **Acceptance Criteria:**
  - Missing roles treated as none
  - Backend enforces required role
  - Forms validate inputs
  - Error states handled gracefully
- **Required Tests:**
  - Tests verifying unauthorized users cannot see or act on approvals
  - Tests covering error scenarios

### 13. Database Migrations & Constraints
**Source:** PDF 1 (Product Audit)
- **Issue:** No schema migrations; lack of foreign key constraints; missing composite indexes
- **Required Fix:**
  - Use Flyway or Liquibase for migrations
  - Add migrations for each entity
  - Add FK constraints (e.g., workflowId in CommandEntity)
  - Add composite indexes on (tenant, workspace)
  - Add TTL/retention columns
- **Acceptance Criteria:**
  - DB can be created and migrated forward/back
  - Constraints enforced
  - Indexes present
- **Required Tests:**
  - Integration tests running migrations
  - Tests verifying constraints
  - Rollback tests

---

## Consolidated P2 Medium Priority Tasks

### 14. Encryption & Hashing Ports (Kernel Hardening)
**Source:** PDF 2 (Kernel Audit) - overlaps with P0 #1 but broader scope
- **Issue:** Kernel lacks generic `EncryptionPort`/`HashingPort`
- **Required Fix:**
  - Add ports to kernel
  - Implement default AES and HMAC hashing
  - Update DMOS to use them for all PII storage
- **Acceptance Criteria:**
  - PII stored hashed/encrypted
  - DSAR operations supported
  - Secrets not logged
- **Required Tests:**
  - DB integration test verifying hashed values
  - DSAR test

### 15. Rule Pack Validation
**Source:** PDF 2 (Kernel Audit)
- **Issue:** No build-time or startup validation for domain packs
- **Required Fix:**
  - Add validation for duplicate rule keys, missing handlers, invalid DSL syntax
  - Fail build or log errors for invalid packs
- **Acceptance Criteria:**
  - Validation runs in CI
  - Invalid packs cause build failure
  - DMOS passes validation
- **Required Tests:**
  - Unit tests for validation
  - Integration test with invalid pack

### 16. Plugin Lifecycle Management
**Source:** PDF 2 (Kernel Audit)
- **Issue:** DMOS does not implement plugin lifecycle management
- **Required Fix:**
  - Implement `KernelPlugin` lifecycle methods (install, uninstall, reload)
  - Enable hot-swap
  - Ensure state preserved across reload
- **Acceptance Criteria:**
  - Plugins can be reloaded without losing context
  - Metrics and audit remain intact
- **Required Tests:**
  - Tests invoking reload and verifying plugin functionality persists

### 17. Ledger Integration
**Source:** PDF 2 (Kernel Audit)
- **Issue:** DMOS does not use ledger plugin; budgets and ROI not tracked
- **Required Fix:**
  - Expose `LedgerPort` to DMOS
  - Implement `DmLedgerPlugin` to record marketing spend
  - Track ROI
  - Connect to accounting system
- **Acceptance Criteria:**
  - Budgets recorded
  - Spend vs. ROI reported
  - Ledger entries created for each spend
- **Required Tests:**
  - Integration test verifying ledger entries created
  - ROI calculations test

### 18. Policy DSL & Configuration Service
**Source:** PDF 2 (Kernel Audit)
- **Issue:** No DSL for policies; no central configuration service
- **Required Fix:**
  - Provide DSL for writing policies (risk, compliance, consent)
  - Central configuration service to load policies at runtime
  - Update DMOS to consume policies via configuration
- **Acceptance Criteria:**
  - Policies can be updated without code changes
  - DMOS fetches policies via service
  - No duplication
- **Required Tests:**
  - Tests verifying dynamic policy reload
  - Integration test updating a policy and seeing effect

### 19. Responsive & Accessible Design
**Source:** PDF 1 (Product Audit)
- **Issue:** UI not responsive; missing ARIA labels; focus management issues
- **Required Fix:**
  - Add responsive CSS and ARIA attributes
  - Test on multiple breakpoints
  - Implement focus management for modals
- **Acceptance Criteria:**
  - UI renders correctly on mobile and desktop
  - Passes basic accessibility tests
- **Required Tests:**
  - Playwright test for responsiveness
  - Accessibility audit test

### 20. Duplicate Definitions Cleanup
**Source:** PDF 1 (Product Audit)
- **Issue:** Approval enums, roles, endpoints defined separately in UI/backend
- **Required Fix:**
  - Create shared domain package
  - Generate TypeScript enums from Java
  - Unify roles definitions
  - Use OpenAPI spec for endpoints
- **Acceptance Criteria:**
  - One source of truth for enums
  - No drift
  - Codegen for UI
- **Required Tests:**
  - Unit test verifying shared enums
  - Contract test verifying no mismatch

---

## P3 Low Priority / Strategic Backlog

### 21. Kernel Documentation & Matrices
**Source:** PDF 2 (Kernel Audit)
- **Required Fix:**
  - Create kernel documentation describing modules, extensions, plugins, lifecycle
  - Document boundary mapping and plugin interfaces
  - Produce traceability matrices
- **Acceptance Criteria:**
  - New docs exist
  - DMOS developers can understand kernel usage
  - Diagrams updated

### 22. Strategic Kernel Enhancements (Phases 5-6)
**Source:** PDF 2 (Kernel Audit)
- **Required Fix:**
  - Build configuration service
  - Event registry
  - Stable rule DSL
  - Generic CLI
  - Plugin marketplace
- **Acceptance Criteria:**
  - Implementation milestones added to roadmap
  - Design documents created

---

## Implementation Phases

### Phase 1: Critical Security & Contract Fixes (P0)
**Duration:** 2-3 weeks
**Tasks:** 1, 2, 3, 4, 5, 6
**Goal:** Make system safe and functionally correct
**Blockers:** None (can start immediately)

### Phase 2: Kernel Integration & Observability (P1)
**Duration:** 3-4 weeks
**Tasks:** 7, 8, 9, 10, 11, 12, 13
**Goal:** Align with kernel hardening plan, enable observability
**Dependencies:** Phase 1 complete

### Phase 3: Data Integrity & Plugin Hardening (P2)
**Duration:** 2-3 weeks
**Tasks:** 14, 15, 16, 17, 18
**Goal:** Complete kernel hardening phases 1-3
**Dependencies:** Phase 2 complete

### Phase 4: UX Polish & Cleanup (P2)
**Duration:** 1-2 weeks
**Tasks:** 19, 20
**Goal:** Improve UX, remove technical debt
**Dependencies:** Phase 3 complete

### Phase 5: Documentation & Strategic (P3)
**Duration:** Ongoing
**Tasks:** 21, 22
**Goal:** Long-term maintainability and extensibility
**Dependencies:** Phase 4 complete

---

## Production Readiness Checklist

Before any production release, ALL of the following must be complete:

### Correctness & Completeness
- [ ] All flows implemented end-to-end (strategy → proposal → approval → campaign → analytics)
- [ ] Compliance checks and risk assessments enforced at runtime
- [ ] No production mocks/stubs (remove all `UnsupportedOperationException`, TODOs, placeholders)
- [ ] Connectors either disabled with proper error or fully implemented

### UI/UX
- [ ] Dashboard and flows responsive, accessible, low cognitive load
- [ ] Clear primary actions and error states
- [ ] No hook violations
- [ ] All forms validated with field-level errors

### Backend/API
- [ ] Contracts match UI exactly
- [ ] Validation and error handling complete
- [ ] Idempotency enforced on all mutating operations
- [ ] All required headers enforced

### Database/Data Integrity
- [ ] Schemas migrated with Flyway/Liquibase
- [ ] PII hashed and encrypted
- [ ] Foreign key constraints added
- [ ] Composite indexes on (tenant, workspace)
- [ ] Retention policies defined and implemented

### Security/Privacy
- [ ] Tenant isolation enforced at all layers
- [ ] Role/permission checks deny by default
- [ ] Consent management integrated and enforced
- [ ] DSAR processes implemented
- [ ] Secret management integrated
- [ ] Audit logs redact PII

### Observability
- [ ] OpenTelemetry instrumentation complete
- [ ] Correlation IDs propagated across all services
- [ ] Metrics exported via kernel ports
- [ ] Dashboards created and configured
- [ ] Alerting rules defined

### Performance
- [ ] No event loop blocking
- [ ] Database indexes optimized
- [ ] Pagination implemented on all list endpoints
- [ ] Concurrency controls in place
- [ ] Load tests passing with defined SLOs

### Tests
- [ ] 100% coverage for changed code
- [ ] Integration tests across API and DB
- [ ] UI E2E tests for critical flows
- [ ] Negative and edge case tests
- [ ] Load and concurrency tests

### Documentation
- [ ] README updated with all modules and capabilities
- [ ] Architecture docs current
- [ ] API docs generated from OpenAPI spec
- [ ] User guides provided
- [ ] Runbooks created
- [ ] Roadmaps updated

---

## Test Strategy Summary

### Unit Tests
- Domain logic and transformations
- Service layer business rules
- Plugin implementations
- Policy/rule engines

### Integration Tests
- API endpoints with real DB
- Kernel plugin wiring
- Outbox/inbox patterns
- Migration compatibility

### E2E Tests
- Critical user journeys (strategy creation, approval, campaign launch)
- Multi-tenant scenarios
- Cross-service workflows

### Contract Tests
- API shape matching between UI and backend
- Enum consistency
- OpenAPI spec validation

### Performance Tests
- Approval queue under 1000 requests
- Workflow engine stress test
- Concurrency and race conditions
- Event loop responsiveness

### Security Tests
- Header enforcement
- Authorization bypass attempts
- PII encryption verification
- Secret leakage prevention

---

## Success Metrics

### Before Implementation
- Production Readiness: ❌ Not ready
- Security Score: 3/10 (raw PII, inconsistent auth)
- Kernel Alignment: 4/10 (duplicate resolvers, product-specific plugins)
- Observability: 3/10 (logs only, no OTel)
- Test Coverage: 60-80% (varies by module)

### After Phase 1 (P0 Complete)
- Production Readiness: ⚠️ Safe for internal demo behind flags
- Security Score: 7/10 (PII hashed, headers enforced)
- Kernel Alignment: 5/10 (basic cleanup)
- Observability: 4/10 (still needs OTel)
- Test Coverage: 85%+ for critical paths

### After Phase 2 (P1 Complete)
- Production Readiness: ⚠️ Ready for beta with feature flags
- Security Score: 8/10 (consent integrated, DSAR implemented)
- Kernel Alignment: 7/10 (generic engines, consolidated resolvers)
- Observability: 8/10 (OTel wired, metrics exported)
- Test Coverage: 90%+ across all modules

### After Phase 3 (P2 Complete)
- Production Readiness: ✅ Ready for production
- Security Score: 9/10 (full encryption, retention policies)
- Kernel Alignment: 9/10 (plugin lifecycle, ledger integrated)
- Observability: 9/10 (dashboards, alerting)
- Test Coverage: 95%+ including load tests

### After Phase 4 (P2 Complete)
- Production Readiness: ✅ Production ready with polished UX
- Security Score: 9/10
- Kernel Alignment: 9/10
- Observability: 9/10
- Test Coverage: 95%+

---

## References

- **Product Audit:** Code Review Analysis.pdf
- **Kernel Audit:** Code Review Analysis (1).pdf
- **Kernel Decision Guide:** `docs/kernel/KERNEL_MODULE_EXTENSION_PLUGIN_DECISION_GUIDE.md`
- **Hardening Plan:** `KERNEL_BOUNDARY_HARDENING_PLAN.md`
- **Commit:** 4431520ab5902d5b306088aec1cdcde8e4477e22

---

## Appendix: Overlapping Issues Consolidation

The following issues appeared in both audits and were consolidated:

| Issue | PDF 1 Reference | PDF 2 Reference | Consolidated Priority |
|-------|----------------|----------------|---------------------|
| Observability gaps | P1 Metrics & O11y | High Observability ports | P1 #7 |
| Risk engine product-specific | P1 Kernel plugin generalisation | High Generic risk engine | P1 #8 |
| Compliance engine product-specific | P1 Kernel plugin generalisation | High Generic compliance engine | P1 #9 |
| Duplicate boundary resolvers | P1 Kernel plugin generalisation | High Duplicate resolvers | P1 #10 |
| Documentation stale | P1 Documentation & APIs | Low Documentation & matrices | P1 #11 |
| Encryption/hashing missing | P0 Privacy (PII storage) | High Encryption & hashing ports | P0 #1 (broader scope) |

This consolidation eliminates duplication and provides a single source of truth for implementation priorities.
