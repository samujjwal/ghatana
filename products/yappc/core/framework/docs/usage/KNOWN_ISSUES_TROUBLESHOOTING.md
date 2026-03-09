# YAPPC Core – Framework – Known Issues & Troubleshooting

## 1. Known Issues

- **Service behavior tied to framework defaults** – Changes in defaults can affect multiple services.

## 2. Troubleshooting Scenarios

### 2.1 Unexpected HTTP or Observability Behavior After Upgrade

- **Symptom**: Logging, metrics, or error responses change after upgrading the framework.
- **Checks**:
  - Review release notes for behavioral changes.
  - Compare configuration before and after upgrade.
- **Actions**:
  - Adjust service overrides where needed.
  - Pin to a previous version if necessary while investigating.

This document is self-contained and covers common issues when upgrading or using the YAPPC Core Framework.
