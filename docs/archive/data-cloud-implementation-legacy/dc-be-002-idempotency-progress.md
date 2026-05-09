# DC-BE-002: Ensure Mutating Routes are Idempotent - Progress Report

**Date:** 2026-05-09
**Status:** Phase 1 Complete, Phase 2 & 3 Pending

---

## Completed Work

### Phase 1: Idempotency Infrastructure ✅
1. **Extended idempotency store interface**
   - Created generic `WriteIdempotencyStore` interface in `planes/shared-spi`
   - Replaces entity-specific `EntityWriteIdempotencyStore` to support all mutating routes
   - Methods: `get(tenantId, operationScope, idempotencyKey)` and `put(...)`
   - Supports operation scoping (e.g., "entities:collection", "pipelines", "events")

2. **Created generic idempotency helper**
   - Created `IdempotencyHelper` class in `delivery/launcher/http`
   - Reusable idempotency checking and storage logic for all handlers
   - Methods: `checkIdempotencyOrNull()` and `storeIdempotency()`
   - Supports both durable store and in-memory fallback
   - Configurable TTL (24 hours default)
   - Configurable max entries (10,000 for in-memory)

3. **Created H2 implementation**
   - Created `H2WriteIdempotencyStore` in `delivery/runtime-composition`
   - Durable persistence in `dc_write_idempotency` table
   - Follows same pattern as `H2EntityWriteIdempotencyStore`
   - Automatic schema initialization
   - Automatic expiration eviction

4. **Integrated with bootstrap**
   - Updated `DataCloudHttpLauncherBootstrap` to wire up generic store
   - Added import for `WriteIdempotencyStore` and `H2WriteIdempotencyStore`
   - Wired up alongside entity-specific store for backward compatibility
   - Configured with 24-hour TTL

5. **Added to HTTP server**
   - Added `genericIdempotencyStore` field to `DataCloudHttpServer`
   - Added `withGenericIdempotencyStore()` builder method
   - Ready for use by handlers

---

## Remaining Work

### Phase 2: Route Migration (Pending)
1. **Audit all mutating routes**
   - Identify all POST, PUT, DELETE, PATCH endpoints
   - Categorize by operation type:
     - Entity CRUD (already has idempotency)
     - Pipelines (pipeline creation/update)
     - Events (event append)
     - Governance (governance operations)
     - Analytics (analytics queries)

2. **Add idempotency to pipeline routes**
   - Update pipeline handlers to use `IdempotencyHelper`
   - Add X-Idempotency-Key header support
   - Scope by "pipelines" operation scope

3. **Add idempotency to event routes**
   - Update event handlers to use `IdempotencyHelper`
   - Add X-Idempotency-Key header support
   - Scope by "events" operation scope

4. **Add idempotency to governance routes**
   - Update governance handlers to use `IdempotencyHelper`
   - Add X-Idempotency-Key header support
   - Scope by "governance" operation scope

5. **Add idempotency to analytics routes**
   - Update analytics handlers to use `IdempotencyHelper`
   - Add X-Idempotency-Key header support
   - Scope by "analytics" operation scope

### Phase 3: Testing (Pending)
1. **Add idempotency tests for each route**
   - Test that repeated requests with same key return cached response
   - Test that different keys execute the operation
   - Test TTL expiration
   - Test tenant isolation

2. **Add retry tests**
   - Test that retries with same idempotency key don't corrupt data
   - Test that retries after expiration re-execute the operation

3. **Add contract tests**
   - Verify X-Idempotency-Key header is documented in OpenAPI specs
   - Verify idempotency behavior is consistent across routes

---

## Current State Summary

**Infrastructure:** ✅ Complete
- Generic WriteIdempotencyStore interface created
- IdempotencyHelper class created
- H2WriteIdempotencyStore implementation created
- Bootstrap integration complete
- HTTP server integration complete

**Migration:** ⏳ Pending
- Entity CRUD already has idempotency (using old interface)
- Pipeline, event, governance, analytics routes need migration
- Need to audit all mutating routes
- Need to update each handler to use IdempotencyHelper

**Testing:** ⏳ Pending
- Idempotency tests needed for each route
- Retry tests needed
- Contract tests needed

---

## Known Issues

None at this time.

---

## Next Steps

1. **Audit mutating routes**
   - Identify all POST, PUT, DELETE, PATCH endpoints
   - Document current idempotency status

2. **Migrate handlers**
   - Update pipeline handlers to use IdempotencyHelper
   - Update event handlers to use IdempotencyHelper
   - Update governance handlers to use IdempotencyHelper
   - Update analytics handlers to use IdempotencyHelper

3. **Add tests**
   - Write idempotency tests for each route
   - Write retry tests
   - Write contract tests

4. **Update documentation**
   - Document idempotency pattern
   - Update ARCHITECTURE.md
   - Document non-idempotent routes explicitly

---

## Backward Compatibility

The entity-specific `EntityWriteIdempotencyStore` is retained for backward compatibility. Existing entity CRUD routes continue to work without modification. New generic infrastructure can be adopted incrementally by other handlers.

---

**Report Version:** 1.0
**Last Updated:** 2026-05-09
