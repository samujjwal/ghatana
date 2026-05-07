# Tutorputor Web App – Design & Architecture

## 1. Purpose

The **Tutorputor Web** app is the primary web UI for Tutorputor. It allows learners, instructors, and admins to interact with Tutorputor services.

## 2. Responsibilities

- Provide views and flows for learning, assessment, analytics, and marketplace features.
- Communicate with the Tutorputor API Gateway and backend services.

## 3. Architectural Position

- Frontend app under `products/tutorputor/apps/tutorputor-web`.
- Consumes Tutorputor API Gateway endpoints.
- UI primitives, product-specific shared controls, and design tokens are governed by `libs/tutorputor-ui/USAGE_BOUNDARIES.md`.
- New shared primitives must be added to `@tutorputor/ui`; app-local `components/ui` files are treated as migration debt and monitored by `scripts/validate-ui-boundaries.mjs`.
- New color tokens should come from the shared theme/token layer. App-local hardcoded hex colors are capped by `config/ui-boundary-baseline.json` until the existing migration debt is removed.

This document is self-contained and summarizes the role and architecture of the Tutorputor Web app.
