# Tutorputor – Design & Architecture

## 1. Purpose

Tutorputor is a product area for **tutoring/assistant-style workflows** built on Ghatana’s event and service infrastructure. It coordinates contracts, services, and apps to provide guided experiences (such as tutorials, walkthroughs, or agent-assisted flows).

## 2. Responsibilities

- Define contracts that describe tutoring/assistant sessions and steps.
- Implement services that orchestrate these workflows.
- Provide apps that surface the experience to end users.

## 3. Architectural Structure

- `contracts/` – shared contracts and schemas for tutoring flows.
- `services/` – backend services implementing orchestration and state.
- `apps/` – UI or client-facing applications.

This document is self-contained and summarizes the role and architecture of the Tutorputor product.
