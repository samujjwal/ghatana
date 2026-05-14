# Product Shape Capability Matrix

This document shows which products can be represented by which lifecycle profiles and what capabilities they require.

Generated from:
- `config/canonical-product-registry.json`
- `config/product-lifecycle-profiles.json`
- `config/toolchain-adapter-registry.json`

## Matrix

| Product | Shape | Lifecycle Status | Required Kernel Capabilities | Status |
|---------|-------|------------------|------------------------------|--------|
| audio-video | backend-api (undefined) | disabled | None | Not enabled |
| aura | backend-api (undefined) | disabled | None | Not enabled |
| data-cloud | backend-api + sdk + web (undefined) | disabled | None | Not enabled |
| dcmaar | backend-api + web (undefined) | disabled | None | Not enabled |
| digital-marketing | backend-api + web (standard-web-api-product) | enabled | backend-api:gradle-java-service, web:pnpm-vite-react | Pilot |
| finance | backend-api + operator + portal + sdk (backend-only-java-service) | planned | backend-api:gradle-java-service | Shape-only |
| flashit | backend-api + mobile + web (mobile-plus-api-product) | planned | backend-api:gradle-java-service | Shape-only |
| phr | backend-api + web (standard-web-api-product) | planned | backend-api:gradle-java-service, web:pnpm-vite-react | Shape-only |
| security-gateway | backend-api (undefined) | disabled | None | Not enabled |
| software-org | backend-api (undefined) | disabled | None | Not enabled |
| tutorputor | backend-api + web (standard-web-api-product) | partial | backend-api:gradle-java-service, web:pnpm-vite-react | Unknown |
| virtual-org | backend-api (undefined) | disabled | None | Not enabled |
| yappc | backend-api + web (undefined) | disabled | None | Not enabled |

## Findings

### audio-video

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"

### aura

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"

### data-cloud

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"
- Surface "web" has no default adapter defined in profile "undefined"
- Surface "sdk" has no default adapter defined in profile "undefined"

### dcmaar

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"
- Surface "web" has no default adapter defined in profile "undefined"

### digital-marketing

- No deployment adapter defined in profile "standard-web-api-product"

### finance

- Surface "portal" has no default adapter defined in profile "backend-only-java-service"
- Surface "operator" has no default adapter defined in profile "backend-only-java-service"
- Surface "sdk" has no default adapter defined in profile "backend-only-java-service"

### flashit

- Surface "web" has no default adapter defined in profile "mobile-plus-api-product"
- Surface "mobile" has no default adapter defined in profile "mobile-plus-api-product"

### security-gateway

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"

### software-org

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"

### virtual-org

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"

### yappc

- Lifecycle profile "undefined" not found in product-lifecycle-profiles.json
- Surface "backend-api" has no default adapter defined in profile "undefined"
- Surface "web" has no default adapter defined in profile "undefined"
