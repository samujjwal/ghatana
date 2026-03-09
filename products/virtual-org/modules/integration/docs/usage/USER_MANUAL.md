# Virtual-Org – Integration Library – User Manual

## 1. Audience

This manual is for backend engineers wiring Virtual‑Org into the broader platform using the Integration library.

## 2. Basic Usage

1. Add the Integration library as a dependency in your service.
2. Use integration helpers to:
   - Map Virtual‑Org events to operator/AEP inputs.
   - Map results/value streams back into Virtual‑Org or domain models.
3. Configure environment-specific endpoints and credentials via your service’s configuration.

## 3. Best Practices

- Keep integration logic thin in services; rely on shared helpers here.
- Avoid duplicating mappings across services.

This manual is self-contained and explains how to use the Virtual‑Org Integration library in typical flows.
