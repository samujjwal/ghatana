# YAPPC Alerting and On-Call Runbook

## Alert Routing
YAPPC alerts are routed using product label matchers in Alertmanager:
- severity=critical -> yappc-critical receiver (immediate paging)
- severity=warning -> yappc-warning receiver (batched notification)
- severity=info or unmatched -> platform default receiver

## On-Call Rotation
- Primary: YAPPC platform on-call engineer.
- Secondary: YAPPC backend runtime engineer.
- Escalation manager: YAPPC engineering lead.
- Rotation handoff: every Monday 09:00 UTC.

## Response SLO
- Critical: acknowledge within 10 minutes.
- Warning: acknowledge within 60 minutes.
- Info: review during business hours.

## Initial Response Checklist
1. Confirm alert scope (single instance vs multi-instance).
2. Check correlated alerts in Grafana YAPPC dashboards.
3. Review service logs and recent deploys.
4. Verify dependency health (database, LLM provider, networking).
5. Apply mitigation or rollback.
6. Update incident record with timeline and owner.

## Post-Incident Requirements
- Incident summary within 24 hours.
- Root cause and corrective actions within 3 business days.
- Add or update alert rule and runbook if a coverage gap was found.

## Core Dashboards
- YAPPC - Operations Control Tower
- YAPPC - Lifecycle KPIs and Business Metrics
- YAPPC - Agent Execution
