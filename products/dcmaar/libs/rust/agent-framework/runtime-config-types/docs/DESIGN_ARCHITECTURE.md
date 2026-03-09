# DCMaar Agent Framework – runtime-config-types – Design & Architecture

## 1. Purpose

`runtime-config-types` provides **generated Rust bindings for the shared runtime configuration schema** used across the DCMaar agent framework.

## 2. Responsibilities

- Represent configuration structures as strongly typed Rust types.
- Ensure schema changes propagate safely to Rust consumers.

## 3. Architectural Position

- Generated types (via `typify`) that are consumed by configuration and runtime components.
- Acts as a contract layer between configuration schemas and Rust code.

This document is self-contained and summarizes the architecture and role of `runtime-config-types`.
