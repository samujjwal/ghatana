# Shared TypeScript API – Coding Guidelines

## 1. Scope

These guidelines apply to `libs/typescript/api`.

## 2. Design Principles

- Keep API helpers generic and product-agnostic.
- Model requests and responses with explicit types.
- Avoid embedding view-specific logic; let apps compose API calls.

This document is self-contained and defines how to structure code in the Shared TypeScript API library.
