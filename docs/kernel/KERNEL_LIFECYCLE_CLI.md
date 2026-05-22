# Kernel Lifecycle CLI

## Mental Model

The Kernel lifecycle follows a simple, consistent mental model:

```
pnpm kernel product <id> <phase> [options]
```

### Parameters

- **`<id>`**: The product identifier (e.g., `phr`, `finance`, `data-cloud`)
- **`<phase>`**: The lifecycle phase to execute (e.g., `validate`, `plan`, `build`, `test`, `package`, `deploy`, `verify`)

### Lifecycle Phases

| Phase | Purpose | Output |
|-------|---------|--------|
| `validate` | Validate product configuration and surface definitions | Validation report |
| `plan` | Generate execution plan for a phase | Execution plan (dry-run) |
| `build` | Build the product surfaces | Build artifacts |
| `test` | Run tests for the product surfaces | Test results |
| `package` | Package the product for deployment | Deployment package |
| `deploy` | Deploy the product to an environment | Deployment status |
| `verify` | Verify the deployment in an environment | Verification report |

### Surface-Specific Execution

To target a specific surface within a product:

```
pnpm kernel product <id> <phase> --surface <surface-id>
```

**Example:**
```bash
# Build only the web surface of PHR
pnpm kernel product phr build --surface web

# Test only the backend-api surface of Finance
pnpm kernel product finance test --surface backend-api
```

### Environment-Specific Deployment

To deploy to a specific environment:

```
pnpm kernel product <id> deploy --env <environment>
```

**Example:**
```bash
# Deploy PHR to local environment
pnpm kernel product phr deploy --env local

# Deploy Finance to staging
pnpm kernel product finance deploy --env staging
```

### Planning Before Execution

The `plan` phase allows you to preview what will happen before executing:

```bash
# Plan a build to see what will be executed
pnpm kernel product phr plan build

# Plan a deployment to see what will change
pnpm kernel product phr plan deploy --env staging
```

## Common Workflows

### Full Product Lifecycle

```bash
# Validate the product configuration
pnpm kernel product phr validate

# Build all surfaces
pnpm kernel product phr build

# Test all surfaces
pnpm kernel product phr test

# Package for deployment
pnpm kernel product phr package

# Deploy to local
pnpm kernel product phr deploy --env local

# Verify the deployment
pnpm kernel product phr verify --env local
```

### Surface-Specific Development

```bash
# Plan and build only the web surface
pnpm kernel product phr plan build --surface web
pnpm kernel product phr build --surface web

# Test the web surface
pnpm kernel product phr test --surface web
```

### Development Mode

For iterative development, use the `dev` phase (if supported by the adapter):

```bash
pnpm kernel product phr dev --surface web
```

## Product Aliases

The monorepo also provides convenience aliases for common operations:

```bash
# PHR aliases
pnpm build:phr
pnpm test:phr
pnpm dev:phr-web
pnpm validate:phr
pnpm package:phr

# Finance aliases
pnpm build:finance-gateway
pnpm test:finance-gateway
```

These aliases are shortcuts for the underlying `pnpm kernel product` commands.

## Key Principles

1. **Consistency**: All products use the same phase names and command structure
2. **Surfaces**: Products can have multiple surfaces (backend-api, web, mobile, etc.)
3. **Planning**: Always plan before executing to understand the impact
4. **Validation**: Validate configuration before building to catch errors early
5. **Environments**: Deployments are environment-aware (local, staging, production)

## Integration with Studio

Studio uses the same lifecycle model under the hood. When you trigger lifecycle operations from the Studio UI, it executes the same `pnpm kernel product` commands with the appropriate parameters.

## Troubleshooting

### "Product not found"
Ensure the product is registered in `config/canonical-product-registry.json`.

### "Surface not found"
Check the product's `kernel-product.yaml` for the list of available surfaces.

### "Adapter not found"
Ensure the required toolchain adapter is registered in `config/toolchain-adapter-registry.json`.

### "Phase not supported"
Not all adapters support all phases. Check the adapter documentation for supported phases.
