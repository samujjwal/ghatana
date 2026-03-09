# DCMaar – Agent UI – Design & Architecture

## 1. Purpose

`@dcmaar/agent-ui` provides **shared React UI components** for DCMaar agent applications (desktop, dashboards, and related UIs).

## 2. Responsibilities

- Define reusable, styled components for agent-centric workflows.
- Encapsulate design and interaction patterns common to agent-facing UIs.

## 3. Architectural Position

- React component library built with `tsup` (CJS/ESM outputs) and Storybook.
- Peer dependency on React/ReactDOM; no direct app wiring or data loading.

This document is self-contained and summarizes the architecture and role of `@dcmaar/agent-ui`.
