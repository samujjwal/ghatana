# Virtual-Org Java Framework – Design & Architecture (Enhanced)

## 1. Purpose

The Virtual-Org Java Framework provides **server-side abstractions and utilities** for modeling and orchestrating virtual organizations, agents, workflows, tasks, and KPIs.

**Version**: 2.0 - Enhanced Phase 1 Implementation  
**Last Updated**: 2025-11-16

## 2. Responsibilities

- Define core Java interfaces and base classes for organizations, departments, agents, tasks, and events.
- Integrate with the platform’s event runtime and AEP-aligned event models.
- Provide reusable patterns for workflows, HITL, and quality gates.

## 3. Architectural Position

- Sits at the **framework layer**:
  - Above core platform modules (`libs/java/*`).
  - Below product applications (YAPPC, Software-Org, etc.).
- Must not depend on product-specific code.

## 4. Layers

- **API layer** – Public Java interfaces and base classes.
- **Domain layer** – Organization, agent, task, and event models.
- **Integration layer** – Adapters for event runtime, observability, and state.

## 5. Interactions & Dependencies

- Uses `libs/java/http-server`, `observability`, `event-runtime`, `state`, and related modules where needed.
- Emits organization events compatible with the AEP and event core libraries.

This document is self-contained and summarizes the architecture of the Virtual-Org Java Framework module.
