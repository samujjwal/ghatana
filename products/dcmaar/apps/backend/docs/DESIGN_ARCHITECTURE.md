# Guardian – Backend API – Design & Architecture

## 1. Purpose

`@guardian/backend` is the **Guardian Parental Control Backend API**. It exposes authentication, policy, and monitoring endpoints for Guardian apps.

## 2. Responsibilities

- Provide HTTP APIs using Fastify for Guardian clients (dashboard, mobile, desktop).
- Implement custom authentication and authorization flows (JWT, sessions).
- Persist data in Postgres and cache in Redis.
- Expose metrics and traces via OpenTelemetry and Prometheus.

## 3. Architectural Position

- Node.js/TypeScript backend using Fastify + PG + Redis.
- Uses OpenTelemetry for observability and Sentry for error tracking.

This document is self-contained and summarizes the architecture and role of the Guardian Backend API.
