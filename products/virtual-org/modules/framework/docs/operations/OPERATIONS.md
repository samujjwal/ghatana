# Virtual-Org Java Framework – Operations Guide

## 1. Overview

The Virtual-Org Java Framework itself is a library, not a standalone service. This guide focuses on how it should be operated when embedded in services.

## 2. Service Integration

- Services using the framework should:
  - Configure event runtime and state backends via shared modules.
  - Expose health checks tied to framework usage where relevant.

## 3. Monitoring

- Monitor:
  - Rates of organization and workflow events.
  - Agent activity metrics and KPI updates.
- Use platform-standard dashboards for event and workflow health.

## 4. Upgrades

- Treat framework upgrades as dependency bumps:
  - Validate existing services in staging.
  - Confirm compatibility with organization and event schemas.

This guide is self-contained and explains operational considerations for services embedding the Virtual-Org Java Framework.
