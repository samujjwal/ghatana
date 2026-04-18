# YAPPC Penetration Testing Program

## Objective
Define recurring penetration testing, remediation ownership, and retesting evidence requirements for YAPPC production readiness.

## Cadence
- Full penetration test: quarterly.
- Deep manual assessment: annually (external or internal red-team).
- Triggered retest: after any critical remediation and before release promotion.

## Scope
- API authentication and session controls.
- RBAC authorization and tenant isolation boundaries.
- Injection classes (SQLi, command injection, template injection).
- Web vulnerabilities (XSS, CSRF, SSRF, clickjacking, open redirect).
- Secrets and sensitive data exposure paths.
- Dependency and container surface where externally reachable.

## Execution Model
1. Scheduled workflow runs ZAP full scan baseline against staging.
2. Security owner triages findings and assigns remediation owners.
3. Engineering submits remediation PRs with test evidence.
4. Retest is executed and artifacts linked to the same tracking issue.

## Severity SLA
- Critical: triage within 4 hours, fix in 24 hours.
- High: triage within 1 business day, fix in 72 hours.
- Medium: triage within 3 business days, fix in 14 days.
- Low: triage within 5 business days, fix in 30 days.

## Deliverables
- Quarterly issue with findings snapshot and action tracker.
- Retest evidence artifact after remediation.
- Annual report using template: docs/security/templates/YAPPC_PENTEST_REPORT_TEMPLATE.md.

## Acceptance Criteria Mapping
- Annual penetration testing: satisfied by quarterly+annual schedule.
- Penetration testing report: template and workflow artifacts required.
- Findings remediated: tracked in issue with PR linkage.
- Retesting after remediation: mandatory retest step before closure.
