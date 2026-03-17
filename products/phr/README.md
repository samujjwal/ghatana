# PHR Nepal — Personal Health Records

**Product Owner:** @ghatana/phr-team  
**Status:** Pre-development (Planning Phase)  
**Stack:** TBD

## Purpose

**PHR Nepal** is a personal health records application designed for the Nepal market. It will provide patients and healthcare providers with a secure, interoperable platform for managing medical records, prescriptions, lab results, and appointment history.

## Current Status

> **This product is in the planning phase.** No production code exists yet. The current content consists entirely of research, requirements, and feature documentation.

## Documents

| Document | Description |
|----------|-------------|
| [`phr-research.md`](phr-research.md) | Market analysis, regulatory considerations (MoHP Nepal), FHIR compliance research |
| [`phr-feature-list.md`](phr-feature-list.md) | Prioritized feature backlog (MoSCoW) |
| [`phr-e2e-requirements.md`](phr-e2e-requirements.md) | End-to-end system requirements |
| [`phr-consolidated-report-v2.md`](phr-consolidated-report-v2.md) | Consolidated product vision and strategy report |

## Planned Architecture

- **Backend:** Java 21 + ActiveJ (Core Platform pattern)
- **Frontend:** React 19 + Tailwind CSS + `@ghatana/design-system`
- **Mobile:** React Native (Expo)
- **Standards:** HL7 FHIR R4 for health data interoperability
- **Auth:** Integration with ghatana security-gateway

## Next Steps

1. Finalize regulatory compliance requirements (Nepal Health Policy)
2. Complete ADR for FHIR R4 vs. Nepal NHR compliance path
3. Scaffold product using platform conventions (`./gradlew init-product`)
