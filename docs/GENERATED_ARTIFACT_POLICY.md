# Ghatana Repo — Generated Artifact Policy

**Status:** Active  
**Last Updated:** 2026-04-23  
**Owner:** Platform Architecture Team  
**Slack:** #platform-architecture  

---

## 1. Purpose

This document defines the repo-wide policy for generated artifacts: which outputs must be
version-controlled (source-of-truth), which must be excluded from version control (build
outputs), and which require explicit review before committing.

All generated outputs that are NOT source-of-truth MUST be listed in `.gitignore` and must
not appear in pull requests. CI enforces this via the generated-file leak detection gate.

---

## 2. Classification

### 2.1 Commit — Source-of-Truth (hand-authored, version-controlled)

| Type | Examples | Location |
|------|---------|---------|
| Protobuf definitions | `*.proto` | `platform/contracts/com/ghatana/` |
| OpenAPI specifications | `*.yaml` | `platform/contracts/openapi/`, `products/*/api/` |
| GraphQL schemas | `*.graphql` | `products/*/api/` |
| JSON Schema | `*.schema.json` | `platform/contracts/schemas/` |
| `buf.yaml` / `buf.gen.yaml` | Protobuf toolchain config | `platform/contracts/` |
| Flyway migration scripts | `V*__*.sql` | `products/*/src/main/resources/db/migration/` |
| Lock files | `pnpm-lock.yaml`, `gradle.lockfile` | repo root, product roots |
| CI workflow definitions | `*.yml` | `.github/workflows/` |
| Design token source | `tokens.json`, `tokens.ts` | `platform/typescript/tokens/src/` |

### 2.2 Never Commit — Derived Build Outputs

These artifacts are regenerated on every build and must not be committed:

| Type | Examples | Produced By | `.gitignore` Required |
|------|---------|------------|----------------------|
| Java proto stubs | `*.java` under `build/generated/` | `protoc` / `buf` | Yes |
| Proto descriptor sets | `*.bin`, `*.pb` | `buf build` | Yes |
| Compiled classes | `*.class` | `javac` / Gradle | Yes |
| Gradle JARs | `*.jar` in `build/libs/` | `jar` task | Yes |
| Gradle reports | `build/reports/` | static analysis / Jacoco | Yes |
| TypeScript build output | `dist/`, `*.js`, `*.d.ts` under `dist/` | `tsc` / Vite / Next.js | Yes |
| TypeScript type declarations | auto-generated `.d.ts` | `tsc` | Yes |
| pnpm cache | `.pnpm-store/`, `node_modules/` | `pnpm install` | Yes |
| Next.js cache | `.next/` | Next.js build | Yes |
| Coverage reports | `coverage/`, `*.lcov` | Vitest / Jacoco | Yes |
| OpenAPI client stubs | auto-generated under `build/generated-sources/` | `openapi-generator` | Yes |
| Playwright test results | `test-results/`, `playwright-report/` | Playwright | Yes |

### 2.3 Review-Required Before Committing

These artifacts may be committed after explicit review, following the process in §4:

| Type | Review By | Notes |
|------|----------|-------|
| New proto field or message | Architecture team | Breaking change check via `buf breaking` |
| New OpenAPI endpoint or schema | API contract owners | Must pass `contractCompatibilityGate` |
| New Flyway migration | DB team + owning product | Must be additive; no destructive changes without ADR |
| Updated `pnpm-lock.yaml` | Frontend platform team | Verify no unexpected transitive dependency changes |
| New design token | Design + platform frontend | Must follow token naming convention |

---

## 3. Module-Specific Rules

### 3.1 `platform/contracts`

See `platform/contracts/GENERATED_ARTIFACT_POLICY.md` for full detail.

- Commit: `.proto` files, OpenAPI `.yaml` files, `buf.yaml`, `buf.gen.yaml`.
- Never commit: Java proto stubs (`build/generated/source/proto/`), descriptor sets, compiled JARs.

### 3.2 `platform/typescript/*`

- Commit: `src/**/*.ts`, `src/**/*.tsx`, `package.json`, `tsconfig.json`, design token source.
- Never commit: `dist/`, `*.d.ts` under `dist/`, `.next/`, `coverage/`.

### 3.3 `products/*/db/migration/`

- Commit: Flyway `V*__*.sql` files — these are the schema source-of-truth.
- Never commit: Schema dumps, generated DDL from ORM introspection.

### 3.4 `products/data-cloud/docs/`

- Data Cloud no longer keeps product-generated narrative docs in a separate committed
  `docs-generated` tree. Keep canonical product docs under `products/data-cloud/docs/`
  and regenerate transient reports outside the source tree or under ignored build output.

---

## 4. Adding a New Generated Artifact Type

When a new toolchain generates artifacts in this repo:

1. Classify the output as source-of-truth or derived (see §2 above).
2. If derived: add the output path to the relevant `.gitignore` file.
3. Update the inventory table in §2.2 in this document.
4. Add a generated-file leak detection rule to `scripts/check-generated-files.sh`.
5. Verify CI passes on the `contractCompatibilityGate` Gradle task.

---

## 5. CI Enforcement

| Gate | Tool | Workflow | Trigger |
|------|-----|---------|---------|
| No committed generated proto stubs | `buf lint` + file leak detection | `.github/workflows/` | Every PR |
| Proto breaking change detection | `buf breaking` | `.github/workflows/` | Every PR vs main |
| OpenAPI contract validation | `contractCompatibilityGate` | `.github/workflows/` | Every PR |
| TypeScript `dist/` not committed | Git status check | `.github/workflows/` | Every PR |
| Flyway migration additive check | Custom script | `.github/workflows/` | Every PR |

---

## 6. Related Documents

- `platform/contracts/GENERATED_ARTIFACT_POLICY.md` — contracts-specific policy
- `docs/SECRETS_CLASSIFICATION.md` — secrets policy (secrets are not artifacts, handled separately)
- `docs/architecture/PROPAGATION_CONTRACTS.md` — propagation contract source-of-truth
- `.gitignore` (root) — canonical ignore list
