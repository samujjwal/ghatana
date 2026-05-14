# Product Shape Capability Matrix

This document shows which products can be represented by which lifecycle profiles and what capabilities they require.

Generated from:
- `config/canonical-product-registry.json`
- `config/product-lifecycle-profiles.json`
- `config/toolchain-adapter-registry.json`

## Matrix

| Product | Kind | Mode | Profile | Lifecycle Status | Required Kernel Capabilities | Status |
|---------|------|------|---------|------------------|------------------------------|--------|
| audio-video | shared-service | disabled-observed | not-declared | disabled | None | Disabled observed |
| aura | demo/example | disabled-observed | not-declared | disabled | None | Disabled observed |
| data-cloud | platform-provider | disabled-observed | not-declared | disabled | None | Disabled observed |
| dcmaar | business-product | disabled-observed | not-declared | disabled | None | Disabled observed |
| digital-marketing | business-product | execution | stable | enabled | backend-api:gradle-java-service, deploy:compose-local, web:pnpm-vite-react | Pilot |
| finance | business-product | shape-only | stable | planned | backend-api:gradle-java-service | Shape-only |
| flashit | business-product | shape-only | experimental | planned | backend-api:gradle-java-service | Shape-only |
| phr | business-product | shape-only | stable | planned | backend-api:gradle-java-service, web:pnpm-vite-react | Shape-only |
| security-gateway | shared-service | disabled-observed | not-declared | disabled | None | Disabled observed |
| software-org | demo/example | disabled-observed | not-declared | disabled | None | Disabled observed |
| tutorputor | business-product | shape-only-with-known-limitations | stable | partial | backend-api:gradle-java-service, web:pnpm-vite-react | Shape-only with limitations |
| virtual-org | demo/example | disabled-observed | not-declared | disabled | None | Disabled observed |
| yappc | platform-provider | disabled-observed | not-declared | disabled | None | Disabled observed |

## Findings

### audio-video

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json

### aura

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json

### data-cloud

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json
- Adapter "undefined" for surface "web" not found in toolchain-adapter-registry.json
- Adapter "undefined" for surface "sdk" not found in toolchain-adapter-registry.json

### dcmaar

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json
- Adapter "undefined" for surface "web" not found in toolchain-adapter-registry.json

### finance

- Surface "portal" has no default adapter defined in profile "backend-only-java-service"
- Surface "operator" has no default adapter defined in profile "backend-only-java-service"
- Surface "sdk" has no default adapter defined in profile "backend-only-java-service"

### flashit

- Surface "web" has no default adapter defined in profile "mobile-plus-api-product"
- Surface "mobile" has no default adapter defined in profile "mobile-plus-api-product"

### security-gateway

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json

### software-org

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json

### virtual-org

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json

### yappc

- Adapter "undefined" for surface "backend-api" not found in toolchain-adapter-registry.json
- Adapter "undefined" for surface "web" not found in toolchain-adapter-registry.json
