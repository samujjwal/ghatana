# Kernel CLI Mental Model

## Overview

The Ghatana Kernel provides a unified CLI interface for kernel-backed product lifecycle operations using the `pnpm kernel` command pattern.

## Kernel-Backed vs Non-Kernel Products

### Kernel-Backed Products

Products that are fully integrated with the Ghatana Kernel use the `pnpm kernel` pattern:
- **PHR** (Personal Health Record)
- **Digital Marketing** (DMOS)

### Non-Kernel Products

Products that are not yet fully kernel-integrated use the `pnpm product` pattern:
- **Finance**
- **FlashIt**
- **Data Cloud**
- **YAPPC**
- **Audio-Video**
- **DCMAAR**
- **TutorPutor**
- **Security Gateway**

## Command Pattern for Kernel-Backed Products

### Basic Syntax

```bash
pnpm kernel <productId> <phase> [options]
```

### Examples

```bash
# Build PHR product
pnpm kernel phr build

# Test Digital Marketing product
pnpm kernel digital-marketing test

# Deploy Digital Marketing to local environment
pnpm kernel digital-marketing deploy --env local

# Plan execution without running
pnpm kernel digital-marketing build --dry-run

# Explain what will be executed
pnpm kernel phr build --explain
```

## Valid Phases

The following lifecycle phases are supported:

- `create` - Initial product scaffolding
- `bootstrap` - Bootstrap kernel providers
- `dev` - Development phase
- `validate` - Validation checks
- `test` - Test execution
- `build` - Build artifacts
- `package` - Package artifacts
- `release` - Release preparation
- `deploy` - Deployment
- `verify` - Post-deployment verification
- `promote` - Environment promotion
- `rollback` - Rollback deployment
- `operate` - Operational tasks
- `retire` - Product retirement

## Common Options

- `--dry-run` - Plan execution without running adapters
- `--explain` - Show detailed execution plan
- `--json` - Output results as JSON
- `--surface <surfaceId>` - Target specific surface
- `--env <environment>` - Target environment (default: local)
- `--mode <bootstrap|platform>` - Provider mode (default: bootstrap)

## Product Aliases

For convenience, product-specific shortcuts are available:

```bash
# PHR
pnpm kernel phr build
pnpm kernel phr test
pnpm kernel phr deploy --env local

# Digital Marketing
pnpm kernel digital-marketing build
pnpm kernel digital-marketing test
pnpm kernel digital-marketing deploy --env local

# Finance
pnpm kernel finance build
pnpm kernel finance test

# Data Cloud
pnpm kernel data-cloud build
pnpm kernel data-cloud test
```

## Planning Mode

To plan execution without running:

```bash
pnpm kernel <productId> <phase> --dry-run
```

Or use the explicit plan mode:

```bash
pnpm kernel plan <productId> <phase>
```

## Recovery Mode

To get recovery guidance for failed executions:

```bash
pnpm kernel recover <productId> <phase>
```

## Status Mode

To check product lifecycle status:

```bash
pnpm kernel status <productId>
```

## Mental Model Benefits

1. **Consistency**: Single command pattern across all products
2. **Discoverability**: `pnpm kernel --help` shows all options
3. **Composability**: Works with pnpm workspace filtering
4. **Safety**: Dry-run and explain modes prevent accidental executions
5. **Traceability**: All executions produce run IDs and correlation IDs

## Enforcement

The `check:kernel-cli-mental-model` script enforces that:
1. All product lifecycle scripts use `pnpm kernel` pattern
2. No direct calls to `node scripts/kernel-product.mjs` exist in package.json
3. The pattern is documented and consistent across the repo
