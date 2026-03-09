# Contract Testing Guide - Data Cloud UI

## Overview

Contract tests validate that the API responses from the backend match the expected schema defined in the frontend. This ensures frontend-backend compatibility and catches breaking changes early.

## Why Contract Testing?

- **Early Detection**: Catch API contract violations before deployment
- **Type Safety**: Ensure runtime data matches TypeScript types
- **Documentation**: Contract tests serve as living documentation
- **Confidence**: Deploy with confidence that APIs won't break the UI

## Setup

### Dependencies

```bash
npm install -D vitest zod
```

### Configuration

Contract tests use Vitest and Zod for schema validation.

## Running Contract Tests

```bash
# Run all contract tests
npm run test:contract

# Run in watch mode
npm run test:contract:watch

# Run with coverage
npm run test:contract:coverage
```

## Writing Contract Tests

### 1. Define Schema

Use Zod to define the expected API response schema:

```typescript
import { z } from "zod";

const UserSchema = z.object({
  id: z.string(),
  email: z.string().email(),
  name: z.string(),
  role: z.enum(["admin", "user"]),
  createdAt: z.string(),
});
```

### 2. Test Response Schema

Validate that API responses match the schema:

```typescript
import { describe, it, expect } from "vitest";

describe("Users API Contract", () => {
  it("should match user schema", () => {
    const apiResponse = {
      id: "user-1",
      email: "test@example.com",
      name: "Test User",
      role: "user",
      createdAt: "2024-01-01T00:00:00Z",
    };

    const result = UserSchema.safeParse(apiResponse);
    expect(result.success).toBe(true);
  });

  it("should reject invalid response", () => {
    const invalidResponse = {
      id: "user-1",
      email: "invalid-email", // Invalid email
      name: "Test User",
      role: "invalid-role", // Invalid enum
    };

    const result = UserSchema.safeParse(invalidResponse);
    expect(result.success).toBe(false);
  });
});
```

### 3. Test Request Schema

Validate request payloads:

```typescript
const CreateUserRequestSchema = z.object({
  email: z.string().email(),
  name: z.string().min(1),
  role: z.enum(["admin", "user"]),
});

it("should accept valid create request", () => {
  const request = {
    email: "new@example.com",
    name: "New User",
    role: "user",
  };

  const result = CreateUserRequestSchema.safeParse(request);
  expect(result.success).toBe(true);
});
```

## Best Practices

### 1. Mirror TypeScript Types

Keep Zod schemas in sync with TypeScript types:

```typescript
// TypeScript type
export interface User {
  id: string;
  email: string;
  name: string;
  role: "admin" | "user";
  createdAt: string;
}

// Zod schema (should match exactly)
const UserSchema = z.object({
  id: z.string(),
  email: z.string().email(),
  name: z.string(),
  role: z.enum(["admin", "user"]),
  createdAt: z.string(),
});

// Validate they match
type InferredUser = z.infer<typeof UserSchema>;
const _typeCheck: User = {} as InferredUser; // Should not error
```

### 2. Test Edge Cases

Include tests for edge cases and validation rules:

```typescript
it("should reject empty name", () => {
  const request = {
    email: "test@example.com",
    name: "", // Empty string
    role: "user",
  };

  const result = CreateUserRequestSchema.safeParse(request);
  expect(result.success).toBe(false);
});

it("should reject invalid email format", () => {
  const request = {
    email: "not-an-email",
    name: "Test",
    role: "user",
  };

  const result = CreateUserRequestSchema.safeParse(request);
  expect(result.success).toBe(false);
});
```

### 3. Test Optional Fields

Ensure optional fields are handled correctly:

```typescript
const UserSchema = z.object({
  id: z.string(),
  email: z.string(),
  phone: z.string().optional(), // Optional field
});

it("should accept response without optional field", () => {
  const response = {
    id: "user-1",
    email: "test@example.com",
    // phone is omitted
  };

  const result = UserSchema.safeParse(response);
  expect(result.success).toBe(true);
});
```

### 4. Test Pagination

Validate paginated response structure:

```typescript
const PaginatedResponseSchema = <T extends z.ZodTypeAny>(itemSchema: T) =>
  z.object({
    items: z.array(itemSchema),
    total: z.number(),
    page: z.number(),
    pageSize: z.number(),
    hasMore: z.boolean(),
  });

it("should match paginated response schema", () => {
  const response = {
    items: [{ id: "1", name: "Item 1" }],
    total: 1,
    page: 1,
    pageSize: 10,
    hasMore: false,
  };

  const ItemSchema = z.object({ id: z.string(), name: z.string() });
  const result = PaginatedResponseSchema(ItemSchema).safeParse(response);
  expect(result.success).toBe(true);
});
```

## Integration with Backend

### Consumer-Driven Contracts

Contract tests can be shared with the backend team:

1. **Export Schemas**: Export Zod schemas for backend validation
2. **Generate OpenAPI**: Use tools like `zod-to-openapi` to generate API specs
3. **Automated Validation**: Backend can validate responses against schemas

### Example Integration

```typescript
// shared/schemas/user.schema.ts
export const UserSchema = z.object({
  id: z.string(),
  email: z.string().email(),
  name: z.string(),
});

// Backend can use the same schema
import { UserSchema } from "@shared/schemas/user.schema";

app.get("/users/:id", (req, res) => {
  const user = getUserById(req.params.id);

  // Validate response matches contract
  const validated = UserSchema.parse(user);
  res.json(validated);
});
```

## Continuous Integration

### GitHub Actions Example

```yaml
name: Contract Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm ci
      - run: npm run test:contract
      - name: Upload coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage/coverage-final.json
```

## Test Organization

```
tests/
└── contract/
    ├── collections.contract.test.ts
    ├── workflows.contract.test.ts
    ├── entities.contract.test.ts
    └── schemas/
        ├── collection.schema.ts
        ├── workflow.schema.ts
        └── entity.schema.ts
```

## Troubleshooting

### Schema Mismatch

If a test fails due to schema mismatch:

1. Check if backend API changed
2. Update TypeScript types
3. Update Zod schema
4. Update contract test

### Type Inference Issues

Use `z.infer` to get TypeScript type from Zod schema:

```typescript
const UserSchema = z.object({
  id: z.string(),
  name: z.string(),
});

type User = z.infer<typeof UserSchema>;
```

## Resources

- [Zod Documentation](https://zod.dev)
- [Vitest Documentation](https://vitest.dev)
- [Contract Testing Guide](https://martinfowler.com/bliki/ContractTest.html)

## Next Steps

1. Add contract tests for all API endpoints
2. Share schemas with backend team
3. Integrate into CI/CD pipeline
4. Monitor for contract violations
