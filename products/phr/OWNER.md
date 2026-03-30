# Owner: PHR Nepal — Personal Health Records

**Team:** PHR Team  
**Slack:** #product-phr  
**On-call:** PHR on-call rotation  
**Architecture lead:** PHR Tech Lead  
**Boundary audit score:** 8/10 (2026-03-29) — core Java implementation active, integration hardening in progress

## Responsibility

PHR Nepal is a **personal health records application** for the Nepal market, providing:

- Secure, interoperable medical record management
- Prescription and lab result access
- Appointment history tracking
- Healthcare provider integration

**Domain boundary:** PHR owns the health records domain for Nepal. It consumes `platform:java:security` for auth/privacy controls, `platform:java:database` for persistence, `platform:java:kernel` for runtime abstractions, and `platform:java:billing` for shared billing contracts. No other products should depend on PHR's internal modules.

## Architecture

**Current status:** Alpha — core production-oriented Java services are implemented and wired through `PhrKernelModule`.

Implemented surface includes:

- Kernel-integrated PHR services (clinical, administrative, and emergency)
- Security/privacy managers with consent delegation
- Immutable audit trail and telemetry hooks
- FHIR R4 transformation and validation support
- Healthcare billing bridge via shared `LedgerPostingService` contract

Reference [the product README](README.md) for status details.

## Known Issues

- Documentation drift may still exist in older planning docs under `docs/`.
- Frontend and mobile delivery remain planned and are not part of the current Java backend implementation.
- No duplication with `shared-services/user-profile-service` for user identity.
