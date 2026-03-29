# @ghatana/sso-client

Cross-product SSO client helpers for platform JWT and redirect handling.

## Purpose

- read platform authentication state from the redirect flow
- expose helpers such as login, logout, `isAuthenticated()`, and `getUser()`

## Boundaries

- no product-specific authorization rules
- no UI components
- no backend token issuing logic

## Usage guidance

- use this package at frontend auth boundaries
- keep app-specific session storage and route protection wrappers in the consuming app
