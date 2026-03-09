# DCMaar Agent Daemon – Operations Guide

## 1. Overview

The Agent Daemon runs on end-user or fleet machines. This guide covers deployment, configuration, monitoring, and incident response.

## 2. Deployment & Configuration

- Deploy as part of platform installers or managed fleet tooling.
- Configure via `connector-config.toml` and environment variables:
  - Server endpoints and certificates.
  - Capture scopes and sampling.
  - Plugin allowlist and resource budgets.

## 3. Monitoring

- Monitor:
  - Connectivity to the DCMaar server.
  - Queue sizes and delivery latency.
  - CPU and memory usage versus budgets.
  - Plugin errors and sandbox violations.

## 4. Incident Response

- On connectivity issues: inspect logs, TLS configuration, and server health.
- On high resource usage: adjust sampling or disable heavy plugins.
- On privacy issues: verify capture configuration and redaction policies.

This guide is self-contained and documents operational considerations for the DCMaar Agent Daemon.
