# ActiveJ Promise Patterns - Best Practices

**Status**: ACTIVE  
**Version**: 1.0.0  
**Date**: March 26, 2026

---

## Overview

This guide documents correct usage patterns for ActiveJ `Promise` API, particularly around `Promise.ofBlocking()` vs `Promise.of()` usage.

---

## Core Principles

### 1. Promise.of() - Synchronous Operations

Use `Promise.of()` when wrapping **already-completed synchronous operations**:

```java
// ✅ CORRECT - Synchronous operation wrapped in Promise
public Promise<User> getUser(String userId) {
    try {
        User user = repository.findById(userId);
        return Promise.of(user);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
```

### 2. Promise.ofBlocking() - Blocking I/O Operations

Use `Promise.ofBlocking()` **ONLY** when you have a blocking I/O operation that needs to run on a separate thread pool:

```java
// ✅ CORRECT - Blocking I/O operation
public Promise<Data> fetchFromDatabase(String query) {
    return Promise.ofBlocking(executor, () -> {
        // This runs on executor thread pool
        return jdbcTemplate.query(query);
    });
}
```

**CRITICAL**: `Promise.ofBlocking()` requires:
1. An `Executor` parameter
2. ActiveJ Reactor context in the calling thread
3. Proper thread pool management

---

## Common Mistakes

### ❌ WRONG: Using Promise.ofBlocking() without Executor

```java
// ❌ WRONG - Will fail with "No reactor in current thread"
public Promise<Node> getNode(String nodeId) {
    return Promise.ofBlocking(() -> {
        return storageAdapter.getNode(nodeId);
    });
}
```

**Error**: `IllegalStateException: No reactor in current thread`

### ✅ CORRECT: Use Promise.of() for synchronous calls

```java
// ✅ CORRECT - Synchronous operation
public Promise<Node> getNode(String nodeId) {
    try {
        Node node = storageAdapter.getNode(nodeId);
        return Promise.of(node);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
```

---

## Pattern Catalog

### Pattern 1: Simple Synchronous Operation

```java
public Promise<Result> simpleOperation(String input) {
    try {
        validateInput(input);
        Result result = processSync(input);
        return Promise.of(result);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
```

### Pattern 2: Blocking I/O with Executor

```java
private final Executor blockingExecutor;

public Promise<Data> blockingIO(String query) {
    return Promise.ofBlocking(blockingExecutor, () -> {
        // Blocking database call
        return database.executeQuery(query);
    });
}
```

### Pattern 3: Async Chaining

```java
public Promise<ProcessedData> processData(String id) {
    return fetchData(id)
        .then(data -> validateData(data))
        .then(validated -> transformData(validated))
        .whenException(e -> logger.error("Processing failed", e));
}
```

### Pattern 4: Parallel Execution

```java
public Promise<CombinedResult> parallelFetch(String id1, String id2) {
    Promise<Data1> p1 = fetchData1(id1);
    Promise<Data2> p2 = fetchData2(id2);
    
    return Promises.toList(p1, p2)
        .map(list -> new CombinedResult(list.get(0), list.get(1)));
}
```

### Pattern 5: Error Handling

```java
public Promise<Result> withErrorHandling(String input) {
    return processData(input)
        .whenException(ValidationException.class, e -> {
            logger.warn("Validation failed: {}", e.getMessage());
            return Promise.of(getDefaultResult());
        })
        .whenException(e -> {
            logger.error("Unexpected error", e);
            return Promise.ofException(new ProcessingException(e));
        });
}
```

---

## Testing Patterns

### Pattern 1: Testing Promise-returning methods

```java
@Test
public void testPromiseMethod() {
    // Use CompletableFuture for testing
    Promise<Result> promise = service.getResult("test");
    
    Result result = promise.toCompletableFuture().join();
    
    assertNotNull(result);
    assertEquals("expected", result.getValue());
}
```

### Pattern 2: Testing with Mock Eventloop

```java
@Test
public void testWithEventloop() {
    Eventloop eventloop = Eventloop.create();
    
    eventloop.submit(() -> {
        Promise<Data> promise = service.fetchData("id");
        
        promise.whenResult(data -> {
            assertNotNull(data);
        });
    });
    
    eventloop.run();
}
```

---

## Migration Guide

### From Promise.ofBlocking() to Promise.of()

**Before (Incorrect)**:
```java
public Promise<Node> getNode(String nodeId, String tenantId) {
    return Promise.ofBlocking(() -> 
        storageAdapter.getNode(nodeId, tenantId)
    );
}
```

**After (Correct)**:
```java
public Promise<Node> getNode(String nodeId, String tenantId) {
    try {
        ensureRunning();
        validateTenantId(tenantId);
        Node node = storageAdapter.getNode(nodeId, tenantId);
        return Promise.of(node);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
```

---

## Checklist for Promise Usage

- [ ] Is this a synchronous operation? → Use `Promise.of()`
- [ ] Is this blocking I/O? → Use `Promise.ofBlocking(executor, ...)`
- [ ] Do I have proper error handling? → Use try/catch or `.whenException()`
- [ ] Am I in a test? → Ensure eventloop context or use `.toCompletableFuture()`
- [ ] Do I need parallel execution? → Use `Promises.toList()`
- [ ] Do I need chaining? → Use `.then()` or `.map()`

---

## Common Scenarios

### Scenario 1: Repository Call (Synchronous)

```java
// ✅ CORRECT
public Promise<Entity> findById(String id) {
    try {
        Entity entity = repository.findById(id)
            .orElseThrow(() -> new NotFoundException(id));
        return Promise.of(entity);
    } catch (Exception e) {
        return Promise.ofException(e);
    }
}
```

### Scenario 2: HTTP Client Call (Async)

```java
// ✅ CORRECT - Already returns Promise
public Promise<Response> callExternalApi(String endpoint) {
    return httpClient.request(HttpRequest.get(endpoint))
        .then(response -> {
            if (response.getCode() != 200) {
                return Promise.ofException(
                    new ApiException("Request failed")
                );
            }
            return Promise.of(response);
        });
}
```

### Scenario 3: Database Query (Blocking)

```java
// ✅ CORRECT - Blocking I/O with executor
public Promise<List<Record>> queryDatabase(String sql) {
    return Promise.ofBlocking(dbExecutor, () -> {
        return jdbcTemplate.query(sql, rowMapper);
    });
}
```

---

## Performance Considerations

### Thread Pool Sizing

```java
// Configure blocking executor appropriately
private static final Executor BLOCKING_EXECUTOR = 
    Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors() * 2,
        new ThreadFactoryBuilder()
            .setNameFormat("blocking-io-%d")
            .build()
    );
```

### Avoid Blocking the Eventloop

```java
// ❌ WRONG - Blocks eventloop thread
public Promise<Data> badPattern() {
    Data data = blockingDatabaseCall(); // BLOCKS!
    return Promise.of(data);
}

// ✅ CORRECT - Offloads to executor
public Promise<Data> goodPattern() {
    return Promise.ofBlocking(executor, () -> {
        return blockingDatabaseCall();
    });
}
```

---

## Static Analysis Rules

Add to your build configuration:

```groovy
// build.gradle.kts
tasks.register("checkPromisePatterns") {
    doLast {
        val violations = fileTree("src/main/java")
            .filter { it.name.endsWith(".java") }
            .flatMap { file ->
                file.readLines()
                    .mapIndexed { index, line ->
                        if (line.contains("Promise.ofBlocking()") && 
                            !line.contains("executor")) {
                            "${file.path}:${index + 1}: " +
                            "Promise.ofBlocking() without executor"
                        } else null
                    }
                    .filterNotNull()
            }
        
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Promise pattern violations:\n${violations.joinToString("\n")}"
            )
        }
    }
}
```

---

## References

- [ActiveJ Documentation](https://activej.io/async-programming)
- [Knowledge-Graph Plugin Fix](../../../products/data-cloud/plugins/knowledge-graph/) - Reference implementation
- [Agent-Core Examples](../agent-core/src/main/java/com/ghatana/agent/)

---

## Troubleshooting

### "No reactor in current thread"

**Cause**: Using `Promise.ofBlocking()` without eventloop context

**Fix**: Replace with `Promise.of()` or provide executor

### "Promise never completes"

**Cause**: Blocking operation on eventloop thread

**Fix**: Use `Promise.ofBlocking(executor, ...)` for blocking calls

### Tests fail with Promise timeouts

**Cause**: Missing eventloop in test

**Fix**: Use `.toCompletableFuture().join()` or create test eventloop

---

**Last Updated**: March 26, 2026  
**Maintainer**: Platform Team  
**Next Review**: June 2026
