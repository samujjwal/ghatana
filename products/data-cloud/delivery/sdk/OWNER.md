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
| Java | Java 21+ | in-repo generator (`DataCloudSdkGeneratorMain`) | `build/generated/sdk/java` |
| TypeScript | ES2020 | in-repo generator (`DataCloudSdkGeneratorMain`) | `build/generated/sdk/typescript` |
| Python | 3.9+ | in-repo generator (`DataCloudSdkGeneratorMain`) | `build/generated/sdk/python` |

### Generation Commands

```bash
./gradlew :products:data-cloud:delivery:sdk:generateDataCloudSdks
./gradlew :products:data-cloud:delivery:sdk:check
```

## Source of Truth

`products/data-cloud/contracts/openapi/data-cloud.yaml` — always regenerate after updating the spec.

## Contract Stability

SDK generation MUST be triggered after every change to `contracts/openapi/data-cloud.yaml`. The CI
drift check ensures the spec stays in sync with the implementation.

## Consumers

- External API consumers (customers, integrations)
- Internal product teams needing typed clients
