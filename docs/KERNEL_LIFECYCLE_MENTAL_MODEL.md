# Kernel Lifecycle Mental Model

## Core Concept: `pnpm kernel product <id> <phase>`

The Kernel lifecycle follows a simple, consistent mental model for executing lifecycle operations on products:

```bash
pnpm kernel product <product-id> <phase>
```

### Parameters

- **`<product-id>`**: The unique identifier of the product unit (e.g., `data-cloud`, `phr`, `digital-marketing`)
- **`<phase>`**: The lifecycle phase to execute (e.g., `validate`, `test`, `build`, `package`, `deploy`)

### Phases

| Phase | Purpose | Typical Gates |
|-------|---------|--------------|
| `validate` | Validate configuration, manifests, and toolchain readiness | registry-validation, manifest-validation, lifecycle-contract-validation, toolchain-preflight |
| `test` | Run unit and integration tests | typecheck, unit-test, integration-test |
| `build` | Compile and build artifacts | registry-validation, manifest-validation, typecheck, unit-test, conformance |
| `package` | Package artifacts for deployment | artifact-validation, security-scan, privacy-check |
| `deploy` | Deploy to target environment | environment-validation, health-check, observability-check |

### Examples

```bash
# Validate data-cloud product configuration
pnpm kernel product data-cloud validate

# Run tests for PHR
pnpm kernel product phr test

# Build digital-marketing product
pnpm kernel product digital-marketing build

# Package data-cloud for deployment
pnpm kernel product data-cloud package

# Deploy to production (requires release gate profile)
pnpm kernel product data-cloud deploy
```

### Surface-Specific Execution

To execute a phase on specific surfaces within a product:

```bash
pnpm kernel product <product-id> <phase> --surfaces <surface-1>,<surface-2>
```

Example:
```bash
# Build only backend-api surface
pnpm kernel product data-cloud build --surfaces backend-api

# Test both sdk and web surfaces
pnpm kernel product digital-marketing test --surfaces sdk,web
```

### Lifecycle Profiles

Products reference lifecycle profiles that define default surfaces, gates, and adapters. To use a specific profile:

```bash
pnpm kernel product <product-id> <phase> --profile <profile-name>
```

Common profiles:
- `standard-web-api-product`: For web applications with backend APIs
- `backend-only-java-service`: For pure Java backend services
- `standard-polyglot-product`: For polyglot fixture products (Rust, Python, TypeScript)
- `fast-gate-profile`: Quick feedback during development
- `focused-gate-profile`: Targeted checks on changed surfaces
- `nightly-gate-profile`: Comprehensive validation including e2e and performance
- `release-gate-profile`: Production deployment with strict gates and approvals

### Affected Surface Execution

To optimize execution by only running on affected surfaces:

```bash
pnpm kernel product <product-id> <phase> --affected
```

This uses surface hashing and dependency graphs to determine which surfaces need to be executed based on recent changes.

### Dry Run

To see what would be executed without actually running:

```bash
pnpm kernel product <product-id> <phase> --dry-run
```

### Verbose Output

To see detailed execution information:

```bash
pnpm kernel product <product-id> <phase> --verbose
```

### Environment-Specific Execution

To execute for a specific environment:

```bash
pnpm kernel product <product-id> <phase> --environment <env>
```

Example:
```bash
# Deploy to staging
pnpm kernel product data-cloud deploy --environment staging
```

## Mental Model Benefits

1. **Consistency**: Single command pattern for all lifecycle operations
2. **Simplicity**: Easy to remember and use: product + phase
3. **Discoverability**: Tab completion works for both product IDs and phases
4. **Flexibility**: Optional flags for advanced use cases (surfaces, profile, affected, environment)
5. **Safety**: Dry-run mode allows previewing before execution

## Error Recovery

When a lifecycle phase fails, the CLI provides:

1. **Failure classification**: Categorizes the failure (adapter, policy, interaction, etc.)
2. **Recovery actions**: Suggests concrete steps to resolve the issue
3. **Context**: Shows relevant error details and affected surfaces

Example:
```
❌ Build failed for surface backend-api
Category: adapter-execute
Source: gradle-java-adapter
Reason: build-failed

Recovery Actions:
1. Run `./gradlew build` to see detailed build errors
2. Fix compilation errors
3. Run `pnpm kernel product data-cloud build --dry-run` to validate fixes
Estimated duration: 5-15 minutes
Automated: false
```

## Best Practices

1. **Start with validate**: Always run `validate` before other phases to catch configuration issues early
2. **Use affected mode**: For large products, use `--affected` to save time during development
3. **Choose appropriate profile**: Use `fast-gate-profile` for quick feedback, `nightly-gate-profile` for comprehensive checks
4. **Check dry-run**: Use `--dry-run` before deploy operations to preview changes
5. **Review recovery actions**: When failures occur, follow the suggested recovery steps
