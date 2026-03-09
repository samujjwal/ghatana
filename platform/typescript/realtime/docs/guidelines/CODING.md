# Shared TypeScript Realtime – Coding Guidelines

## 1. Scope

These guidelines apply to `libs/typescript/realtime`.

## 2. Design Principles

- Encapsulate realtime connection and subscription details behind hooks/utilities.
- Keep APIs framework-friendly (React/TypeScript) but avoid app-specific assumptions.
- Model events and payloads with explicit TypeScript types.

This document is self-contained and defines how to structure code in the Shared TypeScript Realtime library.
