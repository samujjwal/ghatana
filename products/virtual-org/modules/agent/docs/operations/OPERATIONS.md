# Virtual-Org – Java VirtualOrg-Agent Module – Operations Guide

## 1. Overview

`virtualorg-agent` is a library embedded in services. Operational concerns focus on how services use it.

## 2. Service Integration

- Services using this module should:
  - Configure agents through well-defined factories or configuration objects.
  - Propagate tenant and organization context into agent operations.

## 3. Monitoring

- Monitor services for:
  - Agent activity metrics (tasks handled, errors, escalation events).
  - Event emission rates.

## 4. Upgrades

- When upgrading this module:
  - Validate behavior of agents in staging.
  - Confirm event schemas and semantics remain compatible.

This guide is self-contained and documents operational considerations for services using `virtualorg-agent`.
