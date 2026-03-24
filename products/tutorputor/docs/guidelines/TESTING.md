# Tutorputor – Testing Guidelines

> Last updated: 2026-01-19

## 1. Goals

- Every module ships with tests that cover business logic, API contracts, and critical failure paths.
- No stub or mock data is silently returned in production error paths.
- All async service code is covered by unit tests with real mock expectations.
- Coverage thresholds are enforced in CI (see §6).

---

## 2. Test Infrastructure

| Package                        | Runner              | Environment | Config               |
| ------------------------------ | ------------------- | ----------- | -------------------- |
| `services/tutorputor-platform` | Vitest              | Node        | `vitest.config.ts`   |
| `apps/tutorputor-web`          | Vitest              | jsdom       | `vitest.config.ts`   |
| `apps/api-gateway`             | Vitest              | Node        | `vitest.config.ts`   |
| `contracts`                    | Vitest              | Node        | `vitest.config.ts`   |
| `tests/e2e`                    | Vitest / Playwright | Node        | project-level config |

---

## 3. Running Tests

### Run all tests for a specific package

```bash
# Platform service
cd products/tutorputor/services/tutorputor-platform
pnpm test

# Front-end (student app)
cd products/tutorputor/apps/tutorputor-web
pnpm test

# Admin app
cd products/tutorputor/apps/tutorputor-admin
pnpm test

# API gateway
cd products/tutorputor/apps/api-gateway
pnpm test
```

### Run with coverage

```bash
pnpm test --coverage
```

### Run a single test file

```bash
pnpm vitest run src/modules/learning/pathways-service.test.ts
```

### Run tests in watch mode (development)

```bash
pnpm vitest
```

### Run all packages from the monorepo root

```bash
# From products/tutorputor/
pnpm --filter "./services/**" test
pnpm --filter "./apps/**" test
```

---

## 4. Test Categories

### 4.1 Unit Tests

Scope: single service, hook, or utility in isolation.

Location: co-located with source — `src/**/__tests__/*.test.ts` or `src/**/*.test.ts`

Pattern:

```typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { createMyService } from "../my-service";

const mockPrisma = {
  myModel: {
    findFirst: vi.fn(),
    create: vi.fn(),
  },
} as any;

describe("MyService", () => {
  let service: ReturnType<typeof createMyService>;

  beforeEach(() => {
    vi.clearAllMocks();
    service = createMyService(mockPrisma);
  });

  it("returns the expected result for valid input", async () => {
    mockPrisma.myModel.findFirst.mockResolvedValue({ id: "1", title: "Test" });
    const result = await service.getById("1");
    expect(result?.id).toBe("1");
  });

  it("throws when the record is not found", async () => {
    mockPrisma.myModel.findFirst.mockResolvedValue(null);
    await expect(service.getById("missing")).rejects.toThrow("not found");
  });
});
```

### 4.2 Integration Tests

Scope: multi-layer flows exercised against a real (in-memory) database or a test server.

Location: `tests/integration/*.test.ts`

Notes:

- Use Vitest with `environment: "node"`.
- Spin up a real Fastify instance with `createServer()` from `apps/api-gateway`.
- Seed test data via Prisma client pointing at a SQLite test database (`DATABASE_URL=file:./test.db`).

Pattern:

```typescript
import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { build } from "../../apps/api-gateway/src/createServer";

let app: Awaited<ReturnType<typeof build>>;

beforeAll(async () => {
  app = await build({ logger: false });
  await app.ready();
});

afterAll(async () => {
  await app.close();
});

describe("POST /api/v1/modules", () => {
  it("creates a module and returns 201", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/api/v1/modules",
      headers: { Authorization: `Bearer ${TEST_JWT}` },
      payload: { title: "New Module", domain: "PHYSICS" },
    });
    expect(res.statusCode).toBe(201);
    expect(res.json().id).toBeDefined();
  });
});
```

### 4.3 React Component / Hook Tests

Location: co-located — `src/**/*.test.tsx`

Tools: Vitest + jsdom + `@testing-library/react`

Pattern:

```typescript
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi } from "vitest";
import { useContent } from "../useContent";

const wrapper = ({ children }: { children: React.ReactNode }) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
};

describe("useContent", () => {
  it("fetches experiences from the API", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ data: [{ id: "1", title: "T" }], pagination: {} }),
    }));

    const { result } = renderHook(() => useContent(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.data?.experiences[0].id).toBe("1");
  });
});
```

### 4.4 Contract Tests

Scope: Validate that TypeScript types exported from `contracts/v1` match runtime shapes.

Location: `contracts/src/**/*.test.ts`

---

## 5. Authentication in Tests

### JWT test fixture

Generate a signed test token without hitting the real auth server:

```typescript
import jwt from "@fastify/jwt";

// In your test helper
export function createTestJwt(payload: {
  userId: string;
  tenantId: string;
}): string {
  return jwt.sign(payload, process.env.JWT_SECRET ?? "test-secret-dev", {
    expiresIn: "1h",
  });
}

export const TEST_JWT = createTestJwt({
  userId: "test-user-1",
  tenantId: "test-tenant",
});
```

### Fastify inject with auth

```typescript
const res = await app.inject({
  method: "GET",
  url: "/api/v1/modules",
  headers: { Authorization: `Bearer ${TEST_JWT}` },
});
```

### Frontend fetch mock with auth

```typescript
vi.stubGlobal(
  "fetch",
  vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    if (
      !init?.headers ||
      !(init.headers as Record<string, string>)["Authorization"]
    ) {
      return Promise.resolve({
        ok: false,
        status: 401,
        json: async () => ({}),
      });
    }
    return Promise.resolve({ ok: true, json: async () => ({ data: [] }) });
  }),
);
```

---

## 6. Coverage Requirements

Thresholds are enforced in each package's `vitest.config.ts`:

| Metric     | Threshold |
| ---------- | --------- |
| Statements | 90%       |
| Branches   | 85%       |
| Functions  | 90%       |
| Lines      | 90%       |

Run coverage report:

```bash
pnpm test --coverage
# HTML report at: coverage/index.html
```

---

## 7. Test Naming Conventions

- Describe block: name the module/service/hook being tested — e.g. `describe("PathwaysService")`.
- `it` / `test` names: use plain English describing the expected behaviour — e.g. `it("throws when pathway is not found")`.
- Use `describe` nesting for method-level grouping: `describe("generatePathway") > it("uses AI when available")`.
- Prefix negative cases with `throws` or `returns null` or `rejects` to clarify failure expectations.

---

## 8. Error Path Testing (Critical)

All service functions that call external systems MUST have tests covering:

1. **API 4xx** — service throws, not returns stub data.
2. **Network failure** — `fetch` throws `TypeError: Failed to fetch`, service propagates the error.
3. **Malformed response** — JSON parse failure is caught and re-thrown.

```typescript
it("throws when the API returns 500", async () => {
  mockFetch.mockResolvedValue({
    ok: false,
    status: 500,
    statusText: "Server Error",
  });
  await expect(fetchTemplates({ page: 1 })).rejects.toThrow(
    "Failed to fetch simulation templates",
  );
});

it("throws on network failure", async () => {
  mockFetch.mockRejectedValue(new TypeError("Failed to fetch"));
  await expect(fetchTemplates({ page: 1 })).rejects.toThrow("Failed to fetch");
});
```

---

## 9. E2E Tests

Location: `tests/e2e/`

Runner: Vitest (smoke tests) or Playwright (full browser flows)

```bash
# Smoke tests (Vitest, no browser)
cd products/tutorputor
pnpm vitest run tests/e2e/

# Playwright (requires a running server)
cd products/tutorputor
pnpm playwright test
```

---

## 10. CI Enforcement

Tests run automatically on every PR via GitHub Actions. A PR cannot be merged if:

- Any test fails.
- Coverage drops below the thresholds defined in §6.
- `pnpm tsc --noEmit` reports type errors.

See `.github/workflows/ci.yml` for the full pipeline configuration.
