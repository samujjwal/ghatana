# DCMaar – Browser Extension UI – Design & Architecture

## 1. Purpose

`@dcmaar/browser-extension-ui` provides **reusable React UI components** and styles for DCMaar browser extensions, built on top of `@dcmaar/browser-extension-core`.

## 2. Responsibilities

- Define extension-specific layout, panels, and controls.
- Provide shared visual components and styles for use across extension UIs.

## 3. Architectural Position

- React-based UI library (`peerDependencies`: React + ReactDOM).
- Depends on `@dcmaar/browser-extension-core` for pipeline behavior.
- Consumed by concrete extension apps like `@dcmaar/device-health-extension`.

This document is self-contained and summarizes the architecture and role of `@dcmaar/browser-extension-ui`.
