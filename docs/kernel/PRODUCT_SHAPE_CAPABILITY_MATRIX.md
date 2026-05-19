# Product Shape Capability Matrix

This document shows which products can be represented by which lifecycle profiles and what capabilities they require.

Generated from:
- `config/canonical-product-registry.json`
- `config/product-lifecycle-profiles.json`
- `config/toolchain-adapter-registry.json`

## Matrix

| Product | Kind | Mode | Profile | Lifecycle Status | Readiness | Required Adapters | Reason Codes | Status |
|---------|------|------|---------|------------------|-----------|-------------------|--------------|--------|
| audio-video | shared-service | disabled-observed | not-declared | disabled | disabled | None | disabled-observed, requires-lifecycle-profile, requires-product-owner-executable-surface-definition | Disabled observed |
| aura | demo/example | disabled-observed | not-declared | disabled | disabled | None | demo-product-not-execution-ready, disabled-observed, requires-lifecycle-profile, requires-product-owner-executable-surface-definition | Disabled observed |
| data-cloud | platform-provider | disabled-observed | not-declared | disabled | disabled | None | disabled-observed, platform-provider-mode-required, requires-bootstrap-platform-separation, requires-runtime-truth-provider | Disabled observed |
| dcmaar | business-product | disabled-observed | not-declared | disabled | disabled | None | disabled-observed, requires-lifecycle-profile, requires-product-owner-executable-surface-definition, requires-security-threat-model-gates | Disabled observed |
| digital-marketing | business-product | execution | stable | enabled | executable | compose-local, gradle-java-service, pnpm-vite-react | execution-ready | Pilot |
| finance | business-product | shape-only | stable | planned | not-enabled | gradle-java-service | missing-adapter:portal, missing-adapter:sdk, planned-shape-only, requires-multi-module-build-validation, requires-portal-operator-sdk-adapters, requires-promotion-approval, requires-regulatory-gates | Shape-only |
| flashit | business-product | shape-only | experimental | planned | not-enabled | gradle-java-service, pnpm-vite-react | missing-adapter:mobile, planned-shape-only, requires-mobile-adapters, requires-mobile-bundle-artifacts, requires-personal-data-classification, requires-preview-security-gate | Shape-only |
| phr | business-product | execution | stable | enabled | executable | compose-local, gradle-java-service, pnpm-vite-react | consent-gate-active, execution-ready, fhir-r4-pilot-surfaces-validated, healthcare-pilot-enabled, pii-classification-active | Pilot |
| security-gateway | shared-service | disabled-observed | not-declared | disabled | disabled | None | disabled-observed | Disabled observed |
| software-org | demo/example | disabled-observed | not-declared | disabled | disabled | None | demo-product-not-execution-ready, disabled-observed, requires-lifecycle-profile, requires-product-owner-executable-surface-definition | Disabled observed |
| tutorputor | business-product | shape-only-with-known-limitations | stable | partial | not-enabled | gradle-java-service, pnpm-vite-react | partial-lifecycle, requires-content-safety-gates, requires-product-owner-executable-surface-definition | Shape-only with limitations |
| virtual-org | demo/example | disabled-observed | not-declared | disabled | disabled | None | demo-product-not-execution-ready, disabled-observed, requires-lifecycle-profile, requires-product-owner-executable-surface-definition | Disabled observed |
| yappc | platform-provider | disabled-observed | not-declared | disabled | disabled | None | artifact-intelligence-evidence-contracts-ready, creator-lifecycle-distinct-from-kernel, disabled-observed, platform-provider-mode-required | Disabled observed |

## Findings

### finance

- Surface "portal" has no default adapter defined in profile "backend-only-java-service"
- Surface "sdk" has no default adapter defined in profile "backend-only-java-service"

### flashit

- Surface "mobile" has no default adapter defined in profile "mobile-plus-api-product"
