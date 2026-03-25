# Async Patterns Guide — ActiveJ Promise

**Scope:** All Java code in `platform/java/*` and `shared-services/*`  
**Status:** Canonical reference. Supersedes any prior guidance on `Promise.of()` vs `Promise.ofBlocking()`.  
**Related Finding:** SHARED_MODULES_AUDIT_REPORT.md HIGH-004  
**Related ADR:** ADR-004 (ActiveJ Framework)

---

## The Golden Rule

| Use | When |
|-----|------|
| `Promise.ofBlocking(executor, () -> ...)` | **Any** blocking work: JDBC, Redis, HTTP calls, file I/O, `Thread.sleep`, semaphore waits, crypto operations |
| `Promise.of(value)` | Already-computed, in-memory values only (no I/O, no waiting) |
| `Promise.ofException(e)` | Already-known error values (no I/O, no waiting) |
| `SettablePromise<T>` | Custom async coordination where you `.set()` the result from another thread |

---

## Why This Matters

ActiveJ runs all application logic on a **single-threaded event loop**. A single blocking call on the event-loop thread freezes _all_ concurrent requests until the blocking call returns.

```
 Event-loop thread timeline (BAD — blocking on event loop)
 ───────────────────────────────────────────────────────────
 Request A ────► [handler] → jdbcQuery() ─────────────────────────► [response A]
                                          ↑ EVENT LOOP BLOCKED
              Request B queued ──────────────────────────► [response B, delayed]
              Request C queued ──────────────────────────────────► [response C, delayed]
```

```
 Event-loop thread timeline (GOOD — blocking offloaded)
 ───────────────────────────────────────────────────────────
 Request A ────► [handler] → Promise.ofBlocking(...) ──► [response A] (just a callback registration)
                                   ↓ (thread-pool executes JDBC)
              Request B ─────► [handler] → response B (event loop free!)
              Request C ─────► [handler] → response C (event loop free!)
```

---

## Correct Patterns

### Blocking I/O → `Promise.ofBlocking`

```java
// JDBC query
public Promise<List<EventRecord>> readEvents(String tenantId) {
    return Promise.ofBlocking(executor, () -> {
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT ...")) {
            stmt.setString(1, tenantId);
            // ... process ResultSet
            return results;
        }
    });
}

// Redis operation
public Promise<Optional<String>> getCached(String key) {
    return Promise.ofBlocking(executor, () -> 
        Optional.ofNullable(jedis.get(key))
    );
}

// File I/O
public Promise<byte[]> readFile(Path path) {
    return Promise.ofBlocking(executor, () -> Files.readAllBytes(path));
}

// Semaphore wait (e.g., BackpressureManager)
public Promise<Void> acquirePermit(Duration timeout) {
    return Promise.ofBlocking(executor, () -> {
        if (!semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new RateLimitExceededException("Backpressure limit exceeded");
        }
        return null;
    });
}
```

### Already-computed values → `Promise.of`

```java
// In-memory lookup (no I/O)
public Promise<Optional<Tenant>> findCachedTenant(String id) {
    Optional<Tenant> cached = inMemoryCache.get(id); // no blocking, no I/O
    return Promise.of(cached);                        // ✅ correct: value already computed
}

// Validation result (no I/O)
public Promise<ValidationResult> validate(Event event) {
    ValidationResult result = validator.check(event); // pure in-memory
    return Promise.of(result);                         // ✅ correct
}

// Pre-built response
public Promise<String> getVersion() {
    return Promise.of("1.0.0");  // ✅ correct: known at compile time
}
```

### Error paths → `Promise.ofException`

```java
public Promise<User> findUser(String id) {
    if (id == null || id.isBlank()) {
        return Promise.ofException(new ValidationException("User ID must not be blank"));
    }
    return Promise.ofBlocking(executor, () -> userRepository.findById(id));
}
```

---

## Anti-Patterns (Do NOT do these)

### ❌ Wrapping blocking work in `Promise.of()`

```java
// WRONG — JDBC runs on the event-loop thread, stalling all requests
public Promise<List<Row>> getData(String tenantId) {
    List<Row> rows = jdbcTemplate.query("SELECT ...", tenantId); // blocks here!
    return Promise.of(rows);  // ❌ value was computed blocking
}

// CORRECT
public Promise<List<Row>> getData(String tenantId) {
    return Promise.ofBlocking(executor, () ->
        jdbcTemplate.query("SELECT ...", tenantId)  // runs on thread pool
    );
}
```

### ❌ Calling `.getResult()` outside `EventloopTestBase`

```java
// WRONG — getResult() returns null if the Promise hasn't resolved yet
String result = service.processAsync("input").getResult(); // ❌ NPE risk!

// CORRECT in tests — extend EventloopTestBase and use runPromise()
class MyServiceTest extends EventloopTestBase {
    @Test
    void shouldProcess() {
        String result = runPromise(() -> service.processAsync("input")); // ✅
        assertThat(result).isEqualTo("expected");
    }
}
```

### ❌ Mixing CompletableFuture with ActiveJ Promise

```java
// WRONG — CompletableFuture.get() blocks the event loop
CompletableFuture<String> future = externalClient.callAsync();
String result = future.get();  // ❌ blocks event loop thread!
return Promise.of(result);

// CORRECT — bridge via SettablePromise or Promise.ofCallback
SettablePromise<String> p = new SettablePromise<>();
externalClient.callAsync().whenComplete((r, e) -> {
    if (e != null) p.setException(e);
    else p.set(r);
});
return p;
```

### ❌ `Thread.sleep()` on the event-loop thread

```java
// WRONG — blocks the event loop for the entire duration
public Promise<Void> retryAfterDelay() {
    Thread.sleep(1000); // ❌
    return doRetry();
}

// CORRECT — use Eventloop.delay or Promise.ofBlocking for delays
public Promise<Void> retryAfterDelay() {
    return Promise.ofBlocking(executor, () -> {
        Thread.sleep(1000); // runs on thread pool, event loop stays free
        return null;
    }).then(ignored -> doRetry());
}
```

---

## Executor Configuration

Services performing blocking I/O should declare a dedicated executor:

```java
private final Executor blockingExecutor;

// In constructor or @Inject
public MyService(DataSource dataSource) {
    this.dataSource = dataSource;
    // Virtual threads (Java 21+) for lightweight I/O concurrency
    this.blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    // Alternative: bounded thread pool for limited concurrency
    // this.blockingExecutor = Executors.newFixedThreadPool(
    //     Runtime.getRuntime().availableProcessors() * 2,
    //     r -> { Thread t = new Thread(r, "blocking-io"); t.setDaemon(true); return t; }
    // );
}
```

---

## Testing with `EventloopTestBase`

All async tests **MUST** extend `EventloopTestBase` from `libs:activej-test-utils`.

```java
@DisplayName("My Service Tests")
class MyServiceTest extends EventloopTestBase {

    @Test
    void shouldFetchFromDatabase() {
        // GIVEN
        MyService service = new MyService(mockDataSource);
        // WHEN — runPromise drives the event loop and returns the result
        List<Row> rows = runPromise(() -> service.fetchRows("tenant-1"));
        // THEN
        assertThat(rows).hasSize(3);
    }

    @Test
    void shouldThrowOnInvalidInput() {
        MyService service = new MyService(mockDataSource);
        assertThatThrownBy(() -> runPromise(() -> service.fetchRows(null)))
            .isInstanceOf(ValidationException.class);
    }
}
```

**Never** call `.getResult()` as control flow — it returns `null` if the Promise has not yet resolved in the test context.

---

## Audit Checklist

When reviewing code or conducting a code audit, check each `Promise.of(...)` call:

1. **Is the wrapped expression a pure in-memory operation?**  
   → Yes: `Promise.of(...)` is correct.  
   → No (involves JDBC, Redis, HTTP, file, semaphore, sleep): change to `Promise.ofBlocking(executor, ...)`.

2. **Does the class have a `blockingExecutor` field?**  
   → If it performs any I/O, it must have one.

3. **Are tests using `EventloopTestBase` + `runPromise()`?**  
   → If the test calls `.getResult()` directly, flag it.

---

## References

- `io/activej/promise/AbstractPromise.java` — vendored Promise base
- `platform/java/testing/…/EventloopTestBase.java` — test base class
- `platform/java/observability/…/PromisesCompat.java` — compatibility bridge
- ADR-004 (`docs/adr/ADR-004-activej-framework.md`) — framework choice rationale
- SHARED_MODULES_AUDIT_REPORT.md HIGH-004 — original finding
