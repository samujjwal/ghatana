# YAPPC Auth Library

Authentication and identity helpers shared across YAPPC frontend packages.

## Scope
- OAuth and auth-flow helpers.
- Shared auth types, utilities, and client-side integration code.
- Product-local authentication logic that is reused across UI packages.

## Key Areas
- Auth flow utilities.
- Shared auth types and token helpers.
- Package integration points for login and session handling.

## Audit Notes
- Keep token handling explicit and avoid silent auth failures.
- Preserve strict typing around identity payloads and session state.