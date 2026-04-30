# Owner: Data-Cloud Platform Config

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Tech lead:** Data-Cloud Platform Lead  
**Last reviewed:** 2026-04-29  
**ADR:** ADR-DC-001-MODULE-OWNERSHIP

## Responsibility

Configuration loading, validation, and schema definitions for the Data-Cloud product.
Centralises all environment variable parsing, config object construction, and
config-schema validation so that mis-configuration fails fast at startup.

No runtime logic outside configuration concerns belongs here.

## Key Interfaces

| Class | Purpose |
|-------|---------|
| `DataCloudConfig` | Top-level product configuration record |
| `ConfigLoader` | Validates and loads config from environment/YAML |
| `ConfigValidator` | Business-rule validation for config values |

## Dependencies

- `platform:java:core` — base config infrastructure
- Zod-equivalent validation (Java: custom validators)

## Consumers

- `products:data-cloud:platform-launcher` — loads config at startup
- `products:data-cloud:feature-store-ingest` — `FeatureIngestConfig`
