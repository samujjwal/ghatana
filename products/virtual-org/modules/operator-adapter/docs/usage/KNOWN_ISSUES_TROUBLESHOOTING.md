# Virtual-Org – Core Operator Adapter – Known Issues & Troubleshooting

## 1. Known Issues

- **Contract drift** – Changes in Virtual‑Org, operator, or protobuf schemas can break adapters.
- **Flaky tests** – Some historical tests were disabled due to protobuf API changes; these must be updated to match current contracts.

## 2. Troubleshooting Scenarios

### 2.1 Adapter Failures at Runtime

- **Checks**:
  - Verify module and contract versions.
  - Check logs for serialization or mapping errors.
  - Confirm observability configuration is correct for tracing and metrics.

### 2.2 Disabled or Failing Tests

- **Checks**:
  - Review excluded tests and update them to use the latest protobuf and operator APIs.
  - Re-enable tests once they reflect the current contracts.

This document is self-contained and lists common issues and mitigations for the core Operator Adapter library.
