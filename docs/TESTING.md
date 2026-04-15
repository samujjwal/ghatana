# Ghatana Testing Standards

> **Owner:** Platform Team | **Status:** Active | **Scope:** All Java and TypeScript modules

---

## 1. Test Categories

Every test must be tagged with exactly one of the four standard categories.

### Unit (`@Tag("unit")`)

**Purpose:** Test individual functions, classes, or components in isolation.

- No external dependencies — databases, HTTP, filesystem, message brokers
- All collaborators mocked or stubbed
- Execution time: milliseconds
- Name pattern: `{Component}Test.java` / `component.test.ts`

### Integration (`@Tag("integration")`)

**Purpose:** Test interactions between multiple components or real external services (via Testcontainers).

- Uses real database (Testcontainers), real message broker, or real HTTP
- Validates data flow across component boundaries
- Execution time: seconds
- Name pattern: `{Component}IntegrationTest.java` / `component.integration.test.ts`

### Contract (`@Tag("contract")`)

**Purpose:** Validate that APIs conform to their OpenAPI/GraphQL specifications.

- Tests request/response schema compliance and error responses
- Uses MockMvc or HTTP client against the API
- Name pattern: `{Service}ContractTest.java`

### End-to-End (`@Tag("e2e")`)

**Purpose:** Test complete user workflows across multiple services.

- Spans multiple services; tests business workflows end-to-end
- Execution time: minutes
- Name pattern: `{Workflow}E2ETest.java` / `workflow.e2e.test.ts`

---

## 2. Java Test Placement

Tests mirror their source files under `src/test/java/`:

```
platform/java/<module>/src/
  main/java/com/ghatana/...   ← production code
  test/java/com/ghatana/...   ← tests (same package, same relative path)
```

**Async tests MUST extend `EventloopTestBase`** from `libs:activej-test-utils`. Never call `.getResult()` directly on ActiveJ promises.

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest extends EventloopTestBase {

    @Mock private Dependency dep;
    private MyService service;

    @BeforeEach
    void setUp() {
        service = new MyService(dep);
        // Use lenient() when the stub is not needed in every test
        lenient().when(dep.validate(any())).thenReturn(Promise.of(ValidationResult.valid()));
    }

    @Test
    void shouldProcessAsync() {
        when(dep.compute(any())).thenReturn(Promise.of("result"));
        String result = runPromise(() -> service.process("input"));
        assertThat(result).isEqualTo("result");
    }
}
```

**Mockito guidelines:**
- Declare shared stubs in `@BeforeEach` with `lenient().when(...)` when not every test needs them (avoids `UnnecessaryStubbingException`)
- Stub every intermediate method in a chained async call — an unstubbed Mockito method returns `null` and propagates as an NPE through `Promise.then()`
- Concrete classes can be `@Mock`ed without extracting an interface when the interface adds no design value

---

## 3. TypeScript / React Test Placement

Tests are co-located inside `__tests__/` subdirectories:

```
src/
  utils.ts
  __tests__/
    utils.test.ts
  components/
    Button.tsx
    __tests__/
      Button.test.tsx
```

Naming: `<SourceFile>.test.ts` / `<SourceFile>.test.tsx`.

---

## 4. TypeScript Test Framework

**Standard framework:** Vitest (`^4.0.0`).

- Unit and integration tests: Vitest
- Browser / E2E tests: Playwright (configured per product)
- React component tests: Vitest + React Testing Library

### Vitest Config Template

```ts
// vitest.config.ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",   // use "jsdom" for DOM/React libraries
    globals: true,
    coverage: {
      provider: "v8",
      reporter: ["text", "lcov"],
      include: ["src/**/*.ts", "src/**/*.tsx"],
      exclude: ["src/**/__tests__/**", "src/index.ts"],
    },
  },
});
```

---

## 5. Coverage Minimums

| Library type | Overall minimum | Critical paths |
|-------------|----------------|----------------|
| Platform libraries | 80% | 90% |
| Product libraries | 70% | 85% |
| Utility packages | 85% | 95% |

> **Critical paths:** authentication flows, data validation, error handling, state transitions.

---

## 6. CI Quality Gates

Every PR must pass:

| Check | Command | Fail condition |
|-------|---------|---------------|
| TypeScript type check | `tsc --noEmit` | Any TypeScript error |
| ESLint | `eslint` + `@ghatana/eslint-plugin` | Any ESLint error |
| Tests | `vitest run` / `./gradlew test` | Any failing test |
| Coverage | `vitest run --coverage` | Below threshold |
| Formatting | `prettier --check` | Any formatting diff |

Run `scripts/coverage-report.sh` to print per-module coverage status with links to HTML reports.
Run `scripts/scan-test-classifications.sh` to surface potentially mislabeled test types.
Run `scripts/test-tiered.sh` to execute tests by tier (unit → integration → contract → e2e).

---

## 7. Test Quality Principles

1. **Test behaviour, not implementation** — test what the function does, not how
2. **Arrange-Act-Assert** — clear structure in every test
3. **Descriptive names** — `"throws ConfigValidationError for invalid NODE_ENV"` not `"test 1"`
4. **One concern per test** — split multi-concern tests into separate `it()` / `@Test` blocks
5. **No hidden dependencies** — each test must be runnable in isolation
6. **Mock at boundaries** — mock `fetch`, databases, timers; never mock the system under test

---

## 8. Common Misclassifications (and Fixes)

### Mock-only test labeled integration

```java
// Wrong: all dependencies are mocked — this is a unit test
@Tag("integration")
class UserServiceIntegrationTest {
    @Mock UserRepository repository;
    @Mock EmailService emailService;
    @Test void shouldCreateUser() { ... }
}
```

**Fix:** Remove `@Tag("integration")`, add `@Tag("unit")`.

### Multi-service workflow labeled integration

```java
// Wrong: spans login → token validation → resource access → logout
@Tag("integration")
class AuthWorkflowIntegrationTest {
    @Test void shouldCompleteFullAuthFlow() { ... }
}
```

**Fix:** Add `@Tag("e2e")`, rename to `AuthWorkflowE2ETest`.

### Real database test labeled unit

```java
// Wrong: uses real Testcontainers database
class UserServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();
    @Test void shouldPersistUser() { ... }
}
```

**Fix:** Add `@Tag("integration")`, optionally move to `integration/` folder.

---

## Related Documents

- [docs/adr/](./adr/) — Architecture Decision Records
- [GOVERNANCE.md](./GOVERNANCE.md) — CI enforcement gates
- `.github/copilot-instructions.md` §4 — ActiveJ async testing details