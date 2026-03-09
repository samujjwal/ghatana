# DCMaar Server – Operations Guide

## 1. Overview

The DCMaar Server is the backend for ingesting, storing, and querying data. This guide covers deployment, configuration, monitoring, and incident response.

## 2. Deployment & Configuration

- Deploy via Docker/Docker Compose, Kubernetes, or binary + systemd.
- Configure via files and environment variables (Viper):
  - DB and ClickHouse connections.
  - Redis/cache endpoints.
  - OPA bundles and policy paths.
  - TLS certs/keys and mTLS rules.

## 3. Monitoring

- Monitor:
  - Request rates and latencies (gRPC and HTTP).
  - DB/clickhouse query performance and error rates.
  - OPA evaluation times and failures.
  - Resource usage and background jobs.

## 4. Incident Response

- On ingest issues: check agent connectivity, server logs, and storage availability.
- On query slowness: inspect DB metrics, indexes, and query plans.
- On policy failures: examine OPA logs, bundles, and policy syntax.

This guide is self-contained and documents operational considerations for the DCMaar Server.
