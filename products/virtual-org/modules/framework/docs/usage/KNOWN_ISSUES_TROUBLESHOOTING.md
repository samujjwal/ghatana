# Virtual-Org Java Framework – Known Issues & Troubleshooting

## 1. Known Issues

- **Behavioral differences between implementations** – Products may implement abstractions differently.

## 2. Troubleshooting Scenarios

### 2.1 Inconsistent Organization Behavior Across Services

- **Symptom**: Organization or workflow semantics differ between services.
- **Checks**:
  - Ensure all services depend on the same framework version.
  - Review how abstractions are extended or overridden.

### 2.2 Event Schema Mismatches

- **Symptom**: Consumers fail to parse organization events.
- **Checks**:
  - Verify event schemas and versions.
  - Run contract tests for event producers/consumers.

This document is self-contained and lists framework-level issues and mitigations.
