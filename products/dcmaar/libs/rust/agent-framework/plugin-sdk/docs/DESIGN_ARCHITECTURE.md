# DCMaar Agent Framework – dcmaar-plugin-sdk – Design & Architecture

## 1. Purpose

`dcmaar-plugin-sdk` provides the **public SDK for building plugins** that run inside the DCMaar agent framework.

## 2. Responsibilities

- Expose stable traits and types for plugin authors.
- Abstract away internal agent details and host APIs.

## 3. Architectural Position

- Library crate exposing an SDK (`rlib`).
- Used by plugin projects (WASM or native) that integrate with the agent.

This document is self-contained and summarizes the architecture and role of `dcmaar-plugin-sdk`.
