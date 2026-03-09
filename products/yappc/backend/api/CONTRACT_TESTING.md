# OpenAPI Contract Testing for YAPPC API

This directory contains contract tests for the YAPPC API using [Schemathesis](https://schemathesis.readthedocs.io/), a property-based testing tool for OpenAPI specifications.

## Overview

Contract tests verify that the YAPPC API implementation conforms to its OpenAPI specification (`openapi.yaml`). These tests:

- **Validate request/response schemas** — ensures API responses match declared schemas
- **Test edge cases** — uses property-based testing to generate diverse inputs
- **Catch breaking changes** — detects when implementation diverges from spec
- **Document API behavior** — spec serves as executable documentation

## Prerequisites

```bash
# Install Schemathesis
pip install schemathesis==3.27.1

# Or use pipx for isolated installation
pipx install schemathesis
```

## Running Tests Locally

### Option 1: Against Running Server

If you already have the YAPPC API running on `localhost:8080`:

```bash
cd products/yappc/backend/api
./test-contracts.sh
```

### Option 2: Start Server Automatically

The script can start the server, run tests, and stop the server:

```bash
cd products/yappc/backend/api
./test-contracts.sh --start-server
```

### Option 3: Custom Base URL

Test against a different environment:

```bash
cd products/yappc/backend/api
./test-contracts.sh --base-url https://api.yappc.staging.ghatana.com
```

## CI/CD Integration

Contract tests run automatically in GitHub Actions on:
- Pull requests touching `products/yappc/backend/api/**`
- Pushes to `main` or `develop` branches
- Manual workflow dispatch

See `.github/workflows/contract-tests.yml` for the full CI configuration.

## Test Configuration

The contract tests use the following Schemathesis settings:

| Setting | Value | Purpose |
|---------|-------|---------|
| `--checks all` | All checks enabled | Validates status codes, schemas, headers, content-type |
| `--hypothesis-max-examples 50` | 50 examples per endpoint | Balances coverage vs speed |
| `--hypothesis-deadline 5000` | 5 second timeout | Prevents hanging on slow endpoints |
| `--workers 4` | 4 parallel workers | Speeds up test execution |
| `--exitfirst` | Stop on first failure | Fail fast for quicker feedback |

## Interpreting Results

### Successful Run
```
✅ All contract tests passed!
```

All endpoints conform to the OpenAPI spec.

### Failed Run
```
❌ Contract tests failed with exit code 1
```

Check the output for:
- **Schema validation errors** — response doesn't match declared schema
- **Status code mismatches** — endpoint returns unexpected status codes
- **Missing required fields** — response omits required properties
- **Type errors** — field has wrong type (e.g., string instead of integer)

## Common Issues

### 1. Server Not Ready
```
[ERROR] API did not become ready after 60 seconds
```

**Solution:** Ensure PostgreSQL is running and the API can connect to the database.

### 2. Schema Validation Failure
```
Schema validation error: 'id' is a required property
```

**Solution:** Update the API implementation to include the missing field, or update `openapi.yaml` if the spec is incorrect.

### 3. Status Code Mismatch
```
Expected status code 200, got 500
```

**Solution:** Fix the API implementation to return the correct status code, or update the spec if 500 is valid.

## Updating the OpenAPI Spec

When adding or modifying API endpoints:

1. **Update `openapi.yaml`** with the new endpoint definition
2. **Run contract tests locally** to verify the spec is correct
3. **Commit both spec and implementation** in the same PR

## Advanced Usage

### Generate Test Report

```bash
schemathesis run openapi.yaml \
  --base-url http://localhost:8080 \
  --report \
  --junit-xml contract-test-results.xml
```

### Test Specific Endpoint

```bash
schemathesis run openapi.yaml \
  --base-url http://localhost:8080 \
  --endpoint /api/v1/requirements
```

### Increase Test Coverage

```bash
schemathesis run openapi.yaml \
  --base-url http://localhost:8080 \
  --hypothesis-max-examples 200
```

## References

- [Schemathesis Documentation](https://schemathesis.readthedocs.io/)
- [OpenAPI Specification](https://spec.openapis.org/oas/v3.0.3)
- [Hypothesis Property-Based Testing](https://hypothesis.readthedocs.io/)
