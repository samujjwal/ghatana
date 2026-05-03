# yappc-integration

End-to-end integration tests for the YAPPC platform that validate cross-component wiring.

## Purpose

This module tests that all YAPPC components (Security Scanner, Knowledge Retrievers, DI modules) work together correctly when assembled.

## Components Tested

- `OsvScannerAdapter` — dependency vulnerability scanning
- `CompositeSecurityScanner` — SAST + dependency scanning composition  
- `SecurityServiceAdapter` — unified security service with new scanners
- `YappcBM25Retriever` — PostgreSQL FTS semantic search
- `YappcDenseVectorRetriever` — DataCloud vector search framework
- `KnowledgeModule` — DI configuration for retrievers
- `InfrastructureServiceModule` — DI configuration for security scanners

## Running Tests

```bash
# From monorepo root
./gradlew :products:yappc:integration:test

# From yappc product root  
../../gradlew :integration:test
```
