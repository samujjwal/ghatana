# YAPPC Backend – Compliance Module – Known Issues & Troubleshooting

## 1. Known Issues

- **In-memory manager only** – The default `CompliancePolicyManager` is in-memory and must be wrapped/adapted for persistence.
- **Versioning assumptions** – Version bumping logic assumes a specific format (e.g., `major.minor`).

## 2. Troubleshooting Scenarios

### 2.1 Duplicate or Missing Acknowledgments

- **Symptom**: Users appear to have duplicate acknowledgments or are missing expected ones.
- **Checks**:
  - Verify how persistence is wired around the in-memory manager.
  - Confirm that acknowledgment calls are idempotent at the service layer.

### 2.2 Unexpected Version Numbers

- **Symptom**: Policy versions do not follow expected patterns.
- **Checks**:
  - Review version parsing and increment logic.
  - Align formatting and versioning rules with product requirements.

This document is self-contained and lists common Compliance module issues and mitigations.
