# Finance

Finance is the product boundary for financial operations in Ghatana. It owns order and ledger workflows, risk and compliance capabilities, onboarding flows, and regulator-facing surfaces while reusing shared platform modules for runtime, persistence, observability, and security.

## Scope

- Order management, execution, and lifecycle workflows
- Risk management, exposure tracking, and surveillance
- Client onboarding and KYC-related flows
- Regulatory reporting and supporting integrations

## Structure

- `launcher/`: runnable Finance application entry point and container packaging
- `domains/`: product-owned domain logic such as surveillance and finance services
- `calendar-service/`, `client-onboarding/`, `rules-engine/`, `ledger-framework/`, `operator-workflows/`: finance capabilities and supporting subsystems
- `platform-sdk/`, `config/`, `docs/`, `integration-testing/`: finance-specific SDK, configuration, documentation, and verification assets

## Build And Run

Build the Finance product modules:

```bash
./gradlew :products:finance:build
```

Run Finance through the launcher module:

```bash
./gradlew :products:finance:launcher:run
```

Run Finance tests:

```bash
./gradlew :products:finance:test
```

Build the Finance container image:

```bash
docker build -f products/finance/launcher/Dockerfile -t ghatana-finance-launcher:test .
```

## Key Dependencies

- Shared Java platform modules under `platform/java/*` for core runtime, database, observability, and security concerns
- Product-owned Finance modules for business workflows and domain boundaries
- Launcher packaging for local execution and container deployment

## Notes

- Finance is one of the repo's reference implementations for strong product-boundary hygiene.
- Ownership and escalation details remain in `OWNER.md`.