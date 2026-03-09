# DCMaar – Plugin Extension – Known Issues & Troubleshooting

## 1. Known Issues

- **Environment variability** – Device metrics availability can vary by OS, browser, and hardware.
- **Resource usage** – Aggressive sampling can increase CPU/battery usage.

## 2. Troubleshooting Scenarios

### 2.1 Missing or Incomplete Metrics

- **Checks**:
  - Confirm the host app has enabled the relevant plugins.
  - Verify environment support for the requested metrics.

### 2.2 Excessive Resource Consumption

- **Checks**:
  - Review plugin sampling intervals and thresholds.
  - Inspect host app logs for plugin-related warnings.

This document is self-contained and lists common issues and mitigations for `@dcmaar/plugin-extension`.
