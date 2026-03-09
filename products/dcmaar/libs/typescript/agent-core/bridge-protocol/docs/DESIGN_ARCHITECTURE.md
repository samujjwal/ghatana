# DCMaar Agent Core – Bridge Protocol – Design & Architecture

## 1. Purpose

`@dcmaar/bridge-protocol` defines **shared message contracts** for the middleware bridge between the DCMaar Desktop app and the Browser Extension. It centralizes all TypeScript types and validation schemas for this channel.

## 2. Responsibilities

- Provide strongly typed request/response and event payloads.
- Offer runtime validation (via Zod) for messages crossing the bridge.
- Avoid any transport- or UI-specific logic.

## 3. Architectural Position

- Pure TypeScript library built with `tsc`, tested with Vitest.
- Consumed by the desktop, extension, and any bridge middleware.
- Forms a contract layer between frontends and platform services.

## 4. Design Constraints

- Backwards-compatible message evolution (additive changes where possible).
- Types must remain in sync with server/agent contracts where applicable.

This document is self-contained and summarizes the architecture and role of `@dcmaar/bridge-protocol`.
