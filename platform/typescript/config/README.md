# @ghatana/config

Platform-level configuration management, environment variable validation, and feature flags for Ghatana applications.

## Installation

```json
"@ghatana/config": "workspace:*"
```

## API

### Environment Validation

Validates `process.env` against a Zod schema at startup. Fails fast on missing or invalid variables.

```ts
import { z } from "zod";
import { loadEnv, BaseEnvSchema } from "@ghatana/config";

const MyServiceEnv = BaseEnvSchema.extend({
  PORT: z.coerce.number().int().positive().default(3000),
  DATABASE_URL: z.string().url(),
  API_KEY: z.string().min(1),
});

export const env = loadEnv(MyServiceEnv);
// Throws ConfigValidationError immediately if DATABASE_URL is missing or malformed.
```

**`BaseEnvSchema`** — shared base for all services:

| Variable | Values | Default |
|----------|--------|---------|
| `NODE_ENV` | `development`, `production`, `test` | `development` |
| `LOG_LEVEL` | `error`, `warn`, `info`, `debug` | `info` |

### Typed Configuration Object

```ts
import { createConfig } from "@ghatana/config";
import { z } from "zod";

const AppConfigSchema = z.object({
  port: z.number().int().positive(),
  serviceName: z.string().min(1),
  debug: z.boolean().default(false),
});

const config = createConfig(AppConfigSchema, {
  port: 3000,
  serviceName: "my-service",
});

config.get().port;          // 3000
config.getKey("debug");     // false
config.validate();          // throws ConfigValidationError if invalid
```

### Schema Builders

```ts
import { nonEmptyString, urlString, portNumber, positiveInt } from "@ghatana/config";

const schema = z.object({
  name:    nonEmptyString(),   // z.string().min(1).trim()
  apiUrl:  urlString(),        // z.string().url()
  port:    portNumber(),       // z.number().int().min(1).max(65535)
  workers: positiveInt(),      // z.number().int().positive()
});
```

### Feature Flags

#### Boolean Flags

Simple on/off toggles:

```ts
import { createFeatureFlags } from "@ghatana/config";

const flags = createFeatureFlags({
  newDashboard: { type: "boolean", enabled: true },
  legacyMode:   { type: "boolean", enabled: false },
});

flags.isEnabled("newDashboard"); // true
```

#### Rollout Flags

Deterministic percentage-based rollout. Consistent for the same `userId`:

```ts
const flags = createFeatureFlags({
  betaFeature: { type: "rollout", percentage: 20 }, // 20% of users
});

flags.isEnabled("betaFeature", { userId: "user-123" }); // true or false, consistent
```

#### Variant Flags

A/B test or multi-variant experiments:

```ts
const flags = createFeatureFlags({
  theme: {
    type: "variant",
    variants: ["light", "dark", "system"] as const,
    default: "light",
    overrides: { dark: ["vip-user-1", "vip-user-2"] },
  },
});

flags.getVariant("theme", { userId: "vip-user-1" }); // "dark"
flags.getVariant("theme", { userId: "regular-user" }); // deterministic: "light" | "dark" | "system"
```

#### All Flags Snapshot

```ts
const all = flags.getAll({ userId: "u1" });
// { newDashboard: true, betaFeature: false, theme: "light" }
```

## Errors

All validation errors throw `ConfigValidationError` which includes the Zod `issues` array:

```ts
import { ConfigValidationError } from "@ghatana/config";

try {
  loadEnv(MyEnvSchema);
} catch (err) {
  if (err instanceof ConfigValidationError) {
    console.error(err.message);   // human-readable issue list
    console.error(err.issues);    // ZodIssue[] for programmatic handling
  }
}
```

## Tests

```bash
pnpm test          # run once
pnpm test:watch    # watch mode
```

26 tests covering: schema validation, env loading, boolean/rollout/variant flags, error messages.
