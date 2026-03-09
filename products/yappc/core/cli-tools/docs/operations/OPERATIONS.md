# YAPPC Core – KG CLI Tools – Operations Guide

## 1. Overview

KG CLI Tools are used by engineers and operators to inspect and manage Knowledge Graph data. This guide covers recommended usage patterns and operational considerations.

## 2. Usage Contexts

- Local development: inspecting graph content, debugging ingestion issues.
- CI/CD: running checks or migrations as part of pipelines.

## 3. Configuration

- Use configuration files or environment variables to control:
  - Target environments/endpoints.
  - Authentication or credentials (never hard-coded in scripts).

## 4. Safety

- Provide and prefer read-only commands for inspection.
- When performing write operations, encourage dry-runs and backups where possible.

This guide is self-contained and describes operational considerations for KG CLI Tools.
