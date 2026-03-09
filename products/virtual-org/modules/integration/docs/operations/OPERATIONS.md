# Virtual-Org – Integration Library – Operations Guide

## 1. Overview

The Virtual‑Org Integration library is embedded in services. Operations focus on how those services configure and monitor cross-module integrations.

## 2. Service Integration

- Services using this library should:
  - Clearly define which integration helpers they use and for what flows.
  - Configure connections to operator runtimes, AEP components, and domain model stores via platform configuration.

## 3. Monitoring

- Monitor, at the service level:
  - Success/failure rates of cross-module flows.
  - Latency across Virtual‑Org ↔ operator ↔ AEP boundaries.
  - Serialization or mapping errors.

## 4. Incident Response

- On integration issues:
  - Inspect logs for mapping and contract mismatch errors.
  - Verify that schemas and versions are aligned across modules.

This guide is self-contained and documents operational considerations for services that use the Virtual‑Org Integration library.
