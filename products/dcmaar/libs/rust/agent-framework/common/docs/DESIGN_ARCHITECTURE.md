# DCMaar Agent Framework – dcmaar-common – Design & Architecture

## 1. Purpose

`dcmaar-common` provides **shared utilities and error types** used across the DCMaar agent framework crates.

## 2. Responsibilities

- Define common error enums and wrappers.
- Provide logging and tracing helpers.
- Offer small, generic utilities that do not belong in higher-level crates.

## 3. Architectural Position

- Pure Rust utility crate, no direct ties to transport, storage, or UI.
- Used by multiple agent framework crates to avoid duplication.

This document is self-contained and summarizes the architecture and role of `dcmaar-common`.
