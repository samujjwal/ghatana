# Owner: AEP UI

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Operator cockpit and management UI for AEP.

- Pipeline visualization and management
- Operator catalog browser
- Monitoring dashboards
- Cost and run detail views
- Episode review queue

## Key Components

| Component | Purpose |
|-----------|---------|
| Operator cockpit | Management interface |
| Pipeline visualizer | DAG visualization |
| Monitoring views | Metrics and alerts display |
| Episode review | Learning loop review queue |

## Dependencies

- React/TypeScript
- PNPM workspace
- AEP Server API

## Audit Status

- Last audited: 2026-04-29
- Build status: PASS in CI
- Test command: `pnpm test -- --run`
