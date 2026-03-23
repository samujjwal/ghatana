# Owner: PHR Nepal — Personal Health Records

**Team:** PHR Team  
**Slack:** #product-phr  
**On-call:** PHR on-call rotation  
**Architecture lead:** PHR Tech Lead  
**Boundary audit score:** 4/10 (2026-03-22) — planning phase, no production code

## Responsibility

PHR Nepal is a **personal health records application** for the Nepal market, providing:
- Secure, interoperable medical record management
- Prescription and lab result access
- Appointment history tracking
- Healthcare provider integration

**Domain boundary:** PHR owns the health records domain for Nepal. It should consume `platform:java:security` for data encryption, `platform:java:database` for persistence, and `platform:java:kernel-capabilities` for core domain abstractions. No other products should depend on PHR's internal modules.

## Architecture

**Current status:** Planning phase. No production code exists yet. All current content consists of research, requirements, and feature documentation.

Reference [the product README](README.md) for status details.

## Known Issues

- `OWNER.md` was missing as of the 2026-03-22 boundary audit (score 4/10, accountability gap)
- No production implementation exists — product must leave planning phase to receive meaningful audit score
- Stack (`TBD`) should be committed before implementation begins
- When implementation begins: no duplication with `shared-services/user-profile-service` for user identity
