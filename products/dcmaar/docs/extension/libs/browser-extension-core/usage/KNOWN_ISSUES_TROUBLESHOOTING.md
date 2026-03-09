# DCMaar – Browser Extension Core – Known Issues & Troubleshooting

## 1. Known Issues

- **Pipeline misconfiguration** can lead to dropped or duplicated events.
- **Browser-specific differences** may affect behavior in some environments.

## 2. Troubleshooting Scenarios

### 2.1 Events Not Flowing As Expected

- **Checks**:
  - Verify that sources, processors, and sinks are correctly wired.
  - Check logs for errors thrown by pipeline components.

This document is self-contained and lists common issues and mitigations for `@dcmaar/browser-extension-core`.
