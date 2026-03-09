# ActiveJ Test Migration - Quick Reference Guide

## Overview

This guide provides a quick reference for migrating tests to use ActiveJ's EventloopTestBase and Promise-based testing patterns.

## When to Migrate a Test

Migrate a test if:

- ✅ It tests a service that returns `Promise<T>`
- ✅ It uses `.get()` or `.toCompletableFuture().get()` on async operations
- ✅ It throws `ExecutionException` or `InterruptedException`
- ✅ It has a custom `block()` helper method for Promises

Skip migration if:

- ❌ Test only uses synchronous methods
- ❌ Test doesn't interact with migrated services
- ❌ Test only validates static utilities or configs

## Migration Pattern

### Step 1: Add Import

```java
import io.activej.test.EventloopTestBase;
```

### Step 2: Extend EventloopTestBase

```java
// Before
class MyServiceTest {

// After
class MyServiceTest extends EventloopTestBase {
```

Or if extending AbstractLanguageTest:

```java
// AbstractLanguageTest already extends EventloopTestBase
// Just use it as before
class MyServiceTest extends AbstractLanguageTest {
```

### Step 3: Update Service Initialization

```java
// Before
private final MyService service = new MyService();

// After
private MyService service;

@BeforeEach
void setUp() {
    service = new MyService(reactor); // reactor from EventloopTestBase
}
```

### Step 4: Update Test Methods

```java
// Before
@Test
void testMethod() throws ExecutionException, InterruptedException {
    var future = service.asyncMethod();
    var result = future.get();
    assertThat(result).isExpected();
}

// After
@Test
void testMethod() {
    var result = runPromise(() -> service.asyncMethod());
    assertThat(result).isExpected();
}
```

## Common Patterns

### Pattern 1: Simple Promise Call

```java
// Before
Result result = service.asyncMethod().get();

// After
Result result = runPromise(() -> service.asyncMethod());
```

### Pattern 2: Promise with Parameters

```java
// Before
List<Diagnostic> diagnostics = service.diagnose(context, files).get();

// After
List<Diagnostic> diagnostics = runPromise(
    () -> service.diagnose(context, files));
```

### Pattern 3: assertDoesNotThrow

```java
// Before
assertDoesNotThrow(() -> service.asyncMethod().get());

// After
assertDoesNotThrow(() -> runPromise(() -> service.asyncMethod()));
```

### Pattern 4: Multiple Async Calls

```java
// Before
var result1 = service.method1().get();
var result2 = service.method2(result1).get();

// After
var result1 = runPromise(() -> service.method1());
var result2 = runPromise(() -> service.method2(result1));
```

### Pattern 5: Mock with Promises

```java
// Before
when(mockService.asyncMethod()).thenReturn(CompletableFuture.completedFuture(value));

// After
when(mockService.asyncMethod()).thenReturn(Promise.of(value));
```

## EventloopTestBase Features

### Provided Fields

- `reactor` - Reactor instance for service initialization
- Access to all JUnit lifecycle methods

### Provided Methods

- `runPromise(() -> promise)` - Execute Promise synchronously in test
- Automatic event loop lifecycle management
- Proper thread handling for async operations

## Examples

### Example 1: Simple Service Test

```java
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SimpleServiceTest extends EventloopTestBase {
    private SimpleService service;

    @BeforeEach
    void setUp() {
        service = new SimpleService(reactor);
    }

    @Test
    void testAsyncOperation() {
        String result = runPromise(() -> service.process("input"));
        assertThat(result).isEqualTo("expected");
    }
}
```

### Example 2: Service with Mock Dependencies

```java
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceWithDependenciesTest extends EventloopTestBase {
    @Mock private Dependency mockDep;
    private MyService service;

    @BeforeEach
    void setUp() {
        service = new MyService(reactor, mockDep);
    }

    @Test
    void testWithMock() {
        when(mockDep.asyncCall()).thenReturn(Promise.of("mocked"));

        String result = runPromise(() -> service.process());

        assertThat(result).isEqualTo("expected");
        verify(mockDep).asyncCall();
    }
}
```

### Example 3: Test with TempDir

```java
import io.activej.test.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class FileServiceTest extends EventloopTestBase {
    @TempDir Path tempDir;
    private FileService service;

    @BeforeEach
    void setUp() {
        service = new FileService(reactor);
    }

    @Test
    void testFileOperation() {
        Path file = tempDir.resolve("test.txt");
        List<String> lines = runPromise(() -> service.readLines(file));
        assertThat(lines).isEmpty();
    }
}
```

## Troubleshooting

### Problem: "reactor cannot be resolved"

**Solution:** Make sure class extends EventloopTestBase

### Problem: Compilation error on runPromise()

**Solution:** Verify import: `import io.activej.test.EventloopTestBase;`

### Problem: Service constructor doesn't accept Reactor

**Solution:** The service needs to be migrated first (Phases 1-3)

### Problem: Test hangs or times out

**Solution:** Check if Promise chain is properly completed. Use `Promise.of()` for immediate values.

### Problem: Multiple matches in oldString

**Solution:** Add more context lines before/after the code to make it unique

## Best Practices

1. **One Test at a Time:** Migrate and verify one test file at a time
2. **Compile After Each File:** Run `./gradlew testClasses` to catch errors early
3. **Read Full File First:** Understand the test structure before migrating
4. **Preserve Test Logic:** Don't change what the test validates, only how it executes async code
5. **Keep Formatting:** Match the original indentation and style
6. **Use Multi-Replace:** For multiple similar changes in one file
7. **Document Issues:** Note any special cases or problems encountered

## Quick Checklist

Before migrating:

- [ ] Read full test file
- [ ] Identify all async method calls
- [ ] Check if service constructor needs reactor
- [ ] Note any custom Promise handling

During migration:

- [ ] Add EventloopTestBase import
- [ ] Extend EventloopTestBase (or use AbstractLanguageTest)
- [ ] Update service initialization
- [ ] Wrap all async calls in runPromise()
- [ ] Remove exception throws clauses
- [ ] Remove custom block() methods

After migration:

- [ ] Compile: `./gradlew :module:testClasses`
- [ ] Review changes visually
- [ ] Update documentation
- [ ] Mark task complete

## Related Files

- Migration Progress: [PHASE_4_PROGRESS_UPDATE.md](PHASE_4_PROGRESS_UPDATE.md)
- Session Summary: [ACTIVEJ_MIGRATION_SESSION_SUMMARY.md](ACTIVEJ_MIGRATION_SESSION_SUMMARY.md)
- Overall Status: [PHASES_1_2_3_COMPLETE.md](PHASES_1_2_3_COMPLETE.md)

## Reference

- EventloopTestBase documentation: ActiveJ Testing Framework
- Promise API: `io.activej.promise.Promise`
- Reactor: `io.activej.reactor.Reactor`

---

_Last Updated: Current Session_  
_Files Migrated Using This Guide: 8_  
_Success Rate: 100% (all compilations successful)_
