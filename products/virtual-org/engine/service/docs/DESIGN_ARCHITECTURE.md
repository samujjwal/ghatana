# Virtual-Org – Service – Design & Architecture

## 1. Purpose

The `virtual-org-service` application provides a **service layer** around the Virtual-Org framework. It exposes APIs to manage organizations, agents, workflows, and KPIs, and connects the framework to other products and UIs.

## 2. Responsibilities

- Provide HTTP and/or messaging APIs for Virtual-Org operations.
- Coordinate interactions between Virtual-Org framework modules and platform services.
- Expose data and events for dashboards and external consumers.

## 3. Architectural Layers

- **API layer** – HTTP or RPC endpoints for Virtual-Org operations.
- **Application layer** – Use cases coordinating framework operations.
- **Domain layer** – Uses Virtual-Org framework abstractions for organizations, agents, and workflows.
- **Infrastructure layer** – Integrations with event runtime, state, databases, and observability.

## 4. Interactions & Dependencies

- Depends on Virtual-Org framework modules and platform `libs/java/*`.
- Exposes APIs consumed by UIs and other services.

This document is self-contained and summarizes the architecture of the `virtual-org-service` application.
