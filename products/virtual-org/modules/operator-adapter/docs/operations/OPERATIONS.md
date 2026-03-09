# Virtual-Org – Core Operator Adapter – Operations Guide

## 1. Overview

The core Operator Adapter library is embedded in services that connect Virtual‑Org agents to the platform operator framework and observability stack. Operations focus on configuration and monitoring of these services.

## 2. Configuration

- Configure, at the service level:
  - Which agents are exposed as operators.
  - Timeouts, retries, and circuit-breaking policies.
  - Observability settings (metrics, traces, log verbosity).

## 3. Monitoring

- Monitor:
  - Operator invocation success/failure rates.
  - Latency of adapter calls.
  - Serialization and contract mismatch errors.

## 4. Incident Response

- On adapter-related issues:
  - Inspect logs and metrics for mapping or contract drift.
  - Validate that all modules (Virtual‑Org, operator, domain models, protobuf) are using compatible versions.

This guide is self-contained and documents operational considerations for services using the core Operator Adapter library.
