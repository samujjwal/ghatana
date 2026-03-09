# YAPPC Backend – Compliance Module – Operations Guide

## 1. Overview

The Compliance module is a backend building block embedded in services. Operational concerns focus on how services use this logic to enforce and report on compliance.

## 2. Service Integration

- Use Compliance domain types in services that:
  - Present policies to end users.
  - Track acknowledgments.
  - Generate compliance reports.
- Persist Compliance data using the service’s persistence layer; this module itself is in-memory/domain-only.

## 3. Monitoring & Metrics

- At the service level, track:
  - Policy acknowledgment coverage per framework and per tenant.
  - Policies due for review.
  - Policy lifecycle events (created, updated, archived).

## 4. Configuration

- Externalize configuration such as:
  - Retention duration for archived policies.
  - Required frameworks and default review cycles.

## 5. Incident Response

- For inconsistent compliance data:
  - Inspect service logs where Compliance operations are invoked.
  - Rebuild reports from source-of-truth stores if needed.

This guide is self-contained and documents operational considerations when using the Compliance module in services.
