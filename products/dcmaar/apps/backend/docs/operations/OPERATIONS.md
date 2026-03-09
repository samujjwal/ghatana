# Guardian – Backend API – Operations Guide

## 1. Overview

`@guardian/backend` is a Node.js/TypeScript API server. Operations cover running, testing, and monitoring.

## 2. Running & Deploying

- Use `dev` for development (TSX watch).
- Use `build` and `start` for production deployments.
- Run DB migrations and seeds via `db:migrate`, `db:seed`.

## 3. Monitoring

- Monitor metrics via Prometheus exporters and traces via OpenTelemetry.
- Track errors through Sentry.

This guide is self-contained and documents operational considerations for the Guardian Backend API.
