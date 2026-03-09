# DCMaar – Shared UI Core – Design & Architecture

## 1. Purpose

`@dcmaar/shared-ui-core` provides **framework-agnostic design tokens, types, hooks, and utilities** for DCMaar UI surfaces.

## 2. Responsibilities

- Define design tokens (colors, spacing, typography) for DCMaar.
- Provide shared UI-related types and hooks.
- Offer utilities that multiple UIs (desktop, web, extension) can reuse.

## 3. Architectural Position

- TypeScript module exporting tokens, types, hooks, and utilities.
- Serves as a base for higher-level UI packages (e.g., React-specific components).

This document is self-contained and summarizes the architecture and role of `@dcmaar/shared-ui-core`.
