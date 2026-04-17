# Critical Journey E2E Checklist

## Pre-run

- Confirm target environment URL and API base URL.
- Confirm test tenant and seeded accounts are available.
- Confirm feature flags for payments/compliance are enabled.
- Confirm observability stack is reachable.

## Run

- Execute ./scripts/run-critical-journey-e2e.sh (Linux/macOS) or .\\scripts\\run-critical-journey-e2e.ps1 (Windows).
- Capture Playwright traces and screenshots for each journey.
- Save API response payloads for compliance and deletion journeys.
- Run GDPR SQL checks after deletion flow completion.

## Post-run

- Fill CRITICAL_JOURNEY_E2E_EVIDENCE_2026-04-16.md.
- Attach logs and trace artifacts to release ticket.
- Record known failures and rerun results.
- Complete CRITICAL_JOURNEY_E2E_SIGNOFF_TEMPLATE.md.
