# DCMaar Agent Daemon – Known Issues & Troubleshooting

## 1. Known Issues

- **TLS/certificate misconfiguration** can prevent the agent from reaching the server.
- **Heavy plugins or capture settings** can push CPU/memory beyond budgets.

## 2. Troubleshooting Scenarios

### 2.1 Agent Cannot Reach Server

- **Checks**:
  - Verify server endpoint and certificates.
  - Inspect logs for TLS or DNS errors.

### 2.2 High Resource Consumption

- **Checks**:
  - Review enabled plugins and capture scopes.
  - Examine metrics for hotspots.

This document is self-contained and lists common issues and mitigations for the DCMaar Agent Daemon.
