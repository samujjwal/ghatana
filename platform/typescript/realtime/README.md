# @ghatana/realtime

Realtime browser helpers for WebSocket and Server-Sent Events clients.

## Purpose

- provide shared realtime connection helpers
- standardize connection lifecycle and subscription behavior in frontend clients

## Boundaries

- no product-specific channel contracts
- no UI state management embedded in transport helpers

## Usage guidance

- keep protocol-specific message types in the consumer package
- use this package for connection orchestration and shared browser-facing primitives
