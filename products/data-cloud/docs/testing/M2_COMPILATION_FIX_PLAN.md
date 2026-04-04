# Milestone 2 — M2 Refactor & Compilation Fix (April 4, 2026)

> **Status**: 🔄 **REFACTORING (Compilation Failed, Framework Integration Needed)**  
> **Issue**: Test framework integration — need to use existing DataCloudHttpServerTestBase  
> **Compilation Errors**: 100 errors (mostly import/dependency issues)  
> **Action**: Refactor tests to use repo's actual test patterns  

---

## Compilation Errors Analysis

### Root Cause
Tests were written with generic patterns, but repository uses specific framework:
- **HTTP tests**: Use `DataCloudHttpServerTestBase` (already exists in `http/` dir)
- **Database**: Use `HikariCP` + direct JDBC (not Testcontainers)
- **Async**: ActiveJ `Promise<T>` + EventLoop (not JUnit rules)
- **HTTP Response**: ActiveJ's `io.activej.http.HttpResponse` (final, cannot extend)

### Specific Errors
1. ❌ Testcontainers imports missing (not in repo test suite)
2. ❌ EventloopRule import wrong package (should be from ActiveJ testing)
3. ❌ HttpResponse is final (cannot create MockHttpResponse)
4. ❌ DataCloudTestBase had wrong patterns
5. ❌ Wrong port type in DataCloudHttpServer constructor

---

## Refactor Plan

### Step 1: Use Existing TestBase
**File**: `DataCloudHttpServerTestBase` (already exists in `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/`)

**What it provides**:
- ✅ HTTP server management (start/stop)
- ✅ HttpClient (java.net.http)  
- ✅ Helper methods (GET, POST, PUT, DELETE)
- ✅ Response parsing (JSON ObjectMapper)
- ✅ Tenant context helpers
- ✅ Port management (findFreePort)

### Step 2: Placement Adjustment
Move tests from:
- ❌ `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/tests/`

To:
- ✅ `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/`

### Step 3: Test Class Updates

#### DataCloudEntityBoundaryTest
```java
// WRONG (current):
extends DataCloudTestBase

// CORRECT (new):
extends DataCloudHttpServerTestBase
```

#### DataCloudEventOrderingTest
```java
// WRONG (current):
extends DataCloudTestBase with Testcontainers

// CORRECT (new):
extends DataCloudHttpServerTestBase with direct JDBC
```

### Step 4: Remove Dependencies
Delete problematic files/patterns:
- ❌ `DataCloudTestBase.java` (use existing `DataCloudHttpServerTestBase`)
- ❌ Testcontainers setup (use HikariCP directly)
- ❌ MockHttpResponse (use actual clients)

---

## Action Plan (Next 2 Hours)

### High Priority (Get Compiling)
1. **Delete DataCloudTestBase.java** — Already have DataCloudHttpServerTestBase
2. **Move tests to http/ directory** — Collocate with other HTTP tests
3. **Update test imports** — Replace Testcontainers, use java.net.http
4. **Fix constructors** — Use proper DataCloudHttpServer(port, deps)
5. **Run compilation** — Verify no errors

### Medium Priority (Make Tests Pass)
1. **Verify services exist** — Check EntityService, EventService classes
2. **Fix assertion methods** — Use getResponseBody(), assertStatusCode() from base
3. **Database setup** — Use JDBC directly (HikariCP via connection pool)
4. **Event ordering schema** — Use actual SQL creation

### Low Priority (Polish)
1. Update coverage tracking
2. Document patterns
3. Run full test suite

---

## Success Criteria

✅ All tests compile cleanly (0 errors)
✅ All imports resolve correctly
✅ Tests extend DataCloudHttpServerTestBase
✅ No Testcontainers dependencies
✅ Tests located in `http/` directory (consistent) 
✅ Coverage tracking updated (M2: 76% → 85%)

---

## Timeline Estimate

- **Compilation fix**: 1 hour (reorg + refactor)
- **Test verification**: 1 hour (syntax, imports, runs)
- **Progressive tests**: Already written (just need file relocation)

**Target**: Green build by end of current session (4 more hours available)

---

## Files to Update

1. ✅ `DataCloudEntityBoundaryTest.java` — Extend DataCloudHttpServerTestBase
2. ✅ `DataCloudEventOrderingTest.java` — Extend DataCloudHttpServerTestBase
3. ✅ `DataCloudClientSerializationBoundaryTest.java` — Keep as unit test
4. ✅ `DataCloudConfigValidationTest.java` — Keep as unit test
5. ✅ `DataCloudSpiCapabilityTest.java` — Keep as unit test
6. ❌ `DataCloudTestBase.java` — DELETE (use existing base)

**Directory**:
- From: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/tests/`
- To: `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/`

---

## Quick Reference: DataCloudHttpServerTestBase Methods

```java
// These are available:
protected int findFreePort() throws IOException
protected void startServer() throws Exception
protected void stopServer() throws Exception

// HTTP helpers
protected HttpResponse<String> getJson(String path) throws Exception
protected HttpResponse<String> postJson(String path, Object body) throws Exception
protected HttpResponse<String> putJson(String path, Object body) throws Exception
protected HttpResponse<String> deleteJson(String path) throws Exception

// Response parsing
protected <T> T parseJsonResponse(String body, Class<T> type) throws IOException
protected int getStatusCode(HttpResponse<String> resp)

// Assertions
protected void assertStatusOk(HttpResponse<String> resp)
protected void assertGone(HttpResponse<String> resp)
```

---

**Next Action**: Begin refactoring entity/event tests to extend DataCloudHttpServerTestBase (start with imports, then class signature)

