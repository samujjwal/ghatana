# DCMaar Framework – Server – Design & Architecture (Legacy Go Implementation)

> **Status:** This document describes the original Go-based DCMaar Server implementation. The target architecture standardizes on a **Java + Node.js** server stack; this Go server is considered legacy and is planned for decommissioning once the new stack fully covers ingest, policy, storage, and query responsibilities.

## 1. Purpose

The DCMaar Server is the **control plane and data plane backend** for the platform. It ingests telemetry from agents and extensions, applies policy, persists data, and serves APIs to desktop and other clients.

## 2. Responsibilities

- Accept gRPC/HTTP requests from agents and desktop.
- Enforce policy using OPA and configuration.
- Persist data to relational and analytical stores (SQLite/relational, ClickHouse).
- Provide query APIs for dashboards, desktop, and services.
- Expose metrics and traces via Prometheus and OpenTelemetry.

## 3. Architectural Position

- **Language**: Go 1.25+.
- **Key dependencies** (from `go.mod`):
  - Storage: `clickhouse-go/v2`, `go-sqlite3`.
  - Cache/queue: `go-redis/redis/v8`.
  - Policy: `open-policy-agent/opa`.
  - Transport: `grpc`, `protobuf`, HTTP (mux/otelhttp).
  - Observability: `otel`, `prometheus/client_golang`, `zap`.

## 4. High-Level Components

- `cmd/` – binaries and entrypoints.
- `internal/` – core services (ingest, query, policy, auth, jobs, etc.).
- `pkg/` – shared packages.
- `migrations/` – database schema migration files.

## 5. Design Constraints

- Contracts-first via protobuf (`dcmaar-pb`).
- gRPC deadlines and message size limits enforced per platform rules.
- Multi-tenant and privacy-aware by design; policy evaluated on every access.

This document is self-contained and summarizes the architecture and responsibilities of the DCMaar Server.
