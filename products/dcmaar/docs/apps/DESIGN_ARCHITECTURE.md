# DCMaar – Applications – Design & Architecture

## 1. Purpose

This document describes the **application layer** of the DCMaar platform: agent-facing apps, operator dashboards, and parental-control experiences (Guardian, Device Health, and related UIs).

## 2. Responsibilities

- Present data and controls from the DCMaar backend and agents to end-users.
- Coordinate with shared libraries (`libs/*`) and frameworks (`framework/*`) without duplicating logic.
- Respect security, privacy, and performance budgets defined at the platform level.

## 3. Architectural Position

- Apps are thin layers over:
  - **Frameworks**: agent-daemon, desktop, browser-extension.
  - **Backend services**: DCMaar server, Guardian backend.
  - **Libraries**: shared UI packages, agent-core, browser-extension-core, connectors.

This document is self-contained and summarizes the role of applications in the DCMaar architecture.
