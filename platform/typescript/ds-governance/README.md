# @ghatana/ds-governance

## Purpose

`@ghatana/ds-governance` provides the governance gates for the Ghatana design system, including naming rules, duplication detection, compatibility checks, and contribution policies.

## Dependencies

- `@ghatana/ds-schema` for schema-level validation inputs
- `@ghatana/ds-registry` for registry-backed governance and compatibility checks
- `zod` for typed runtime validation

## Usage

Import governance helpers from the root package or subpath exports:

```ts
import * as Governance from '@ghatana/ds-governance';
import * as Naming from '@ghatana/ds-governance/naming';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ds-governance build
pnpm --filter @ghatana/ds-governance test
```

## Public API Surface

- Naming governance under `./naming`
- Duplication checks under `./duplication`
- Compatibility gates under `./compatibility`
- Contribution-rule helpers under `./contribution`