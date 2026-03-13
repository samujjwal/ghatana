# MILESTONE 3C — OPERATIONS, COMPLIANCE & PORTALS

## Sprints 15–16 | 56 Stories | O-01, P-01, R-01, R-02, PU-004

> **Story Template**: Each story includes ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, and dependencies.

---

# EPIC O-01: OPERATOR CONSOLE (14 Stories)

## Feature O01-F01 — Multi-Firm Tenancy Management (3 Stories)

---

### STORY-O01-001: Implement multi-firm tenant registry

**Feature**: O01-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Alpha

Operator-level tenant registry: manage multiple firms/tenants on the platform. Tenant entity: tenant_id, name, license_type (BROKER/ASSET_MANAGER/CUSTODIAN), jurisdiction, status (ONBOARDING/ACTIVE/SUSPENDED/OFFBOARDED), config_profile (K-02), resource_quotas, inception_date. CRUD API with maker-checker. TenantCreated/Suspended/Offboarded events. Namespace isolation per tenant in K8s.

**ACs**:

1. Given new tenant registered, When maker-checker approved, Then tenant created with dedicated namespace
2. Given tenant suspended, When status updated, Then all tenant API tokens invalidated within 60 seconds
3. Given tenant offboarded, When processed, Then data retention per K-08 policy, namespace cleaned up

**Tests**: tenant_create · tenant_suspend · token_invalidation_60sec · tenant_offboard · namespace_isolation · maker_checker · event_lifecycle · resource_quota_applied

**Dependencies**: K-01, K-02, K-08, K-05

---

### STORY-O01-002: Implement tenant configuration isolation

**Feature**: O01-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Alpha

Per-tenant configuration: each tenant has isolated K-02 namespace, cannot access other tenants' configs. Operator-level overrides: operator can set platform-wide defaults that tenants can optionally override within permitted bounds. Tenant config boundaries: define min/max for configurable values (e.g., tenant can set session_timeout between 15-480 minutes, not outside). Config inheritance: tenant defaults from license_type template. Dynamic value catalogs and process-profile overlays allow operator-approved variation in allowed choices, approval paths, and step sequencing without cross-tenant leakage.

**ACs**:

1. Given tenant A updates their timeout config, When applied, Then only affects tenant A's services
2. Given operator default timeout = 60 min, When tenant tries to set 600 min (beyond max), Then rejected
3. Given new tenant created with BROKER license, When defaults applied, Then broker-specific configs preset
4. Given tenant-specific value catalog override for an onboarding or approval process, When resolved, Then only allowed options within operator-set bounds are exposed to that tenant

**Tests**: tenant_config_isolation · cross_tenant_no_access · operator_override · boundary_enforcement · license_type_defaults · config_inheritance · tenant_value_catalog_override · process_profile_isolation · perf_config_load_sub_10ms

**Dependencies**: K-02, STORY-O01-001

---

### STORY-O01-003: Implement tenant resource usage monitoring

**Feature**: O01-F01 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Monitor resource usage per tenant: API call volume, event throughput, storage usage, active users. Quota enforcement: if tenant exceeds API rate limit, throttle with 429. Usage dashboard: per tenant with trend. Billing data export (if commercial model): usage metrics per billing period. Alert: tenant approaching quota at 80%.

**ACs**:

1. Given tenant exceeds API quota (1000 req/min), When throttled, Then 429 with retry-after header
2. Given usage at 80% of quota, When alert triggers, Then tenant admin notified
3. Given monthly usage export, When generated, Then per-tenant usage metrics in CSV

**Tests**: api_rate_limit_429 · throttle_retry_after · quota_80_alert · usage_dashboard · monthly_export · multi_tenant_isolated · storage_tracking

**Dependencies**: STORY-O01-001, K-06

---

## Feature O01-F02 — License & Feature Flags (2 Stories)

---

### STORY-O01-004: Implement license management and feature gates

**Feature**: O01-F02 · **Points**: 3 · **Sprint**: 15 · **Team**: Alpha

License management: each tenant has a license defining enabled features. Feature flags per tenant: e.g., ALGO_TRADING (T3 plugins enabled), MULTI_CURRENCY, SANCTIONS_SCREENING, ADVANCED_RISK. License expiry: features disabled on expiry (grace period 7 days with warning). Feature gate check at API and event level. LicenseExpired event triggers workflow.

**ACs**:

1. Given tenant without ALGO_TRADING license, When T3 plugin activation attempted, Then FEATURE_NOT_LICENSED error
2. Given license expiry in 7 days, When grace period starts, Then tenant admin warned daily
3. Given license expired, When grace period ends, Then affected features disabled, tenant services continue (non-gated)

**Tests**: feature_gate_unlicensed · feature_gate_licensed · grace_period_warning · license_expiry_disable · multiple_features · expiry_event · api_gate · event_gate

**Dependencies**: STORY-O01-001, K-02

---

### STORY-O01-005: Implement feature rollout and A/B management

**Feature**: O01-F02 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Operator-managed feature rollout: gradually enable new platform features for tenants. Rollout strategies: ALL_AT_ONCE, PERCENTAGE (random X% of tenants), TENANT_LIST (specific tenants for beta). A/B testing: serve different feature variants to different tenant groups. Feature flag state evaluated per request (not cached long). Kill switch: instantly disable feature for all tenants.

**ACs**:

1. Given 20% rollout, When feature evaluated per tenant, Then randomly assigned percentage get feature ON
2. Given kill switch activated, When feature flag checked by any tenant, Then OFF within 60 seconds
3. Given tenant_list rollout, When feature evaluated, Then only listed tenants see feature ON

**Tests**: percentage_rollout · kill_switch_60sec · tenant_list_rollout · ab_variant · evaluation_per_request · consistency_per_tenant · operator_ui

**Dependencies**: STORY-O01-004, K-02

---

## Feature O01-F03 — Platform Health & Ops (3 Stories)

---

### STORY-O01-006: Implement operator platform health dashboard

**Feature**: O01-F03 · **Points**: 3 · **Sprint**: 15 · **Team**: Epsilon

Operator-level health dashboard (distinct from tenant admin dashboard): cross-tenant view of platform health. Metrics: total API calls/sec across all tenants, K-05 event bus throughput, total K8s pod health, overall settlement success rate, DLQ (K-19) health. Per-tenant health summary: green/yellow/red. Alert center: critical alerts across all tenants. Global search: find any entity across all tenants.

**ACs**:

1. Given operator dashboard, When loaded, Then cross-tenant aggregated metrics displayed
2. Given one tenant having issues, When dashboard viewed, Then that tenant shown in red
3. Given global search for order_id, When searched, Then found across any tenant's data

**Tests**: cross_tenant_metrics · per_tenant_health · alert_center · global_search · aggregated_throughput · k8s_pod_health · perf_under_5sec

**Dependencies**: K-06, O01-001

---

### STORY-O01-007: Implement incident management for operators

**Feature**: O01-F03 · **Points**: 3 · **Sprint**: 15 · **Team**: Alpha

Operator incident management: when platform issues affect multiple tenants, manage via structured incident. Incident: severity (P1/P2/P3/P4), affected_tenants, affected components, status (INVESTIGATING/IDENTIFIED/MONITORING/RESOLVED), timeline, communication log. P1/P2: auto-notify affected tenant admins. Post-incident review (PIR) workflow. Integration with K-06 alerts as incident triggers.
Post-incident review forms, resolution code lists, and escalation paths are metadata-driven via K-02/W-01 so operators can evolve investigation fields and response paths without portal rewrites.

**ACs**:

1. Given P1 incident created, When published, Then all affected tenant admins notified within 5 minutes
2. Given incident resolved, When status updated, Then resolution communication sent to affected tenants
3. Given PIR workflow, When triggered post-resolution, Then structured review form with timeline
4. Given PIR schema updated with an additive field, When next incident review opens, Then new field renders from metadata without affecting already-closed incidents

**Tests**: p1_notification_5min · resolution_communication · pir_workflow · pir_schema_versioning · incident_timeline · severity_escalation · multi_tenant_affection · audit_trail

**Dependencies**: K-06, K-05, STORY-O01-001

---

### STORY-O01-008: Implement platform maintenance window management

**Feature**: O01-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Maintenance window scheduling: plan scheduled maintenance, notify tenants in advance. Maintenance window: start_time, end_time, affected_components, expected_impact, tenant_notification_mins_before (default 60). During window: affected APIs return 503 with retry-after header and maintenance message. BS calendar for scheduling. Post-maintenance health check automation.

**ACs**:

1. Given maintenance window scheduled, When 60 min before start, Then all affected tenant admins notified
2. Given window active, When API called for affected component, Then 503 with maintenance message
3. Given window ends, When health checks pass, Then services resumed, completion notification sent

**Tests**: advance_notification · api_503_during_maintenance · retry_after_header · post_maintenance_health · bs_calendar · completion_notification · emergency_window

**Dependencies**: STORY-O01-001, K-15

---

## Feature O01-F04 — Multi-Jurisdiction Management (2 Stories)

---

### STORY-O01-009: Implement jurisdiction registry

**Feature**: O01-F04 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Jurisdiction registry: define operating jurisdictions (e.g., Nepal, India, Bhutan). Jurisdiction entity: code, name, regulator (SEBON for Nepal), calendar_id (K-15), default_settlement_cycle (T+2), currency, language, supported_doc_types[], active. Tenant-jurisdiction mapping: tenant can operate in one or more jurisdictions. Jurisdiction determines: K-03 rules package, K-15 calendar, D-10 report templates.

**ACs**:

1. Given Nepal jurisdiction registered, When tenant onboards with Nepal, Then SEBON regulator, BS calendar, NPR applied
2. Given multi-jurisdiction tenant, When rules evaluated, Then correct jurisdiction rules per transaction
3. Given new jurisdiction added, When activated, Then available for tenant assignment

**Tests**: jurisdiction_crud · nepal_defaults · multi_jurisdiction_tenant · rules_per_jurisdiction · calendar_assignment · regulator_assignment · soft_delete

**Dependencies**: K-03, K-15, D-10

---

### STORY-O01-010: Implement cross-jurisdiction reporting aggregation

**Feature**: O01-F04 · **Points**: 3 · **Sprint**: 16 · **Team**: Alpha

Cross-jurisdiction reporting for operators: aggregate platform-wide metrics across jurisdictions and tenants. Reports: total settlement volume by jurisdiction, regulatory submission status by jurisdiction, AML risk distribution, platform uptime by jurisdiction. Operator-only access. Export to PDF/CSV. Scheduled delivery to operator email.

**ACs**:

1. Given operator requests cross-jurisdiction report, When generated, Then volume data per jurisdiction
2. Given regulatory submission status, When queried, Then per-jurisdiction submission compliance rate
3. Given scheduled monthly report, When delivered, Then PDF emailed to operator

**Tests**: cross_jurisdiction_volume · submission_compliance · aml_distribution · monthly_schedule · pdf_export · csv_export · operator_access_only · perf_under_30sec

**Dependencies**: STORY-O01-009, D-10, K-06

---

## Feature O01-F05 — Billing & Commercial (2 Stories)

---

### STORY-O01-011: Implement usage metering for billing

**Feature**: O01-F05 · **Points**: 3 · **Sprint**: 16 · **Team**: Alpha

Usage metering: track billable events per tenant. Billable metrics: API calls per service, settlement volume, number of reconciliations, report submissions, plugin executions. Metering store: time-series per tenant per metric per day. Monthly billing report per tenant: usage summary against plan. Export to CSV/PDF for invoicing. Plan configuration: flat fee + usage-based tiers.

**ACs**:

1. Given tenant makes 50,000 API calls, When metered, Then usage recorded per service per day
2. Given monthly billing period, When report generated, Then per-tenant usage with cost calculation
3. Given usage above tier threshold, When billing calculated, Then next tier rate applied for excess

**Tests**: api_call_metering · settlement_metering · monthly_report · tier_calculation · csv_export · pdf_invoice · multi_tenant · perf_metering_overhead

**Dependencies**: STORY-O01-003, K-08

---

### STORY-O01-012: Implement tenant trial and provisioning automation

**Feature**: O01-F05 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Automated tenant provisioning workflow: trial request → auto-provision sandbox tenant with trial license → trial period tracking (30 days) → conversion to paid or offboarding. Trial limitations: limited API quota, sandbox only. Conversion: upgrade to paid, lift restrictions. Automated welcome email with sandbox credentials and documentation links.

**ACs**:

1. Given trial request submitted, When auto-provisioned, Then sandbox tenant created within 5 minutes
2. Given trial period ends, When expiry reached, Then tenant notified, grace of 7 days before suspend
3. Given trial converted to paid, When upgraded, Then production license applied, quotas lifted immediately

**Tests**: auto_provision_5min · trial_expiry · grace_7_days · conversion_to_paid · sandbox_limitations · welcome_email · auto_offboard_expired

**Dependencies**: STORY-O01-001, STORY-O01-004

---

## Feature O01-F06 — AI Operations Intelligence (2 Stories)

---

### STORY-O01-013: Implement natural language platform query engine

**Feature**: O01-F06 · **Points**: 5 · **Sprint**: 16 · **Team**: Alpha

Natural Language Query (NLQ) interface for platform operators: allows SRE/ops team to interrogate platform metrics, logs, and alerts using plain English instead of PromQL/ES query syntax. Architecture: LLM (locally-hosted, K-04 T2 sandbox) translates natural language query to PromQL/ES query + interprets result into a human-readable summary. Examples: "Show me the top 5 services by error rate in the last 4 hours", "Which tenants had P2+ incidents this week?", "What is the settlement failure rate trend for the last 30 days?". NLQ engine retrieves live data from K-06 Prometheus/ELK; response includes both raw data table and LLM-written narrative summary. All queries logged via K-07. Governed by K-09 advisory (local LLM, no external API).

**ACs**:

1. Given operator query "Show me the top 5 services by error rate in the last 4 hours", When NLQ engine processes, Then PromQL generated: `topk(5, rate(http_errors_total[4h]))`, result returned as ranked table AND as narrative summary: "The top services by error rate are: D-01 OMS (2.3%), D-09 Post-Trade (1.8%), ..."
2. Given ambiguous query "any problems today?", When NLQ interprets, Then clarification offered: "Did you mean: (1) active incidents, (2) error rate > 1%, or (3) SLO breaches?" — operator selects, query executes
3. Given NLQ query and result, When audited, Then K-07 audit record contains: original_query, generated_promql, result_summary, operator_id, timestamp

**Tests**: nlq_promql_translation · nlq_es_translation · result_narrative_summary · ambiguity_clarification · k07_audit_log · local_llm_no_external_api · k09_advisory_tier · k06_live_data · perf_query_under_5sec

**Dependencies**: K-06 (observability), K-04 (T2 sandbox), K-09 (AI governance), K-07

---

### STORY-O01-014: Implement AI-powered capacity planning and predictive alerting

**Feature**: O01-F06 · **Points**: 5 · **Sprint**: 16 · **Team**: Alpha

ML-powered capacity planning that forecasts resource utilization (CPU, memory, Kafka consumer lag, DB connection pool, storage) per service over the next 7 days using Prophet time-series models with seasonality (trading day patterns, end-of-month spikes). Forecasts updated daily. Predictive alert: if forecast shows a service will breach resource quota (K-10-011) within 7 days, sends advance alert to SRE with recommended scale-up action. Also detects growth trend anomalies: sustained 15%+ week-over-week resource growth = CapacityGrowthAlert (potential organic growth vs runaway resource leak). All forecasts exposed via O-01 dashboard and as K-06 Grafana panels. Models governed by K-09.

**ACs**:

1. Given OMS service showing 40% CPU steady state with a Prophet model forecasting 92% in 6 days (end-of-month trade spike pattern), When predictive alert triggers, Then SRE receives: "OMS CPU predicted to reach 92% quota in 6 days (end-of-month pattern) — recommend scaling from 4 to 6 replicas by [date]"
2. Given Kafka consumer lag for settlement service growing 18% week-over-week for 3 consecutive weeks, When growth anomaly detected, Then CapacityGrowthAlert: "D-09 settlement consumer lag growing 18% WoW for 3 weeks — possible throughput bottleneck or message volume leak, investigate before breach"
3. Given capacity dashboard loaded, When viewed, Then 7-day forecast charts per service per resource, with "in-range" (green), "approaching-limit" (yellow), "breach-predicted" (red) color coding

**Tests**: prophet_7day_forecast · endofmonth_seasonality · predictive_alert_6day · scale_recommendation · growth_anomaly_detection · dashboard_forecast_charts · color_coding · k09_model_governance · perf_daily_forecast_under_10min

**Dependencies**: K-06 (metrics), K-10-011 (resource quotas), K-09 (AI governance)

---

# EPIC P-01: PACK CERTIFICATION (11 Stories)

## Feature P01-F01 — Certification Authority (2 Stories)

---

### STORY-P01-001: Implement plugin certification authority service

**Feature**: P01-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Alpha

Certification Authority (CA) for platform plugins: manages the certification lifecycle. CA entity: issues, revokes, and tracks plugin certificates. Certificate: plugin_id, version, tier, issuer (platform CA), issued_date, expiry_date, fingerprint, status (VALID/REVOKED/EXPIRED). Certificate storage in HSM (or K-14 managed key store). Certificate chain: plugin cert signed by platform CA. CertificateIssued event.

**ACs**:

1. Given plugin passes certification, When certificate issued, Then signed by platform CA with expiry
2. Given certificate chain, When verified, Then chain validates to trusted platform root CA
3. Given certificate revoked, When runtime checks, Then plugin execution blocked immediately

**Tests**: cert_issuance · cert_chain_verify · revocation · expiry_check · revocation_immediate_block · hsm_signing · event_emission · cert_crud

**Dependencies**: K-04, K-14, K-07

---

### STORY-P01-002: Implement certification program policy engine

**Feature**: P01-F01 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Certification policies per tier: T1 (no network, no file system, size < 1MB), T2 (sandboxed rules, pre-approved data access, size < 5MB), T3 (configurable sandbox per plugin, security review required, expiry 1 year). Policy defined in YAML, versioned. Policy evaluation: automated checks during certification test suite. Non-compliant → certification denied with report.

**ACs**:

1. Given T1 plugin attempts network call, When policy checked, Then NETWORK_ACCESS_DENIED violation
2. Given T3 plugin with custom sandbox config, When reviewed, Then security review checklist applied
3. Given policy v2 published, When new certification runs, Then v2 policy applied

**Tests**: t1_no_network · t1_no_filesystem · t2_approved_access · t3_security_review · policy_versioning · violation_report · policy_evaluation_automated

**Dependencies**: STORY-P01-001, K-04

---

## Feature P01-F02 — Security Scanning (3 Stories)

---

### STORY-P01-003: Implement static code analysis for plugins

**Feature**: P01-F02 · **Points**: 3 · **Sprint**: 15 · **Team**: Zeta

Static security analysis: scan plugin source code before packaging. Tools: Semgrep (custom rules for platform security), Bandit (Python), ESLint security plugin. Checks: hardcoded credentials, command injection, SQL injection, safe deserialization, no eval(). Scan report: severity (CRITICAL/HIGH/MEDIUM/LOW), file:line, rule violated. CRITICAL findings → certification denied.

**ACs**:

1. Given plugin with hardcoded password, When scanned, Then CRITICAL finding, certification denied
2. Given plugin with eval() usage, When scanned, Then HIGH finding, must fix before retry
3. Given clean scan, When passed, Then ZERO critical or high findings, scan report attached to cert

**Tests**: hardcoded_credential_detect · eval_detect · command_injection · sql_injection · safe_deserialization · semgrep_custom_rules · clean_scan_pass · multi_language_scan

**Dependencies**: STORY-P01-002

---

### STORY-P01-004: Implement dependency vulnerability scanning

**Feature**: P01-F02 · **Points**: 2 · **Sprint**: 15 · **Team**: Zeta

Dependency vulnerability scanning: check all plugin dependencies against CVE databases (OSV, NVD). Tools: OWASP Dependency-Check, Syft + Grype (for SBOM generation). SBOM (Software Bill of Materials) generated per plugin per version. Vulnerabilities: CRITICAL/HIGH block certification, MEDIUM = warning. Weekly rescan of deployed plugins against new CVEs. Re-certification required if new CRITICAL found.

**ACs**:

1. Given plugin with CVE-2024-XXXX in dependency, When scanned, Then vulnerability found, certification blocked
2. Given SBOM generated, When attached to cert, Then consumable by security teams
3. Given weekly rescan finds new CRITICAL CVE, When detected, Then plugin auto-suspended, owner notified

**Tests**: cve_detection_critical · cve_detection_high · sbom_generation · weekly_rescan · auto_suspend_new_cve · medium_warning_only · clean_scan_pass

**Dependencies**: STORY-P01-003

---

### STORY-P01-005: Implement dynamic sandbox security testing

**Feature**: P01-F02 · **Points**: 3 · **Sprint**: 16 · **Team**: Zeta

Dynamic security testing: run plugin in instrumented sandbox and attempt to trigger security violations. Test scenarios: attempt network access, attempt file system access beyond boundary, heavy CPU (DoS attempt), memory exhaustion attempt, infinite loop. All violations recorded. Plugin must complete test scenarios without crashing sandbox or causing resource exhaustion.

**ACs**:

1. Given T1 plugin, When network access attempted in test, Then violation recorded, plugin continues gracefully
2. Given CPU stress test, When plugin attempts infinite loop, When sandbox timeout enforced, Then plugin terminated, sandbox remains healthy
3. Given all scenarios pass, When sandbox test complete, Then DYNAMIC_TEST_PASSED status in cert

**Tests**: network_violation_recorded · filesystem_violation · cpu_stress · memory_exhaustion · timeout_enforcement · sandbox_health_maintained · graceful_handling · test_report

**Dependencies**: K-04, STORY-P01-002

---

## Feature P01-F03 — Marketplace (3 Stories)

---

### STORY-P01-006: Implement plugin marketplace UI

**Feature**: P01-F03 · **Points**: 3 · **Sprint**: 15 · **Team**: Epsilon

Plugin marketplace: browsable catalog of certified plugins available for tenant installation. Plugin card: name, tier, author, description, rating, install_count, cert_expiry. Filter: by tier, domain (trading/compliance/risk/reporting), author. Detail page: full description, changelog, screenshots, reviews, certification details, API docs. Install button (if tenant has license). Featured plugins section.

**ACs**:

1. Given plugin marketplace, When browsed, Then certified plugins listed with filter/search
2. Given plugin detail, When viewed, Then certification details (issuer, expiry, scan results) shown
3. Given install button, When clicked, When tenant has license, Then installation workflow starts

**Tests**: marketplace_load · filter_by_tier · filter_by_domain · search_by_name · detail_page · cert_details · install_triggers_workflow · license_gate · perf_load_under_3sec

**Dependencies**: STORY-P01-001, K-04, K-13

---

### STORY-P01-007: Implement plugin rating and review system

**Feature**: P01-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Epsilon

Rating and review: tenants that have installed a plugin can rate (1-5 stars) and review. Review moderation: operator reviews before publishing. Review entity: tenant, rating, review_text, helpful_count, date, status (PENDING/PUBLISHED/REJECTED). Average rating calculated. Review response by plugin developer. Abuse reporting. Review visible on marketplace.

**ACs**:

1. Given tenant installed plugin, When review submitted, When moderate approved, Then published on marketplace
2. Given review pending moderation, When operator approves, Then visible with author's tenant name (anonymized)
3. Given plugin developer response, When posted, Then reply visible below review

**Tests**: review_submit · moderation_approve · moderation_reject · average_rating · developer_response · abuse_report · anonymization · pagination

**Dependencies**: STORY-P01-006

---

### STORY-P01-008: Implement plugin version management in marketplace

**Feature**: P01-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Plugin version management: multiple versions available in marketplace. Latest stable marked as default. Tenant can pin to specific version (not auto-upgrade) or follow latest. Auto-upgrade: if tenant follows latest, new certified version deployed automatically in maintenance window. Changelog per version. Breaking changes flagged (incompatible with previous version).

**ACs**:

1. Given plugin v2 certified, When tenant follows latest, Then auto-upgrade scheduled in next maintenance window
2. Given tenant pinned to v1, When v2 released, Then no auto-upgrade, notification only
3. Given breaking change in v2, When flagged, Then tenant must manually approve upgrade

**Tests**: auto_upgrade_latest · pinned_version_no_upgrade · breaking_change_requires_approval · changelog_display · version_selection · rollback_to_previous

**Dependencies**: STORY-P01-006, STORY-P01-001

---

## Feature P01-F04 — Compliance Verification (2 Stories)

---

### STORY-P01-009: Implement compliance verification checklist

**Feature**: P01-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Compliance verification for T3 plugins: human reviewer checklist. Items: data handling (no PII leakage), audit logging (uses K-07 SDK), error handling (graceful degradation), documentation complete, regulatory compliance declared. Checklist per tier (T3 is most thorough). Reviewer signs off each item. Failed items: must fix and resubmit.

**ACs**:

1. Given T3 plugin for certification, When reviewer opens checklist, Then tier-appropriate items shown
2. Given all items signed off, When checklist complete, Then certification proceeds to certificate issuance
3. Given failed item, When documented, Then developer notified with specific failure reason

**Tests**: checklist_t3 · checklist_t2 · checklist_t1 · all_items_signoff · failed_item_notification · reviewer_assignment · audit_trail

**Dependencies**: STORY-P01-001, K-07

---

### STORY-P01-010: Implement certification audit trail and transparency

**Feature**: P01-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Full certification audit trail: every step of certification logged (scan results, checklist responses, reviewers, decisions). Immutable via K-07. Public certification transparency log: for installed plugins, tenants can see certification history (without internal reviewer notes). Certificate verification API: given cert fingerprint, return cert details + validation status.

**ACs**:

1. Given certification completed, When audit trail queried, Then all steps with timestamps and actors
2. Given public transparency log, When tenant queries, Then cert history without internal reviewer notes
3. Given cert fingerprint, When verification API called, Then VALID/REVOKED/EXPIRED status returned

**Tests**: audit_trail_complete · transparency_log_public · cert_verification_api · internal_notes_excluded · revocation_check · immutable_trail · perf_verification_sub_100ms

**Dependencies**: STORY-P01-009, K-07

---

## Feature P01-F05 — Revocation (1 Story)

---

### STORY-P01-011: Implement certificate revocation and emergency kill

**Feature**: P01-F05 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Certificate revocation: when plugin found to have security vulnerability, compliance breach, or abuse. Revocation types: VOLUNTARY (developer recalls), FORCED (operator revokes). Revocation propagates within 60 seconds to all K-04 runtimes. Emergency kill: operator can kill all instances of a plugin platform-wide immediately. CertificateRevoked event. Revocation reason and date stored in cert.

**ACs**:

1. Given forced revocation, When operator revokes, Then all runtime instances blocked within 60 seconds
2. Given emergency kill, When triggered, When running plugin instances, Then terminated within 30 seconds
3. Given revocation, When cert VL queried, Then REVOKED status with reason and effective date

**Tests**: forced_revocation_60sec · emergency_kill_30sec · transparent_blocking · revocation_reason · event_emission · voluntary_revocation · cross_runtime_propagation

**Dependencies**: STORY-P01-001, K-04

---

# EPIC R-01: REGULATOR PORTAL (11 Stories)

## Feature R01-F01 — Regulator Authentication & Access (2 Stories)

---

### STORY-R01-001: Implement regulator portal authentication

**Feature**: R01-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Epsilon

Dedicated regulator portal: separate authenticated access for regulators (SEBON inspectors, NRB supervisors). Regulator users: provisioned by operator, not by tenants. Auth: MFA mandatory, session duration limited (4 hours). Access scope: READ_ONLY by default, specific tenants assigned. Regulator cannot modify any data. Full audit trail of regulator access (what was viewed, when, by whom).

**ACs**:

1. Given regulator user, When logging in, Then MFA required, session limited to 4 hours
2. Given regulator assigned to Tenant A, When portal accessed, Then only Tenant A data visible
3. Given regulator views audit log, When access event logged, Then view event itself in audit trail

**Tests**: mfa_mandatory · session_4_hours · tenant_scope · read_only · access_audit · logout_on_session_expire · view_event_audited · multi_tenant_regulator

**Dependencies**: K-01, K-07

---

### STORY-R01-002: Implement regulator access request and provisioning

**Feature**: R01-F01 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Regulator access provisioning: operator creates regulator user accounts on behalf of regulatory body. Access request: regulators submit access request with mandate reference, scope, duration. Operator reviews and provisions. Time-limited access: access expires after investigation period. Access renewal workflow. Provisional access notification to affected tenants.

**ACs**:

1. Given regulator access request with mandate, When operator provisions, Then regulator user created with expiry
2. Given access expired, When regulator attempts login, Then access_expired error, renewal workflow offered
3. Given tenant notified, When regulator accesses their data, Then tenant compliance officer notified

**Tests**: provision_with_mandate · access_expiry · renewal_workflow · tenant_notification · operator_review · time_limited_access · audit_on_provision

**Dependencies**: STORY-R01-001, K-01

---

## Feature R01-F02 — On-Demand Data Access (3 Stories)

---

### STORY-R01-003: Implement regulator data query interface

**Feature**: R01-F02 · **Points**: 3 · **Sprint**: 15 · **Team**: Epsilon

Regulator query portal: pre-built queries for common regulatory requests. Query types: client positions as-of date, trade history for client (date range), settlement status, reconciliation reports, audit logs for client. Dual-calendar date input (BS/Gregorian). Regulator can EXPORT results (PDF/CSV). All queries logged. Query builder for ad-hoc: select entity + filters + date range.
Available query templates, export field sets, and regulator-visible value catalogs are metadata-driven so regulator-specific request packs can evolve without rebuilding the portal.

**ACs**:

1. Given regulator queries "positions for client C001 as-of 2081-10-01 BS", When executed, Then positions displayed and exportable
2. Given ad-hoc query builder, When client + trade history + date range specified, Then results shown
3. Given every query, When executed, Then logged in audit trail with query params and result count
4. Given regulator template pack updated with a new approved query preset, When portal refreshed, Then preset appears with its governed filter schema and export policy

**Tests**: query_positions · query_trade_history · query_settlement · query_audit_logs · query_template_pack_refresh · bs_date_input · export_pdf · export_csv · query_logged · ad_hoc_builder

**Dependencies**: STORY-R01-001, K-07, K-15

---

### STORY-R01-004: Implement regulatory document access vault

**Feature**: R01-F02 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Document vault: regulators access submitted regulatory reports, KYC documents, audit evidence packages. Document list: filter by document_type, tenant, date_range. Preview: PDF inline in portal. Download with logged access. Search: by reference number, report type, date. Retained documents per K-08 retention policy (regulators can only access within retention period).

**ACs**:

1. Given regulator queries document vault, When filtered by report type + date, Then matching documents listed
2. Given document download, When accessed, Then download event logged with regulator_id and document_id
3. Given document beyond retention period (deleted), When queried, Then REMOVED_PER_RETENTION message

**Tests**: document_list_filter · preview_inline · download_logged · search_by_reference · retention_period_check · multi_tenant · perf_list_under_3sec

**Dependencies**: STORY-R01-003, K-08, D-10

---

### STORY-R01-005: Implement regulatory data export and packaging

**Feature**: R01-F02 · **Points**: 3 · **Sprint**: 16 · **Team**: Alpha

Structured data export for regulatory investigations: given scope (tenant, date range, data types), generate comprehensive data package. Package contents: trades, positions, reconciliations, communications log reference, audit events, report submissions. Format: regulator-specified (JSON, CSV, PDF). Package integrity: SHA-256 manifest. Async generation for large datasets with download link email.

**ACs**:

1. Given regulator requests full investigation package for Tenant A (Jan–Jun 2081 BS), When generated, Then all data types included with hash manifest
2. Given large dataset (>100K records), When generated async, Then email with download link within 30 minutes
3. Given package downloaded, When hash verified, Then integrity confirmed

**Tests**: full_package_generation · hash_manifest · async_generation · email_download_link · large_dataset_30min · integrity_verification · format_json · format_csv

**Dependencies**: STORY-R01-004, K-08, K-07

---

## Feature R01-F03 — Investigation Workflow (2 Stories)

---

### STORY-R01-006: Implement regulatory investigation case

**Feature**: R01-F03 · **Points**: 3 · **Sprint**: 16 · **Team**: Alpha

Regulatory investigation case management: regulator opens investigation → scope defined (tenant, subject, period) → data access provisioned → evidence collected → investigation notes → findings → outcome. Case statuses: OPENED → INVESTIGATING → EVIDENCE_REVIEW → FINDING → CLOSED. Response SLA for data requests (operator must respond within 5 business days). All case interactions audited.

**ACs**:

1. Given investigation opened, When scoped, Then specific data access provisioned for regulator
2. Given data request within case, When submitted, Then operator notified with 5-day SLA countdown
3. Given case closed, When outcome recorded, Then immutable case record with all findings

**Tests**: case_open · scope_provisioning · data_request_sla · sla_notification · case_close · outcome_recorded · immutable_record · audit_all_interactions

**Dependencies**: STORY-R01-001, K-07

---

### STORY-R01-007: Implement regulatory query-response workflow

**Feature**: R01-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Formal query-response workflow: regulator submits formal information request → operator or tenant compliance responds → regulator reviews → follow-up questions. Query: subject, reference, deadline, required_info. Response: submitted data + explanatory notes. Response tracked against deadline. Escalation if not responded by deadline.
Request forms, response templates, and escalation routing are schema-driven and can be updated per regulator mandate without modifying the portal code.

**ACs**:

1. Given formal query with 5-day deadline, When submitted, When recipient viewing portal, Then query and countdown shown
2. Given response submitted before deadline, When sent, Then regulator notified, response logged
3. Given deadline passed without response, When detected, Then operator and tenant compliance escalated
4. Given regulator mandate updates the required response fields, When template revised and approved, Then new requests use the new schema while existing cases retain the pinned old schema

**Tests**: query_submission · deadline_countdown · response_submission · regulator_notification · deadline_escalation · follow_up_question · response_schema_versioning · response_logged · audit_trail

**Dependencies**: STORY-R01-006

---

## Feature R01-F04 — Transparency Reports (2 Stories)

---

### STORY-R01-008: Implement automated transparency report generation

**Feature**: R01-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Automated transparency reports for regulators: scheduled reports providing platform oversight data to regulator without ad-hoc queries. Reports: monthly AML screening summary, quarterly settlement analysis, annual platform audit summary. Auto-submitted to regulator portal. Regulator can view historical transparency reports. Format per regulator specification.

**ACs**:

1. Given monthly AML report schedule, When triggered, Then generated and submitted to regulator portal
2. Given regulator views historical reports, When browsed, Then previous months' reports accessible
3. Given report format changed by regulator, When template updated, Then next generation uses new template

**Tests**: monthly_aml_report · quarterly_settlement · annual_audit · schedule_trigger · historical_browse · template_update · perf_generation_under_30min

**Dependencies**: D-10, STORY-R01-004

---

### STORY-R01-009: Implement regulator notification service

**Feature**: R01-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Proactive notifications to regulators for significant events: large trade threshold breach, client risk score spike, settlement failure cluster, AML alert escalation, report submission. Notification preferences per regulator: email digest (daily/weekly) or real-time for critical events. Notification center in regulator portal. Unsubscribe/preference management.

**ACs**:

1. Given AML alert above CRITICAL threshold, When regulator subscribed, Then real-time notification
2. Given daily digest preference, When configured, When events occur, Then aggregated next morning
3. Given notification preferences, When updated in portal, Then effective immediately

**Tests**: realtime_critical · daily_digest · large_trade_threshold · settlement_failure_cluster · preference_update · email_notification · portal_notification · unsubscribe

**Dependencies**: STORY-R01-001, K-05

---

## Feature R01-F05 — Regulatory Dashboard (1 Story)

---

### STORY-R01-010: Implement regulator analytics dashboard

**Feature**: R01-F05 · **Points**: 3 · **Sprint**: 16 · **Team**: Epsilon

Regulator analytics dashboard: macro-view of supervised entities. Metrics: settlement efficiency trends, failed settlement rates, AML alert rates, KYC completion rates, regulatory report compliance rate. Cross-firm comparison (anonymized where required). Trend charts: time series, benchmark lines. Custom date range (BS calendar). Export as PDF regulatory summary report.

**ACs**:

1. Given regulator views dashboard, When loaded, Then all regulated tenants' aggregate metrics shown
2. Given trend chart, When date range set in BS, Then chart data aligned to BS period
3. Given PDF export, When generated, Then professional regulatory report with all charts

**Tests**: dashboard_load · aggregate_metrics · trend_charts · bs_date_range · cross_firm_comparison · pdf_export · anonymization · perf_under_5sec

**Dependencies**: STORY-R01-001, K-06, K-15

---

## Feature R01-F06 — AI Regulatory Intelligence (1 Story)

---

### STORY-R01-011: Implement AI-assisted regulatory query and analysis copilot

**Feature**: R01-F06 · **Points**: 5 · **Sprint**: 16 · **Team**: Gamma

RAG-based LLM copilot for regulators (SEBON/NRB examiners) within the regulator portal. Enables examiners to investigate supervised entities using natural language: "Show all trades by Broker A that occurred within 30 minutes of insider event X", "Summarise KYC compliance status for all brokers in October 2082 BS", "List any client money segregation breaches in Q3 with resolution timelines". Architecture: LLM (locally-hosted, K-04 T2 sandbox) generates optimized SQL/API queries over the K-08 data catalogs and K-07 audit records; results formatted as structured tables + LLM-written summary. All examiner queries and responses logged immutably. Copilot scoped to regulator’s authorized jurisdiction and tenants (K-01 RBAC enforced). Governed by K-09 advisory.

**ACs**:

1. Given SEBON examiner query "List all trades on instrument NLICL in October 2082 BS where order was placed within 10 minutes of a price move > 3%", When copilot processes, Then SQL generated against D-01/D-04 data, trades returned as table with trade_time, price_move_pct, K-07 audit_refs; narrative summary: "14 trades identified matching criteria involving 3 brokers"
2. Given examiner query referencing a jurisdiction they are not authorized for, When RBAC check runs, Then query blocked: "Access denied — you are authorized for NP jurisdiction only"
3. Given examiner copilot interaction, When audited, Then K-07 record contains: examiner_id, query_text, generated_sql, result_row_count, timestamp — all immutable and exportable for regulatory record

**Tests**: natural_language_sql_generation · multi_filter_query · narrative_summary · rbac_jurisdiction_enforcement · k07_immutable_audit · result_export · local_llm_only · k09_advisory_governance · perf_query_under_10sec

**Dependencies**: K-07 (audit), K-08 (data catalog), K-09 (AI governance), K-04 (T2 sandbox), K-01 (RBAC), K-15

---

# EPIC R-02: INCIDENT RESPONSE (12 Stories)

## Feature R02-F01 — Incident Detection (2 Stories)

---

### STORY-R02-001: Implement automated incident detection engine

**Feature**: R02-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Zeta

Automated incident detection from K-06 alerts. Incident trigger rules: if N alerts of same type in T minutes → P1/P2 incident. Alert correlation: group related alerts into single incident. Incident classification: PLATFORM (all tenants affected), TENANT (single tenant), SERVICE (single microservice). Auto-severity: based on number of affected tenants, services, and user impact. IncidentDetected event.

**ACs**:

1. Given 5 settlement failure alerts in 2 minutes, When correlated, Then P1 incident auto-created
2. Given related alerts (payment + ledger + settlement), When grouped, Then single incident with all alerts
3. Given single-tenant issue, When classified, Then TENANT incident, not PLATFORM

**Tests**: auto_incident_from_alerts · alert_correlation · severity_p1 · severity_p2 · tenant_vs_platform · auto_classification · event_emission · perf_detection_under_30sec

**Dependencies**: K-06, K-05

---

### STORY-R02-002: Implement incident runbook integration

**Feature**: R02-F01 · **Points**: 2 · **Sprint**: 15 · **Team**: Zeta

Runbook library: pre-defined step-by-step procedures for common incidents (settlement failure, event bus overload, service crash, database connection loss). Runbook auto-suggested based on incident type/service. Runbook steps: MANUAL (human action) and AUTOMATED (script/API call). Step execution tracked. Runbook completion rate metric.

**ACs**:

1. Given settlement failure incident, When created, Then settlement runbook auto-suggested
2. Given automated step, When executed from runbook, Then API call made and result logged
3. Given runbook completed, When all steps checked, Then incident moves to MONITORING phase

**Tests**: runbook_suggestion · auto_step_execute · manual_step_track · completion_metric · runbook_library · step_api_call · perf_runbook_load

**Dependencies**: STORY-R02-001

---

## Feature R02-F02 — Communications (2 Stories)

---

### STORY-R02-003: Implement incident communication templates

**Feature**: R02-F02 · **Points**: 2 · **Sprint**: 15 · **Team**: Alpha

Incident communication: structured updates to stakeholders during incidents. Templates: Initial Notification, Progress Update, Resolution. Audience segmentation: internal (engineering, ops, management), external (affected tenants, regulators if P1). Template placeholders: {{ incident_id }}, {{ severity }}, {{ affected_services }}, {{ estimated_resolution }}. Draft → reviewed → sent workflow. Communication log per incident.

**ACs**:

1. Given P1 incident, When initial comms triggered, Then template pre-filled, sent to internal + external
2. Given comms draft, When reviewed by incident commander, Then approved before sending
3. Given communication sent, When logged, Then all messages with timestamps in incident timeline

**Tests**: template_prefill · comms_draft_review · send_internal · send_external · communication_log · p2_internal_only · template_customization · audit_trail

**Dependencies**: STORY-R02-001

---

### STORY-R02-004: Implement status page integration

**Feature**: R02-F02 · **Points**: 3 · **Sprint**: 15 · **Team**: Zeta

Public/internal status page: reflects real-time platform component health. Components: API Gateway, Order Management, Settlement, Event Bus, Market Data, Regulatory Reporting, Admin Portal. Status: OPERATIONAL/DEGRADED/PARTIAL_OUTAGE/MAJOR_OUTAGE. Auto-update from incident: P1 incident → component status → MAJOR_OUTAGE. P2 → DEGRADED. Manual override possible. Tenant-facing URL (each tenant gets their status page view). History of past incidents (90 days).

**ACs**:

1. Given P1 incident on settlement, When created, Then settlement component → MAJOR_OUTAGE on status page
2. Given incident resolved, When status updated, Then component returns to OPERATIONAL
3. Given tenant status page, When viewed, Then shows only components relevant to that tenant

**Tests**: p1_major_outage · p2_degraded · incident_resolved_operational · manual_override · tenant_view · history_90_days · auto_update · perf_status_page_sub_100ms

**Dependencies**: STORY-R02-001

---

## Feature R02-F03 — Post-Incident Review (2 Stories)

---

### STORY-R02-005: Implement post-incident review workflow

**Feature**: R02-F03 · **Points**: 3 · **Sprint**: 16 · **Team**: Alpha

PIR (Post-Incident Review) workflow: mandatory for P1/P2 incidents, optional for P3/P4. PIR structure: incident timeline, root cause analysis (5-whys template), impact assessment, action items, preventive measures. PIR workflow: SRE drafts → incident commander reviews → engineering manager approves → published. SLA: PIR completed within 72 hours of incident resolution.

**ACs**:

1. Given P1 resolved, When PIR triggered, Then SRE assigned, 72-hour deadline set
2. Given PIR draft, When reviewed by incident commander, When approved by manager, Then published
3. Given PIR published, When accessed, Then timeline, root cause, action items visible

**Tests**: pir_mandatory_p1_p2 · optional_p3 · pir_72_hour_sla · draft_review_approve · published_accessible · action_items_tracked · 5_whys_template · audit_trail

**Dependencies**: STORY-R02-001, W-01

---

### STORY-R02-006: Implement action item tracking

**Feature**: R02-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Action items from PIR: each preventive measure tracked as action item. Action item: title, description, assignee, due_date, priority, status (OPEN → IN_PROGRESS → COMPLETED/CANCELLED), linked_incident. Dashboard: open action items by assignee, SLA compliance. Weekly digest to engineering leads. Block re-opening closed incident if action items overdue.

**ACs**:

1. Given PIR with 3 action items, When published, Then 3 action items created and assigned
2. Given action item overdue, When SLA missed, Then escalation to engineering manager
3. Given all action items closed, When checked, Then incident can be marked FULLY_RESOLVED

**Tests**: action_item_creation · assignment · overdue_escalation · weekly_digest · fully_resolved_gate · status_transitions · dashboard_view · priority_sorting

**Dependencies**: STORY-R02-005, K-07

---

## Feature R02-F04 — Incident Analytics (2 Stories)

---

### STORY-R02-007: Implement incident metrics and MTTR tracking

**Feature**: R02-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Incident metrics: MTTA (Mean Time to Acknowledge), MTTR (Mean Time to Resolve), MTTD (Detect), incident frequency by severity, repeat incident rate. Trend charts: MTTR over last 12 months, incidents per week. SLA targets: P1 MTTA < 5 min, MTTR < 4 hours. SLA compliance %. Service-level breakdown: which services have most incidents. Export to management report.

**ACs**:

1. Given 10 P1 incidents, When MTTR calculated, Then average minutes from detection to resolution
2. Given MTTR target 4h, When SLA compliance queried, Then percentage within target shown
3. Given service breakdown, When queried, Then incidents per service ranked highest to lowest

**Tests**: mtta_calculation · mttr_calculation · mttd_calculation · sla_compliance · trend_12_months · service_breakdown · management_report · repeat_incident_rate

**Dependencies**: STORY-R02-001, K-06

---

### STORY-R02-008: Implement reliability reporting

**Feature**: R02-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Reliability reports: monthly/quarterly reliability summary. Metrics: uptime per service (%), incident count by severity, SLA compliance, PIR completion rate, action item closure rate. Executive summary: overall platform reliability score. Trend: improving/stable/deteriorating. Exported as PDF. Auto-delivered to CTO, engineering leads.

**ACs**:

1. Given monthly period, When report generated, Then all reliability metrics included
2. Given overall platform reliability score, When calculated, Then weighted composite of all metrics
3. Given deteriorating trend, When detected, Then highlighted in red with root cause suggestions

**Tests**: monthly_report_all_metrics · quarterly_report · platform_score · trend_deteriorating · pdf_export · auto_delivery · weighted_scoring · executive_summary

**Dependencies**: STORY-R02-007

---

## Feature R02-F05 — Incident Playbooks (2 Stories)

---

### STORY-R02-009: Implement automated incident playbook execution

**Feature**: R02-F05 · **Points**: 3 · **Sprint**: 16 · **Team**: Zeta

Automated playbook execution for known incident patterns. Playbook: trigger_condition (incident type/severity), automated_steps (K-06 alerts suppression, scale up services, circuit breaker activation, notification dispatch). Playbook runs on incident creation matching trigger. Human escalation step if automated steps don't resolve. Playbook success: incident moves to MONITORING.

**ACs**:

1. Given event bus overload incident, When playbook triggered, Then auto-scale consumers, suppress non-critical alerts
2. Given playbook automated steps succeed, When verified, Then incident → MONITORING, team notified
3. Given automated steps insufficient, When escalation step reached, Then SRE paged immediately

**Tests**: playbook_trigger · auto_scale · alert_suppression · circuit_breaker_activate · success_monitoring · escalation_sre · playbook_completion · audit_steps

**Dependencies**: STORY-R02-001, K-18, K-06

---

### STORY-R02-010: Implement game day and chaos drill integration

**Feature**: R02-F05 · **Points**: 2 · **Sprint**: 16 · **Team**: Zeta

Game Day management: schedule and execute chaos engineering exercises (with K-10 chaos tools from M4). Game day: define scenario, inject failure, observe detection + response + recovery. Measure: detection time, response accuracy, runbook effectiveness, MTTR. Game day report: scenario description, timeline, team performance, gaps found. Schedule in maintenance window. Learning outcomes feed PIR process.

**ACs**:

1. Given game day scheduled, When maintenance window starts, Then chaos injection begins
2. Given failure injected, When detection time measured, Then compared to target (P1 < 5 min)
3. Given game day report, When generated, Then gaps identified, action items created

**Tests**: game_day_schedule · chaos_injection · detection_time_measured · response_accuracy · runbook_effectiveness · gaps_identified · action_item_creation · report_generation

**Dependencies**: STORY-R02-009

---

## Feature R02-F06 — AI Incident Intelligence (2 Stories)

---

### STORY-R02-011: Implement ML-powered incident pattern detection and clustering

**Feature**: R02-F06 · **Points**: 5 · **Sprint**: 16 · **Team**: Zeta

Apply ML to the K-07 incident history corpus to detect recurring incident patterns, surface seasonal risks, and predict high-risk time windows. Clustering: HDBSCAN on incident embeddings (sentence-transformer on incident title + alert description + affected services) groups structurally similar incidents across time. Recurring patterns surfaced to SRE as "Known Pattern" labels on new incidents, shortcutting investigation. Predictive risk: logistic regression on temporal features (day-of-week, end-of-month, deployment events) predicts high-risk windows 48h in advance — enabling pre-emptive staffing and enhanced monitoring. Governed by K-09.

**ACs**:

1. Given a new settlement service P2 incident, When incident pattern matching runs, Then "Known Pattern: DB connection pool exhaustion during end-of-month settlement spike" surfaced with 6 prior instances, links to their PIRs and resolutions — expected resolution time shown
2. Given temporal model forecasting end-of-month settlement spike (3 days away) with 78% probability of P2 incident based on historical pattern, When HighRiskWindowPredicted emitted, Then SRE receives advance alert: "High-risk window predicted Fri-Sat Mar 28-29 (end-of-month settlement) — 78% P2 incident probability based on 8 prior occurrences; recommend: pre-scale D-09, enable enhanced monitoring"
3. Given 3 months of incident data, When clustering runs, Then at least 5 distinct recurring incident clusters identified above noise threshold; each cluster labeled with human-readable pattern name

**Tests**: incident_cluster_known_pattern · prior_pir_links · resolution_time_estimate · high_risk_window_prediction · temporal_feature_endofmonth · advance_alert_48h · pre_scale_recommendation · k09_model_governance · perf_clustering_under_5min

**Dependencies**: STORY-R02-001, K-07 (incident history), K-09 (AI governance)

---

### STORY-R02-012: Implement AI root cause analysis and remediation recommendation

**Feature**: R02-F06 · **Points**: 5 · **Sprint**: 16 · **Team**: Zeta

LLM + retrieval-based root cause analysis (RCA) assistant for SRE incident response. On incident creation, async pipeline: (1) retrieve K-06 correlated alerts (from K06-022 alert correlation), K-07 recent deployment events, K-08 lineage of affected services; (2) LLM generates structured RCA hypothesis: probable root cause, contributing factors, affected blast radius, recommended immediate remediation steps with runbook references. Hypothesis presented in incident management UI within 3 minutes of incident creation. SRE validates/updates hypothesis; accepted RCA stored in PIR (R02-F03); rejected hypotheses fed back to improve retrieval. All LLM interactions audited via K-07. Governed by K-09 advisory.

**ACs**:

1. Given P1 incident: 5 correlated settlement/ledger alerts, a D-09 deployment 2 hours ago (from K-07), and K-08 showing D-09 is a critical dependency of D-12/D-13 (from lineage), When RCA pipeline runs, Then within 3 minutes: hypothesis generated: "Root cause: likely D-09 v2.3.1 deployment introduced regression in ledger posting (see K-07 deploy event); contributing factor: DB migration ran during peak settlement window; blast radius: D-09, D-12, D-13; Immediate action: rollback D-09 to v2.3.0 (K-10 rollback), pause settlement batch"
2. Given SRE validates hypothesis as correct and selects recommended remediation, When executed, Then K-10 rollback initiated; SRE action logged; accepted hypothesis stored in PIR record
3. Given hypothesis rejected by SRE (actual root cause was infrastructure, not deployment), When marked rejected, Then retrieval corpus updated: deployment as false signal noted; model feedback stored for next RCA refinement

**Tests**: rca_hypothesis_within_3min · deployment_correlation · lineage_blast_radius · correlated_alerts_input · remediation_steps_with_runbook · k10_rollback_integration · pir_storage · rejected_hypothesis_feedback · k07_audit · local_llm_no_external · k09_advisory_tier · perf_rca_under_3min

**Dependencies**: STORY-R02-001, K-06 (K06-022 alert correlation), K-07, K-08 (lineage), K-09 (AI governance), K-10 (rollback)

---

# EPIC PU-004: OPERATOR MANIFEST & PACKAGING (8 Stories)

## Feature PU004-F01 — Release Manifest (2 Stories)

---

### STORY-PU004-001: Implement release manifest definition format

**Feature**: PU004-F01 · **Points**: 3 · **Sprint**: 15 · **Team**: Zeta

Define release manifest format: YAML definition of a platform release. Manifest: release_id, version (SemVer), release_date, services[] (name, version, chart_version), plugins[] (id, version), config_changes[] (K-02 key-value pairs), migration_scripts[], dependencies_matrix, breaking_changes[], upgrade_notes. Schema validated. Manifest stored in release registry.

**ACs**:

1. Given release YAML, When schema validated, Then all required fields present
2. Given manifest with services list, When parsed, Then each service maps to Helm chart + version
3. Given breaking change declared, When tenant reads manifest, Then upgrade path explicitly documented

**Tests**: manifest_schema_validation · service_version_mapping · config_changes_parsed · migration_scripts_listed · breaking_change_flag · semver_format · registry_storage

**Dependencies**: K-10

---

### STORY-PU004-002: Implement manifest signing and verification

**Feature**: PU004-F01 · **Points**: 2 · **Sprint**: 15 · **Team**: Zeta

Sign release manifests with platform signing key (K-14 managed). Signature: RSA-4096 or Ed25519. Verification: before any deployment, manifest signature verified. Tampered manifest (signature mismatch) rejected; deployment blocked. Manifest hash included in deployment audit log. Distribute public key to tenants for independent verification.

**ACs**:

1. Given valid manifest, When signed, Then signature embedded in manifest file
2. Given signature verification at deploy time, When valid, Then deployment proceeds
3. Given tampered manifest (field modified), When verified, Then signature invalid, deployment blocked

**Tests**: sign_manifest · verify_valid · verify_tampered_blocked · hash_in_audit · public_key_distribution · key_rotation · ed25519_support

**Dependencies**: STORY-PU004-001, K-14, K-07

---

## Feature PU004-F02 — Package Build Pipeline (2 Stories)

---

### STORY-PU004-003: Implement release packaging pipeline

**Feature**: PU004-F02 · **Points**: 3 · **Sprint**: 15 · **Team**: Zeta

CI/CD pipeline for platform release packages. Steps: collect service Docker images (from image registry) + Helm charts + config defaults + migration scripts + documentation → validate all components → sign manifest → package into release bundle (tar.gz or OCI artifact) → publish to release registry. Pipeline triggered on release tag (Git tag vX.Y.Z). Release bundle versioned and immutable.

**ACs**:

1. Given Git tag v3.0.0, When pipeline triggers, Then all components collected and bundled
2. Given release bundle, When published to registry, Then immutable, further modifications rejected
3. Given component missing (service image not found), When packaging, Then pipeline fails with details

**Tests**: pipeline_trigger_on_tag · component_collection · manifest_signing · oci_artifact · publish_immutable · missing_component_fail · pipeline_audit · perf_pipeline_under_30min

**Dependencies**: STORY-PU004-002, K-10

---

### STORY-PU004-004: Implement release notes generation

**Feature**: PU004-F02 · **Points**: 2 · **Sprint**: 16 · **Team**: Alpha

Auto-generate release notes from: Git commit history (conventional commits format), migration impact analysis, breaking changes from manifest, new features (from linear/Jira), fixed bugs. Format: Markdown with sections: Highlights, New Features, Bug Fixes, Breaking Changes, Migration Guide, Service Versions. Published to developer portal, emailed to tenant admins on new release.

**ACs**:

1. Given release v3.0.0, When notes generated, Then all sections populated from conventional commits
2. Given breaking change, When notes include migration guide, Then step-by-step upgrade instructions
3. Given released, When emailed to tenant admins, Then formatted HTML email with version highlights

**Tests**: conventional_commits_parse · breaking_change_guide · feature_section · bug_fix_section · markdown_output · portal_publish · email_delivery · service_versions_table

**Dependencies**: STORY-PU004-003

---

## Feature PU004-F03 — Upgrade Orchestration (2 Stories)

---

### STORY-PU004-005: Implement platform upgrade orchestrator

**Feature**: PU004-F03 · **Points**: 5 · **Sprint**: 16 · **Team**: Zeta

Platform upgrade orchestrator: upgrade entire platform from release bundle. Steps: 1) Download + verify bundle, 2) Pre-upgrade health check, 3) Run migration scripts (DB schema), 4) Rolling upgrade of services (order per dependency graph), 5) Post-upgrade smoke tests, 6) Cut-over. Zero-downtime rolling upgrades for non-breaking changes. Blue-green for breaking changes. Rollback on any step failure.

**ACs**:

1. Given release bundle v3.0.0, When upgrade initiated, Then services upgraded in dependency order
2. Given migration script failure, When step 3 fails, Then rollback initiated, database restored
3. Given post-upgrade smoke tests fail, When detected, Then rollback, incident created automatically

**Tests**: upgrade_dependency_order · migration_failure_rollback · smoke_test_failure_rollback · zero_downtime_rolling · blue_green_breaking · post_upgrade_verification · incident_on_failure · perf_upgrade_under_2hr

**Dependencies**: STORY-PU004-003, K-10

---

### STORY-PU004-006: Implement tenant-specific upgrade scheduling

**Feature**: PU004-F03 · **Points**: 2 · **Sprint**: 16 · **Team**: Zeta

Per-tenant upgrade scheduling: tenants can choose upgrade timing within operator-defined upgrade window. Tenant options: IMMEDIATE (when operator releases), SCHEDULED (specific date in upgrade window), MANUAL (tenant initiates). Operator can force upgrade if more than 1 major version behind. Upgrade status per tenant. Tenant pre-upgrade checklist (data backup confirmation, change freeze).

**ACs**:

1. Given tenant selects SCHEDULED upgrade on specific date, When that date arrives, Then upgrade runs automatically
2. Given tenant 2 major versions behind, When forced upgrade policy applied, Then upgrade scheduled by operator
3. Given pre-upgrade checklist, When tenant confirms data backup, Then upgrade allowed to proceed

**Tests**: scheduled_upgrade · forced_upgrade_policy · pre_upgrade_checklist · tenant_manual_initiate · upgrade_status · change_freeze · backup_confirmation

**Dependencies**: STORY-PU004-005, STORY-O01-001

---

## Feature PU004-F04 — Upgrade Verification (2 Stories)

---

### STORY-PU004-007: Implement post-upgrade smoke test suite

**Feature**: PU004-F04 · **Points**: 3 · **Sprint**: 16 · **Team**: Zeta

Automated post-upgrade smoke test suite: fast-running tests verifying critical platform paths after upgrade. Tests: API Gateway reachable, K-05 event bus accepts and delivers events, K-02 config service responds, auth token generation works, core order submission, settlement query. Suite runs in < 2 minutes. Each test: retry 3x before fail. PASS = upgrade confirmed, FAIL = rollback triggered.

**ACs**:

1. Given upgrade completed, When smoke suite runs, Then 8 critical path tests execute within 2 minutes
2. Given test fails after 3 retries, When failure confirmed, Then rollback triggered, incident created
3. Given all tests pass, When suite completes, Then UpgradeVerified event, upgrade marked STABLE

**Tests**: api_gateway_test · event_bus_test · config_service_test · auth_test · order_submission_test · suite_under_2min · retry_3x · rollback_on_fail

**Dependencies**: STORY-PU004-005

---

### STORY-PU004-008: Implement upgrade history and audit

**Feature**: PU004-F04 · **Points**: 2 · **Sprint**: 16 · **Team**: Zeta

Upgrade history: every upgrade/rollback logged. Record: from_version, to_version, initiated_by, start_time, end_time, status (SUCCESS/ROLLED_BACK/PARTIAL_FAIL), migration_scripts_run[], smoke_test_results, rollback_reason. Audit trail via K-07. History queryable per tenant and platform-wide. Export for compliance (upgrade log per year).

**ACs**:

1. Given upgrade completed, When logged, Then all fields including migration scripts and smoke results
2. Given rollback, When logged, Then rollback_reason and state at rollback time captured
3. Given yearly compliance export, When generated, Then all upgrades in the year in PDF

**Tests**: success_logged · rollback_logged · migration_scripts_recorded · smoke_results_recorded · per_tenant_query · platform_wide_query · yearly_export

**Dependencies**: STORY-PU004-005, K-07

---

# MILESTONE 3C SUMMARY

| Epic                        | Feature Count | Story Count | Total SP |
| --------------------------- | ------------- | ----------- | -------- |
| O-01 Operator Console       | 6             | 14          | 45       |
| P-01 Plugin Certification   | 5             | 11          | 29       |
| R-01 Regulator Portal       | 6             | 11          | 32       |
| R-02 Incident Response      | 6             | 12          | 37       |
| PU-004 Manifest & Packaging | 4             | 8           | 24       |
| **TOTAL**                   | **27**        | **56**      | **167**  |

**Sprint 15**: O-01 (001-006,009), P-01 (001-006), R-01 (001-005), R-02 (001-004), PU-004 (001-003) (~28 stories)
**Sprint 16**: O-01 (007,008,010-014), P-01 (007-011), R-01 (006-011), R-02 (005-012), PU-004 (004-008) (~28 stories — includes all AI intelligence stories)
