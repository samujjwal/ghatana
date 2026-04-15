# Owner: Data-Cloud SDK

**Team:** Data-Cloud SDK Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-03-23  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Generates typed client SDKs from the Data-Cloud OpenAPI contract. SDKs are
generated at build time and are NOT committed to VCS.

### Generated Artifacts

| SDK | Language | Generator | Output |
|-----|----------|-----------|--------|
| Java | Java 8+ | `openapi-generator` (okhttp-gson) | `build/generated/java-sdk` |
| TypeScript | ES2020 | `openapi-generator` (fetch) | `build/generated/typescript-sdk` |
| Python | 3.9+ | `openapi-generator` (urllib3) | `build/generated/python-sdk` |

### Generation Commands

```bash
./gradlew :products:data-cloud:sdk:generateJavaSdk
./gradlew :products:data-cloud:sdk:generateTypescriptSdk
./gradlew :products:data-cloud:sdk:generatePythonSdk
./gradlew :products:data-cloud:sdk:generateAllSdks
```

## Source of Truth

`products/data-cloud/api/openapi.yaml` — always regenerate after updating the spec.

## Contract Stability

SDK generation MUST be triggered after every change to `openapi.yaml`. The CI
drift check ensures the spec stays in sync with the implementation.

## Consumers

- External API consumers (customers, integrations)
- Internal product teams needing typed clients
