# YAPPC AEP Adapter Module

This module owns YAPPC's direct integration with AEP runtime and registry components.

## Purpose

- Provide adapter implementations for YAPPC ports that require AEP runtime/registry.
- Keep direct `products:data-cloud:planes:action:*` dependencies out of capability and domain modules.

## Current Adapters

- `AepAgentRegistryAdapter`
- `AepAgentRuntimeAdapter`

## Dependency Rule

Capability and domain modules must depend on YAPPC ports, not AEP classes directly. This module is the boundary implementation.
