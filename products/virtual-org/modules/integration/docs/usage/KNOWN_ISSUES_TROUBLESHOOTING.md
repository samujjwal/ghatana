# Virtual-Org – Integration Library – Known Issues & Troubleshooting

## 1. Known Issues

- **Contract drift** – Changes in Virtual‑Org, operator, AEP, or domain-model schemas can break integrations.

## 2. Troubleshooting Scenarios

### 2.1 Mapping Failures or Runtime Errors

- **Checks**:
  - Compare contract versions across modules.
  - Validate ID and schema assumptions used in integration helpers.

### 2.2 Performance Issues Across Boundaries

- **Checks**:
  - Measure latency across Virtual‑Org ↔ operator ↔ AEP flows.
  - Identify heavy mappings or unnecessary calls.

This document is self-contained and lists common issues and mitigations for the Virtual‑Org Integration library.
