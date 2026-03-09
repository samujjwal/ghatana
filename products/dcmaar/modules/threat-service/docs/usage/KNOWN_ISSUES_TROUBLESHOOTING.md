# DCMaar Server – Known Issues & Troubleshooting

## 1. Known Issues

- **Schema or migration drift** can cause query failures or ingest errors.
- **Policy misconfiguration** can block legitimate requests.

## 2. Troubleshooting Scenarios

### 2.1 Ingest Failures

- **Checks**:
  - Agent logs for connectivity errors.
  - Server logs for validation or policy denials.
  - DB/ClickHouse availability.

### 2.2 Slow Queries

- **Checks**:
  - Query patterns and filters.
  - DB metrics and query plans.

This document is self-contained and lists common issues and mitigations for the DCMaar Server.
