# MILESTONE 3B — PLATFORM TOOLS & WORKFLOWS

## Sprints 13–14 | 58 Stories | K-13, K-12, W-01, W-02

> **Story Template**: Each story includes ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, and dependencies.

---

# EPIC K-13: ADMIN PORTAL (14 Stories)

## Feature K13-F01 — Platform Configuration UI (3 Stories)

---

### STORY-K13-001: Implement admin portal shell and navigation

**Feature**: K13-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

Build the administrative portal React SPA: shell layout with sidebar navigation, header (env indicator, user menu, notifications bell), breadcrumbs, and global search. Navigation sections: Configuration, Users & Roles, Observability, Deployments, Data Governance, Audit Logs, Plugin Management, System Health. JWT auth integrated with K-01. Role-based menu: items hidden if role lacks permission.

**ACs**:

1. Given admin user logs in, When portal loads, Then sidebar shows all permitted navigation sections
2. Given read-only user, When sidebar renders, Then write-action items hidden (e.g., Plugin Management)
3. Given global search "settlement", When typed, Then matching config keys, users, audit entries shown

**Tests**: shell_renders · auth_required · role_based_menu · global_search · breadcrumbs · env_indicator · notification_bell · keyboard_navigation

**Dependencies**: K-01

---

### STORY-K13-002: Implement K-02 configuration management UI

**Feature**: K13-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

UI for K-02 configuration engine: browse config tree (namespace/service/key hierarchy), edit values with type-aware inputs (string, number, boolean, JSON, duration), and manage dynamic value catalogs and metadata-backed task/form schemas. View history/diff, trigger override promotions, and preview effective tenant-specific option sets before activation. Maker-checker: edits submitted for approval inline with diff view. Bulk import from YAML. Config search with metadata filtering.

**ACs**:

1. Given config key with type=duration, When edited, Then validated as ISO duration, preview shown
2. Given config change submitted, When maker-checker flow, Then approver sees diff (old → new) before approving
3. Given bulk YAML import, When 50 keys uploaded, Then all validated and submitted for approval atomically
4. Given a metadata-backed value catalog update, When tenant preview selected, Then effective options and bounds are shown before approval

**Tests**: edit_string · edit_number · edit_json · duration_validation · dynamic_value_catalog_preview · schema_form_render · maker_checker_diff · bulk_import · history_diff · search_filter · perf_tree_render_under_1sec

**Dependencies**: K-02, K-01

---

### STORY-K13-003: Implement system health overview dashboard

**Feature**: K13-F01 · **Points**: 2 · **Sprint**: 13 · **Team**: Epsilon

System health overview: real-time status of all services (green/yellow/red), K-05 event bus health, database connection status, cache health (Redis), external adapter statuses (CSD, bank feeds). Service map with dependency arrows colored by health. Historical uptime calculation (last 30 days, 90 days). Incident timeline showing past outages.

**ACs**:

1. Given all services healthy, When dashboard loaded, Then all indicators green, uptime 99.9%
2. Given service in ERROR state, When viewed, Then red indicator, recent error log preview, link to Grafana
3. Given service dependency map, When rendered, Then arrows show dependency direction with health color

**Tests**: all_healthy_green · error_state_red · dependency_map · uptime_calculation · incident_timeline · external_adapter_status · auto_refresh_30sec

**Dependencies**: K-06

---

## Feature K13-F02 — User & Role Management UI (3 Stories)

---

### STORY-K13-004: Implement user management UI

**Feature**: K13-F02 · **Points**: 2 · **Sprint**: 13 · **Team**: Epsilon

User management: list users with search/filter (name, email, role, status), create/edit/deactivate, assign roles (K-01), view login history, reset MFA. Bulk operations: bulk deactivate, bulk role assign. User detail: permissions effective list, active sessions, recent audit events. Export user list CSV.

**ACs**:

1. Given user list, When searched by email, Then matching user shown with role and status
2. Given user selected for role assignment, When multiple roles added, Then effective permissions recalculated
3. Given deactivate user, When confirmed, Then all sessions invalidated immediately

**Tests**: search_by_email · role_assignment · effective_permissions · deactivate_sessions · bulk_deactivate · mfa_reset · export_csv · login_history

**Dependencies**: K-01

---

### STORY-K13-005: Implement role and permission management UI

**Feature**: K13-F02 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

Role management: list roles, create custom roles, assign permissions. Permission browser: hierarchical tree of all permissions (system, service, action level). Role comparison: side-by-side permission diff. Inheritance: roles can extend other roles. Maker-checker for role creation/modification. Audit trail showing who changed what role permissions.

**ACs**:

1. Given custom role creation, When permissions selected from tree, Then role created via maker-checker
2. Given role A extends role B, When permissions computed, Then union of permissions shown as effective
3. Given role comparison view, When two roles selected, Then differences highlighted

**Tests**: role_create · permission_tree · role_inheritance · role_comparison · maker_checker · audit_trail · permission_search · delete_prevents_active_users

**Dependencies**: K-01, K-07

---

### STORY-K13-006: Implement API key and service account management

**Feature**: K13-F02 · **Points**: 2 · **Sprint**: 13 · **Team**: Epsilon

Service account management: create service accounts for inter-service auth, generate API keys, set scopes (limited permissions), set expiry. API key list: masked preview, last_used, expiry, associated service. Key rotation: generate new, grace period for old. Revoke immediately. Webhook for key expiry notification (30 days before).

**ACs**:

1. Given service account created, When API key generated, Then shown once in full, stored as hash
2. Given API key expiry in 5 days, When expiry check runs, Then notification sent to owner
3. Given key rotation, When new key generated, Then old key valid for grace period (7 days default)

**Tests**: service_account_create · api_key_generate · masked_display · expiry_notification_30d · key_rotation · revoke_immediately · scope_enforcement · hash_storage

**Dependencies**: K-01

---

## Feature K13-F03 — Plugin Lifecycle UI (3 Stories)

---

### STORY-K13-007: Implement plugin registry UI

**Feature**: K13-F03 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

Plugin management UI (frontend for K-04 Plugin Runtime): list all plugins with tier, status, version, author, last_updated. Filter by tier (T1/T2/T3), status (STAGED/ACTIVE/SUSPENDED/DEPRECATED), domain. Plugin detail: manifest, changelog, sandbox metrics, incident history. Bulk operations: suspend/activate multiple plugins.

**ACs**:

1. Given plugin list, When filtered by TIER_3 + ACTIVE, Then only T3 active plugins shown
2. Given plugin detail, When viewed, Then shows manifest, metrics, and sandbox usage
3. Given bulk suspend on 5 plugins, When confirmed, Then all 5 suspended atomically

**Tests**: filter_tier_status · plugin_detail · sandbox_metrics · bulk_suspend · bulk_activate · search_by_name · sort_by_last_updated · perf_list_100_plugins

**Dependencies**: K-04

---

### STORY-K13-008: Implement plugin deployment and certification UI

**Feature**: K13-F03 · **Points**: 3 · **Sprint**: 14 · **Team**: Epsilon

Plugin deployment wizard: upload artifact → validate manifest → run sandbox tests → security scan → certification review (for T3) → deploy. Step-by-step UI with validation at each stage. Certification workflow: submit for review, reviewer checklist, approve/reject with notes. Version comparison: diff between plugin versions. Rollback plugin to previous version.

**ACs**:

1. Given plugin artifact uploaded, When wizard runs, Then validation and sandbox test results shown step-by-step
2. Given T3 plugin, When certification submitted, Then reviewer checklist with approve/reject
3. Given plugin v2 deployed, When rollback requested, Then v1 reactivated within 30 seconds

**Tests**: wizard_upload · validation_step · sandbox_test_step · security_scan · certification_workflow · rollback_30sec · version_diff · perf_wizard_under_5min

**Dependencies**: K-04, K-01

---

### STORY-K13-009: Implement plugin sandbox monitoring UI

**Feature**: K13-F03 · **Points**: 2 · **Sprint**: 14 · **Team**: Epsilon

Real-time sandbox monitoring per plugin: CPU usage, memory usage, execution time histogram, syscall violations, network access attempts, error rate. Charts: time-series for last 1h/6h/24h. Violation log: each sandbox violation with timestamp, violation_type, details. Alert widget: if usage exceeds quota, shows warning. SLA compliance per plugin API call.

**ACs**:

1. Given active T3 plugin, When monitoring viewed, Then CPU/memory usage charts real-time
2. Given sandbox violation (syscall blocked), When logged, Then violation appears in violation log
3. Given plugin exceeding CPU quota, When detected, Then warning badge on plugin card

**Tests**: cpu_chart · memory_chart · execution_histogram · violation_log · alert_badge · time_range_selection · sla_compliance · perf_chart_render

**Dependencies**: K-04, K-06

---

## Feature K13-F04 — Audit Log Explorer (2 Stories)

---

### STORY-K13-010: Implement audit log explorer UI

**Feature**: K13-F04 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

Audit log explorer: search and browse K-07 audit trail. Search: full-text, filter by service, action, actor, entity_id, date range, severity. Result timeline view: events in chronological order with actor avatar. Detail panel: full audit event with before/after JSON diff. Export: PDF evidence package, CSV for analysis. Tamper detection flag on events.

**ACs**:

1. Given search for "actor=user@firm.com AND action=APPROVE", When results shown, Then matching audit events
2. Given audit event with before/after data, When detail viewed, Then JSON diff with added/removed highlighted
3. Given evidence export requested, When generated, Then PDF with events, actor, timestamps, hash chain

**Tests**: search_full_text · filter_actor · filter_action · date_range · detail_diff · export_pdf · export_csv · tamper_flag · perf_search_1M_events_under_5sec

**Dependencies**: K-07

---

### STORY-K13-011: Implement audit analytics and anomaly view

**Feature**: K13-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Epsilon

Audit analytics: unusual access patterns flagged (many events from same actor in short time, access to RESTRICTED data, off-hours access). Heatmap: action frequency by hour of day / day of week. Top actors by event count. Anomaly score per actor based on historical patterns. Alert: AuditAnomalyDetected for high-score actors.

**ACs**:

1. Given user generates 500 audit events in 5 minutes, When anomaly detected, Then AuditAnomalyDetected event
2. Given heatmap, When viewed, Then shows action frequency with color intensity
3. Given top actors, When sorted by event count, Then descending list with action type breakdown

**Tests**: anomaly_rate_detection · off_hours_access · restricted_data_access · heatmap_render · top_actors · anomaly_score · perf_analytics_under_5sec

**Dependencies**: STORY-K13-010, K-07

---

## Feature K13-F05 — Observability Hub (2 Stories)

---

### STORY-K13-012: Implement observability hub in admin portal

**Feature**: K13-F05 · **Points**: 3 · **Sprint**: 14 · **Team**: Epsilon

Embedded observability: Grafana dashboards inline in admin portal (iframe + SSO), alert management (create/edit K-06 alert rules), distributed trace explorer (Jaeger), log aggregation search (Loki). Single-pane-of-glass: admins don't need to leave portal for monitoring. Quick-link from service health status to relevant dashboard.

**ACs**:

1. Given admin clicks on service in health dashboard, When navigating, Then relevant Grafana dashboard opens inline
2. Given alert rule editor, When new rule created, Then applied to K-06 Prometheus
3. Given trace explorer, When trace ID searched, Then full distributed trace rendered in portal

**Tests**: grafana_iframe_sso · alert_rule_create · trace_explore · log_search · service_to_dashboard_link · responsive_layout · perf_dashboard_load_under_3sec

**Dependencies**: K-06

---

### STORY-K13-013: Implement SLA and KPI dashboard

**Feature**: K13-F05 · **Points**: 2 · **Sprint**: 14 · **Team**: Epsilon

Platform SLA/KPI dashboard: API availability, p50/p95/p99 latency per service, error rate per service, event processing throughput, settlement success rate, reconciliation compliance. Historical SLA compliance percentage (last 30/90 days). Traffic light per SLA target. Monthly SLA report auto-generation. Executive summary view (simplified metrics for non-technical users).

**ACs**:

1. Given SLA dashboard, When loaded, Then all KPIs with traffic lights vs target
2. Given API availability < 99.9%, When detected, Then red indicator, SLA breach logged
3. Given monthly report, When generated, Then PDF with SLA compliance per service

**Tests**: all_kpis · traffic_lights · sla_breach_log · monthly_report · executive_summary · 30_day_history · perf_under_3sec

**Dependencies**: K-06

---

## Feature K13-F06 — Maker-Checker Task Center (1 Story)

---

### STORY-K13-014: Implement maker-checker task center

**Feature**: K13-F06 · **Points**: 2 · **Sprint**: 14 · **Team**: Epsilon

Centralized task center: all pending maker-checker approvals across the platform in one UI. Tasks from all services: config changes (K-02), trade approvals (D-01), rebalances (D-03), regulatory reports (D-10), plugin deployments (K-04). Task: entity type, action, summary, maker, submitted_time, SLA deadline. Approve/reject with one-click + comment. Priority sorting: by deadline and business impact.

**ACs**:

1. Given maker-checker tasks across 5 services, When task center loaded, Then all pending tasks listed
2. Given task with SLA approaching, When sorted by deadline, Then at top of list with countdown
3. Given approve with comment, When submitted, Then specific service API called, audit logged

**Tests**: multi_service_tasks · deadline_sort · approve_with_comment · reject_with_reason · sla_countdown · priority_sort · pagination · perf_load_under_2sec

**Dependencies**: K-01, K-07

---

# EPIC K-12: PLATFORM SDK (15 Stories)

## Feature K12-F01 — SDK Core & Code Generation (3 Stories)

---

### STORY-K12-001: Implement SDK core abstractions and interfaces

**Feature**: K12-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Platform SDK providing standard interfaces for plugin/service development. Core modules: EventClient (K-05 wrapper), ConfigClient (K-02 wrapper), AuditClient (K-07 wrapper), RulesClient (K-03 wrapper), AuthClient (K-01 wrapper). SDK available in TypeScript, Python, Java. Auto-discovers service endpoints from K-02. SDK version aligned with platform version.

**ACs**:

1. Given SDK initialized in plugin, When EventClient.publish() called, Then event published to K-05 with metadata
2. Given ConfigClient.get("my.key"), When called, Then value fetched from K-02 with caching
3. Given SDK version, When platform version incompatible, Then CompatibilityError raised with guidance

**Tests**: event_client_publish · event_client_subscribe · config_client_get · audit_client_log · rules_client_evaluate · multi_language_ts · multi_language_python · version_compat

**Dependencies**: K-05, K-02, K-07, K-03, K-01

---

### STORY-K12-002: Implement OpenAPI code generation pipeline

**Feature**: K12-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Code generation from service OpenAPI specs: generate typed client SDKs (TypeScript, Python, Java) from OpenAPI 3.x definitions. Generated clients: typed request/response objects, async/await, retry logic, circuit breaker (K-18). Generation pipeline: spec validation → client generation → type checking → publish to package registry. CI trigger on spec change. Versioned packages.

**ACs**:

1. Given OMS OpenAPI spec, When generator runs, Then TypeScript + Python + Java clients generated
2. Given generated client, When type checked, Then no type errors in strict mode
3. Given spec change (new endpoint), When CI triggers, Then new package version published to registry

**Tests**: generate_ts · generate_python · generate_java · type_check_strict · retry_in_generated · circuit_breaker_in_generated · package_versioning · spec_validation

**Dependencies**: STORY-K12-001

---

### STORY-K12-003: Implement event schema code generation

**Feature**: K12-F01 · **Points**: 2 · **Sprint**: 13 · **Team**: Alpha

Generate type-safe event classes from Avro/JSON schemas in K-08 schema registry. Generated: event builder, serializer/deserializer, validation. Types match schema exactly (required vs optional). Generation on schema publish. Consumer stub generation: typed handler interface for each event type. Used by all services and plugins.

**ACs**:

1. Given OrderFilled Avro schema, When generated, Then TypeScript class with all fields typed
2. Given consumer stub, When service implements handler, Then type-safe event parameter
3. Given schema v2 with new field, When regenerated, Then new field added to generated class

**Tests**: generate_from_avro · generate_from_json · builder_pattern · serializer · deserializer · consumer_stub · v2_regeneration · backward_compat

**Dependencies**: K-08, STORY-K12-001

---

## Feature K12-F02 — Test Scaffolding (3 Stories)

---

### STORY-K12-004: Implement test harness SDK

**Feature**: K12-F02 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Testing utilities for platform services: TestEventBus (in-memory K-05 mock), TestConfigStore (in-memory K-02 mock), TestAuditStore, TestRulesEngine. Factory methods: EventFactory.orderFilled(overrides?), EventFactory.tradeFilled(overrides?). Given/When/Then helpers for BDD-style tests. Test database setup with Docker-compose fixtures. Test data cleanup utilities.

**ACs**:

1. Given TestEventBus in test, When event published, Then subscribers receive synchronously
2. Given EventFactory.orderFilled({ qty: 100 }), When built, Then fully valid OrderFilled event with overrides
3. Given testdb.setup(), When test starts, Then isolated schema created; testdb.teardown() drops it

**Tests**: test_event_bus_pub_sub · event_factory · config_mock · audit_mock · rules_mock · test_db_setup · test_db_teardown · isolation_between_tests

**Dependencies**: STORY-K12-001

---

### STORY-K12-005: Implement contract testing framework

**Feature**: K12-F02 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Consumer-driven contract testing: producers verify they meet consumer contracts. Contract definition: consumer specifies expected request/response or event format. Pact-compatible format. Contract broker: store contracts centrally. CI gate: producer tests fail if contract breaks. SDK utilities for defining and verifying contracts. Supports HTTP and event-based contracts.

**ACs**:

1. Given OMS consumer contract for EMS API, When EMS producer tests run, Then contract verified
2. Given event producer changes schema breaking consumer contract, When CI runs, Then test fails with contract diff
3. Given contract broker, When all contracts stored, Then version history and compatibility matrix

**Tests**: http_contract_verify · event_contract_verify · breaking_change_detected · contract_broker_store · compatibility_matrix · ci_gate · pact_format

**Dependencies**: STORY-K12-003, K-08

---

### STORY-K12-006: Implement performance test utilities

**Feature**: K12-F02 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

SDK utilities for performance testing: load generator (configurable TPS), latency measurement (p50/p95/p99), throughput calculation, memory profiler, event storm simulator (burst K-05 events). Integration with k6 for HTTP load testing. Performance test report: baseline comparison, regression detection (>10% degradation = fail).

**ACs**:

1. Given load generator at 1000 TPS, When run for 60 seconds, Then p99 latency reported
2. Given performance report vs baseline, When regression >10%, Then test marked FAIL
3. Given event storm simulator at 5000 events/sec, When run, Then consumer throughput measured

**Tests**: load_generator_1000tps · latency_measurement · regression_detection · event_storm · memory_profile · k6_integration · baseline_comparison

**Dependencies**: STORY-K12-004

---

## Feature K12-F03 — Plugin Development Kit (3 Stories)

---

### STORY-K12-007: Implement plugin development scaffold generator

**Feature**: K12-F03 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

CLI tool: `siddhanta plugin new --type T1|T2|T3 --domain trading --name my-plugin`. Generates: project structure, manifest.json template, test boilerplate, sandbox configuration, CI workflow (GitHub Actions). Plugin template per tier with correct sandbox constraints already configured. Local development: `siddhanta plugin run --dev` starts local sandbox.

**ACs**:

1. Given `siddhanta plugin new --type T3 --name sanctions-check`, When run, Then scaffolded project with T3 manifest
2. Given scaffolded project, When `siddhanta plugin run --dev`, Then local sandbox starts with hot reload
3. Given CI workflow generated, When plugin pushed to GitHub, Then CI runs tests and security scan

**Tests**: scaffold_t1 · scaffold_t2 · scaffold_t3 · manifest_generated · ci_workflow · local_sandbox · hot_reload · correct_sandbox_config

**Dependencies**: K-04, STORY-K12-001

---

### STORY-K12-008: Implement plugin testing utilities

**Feature**: K12-F03 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Plugin-specific testing: TestSandbox (runs plugin code in isolated sandbox for unit tests), mock dependencies (market data, rules engine), assertion helpers for plugin-specific patterns. SandboxViolationSimulator: tests that plugin handles denied syscalls gracefully. Plugin benchmark tool: measure execution time against SLA target.

**ACs**:

1. Given TestSandbox, When plugin code executed in test, Then sandbox constraints enforced even in test
2. Given SandboxViolationSimulator, When network access attempted in T1 plugin, Then NetworkDenied error returned
3. Given plugin benchmark, When run against SLA, Then pass/fail with p99 latency vs target

**Tests**: test_sandbox_constraints · violation_simulator · network_denied · filesystem_denied · benchmark_sla · mock_dependencies · assertion_helpers

**Dependencies**: K-04, STORY-K12-004

---

### STORY-K12-009: Implement plugin certification test suite

**Feature**: K12-F03 · **Points**: 3 · **Sprint**: 14 · **Team**: Alpha

Automated certification test suite run before plugin goes live. Tests: manifest completeness, sandbox compliance (no violations in 1000 executions), performance SLA (p99 < tier limit), memory limit compliance, graceful error handling (test with injected failures), data leakage check (output doesn't contain unexpected data). Report: PASS/FAIL per test with details.

**ACs**:

1. Given plugin submitted for certification, When suite runs, Then 8 mandatory checks executed
2. Given performance SLA fail (p99 > limit), When failed, Then certification FAILED with details
3. Given certification PASSED, When report generated, Then PDF certificate with plugin_id, version, test results

**Tests**: manifest_completeness · sandbox_compliance_1000 · performance_sla · memory_limit · error_handling · data_leakage · certificate_generation · multi_tier

**Dependencies**: STORY-K12-008

---

## Feature K12-F04 — Documentation Generation (3 Stories)

---

### STORY-K12-010: Implement auto-documentation from OpenAPI specs

**Feature**: K12-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Auto-generate API documentation from OpenAPI specs. Output: interactive HTML (Swagger UI, Redoc), PDF reference manual. Merge all service specs into unified developer portal. Code examples auto-generated per endpoint in TypeScript, Python, curl. Documentation versioned with API versions. Hosted as static site updated on each spec change via CI.

**ACs**:

1. Given OpenAPI spec, When docs generated, Then Swagger UI shows all endpoints interactive
2. Given endpoint GET /orders, When code examples generated, Then TypeScript + Python + curl shown
3. Given API v2 spec, When published, Then v2 docs visible alongside v1 with version switcher

**Tests**: swagger_ui_renders · redoc_renders · code_examples_ts · code_examples_python · code_examples_curl · versioning · pdf_export · ci_update

**Dependencies**: STORY-K12-002

---

### STORY-K12-011: Implement event catalog documentation

**Feature**: K12-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Event catalog documentation: list all K-05 events with schema, description, producers, consumers, example payloads. Auto-generated from K-08 schema registry and K-05 topic registry. AsyncAPI 2.x format. Browsable web UI: search by event name, producer service, consumer service. Lineage visualization: event flow diagram.

**ACs**:

1. Given OrderFilled event, When catalog viewed, Then schema, producers (OMS), consumers (EMS, D-09) shown
2. Given AsyncAPI format, When exported, Then valid AsyncAPI 2.x document
3. Given lineage diagram, When rendered, Then event flow from producer through all consumers

**Tests**: catalog_render · asyncapi_format · producer_consumer_links · lineage_diagram · search_by_name · search_by_producer · example_payloads

**Dependencies**: K-08, K-05, STORY-K12-001

---

### STORY-K12-012: Implement developer portal

**Feature**: K12-F04 · **Points**: 3 · **Sprint**: 14 · **Team**: Alpha

Developer portal: single destination for all platform documentation. Sections: Getting Started, Architecture Overview, API Reference (auto from K12-010), Event Catalog (K12-011), Plugin Development Guide, SDK Reference, Changelog. Interactive: API explorer with live sandbox environment. Search across all documentation. Feedback mechanism per page.

**ACs**:

1. Given developer portal, When loaded, Then all sections accessible with search working
2. Given API explorer, When request sent in sandbox, Then live response shown in browser
3. Given changelog, When new release, Then auto-updated from Git tags

**Tests**: portal_load · all_sections · search_across_docs · api_explorer_live · changelog_auto_update · feedback_mechanism · mobile_responsive · perf_under_2sec

**Dependencies**: STORY-K12-010, STORY-K12-011

---

## Feature K12-F05 — SDK Versioning (2 Stories)

---

### STORY-K12-013: Implement SDK package registry and versioning

**Feature**: K12-F05 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Internal package registry (Nexus/Artifactory or Gitea packages) for SDK artifacts. Versioning: semantic versioning, publish pre-release (alpha/beta/rc) and stable. SDK compatibility matrix: SDK version → platform version → services supported. Deprecation notices: when old SDK version support ending. Automated publish to npm (TypeScript), PyPI (Python), Maven (Java) for stable releases.

**ACs**:

1. Given SDK v2.1.0 published, When consumed by plugin, Then `npm install @siddhanta/sdk@2.1.0` succeeds
2. Given SDK v1.x deprecated, When plugin using v1.x starts, Then deprecation warning with migration guide URL
3. Given compatibility matrix, When queried, Then shows which SDK versions work with current platform

**Tests**: npm_publish · pypi_publish · maven_publish · semver · deprecation_warning · compatibility_matrix · prerelease · perf_publish_pipeline

**Dependencies**: STORY-K12-001

---

### STORY-K12-014: Implement SDK migration tooling

**Feature**: K12-F05 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

SDK migration assistant for major version upgrades. CLI: `siddhanta sdk migrate --from 1.x --to 2.x`. Code analysis: identify breaking API usage. Auto-fixer: apply safe migrations automatically, flag unsafe ones for manual review. Migration report: list of changes, risk level per change. Test re-run after migration.

**ACs**:

1. Given plugin using SDK 1.x API, When migration tool runs, Then breaking usages identified
2. Given safe migration (renamed method), When auto-fix applied, Then code updated, tests still pass
3. Given unsafe migration, When requiring manual review, Then flagged with explanation and code location

**Tests**: identify_breaking · autofix_rename · autofix_safe · flag_unsafe · migration_report · test_rerun · multi_file · perf_analysis_under_30sec

**Dependencies**: STORY-K12-013

---

## Feature K12-F06 — Observability SDK Module (1 Story)

---

### STORY-K12-015: Implement SDK observability module

**Feature**: K12-F06 · **Points**: 2 · **Sprint**: 13 · **Team**: Alpha

SDK module for K-06 observability integration. Auto-instrumentation: wrap HTTP handlers and event handlers with tracing (OpenTelemetry). Custom metrics API: `metrics.counter("orders.placed").increment()`. Custom spans: `tracer.start("operation")`. Structured logging with correlation_id propagation. Plugin observability: sandbox metrics auto-emitted from SDK.

**ACs**:

1. Given SDK auto-instrumentation applied, When HTTP request handled, Then trace started with correlation_id
2. Given custom metric defined, When incremented, Then appears in K-06 Prometheus within 30 seconds
3. Given log in plugin with SDK logger, When logged, Then correlation_id and trace_id included automatically

**Tests**: auto_trace_http · auto_trace_event · custom_counter · custom_histogram · correlation_propagation · k06_integration · structured_log · plugin_sandbox_metrics

**Dependencies**: K-06, K-12

---

# EPIC W-01: WORKFLOW ORCHESTRATION (16 Stories)

## Feature W01-F01 — Workflow Definition DSL (3 Stories)

---

### STORY-W01-001: Implement workflow definition and storage

**Feature**: W01-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Workflow orchestration core: define workflows in YAML/JSON DSL. Workflow: id, name, version, trigger (EVENT/SCHEDULE/MANUAL/API), steps[], error_handling, timeout. Step types: TASK (execute microservice call), DECISION (conditional branch), PARALLEL (concurrent steps), WAIT (delay or event), SUB_WORKFLOW. Definitions support references to registered step templates, human-task form schemas, and value catalogs so jurisdictions/operators can adjust step order, choices, and routing data without engine code changes. Stored in PostgreSQL with full version history. WorkflowDefined event.

**ACs**:

1. Given YAML workflow definition, When submitted, Then parsed, validated, stored with version 1
2. Given workflow with DECISION step, When parsed, Then branch conditions validated as valid expressions
3. Given workflow v2 submitted, When stored, Then v1 preserved, v2 becomes active
4. Given workflow references a registered step template and value catalog, When validated, Then pinned metadata versions stored with the definition

**Tests**: yaml_parse · json_parse · step_task · step_decision · step_parallel · step_wait · step_template_ref · value_catalog_ref · version_history · event_emission · perf_parse_under_100ms

**Dependencies**: K-05, K-02

---

### STORY-W01-002: Implement workflow trigger mechanisms

**Feature**: W01-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

Trigger types for workflows: EVENT (subscribe to K-05 topic, trigger on matching event), SCHEDULE (cron expression with K-15 BS calendar support), MANUAL (REST API trigger), API_WEBHOOK (external HTTP trigger with HMAC auth). Event trigger: filter expression (JSONPath or CEL) to conditionally trigger on event fields. Trigger registry: map trigger→workflow.

**ACs**:

1. Given event trigger "OrderFilled where amount > 100000", When matching event arrives, Then workflow triggered
2. Given cron trigger "0 6 \* \* 1-5 BS", When 06:00 BS weekday, Then workflow triggered
3. Given API trigger with HMAC signature, When valid request received, Then workflow triggered

**Tests**: event_trigger · event_filter_expression · cron_schedule · bs_calendar_cron · manual_trigger · api_webhook_hmac · trigger_registry · perf_trigger_under_10ms

**Dependencies**: STORY-W01-001, K-05, K-15

---

### STORY-W01-003: Implement CEL (Common Expression Language) evaluator

**Feature**: W01-F01 · **Points**: 2 · **Sprint**: 13 · **Team**: Alpha

CEL expression evaluation for workflow decision logic: condition expressions in DECISION steps, filter expressions in triggers, loop conditions. Type-safe expression evaluation with sandboxed execution. Type checking at definition time (fail fast). Built-in functions: string operations, date operations, math, collection functions. Custom functions registered by service.

**ACs**:

1. Given expression `event.amount > 100000 && event.currency == 'NPR'`, When evaluated, Then correct boolean result
2. Given expression with type error (string > number), When workflow defined, Then validation fails at definition
3. Given custom function `businessDays(date, n)`, When registered, Then usable in expressions

**Tests**: basic_conditions · string_ops · date_ops · math_ops · collection_ops · type_check_at_definition · custom_function · sandbox_safe · perf_eval_sub_1ms

**Dependencies**: STORY-W01-001

---

## Feature W01-F02 — Workflow Execution Engine (4 Stories)

---

### STORY-W01-004: Implement workflow execution runtime

**Feature**: W01-F02 · **Points**: 5 · **Sprint**: 13 · **Team**: Alpha

Workflow execution engine: stateful execution of workflow instances. WorkflowInstance: instance_id, workflow_id, version, status (PENDING → RUNNING → WAITING → COMPLETED → FAILED → CANCELLED), current_step, context (JSON payload), started_at, completed_at. Execution state persisted after every step (resumable). Concurrent instances supported. Instance event log.

**ACs**:

1. Given workflow triggered, When instance starts, Then status=RUNNING, context initialized with trigger data
2. Given TASK step executes service call, When complete, Then result stored in context, next step started
3. Given instance persisted after each step, When engine restarts mid-execution, Then resumes from last step

**Tests**: instance_created · step_task_execute · step_result_to_context · step_decision_branch · resumable_after_restart · concurrent_100_instances · cancel_instance · perf_step_under_100ms

**Dependencies**: STORY-W01-001, K-05

---

### STORY-W01-005: Implement parallel step execution

**Feature**: W01-F02 · **Points**: 3 · **Sprint**: 13 · **Team**: Alpha

PARALLEL step: execute multiple sub-steps concurrently. Join strategies: ALL (wait for all), FIRST (proceed when any one completes), N_OF_M (wait for N out of M). Timeout per parallel branch. If branch fails: configurable strategy (FAIL_ALL, IGNORE, FALLBACK). Context merge: results from parallel branches merged into main context.

**ACs**:

1. Given parallel step with 3 branches, When ALL join, Then waits for all 3 branches before continuing
2. Given FIRST join strategy, When first branch completes, Then continues, others cancelled
3. Given branch failure with IGNORE strategy, When one branch fails, Then others continue, failure logged

**Tests**: parallel_all_join · parallel_first_join · parallel_n_of_m · branch_timeout · branch_fail_all · branch_fail_ignore · context_merge · perf_parallel_10_branches

**Dependencies**: STORY-W01-004

---

### STORY-W01-006: Implement wait/correlation step

**Feature**: W01-F02 · **Points**: 3 · **Sprint**: 14 · **Team**: Alpha

WAIT step: pause workflow instance waiting for: external event (correlated by instance_id or custom key), timer (duration or specific date via K-15), manual signal via API. Correlation: `waitForEvent("PaymentConfirmed", correlate: "trade_id == context.trade_id")`. Timer: `waitFor(duration: "P2DT4H")` — wait 2 days 4 hours. Manual: `waitForSignal(signal: "APPROVED")`. Expiry: if wait times out, configurable action.

**ACs**:

1. Given WAIT step for PaymentConfirmed correlated by trade_id, When matching event arrives, Then instance resumes
2. Given timer wait of 2 hours, When elapsed, Then instance resumes automatically
3. Given wait timeout (no correlation in 24h), When expires, Then TIMEOUT branch executed

**Tests**: wait_event_correlation · wait_timer · wait_manual_signal · wait_timeout · concurrent_waits · correlation_key_multiple · bs_calendar_wait · perf_correlation_sub_10ms

**Dependencies**: STORY-W01-004, K-15, K-05

---

### STORY-W01-007: Implement workflow error handling and retry

**Feature**: W01-F02 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Error handling in workflows: step-level retry (configurable attempts, backoff), CATCH blocks (handle specific error types), FINALLY steps (always execute), compensation (undo completed steps on failure, saga pattern). Error types: TASK_ERROR, TIMEOUT, TRANSIENT, PERMANENT. Permanent errors skip retry, go to CATCH. Failed instance stored for manual intervention.

**ACs**:

1. Given TASK step fails with TRANSIENT error, When retried 3 times, Then succeeds on 3rd attempt
2. Given PERMANENT error, When caught, Then CATCH block executes, no retry
3. Given compensation defined, When workflow fails mid-saga, Then completed steps reversed in order

**Tests**: retry_transient · no_retry_permanent · catch_block · finally_step · compensation_saga · retry_backoff · max_retries_failed_instance · concurrent_retry_isolation

**Dependencies**: STORY-W01-004

---

## Feature W01-F03 — Workflow Monitoring & History (2 Stories)

---

### STORY-W01-008: Implement workflow instance dashboard

**Feature**: W01-F03 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Workflow monitoring UI: list instances with filters (workflow, status, date). Instance detail: step-by-step execution timeline with duration, input/output per step. RUNNING instances show real-time current step. Visual workflow diagram with step highlighting. Manual intervention: send signal, cancel instance, retry failed step.

**ACs**:

1. Given running instance, When viewed, Then current step highlighted in workflow diagram real-time
2. Given failed instance, When viewed, Then failed step shown with error + retry option
3. Given manual signal sent from UI, When submitted, Then WAIT step resumes

**Tests**: list_instances · filter_by_status · instance_timeline · real_time_highlight · failed_step_retry · manual_signal · cancel_instance · perf_list_10k_instances

**Dependencies**: STORY-W01-004, K-13

---

### STORY-W01-009: Implement workflow metrics and SLA tracking

**Feature**: W01-F03 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Workflow metrics: completion rate, average duration per workflow, step latency distribution, error rate per step, SLA compliance (workflows completed within configured time limit). Trend charts: completions/failures per day. Bottleneck analysis: slowest steps. Alerts: if completion rate drops or average duration exceeds threshold.

**ACs**:

1. Given 100 completed instances, When metrics queried, Then avg_duration, p95 shown per workflow
2. Given SLA = 4 hours, When instance takes 5 hours, Then SLA breach logged, alert sent
3. Given bottleneck view, When queried, Then top 5 slowest steps identified with avg latency

**Tests**: completion_rate · avg_duration · sla_breach · bottleneck_analysis · trend_charts · error_rate_per_step · alert_on_threshold · perf_metrics_under_3sec

**Dependencies**: STORY-W01-008, K-06

---

## Feature W01-F04 — Built-in Financial Workflows (4 Stories)

---

### STORY-W01-010: Implement reconciliation orchestration workflow

**Feature**: W01-F04 · **Points**: 3 · **Sprint**: 14 · **Team**: Alpha

Pre-built workflow: Daily Client Money Reconciliation (D-13). Steps: 1) Trigger at 06:00, 2) Extract internal balances (parallel), 3) Fetch external statements (parallel), 4) Run matching engine, 5) DECISION — breaks found?, 6a) No breaks: generate report + submit, 6b) Breaks: classify + route for resolution, 7) Wait for resolution (signal), 8) Finalize. Workflow defined in YAML.

**ACs**:

1. Given 06:00 trigger, When workflow runs, Then all 8 steps execute in correct sequence
2. Given breaks found, When decision branch taken, Then routed to operations for resolution
3. Given manual resolution signal, When received, Then workflow resumes and finalizes

**Tests**: full_workflow_run · parallel_extract · break_branch · no_break_branch · resolution_signal · timeout_escalation · idempotent_retrigger · audit_trail

**Dependencies**: STORY-W01-006, D-13

---

### STORY-W01-011: Implement trade settlement workflow

**Feature**: W01-F04 · **Points**: 3 · **Sprint**: 14 · **Team**: Alpha

Pre-built workflow: Settlement lifecycle (D-09). Steps: 1) Trigger on TradeConfirmed, 2) Netting eligibility check, 3) PARALLEL — generate instruction + notify counterparty, 4) Wait for matching (T-1 day), 5) DECISION — matched?, 6) DVP execution, 7) Ledger posting, 8) Settlement confirmation. Compensation on step 6/7 failure.

**ACs**:

1. Given TradeConfirmed event, When workflow triggered, Then proceeds through settlement steps
2. Given matching step timeout, When 24h elapsed, Then escalation + manual matching requested
3. Given DVP failure, When step 6 fails, Then compensation reverses instruction, settlement marked FAILED

**Tests**: full_settlement_workflow · parallel_instruction_notify · matching_wait · matching_timeout · dvp_success · dvp_failure_compensation · ledger_posting_step · audit_trail

**Dependencies**: STORY-W01-006, D-09

---

### STORY-W01-012: Implement corporate action processing workflow

**Feature**: W01-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Pre-built workflow: Corporate Action (D-12). Steps: 1) Trigger on CA Announced, 2) Capture holder snapshot at record date (WAIT), 3) Calculate entitlements, 4) Tax withholding, 5) DECISION — election required?, 5a) Yes: open election portal, wait for elections/deadline, 5b) No: proceed, 6) Ledger posting, 7) Confirmation + notification.

**ACs**:

1. Given CA announced, When workflow starts, Then pauses at step 2 until record_date
2. Given election step, When deadline passes, Then auto-lapse for non-respondents, proceeds
3. Given ledger posting failure, When compensation, Then entitlements reversed, CA marked FAILED

**Tests**: ca_workflow_full · wait_record_date · election_workflow · election_timeout · ledger_posting · compensation · bonus_path · dividend_path

**Dependencies**: STORY-W01-006, D-12

---

### STORY-W01-013: Implement regulatory report submission workflow

**Feature**: W01-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Pre-built workflow: Regulatory report submission (D-10). Steps: 1) Trigger (schedule or event), 2) Data extraction, 3) Report rendering, 4) Maker-checker approval (WAIT for signal), 5) Submission via adapter, 6) WAIT for ACK/NACK, 7a) ACK: archive + notify, 7b) NACK: parse errors, resubmission loop (max 3). Deadline SLA enforcement.

**ACs**:

1. Given scheduled trigger, When workflow runs, Then data extracted, rendered, submitted for approval
2. Given NACK received, When resubmission loop, Then max 3 attempts before escalation
3. Given deadline approaching, When SLA 24h before deadline, Then expedited approval notification

**Tests**: full_submission_workflow · maker_checker_approval · ack_path · nack_resubmission_loop · nack_max_attempts · deadline_sla · archive_on_ack · audit_trail

**Dependencies**: STORY-W01-006, D-10

---

## Feature W01-F05 — Sub-Workflow & Reuse (1 Story)

---

### STORY-W01-014: Implement sub-workflow composition

**Feature**: W01-F05 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

SUB_WORKFLOW step: invoke another workflow as a step from parent workflow. Context passing: parent passes input, child returns output to parent. Reuse common workflows (e.g., maker-checker approval sub-workflow used across many workflows). Synchronous (wait for child) and asynchronous (fire and forget) modes. Error propagation from child to parent configurable.

**ACs**:

1. Given parent workflow with sub_workflow step, When executed, Then child workflow triggered with parent context
2. Given synchronous mode, When child completes, Then parent resumes with child's output
3. Given reusable maker-checker sub-workflow, When called from 5 parent workflows, Then single definition reused

**Tests**: sub_workflow_synchronous · sub_workflow_async · context_passing · output_return · error_propagation · reuse_across_parents · nested_depth_3 · infinite_loop_detection

**Dependencies**: STORY-W01-004

---

## Feature W01-F06 — Workflow Versioning (2 Stories)

---

### STORY-W01-015: Implement workflow version management

**Feature**: W01-F06 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Workflow versioning: multiple versions coexist. Active version for new instances, older versions for in-flight instances (they complete on the version they started). Version migration: migrate running instances to new version at safe migration point (configurable). Version deprecation: after grace period, stop accepting new triggers for old version.

**ACs**:

1. Given workflow v1 has 10 running instances, When v2 deployed, Then existing instances stay on v1
2. Given safe migration point "AFTER_STEP_3", When configured, Then running instances migrate to v2 after step 3
3. Given v1 deprecated (30 days), When new trigger arrives, Then v2 used, v1 triggers rejected

**Tests**: co_existing_versions · in_flight_on_v1 · migration_at_safe_point · deprecation_rejection · new_instances_on_v2 · rollback_version · history_preserved

**Dependencies**: STORY-W01-001

---

### STORY-W01-016: Implement workflow testing environment

**Feature**: W01-F06 · **Points**: 2 · **Sprint**: 14 · **Team**: Alpha

Test mode for workflows: dry-run where TASK steps return mock responses instead of real service calls. Step mock configuration: `mockStep("settlement.dvp", { status: "SETTLED" })`. Assertion step: verify context at specific points. Test scenarios: happy path, error path, timeout scenarios. Test report showing step-by-step results.

**ACs**:

1. Given workflow in test mode with mocked steps, When run, Then uses mocks, no real service calls
2. Given assertion step, When context checked, Then test fails if assertion fails
3. Given error path test, When injected step failure, Then error handling and compensation verified

**Tests**: dry_run_mode · step_mocking · assertion_step · happy_path_test · error_path_test · timeout_scenario · compensation_verification · test_report

**Dependencies**: STORY-W01-004, STORY-K12-004

---

# EPIC W-02: CLIENT ONBOARDING WORKFLOW (13 Stories)

## Feature W02-F01 — KYC Document Collection (3 Stories)

---

### STORY-W02-001: Implement KYC workflow definition and trigger

**Feature**: W02-F01 · **Points**: 2 · **Sprint**: 13 · **Team**: Beta

Client onboarding KYC workflow: triggered by ClientOnboardingRequested event. Steps: document collection → document verification → identity verification → risk assessment → compliance review → account creation → welcome notification. Workflow handles individual and institutional clients with different document sets. Dual-calendar date tracking throughout.

**ACs**:

1. Given ClientOnboardingRequested event, When workflow triggered, Then KYC instance created per client type (individual/institutional)
2. Given individual client, When workflow starts, Then required documents list: NatID/Passport, address proof, PAN
3. Given institutional client, When workflow starts, Then required documents: registration cert, owner list, authorized signatories

**Tests**: trigger_individual · trigger_institutional · individual_doc_list · institutional_doc_list · event_emission · dual_calendar · idempotent_trigger

**Dependencies**: W-01, K-05, K-15

---

### STORY-W02-002: Implement document upload portal

**Feature**: W02-F01 · **Points**: 3 · **Sprint**: 13 · **Team**: Epsilon

Client-facing document upload: per required document, upload button with file type validation (PDF/JPG/PNG, max 5MB). Drag-and-drop. Upload progress. Secure storage in S3/Ceph with client-specific prefix. Document status: PENDING_UPLOAD → UPLOADED → UNDER_REVIEW → APPROVED/REJECTED. Portal shows completion percentage. Email notification on document received.

**ACs**:

1. Given required document list, When client uploads NatID, Then stored securely, status → UPLOADED
2. Given file > 5MB, When upload attempted, Then rejected with size error before upload
3. Given document REJECTED with reason, When client views portal, Then reason shown with re-upload option

**Tests**: upload_valid_pdf · upload_jpg · file_too_large_rejected · wrong_type_rejected · secure_storage · status_update · completion_percentage · rejection_re_upload

**Dependencies**: STORY-W02-001, K-01

---

### STORY-W02-003: Implement document request and reminder engine

**Feature**: W02-F01 · **Points**: 2 · **Sprint**: 13 · **Team**: Beta

Automated document request: on workflow start, send document request email/SMS with secure upload link. Reminders: if documents not uploaded after 3 days, send reminder. Escalation: after 7 days without upload, KYC coordinator notified. Secure upload link: time-limited (7-day expiry), one per document. Link regeneration on expiry.

**ACs**:

1. Given KYC started, When document request sent, Then client receives email with upload links
2. Given 3 days without uploads, When reminder triggered, Then follow-up email/SMS sent
3. Given upload link expired, When client tries, Then error + option to request new link

**Tests**: initial_request_sent · reminder_3_days · escalation_7_days · secure_link_expiry · link_regeneration · sms_channel · email_channel · multi_document_links

**Dependencies**: STORY-W02-001

---

## Feature W02-F02 — Identity Verification (3 Stories)

---

### STORY-W02-004: Implement document AI verification

**Feature**: W02-F02 · **Points**: 5 · **Sprint**: 13 · **Team**: Gamma

AI-powered document verification: extract data from uploaded ID documents (OCR), verify document authenticity (template matching, security feature check), cross-reference extracted data against client-provided data. Fields extracted: name, DOB, ID number, expiry. Confidence score: HIGH/MEDIUM/LOW. Low confidence → manual review queue. T3 plugin for jurisdiction-specific ID types.

**ACs**:

1. Given passport uploaded, When AI verified, Then name, DOB, passport_number extracted with confidence score
2. Given confidence = LOW (<0.7), When flagged, Then routed to manual review queue
3. Given extracted DOB mismatches client-provided, When compared, Then DocumentMismatch alert

**Tests**: ocr_passport · ocr_national_id · confidence_high · confidence_low_manual · data_mismatch · expiry_check · t3_jurisdiction_plugin · perf_ocr_under_5sec

**Dependencies**: K-09, K-04, STORY-W02-002

---

### STORY-W02-005: Implement sanctions and PEP screening at onboarding

**Feature**: W02-F02 · **Points**: 3 · **Sprint**: 14 · **Team**: Gamma

Screen new clients against sanctions lists and PEP (Politically Exposed Persons) databases during onboarding. Integration with D-14 Sanctions Engine. Fuzzy name matching (same as D-14). If hit: BLOCK workflow, notify compliance, require manual review and override with full documentation. Clear: proceed to next step. Continuous monitoring enrollment post-approval.

**ACs**:

1. Given client name matches sanctions list, When screened, Then KYC workflow → BLOCKED, compliance notified
2. Given PEP match, When detected, Then enhanced due diligence (EDD) branch triggered
3. Given clear screening, When passed, Then proceeds to risk assessment step

**Tests**: sanctions_hit_block · pep_match_edd · clear_proceeds · fuzzy_match · compliance_notification · manual_override · continuous_monitoring_enroll

**Dependencies**: D-14, STORY-W02-001

---

### STORY-W02-006: Implement manual KYC review UI

**Feature**: W02-F02 · **Points**: 3 · **Sprint**: 14 · **Team**: Epsilon

KYC review portal for compliance officers: list pending reviews (from document verification failures, sanctions hits, PEP matches). Review detail: client info, uploaded documents (with preview), AI verification results, sanctions screening result. Actions: APPROVE ALL, REJECT DOCUMENT (with reason, request reupload), APPROVE WITH EDD. SLA: reviews completed within 2 business days.

**ACs**:

1. Given pending review, When compliance officer opens, Then all documents previewed inline
2. Given document rejected with reason, When submitted, Then client notified with reason, re-upload requested
3. Given SLA check, When review pending > 2 days, Then escalation to senior compliance

**Tests**: review_list · document_preview · approve_all · reject_document · approve_edd · sla_2_days · escalation · audit_trail · perf_load_under_3sec

**Dependencies**: STORY-W02-004, K-01, K-07

---

## Feature W02-F03 — Risk Assessment & Scoring (2 Stories)

---

### STORY-W02-007: Implement AML risk scoring engine

**Feature**: W02-F03 · **Points**: 3 · **Sprint**: 14 · **Team**: Gamma

AML risk scoring: calculate client risk score during onboarding. Risk factors: nationality (high-risk countries), PEP status, occupation, expected transaction volume, account type, source of funds. Score: 0-100 (LOW 0-39, MEDIUM 40-69, HIGH 70-100). Rules via K-03 T2 (jurisdiction-specific). Score determines: monitoring frequency, EDD requirement, periodic review schedule. RiskScoreCalculated event.

**ACs**:

1. Given client from FATF high-risk jurisdiction, When scored, Then higher base score applied
2. Given PEP client, When scored, Then PEP factor adds significant score increase
3. Given score = HIGH, When determined, Then EDD step required, enhanced monitoring enrolled

**Tests**: score_low · score_medium · score_high · pep_factor · country_risk · occupation_factor · edd_triggered · monitoring_enroll · rule_hot_reload

**Dependencies**: K-03, D-14, K-09

---

### STORY-W02-008: Implement CDD and EDD document collection

**Feature**: W02-F03 · **Points**: 2 · **Sprint**: 14 · **Team**: Beta

Customer Due Diligence (CDD) standard and Enhanced Due Diligence (EDD) for high-risk clients. EDD requires additional documents: source of funds proof, business relationship explanation, beneficial ownership declaration (>25% ownership). Document request workflow same as standard but with EDD document set. EDD reviewed by senior compliance (not standard reviewer).

**ACs**:

1. Given HIGH risk client, When EDD triggered, Then EDD document list requested in addition to standard KYC
2. Given beneficial ownership form, When submitted, Then all owners >25% listed with ID docs
3. Given EDD reviewer, When assigned, Then senior compliance officer notified

**Tests**: edd_triggered_high_risk · edd_document_list · beneficial_ownership · senior_reviewer · standard_cdd · edd_upload_portal · audit_trail

**Dependencies**: STORY-W02-007, STORY-W02-003

---

## Feature W02-F04 — Account Setup (2 Stories)

---

### STORY-W02-009: Implement account creation and provisioning

**Feature**: W02-F04 · **Points**: 3 · **Sprint**: 14 · **Team**: Beta

After KYC approval, auto-provision client account. Steps: create client record in IAM (K-01), create cash accounts (K-16 ledger account per currency), create securities account (linked to CSD), set trading limits (K-03 rules), enroll in monitoring (D-07, D-14). Account_id assigned, client portal credentials sent. AccountProvisioned event. Saga for atomicity across services.

**ACs**:

1. Given KYC approved, When provisioning runs, Then IAM user, cash accounts, securities account all created
2. Given any provisioning step fails, When saga compensates, Then all partial steps reversed
3. Given AccountProvisioned event, When emitted, Then welcome email triggered with portal login

**Tests**: full_provisioning_saga · iam_user_created · cash_account_created · securities_account · trading_limits_set · compensation_on_failure · event_emission · perf_provisioning_under_30sec

**Dependencies**: K-01, K-16, D-07, D-14, K-17

---

### STORY-W02-010: Implement client onboarding notification suite

**Feature**: W02-F04 · **Points**: 2 · **Sprint**: 14 · **Team**: Beta

Notification suite for onboarding journey: 1) Onboarding started (with document checklist), 2) Document received confirmation, 3) Review started, 4) Documents approved / rejected with reason, 5) Account created (with login instructions), 6) First login prompt. Email (HTML templates), SMS (short text), in-app notification (portal). Notification preference per client (K-02). Dual-calendar dates in notifications.

**ACs**:

1. Given account created, When welcome email sent, Then includes portal login URL, temporary password instructions
2. Given document rejected, When notified, Then email/SMS with specific rejection reason and re-upload link
3. Given dual-calendar, When date in notification, Then shows both Gregorian and BS date

**Tests**: welcome_email · rejection_notification · upload_confirmation · channel_preference · dual_calendar_dates · html_template · sms_short_text · unsubscribe_handling

**Dependencies**: STORY-W02-009, K-02

---

## Feature W02-F05 — Onboarding Dashboard (1 Story)

---

### STORY-W02-011: Implement onboarding pipeline dashboard

**Feature**: W02-F05 · **Points**: 2 · **Sprint**: 14 · **Team**: Epsilon

Onboarding dashboard for compliance/operations: pipeline view (kanban-style) showing clients in each stage (Document Collection, Under Review, EDD, Account Setup, Completed). Stats: average time per stage, completion rate, rejection rate. SLA compliance: % completed within target days. Filter by: risk tier, client type, date. Drilldown to individual KYC instance.

**ACs**:

1. Given dashboard, When loaded, Then kanban shows count per stage with real-time updates
2. Given SLA target 5 business days, When compliance checked, Then % within SLA shown
3. Given filter by HIGH risk, When applied, Then only high-risk clients shown across stages

**Tests**: kanban_stages · real_time_counts · sla_compliance · filter_risk_tier · filter_client_type · drilldown · avg_time_per_stage · export_report

**Dependencies**: W-01, K-13

---

## Feature W02-F06 — Periodic Review (2 Stories)

---

### STORY-W02-012: Implement KYC periodic review scheduler

**Feature**: W02-F06 · **Points**: 3 · **Sprint**: 14 · **Team**: Beta

Schedule periodic KYC reviews based on risk tier: LOW = every 3 years, MEDIUM = every 2 years, HIGH = every 1 year. Review reminder at T-60 days (email), T-30 days (email + SMS), T-7 days (urgent). On review due date, trigger full KYC re-verification workflow. BS calendar for date calculations. Review status tracked per client.

**ACs**:

1. Given HIGH risk client, When 1 year since last review, Then periodic review workflow triggered
2. Given T-60 reminder, When triggered, Then client receives advance notice email
3. Given BS review date, When calculated, Then uses K-15 for BS anniversary

**Tests**: schedule_low_3yr · schedule_medium_2yr · schedule_high_1yr · reminder_t60 · reminder_t30 · reminder_t7 · bs_anniversary · workflow_trigger

**Dependencies**: STORY-W02-009, W-01, K-15

---

### STORY-W02-013: Implement trigger-based KYC refresh

**Feature**: W02-F06 · **Points**: 2 · **Sprint**: 14 · **Team**: Beta

Trigger KYC refresh on significant events: sanctions list hit post-onboarding (D-14 continuous monitoring), client risk score change, suspicious activity flag (D-08), client-reported personal data change. Refresh workflow: lightweight re-verification (not full onboarding). Updates client record with new KYC timestamp. Audit trail of trigger reason.

**ACs**:

1. Given post-onboarding sanctions hit, When detected, Then KYC refresh workflow triggered immediately
2. Given client updates address in portal, When changed, When address verification step triggered
3. Given refresh complete, When passed, Then KYC timestamp updated, no interruption to trading

**Tests**: sanctions_hit_refresh · score_change_refresh · suspicious_activity_refresh · address_change_refresh · lightweight_refresh · trading_not_interrupted · audit_reason · perf_under_5min

**Dependencies**: STORY-W02-012, D-14, D-08

---

# MILESTONE 3B SUMMARY

| Epic                        | Feature Count | Story Count | Total SP |
| --------------------------- | ------------- | ----------- | -------- |
| K-13 Admin Portal           | 6             | 14          | 36       |
| K-12 Platform SDK           | 6             | 15          | 37       |
| W-01 Workflow Orchestration | 6             | 16          | 43       |
| W-02 Client Onboarding      | 6             | 13          | 38       |
| **TOTAL**                   | **24**        | **58**      | **154**  |

**Sprint 13**: K-13 (001-007), K-12 (001-007,015), W-01 (001-005), W-02 (001-003) (~26 stories)
**Sprint 14**: K-13 (008-014), K-12 (008-014), W-01 (006-016), W-02 (004-013) (~32 stories)
