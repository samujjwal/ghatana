# YAPPC – Operations Guide

## 1. What is Being Operated

YAPPC includes backend services and the app-creator frontend. Operations covers:

- Backend service health (HTTP, observability, storage).
- App-creator availability and responsiveness.

## 2. Dependencies & Pre-Conditions

- Ensure required services are running:
  - YAPPC backend services.
  - Datastores and search/indexing services.
- Ensure frontend and backend versions are compatible.

## 3. Configuration

Configuration typically includes:

- Backend service endpoints and credentials.
- Feature flags and state-management settings.
- Observability and logging configuration.

## 4. Health & Monitoring

- Monitor at least:

- Backend:
  - HTTP success/error rates, latency, resource usage.
  - Observability metrics via shared telemetry abstractions.
- Frontend:
  - Uptime and error logs.
  - Key performance indicators (page load, interaction latency) where available.

### 4.1 Example Signals

- Increases in backend 5xx or 4xx rates for core APIs.
- Elevated response times for key endpoints used by the app-creator.
- Increases in client-side errors (JavaScript errors, failed API calls).

## 5. Deployment & Rollout

- Deploy backend and frontend together or with clear compatibility guarantees.
- Use staged rollout or canary environments when releasing major changes.

### 5.1 Rollout Practices

- Use a staging environment with realistic data to validate backend and app-creator behavior.
- Validate critical flows (requirements, refactoring, knowledge graph operations, canvas flows) before promoting changes.

## 6. Incident Response

- For backend issues:
  - Check logs, metrics, and recent deployments.
- For frontend issues:
  - Verify build artifacts and CDN or hosting configuration.

For incidents that span both backend and frontend:

- Confirm version compatibility between services and the app-creator.
- Check that feature flags and configuration are aligned across environments.

This guide is self-contained and describes how to operate YAPPC as a product.
