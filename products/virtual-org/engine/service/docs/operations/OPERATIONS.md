# Virtual-Org – Service – Operations Guide

## 1. Overview

The `virtual-org-service` application is the backend entry point for Virtual-Org operations. This guide covers deployment, configuration, and monitoring.

## 2. Deployment & Configuration

- Deploy alongside other core services.
- Configure:
  - HTTP ports and TLS.
  - Connections to event runtime and state/storage backends.
  - Observability endpoints and sampling.

## 3. Monitoring

- Track:
  - Request rates and error rates per endpoint.
  - Event emission/consumption metrics.
  - Workflow and agent activity metrics where available.

## 4. Incident Response

- For elevated error rates, check:
  - Recent deployments.
  - Configuration changes.
  - Health of Virtual-Org framework modules and dependencies.

This guide is self-contained and documents operational practices for `virtual-org-service`.
