# DMOS Feature Freeze Policy

**Status:** Active  
**Effective Date:** 2026-05-03  
**Purpose:** Ensure production readiness by completing all P0/P1 quality gates before feature expansion

---

## Policy Statement

**All new feature development is frozen until all P0 and P1 quality gates are closed.**

This policy applies to:
- New UI pages/components
- New API endpoints
- New domain models
- New integrations/connectors
- New workflows or business logic

---

## Completed P0 Gates

- ✅ P0-008: Complete real session refresh implementation
- ✅ P0-009: Generate canonical OpenAPI contract from backend routes and enforce in CI
- ✅ P0-012: Add browser E2E for campaign list/create/launch/pause lifecycle
- ✅ P0-013: Add production-mode auth E2E test
- ✅ P0-014: Add feature-flag production build and runtime tests
- ✅ P0-016: Inventory and hard-disable critical deterministic stubs in production
- ✅ P0-017: Add release gate to block production until all P0 tests are green

---

## Completed P1 Gates

- ✅ P1-014: Add sensitive redaction tests for AI action log
- ✅ P1-015: Expand dashboard to complete DMOS command center
- ✅ P1-016: Make all route/page availability backend-capability driven
- ✅ P1-020: Prove NotificationPlugin retry/DLQ behavior
- ✅ P1-029: Add AI/model provenance for strategy and budget generation
- ✅ P1-033: Add real strategy lifecycle E2E
- ✅ P1-034: Add real budget lifecycle E2E
- ✅ P1-035: Add approval role and permission matrix tests
- ✅ P1-036: Inventory content generation backend-only surfaces
- ✅ P1-037: Add UI or feature gates for backend-only marketed capabilities
- ✅ P1-038: Add public intake abuse controls
- ✅ P1-039: Add data retention and DSAR end-to-end proof
- ✅ P1-040: Add production startup test that default-deny policy pack is loaded
- ✅ P1-041: Add ArchUnit/lint rule against product logic in Kernel/platform plugins
- ✅ P1-042: Add static scan for test-only utilities in production code
- ✅ P1-043: Add exact changed-flow API integration suite
- ✅ P1-044: Add exact changed-flow browser E2E suite
- ✅ P1-045: Add DB state assertions to integration tests
- ✅ P1-046: Add feature-flag off/on backend tests
- ✅ P1-047: Add feature-flag off/on UI tests
- ✅ P1-048: Add OpenAPI/client generation CI
- ✅ P1-049: Add production persistence wiring proof
- ✅ P1-050: Add migration audit/deployment metric
- ✅ P1-051: Add frontend correlation ID display/support
- ✅ P1-052: Add distributed-safe rate limiting or document single-node limits
- ✅ P1-053: Add connector chaos/retry tests
- ✅ P1-054: Replace stale root audit doc or move DMOS audit to product audit path

---

## Exceptions

The following activities are permitted during the freeze:
- Bug fixes for existing features
- Security patches
- Performance optimizations (non-feature)
- Test improvements for existing features
- Documentation updates
- Infrastructure/tooling improvements

Any exception requires approval from the product owner and technical lead.

---

## Lifting the Freeze

The feature freeze will be lifted when:
1. All P0 gates are verified as passing in CI
2. All P1 gates are verified as passing in CI
3. A production readiness review is conducted
4. Explicit approval from product leadership is obtained

---

## Enforcement

- PRs adding new features during freeze will be rejected
- CI gate will block merges for new feature branches
- Code review checklist includes verification that change is not a new feature

---

## References

- DMOS Quality Gates: See `code-audits/dmos-7432-todo-register.md`
- Production Readiness Checklist: `products/digital-marketing/docs/RELEASE_CHECKLIST.md`
