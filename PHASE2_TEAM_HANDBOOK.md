# Phase 2 Team Execution Handbook

**For**: Platform Engineering Team  
**Date**: April 4, 2026  
**Purpose**: Continue Phase 2 security module tests (38 remaining tests)  
**Time Estimate**: 15 hours (complete by Friday Apr 26)  

---

## 🚀 Quick Start (5 Minutes)

### What You Need to Know

1. **Pattern is established** ✅ — 259 existing security tests prove the pattern works
2. **Templates are ready** ✅ — Copy from `SECURITY_MODULE_TEST_TEMPLATES.md`, adapt, run
3. **Infrastructure is complete** ✅ — SecurityEventloopTestBase, SecurityTestFixture, SecurityMockFactory
4. **Team has full specs** ✅ — Every test method is specified with full implementation

### Success Criteria

- [ ] JWT tests compiled (12 tests)
- [ ] Encryption tests compiled (8 tests)
- [ ] RBAC tests compiled (10 tests)
- [ ] API Key tests compiled (4 tests)
- [ ] Integration tests compiled (4 tests)
- [ ] All 297 tests pass: `./gradlew platform:java:security:test`
- [ ] Zero compilation errors
- [ ] Zero test failures

### Target Timeline

**Week 5** (Apr 22-26): 38 tests complete  
- Monday/Tuesday: JWT tests (12) + Encryption tests (8)
- Wednesday: RBAC tests (10)
- Thursday: API Key tests (4) + Integration tests (4)
- Friday: Build validation + final review

---

## 📋 Implementation Workflow

### Step 1: Copy Template

```bash
# Open SECURITY_MODULE_TEST_TEMPLATES.md
# Navigate to category: JWT/OAuth Tests
# Copy JwtTokenProviderTest template code
```

### Step 2: Create File

```bash
# File path: platform/java/security/src/test/java/com/ghatana/platform/security/
# File name: JwtTokenProviderTest.java
# Paste template
```

### Step 3: Adapt Template

```java
// ✅ DO: Replace class-specific names
class JwtTokenProviderTest extends SecurityEventloopTestBase {
    @Mock private JwtTokenProvider provider;  // ← Your service
    // ...
}

// ✅ DO: Adjust @DisplayName to your test
@DisplayName("JwtTokenProvider")

// ✅ DO: Match test method names to actual behavior
void shouldGenerateValidJwt() { }

// ❌ DON'T: Change the pattern structure (extends SecurityEventloopTestBase, etc.)
// ❌ DON'T: Skip the @doc.* tags on class
// ❌ DON'T: Remove the lenient() mocking setup
```

### Step 4: Compile and Validate

```bash
# Navigate to repo root
cd /Users/samujjwal/Development/ghatana

# Compile tests only (fast feedback)
./gradlew platform:java:security:compileTestJava

# Fix any import errors or compilation issues
# (Files typically needed: your service interface, SecurityTestFixture, SecurityMockFactory)

# Run just the new test file
./gradlew platform:java:security:test --tests "*JwtTokenProvider*"

# Expected output:
# ✅ JwtTokenProviderTest > token generation > should generate... PASSED
# ✅ JwtTokenProviderTest > claim extraction > should extract... PASSED
# Test results: 5 passed, 0 failed
```

### Step 5: Rinse & Repeat

Repeat for TokenRefreshTest (4 tests), TokenRevocationTest (3 tests), etc.

---

## 🎛️ Essential Reference

### SecurityEventloopTestBase (Async Tests)

**Use when**: Testing Promise-based async services

```java
@ExtendWith(MockitoExtension.class)
class MyAsyncTest extends SecurityEventloopTestBase {
    @Mock private AsyncService service;
    
    @Test
    void testAsync() {
        when(service.asyncMethod()).thenReturn(Promise.of("result"));
        
        String result = runPromise(() -> service.asyncMethod());
        
        assertThat(result).isEqualTo("result");
    }
}
```

### SecurityTestFixture (Object Creation)

**Use when**: Building test SecurityContext objects

```java
// Simple context
SecurityContext ctx = SecurityTestFixture.securityContext()
    .userId("user-1")
    .build();

// Admin context
SecurityContext admin = SecurityTestFixture.securityContext()
    .admin()
    .build();

// Custom roles + permissions
SecurityContext custom = SecurityTestFixture.securityContext()
    .userId("user-2")
    .roles("EDITOR", "VIEWER")
    .permissions("read", "write")
    .build();
```

### SecurityMockFactory (Pre-configured Mocks)

**Use when**: Need quick pre-configured SecurityContext objects

```java
// Quick mocks
SecurityContext admin = SecurityMockFactory.adminContext();
SecurityContext user = SecurityMockFactory.userContext();
SecurityContext viewer = SecurityMockFactory.viewerContext();

// Argument matchers for Mockito
ArgumentMatcher<SecurityContext> hasAdminRole = 
    SecurityMockFactory.contextWithRole("ADMIN");

when(service.doSomething(argThat(hasAdminRole)))
    .thenReturn(Promise.of("admin-only-result"));
```

### Mocking Best Practices

```java
@BeforeEach
void setUp() {
    // ✅ Use lenient() for OPTIONAL mocks (not called in every test)
    lenient().when(optionalService.doSomething())
        .thenReturn(Promise.of(default));
    
    // ✅ Use when() for REQUIRED mocks (called in test)
    when(requiredService.doSomething())
        .thenReturn(Promise.of(value));
}

@Test
void testSomething() {
    // ✅ Override when() in specific test
    when(requiredService.doSomething())
        .thenReturn(Promise.of("different-value"));
    
    // Test code...
}
```

### Async Promise Pattern → Don't Forget!

```java
// ❌ WRONG: This will fail or timeout
String result = service.asyncMethod().thenApply(x -> x).get();

// ✅ CORRECT: Always use runPromise()
String result = runPromise(() -> service.asyncMethod());

// ✅ also correct: Chain promises
String result = runPromise(() -> 
    service.method1()
        .then(result1 -> service.method2(result1))
        .then(result2 -> Promise.of(result2))
);
```

### Assertion Patterns (AssertJ)

```java
// ✅ Objects
assertThat(object).isNotNull();
assertThat(object).isEqualTo(expected);

// ✅ Strings
assertThat(str).isNotEmpty();
assertThat(str).startsWith("prefix");
assertThat(str).contains("substring");

// ✅ Collections
assertThat(list).hasSize(5);
assertThat(list).contains("item1", "item2");
assertThat(list).containsExactlyInAnyOrder("a", "b");

// ✅ Optionals (used in SecurityContext tests)
assertThat(optional).isPresent();
assertThat(optional).isEmpty();

// ✅ Numbers (used in key rotation tests)
assertThat(count).isBetween(10, 20);
assertThat(duration).isGreaterThan(5000);

// ✅ Exceptions
assertThatThrownBy(() -> service.throwingMethod())
    .isInstanceOf(SecurityException.class)
    .hasMessageContaining("not authorized");
```

### @DisplayName for Clear Test Names

```java
@DisplayName("JwtTokenProvider")  // Class name
class JwtTokenProviderTest {
    
    @Nested
    @DisplayName("token generation")  // Nested group name
    class TokenGenerationTests {
        
        @Test
        @DisplayName("should generate valid JWT with claims")  // What it tests
        void shouldGenerateValidJwt() { }
    }
}

// Output when test fails:
// JwtTokenProvider > token generation > should generate valid JWT with claims
```

---

## 📁 File Locations Reference

### Source Code (What You're Testing)

```
platform/java/security/src/main/java/com/ghatana/platform/security/
├── JwtTokenProvider.java
├── JwtKeyManager.java
├── TokenBlacklist.java
├── AesGcmEncryptionProvider.java
├── RoleAssignmentService.java
├── PermissionEvaluator.java
└── ... (67 total production classes)
```

### Test Files (What You're Creating)

```
platform/java/security/src/test/java/com/ghatana/platform/security/
├── base/
│   └── SecurityEventloopTestBase.java
├── fixtures/
│   ├── SecurityTestFixture.java
│   └── SecurityMockFactory.java
├── JwtTokenProviderTest.java  ← Create this
├── TokenRefreshTest.java       ← Create this
├── TokenRevocationTest.java    ← Create this
├── AesGcmEncryptionTest.java   ← Create this
├── KeyRotationTest.java        ← Create this
├── RoleAssignmentTest.java     ← Create this
├── PermissionEvaluatorTest.java ← Create this
├── PolicyEnforcementTest.java  ← Create this
└── SecurityContextTest.java    ← ALREADY DONE ✅
```

### Command Reference

```bash
# Navigate to repo
cd /Users/samujjwal/Development/ghatana

# Compile just test code (no production changes)
./gradlew platform:java:security:compileTestJava

# Run all security tests
./gradlew platform:java:security:test

# Run specific test class
./gradlew platform:java:security:test --tests "*JwtTokenProvider*"

# Run specific nested test
./gradlew platform:java:security:test --tests "*JwtTokenProvider*TokenGeneration*"

# View test output
./gradlew platform:java:security:test --console=plain

# Fresh build (if stuck)
./gradlew clean platform:java:security:test
```

---

## ⚠️ Common Mistakes & Fixes

### Mistake 1: Forgetting to extend SecurityEventloopTestBase

```java
// ❌ WRONG: For async tests
class MyAsyncTest {
    @Test
    void test() { }
}

// ✅ CORRECT: For async tests
class MyAsyncTest extends SecurityEventloopTestBase {
    @Test
    void test() { }
}
```

**Fix**: Add ` extends SecurityEventloopTestBase`

### Mistake 2: Not importing SecurityTestFixture/SecurityMockFactory

```java
// ❌ WRONG: Does not compile
SecurityContext ctx = SecurityTestFixture.securityContext().build();

// ✅ CORRECT: Add imports
import com.ghatana.platform.security.fixtures.SecurityTestFixture;
import com.ghatana.platform.security.fixtures.SecurityMockFactory;
```

**Fix**: Copy imports from template

### Mistake 3: Forgetting runPromise() for async

```java
// ❌ WRONG: Hangs or fails
Promise<String> result = runPromise(() -> service.asyncCall());

// ✅ CORRECT: unwrap the promise
String result = runPromise(() -> service.asyncCall());
```

**Fix**: Remove `Promise<>` wrapper around runPromise()

### Mistake 4: Removing @Mock or lenient()

```java
// ❌ WRONG: NullPointerException or UnnecessaryStubbingException
@BeforeEach
void setUp() {
    // missing @Mock or lenient when(...).thenReturn(...)
}

// ✅ CORRECT: Declare mocks and stub optional ones
@Mock private Service service;

@BeforeEach
void setUp() {
    lenient().when(service.optional()).thenReturn(Promise.of(default));
}
```

**Fix**: Keep @Mock, add lenient() for optional stubs

### Mistake 5: Missing @doc.* tags

```java
// ❌ WRONG: Fails documentation checks
class JwtTokenProviderTest { }

// ✅ CORRECT: Add @doc tags
/**
 * @doc.type class
 * @doc.purpose Tests for JWT token generation and validation.
 * @doc.layer platform
 * @doc.pattern Test
 */
class JwtTokenProviderTest { }
```

**Fix**: Copy @doc tags from template

---

## 🎯 Category Implementation Order (Recommended)

### Day 1 (Monday): JWT Tests (12 tests)
**Time**: 4 hours  
**Files**: JwtTokenProviderTest (5), TokenRefreshTest (4), TokenRevocationTest (3)
```bash
# Copy all 3 test files from SECURITY_MODULE_TEST_TEMPLATES.md
# Validate: ./gradlew platform:java:security:test --tests "*Jwt*"
# Expected: 12 passed
```

### Day 2 (Tuesday): Encryption Tests (8 tests)
**Time**: 3 hours  
**Files**: AesGcmEncryptionTest (4), KeyRotationTest (4)
```bash
# Copy 2 test files from templates
# Validate: ./gradlew platform:java:security:test --tests "*Encryption|*Rotation*"
# Expected: 8 passed
```

### Day 3 (Wednesday): RBAC Tests (10 tests)
**Time**: 4 hours  
**Files**: RoleAssignmentTest (3), PermissionEvaluatorTest (4), PolicyEnforcementTest (3)
```bash
# Copy 3 test files from templates
# Validate: ./gradlew platform:java:security:test --tests "*Role*|*Permission*|*Policy*"
# Expected: 10 passed
```

### Day 4 (Thursday): API Key + Integration Tests (8 tests)
**Time**: 4 hours  
**Files**: ApiKeyTests (4), IntegrationTests (4)
```bash
# Implement using same pattern from templates
# Validate: ./gradlew platform:java:security:test --tests "*ApiKey|*Integration*"
# Expected: 8 passed
```

### Day 5 (Friday): Final Validation
**Time**: 1 hour  
**Task**: Run full test suite
```bash
# Full validation
./gradlew platform:java:security:test

# Expected output:
# Test results: 297 total, 297 passed, 0 failed, 0 skipped
# BUILD SUCCESSFUL in 16s
```

---

## ✅ Final Validation Checklist

Before marking week complete:

- [ ] All 38 test files created
- [ ] All files compile: `./gradlew platform:java:security:compileTestJava` ✅
- [ ] All tests pass: `./gradlew platform:java:security:test` → 297 passing ✅
- [ ] Zero compilation errors
- [ ] Zero test failures
- [ ] All test classes have @doc.* tags
- [ ] All async tests extend SecurityEventloopTestBase
- [ ] All fixtures use SecurityTestFixture/SecurityMockFactory
- [ ] Code review ready (no console warnings)

---

## 🆘 Troubleshooting

### Tests Won't Compile

```
Error: Cannot find symbol: SecurityEventloopTestBase
→ Add: import com.ghatana.platform.testing.activej.EventloopTestBase;

Error: Cannot find symbol: SecurityTestFixture
→ Add: import com.ghatana.platform.security.fixtures.SecurityTestFixture;

Error: Cannot find symbol: SecurityMockFactory
→ Add: import com.ghatana.platform.security.fixtures.SecurityMockFactory;
```

### Tests Timeout

```
runPromise(() -> service.method()) hangs
→ Check: Does SecurityEventloopTestBase exist and is test extending it?
→ Check: Is mocked service actually returning a Promise?
→ Check: Is there an infinite loop in the async code?
```

### Mocking Errors

```
UnnecessaryStubbingException: service.optional() was not invoked
→ Use: lenient().when(service.optional()).thenReturn(...)

NullPointerException in test
→ Check: Did you mock all dependencies required by the service?
→ Check: Did you stub all Promise methods the service calls?
```

### Import Errors

```
Cannot resolve symbol XYZ
→ Run: ./gradlew platform:java:security:compileTestJava (full output shows missing imports)
→ Check: SECURITY_MODULE_TEST_TEMPLATES.md for required imports
```

---

## 📞 Support Resources

**Documentation**:
- `SECURITY_MODULE_TEST_TEMPLATES.md` — Full test specifications
- `SECURITY_MODULE_WEEK5_PROGRESS.md` — Build validation results
- `PHASE2_SESSION_SUMMARY.md` — Architecture & pattern overview
- `copilot-instructions.md` — Ghatana standards reference

**Key Files**:
- `/platform/java/security/src/test/java/com/ghatana/platform/security/base/SecurityEventloopTestBase.java` — extends from
- `/platform/java/security/src/test/java/com/ghatana/platform/security/fixtures/SecurityTestFixture.java` — create contexts with
- `/platform/java/security/src/test/java/com/ghatana/platform/security/fixtures/SecurityMockFactory.java` — pre-configured mocks

**Build Commands**:
```bash
# Fast validation (just compilation)
./gradlew platform:java:security:compileTestJava

# Run tests (full validation)
./gradlew platform:java:security:test

# Run specific tests
./gradlew platform:java:security:test --tests "TestClassName"

# Fresh build (if stuck)
./gradlew clean platform:java:security:test
```

---

## 🎓 Learning Path (If New to Ghatana)

1. **Read**: `copilot-instructions.md` → 5 minutes
   - Understand Ghatana conventions
   - Know type safety requirements
   - Learn async pattern (runPromise)

2. **Read**: `SECURITY_MODULE_TEST_TEMPLATES.md` → 15 minutes
   - See full test specifications
   - Understand fixture/mock patterns
   - Copy template structure

3. **Practice**: Create JwtTokenProviderTest → 1 hour
   - Follow template exactly
   - Compile and run
   - Validate 5 tests pass

4. **Repeat**: Use same pattern for 33 remaining tests
   - Copy → Create → Compile → Validate
   - Same pattern, different class/method names

---

## 🚀 You're Ready!

Everything is ready:
- ✅ Patterns established (259 tests validate it)
- ✅ Templates provided (copy-paste specs)
- ✅ Infrastructure complete (base classes, fixtures)
- ✅ Build validated (zero errors)
- ✅ Documentation comprehensive

**Go implement the remaining 38 tests!** 🎯

---

**Created**: April 4, 2026  
**For**: Platform Engineering Team  
**Timeline**: Complete by Friday Apr 26  
**Target**: 297 total security tests passing ✅

