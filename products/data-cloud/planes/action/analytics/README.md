# aep-analytics

## Purpose

`products/data-cloud/planes/action/analytics` provides AI-powered analytics and pattern detection for event streams. It owns:

- Pattern hashing and equivalence detection (`PatternHasher`)
- Schema-to-Java type mapping for analytical queries (`SchemaToJavaMapper`)
- AI gateway integration for LLM-assisted pattern recognition (`AIGateway`)
- Learning pipelines and validation of discovered patterns

## Boundaries

- **Uses:** `platform:java:ai-integration` for LLM connectivity; `aep-engine` for event and pattern models
- **Does not own:** raw event ingestion — that is `aep-engine`; compliance enforcement — that is `aep-compliance`
- **Does not own:** storage — patterns are persisted via `aep-registry`

## Key classes

| Class | Role |
|---|---|
| `AIGateway` | Thin typed adapter over the platform AI client for pattern analysis |
| `PatternHasher` | Stable content hash for deduplicating equivalent patterns |
| `SchemaToJavaMapper` | Converts event schema descriptors into typed Java field mappings |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:analytics:test
```
