# AI Agent Instructions for the Ghatana Codebase

**Last Updated:** 2025-11-05  
**Version:** 2.1.0

> **IMPORTANT**: These instructions are the single source of truth for all development in this repository. All code must follow these guidelines exactly.

## Code Quality Requirements

1. **Type Safety**
   - All code must be fully type-safe according to the language's type system
   - No use of `any` or similar type-escape hatches without explicit justification
   - Use strict null checking and proper type guards

2. **Linting & Static Analysis**
   - Code must pass all linter rules with zero warnings or errors
   - No disabled or suppressed linter rules without explicit justification
   - Follow project-specific style guidelines for consistent formatting

3. **Compilation & Build**
   - Code must compile without warnings or errors
   - All type checks must pass during build
   - Dependencies must be properly declared and versioned

These instructions capture project-specific knowledge so AI coding agents can be productive immediately in this repository.

## Big Picture Architecture

### Core Principles

1. **Reuse-First Architecture**
   - Always check `core/*` and `libs/*` before implementing new functionality
   - Prefer composition over inheritance
   - Favor small, focused modules with clear responsibilities

2. **Layered Architecture**

   ```
   API Layer (HTTP/RPC endpoints)
      ↓
   Application Layer (Use Cases, Services)
      ↓
   Domain Layer (Entities, Value Objects, Ports)
      ↓
   Infrastructure Layer (Adapters, Repositories)
   ```

3. **Module Organization**
   - `core/` - Reusable platform components
   - `libs/` - Shared libraries and utilities
   - `products/` - Product-specific implementations
   - `contracts/` - API contracts and schemas
   - `testing/` - Test utilities and infrastructure

4. **Dependency Flow**
   - Dependencies must only flow downward
   - No circular dependencies between modules
   - Use interfaces for cross-module communication
   - All external dependencies must be declared in `gradle/libs.versions.toml`

### UI Architecture

1. **Component Organization**
   - Follow atomic design principles (atoms → molecules → organisms → templates → pages)
   - Shared UI components in `libs/ui-components`
   - Feature components in `**/{feature-name}/components`
   - Keep components small and focused (max 200-300 lines)

2. **State Management**
   - **Server State**: Use React Query (TanStack Query)
   - **App State**: Use Jotai for app-scoped state
   - **Local State**: Use React's `useState` and `useReducer`
   - **Colocation**: Keep state close to where it's used
   - **Feature Stores**: Store Jotai atoms in `**/{feature}/stores`

3. **Styling**
   - Use Tailwind CSS with design tokens from `libs/design-system`
   - Use `clsx` for conditional class names
   - Follow BEM naming for custom CSS modules
   - Use CSS variables for theming

4. **Project Structure**

   ```
   src/
     **/              # Feature modules
       feature-name/
         components/       # Feature components
         hooks/            # Custom hooks
         stores/           # Jotai stores (app-scoped)
           store-name.store.ts
         types/            # TypeScript types
         index.ts          # Public API

     lib/                  # Application-specific shared code
       hooks/              # Shared hooks
       utils/             # Utility functions
       services/          # API clients and services
   ```

## 🎨 UI Development Guidelines

### Jotai State Management (App-Scoped)

#### Core Principles

- **App Scope**: State is scoped to the application instance
- **Feature-Centric**: Group state by feature/module
- **Colocation**: Keep state close to where it's used
- **Derivation**: Use derived atoms for computed state
- **Atomic Updates**: Keep state updates focused and predictable

#### Feature-Based State Structure

```
src/
  **/
    auth/
      stores/
        auth.store.ts     # Auth-related state
        session.store.ts  # Session management
    cart/
      stores/
        cart.store.ts     # Shopping cart state
        checkout.store.ts # Checkout process
    user/
      stores/
        profile.store.ts  # User profile state
        preferences.store.ts # User preferences
```

#### Example: Feature Store

```typescript
// **/auth/stores/auth.store.ts
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Base auth state
type AuthState = {
  user: User | null;
  status: 'idle' | 'loading' | 'authenticated' | 'unauthenticated';
  error: string | null;
};

const initialState: AuthState = {
  user: null,
  status: 'idle',
  error: null,
};

// Core atom
export const authAtom = atom<AuthState>(initialState);

// Derived atoms
export const isAuthenticatedAtom = atom(
  (get) => get(authAtom).status === 'authenticated'
);

export const currentUserAtom = atom((get) => get(authAtom).user);

// Action atoms
export const loginAtom = atom(
  null,
  async (get, set, credentials: LoginCredentials) => {
    set(authAtom, {
      ...get(authAtom),
      status: 'loading',
      error: null,
    });

    try {
      const user = await authService.login(credentials);
      set(authAtom, {
        user,
        status: 'authenticated',
        error: null,
      });
      return user;
    } catch (error) {
      set(authAtom, {
        ...get(authAtom),
        status: 'unauthenticated',
        error: error.message,
      });
      throw error;
    }
  }
);

// Reset auth state
export const logoutAtom = atom(null, (get, set) => {
  authService.logout();
  set(authAtom, initialState);
});
```

#### Using Jotai in Components

```tsx
// **/auth/components/LoginForm.tsx
import { useAtom } from 'jotai';
import { loginAtom, isAuthenticatedAtom } from '../stores/auth.store';

function LoginForm() {
  const [login, loginMutation] = useAtom(loginAtom);
  const [isAuthenticated] = useAtom(isAuthenticatedAtom);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login({ email, password });
      // Redirect on success handled by parent
    } catch (error) {
      // Error is handled in the atom
    }
  };

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return <form onSubmit={handleSubmit}>{/* Form fields */}</form>;
}
```

#### Testing Jotai Stores

```typescript
// **/auth/__tests__/auth.store.test.ts
import { Provider } from "jotai";
import { renderHook, act } from "@testing-library/react";
import { authAtom, loginAtom } from "../stores/auth.store";

const wrapper = ({ children }) => (
  <Provider
    initialValues={[[authAtom, { user: null, status: "idle", error: null }]]}
  >
    {children}
  </Provider>
);

describe("auth store", () => {
  it("handles login success", async () => {
    const { result } = renderHook(() => useAtom(loginAtom), { wrapper });

    await act(async () => {
      await result.current[1]({
        email: "test@example.com",
        password: "password",
      });
    });

    expect(result.current[0].status).toBe("authenticated");
  });
});
```

#### Best Practices

1. **Naming Conventions**
   - Suffix atoms with `.atom.ts`
   - Use `camelCase` for atom names
   - Prefix derived atoms with the source atom name (e.g., `userProfileAtom` → `userProfileLoadingAtom`)

2. **Performance**
   - Use `atomFamily` for lists of items
   - Memoize selectors with `useMemo` when needed
   - Use `useAtomValue` for read-only access

3. **Testing**
   - Test atoms in isolation with `@jotai/test-utils`
   - Mock external dependencies
   - Test async flows with `waitFor`

### Component Patterns

#### Presentational Components

- Keep components dumb and stateless when possible
- Accept all data and callbacks as props
- Use TypeScript interfaces for prop types

#### Container Components

- Manage state and side effects
- Compose presentational components
- Handle data fetching and state updates

### Styling Guidelines

#### Tailwind CSS

- Use `@apply` sparingly in CSS modules
- Extract repeated patterns to components
- Use design tokens from `libs/design-system`

#### CSS Modules

- Use for complex component-specific styles
- Follow BEM naming convention
- Use `composes` for style composition

### Testing

#### Unit Tests

- Test components in isolation
- Mock external dependencies
- Use Testing Library for component tests

#### Integration Tests

- Test component interactions
- Verify state updates
- Mock API responses with MSW

### Performance

#### Code Splitting

- Use `React.lazy` for route-based code splitting
- Prefetch critical resources
- Use `Suspense` for loading states

#### Memoization

- `React.memo` for expensive renders
- `useMemo` for expensive calculations
- `useCallback` for stable function references

### Accessibility

#### Semantic HTML

- Use proper heading hierarchy
- Add ARIA attributes when needed
- Ensure keyboard navigation works

#### Testing

- Use `@testing-library/user-event` for interactions
- Run accessibility audits with `@axe-core/react`
- Test with screen readers

4. **Testing**
   - Component tests with React Testing Library
   - Integration tests with Cypress
   - Visual regression tests with Storybook + Chromatic

- Build conventions and configurations are centralized in the root `build.gradle.kts`. Individual module build files should only contain module-specific dependencies and configurations.
- Hexagonal style: domain models and ports in `core/*`, adapters in service/product modules. Prefer reusing `core/*` and `contracts/*` over reinventing.
- HTTP implementation: ActiveJ HTTP is the platform standard. Modules may use ActiveJ HTTP **types** directly (HttpRequest, HttpResponse) but MUST use `core/http-server` abstractions for **operations** (HttpServerBuilder, ResponseBuilder, RoutingServlet) to ensure platform-level control over security, observability, and business logic.
- Events and schemas: protobuf contracts in `contracts/proto`; JSON event schemas in `yappc/schemas/events/v1`. Domain models moved to `core/domain-models`.
- Observability: Micrometer + OpenTelemetry are the platform standards. Modules may use Micrometer **types** directly but MUST use `core/observability` abstractions for **standard operations** (MetricsCollector, MetricsCollectorFactory) to ensure consistent patterns and platform control. Local stack in `docker-compose.observability.yml`.
- Logging uses Log4j2 with SLF4J bridge. Logback should not be used anywhere in the project.

## Critical Workflows

### Development Workflow

1. **Local Development**

   ```bash
   # Start backend services
   ./gradlew :products:my-product:bootRun

   # Start frontend dev server
   cd products/my-product/frontend
   pnpm dev
   ```

2. **Building**

   ```bash
   # Build everything
   ./gradlew clean build

   # Build specific module
   ./gradlew :products:my-product:build
   ```

3. **Testing**

   ```bash
   # Run all tests
   ./gradlew test

   # Run specific test class
   ./gradlew :products:my-product:test --tests "*.MyTestClass"

   # Run with coverage
   ./gradlew jacocoTestReport
   ```

4. **Code Quality**

   ```bash
   # Format code
   ./gradlew spotlessApply

   # Check code style
   ./gradlew checkstyleMain checkstyleTest

   # Run static analysis
   ./gradlew pmdMain pmdTest
   ```

5. **Dependency Management**

   ```bash
   # Check for dependency updates
   ./gradlew dependencyUpdates

   # Update version catalog
   ./gradlew updateVersionCatalog
   ```

- Unit tests: `./gradlew test`; coverage: `./gradlew jacocoTestReport`. Some integration tests rely on Testcontainers.
- Architecture tests: `./gradlew :testing:architecture-tests:test` to verify architecture boundaries (ArchUnit rules). This validates that product modules use core abstractions instead of direct third-party imports.
- Code style and quality: `./gradlew spotlessApply` (format). Checkstyle/PMD/SpotBugs are configured in the root build.
- Version catalog and dependency updates: `./gradlew allDeps` and `./gradlew updateVersionCatalog` (safe no-op if plugin is missing).
- Observability stack: `docker compose -f docker-compose.observability.yml up -d` to run Jaeger, Prometheus, Grafana locally.
- To add a new module:
  1. Add it to the root `settings.gradle.kts`
  2. Create a minimal `build.gradle` file with just the module's specific dependencies
  3. All standard configurations will be inherited from the root build

## Build System Conventions

### Dependencies

1. **Version Catalog**
   - All dependencies must be declared in `gradle/libs.versions.toml`
   - Use version references (e.g., `libs.versions.kotlin`)
   - Group related dependencies in the catalog

2. **Module Dependencies**
   - Only declare project-specific dependencies in module `build.gradle` files
   - Inherit common configurations from root build
   - Use `api` for compile-time dependencies
   - Use `implementation` for runtime dependencies
   - Use `compileOnly` for compile-time only dependencies
   - Use `testImplementation` for test dependencies

3. **Plugin Management**
   - Declare plugins in `gradle/libs.versions.toml`
   - Apply plugins using `plugins { id("plugin.id") }`
   - Configure plugins in the root build script

### Code Style

1. **Kotlin**
   - Follow official Kotlin style guide
   - Use `ktlint` with `ktlint-gradle`
   - Max line length: 120 characters

2. **Java**
   - Follow Google Java Style Guide
   - Use 4 spaces for indentation
   - Max line length: 120 characters

3. **TypeScript/JavaScript**
   - Use TypeScript for all new code
   - Follow `@typescript-eslint` rules
   - Use Prettier for formatting

### Testing

1. **Unit Tests**
   - Place in `src/test/kotlin` or `src/test/java`
   - Use JUnit 5 with AssertJ
   - Follow Arrange-Act-Assert pattern

2. **Integration Tests**
   - Place in `src/integrationTest`
   - Use Testcontainers for external services
   - Annotate with `@IntegrationTest`

3. **UI Tests**
   - Use React Testing Library
   - Follow Testing Library best practices
   - Mock external dependencies

## 🧪 Java Testing Patterns (MANDATORY for ALL tests)

### Core Testing Requirements

All Java tests MUST follow these patterns from `copilot-instructions.md`:

1. **Test Base Classes**
   - ✅ MUST extend `EventloopTestBase` from `libs:activej-test-utils` for ALL tests using ActiveJ Promises
   - ✅ MUST use `runPromise(() -> promise)` instead of `.getResult()`
   - ❌ NEVER call `.getResult()` on Promises in tests (causes NullPointerException)

2. **Test Structure**
   - ✅ MUST use GIVEN-WHEN-THEN structure with comments
   - ✅ MUST use `@DisplayName` on test classes and methods
   - ✅ MUST use descriptive test method names: `should[ExpectedOutcome]When[Condition]()`
   - ✅ MUST include comprehensive JavaDoc explaining what is being tested

3. **Assertions**
   - ✅ MUST use AssertJ fluent assertions
   - ✅ MUST use `.as("description")` for clear failure messages
   - ✅ MUST test both success and error cases
   - ✅ MUST test edge cases and boundary conditions

4. **Test Data**
   - ✅ MUST use TestDataBuilders for domain object construction
   - ✅ MUST NOT hardcode test data in test methods
   - ✅ MUST use sensible defaults with overrides only for specific test cases

5. **Documentation**
   - ✅ MUST document GIVEN/WHEN/THEN in JavaDoc
   - ✅ MUST explain WHY not WHAT in comments
   - ✅ MUST reference production classes being tested

### ActiveJ Promise Testing (CRITICAL)

**Problem**: ActiveJ Promises require an Eventloop to execute. Calling `.getResult()` in a test thread without eventloop context causes `NullPointerException`.

**Solution**: Use `EventloopTestBase` from `libs:activej-test-utils`.

**✅ CORRECT Pattern:**

```java
package com.ghatana.products.collection.infrastructure.persistence;

import com.ghatana.testing.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JpaCollectionRepositoryImpl.
 *
 * Tests validate:
 * - Collection CRUD operations
 * - Tenant isolation
 * - Active/inactive filtering
 * - Promise-based async operations
 *
 * @see JpaCollectionRepositoryImpl
 */
@DisplayName("JPA Collection Repository Tests")
class JpaCollectionRepositoryImplTest extends EventloopTestBase {

    private JpaCollectionRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // GIVEN: Repository with mocked dependencies
        EntityManager entityManager = mock(EntityManager.class);
        MetricsCollector metrics = NoopMetricsCollector.getInstance();
        repository = new JpaCollectionRepositoryImpl(entityManager, metrics);
    }

    /**
     * Verifies that save operation persists collection and returns it.
     *
     * GIVEN: A valid MetaCollection
     * WHEN: save() is called
     * THEN: Collection is persisted and returned with same properties
     */
    @Test
    @DisplayName("Should persist collection when save is called")
    void shouldPersistCollectionWhenSaveIsCalled() {
        // GIVEN: Valid collection
        MetaCollection collection = TestDataBuilders.metaCollection()
            .tenantId("tenant-123")
            .name("products")
            .build();

        // WHEN: Save is called using runPromise()
        MetaCollection saved = runPromise(() -> repository.save(collection));

        // THEN: Collection is persisted
        assertThat(saved)
            .as("Saved collection should not be null")
            .isNotNull();
        assertThat(saved.getTenantId())
            .as("Tenant ID should match input")
            .isEqualTo("tenant-123");
        assertThat(saved.getName())
            .as("Name should match input")
            .isEqualTo("products");
    }

    /**
     * Verifies tenant isolation - collections from different tenants are isolated.
     *
     * GIVEN: Collections for different tenants
     * WHEN: findByTenantId() is called
     * THEN: Only collections for requested tenant are returned
     */
    @Test
    @DisplayName("Should enforce tenant isolation when retrieving collections")
    void shouldEnforceTenantIsolationWhenRetrievingCollections() {
        // GIVEN: Collections for two tenants
        MetaCollection tenant1Collection = TestDataBuilders.metaCollection()
            .tenantId("tenant-1")
            .name("tenant1-collection")
            .build();
        MetaCollection tenant2Collection = TestDataBuilders.metaCollection()
            .tenantId("tenant-2")
            .name("tenant2-collection")
            .build();

        runPromise(() -> repository.save(tenant1Collection));
        runPromise(() -> repository.save(tenant2Collection));

        // WHEN: Retrieve collections for tenant-1
        List<MetaCollection> tenant1Collections = runPromise(() ->
            repository.findByTenantId("tenant-1"));

        // THEN: Only tenant-1 collections are returned
        assertThat(tenant1Collections)
            .as("Should return collections for tenant-1 only")
            .hasSize(1)
            .extracting(MetaCollection::getTenantId)
            .containsOnly("tenant-1");
    }

    @AfterEach
    void tearDown() {
        // Cleanup if needed
        runPromise(() -> repository.close());
    }
}
```

**❌ WRONG Pattern (Causes NullPointerException):**

```java
// ❌ WRONG - Missing EventloopTestBase
class JpaCollectionRepositoryImplTest {

    @Test
    void shouldSaveCollection() {
        MetaCollection collection = new MetaCollection();

        // ❌ WRONG - .getResult() fails without eventloop context
        MetaCollection saved = repository.save(collection).getResult(); // NPE!

        assertThat(saved).isNotNull();
    }
}
```

**Why EventloopTestBase is Required:**

- ActiveJ Promises need an Eventloop to execute asynchronous operations
- Test threads don't have an Eventloop by default
- `EventloopTestBase` provides a managed Eventloop per test with automatic lifecycle
- `runPromise(() -> promise)` executes the Promise in the test's Eventloop and waits for completion

**Dependency:**

```gradle
dependencies {
    testImplementation project(':libs:activej-test-utils')
}
```

**Available Helpers from EventloopTestBase:**

- `runPromise(() -> promise)` - Execute Promise, return result
- `runBlocking(() -> code)` - Run code in eventloop thread
- `eventloop()` - Access underlying eventloop
- `eventloopDelayMillis(ms)` - Create delay Promise

### Test Data Builders Pattern

Create centralized test data builders to avoid duplication across tests.

**Example: TestDataBuilders.java**

```java
package com.ghatana.products.collection.infrastructure.test;

import com.ghatana.products.collection.domain.model.MetaCollection;
import java.util.UUID;
import java.time.Instant;

/**
 * Test data builders for collection domain objects.
 *
 * <p>Provides fluent API for constructing test objects with sensible defaults.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetaCollection collection = TestDataBuilders.metaCollection()
 *     .tenantId("tenant-123")
 *     .name("products")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Test data construction utilities
 * @doc.layer product
 * @doc.pattern Builder
 */
public final class TestDataBuilders {

    private TestDataBuilders() {
        // Utility class - no instantiation
    }

    /**
     * Creates a MetaCollection builder with sensible defaults.
     *
     * @return builder instance
     */
    public static MetaCollectionBuilder metaCollection() {
        return new MetaCollectionBuilder();
    }

    /**
     * Builder for MetaCollection test objects.
     *
     * <p>Provides sensible defaults for all fields. Override only what's needed
     * for specific test cases.
     */
    public static class MetaCollectionBuilder {
        private UUID id = UUID.randomUUID();
        private String tenantId = "tenant-test";
        private String name = "test-collection";
        private String description = "Test collection description";
        private boolean isActive = true;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public MetaCollectionBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public MetaCollectionBuilder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public MetaCollectionBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MetaCollectionBuilder description(String description) {
            this.description = description;
            return this;
        }

        public MetaCollectionBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public MetaCollectionBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public MetaCollectionBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Builds MetaCollection instance.
         *
         * <p>NOTE: MetaCollection uses no-arg constructor + setters, NOT builder pattern.
         *
         * @return configured MetaCollection instance
         */
        public MetaCollection build() {
            MetaCollection collection = new MetaCollection();
            collection.setId(id);
            collection.setTenantId(tenantId);
            collection.setName(name);
            collection.setDescription(description);
            collection.setActive(isActive);
            collection.setCreatedAt(createdAt);
            collection.setUpdatedAt(updatedAt);
            return collection;
        }
    }
}
```

**Usage in Tests:**

```java
// Minimal setup - use defaults
MetaCollection collection = TestDataBuilders.metaCollection().build();

// Override only what's needed
MetaCollection customCollection = TestDataBuilders.metaCollection()
    .tenantId("tenant-prod")
    .name("customers")
    .isActive(false)
    .build();
```

### Mocking Patterns

**MetricsCollector Mocking:**

```java
import com.ghatana.observability.NoopMetricsCollector;

// ✅ CORRECT - Use NoopMetricsCollector for tests
MetricsCollector metrics = NoopMetricsCollector.getInstance();

// ❌ WRONG - Don't mock getMeterRegistry() (doesn't exist)
// ❌ WRONG - Don't mock recordTimer() (doesn't exist)
```

**Repository Mocking:**

```java
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// Mock repository
CollectionRepository repository = mock(CollectionRepository.class);

// Setup behavior
when(repository.findById(any(UUID.class)))
    .thenReturn(Promise.of(Optional.of(collection)));

// Verify interactions
verify(repository, times(1)).save(any(MetaCollection.class));
```

### Test Categories

**1. Persistence Layer Tests**

```java
/**
 * Tests JPA repository CRUD operations.
 *
 * Tests validate:
 * - Save/update operations
 * - Retrieval by ID, tenant, filters
 * - Soft delete behavior
 * - Tenant isolation
 * - Transaction boundaries
 */
@DisplayName("JPA Collection Repository Tests")
class JpaCollectionRepositoryImplTest extends EventloopTestBase {
    // Tests here
}
```

**2. Cache Layer Tests**

```java
/**
 * Tests Redis cache adapter operations.
 *
 * Tests validate:
 * - Cache hit/miss scenarios
 * - TTL expiration
 * - Cache invalidation
 * - Tenant-scoped cache keys
 * - Serialization/deserialization
 */
@DisplayName("Redis Collection Cache Tests")
class RedisCollectionCacheAdapterTest extends EventloopTestBase {
    // Tests here
}
```

**3. HTTP Adapter Tests**

```java
/**
 * Tests HTTP collection endpoints.
 *
 * Tests validate:
 * - GET/POST/PUT/DELETE operations
 * - Request validation
 * - Error responses
 * - Tenant extraction from headers
 * - Response serialization
 */
@DisplayName("Collection HTTP Adapter Tests")
class CollectionHttpAdapterTest extends EventloopTestBase {
    // Tests here
}
```

**4. Service Layer Tests**

```java
/**
 * Tests collection service business logic.
 *
 * Tests validate:
 * - Business rule enforcement
 * - Orchestration across repositories/caches
 * - Error handling and recovery
 * - Metrics collection
 * - Event publishing
 */
@DisplayName("Collection Service Tests")
class CollectionServiceImplTest extends EventloopTestBase {
    // Tests here
}
```

### Common Anti-Patterns to Avoid

❌ **Don't test private methods directly**

```java
// ❌ WRONG
@Test
void testPrivateMethod() {
    Method method = MyClass.class.getDeclaredMethod("privateMethod");
    method.setAccessible(true);
    method.invoke(instance);
}

// ✅ CORRECT - Test via public API
@Test
void shouldBehaviorWhenCondition() {
    String result = instance.publicMethod();
    assertThat(result).isEqualTo("expected");
}
```

❌ **Don't hardcode test data in methods**

```java
// ❌ WRONG
@Test
void shouldSaveCollection() {
    MetaCollection collection = new MetaCollection();
    collection.setTenantId("tenant-123");
    collection.setName("products");
    collection.setDescription("Product catalog");
    // ... 10 more setters
}

// ✅ CORRECT - Use TestDataBuilders
@Test
void shouldSaveCollection() {
    MetaCollection collection = TestDataBuilders.metaCollection()
        .tenantId("tenant-123")
        .name("products")
        .build();
}
```

❌ **Don't call .getResult() without EventloopTestBase**

```java
// ❌ WRONG - Causes NullPointerException
@Test
void shouldProcessAsync() {
    String result = service.processAsync("input").getResult(); // NPE!
}

// ✅ CORRECT - Use runPromise()
@Test
void shouldProcessAsync() {
    String result = runPromise(() -> service.processAsync("input"));
}
```

❌ **Don't mock what you don't own**

```java
// ❌ WRONG - Mocking third-party library internals
MeterRegistry registry = mock(MeterRegistry.class);
when(registry.counter(...)).thenReturn(...);

// ✅ CORRECT - Use NoopMetricsCollector
MetricsCollector metrics = NoopMetricsCollector.getInstance();
```

### Test Execution Commands

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew :products:collection:infrastructure:test --tests "JpaCollectionRepositoryImplTest"

# Run specific test method
./gradlew :products:collection:infrastructure:test --tests "JpaCollectionRepositoryImplTest.shouldSaveCollection"

# Run with coverage
./gradlew jacocoTestReport

# Run only fast tests (no integration)
./gradlew test -x integrationTest
```

### Test Documentation Checklist

Before marking test rewrite complete:

- [ ] Extends `EventloopTestBase` for ActiveJ Promise tests
- [ ] Uses `runPromise()` for all async operations
- [ ] Uses `TestDataBuilders` for object construction
- [ ] Follows GIVEN-WHEN-THEN structure with comments
- [ ] Uses `@DisplayName` on class and methods
- [ ] Uses AssertJ fluent assertions with `.as()` descriptions
- [ ] Includes comprehensive JavaDoc
- [ ] Tests both success and error cases
- [ ] Tests edge cases and boundary conditions
- [ ] Tests tenant isolation where applicable
- [ ] No `.getResult()` calls (all use `runPromise()`)
- [ ] No mocking of MetricsCollector internals
- [ ] No testing of private methods
- [ ] All tests pass: `./gradlew :module:test`

### CI/CD

1. **GitHub Actions**
   - Workflows in `.github/workflows/`
   - Required checks:
     - Build
     - Test
     - Code coverage
     - Linting
     - Security scanning

2. **Deployment**
   - Use Helm for Kubernetes deployments
   - Follow GitOps principles
   - Use semantic versioning

- **Code Quality**: Checkstyle, PMD, SpotBugs, and JaCoCo configured in root build; use `./gradlew spotlessApply` for formatting
- **Module Structure**: Each module single-responsibility; minimal build files; all common configs inherited from root
- **Logging**: Use SLF4J + Log4j2 (never Logback)
- **Build files**: Keep minimal, never duplicate root configurations

## Project Conventions (Follow These Exactly)

### Naming Conventions

1. **Packages**
   - Base: `com.ghatana`
   - Core modules: `com.ghatana.core.*`
   - Product modules: `com.ghatana.products.*`
   - Domain models: `com.ghatana.domain.*`
   - Application services: `com.ghatana.application.*`
   - Infrastructure: `com.ghatana.infrastructure.*`

2. **Classes**
   - Use PascalCase
   - Suffix with type (e.g., `UserService`, `UserRepository`)
   - Interfaces: `I` prefix or `-able` suffix (e.g., `IUserRepository` or `UserRepository`)

3. **Methods**
   - Use camelCase
   - Verb-noun format (e.g., `getUser`, `createOrder`)
   - Boolean methods: `is*`, `has*`, `can*` (e.g., `isValid`, `hasPermission`)

4. **Variables**
   - Use camelCase
   - Be descriptive (avoid abbreviations)
   - No Hungarian notation

### Code Organization

1. **Package by Feature**

   ```
   com.ghatana.products.reporting
   ├── application
   │   ├── service
   │   ├── dto
   │   └── mapper
   ├── domain
   │   ├── model
   │   └── port
   └── infrastructure
       ├── repository
       └── client
   ```

2. **File Naming**
   - One class/interface per file
   - File name matches class name
   - Suffix with type (e.g., `UserService.kt`, `UserRepository.kt`)

3. **Imports**
   - Group by source (stdlib, third-party, project)
   - Sort alphabetically within groups
   - Use wildcards for test dependencies only

- **HTTP servers**: Use `core/http-server` abstractions (HttpServerBuilder, ResponseBuilder, RoutingServlet)
- **Domain models**: In `core/domain-models` + `core/types`; ports in `*-ports` packages; adapters in service modules
- **Tenancy**: Extract from path/headers early; exclude from request bodies
- **Contracts**: Protobuf in `contracts/proto`; JSON schemas in `yappc/schemas/events/v1`

## 📚 Documentation Standards (Non-Negotiable)

### Documentation Requirements

1. **Code Comments**
   - Explain "why" not "what"
   - Use Javadoc/KDoc for public APIs
   - Keep comments up-to-date

2. **API Documentation**
   - Use OpenAPI/Swagger
   - Document all endpoints
   - Include examples

3. **Architecture Decision Records (ADRs)**
   - Document significant decisions
   - Use MADR format
   - Store in `docs/adr/`

### Documentation Generation

1. **Backend**
   - Use Dokka for Kotlin/Java
   - Generate OpenAPI specs
   - Include usage examples

2. **Frontend**
   - Use Storybook for components
   - Document props and examples
   - Include accessibility notes

### Documentation Review

1. **Code Review**
   - Documentation is part of the review
   - Check for accuracy and completeness
   - Verify examples work

2. **Living Documentation**
   - Keep documentation up-to-date
   - Remove outdated information
   - Use versioned documentation

**CRITICAL**: Documentation is NOT optional. **Every line of code that's not self-explanatory requires documentation.** Complete documentation is as important as passing tests. Code is incomplete without:

1. ✅ **In-file JavaDoc** (classes, methods, constants)
2. ✅ **@doc.\* metadata tags** (type, purpose, layer, pattern)
3. ✅ **Unit tests** (with descriptive names and comments)
4. ✅ **Code comments** (why, not what)
5. ✅ **Architecture documentation** (when applicable)

### Documentation Completion Checklist (REQUIRED)

Before ANY code merge, verify:

- [ ] **Class-level JavaDoc**: All public classes have @param, @return, @see references
- [ ] **Method-level JavaDoc**: All public methods have clear purpose, parameters, return, exceptions
- [ ] **@doc.\* Tags**: All classes tagged with @doc.type, @doc.purpose, @doc.layer, @doc.pattern
- [ ] **Usage Examples**: Complex classes include `<pre>{@code ...}</pre>` usage examples
- [ ] **Tests**: All behavioral code has corresponding unit tests with `@DisplayName`
- [ ] **Test Comments**: Tests explain "why" not "what" (the assertion explains "what")
- [ ] **Code Comments**: Non-obvious logic has explanatory comments (especially "why" decisions)
- [ ] **Thread Safety**: Thread-safe or mutable classes document synchronization strategy
- [ ] **Architecture Role**: Document role in system (CRITICAL for core modules)
- [ ] **Cross-References**: Link to related classes, patterns, documentation

### JavaDoc Template Structure

#### Class-Level JavaDoc (REQUIRED for all public classes)

```java
/**
 * [One-line concise description starting with verb].
 *
 * <p><b>Purpose</b><br>
 * [2-3 sentences explaining why this class exists and what problem it solves]
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * [Realistic code example showing typical usage]
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * [Where does this fit in the system? CRITICAL for core modules]
 * - Used for [primary responsibility]
 * - Integrates with [related components]
 * - Consumed by [typical consumers]
 *
 * <p><b>Thread Safety</b><br>
 * [Immutable/Synchronized/Thread-unsafe - explain carefully]
 *
 * <p><b>Performance Characteristics</b><br>
 * [If applicable: O(n) operations, memory usage, GC implications]
 *
 * @see [RelatedClass]
 * @doc.type [record/class/interface/enum]
 * @doc.purpose [Brief purpose for documentation extraction]
 * @doc.layer [core/platform/product]
 * @doc.pattern [Value Object/Factory/Adapter/Builder/etc]
 */
public class MyClass {
```

#### Method-Level JavaDoc (REQUIRED for all public methods)

```java
/**
 * [One-line description: verb-noun format].
 *
 * [Detailed description if needed]
 *
 * @param paramName [description]
 * @param paramName2 [description]
 * @return [description of return value]
 * @throws [ExceptionType] if [condition when thrown]
 * @see [RelatedMethod]
 */
public Type methodName(Type1 param, Type2 param2) throws ExceptionType {
```

#### Field/Constant Documentation

```java
/**
 * [Purpose of this field].
 * [Constraints if any: e.g., "never null", "always positive"]
 */
private static final String CONSTANT_NAME = "value";
```

### @doc.\* Metadata Tags (REQUIRED)

Every public class MUST have exactly 4 tags:

```
@doc.type      [record|class|interface|enum|exception]
@doc.purpose   [One-line purpose for searchable documentation]
@doc.layer     [core|platform|product|contracts]
@doc.pattern   [Value Object|Factory|Builder|Adapter|Repository|Service|etc]
```

**Layer Definitions**:

- **core**: Canonical implementations, abstractions, interfaces (reusable across org)
- **platform**: Platform-specific utilities, infrastructure
- **product**: Product-specific features, domain logic
- **contracts**: Protobuf, API contracts

**Pattern Definitions** (use exactly these terms):

- Value Object (immutable, identity-based on value)
- Factory (creates objects)
- Builder (constructs complex objects)
- Adapter (bridges two interfaces)
- Repository (data access)
- Service (business logic)
- Strategy (pluggable algorithm)
- Filter (processes stream)
- Interceptor (wraps behavior)
- Exception (error condition)
- Configuration (holds settings)

### Test Documentation Standards

Every test class and method MUST be clearly documented:

```java
/**
 * Unit tests for [ClassName].
 *
 * Tests validate:
 * - [Behavior 1]
 * - [Behavior 2]
 * - [Edge case]
 */
@DisplayName("ClassName Tests")
class ClassNameTest {

    /**
     * [What should happen when condition X].
     *
     * GIVEN: [initial state]
     * WHEN: [action taken]
     * THEN: [expected outcome]
     */
    @Test
    @DisplayName("Should [expected behavior] when [condition]")
    void shouldBehaviorWhenCondition() {
        // Setup

        // Action

        // Assertion with clear message
        assertThat(result)
            .as("Description of what we're asserting")
            .isEqualTo(expected);
    }
}
```

**Test Naming Convention**: `should[ExpectedOutcome]When[Condition]`
**Test Display Names**: Use @DisplayName("human-readable description")

### Code Comment Standards

Comments explain **WHY**, not **WHAT**. Code should be clear enough to show what it does.

**✅ GOOD Comments**:

```java
// We check for null before dereferencing because EventCloud may return
// null events during backpressure situations (see EventCloud spec v4, section 3.2)
if (event != null) {
    process(event);
}
```

**❌ BAD Comments**:

```java
// Check if event is null
if (event != null) {  // THIS IS OBVIOUS!
    process(event);
}
```

### Architecture Documentation Requirements

For classes in `core/*` modules, add architecture context:

```java
/**
 * [Brief description].
 *
 * <p><b>Architecture Role</b><br>
 * This is a [Core Platform Abstraction | Domain Model | Value Type | Port | Adapter].
 * Part of the [subsystem] responsible for [responsibility].
 *
 * <p><b>Integration Points</b><br>
 * - Consumed by: [list of typical consumers]
 * - Depends on: [list of dependencies]
 * - Events: [if applicable, which events are emitted/consumed]
 * - Storage: [if applicable, how is this persisted]
 *
 * <p><b>Related Documentation</b><br>
 * - See [core module] for [related abstraction]
 * - See /docs/architecture/[doc].md for [architectural context]
 *
 * @see [RelatedInterface]
 * @see [RelatedImplementation]
 */
```

### Module README Documentation

Every module MUST have a README.md with:

```markdown
# [Module Name]

## Purpose

[What does this module do?]

## Key Components

- [Class1]: [Purpose]
- [Class2]: [Purpose]

## Usage

[Code examples]

## Architecture

[How does it fit into the system?]

## Dependencies

[What does this depend on?]

## Testing

[How to test this module?]

## Documentation

[Links to relevant documentation]
```

### Documentation Validation Checklist

Run before every commit:

```bash
# 1. Check JavaDoc coverage
./gradlew :module:javadoc

# 2. Verify @doc.* tags (use grep)
grep -r "@doc\." src/main/java/com/ghatana/

# 3. Run tests
./gradlew :module:test

# 4. Check code formatting
./gradlew spotlessApply

# 5. Verify no implementation in tests
grep -r "public class" src/test/java/  # Should only be test classes

# 6. Validate architecture (if in core/)
./gradlew :testing:architecture-tests:test
```

### Documentation Examples by Type

#### Value Object (Record)

```java
/**
 * Unique identifier for requests in EventCloud.
 *
 * <p><b>Purpose</b><br>
 * Provides type-safe correlation tracking across distributed services
 * to prevent accidental mixing with other UUID-based identifiers.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CorrelationId id = CorrelationId.of("550e8400-e29b-41d4-a716-446655440000");
 * CorrelationId newId = CorrelationId.random();
 * String raw = id.raw();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Unique identifier for request correlation
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record CorrelationId(String value) implements Identifier { }
```

#### Interface (Port)

```java
/**
 * Abstraction for collecting system metrics.
 *
 * <p><b>Purpose</b><br>
 * Decouples metric collection from specific implementations (Micrometer, etc.)
 * to allow flexible observability backends.
 *
 * <p><b>Implementation</b><br>
 * Implementations should be thread-safe and non-blocking.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * MetricsCollector metrics = MetricsCollectorFactory.create(registry);
 * metrics.incrementCounter("requests.total", "status", "200");
 * }</pre>
 *
 * @see MetricsCollectorFactory
 * @see SimpleMetricsCollector
 * @doc.type interface
 * @doc.purpose Abstraction for metrics collection
 * @doc.layer core
 * @doc.pattern Port
 */
public interface MetricsCollector {
```

#### Configuration Class

```java
/**
 * Configuration holder for observability settings.
 *
 * <p><b>Purpose</b><br>
 * Centralizes all observability configuration (tracing, metrics, logging)
 * in an immutable, validated configuration object.
 *
 * <p><b>Validation</b><br>
 * All settings are validated on construction. Invalid values throw
 * IllegalArgumentException with descriptive messages.
 *
 * @doc.type class
 * @doc.purpose Observability configuration holder
 * @doc.layer core
 * @doc.pattern Configuration
 */
@Immutable
public final class ObservabilityConfig {
```

#### Exception Class

```java
/**
 * Thrown when an invalid identifier is constructed.
 *
 * <p>This exception indicates that an identifier string failed validation:
 * - Empty or null
 * - Invalid format (not a valid UUID)
 * - Reserved value
 *
 * <p>Recovery: Validate identifier source before construction.
 *
 * @doc.type exception
 * @doc.purpose Invalid identifier error
 * @doc.layer core
 * @doc.pattern Exception
 */
public class InvalidIdentifierException extends RuntimeException {
```

---

## Non-Negotiable Rules (MUST Follow)

### Duplication & Reuse

- **ALWAYS search first**: Check `core/*`, `contracts/*`, `dcmaar/platform/` before creating anything new
- **NEVER duplicate**: One implementation per concept; consolidate or refactor if found
- **REUSE over invent**: Prefer existing `core/*` abstractions over new implementations

### Code example sourcing (MANDATORY)

- Before adding or updating any code, developers MUST follow this procedure:
  1. Search the repository for canonical examples or existing implementations that solve the same problem (start with `core/*`, `contracts/*`, then product modules). Prefer the in-repo example when it fits the need.
  2. If no suitable example exists in the repository, perform a focused web search for high-quality, canonical examples or official documentation (library docs, RFCs, or widely-accepted patterns).
  3. Decide on the approach to use (repo example or vetted external example). Document the decision briefly in the change (commit message and PR description) explaining why this approach was chosen and where the canonical example came from.
  4. Apply the chosen pattern consistently across the repository. If the change affects multiple modules, include a clear migration or compatibility plan in the PR.

This is mandatory: every PR that introduces new patterns, libraries, or significant API changes must include a short "Consistency note" in the PR description listing the canonical example used (repo path or external link) and the rationale for choosing it.

### Dependency & Library Management

- **Version catalog only**: All versions declared in `gradle/libs.versions.toml`; never hardcode in modules
- **Canonical libraries** (enforce these):
  - **JWT**: `nimbus-jose-jwt` only
  - **Logging**: Log4j2+SLF4J only (never Logback)
  - **HTTP**: Use `core/http-server` abstractions (never direct ActiveJ, OkHttp, Apache HttpClient)
  - **Metrics**: Use `core/observability` abstraction (never direct Micrometer)
  - **Database**: Use `core/database` abstraction (never direct Hibernate/HikariCP)
  - **Redis**: `jedis` only
  - **JSON**: Jackson only
- **No duplicate libraries**: Verify in version catalog before adding
- **License compliance**: Only Apache 2.0, MIT, BSD, EPL 2.0 (never GPL/LGPL/proprietary)

### Code Organization

- **File size**: Target <300 LOC; >600 LOC requires justification comment
- **Packages**: `com.ghatana.*` strictly; validation code MUST use `com.ghatana.core.validation`
- **Single responsibility**: One clear purpose per class/module
- **No stubs**: No placeholder classes; depend on real implementations or implement fully

### Library Addition Checklist

Before adding ANY external library:

- [ ] Search for existing `core/*` solution first
- [ ] Verify not already in `gradle/libs.versions.toml`
- [ ] Confirm not duplicate of canonical choice
- [ ] Document rationale in PR
- [ ] Verify license (permissive only)
- [ ] Run CVE checks: `./gradlew dependencyCheckAnalyze`
- [ ] Update version catalog (never hardcode in modules)
- [ ] Test with module's test suite
- [ ] Assign ownership for future maintenance

## Core Module Standards

### Canonical Domain Models (First-Class Citizens)

- **Agent, Event, EventType, Pattern, EventPattern** in `core/domain-models/` + `core/event-runtime/`
- **Protobuf contracts** in `contracts/proto/`
- **JSON schemas** in `yappc/schemas/events/v1/`
- **RULE**: Never duplicate these in product modules; always source from `core/*`

### Type References vs. Operations (CRITICAL)

- ✅ **OK**: Use `io.activej.http.*`, `io.micrometer.core.*` types in method signatures
- 🎯 **REQUIRED**: Use `com.ghatana.core.http.*`, `com.ghatana.observability.*` for operations
- **WHY**: Platform control over security, observability, migrations
- Verify with: `./gradlew :testing:architecture-tests:test`

### Infrastructure Abstractions (Platform-Wide)

These modules provide **abstracted** infrastructure so consuming modules **never** directly depend on external libraries:

#### `core/http-server`

- **Purpose**: Single source of truth for HTTP servers, routing, filters, response builders
- **Abstracts**: ActiveJ HTTP (consumers should not import `io.activej.http.*` directly)
- **Usage**: Use `HttpServerBuilder`, `RouteRegistry`, `ResponseBuilder` for all HTTP endpoints
- **Testing**: Use provided JUnit extensions and test utilities
- **Package**: `com.ghatana.http.*`

**Example:**

```java
import com.ghatana.http.HttpServerBuilder;
import com.ghatana.http.ResponseBuilder;

HttpServerBuilder.create()
    .addRoute("/health", request ->
        ResponseBuilder.ok().json(Map.of("status", "healthy")))
    .build();
```

#### `core/database`

- **Purpose**: Database access, connection pooling, migrations, JPA/Hibernate setup
- **Abstracts**: Hibernate, HikariCP, Flyway, JDBC drivers
- **Usage**: Depend on this module for all database operations; never add `hibernate-core`, `hikaricp`, or `flyway-core` directly to service modules
- **Testing**: Use Testcontainers integration provided by this module
- **Package**: `com.ghatana.database.*`

**Example:**

```java
import com.ghatana.database.Repository;
import com.ghatana.database.DatabaseConfig;

@Repository
public interface UserRepository extends Repository<User, UserId> {
    // No direct Hibernate imports needed
}
```

#### `core/observability`

- **Purpose**: Metrics, tracing, and monitoring
- **Abstracts**: Micrometer, OpenTelemetry, Prometheus client
- **Usage**: Use `MetricsCollector`, `TracingContext` for instrumentation; expose `/metrics` via provided utilities
- **Testing**: Mock/stub utilities available for testing metrics collection
- **Package**: `com.ghatana.observability.*`

**Example:**

```java
import com.ghatana.observability.MetricsCollector;
import com.ghatana.observability.MetricsCollectorFactory;

public class MyService {
    private final MetricsCollector metrics;

    public MyService(MeterRegistry registry) {
        this.metrics = MetricsCollectorFactory.create(registry);
    }

    public void processRequest() {
        metrics.incrementCounter("requests.total",
            "method", "POST", "status", "200");
    }
}
```

**Available Classes:**

- `MetricsCollector` - Interface for metrics collection
- `MetricsCollectorFactory` - Factory for creating collectors
- `BaseMetricsCollector` - Base implementation
- `SimpleMetricsCollector` - Concrete implementation
- `NoopMetricsCollector` - No-op implementation for tests
- `MetricsRegistry` - EventCloud taxonomy metrics
- `TracingProvider`, `TracingManager` - Distributed tracing
- `Metrics` - Simple wrapper for Timer and Counter

#### `libs/ai-integration`

- **Purpose**: Unified LLM, embedding, and vector-store utilities for AI/ML features
- **Abstracts**: OpenAI API, pgvector, LangChain integrations
- **Usage**: Use `EmbeddingService`, `VectorStore`, `PromptTemplateManager` for all AI operations; never add direct OpenAI or LangChain dependencies to service modules
- **Package**: `com.ghatana.ai.*`
- **Key Components**:
  - `EmbeddingService` - Generate and manage text embeddings
  - `VectorStore` - Store and search vectors with similarity scoring
  - `LLMConfiguration` - Centralized LLM service configuration
  - `PromptTemplateManager` - Template management with variable substitution
  - `SimilarityCalculator` - Deterministic vector similarity calculations
  - `PgVectorStore` - PostgreSQL pgvector adapter

**Example:**

```java
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.embedding.OpenAIEmbeddingService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.ai.vectorstore.PgVectorStore;

// Configure LLM
LLMConfiguration config = LLMConfiguration.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

// Create embedding service
EmbeddingService embeddingService = new OpenAIEmbeddingService(config, metricsCollector);

// Create vector store
VectorStore vectorStore = new PgVectorStore(dataSource, metricsCollector, "vectors", 1536);

// Generate embedding
CompletableFuture<Result<EmbeddingResult>> future = embeddingService.createEmbedding("Hello world");

// Store vector
future.thenCompose(result -> {
    if (result.isSuccess()) {
        EmbeddingResult embedding = result.getValue();
        return vectorStore.store("doc-1", "Hello world", embedding.getVector(), metadata);
    }
    return CompletableFuture.completedFuture(Result.failure(result.getError()));
});
```

**Non-Negotiable**:

- ❌ NO direct OpenAI client imports in service modules
- ❌ NO direct LangChain imports without abstraction
- ❌ NO duplicate embedding or vector store implementations
- ✅ MUST use `EmbeddingService` interface for all embedding operations
- ✅ MUST use `VectorStore` interface for all vector operations
- ✅ MUST integrate with `core/observability` for metrics
- ✅ MUST use `Result<T>` for error handling

**Available Implementations**:

- `OpenAIEmbeddingService` - OpenAI embeddings
- `PgVectorStore` - PostgreSQL pgvector storage
- `SimilarityCalculator` - Cosine, Euclidean, and dot product calculations
- `PromptTemplateManager` - Template registry with variable substitution

**See Also**: `libs/java/ai-integration/README.md` for detailed usage examples and API documentation.

#### `core/validation`

- **Purpose**: Input validation, constraint checking, schema validation
- **Abstracts**: Jakarta Validation API, Hibernate Validator, JSON Schema validators
- **Usage**: Use `Validator` interface and provided validators; never add `hibernate-validator` directly to service modules
- **Package**: `com.ghatana.core.validation.*`

#### `core/common-utils`

- **Purpose**: Shared utilities, exception hierarchies, functional helpers
- **Abstracts**: Common patterns (retry, circuit breaker, rate limiting)
- **Usage**: Reuse exception types, collection utilities, string helpers
- **Package**: `com.ghatana.core.common.*`, `com.ghatana.core.exception.*`

**Available Exceptions:**

- `BaseException` - Base for all custom exceptions
- `ServiceException` - Service-level errors
- `ResourceNotFoundException` - 404 scenarios
- `ErrorCode` - Standard error codes
- `ErrorCodeMappers` - Error code mapping utilities

#### `core/types`

- **Purpose**: Value objects, identifiers, enums, type-safe wrappers
- **Abstracts**: Common value types (IDs, timestamps, URIs)
- **Usage**: Use canonical `Identifier`, `TenantId`, `EventId` types; never create duplicate ID types
- **Package**: `com.ghatana.core.types.*`

#### `core/testing/*`

- **`core/testing/test-utils`**: Base test utilities, assertions, matchers
- **`core/testing/activej-test-utils`**: **MANDATORY** ActiveJ test infrastructure - EventloopTestBase, EventloopRunner
- **`core/testing/test-containers`**: Testcontainers setup for databases, Kafka, etc.
- **`core/testing/test-fixtures`**: Reusable test data builders and fixtures
- **`core/testing/test-data`**: Test data generation utilities
- **Abstracts**: JUnit, Mockito, AssertJ, Testcontainers, ActiveJ test patterns
- **Usage**: All test dependencies should come from these modules
- **Package**: `com.ghatana.testing.*`

**CRITICAL - ActiveJ Promise Testing:**

ALL tests using ActiveJ Promises MUST extend `EventloopTestBase` and use `runPromise()` instead of `.getResult()`.

**✅ CORRECT Pattern:**

```java
import com.ghatana.testing.EventloopTestBase;

class MyTest extends EventloopTestBase {
    @Test
    void shouldProcessAsync() {
        // Given
        MyService service = new MyService();

        // When - use runPromise() for all Promise operations
        String result = runPromise(() -> service.processAsync("input"));

        // Then
        assertThat(result).isEqualTo("expected");

        // Cleanup
        runPromise(() -> service.close());
    }
}
```

**❌ WRONG Pattern (Causes NullPointerException):**

```java
class MyTest {
    @Test
    void shouldProcessAsync() {
        MyService service = new MyService();

        // ❌ WRONG - .getResult() fails without eventloop context
        String result = service.processAsync("input").getResult(); // NPE!

        assertThat(result).isEqualTo("expected");
    }
}
```

**Why:** ActiveJ Promises require an Eventloop to execute. Calling `.getResult()` in a test thread without eventloop context causes `NullPointerException`. `EventloopTestBase` provides managed eventloop per test with automatic lifecycle management.

**Dependency:**

```gradle
dependencies {
    testImplementation project(':core:testing:activej-test-utils')
}
```

**Available Helpers:**

- `runPromise(() -> promise)` - Execute Promise, return result
- `runBlocking(() -> code)` - Run code in eventloop thread
- `eventloop()` - Access underlying eventloop
- `eventloopDelayMillis(ms)` - Create delay Promise

**See:** `ACTIVEJ_TEST_MIGRATION_GUIDE.md` for complete migration patterns.

#### `core/activej-runtime`

- **Purpose**: ActiveJ dependency injection, launchers, configuration
- **Abstracts**: ActiveJ DI, boot, config, launcher modules
- **Usage**: Use provided `ActiveJModule`, `Launcher`, `ConfigModule` for DI setup
- **Package**: `com.ghatana.activej.*`

#### `core/redis-cache` (formerly `core/cache`)

- **Purpose**: Caching abstractions (in-memory, distributed)
- **Abstracts**: Redis clients (Jedis), cache implementations
- **Usage**: Use `CacheManager` interface; never add `jedis` directly to service modules
- **Package**: `com.ghatana.cache.*`

#### `core/proto` / `contracts/proto`

- **Purpose**: Protocol Buffer definitions and generated code
- **Abstracts**: Protobuf Java runtime
- **Usage**: Define all gRPC contracts here; services depend on this for wire protocols
- **Package**: `com.ghatana.contracts.*`

#### `core/event-runtime`

- **Purpose**: Event bus, event handling, and webhook configuration
- **Abstracts**: Event distribution patterns
- **Usage**: Use `EventBus`, `EventHandler` interfaces for event-driven communication
- **Package**: `com.ghatana.core.event.*`

**Available Classes:**

- `EventBus` - Core event bus interface
- `EventHandler` - Event handler interface
- `EventBusWithPromise` - ActiveJ Promise-based event bus
- `InMemoryEventBus*` - In-memory implementations
- `WebhookConfig` - Webhook configuration value object (in `com.ghatana.core.event.webhook`)

**Example:**

```java
import com.ghatana.core.event.webhook.WebhookConfig;

WebhookConfig config = WebhookConfig.builder()
    .withUrl("https://api.example.com/webhook")
    .withSecret("my-secret")
    .withMaxRetries(5)
    .withTimeout(Duration.ofSeconds(30))
    .build();
```

#### `core/agent-runtime`

- **Purpose**: Agent lifecycle, status management, and registry
- **Abstracts**: Agent patterns with ActiveJ Promise support
- **Usage**: Extend `BaseAgent` for agent implementations, use `AgentRegistry` for discovery
- **Package**: `com.ghatana.core.agent.runtime.*`

**Available Classes:**

- `BaseAgent` - Base agent implementation
- `AgentStatus` - Agent lifecycle status
- `AgentRegistry` - Agent discovery interface
- `InMemoryAgentRegistry*` - In-memory registry implementations

### Anti-Patterns (Never Do This) 🚨

These patterns have been identified as problematic through codebase analysis. **Avoid at all costs**:

#### 1. **Package Duplication Within Same Module**

```java
// ❌ WRONG - Multiple packages for same concepts
core/validation-api/
├── com/ghatana/core/validation/ValidationService.java
└── com/ghatana/validation/ValidationService.java  // Duplicate!
```

**Rule**: One canonical package per concept. No "compatibility" or "legacy" packages within the same module.

#### 2. **Stub Classes in Production Code**

```java
// ❌ WRONG - Stub in main source
public class TenantContext {
    // TODO: stub implementation - replace later
    public String getTenantId() { return null; }
}

// ✅ CORRECT - Depend on real implementation
import com.ghatana.core.governance.security.TenantContext;
```

**Rule**: Depend on proper implementations from `core/` or implement fully. No "stub" packages.

#### 3. **Inner Enums for Domain Concepts**

```java
// ❌ WRONG - Domain concept as inner class
public class RecommendationService {
    public enum RecommendationType { PATTERN, ANOMALY }
}

// ✅ CORRECT - Extract to domain models
// In core/domain-models/recommendation/RecommendationType.java
public enum RecommendationType { PATTERN, ANOMALY }
```

**Rule**: Extract domain concepts to `core/domain-models/` for reuse across modules.

#### 4. **Direct ActiveJ Server Construction**

```java
// ❌ WRONG - Manual server bootstrap
public static void main(String[] args) {
    HttpServer server = HttpServer.builder()
        .withHandler(...)
        .build();
    server.listen(...);
}

// ✅ CORRECT - Use core abstractions
import com.ghatana.core.http.server.HttpServerBuilder;

HttpServerBuilder.create()
    .addRoute("/api", handler)
    .build();
```

**Rule**: Use `core/http-server` abstractions for all server operations.

#### 5. **Direct DI Module Wiring in Product Services**

```java
// ❌ WRONG - Manual DI bootstrap
Injector injector = Injector.of(
    ModuleBuilder.create()
        .bind(Foo.class).to(FooImpl.class)
        ...
);

// ✅ CORRECT - Create service launcher abstraction (TODO: core/service-launcher)
// See CODEBASE_AUDIT_FINDINGS.md Section 4.1 for planned pattern
```

**Rule**: Product services should use standardized bootstrapping, not manual DI wiring.

#### 6. **Direct Micrometer Operations**

```java
// ❌ WRONG - Bypass observability abstraction
import io.micrometer.core.instrument.Counter;
registry.counter("requests", "status", "200").increment();

// ✅ CORRECT - Use core/observability
import com.ghatana.observability.MetricsCollector;
metrics.incrementCounter("requests", "status", "200");
```

**Rule**: All metrics collection MUST go through `core/observability` MetricsCollector.

### Module Dependency Rules

1. **Core modules are self-contained**: They expose abstractions and hide implementation details
2. **Services depend on core, not external libs**: If you need HTTP, depend on `core/http-server`, not `activej-http`
3. **No transitive leakage**: Core modules use `implementation` scope for external deps, `api` only for exposed abstractions
4. **Version catalog only**: All external dependencies declared in `gradle/libs.versions.toml`
5. **No product-specific shared modules**: Use `core/` for platform-wide code; product modules should be leaf nodes
6. **Never use `multi-agent-system:shared:*`**: These are deprecated; use `core:*` equivalents (see migration mapping below)

### Shared-to-Core Migration Mapping

**CRITICAL**: The following `multi-agent-system:shared:*` modules are deprecated and must not be used:

| ❌ Deprecated Shared Module                    | ✅ Use Core Module Instead         | Status              | Package Change                                                           | Notes                                       |
| ---------------------------------------------- | ---------------------------------- | ------------------- | ------------------------------------------------------------------------ | ------------------------------------------- |
| `:multi-agent-system:shared:metrics`           | `:core:observability`              | ✅ **DELETED**      | `com.ghatana.shared.metrics.*` → `com.ghatana.observability.*`           | Complete duplicate                          |
| `:multi-agent-system:shared:webhooks`          | `:core:event-runtime`              | ✅ **DELETED**      | `com.ghatana.shared.webhooks.*` → `com.ghatana.core.event.webhook.*`     | WebhookConfig in core                       |
| `:multi-agent-system:shared:exception`         | `:core:common-utils`               | ✅ **DELETED**      | `com.ghatana.shared.exception.*` → `com.ghatana.core.exception.*`        | 14 identical classes                        |
| `:multi-agent-system:shared:validation`        | `:core:validation`                 | ✅ **DELETED**      | `com.ghatana.shared.validation.*` → `com.ghatana.core.validation.*`      | Duplicates core                             |
| `:multi-agent-system:shared:common`            | `:core:common-utils`               | ✅ **DELETED**      | `com.ghatana.shared.common.*` → `com.ghatana.core.common.*`              | Nearly empty (1 file)                       |
| `:multi-agent-system:shared:observability`     | `:core:observability`              | ✅ **DELETED**      | N/A                                                                      | Empty directory                             |
| `:multi-agent-system:shared:common-interfaces` | `:core:common-utils`               | ✅ **DELETED**      | N/A                                                                      | Empty directory                             |
| `:multi-agent-system:shared:test-utils`        | `:core:testing:activej-test-utils` | ⚠️ **THIN WRAPPER** | `com.ghatana.shared.test.*` → `com.ghatana.testing.activej.*`            | Delegates to core, kept for backward compat |
| `:multi-agent-system:shared:agent`             | `:core:agent-runtime`              | ✅ **DELETED**      | `com.ghatana.shared.common.agent.*` → `com.ghatana.core.agent.runtime.*` | Migrated                                    |
| `:multi-agent-system:shared:event`             | `:core:event-runtime`              | ✅ **DELETED**      | `com.ghatana.shared.common.event.*` → `com.ghatana.core.event.*`         | Migrated                                    |
| `:multi-agent-system:shared:models`            | `:core:domain-models`              | ❌ Not included     | `com.ghatana.shared.models.*` → `com.ghatana.core.domain.*`              | To be evaluated                             |
| `:multi-agent-system:shared:persistence`       | `:core:database`                   | ❌ Not included     | `com.ghatana.shared.persistence.*` → `com.ghatana.database.*`            | To be evaluated                             |
| `:multi-agent-system:shared:core`              | `:core:common-utils`               | ❌ Not included     | `com.ghatana.shared.core.*` → `com.ghatana.core.common.*`                | To be evaluated                             |
| `:multi-agent-system:shared:governance`        | `:core:governance`                 | ❌ Not included     | `com.ghatana.shared.governance.*` → `com.ghatana.governance.*`           | To be evaluated                             |

**IMPORTANT**: `shared:test-utils` now **delegates** to `core:testing:activej-test-utils`:

1. ActiveJ test utilities moved to **canonical location**: `core:testing:activej-test-utils`
2. 20+ modules can gradually migrate to use `core:testing:activej-test-utils` directly
3. `shared:test-utils` kept as **thin wrapper** for backward compatibility
4. New modules should use `core:testing:activej-test-utils` directly
5. Proper architecture: ActiveJ is **platform-wide** (used in core/observability + 20+ modules)

**Migration Checklist** (for each module):

1. Update `build.gradle`: Replace shared dependency with core equivalent
2. Update imports: Change package from `com.ghatana.shared.*` to `com.ghatana.*` (core packages)
3. Remove third-party imports: Ensure no direct imports of Micrometer, ActiveJ, Hibernate, etc.
4. Build and test: `./gradlew :module:clean :module:build :module:test`
5. Update README: Document any module-specific migration notes

**See:**

- `CODEBASE_ANALYSIS_AND_RECOMMENDATIONS.md` for detailed duplicate analysis and cleanup strategy
- **`CODEBASE_AUDIT_FINDINGS.md`** for comprehensive audit results (Oct 2025) including:
  - 23 duplicate type names requiring consolidation
  - 100+ architectural violations (direct ActiveJ/Micrometer usage)
  - Validation module package duplication issues
  - Prioritized action items and consolidation opportunities

### Decision Tree: Where Does My Code Go?

```
Is it a domain concept (Agent/Event/Pattern)?
  → YES: `core/domain-models/` or `core/event-model/`
  → NO: Continue

Is it infrastructure (HTTP/DB/cache/metrics)?
  → YES: Extend existing `core/<infra-module>/` or create new core module
  → NO: Continue

Is it a value type or identifier?
  → YES: `core/types/`
  → NO: Continue

Is it a validation rule or exception?
  → YES: `core/validation/` or `core/common-utils/`
  → NO: Continue

Is it product-specific business logic?
  → YES: Product module (e.g., `multi-agent-system/<service>/`, `yappc/<service>/`)
  → NO: Reconsider if it should be in `core/`
```

### Migration from Product Shared Modules

If you find code in `multi-agent-system/shared/*` or similar:

1. **Check if equivalent exists in `core/`** — reuse instead of migrate
2. **If unique and reusable** — move to appropriate `core/` module
3. **If product-specific** — keep in product module, not shared
4. **Update dependencies** — point consumers to `core/` modules
5. **Remove redundant shared modules** — after migration complete

### Integration Points Summary

- `core/http-server` — HTTP servers, routing, filters, response builders
- `core/observability` — metrics, tracing, monitoring
- `core/database` — database access, migrations, JPA
- `core/validation` — input validation, constraint checking
- `core/common-utils` — utilities, exceptions, helpers
- `core/types` — value objects, identifiers
- `core/testing/*` — test utilities, containers, fixtures
- `core/activej-runtime` — ActiveJ DI and runtime
- `contracts/proto` — protobuf contracts
- `dcmaar/platform` — DCMAAR-specific platform utilities

## Typical tasks (examples)

- Add a new module:
  1. Include it in `settings.gradle.kts` via `includeExternalProject("<path>")`.
  2. Apply Java conventions from `gradle/java-conventions.gradle` and reuse `core/*`.
- Add a HTTP endpoint:
  - Implement servlet/route in a service using `core/http-server` routing and filters; build consistent JSON responses via `ResponseBuilder`.
- Add an event type:
  - Update JSON schema in `yappc/schemas/events/v1` and/or protobuf in `contracts/proto`; enforce compatibility checks in catalog/validation services.

## Non-obvious details

- Root `build.gradle` aggregates JaCoCo and wires quality tools; some tools are intentionally disabled to unblock builds — check comments before enabling.
- Gradle build cache is configured to use `build-cache/`. Wrapper uses Gradle 8.10.2.
- CI/security tasks exist: OWASP Dependency-Check and PIT (mutation testing) plugins are available but not universally applied.

## Files to consult first

- Root `build.gradle`, `settings.gradle.kts`, `gradle.properties`, `gradle/*` for conventions.
- `core/http-server/README.md`, `core/domain-models/README.md`, `core/observability/README.md`.
- `dcmaar/platform/README.md` for platform-wide server/testing utilities.
- `docs/architecture/CURRENT_TASKS/` folder for design documents (see section below)

---

## 🎯 CRITICAL ARCHITECTURAL DECISIONS (From Design Review, Oct 2025)

### The Event Processing v2.0 Platform — Non-Negotiable Imperatives

After comprehensive design review of the World-Class Multi-Agent Event Processing System (v2.0), the following **binding architectural decisions** have been approved and must be followed during implementation. These are NOT options or guidelines—they are **imperative project standards**.

### 1. **Unified Operator Model (CRITICAL DECISION)**

**Decision**: All operators (Stream, Pattern, Learning) MUST extend `UnifiedOperator` interface in a single abstraction.

**Binding Requirements**:

- **Stream Operators** (`Filter`, `Map`, `Window`, `Join`, `Reduce`): Transform/filter event streams
- **Pattern Operators** (`SEQ`, `AND`, `OR`, `NOT`, `WITHIN`, `REPEAT`, `UNTIL`): Detect temporal patterns (CEP)
- **Learning Operators** (`FrequentSequenceMiner`, `CorrelationAnalyzer`, `PatternSynthesizer`, `Recommender`): ML-driven discovery

**Why**: Enables operator composition, centralized cataloging, versioning, and operator-as-agent serialization to EventCloud.

**Implementation Location**: `com.ghatana.core.operator.UnifiedOperator` interface with three concrete base classes:

- `StreamOperator extends BaseOperator` (for stream transformations)
- `PatternOperator extends BaseOperator` (for CEP patterns)
- `LearningOperator extends BaseOperator` (for ML/discovery)

**Non-Negotiable**:

- ❌ NO parallel operator hierarchies—all must converge to `UnifiedOperator`
- ❌ NO direct use of pattern-compiler Operators without adapter bridge
- ✅ MUST create `PatternOperatorAdapter` wrapping pattern-compiler `Operator` SPI
- ✅ MUST create `StreamOperatorAdapter` wrapping pattern-engine `StreamOperator`
- ✅ MUST support operator lifecycle (initialize, start, stop, isHealthy)
- ✅ MUST serialize operators to/from events (toEvent/fromEvent for EventCloud storage)

**Consequence**: Enables declarative pipelines, Git-like versioning, dynamic operator deployment.

---

### 2. **Operator Catalog as Central Registry (CRITICAL DECISION)**

**Decision**: Single authoritative registry for ALL operators (OperatorCatalog interface).

**Binding Requirements**:

- **Interface**: `com.ghatana.core.operator.OperatorCatalog`
- **Implementations**:
  - In-memory for tests: `InMemoryOperatorCatalog`
  - Production: `EventCloudOperatorCatalog` (backed by EventCloud events)
- **Operations**: CRUD, discovery (by type/capability/eventType), versioning, recommendations
- **Persistence**: Operators stored as events in EventCloud with version history
- **Package**: `com.ghatana.core.operator.*`

**Non-Negotiable**:

- ❌ NO direct operator registration outside OperatorCatalog
- ❌ NO operator instances without catalog entries
- ✅ MUST query catalog by operator ID, type, capability, version
- ✅ MUST support operator recommendations based on pipeline context
- ✅ MUST emit audit events for all catalog operations

**Consequence**: Centralized operator lifecycle management, discovery, version control.

---

### 3. **Pipeline Builder with Fluent API & YAML/JSON (CRITICAL DECISION)**

**Decision**: Pipelines are declared using fluent API and/or YAML/JSON, NEVER imperatively wired.

**Binding Requirements**:

- **Fluent API**: `com.ghatana.core.pipeline.PipelineBuilder`
  ```java
  Pipeline.create("fraud")
    .filter(e -> e.getType().equals("transaction"))
    .map(e -> enrichWithProfile(e))
    .detectSequence("login.failed", "transaction")
    .window(Duration.ofMinutes(1), events -> count(events))
    .onError((e, ex) -> deadLetterQueue(e))
    .build()
  ```
- **YAML Format**: Stages defined with operator references (from catalog)
- **JSON Format**: OpenAPI-compatible schema with validation
- **Serialization**: `toEvents()/fromEvents()` for EventCloud storage
- **Parser**: Full round-trip equivalence (parse → serialize → identical)

**Non-Negotiable**:

- ❌ NO procedural pipeline construction (no manual stage wiring)
- ❌ NO hard-coded operator instances in pipeline definitions
- ✅ MUST support branching (fan-out), merging (fan-in), error handling
- ✅ MUST support conditional routing and dead-letter queues
- ✅ MUST serialize pipelines to EventCloud as operator events
- ✅ MUST version control pipelines (Git-like branching)

**Consequence**: Pipelines become first-class data structures, stored in EventCloud, version-controlled, dynamically deployable.

---

### 4. **EventCloud Real-Time Tailing (CRITICAL DECISION)**

**Decision**: Operators subscribe to EventCloud and process events in real-time via EventCloudTailOperator.

**Binding Requirements**:

- **Operator**: `EventCloudTailOperator extends BaseOperator`
- **Subscription**: Uses `EventCloud.subscribe(tenant, selection, startAt)`
- **Consumption**: Partition-aware, offset tracking, auto-recovery on restart
- **Latency Target**: <10ms p99 from append to processing
- **Throughput Target**: 50k+ events/sec per node (Phase 2), 100k+ (Phase 4)
- **Backpressure**: Automatic throttling when downstream slower
- **Recovery**: Resume from last committed offset on operator restart

**Non-Negotiable**:

- ❌ NO batching to message queues (Kafka optional, not required)
- ❌ NO pulling from EventCloud by offset polling
- ✅ MUST use push-based EventCloud subscription
- ✅ MUST handle partition rebalancing
- ✅ MUST implement exponential backoff reconnection
- ✅ MUST track and emit partition lag metrics

**Consequence**: True real-time event processing with sub-10ms latency, no external queue broker required for MVP.

---

### 5. **Hybrid State Management (CRITICAL DECISION)**

**Decision**: All stateful operators use hybrid state stores (local + centralized).

**Binding Requirements**:

- **Interface**: `com.ghatana.core.state.StateStore<K, V>`
- **Local Stores** (fast, local recovery):
  - `InMemoryStateStore` (testing)
  - `FileBasedStateStore` (simple persistent)
  - `RocksDBStateStore` (high-performance embedded KV)
  - `H2StateStore` / `SQLiteStateStore` (embedded SQL)
- **Centralized Stores** (fault tolerance, cross-instance):
  - `RedisStateStore` (RECOMMENDED for production)
  - `PostgreSQLStateStore` (durable alternative)
  - `HazelcastStateStore` (distributed grid option)
- **Hybrid Mode**: `HybridStateStore(localStore, centralStore, syncStrategy)`
- **Sync Strategies**:
  - `IMMEDIATE`: Every write synced (safest, slowest)
  - `BATCHED`: Batch syncs (balanced, recommended)
  - `PERIODIC`: Every T milliseconds (fastest, eventual)
  - `ON_CHECKPOINT`: Only during checkpoint (maximum performance)
- **State Key Format**: `{tenant}:{operatorId}:{stateType}:{partition}:{key}`
- **Checkpoint Integration**: See STATE_MANAGEMENT.md for aligned barriers, savepoints, recovery

**Non-Negotiable**:

- ❌ NO operator state stored only in local memory (non-persistent)
- ❌ NO Redis-only (loses local recovery on restart)
- ❌ NO PostgreSQL-only (too slow for hot path)
- ✅ MUST use HybridStateStore for production stateful operators
- ✅ MUST partition state by tenant + operator + partition
- ✅ MUST implement TTL-based expiration
- ✅ MUST track state sync lag as metric
- ✅ MUST test local recovery and central fallback
- ✅ MUST support checkpoint/savepoint restoration

**Consequence**: Fast local access (~1ms), cross-instance sharing (100ms consistency), and fast recovery from checkpoints.

---

### 6. **ActiveJ Runtime Only (CRITICAL DECISION)**

**Decision**: All async/reactive code uses ActiveJ (Eventloop, Promise, Datastream) exclusively.

**Binding Requirements**:

- **Concurrency**: ActiveJ Eventloop (no Spring Reactor, Project Reactor, or coroutines)
- **Async**: ActiveJ Promise (not CompletableFuture, not Mono/Flux)
- **Streaming**: ActiveJ Datastream (not reactive streams, not RxJava)
- **HTTP**: ActiveJ HTTP for servers (not Spring WebFlux, not Netty direct)
- **RPC**: ActiveJ RPC (not gRPC, unless wrapped for external APIs)

**Package Standard**:

- Core async: Use `com.ghatana.core.async.*` abstractions
- HTTP servers: Use `com.ghatana.core.http.server.*`
- Observability: Use `com.ghatana.observability.*` (Micrometer/OTel abstraction)

**Non-Negotiable**:

- ❌ NO Spring Framework, Spring Boot, Spring WebFlux, or Spring Data
- ❌ NO Project Reactor, RxJava, or other reactive libraries
- ❌ NO mixed concurrency models (ActiveJ + Reactor = failure)
- ✅ MUST use ActiveJ Promise everywhere
- ✅ MUST use ActiveJ Eventloop for async execution
- ✅ MUST use ActiveJ Datastream for stream operations
- ✅ MUST respect EventLoop affinity (single thread per loop)
- ✅ MUST avoid blocking operations (use Promise.ofBlocking for DB/IO)

**Rationale**: Single concurrency model ensures predictable latency (<10ms p99), minimal GC pressure, and platform consistency.

**Consequence**: Lightweight, high-performance, predictable async execution without Spring/Reactor overhead.

---

### 7. **Micrometer + OpenTelemetry for Observability (CRITICAL DECISION)**

**Decision**: All metrics and tracing go through Micrometer + OpenTelemetry via core/observability abstractions.

**Binding Requirements**:

- **Metrics Interface**: `com.ghatana.observability.MetricsCollector`
- **Factory**: `MetricsCollectorFactory.create(MeterRegistry)`
- **Standard Metrics**:
  - `operator.process.count` (counter)
  - `operator.process.duration` (timer, track p99)
  - `operator.process.errors` (counter by error type)
  - `operator.state.size` (gauge)
  - `pipeline.throughput` (counter)
  - `pattern.match.count` (counter)
  - `eventcloud.append.rate` (counter)
- **Tracing**: OpenTelemetry spans for every operator process call with parent context
- **Logging**: Log4j2 JSON layout with SLF4J bridge, structured logging with requestId/traceId

**Non-Negotiable**:

- ❌ NO direct Micrometer API calls (MeterRegistry.counter() direct)
- ❌ NO Prometheus client library usage
- ❌ NO Logback (only Log4j2)
- ✅ MUST use MetricsCollector interface
- ✅ MUST emit tracing spans for distributed tracing
- ✅ MUST include tenant context in all metrics
- ✅ MUST implement SLA checker patterns (detect SLO breaches)
- ✅ MUST configure sampling for high-volume metrics (avoid cardinality explosion)

**Consequence**: Unified observability pipeline, consistent metric taxonomy, platform-level SLO enforcement.

---

### 8. **Pattern Learning with Apriori (Starting Algorithm)**

**Decision**: Start with Apriori for frequent sequence mining, evolve to PrefixSpan/SPADE later.

**Binding Requirements**:

- **Phase 3 Milestone**: Implement `FrequentSequenceMiner` using Apriori algorithm
- **Performance Target**: Mine patterns from 1M events in <5 minutes
- **Support/Confidence Metrics**: Calculate and track quality scores
- **Correlation Analysis**: `TemporalCorrelationAnalyzer` for event correlation
- **Synthesis**: `PatternSynthesizer` converts sequences → `UnifiedOperatorSpec` (SEQ + temporal constraints)
- **Recommendation**: `CollaborativeFilteringRecommender` suggests patterns to tenants

**Non-Negotiable**:

- ❌ NO deep learning (LSTM, transformer) for MVP (defer to Phase 4+)
- ❌ NO GPU requirements
- ✅ MUST start with Apriori (simple, proven)
- ✅ MUST support pluggable miners (SPI for algorithm swaps)
- ✅ MUST evaluate pattern quality (precision, recall, F1)
- ✅ MUST implement A/B testing framework
- ✅ MUST emit pattern.discovered events to EventCloud
- ✅ MUST track learning metrics (discovery time, recommendation relevance)

**Consequence**: Automated pattern discovery from real event data, user-driven feedback loop, adaptive learning platform.

---

### 9. **Multi-Tenant Isolation by Default (CRITICAL DECISION)**

**Decision**: Every component enforces tenant isolation at all layers.

**Binding Requirements**:

- **Event Layer**: Every event has tenantId in metadata
- **Operator Layer**: Operators scoped to tenant, partition by tenant+key
- **State Layer**: State keys prefixed with tenant ID
- **Pipeline Layer**: Pipelines owned by tenant, execution isolated
- **Catalog Layer**: Operators visible only within tenant's scope (unless shared explicitly)
- **Query Layer**: EventCloud queries filtered by tenant

**Non-Negotiable**:

- ❌ NO cross-tenant data access (even read)
- ❌ NO shared state between tenants
- ❌ NO global metrics (ALL metrics tagged with tenant)
- ✅ MUST extract tenant from context/headers early
- ✅ MUST propagate tenant through all async operations
- ✅ MUST enforce RBAC per tenant
- ✅ MUST redact sensitive data based on tenant policy
- ✅ MUST audit all tenant operations

**Consequence**: True multi-tenancy, data isolation, compliance-ready platform.

---

### 10. **Version 4 EventCloud Specification (BINDING)**

**Decision**: EventCloud design from `EventCloud_Architecture_and_Design_v4.md` (Appendix C) is authoritative.

**Binding Requirements**:

- **Event Model**: Event = metadata (ID/ETIME/STAT/tenant) + payload (key-value)
- **Canonical Modules**:
  - `:event-core:model` (Event, EventType, EventPattern, EventStream interfaces)
  - `:event-core:operators` (SEQ, AND, OR, NOT, REPEAT, WINDOW SPI)
  - `:event-core:engine` (Compile patterns → operator DAGs, low-latency matching)
  - `:event-core:learning` (Correlated group mining, adaptive learning)
  - `:event-core:repository` (Pattern/Operator versioning, activation)
  - `:event-core:ingress` (HTTP/gRPC event intake, validation, enrichment)
  - `:event-core:streams` (Stream manager, replay, checkpointing, DLQ)
  - `:event-core:query` (SQL-like queries → operator graph planner)
  - `:event-core:api` (REST/gRPC stable contracts)
  - `:event-core:security` (mTLS, JWT, encryption, redaction)
  - `:event-core:observability` (Metrics as events, traces, health)
- **Storage Tiers**:
  - M0: Off-heap ring buffers (ingest)
  - L0: Optional Kafka (fan-out)
  - M1: RocksDB (operator state)
  - L1: PostgreSQL partitions (hot log, recent events)
  - L4: Parquet (cold archive)
- **Concurrency Model**: ActiveJ Eventloop per shard, Promise-based async
- **API Contracts**: OpenAPI + Protobuf, strict versioning

**Non-Negotiable**:

- ✅ MUST follow module structure exactly
- ✅ MUST use ActiveJ for concurrency
- ✅ MUST implement multi-stage memory (M0→L1→L4)
- ✅ MUST use PostgreSQL L1 (no Kafka required for MVP)
- ✅ MUST implement checkpointing & DLQ
- ✅ MUST emit internal metrics as events to EventCloud

**Consequence**: Scalable, deterministic, auditable event processing core.

---

### 11. **Implementation Timeline: 20 Weeks (4 Phases)**

**Decision**: Implementation follows 20-week roadmap with 4 phases (binding milestones).

**Binding Requirements**:

- **Phase 1** (Weeks 1-4): Foundation
  - Unified operators, operator catalog, pipeline builder
  - Milestone: Demo fraud-detection pipeline working
- **Phase 2** (Weeks 5-8): Real-time
  - EventCloud tailing, hybrid state, distributed state manager
  - Milestone: 50k events/sec sustained, <10ms p99 latency
- **Phase 3** (Weeks 9-16): Learning
  - Sequence mining, correlation analysis, pattern synthesis, recommendations
  - Milestone: 1M events mined in <5 min, >80% recommendation relevance
- **Phase 4** (Weeks 17-20): Production hardening
  - Load testing, security audit, DR procedures, deployment guide
  - Milestone: 100k events/sec, all security checks passed, production-ready

**Non-Negotiable**:

- ❌ NO implementation before Phase 1 complete
- ❌ NO skipping phases (sequential delivery)
- ❌ NO timeline compression without scope reduction
- ✅ MUST track progress weekly
- ✅ MUST maintain acceptance criteria per phase
- ✅ MUST conduct design review before Phase 1 kickoff

**Consequence**: Structured, predictable delivery with clear milestones and go/no-go gates.

---

### 12. **No Duplication of Operator Implementations**

**Decision**: Extend or adapt existing operators; never duplicate.

**Binding Requirements**:

- **Before creating ANY new operator**:
  1. Search codebase for similar operator (grep in `multi-agent-system/pattern-engine/`, `core/domain-models/`, `contracts/`)
  2. Check if existing operator can be extended or adapted
  3. If creating new, document why existing options don't work
  4. Add comment referencing similar operators and explaining differences

**Non-Negotiable**:

- ❌ NO duplicate pattern operators (SEQ, AND, OR already exist)
- ❌ NO duplicate stream operators (Filter, Map already exist)
- ❌ NO custom implementations of standard operators
- ✅ MUST use adapters to bridge existing operators
- ✅ MUST consolidate into UnifiedOperator gradually
- ✅ MUST maintain backward compatibility during migration

**Consequence**: Clean operator taxonomy, no duplicate code, unified execution model.

---

### Summary Table: Binding Decisions

| Decision               | Location                                    | Owner        | Phase |
| ---------------------- | ------------------------------------------- | ------------ | ----- |
| Unified Operator Model | `com.ghatana.core.operator.*`               | Tech Lead    | 1     |
| Operator Catalog       | `com.ghatana.core.operator.OperatorCatalog` | Backend Lead | 1     |
| Pipeline Builder       | `com.ghatana.core.pipeline.*`               | Backend Lead | 1     |
| EventCloud Tailing     | `EventCloudTailOperator`                    | Backend Lead | 2     |
| Hybrid State           | `com.ghatana.core.state.*`                  | Backend Lead | 2     |
| ActiveJ Only           | All async code                              | Tech Lead    | ALL   |
| Micrometer+OTel        | `com.ghatana.observability.*`               | DevOps       | ALL   |
| Apriori Learning       | `FrequentSequenceMiner`                     | ML Engineer  | 3     |
| Multi-Tenant           | All layers                                  | Tech Lead    | ALL   |
| EventCloud v4          | `event-core:*`                              | Tech Lead    | ALL   |
| 20-Week Timeline       | 4 phases                                    | PM           | ALL   |
| No Duplication         | Code review                                 | Tech Lead    | ALL   |

---

### When in Doubt During Implementation

1. **Check EventCloud_Architecture_and_Design_v4.md** for event/pattern/learning model
2. **Check WORLD_CLASS_DESIGN_MASTER.md** for detailed component specs
3. **Check WORLD_CLASS_IMPLEMENTATION_CHECKLIST.md** for phase tasks
4. **Follow TEST_PLAN.md** for acceptance criteria
5. **Escalate to Tech Lead** if decision conflicts with above

## Migration & Cleanup Documentation

**Current Phase**: Phase 2 - Shared Module Consolidation

**Key Documents**:

- `SHARED_MODULES_MIGRATION_PLAN.md` - Comprehensive Phase 2 migration strategy for remaining `shared:*` modules
- `PHASE2_INCLUSION_STRATEGY.md` - Module inclusion priorities and unblocking strategy
- `ACTIVEJ_TEST_UTILS_MIGRATION_SUCCESS.md` - Phase 1 completion (ActiveJ test utils migration)
- `CODEBASE_ANALYSIS_AND_RECOMMENDATIONS.md` - Duplicate analysis and cleanup recommendations

**Migration Status**:

- ✅ Phase 1 Complete: 8 shared modules deleted (metrics, webhooks, exception, validation, common, observability, common-interfaces, test-utils)
- ⏸️ Phase 2 Planning: 4 major shared modules remaining (core, models, persistence, + evaluation candidates)
- 🔴 12 modules blocked awaiting migration (validation, catalog, eventlog, pattern-engine, etc.)

**Quick Reference**:

- Shared → Core mapping: See "Shared-to-Core Migration Mapping" section above
- New module checklist: Add to `settings.gradle.kts`, minimal `build.gradle`, depend on `core:*` only
- Before adding code: Search for duplicates in `core/*`, `contracts/*`, `dcmaar/platform/`

## Questions or unclear areas? Tell me which workflows or modules you plan to touch, and I'll refine these instructions with the exact patterns and commands used there.

## 🎯 CODE COMPLETION STANDARDS

### What is "Code Complete"?

**Code is NOT complete when:**

- ❌ It compiles and runs
- ❌ Tests pass
- ❌ It's deployed to production
  **Code IS complete only when:**
- ✅ Implementation written and tested
- ✅ All public APIs documented with JavaDoc
- ✅ @doc.\* metadata tags present
- ✅ Comprehensive unit tests with @DisplayName
- ✅ Non-obvious logic has explanatory comments
- ✅ Architecture role documented (core modules)
- ✅ Code review passed
- ✅ All validation checks passed

### Code Completion Checklist (REQUIRED for ALL code)

**Before committing ANY code, verify:**

#### Implementation (REQUIRED)

- [ ] Code compiles without warnings
- [ ] Follows project naming conventions (`com.ghatana.*` packages)
- [ ] No duplicate implementations (search `core/*`, `contracts/*` first)
- [ ] Single responsibility per class
- [ ] <300 lines per file (justify if longer)
