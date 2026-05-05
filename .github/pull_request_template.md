<!--
Thank you for contributing to EventCloud! Please make sure to read the [Contributing Guidelines](.github/CONTRIBUTING.md) before opening a pull request.
-->

## Description

<!-- 
Please include a summary of the change and which issue is fixed. Please also include relevant motivation and context. 
List any dependencies that are required for this change.
-->

Fixes # (issue)

## Type of Change

Please delete options that are not relevant.

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Code style update (formatting, local variables)
- [ ] Refactoring (no functional changes, no API changes)
- [ ] Build/CI/CD changes
- [ ] Other (please describe): 

## How Has This Been Tested?

Please describe the tests that you ran to verify your changes. Provide instructions so we can reproduce. Please also list any relevant details for your test configuration

- [ ] Unit Tests
- [ ] Integration Tests
- [ ] Manual Testing

## Test Configuration

- Java Version: 
- Operating System: 
- Dependencies: 

## Checklist

- [ ] My code follows the style guidelines of this project
- [ ] I have performed a self-review of my own code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes
- [ ] Any dependent changes have been merged and published in downstream modules

## Release-Truth Checklist

<!-- Complete this section for every change that touches production code, APIs, UI, or infrastructure. -->

### Production Quality
- [ ] No production mocks, stubs, placeholders, or TODO/FIXME comments in production code paths
- [ ] No disabled tests on the main branch (`.skip`, `@Disabled` must reference an open issue)
- [ ] All TypeScript code is fully typed — no `any`, no untyped parameters, no missing interfaces
- [ ] All public Java APIs include required JavaDoc and `@doc.*` tags

### Security and Tenant Boundaries
- [ ] Tenant/auth boundary tests are included (missing `X-Tenant-Id`, invalid token, cross-tenant isolation)
- [ ] Inputs validated at system boundaries; no user-controlled data flows to dangerous operations unvalidated
- [ ] No hardcoded secrets, credentials, or unsafe defaults introduced

### Contracts and API Alignment
- [ ] UI/backend/OpenAPI contract are aligned — spec updated if endpoints changed
- [ ] No breaking changes to public APIs without a migration or compatibility strategy
- [ ] UI clients are generated or manually checked against current OpenAPI if client code was modified

### Observability and Operations
- [ ] Observability (structured logs, metrics, traces, correlation IDs) included for new/changed critical flows
- [ ] Restart durability covered if persistence or state management was touched
- [ ] Health/readiness semantics remain correct for deployable services

### Design-System and Feature-Gating
- [ ] Design-system usage covered if UI was touched — raw HTML controls replaced with design-system components
- [ ] Optional features are capability-gated; no unavailable feature appears active in the UI
- [ ] Incomplete features use feature flags; no partial implementation ships without a gate

### Cross-Product Boundaries
- [ ] Existing platform modules (`platform/java/*`, `platform/typescript/*`) checked before creating new abstractions
- [ ] No direct cross-product peer imports (AEP ↔ Data Cloud must use SPI/contracts only)
- [ ] Shared platform packages remain product-agnostic

## Additional Information

<!-- Add any other relevant information about the pull request here. -->
