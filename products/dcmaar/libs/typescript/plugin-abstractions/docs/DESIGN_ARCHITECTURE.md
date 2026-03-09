# DCMaar – Plugin Abstractions – Design & Architecture

## 1. Purpose

`@dcmaar/plugin-abstractions` provides **plugin abstraction interfaces and implementations** for the DCMaar framework. It defines how plugins interact with the platform in TypeScript.

## 2. Responsibilities

- Define stable plugin interfaces (contracts) for plugin authors.
- Provide reference implementations and helpers built on those interfaces.

## 3. Architectural Position

- TypeScript library used by plugin projects and host applications.
- Depends on `@dcmaar/types` for shared domain types.

This document is self-contained and summarizes the architecture and role of `@dcmaar/plugin-abstractions`.
