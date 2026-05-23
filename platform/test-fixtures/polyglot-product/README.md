# Polyglot Product Fixture

This fixture validates the Ghatana platform's ability to work with multiple language surfaces.

## Purpose

- Validate toolchain behavior for each supported language
- Ensure artifact manifests are generated correctly
- Test missing toolchain environment blocking
- Validate build/package/verify workflows

## Language Surfaces

- **Java**: `java-service/` - Spring Boot service
- **TypeScript Web**: `ts-web/` - React web application
- **TypeScript Node**: `ts-node-service/` - Node.js API service
- **Rust**: `rust-service/` - Rust binary/service
- **Python**: `python-worker/` - Python worker service

## Validation

Run the polyglot validation script:
```bash
node scripts/validate-polyglot-fixture.mjs
```

This checks:
- Each language surface has the required build configuration
- Toolchain detection and blocking behavior
- Artifact manifest generation
- Build/package/verify workflow execution
