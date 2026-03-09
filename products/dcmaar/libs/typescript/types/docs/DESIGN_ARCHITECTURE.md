# DCMaar – Types – Design & Architecture

## 1. Purpose

`@dcmaar/types` provides **shared type definitions** for the DCMaar framework, including common models, errors, and utilities.

## 2. Responsibilities

- Centralize TypeScript types used across backend-facing and frontend modules.
- Provide typed entrypoints for `common`, `errors`, and `utils` subpackages.

## 3. Architectural Position

- Foundation library that other TS packages depend on (`plugin-abstractions`, UI libs, etc.).

This document is self-contained and summarizes the architecture and role of `@dcmaar/types`.
