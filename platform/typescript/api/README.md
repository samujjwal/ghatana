# @ghatana/api

Fetch-based API client for shared frontend consumers.

## Purpose

- provide a small HTTP client layer for browser and frontend runtime use
- centralize middleware, retry, and request/response shaping concerns
- avoid ad hoc fetch wrappers in product code

## Boundaries

- no product-specific endpoints or DTOs
- no UI components
- keep transport helpers generic and reusable

## Usage guidance

- use this package for shared request orchestration and middleware hooks
- keep domain-specific API clients in the consuming app or feature package
