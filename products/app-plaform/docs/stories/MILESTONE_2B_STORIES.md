# MILESTONE 2B — DOMAIN POST-TRADE & FINANCE

## Sprints 8–10 | 104 Stories | D-09, D-13, D-03, D-05, D-08, D-10, D-12

> **Story Template**: Each story includes ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, and dependencies.

---

# EPIC D-09: POST-TRADE PROCESSING (18 Stories)

## Feature D09-F01 — Trade Confirmation Generation (2 Stories)

---

### STORY-D09-001: Implement trade confirmation document generator

**Feature**: D09-F01 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Build trade confirmation service that generates confirmation documents after order reaches FILLED state. Listens to OrderFilled events from K-05. Generates PDF and JSON confirmation with: trade_id, instrument, quantity, price, fees, settlement_date (K-15 T+n calculation), counterparty, broker details. Templates stored in S3/MinIO. Dual-calendar timestamps throughout.

**ACs**:

1. Given OrderFilled event, When confirmation generated, Then PDF + JSON created within 2 seconds
2. Given confirmation, When settlement_date calculated, Then uses K-15 T+n with holiday awareness
3. Given confirmation generated, When TradeConfirmed event emitted, Then downstream services notified

**Tests**: generate_pdf_valid · generate_json_valid · settlement_tPlusN_holidays · dual_calendar_timestamps · event_emission · template_not_found_error · perf_2sec_generation

**Dependencies**: D-01, D-02, K-05, K-15, K-07

---

### STORY-D09-002: Implement confirmation delivery and acknowledgment

**Feature**: D09-F01 · **Points**: 2 · **Sprint**: 8 · **Team**: Beta

Deliver trade confirmations to clients via configured channels: email (SMTP), portal notification, API push. Track delivery status: PENDING → SENT → DELIVERED → ACKNOWLEDGED. Client acknowledgment via portal click or API call. Unacknowledged confirmations trigger escalation after configurable period (K-02).

**ACs**:

1. Given confirmation generated, When delivered via email, Then delivery_status = SENT, email receipt tracked
2. Given client clicks acknowledge in portal, When acknowledged, Then status = ACKNOWLEDGED, audit logged
3. Given unacknowledged after 24h, When escalation triggers, Then notification to operations team

**Tests**: deliver_email_sent · deliver_portal_notification · acknowledge_updates_status · escalation_24h_trigger · multi_channel_delivery · audit_logged

**Dependencies**: STORY-D09-001, K-05, K-07

---

## Feature D09-F02 — Netting Engine (3 Stories)

---

### STORY-D09-003: Implement bilateral netting calculation engine

**Feature**: D09-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Build netting engine that consolidates multiple trades between two counterparties into net obligations. For each counterparty pair: net_quantity = Σ(buy_qty) - Σ(sell_qty) per instrument per settlement_date. Net_cash = Σ(sell_value) - Σ(buy_value) + fees. Generates NettingSet with net positions and net cash per currency. Runs at configurable cutoff time (EOD default).

**ACs**:

1. Given 3 buys (100+200+150) and 2 sells (250+100) for same instrument/counterparty, When netted, Then net_buy = 100,net_cash calculated correctly
2. Given trades in multiple currencies, When netted, Then separate net per currency
3. Given netting cutoff 16:00, When triggered, Then all eligible trades included, NettingCompleted event emitted

**Tests**: net_bilateral_simple · net_bilateral_multicurrency · net_zero_offset · cutoff_time_respected · event_nettingcompleted · net_no_eligible_trades · perf_10k_trades_under5sec

**Dependencies**: D-01, D-02, K-05, K-15

---

### STORY-D09-004: Implement multilateral netting with CCP support

**Feature**: D09-F02 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

Extend netting engine for multilateral netting: all participants net through a central counterparty (CCP). Each participant has single net obligation vs CCP. Algorithm: for each instrument, aggregate all buys and sells across participants, compute net per participant vs CCP. Generates multilateral NettingSet. T3 plugin interface for CCP-specific netting rules.

**ACs**:

1. Given 5 participants with cross-trades, When multilateral netting runs, Then each participant has single net obligation vs CCP
2. Given CCP-specific rule (T3 plugin), When applied, Then custom netting logic executed in sandbox
3. Given multilateral netting, When compared to bilateral, Then total settlement obligations reduced

**Tests**: multilateral_5_participants · ccp_plugin_execution · net_reduction_vs_bilateral · settlement_obligations_balanced · zero_sum_verification · perf_100_participants

**Dependencies**: STORY-D09-003, K-04

---

### STORY-D09-005: Implement netting report and reconciliation

**Feature**: D09-F02 · **Points**: 3 · **Sprint**: 8 · **Team**: Beta

Generate netting reports after each netting run. Report includes: gross vs net obligations, netting efficiency ratio, per-instrument breakdown, per-counterparty summary. Report reconciles against individual trades to ensure zero-sum. Export formats: PDF, CSV, JSON. Reconciliation breaks flagged as NettingBreak events.

**ACs**:

1. Given completed netting run, When report generated, Then gross_obligations = sum of individual trades
2. Given net obligations, When zero-sum checked, Then total_net_buy == total_net_sell per instrument
3. Given reconciliation break, When detected, Then NettingBreak event emitted, break flagged for review

**Tests**: report_gross_vs_net · zero_sum_check · reconciliation_pass · reconciliation_break_flagged · export_pdf · export_csv · netting_efficiency_ratio

**Dependencies**: STORY-D09-003, K-05, K-07

---

## Feature D09-F03 — Settlement Lifecycle (4 Stories)

---

### STORY-D09-006: Implement settlement instruction generation

**Feature**: D09-F03 · **Points**: 5 · **Sprint**: 8 · **Team**: Beta

After netting, generate settlement instructions for each net obligation. SettlementInstruction: instruction_id, netting_set_id, instrument, quantity, direction (DELIVER/RECEIVE), counterparty, settlement_date, settlement_account, currency, amount. Status: GENERATED → MATCHED → AFFIRMED → SETTLED → FAILED. Uses K-15 for settlement date T+n calculation with holiday awareness.

**ACs**:

1. Given netting set, When instructions generated, Then one instruction per net obligation with correct settlement_date
2. Given settlement_date falls on holiday, When calculated, Then rolls to next business day per K-15
3. Given instruction generated, When SettlementInstructionCreated event emitted, Then CSD adapter notified

**Tests**: generate_from_netting · settlement_date_holiday_roll · instruction_status_generated · event_emission · one_per_obligation · dual_calendar · perf_1k_instructions_under2sec

**Dependencies**: STORY-D09-003, K-05, K-15

---

### STORY-D09-007: Implement settlement matching and affirmation

**Feature**: D09-F03 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Settlement matching: compare our instruction with counterparty's instruction. Match on: instrument, quantity, settlement_date, direction (opposite), counterparty. Matching states: UNMATCHED → ALLEGED → MATCHED → AFFIRMED. Auto-match when both sides submit matching instructions. Discrepancy detection with field-level diff report. Manual affirmation workflow for unmatched items.

**ACs**:

1. Given both sides submit matching instructions, When auto-match runs, Then status → MATCHED within 1 second
2. Given discrepancy in quantity, When compared, Then ALLEGED status with diff report showing quantity mismatch
3. Given manual affirmation by authorized user, When affirmed, Then status → AFFIRMED, maker-checker logged

**Tests**: auto_match_both_sides · discrepancy_quantity · discrepancy_settlement_date · manual_affirmation · maker_checker_required · timeout_unmatched_escalation · perf_auto_match_1sec

**Dependencies**: STORY-D09-006, K-01, K-07

---

### STORY-D09-008: Implement DVP settlement execution

**Feature**: D09-F03 · **Points**: 5 · **Sprint**: 9 · **Team**: Beta

Implement Delivery vs Payment (DVP) settlement: securities and cash move simultaneously to prevent settlement risk. Saga orchestration via K-17: Step 1 — reserve securities in deliverer's account, Step 2 — reserve cash in receiver's account, Step 3 — execute atomic transfer. If any step fails, compensation reverses completed steps. Ledger posting via K-16 for both securities and cash legs.

**ACs**:

1. Given affirmed instruction, When DVP executes, Then securities and cash transferred atomically via saga
2. Given cash reservation fails, When Step 2 fails, Then Step 1 (securities) compensated, no partial settlement
3. Given successful DVP, When ledger posted, Then debit/credit entries balanced per K-16

**Tests**: dvp_success_atomic · dvp_cash_fail_compensation · dvp_securities_fail_compensation · ledger_balanced · saga_timeout_compensation · concurrent_dvp_isolation · perf_dvp_under500ms

**Dependencies**: STORY-D09-007, K-16, K-17, K-05

---

### STORY-D09-009: Implement settlement calendar and scheduling

**Feature**: D09-F03 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

Settlement scheduler: run settlement cycles at configured times per market and instrument type. T+0 for government bonds, T+2 for equities (configurable via K-02). Calendar integration via K-15 for BS/Gregorian settlement dates. Settlement window management: pre-settlement (T-1 matching), settlement day processing, post-settlement (confirmation). Automatic retry for pending settlements at next cycle.

**ACs**:

1. Given equity trade, When settlement scheduled, Then settlement_date = trade_date + 2 business days
2. Given settlement window opens, When cycle runs, Then all eligible affirmed instructions processed
3. Given failed settlement, When next cycle runs, Then retried automatically

**Tests**: tPlus2_equities · tPlus0_govBonds · holiday_skip · settlement_window · auto_retry_failed · cycle_timing · dual_calendar_dates

**Dependencies**: STORY-D09-006, K-02, K-15

---

## Feature D09-F04 — CSD/Depository Adapter (3 Stories)

---

### STORY-D09-010: Implement T3 CSD adapter plugin interface

**Feature**: D09-F04 · **Points**: 5 · **Sprint**: 9 · **Team**: Beta

Define T3 plugin interface for CSD/depository connectivity: ICsdAdapter with methods submitInstruction(), getStatus(), confirmSettlement(), cancelInstruction(). Plugin runs in T3 sandbox with network access to CSD only. Adapter registry for multiple CSDs (e.g., CDSC for Nepal). Message format translation between internal and CSD-specific formats. Connection management with heartbeat monitoring.

**ACs**:

1. Given T3 CSD adapter registered, When settlement instruction submitted, Then translated to CSD format and sent
2. Given CSD returns settlement confirmation, When received, Then internal status updated to SETTLED
3. Given CSD connection lost, When heartbeat fails, Then circuit breaker opens, alert raised

**Tests**: adapter_submit_instruction · adapter_receive_confirmation · adapter_cancel · connection_heartbeat · circuit_breaker_on_failure · message_translation · t3_sandbox_isolation

**Dependencies**: K-04, K-18, STORY-D09-006

---

### STORY-D09-011: Implement CDSC adapter (Nepal depository)

**Feature**: D09-F04 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

Implement concrete T3 plugin for CDSC (Central Depository System and Clearing Ltd, Nepal). Map internal settlement instruction fields to CDSC message format. Handle CDSC-specific flows: demat transfer, physical share handling, pledge/unpledge. Connection via CDSC API (REST/SOAP depending on version). Credential management via K-14.

**ACs**:

1. Given internal settlement instruction, When sent to CDSC, Then correctly mapped to CDSC format
2. Given CDSC settlement response, When received, Then mapped back to internal SettlementConfirmed event
3. Given CDSC credentials rotated, When K-14 rotates, Then adapter uses new credentials without restart

**Tests**: cdsc_format_mapping · cdsc_response_mapping · credential_rotation · demat_transfer · pledge_handling · connection_retry · error_code_mapping

**Dependencies**: STORY-D09-010, K-14

---

### STORY-D09-012: Implement CSD reconciliation engine

**Feature**: D09-F04 · **Points**: 3 · **Sprint**: 10 · **Team**: Beta

Daily reconciliation between internal settlement records and CSD position statements. Fetch CSD position report via adapter. Compare: internal position vs CSD position per instrument per account. Break detection: quantity mismatch, missing positions, extra positions. Generate reconciliation report with breaks classified by severity. CsdReconciliationBreak events for unresolved breaks.

**ACs**:

1. Given internal position matches CSD position, When reconciled, Then status = MATCHED, no breaks
2. Given internal shows 1000 shares, CSD shows 995, When reconciled, Then break flagged: qty_mismatch, severity=HIGH
3. Given reconciliation complete, When report generated, Then sent to operations with break summary

**Tests**: recon_match · recon_qty_mismatch · recon_missing_position · recon_extra_position · break_severity · report_generation · auto_schedule_daily

**Dependencies**: STORY-D09-010, K-05, K-07

---

## Feature D09-F05 — Ledger Posting (2 Stories)

---

### STORY-D09-013: Implement post-trade ledger posting engine

**Feature**: D09-F05 · **Points**: 3 · **Sprint**: 9 · **Team**: Beta

On successful settlement, post ledger entries via K-16. Securities leg: debit buyer's securities account, credit seller's securities account. Cash leg: debit buyer's cash account, credit seller's cash account. Fees: debit client's account, credit fee income account. All entries in same journal with zero-sum balance. Event-driven: triggered by SettlementCompleted event.

**ACs**:

1. Given settlement completed, When ledger posted, Then securities + cash + fees balanced in single journal
2. Given multi-currency settlement, When posted, Then separate balanced journals per currency
3. Given ledger posting fails, When error occurs, Then compensation saga triggered, settlement status → POSTING_FAILED

**Tests**: post_securities_leg · post_cash_leg · post_fees · balanced_journal · multi_currency · posting_failure_compensation · idempotent_repost · perf_posting_under100ms

**Dependencies**: K-16, K-17, STORY-D09-008

---

### STORY-D09-014: Implement position projection from settlements

**Feature**: D09-F05 · **Points**: 2 · **Sprint**: 9 · **Team**: Beta

Maintain real-time position projections updated from settlement events. Position: client_id, instrument_id, quantity, avg_cost, market_value, unrealized_pnl. Updated on SettlementCompleted event (add to position) and CorporateAction events (adjust quantity/cost). Materialzed in PostgreSQL read model. Redis cache for hot position queries.

**ACs**:

1. Given settlement completing 100 share purchase, When projected, Then position_quantity += 100, avg_cost recalculated
2. Given position query, When Redis cached, Then response < 1ms
3. Given corporate action (2:1 bonus), When processed, Then quantity doubled, avg_cost halved

**Tests**: position_update_buy · position_update_sell · avg_cost_calculation · redis_cache_sub1ms · corporate_action_bonus · corporate_action_dividend_cash · concurrent_updates_consistent

**Dependencies**: STORY-D09-013, K-05, D-12

---

## Feature D09-F06 — Settlement Failure Management (2 Stories)

---

### STORY-D09-015: Implement settlement failure detection and buy-in

**Feature**: D09-F06 · **Points**: 3 · **Sprint**: 10 · **Team**: Beta

Detect settlement failures: instruction not settled by settlement_date + grace_period (configurable). Failure classification: SECURITIES_SHORTFALL, CASH_SHORTFALL, CSD_REJECT, TIMEOUT. Buy-in process: if seller fails to deliver securities after grace period, buyer can initiate buy-in (purchase from market). Buy-in saga: place buy-in order via D-01, execute via D-02, charge difference to failing party.

**ACs**:

1. Given settlement not completed by SD + 2 days, When grace expires, Then SettlementFailed event with reason
2. Given securities shortfall, When buy-in initiated, Then buy-in order placed, cost difference charged to seller
3. Given settlement failure, When detected, Then escalation to operations, regulatory reporting flagged

**Tests**: failure_detection_grace_period · buyin_order_placement · buyin_cost_allocation · cash_shortfall_handling · csd_rejection · timeout_failure · escalation_to_ops · reg_reporting_flag

**Dependencies**: STORY-D09-008, D-01, D-02, K-05

---

### STORY-D09-016: Implement settlement failure reporting and metrics

**Feature**: D09-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Beta

Dashboard and reports for settlement failures. Metrics: settlement_success_rate, average_settlement_time, failure_by_reason, buy_in_count. Daily settlement report: attempted, settled, failed, pending. Regulatory reporting integration for settlement discipline regime (SDR) compliance. Historical trend analysis.

**ACs**:

1. Given settlement cycle complete, When metrics calculated, Then settlement_success_rate = settled/attempted
2. Given regulatory query, When SDR report requested, Then fail rates and corrective actions exported
3. Given Grafana dashboard, When queried, Then live settlement metrics displayed

**Tests**: success_rate_calculation · daily_report_generation · sdr_export · grafana_metrics · trend_analysis · perf_report_under5sec

**Dependencies**: STORY-D09-015, K-06, D-10

---

# EPIC D-13: CLIENT MONEY RECONCILIATION (18 Stories)

## Feature D13-F01 — Daily Automated Recon Workflow (3 Stories)

---

### STORY-D13-001: Implement daily reconciliation scheduler and orchestrator

**Feature**: D13-F01 · **Points**: 3 · **Sprint**: 8 · **Team**: Delta

Build reconciliation orchestrator triggered daily at configurable time (default 06:00 UTC). Workflow: 1) Fetch internal balances from K-16, 2) Fetch external bank statements, 3) Run matching engine, 4) Generate break report, 5) Route breaks to escalation. Orchestration via W-01 workflow orchestration. Status tracking: SCHEDULED → RUNNING → COMPLETED → FAILED. Dual-calendar date awareness for recon date.

**ACs**:

1. Given 06:00 UTC, When scheduler triggers, Then reconciliation workflow starts for previous business day
2. Given all steps complete, When workflow finishes, Then status = COMPLETED, ReconCompleted event emitted
3. Given step failure, When error occurs, Then workflow retries step 3x, then escalates

**Tests**: scheduler_trigger · workflow_all_steps · step_failure_retry · status_tracking · event_emission · weekend_skip · bs_calendar_date · perf_full_run_under5min

**Dependencies**: K-16, K-05, K-15, W-01 _(Note: W-01 Workflow Orchestration delivers Sprint 13; initial Sprint 8 implementation uses K-05 event-triggered orchestration for recon pipeline steps — W-01 integration in Sprint 13 adds durable workflow state, per-step retry policies, and regulator-grade audit trail)_

---

### STORY-D13-002: Implement internal balance extraction

**Feature**: D13-F01 · **Points**: 2 · **Sprint**: 8 · **Team**: Delta

Extract internal client money balances from K-16 ledger as-of recon date. Query: per-client cash balance by currency. Include pending settlements (earmarked amounts). Output: ReconciliationBalanceSet { client_id, currency, available_balance, earmarked_balance, total_balance, as_of_date, as_of_date_bs }. Snapshot stored for audit trail.

**ACs**:

1. Given recon date 2081-06-15 BS, When balance extracted, Then reflects all postings through that date
2. Given client with multi-currency accounts, When extracted, Then separate balance per currency
3. Given balance snapshot stored, When audited, Then immutable record with hash

**Tests**: extract_single_client · extract_multi_currency · earmarked_included · snapshot_stored · as_of_date_boundary · audit_hash · perf_10k_clients_under30sec

**Dependencies**: STORY-D13-001, K-16, K-15

---

### STORY-D13-003: Implement recon audit trail and evidence package

**Feature**: D13-F01 · **Points**: 2 · **Sprint**: 8 · **Team**: Delta

Every reconciliation run produces an immutable audit trail: run_id, recon_date, operator, start_time, end_time, internal_balance_hash, external_balance_hash, match_count, break_count, resolution_status. Evidence package in PDF/CSV for regulatory inspection via K-07. Package includes: balance snapshots, match details, break details, resolution notes.

**ACs**:

1. Given recon completed, When audit trail generated, Then all metadata captured with hash-chain integrity
2. Given regulator requests evidence, When export requested, Then PDF/CSV package generated < 30 seconds
3. Given evidence package, When inspected, Then includes balance snapshots, matches, and breaks

**Tests**: audit_trail_creation · evidence_pdf_export · evidence_csv_export · hash_chain_integrity · regulatory_format · generation_under_30sec

**Dependencies**: STORY-D13-001, K-07

---

## Feature D13-F02 — Statement Ingestion (3 Stories)

---

### STORY-D13-004: Implement bank statement ingestion engine

**Feature**: D13-F02 · **Points**: 3 · **Sprint**: 8 · **Team**: Delta

Ingest external bank statements from multiple sources: SWIFT MT940/MT942 messages, CSV files, API feeds. Parser framework with pluggable format handlers. Normalize to internal StatementEntry: date, reference, amount, currency, counterparty, narrative. Validation: duplicate detection, date range verification, currency matching. Store in statement_entries table.

**ACs**:

1. Given MT940 file uploaded, When parsed, Then all transactions extracted and normalized
2. Given duplicate statement entry (same reference), When ingested, Then deduplicated, no double-count
3. Given CSV from bank_B, When ingested, Then correct parser selected and entries normalized

**Tests**: parse_mt940 · parse_mt942 · parse_csv · duplicate_detection · currency_validation · date_range_check · unknown_format_error · perf_10k_entries_under10sec

**Dependencies**: K-05, K-07

---

### STORY-D13-005: Implement SFTP/API statement fetch automation

**Feature**: D13-F02 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Automate statement fetching from banks: SFTP pull at scheduled times (configurable per bank), REST API integration for real-time feeds. Connection management with K-14 credentials. Retry logic for temporary connectivity issues. File integrity verification (checksum). Fetch status tracking: SCHEDULED → FETCHING → RECEIVED → PARSED → ERROR.

**ACs**:

1. Given SFTP credentials configured, When scheduled fetch runs, Then statement file downloaded and queued for parsing
2. Given API feed configured, When new statement arrives, Then automatically ingested within 60 seconds
3. Given SFTP connection fails, When retried 3 times, Then SFTP_ERROR status, alert raised

**Tests**: sftp_fetch_success · sftp_retry_on_failure · api_realtime_ingest · checksum_verification · credential_rotation · fetch_status_tracking · concurrent_fetches

**Dependencies**: STORY-D13-004, K-14, K-18

---

### STORY-D13-006: Implement statement normalization and validation

**Feature**: D13-F02 · **Points**: 2 · **Sprint**: 9 · **Team**: Delta

Post-ingestion normalization: standardize date formats (Gregorian + BS via K-15), normalize currency codes, clean narratives (remove special characters), standardize counterparty names. Validation rules: amount must be non-zero, date within expected range, currency is supported, reference is present. Invalid entries quarantined for manual review.

**ACs**:

1. Given statement with non-standard date, When normalized, Then both Gregorian and BS dates populated
2. Given entry with zero amount, When validated, Then quarantined with reason ZERO_AMOUNT
3. Given normalized entries, When stored, Then standardized format for matching engine

**Tests**: normalize_date_formats · normalize_currency · clean_narratives · validate_zero_amount · validate_missing_ref · quarantine_invalid · bs_date_conversion

**Dependencies**: STORY-D13-004, K-15

---

## Feature D13-F03 — Matching Engine (3 Stories)

---

### STORY-D13-007: Implement exact matching algorithm

**Feature**: D13-F03 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Core matching engine: compare internal ledger entries against external statement entries. Exact match criteria: date (±0 days), amount (exact), currency, reference. One-to-one matching. Result: MATCHED pairs + UNMATCHED internal + UNMATCHED external. Matching runs per client per bank account per recon date. MatchResult stored with confidence_score = 1.0 for exact matches.

**ACs**:

1. Given internal entry (date=2081-06-15, amount=50000, ref=TXN001), When statement has identical, Then MATCHED with confidence=1.0
2. Given internal entry with no matching statement, When matching runs, Then classified as UNMATCHED_INTERNAL
3. Given all entries matched, When recon completes, Then zero breaks reported

**Tests**: exact_match_found · no_match_internal · no_match_external · one_to_one_only · confidence_1_0 · zero_breaks_clean · perf_50k_entries_under60sec

**Dependencies**: STORY-D13-002, STORY-D13-004

---

### STORY-D13-008: Implement fuzzy matching for near-matches

**Feature**: D13-F03 · **Points**: 5 · **Sprint**: 9 · **Team**: Delta

After exact matching, run fuzzy matching on remaining unmatched entries. Fuzzy criteria: date tolerance ±2 business days, amount tolerance ±0.01 (rounding), reference substring match, narrative keyword match. Confidence scoring: 0.7–0.99 based on match quality. Probable matches flagged for human review with diff report. Configurable thresholds per bank/account (K-02).

**ACs**:

1. Given amount difference of 0.01 (rounding), When fuzzy matched, Then confidence=0.95, flagged as PROBABLE_MATCH
2. Given date off by 1 business day, When fuzzy matched, Then confidence=0.85
3. Given fuzzy match below threshold (0.7), When evaluated, Then not matched, remains UNMATCHED

**Tests**: fuzzy_rounding_match · fuzzy_date_tolerance · fuzzy_reference_substring · fuzzy_below_threshold · confidence_scoring · configurable_thresholds · perf_10k_fuzzy_under30sec

**Dependencies**: STORY-D13-007, K-02

---

### STORY-D13-009: Implement many-to-one and split matching

**Feature**: D13-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Handle cases where multiple internal entries match one external entry (aggregated bank posting) or one internal matches multiple external (split payments). Many-to-one: sum of internal amounts == external amount. One-to-many: internal amount == sum of external amounts. Matching constrained to same date range and same client. Audit trail for composite matches.

**ACs**:

1. Given 3 internal entries summing to 150000, When statement shows single 150000, Then many-to-one match
2. Given 1 internal entry of 100000, When statement shows 60000 + 40000, Then one-to-many match
3. Given composite match, When logged, Then all constituent entries linked in audit trail

**Tests**: many_to_one_exact · one_to_many_exact · composite_audit_trail · partial_sum_no_match · date_range_constraint · recursive_split_limit

**Dependencies**: STORY-D13-007, K-07

---

## Feature D13-F04 — Break Detection & Classification (2 Stories)

---

### STORY-D13-010: Implement break detection and classification engine

**Feature**: D13-F04 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

After matching, unmatched items become breaks. Break classification: TIMING_DIFFERENCE (expected to resolve next day), MISSING_ENTRY (internal present, external absent), UNEXPECTED_ENTRY (external present, internal absent), AMOUNT_MISMATCH (matched pair with amount discrepancy), DUPLICATE (suspected double-posting). Auto-classify based on patterns. Break severity: LOW, MEDIUM, HIGH, CRITICAL based on amount and age.

**ACs**:

1. Given unmatched internal entry, When classified, Then break_type = MISSING_ENTRY, severity based on amount
2. Given break older than 3 days, When severity assessed, Then escalated to HIGH
3. Given suspected duplicate entries, When detected, Then break_type = DUPLICATE, flagged for investigation

**Tests**: classify_missing_entry · classify_unexpected · classify_amount_mismatch · classify_duplicate · severity_by_amount · severity_by_age · auto_classification_accuracy

**Dependencies**: STORY-D13-007, K-05

---

### STORY-D13-011: Implement break aging and trend tracking

**Feature**: D13-F04 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Track break aging from detection date. Aging tiers: 0-1 days (FRESH), 2-3 days (PENDING), 4-7 days (AGING), 8-14 days (OVERDUE), 15+ days (CRITICAL). Daily aging update: recalculate age, escalate if threshold crossed. Historical trend: break count over time, resolution rate, average resolution time. Dashboard metrics exported to K-06.

**ACs**:

1. Given break detected 5 days ago, When aging runs, Then tier = AGING
2. Given break crosses from PENDING to AGING, When threshold crossed, Then escalation event emitted
3. Given historical data, When trend queried, Then break_count and resolution_rate over 30 days

**Tests**: aging_tier_calculation · escalation_on_threshold · trend_30_day · resolution_rate · dashboard_metrics · daily_aging_update

**Dependencies**: STORY-D13-010, K-06

---

## Feature D13-F05 — Segregation Verification (2 Stories)

---

### STORY-D13-012: Implement client money segregation check

**Feature**: D13-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Client money segregation verification: ensure client funds are held in segregated accounts separate from firm money. Daily check: total_client_balances <= segregated_account_balance. Segregation ratio: segregated_balance / client_obligations. Regulatory minimum: ratio >= 1.0. Breach detection with immediate escalation. Multiple segregated accounts per jurisdiction supported.

**ACs**:

1. Given client obligations = 10M, segregated balance = 10.5M, When checked, Then ratio = 1.05, PASS
2. Given segregation ratio < 1.0, When detected, Then BREACH event, immediate escalation to compliance
3. Given multiple jurisdictions, When checked, Then separate segregation check per jurisdiction

**Tests**: segregation_pass · segregation_breach · multi_jurisdiction · ratio_calculation · breach_escalation · historical_ratio_trend · daily_schedule

**Dependencies**: STORY-D13-002, K-16, K-05

---

### STORY-D13-013: Implement segregation reporting and regulatory export

**Feature**: D13-F05 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Regulatory segregation reports: daily attestation of client money segregation status. Report includes: client obligations breakdown, segregated account balances, segregation ratio, any breaches. Export formats per regulator requirements (PDF/CSV/XBRL via D-10). Report archival for 7 years per retention policy. Maker-checker for report sign-off.

**ACs**:

1. Given daily segregation check complete, When report generated, Then includes all required regulatory fields
2. Given report requires sign-off, When maker-checker approves, Then archived with audit trail
3. Given 7-year retention, When stored, Then retention policy enforced via K-08

**Tests**: report_generation · maker_checker_signoff · archival_7_year · export_pdf · export_csv · regulatory_fields_complete · audit_trail

**Dependencies**: STORY-D13-012, D-10, K-07, K-08

---

## Feature D13-F06 — Escalation Workflow (2 Stories)

---

### STORY-D13-014: Implement break escalation matrix and routing

**Feature**: D13-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Configurable escalation matrix: breaks route to appropriate resolver based on type, severity, and age. Level 1: Operations team (0-2 days). Level 2: Finance manager (3-5 days). Level 3: Compliance officer (6-10 days). Level 4: Senior management (10+ days). Escalation path per break type configurable via K-02. Notification via email/SMS/portal on escalation.

**ACs**:

1. Given FRESH break (age 0), When routed, Then assigned to Level 1: Operations
2. Given break ages to 6 days without resolution, When escalation runs, Then routed to Level 3: Compliance
3. Given escalation, When notified, Then email + portal notification sent to target resolver

**Tests**: routing_level1 · routing_level2 · escalation_on_aging · notification_email · notification_portal · configurable_matrix · multi_break_routing

**Dependencies**: STORY-D13-010, K-02, K-05

---

### STORY-D13-015: Implement break resolution workflow

**Feature**: D13-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Break resolution workflow: resolver investigates → adds resolution notes → selects resolution action (WRITE_OFF, ADJUST, TIMING_RESOLVED, FALSE_POSITIVE) → maker-checker approval for write-offs above threshold (K-02). Resolution audit trail: who, when, action, notes, approval. Resolved breaks removed from aging. Bulk resolution for TIMING_RESOLVED breaks that auto-clear.

**ACs**:

1. Given operations resolves break as TIMING_RESOLVED, When confirmed next day, Then break auto-cleared
2. Given write-off > threshold, When submitted, Then maker-checker approval required
3. Given resolved break, When audited, Then full resolution trail with notes and approver

**Tests**: resolve_timing · resolve_writeoff · maker_checker_writeoff · bulk_timing_resolution · audit_resolution_trail · auto_clear_timing · threshold_configurable

**Dependencies**: STORY-D13-014, K-01, K-07

---

# EPIC D-03: PORTFOLIO MANAGEMENT SYSTEM (13 Stories)

## Feature D03-F01 — Portfolio Construction & Optimization (3 Stories)

---

### STORY-D03-001: Implement portfolio data model and CRUD API

**Feature**: D03-F01 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Portfolio entity: portfolio_id, client_id, name, strategy (GROWTH/INCOME/BALANCED/INDEX), benchmark_id (D-11), currency, inception_date, status (ACTIVE/FROZEN/CLOSED). Holdings: portfolio_id, instrument_id, quantity, avg_cost, market_value, weight_pct. REST API: GET /portfolios, GET /portfolios/:id/holdings, POST /portfolios. Dual-calendar inception dates. Event emission on changes.

**ACs**:

1. Given POST /portfolios, When created, Then portfolio with holdings initialized, PortfolioCreated event emitted
2. Given GET /portfolios/:id/holdings, When queried, Then returns current holdings with market values and weights
3. Given portfolio benchmark set, When queried, Then benchmark returns from D-11 reference data

**Tests**: create_portfolio_valid · get_holdings · set_benchmark · update_strategy · event_emission · dual_calendar · soft_delete_close · perf_holdings_sub5ms

**Dependencies**: K-01, K-05, D-11

---

### STORY-D03-002: Implement target allocation and constraint engine

**Feature**: D03-F01 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Target allocation model: define desired portfolio composition. TargetAllocation: portfolio_id, instrument_id (or sector/asset_class), target_weight_pct, min_weight_pct, max_weight_pct. Constraint engine validates: sum of targets = 100%, min <= target <= max, instrument exists and is ACTIVE. Multiple allocation models per portfolio (conservative/aggressive). Maker-checker for allocation changes.

**ACs**:

1. Given target allocations summing to 100%, When saved, Then accepted and versioned
2. Given allocation with instrument weight > max_weight, When validated, Then constraint violation error
3. Given allocation change, When submitted, Then requires maker-checker approval

**Tests**: allocation_sum_100 · constraint_min_max · constraint_sum_not_100_reject · maker_checker_required · version_history · multiple_models · active_instrument_check

**Dependencies**: STORY-D03-001, D-11, K-07

---

### STORY-D03-003: Implement portfolio optimization engine

**Feature**: D03-F01 · **Points**: 5 · **Sprint**: 10 · **Team**: Delta

Mean-variance optimization: given expected returns, covariance matrix, and constraints, compute optimal portfolio weights. Inputs from D-05 (pricing) and D-06 (risk). Efficient frontier calculation. Constraints: no short-selling (weight >= 0), sector limits, single-stock concentration limits. Output: recommended allocation with expected return, volatility, Sharpe ratio. T3 plugin for custom optimization models.

**ACs**:

1. Given expected returns and covariance, When optimization runs, Then optimal weights minimize volatility for target return
2. Given no-short-sell constraint, When optimized, Then all weights >= 0
3. Given T3 custom model, When loaded, Then custom optimization logic executes in sandbox

**Tests**: optimize_basic · no_short_sell · sector_limits · concentration_limits · efficient_frontier · sharpe_ratio · t3_custom_model · perf_100_instruments_under5sec

**Dependencies**: STORY-D03-002, D-05, D-06, K-04

---

## Feature D03-F02 — NAV Calculation (3 Stories)

---

### STORY-D03-004: Implement daily NAV calculation engine

**Feature**: D03-F02 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Net Asset Value calculation: NAV = (sum of market_values of all holdings) - liabilities. Market values from D-05 pricing (latest or EOD). Runs daily after market close. Per-unit NAV for fund-type portfolios. Cash holdings included. Accrued income (dividends, interest) included. NAV history stored for time-series analysis. NAVCalculated event emitted.

**ACs**:

1. Given portfolio with 5 holdings, When NAV calculated, Then NAV = sum(qty × price) for all holdings + cash - liabilities
2. Given per-unit NAV for fund, When calculated, Then NAV_per_unit = portfolio_NAV / total_units
3. Given NAV calculated, When stored, Then NAV history queryable by date (both Gregorian and BS)

**Tests**: nav_basic_5_holdings · nav_per_unit · nav_with_cash · nav_accrued_income · nav_history_stored · nav_event_emitted · dual_calendar_date · perf_100_holdings_under2sec

**Dependencies**: STORY-D03-001, D-05, K-15

---

### STORY-D03-005: Implement TWR and MWR return calculation

**Feature**: D03-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Time-Weighted Return (TWR): measures portfolio performance independent of cash flows. Period return = (NAV_end / NAV_begin) - 1, compounded across sub-periods split at each cash flow. Money-Weighted Return (MWR/IRR): internal rate of return considering cash flow timing. Both computed for configurable periods: daily, MTD, QTD, YTD, ITD. Returns in both nominal and annualized form.

**ACs**:

1. Given NAV series with 2 cash flows, When TWR calculated, Then geometric linking of sub-period returns
2. Given cash flows at various dates, When MWR calculated, Then IRR computed using Newton-Raphson
3. Given period = YTD starting from BS new year, When calculated, Then return from 1 Baisakh to date

**Tests**: twr_no_cashflow · twr_with_cashflows · mwr_irr_basic · mwr_newton_convergence · period_daily · period_ytd_bs · annualized_return · perf_5year_history_under3sec

**Dependencies**: STORY-D03-004, K-15

---

### STORY-D03-006: Implement benchmark comparison and attribution

**Feature**: D03-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Performance attribution: compare portfolio return vs benchmark return. Active return = portfolio_return - benchmark_return. Attribution analysis: allocation effect (sector weight diff × sector return) + selection effect (stock selection within sector). Benchmark data from D-11 (index definitions). Report: sector-level and stock-level attribution. Tracking error calculation.

**ACs**:

1. Given portfolio return 12%, benchmark return 10%, When compared, Then active_return = 2%
2. Given sector attribution, When calculated, Then allocation_effect + selection_effect ≈ active_return
3. Given tracking error, When computed, Then annualized std dev of active returns

**Tests**: active_return_basic · allocation_effect · selection_effect · tracking_error · attribution_sum_to_active · sector_breakdown · perf_attribution_under5sec

**Dependencies**: STORY-D03-005, D-11

---

## Feature D03-F03 — Drift Detection & Rebalancing (3 Stories)

---

### STORY-D03-007: Implement portfolio drift detection engine

**Feature**: D03-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Monitor portfolio holdings vs target allocation. Drift = |actual_weight - target_weight| per instrument/sector. Drift threshold configurable per portfolio (K-02 default: 5%). Check runs: on trade execution, on price update (EOD), on scheduled basis. DriftDetected event emitted when any holding breaches threshold. Dashboard widget showing drift heatmap.

**ACs**:

1. Given target 20%, actual 26%, threshold 5%, When checked, Then drift = 6%, DriftDetected event
2. Given all holdings within threshold, When checked, Then status = ALIGNED, no event
3. Given EOD price update changes weights, When drift check runs, Then recalculated with new market values

**Tests**: drift_above_threshold · drift_within_threshold · drift_on_price_update · drift_on_trade · configurable_threshold · drift_event_emission · heatmap_data

**Dependencies**: STORY-D03-002, D-05, K-02, K-05

---

### STORY-D03-008: Implement rebalancing order generation

**Feature**: D03-F03 · **Points**: 5 · **Sprint**: 10 · **Team**: Delta

When drift exceeds threshold, generate rebalancing orders to restore target allocation. Algorithm: for each drifted holding, calculate required buy/sell to reach target weight. Consider: lot sizes (D-11), minimum order values, tax-loss harvesting opportunities. Generate order set with net orders (avoid unnecessary round-trips). Submit to D-01 with maker-checker approval. Rebalancing saga via K-17.

**ACs**:

1. Given drift detected, When rebalance generated, Then buy/sell orders to restore target weights within tolerance
2. Given lot size constraint, When order quantity calculated, Then rounded to nearest lot
3. Given rebalance orders generated, When submitted, Then routed to D-01 with maker-checker approval

**Tests**: rebalance_basic · lot_size_rounding · minimum_order_filter · tax_loss_harvest · net_orders_no_roundtrip · maker_checker_required · saga_compensation · perf_50_holdings_under3sec

**Dependencies**: STORY-D03-007, D-01, D-11, K-17

---

### STORY-D03-009: Implement what-if scenario analysis

**Feature**: D03-F04 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

What-if analysis: simulate portfolio changes without executing. Scenarios: add/remove holding, change allocation, stress test (price shock). Inputs: hypothetical trades or allocation changes. Output: simulated NAV, return, risk metrics, drift impact. No actual orders placed. Results cached for comparison. Side-by-side current vs proposed view.

**ACs**:

1. Given hypothetical buy of 1000 shares instrument X, When simulated, Then projected portfolio with updated weights and NAV
2. Given -20% market stress on equities, When simulated, Then projected NAV and VaR impact shown
3. Given two scenarios compared, When side-by-side view, Then current vs proposed metrics displayed

**Tests**: what_if_add_holding · what_if_remove_holding · stress_test_equity · stress_test_rate · side_by_side_compare · no_orders_placed · results_cached

**Dependencies**: STORY-D03-001, D-05, D-06

---

## Feature D03-F05 — Maker-Checker for Rebalance (1 Story)

---

### STORY-D03-010: Implement rebalance approval workflow

**Feature**: D03-F05 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Rebalance order sets require maker-checker approval before submission. Approval UI shows: current allocation, target allocation, proposed orders, estimated costs (fees, market impact). Approver can: approve all, reject all, approve with modifications. Rejection requires reason. Approval generates RebalanceApproved event, triggering order submission to D-01.

**ACs**:

1. Given rebalance order set, When submitted for approval, Then approver sees full impact analysis
2. Given approver rejects, When reason provided, Then RebalanceRejected event, orders not submitted
3. Given approver approves, When confirmed, Then orders submitted to D-01, audit trail logged

**Tests**: approval_full_view · reject_with_reason · approve_submit_orders · modified_approval · audit_trail · concurrent_approval_race · notification_to_maker

**Dependencies**: STORY-D03-008, K-01, K-07

---

## Feature D03-F04 extra — Portfolio reporting (3 stories)

---

### STORY-D03-011: Implement portfolio valuation report

**Feature**: D03-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Generate portfolio valuation report showing: holdings list with quantity, avg_cost, market_price, market_value, unrealized P&L per holding. Summary: total market value, total cost, total unrealized P&L, asset allocation pie chart data. Export: PDF, CSV, JSON. Report date parameter supports dual-calendar.

**ACs**:

1. Given portfolio with 10 holdings, When valuation report generated, Then all holdings listed with P&L
2. Given CSV export requested, When generated, Then correct delimiters and headers
3. Given as_of date in BS, When report generated, Then prices as of that BS date

**Tests**: report_10_holdings · unrealized_pnl · export_pdf · export_csv · as_of_bs_date · asset_allocation_data · perf_under_5sec

**Dependencies**: STORY-D03-001, STORY-D03-004, K-15

---

### STORY-D03-012: Implement portfolio transaction history

**Feature**: D03-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Transaction history for portfolio: trades, corporate actions, cash flows, dividends. Filterable by date range, instrument, transaction type. Paginated API with cursor-based pagination. Each entry links to original order (D-01) or corporate action (D-12). Export to CSV.

**ACs**:

1. Given portfolio with 50 transactions, When queried with page_size=20, Then first 20 returned with cursor
2. Given filter by instrument, When applied, Then only that instrument's transactions returned
3. Given transaction entry, When clicked, Then links to source order or corporate action

**Tests**: paginated_list · filter_by_instrument · filter_by_type · filter_by_date · cursor_pagination · link_to_source · export_csv · perf_1000_txns_under_2sec

**Dependencies**: STORY-D03-001, D-01, D-12

---

### STORY-D03-013: Implement portfolio risk summary

**Feature**: D03-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Portfolio risk summary from D-06: VaR (1-day 95%, 1-day 99%), beta vs benchmark, Sharpe ratio, max drawdown, volatility. Concentration risk: top 5 holdings weight, sector exposure. Updated on EOD or on-demand. Risk limit breach alerts if VaR exceeds portfolio-level limit.

**ACs**:

1. Given portfolio risk calculated, When queried, Then VaR, beta, Sharpe, drawdown displayed
2. Given top holding weight > 25%, When checked, Then concentration_warning flagged
3. Given VaR exceeds limit, When detected, Then RiskBreachAlert event emitted

**Tests**: var_calculation · beta_vs_benchmark · sharpe_ratio · max_drawdown · concentration_check · risk_breach_alert · eod_update

**Dependencies**: STORY-D03-001, D-06, D-11

---

# EPIC D-05: PRICING ENGINE (12 Stories)

## Feature D05-F01 — Real-Time & EOD Pricing (3 Stories)

---

### STORY-D05-001: Implement real-time price ingestion and distribution

**Feature**: D05-F01 · **Points**: 3 · **Sprint**: 9 · **Team**: Delta

Ingest real-time prices from D-04 market data service. Store latest price per instrument in Redis for sub-millisecond lookups. Price entity: instrument_id, bid, ask, mid, last, volume, timestamp_utc, timestamp_bs, source. PriceUpdated events for subscribers. Price staleness detection: if no update in configurable period, flag as STALE.

**ACs**:

1. Given market data tick for instrument X, When ingested, Then Redis updated within 10ms, PriceUpdated event emitted
2. Given price query, When Redis hit, Then response < 1ms
3. Given no price update for 5 minutes, When staleness check runs, Then price flagged as STALE

**Tests**: ingest_from_d04 · redis_sub_1ms · price_updated_event · staleness_detection · multi_instrument · dual_calendar · perf_10k_updates_per_sec

**Dependencies**: D-04, K-05, Redis

---

### STORY-D05-002: Implement EOD price capture and official close

**Feature**: D05-F01 · **Points**: 2 · **Sprint**: 9 · **Team**: Delta

End-of-day price capture: at market close, snapshot official closing prices for all instruments. Source: exchange closing price > VWAP > last trade > previous close (waterfall). EOD price stored in PostgreSQL price_history table partitioned by date. EODPriceCaptured event triggers downstream: NAV calculation, risk calculation, MTM batch.

**ACs**:

1. Given market close at 15:00, When EOD capture runs, Then official close price stored for all active instruments
2. Given no trades for instrument today, When closing determined, Then uses previous close with flag NO_TRADE
3. Given EOD prices captured, When event emitted, Then D-03, D-05, D-06 downstream processes triggered

**Tests**: eod_capture_close_price · no_trade_previous_close · waterfall_priority · event_triggers · partitioned_storage · dual_calendar · perf_5k_instruments_under_30sec

**Dependencies**: STORY-D05-001, D-04, K-15

---

### STORY-D05-003: Implement price history and time-series queries

**Feature**: D05-F01 · **Points**: 2 · **Sprint**: 9 · **Team**: Delta

Price history API: GET /prices/:instrumentId/history?from=&to=&interval=. Intervals: TICK, 1MIN, 5MIN, 15MIN, 1H, 1D. OHLC aggregation for interval queries. TimescaleDB for performance. Supports BS date range queries via K-15 conversion. Pagination for large result sets.

**ACs**:

1. Given 1 year price history for instrument, When daily interval queried, Then ~250 OHLC records returned
2. Given BS date range, When queried, Then converted to Gregorian for TimescaleDB query
3. Given TICK interval, When queried with 1-hour range, Then raw tick data returned paginated

**Tests**: history_daily_ohlc · history_tick_raw · bs_date_conversion · pagination_large_set · timescaledb_performance · ohlc_aggregation · perf_1yr_sub_500ms

**Dependencies**: STORY-D05-002, K-15, TimescaleDB

---

## Feature D05-F02 — Yield Curve Bootstrapping (3 Stories)

---

### STORY-D05-004: Implement yield curve construction engine

**Feature**: D05-F02 · **Points**: 5 · **Sprint**: 9 · **Team**: Delta

Yield curve bootstrapping: construct zero-coupon yield curve from market instruments. Input: deposit rates (short-end), FRA rates, swap rates (long-end). Bootstrapping algorithm: iterative strip from short to long tenors. Interpolation methods: linear, log-linear, cubic spline (configurable). Output: YieldCurve object with zero rates at standard tenors. Multiple curves per currency/risk-free rate.

**ACs**:

1. Given deposit rates + swap rates, When bootstrapped, Then zero curve produced with rates at 1M/3M/6M/1Y/2Y/5Y/10Y
2. Given interpolation = cubic_spline, When querying off-tenor rate, Then smooth interpolation
3. Given new market data, When curve rebuilt, Then YieldCurveUpdated event emitted

**Tests**: bootstrap_basic · interpolation_linear · interpolation_cubic · off_tenor_rate · multi_currency_curves · event_emission · perf_curve_build_under_1sec

**Dependencies**: D-04, K-05

---

### STORY-D05-005: Implement discount factor and forward rate calculation

**Feature**: D05-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

From yield curve, derive: discount factors — DF(t) = exp(-r(t) × t), forward rates — f(t1,t2) = (r2×t2 - r1×t1)/(t2-t1). Day count conventions: ACT/365, ACT/360, 30/360 (configurable per instrument). Forward rate between any two dates. Present value calculation: PV = CF × DF(t). Batch PV for bond cash flows.

**ACs**:

1. Given zero rate 5% for 2Y, When discount factor calculated, Then DF = exp(-0.05 × 2) ≈ 0.9048
2. Given 1Y rate 4%, 2Y rate 5%, When forward rate 1Y1Y calculated, Then f ≈ 6% (approx)
3. Given bond with 10 cash flows, When batch PV computed, Then sum(CF_i × DF_i) = bond PV

**Tests**: discount_factor_basic · forward_rate_basic · day_count_act365 · day_count_30_360 · batch_pv_bond · multi_curve · perf_1000_pv_sub_100ms

**Dependencies**: STORY-D05-004

---

### STORY-D05-006: Implement curve scenario and shift analysis

**Feature**: D05-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Yield curve scenarios: parallel shift (all rates ±X bps), steepening (short rates -, long rates +), flattening (inverse). Scenario output: shifted curve, PV impact on portfolio. Integration with D-03 (portfolio) and D-06 (risk) for scenario analysis. Pre-defined scenarios: +100bps, -100bps, bear steepener, bull flattener.

**ACs**:

1. Given +100bps parallel shift, When applied, Then all zero rates increased by 1%, new PVs calculated
2. Given steepener scenario, When applied, Then short rates -50bps, long rates +50bps
3. Given scenario results, When sent to D-06, Then VaR impact assessed

**Tests**: parallel_shift_up · parallel_shift_down · steepener · flattener · portfolio_impact · var_integration · custom_scenario

**Dependencies**: STORY-D05-005, D-03, D-06

---

## Feature D05-F03 — T3 Pricing Model Plugins (2 Stories)

---

### STORY-D05-007: Implement T3 pricing model plugin interface

**Feature**: D05-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Define T3 plugin interface for custom pricing models: IPricingModel with methods price(instrument, marketData), calibrate(marketData), getModelParams(). Plugin runs in T3 sandbox (K-04) with access to market data but no network. Model registry: register, version, activate, deprecate. A/B testing support: run two models in parallel, compare results.

**ACs**:

1. Given T3 pricing model registered, When price() called, Then model executes in sandbox, returns price
2. Given model calibration required, When calibrate() called, Then model parameters updated
3. Given A/B test configured, When pricing runs, Then both models execute, results compared and logged

**Tests**: t3_model_registration · price_execution_sandbox · calibration · ab_test_parallel · model_versioning · sandbox_isolation · perf_pricing_sub_50ms

**Dependencies**: K-04, STORY-D05-001

---

### STORY-D05-008: Implement Black-Scholes and binomial model plugins

**Feature**: D05-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Built-in pricing models as T3 plugins: Black-Scholes for European options (call/put), Binomial tree for American options. Black-Scholes: C = S×N(d1) - K×e^(-rT)×N(d2). Greeks: delta, gamma, theta, vega, rho. Binomial: CRR model with configurable steps (default 100). Implied volatility solver using Newton-Raphson.

**ACs**:

1. Given European call option parameters, When Black-Scholes priced, Then price matches analytical formula within 0.01
2. Given American put option, When binomial model priced, Then accounts for early exercise premium
3. Given market price and model params, When implied vol solved, Then convergence within 10 iterations

**Tests**: bs_call_price · bs_put_price · bs_greeks_delta · bs_greeks_gamma · binomial_american · implied_vol_solver · convergence_check · perf_1000_options_under_5sec

**Dependencies**: STORY-D05-007

---

## Feature D05-F04 — Mark-to-Market Batch (2 Stories)

---

### STORY-D05-009: Implement MTM batch engine

**Feature**: D05-F04 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

End-of-day mark-to-market batch: value all positions at current market prices. Process: for each portfolio × instrument, calculate market_value = qty × latest_price. Unrealized P&L = market_value - cost_basis. Run after EOD price capture (STORY-D05-002). MTM results stored per date. MTMCompleted event triggers D-10 regulatory reporting. Bulk processing with parallel workers.

**ACs**:

1. Given 10,000 positions, When MTM batch runs, Then all positions valued within 5 minutes
2. Given MTM complete, When stored, Then queryable by date, portfolio, instrument
3. Given MTMCompleted event, When emitted, Then D-03 NAV and D-06 risk recalculated

**Tests**: mtm_basic_position · mtm_10k_positions · unrealized_pnl · event_triggers_nav · event_triggers_risk · date_queryable · perf_10k_under_5min

**Dependencies**: STORY-D05-002, D-03, K-05

---

### STORY-D05-010: Implement price challenge workflow

**Feature**: D05-F05 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Price challenge: if MTM price seems incorrect (>X% deviation from previous close or model price), flag for review. Challenge workflow: auto-flag → analyst review → accept/override price → re-run MTM for affected positions. Override requires maker-checker with reason. ChallengeReason: STALE_PRICE, OUTLIER, MODEL_DEVIATION, MANUAL.

**ACs**:

1. Given price deviates >10% from previous close, When MTM runs, Then price flagged as OUTLIER
2. Given analyst overrides price, When maker-checker approves, Then MTM re-run with override price
3. Given challenge, When resolved, Then audit trail with original price, override, reason, approver

**Tests**: outlier_detection · manual_challenge · override_maker_checker · re_run_mtm · audit_trail · threshold_configurable · stale_price_flag

**Dependencies**: STORY-D05-009, K-01, K-07

---

## Feature D05-F05 extra — Price validation (2 Stories)

---

### STORY-D05-011: Implement price validation rules

**Feature**: D05-F01 · **Points**: 2 · **Sprint**: 9 · **Team**: Delta

Price validation before storage: price > 0, bid <= ask, price within circuit breaker limits (D-04), price change within daily limit (configurable %). Validation rules configurable per instrument type via K-02. Invalid prices quarantined with reason. Quarantined prices don't flow to downstream consumers.

**ACs**:

1. Given price with bid > ask, When validated, Then rejected with INVALID_SPREAD
2. Given price change > 20% from previous, When validated, Then quarantined with EXCESSIVE_CHANGE
3. Given quarantined price, When downstream queries, Then receives last valid price

**Tests**: valid_price_passes · bid_gt_ask_rejects · excessive_change · circuit_breaker_limit · zero_price_rejects · quarantine_storage · last_valid_fallback

**Dependencies**: STORY-D05-001, D-04, K-02

---

### STORY-D05-012: Implement cross-validation between sources

**Feature**: D05-F01 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Cross-validate prices from multiple sources (if available). Compare: primary feed vs secondary feed vs model price. Deviation threshold: configurable per instrument (K-02). If deviation exceeds threshold, flag for review. Source priority: exchange official > primary feed > secondary feed > model. Event: PriceDeviationDetected.

**ACs**:

1. Given primary = 100, secondary = 105 (5% deviation, threshold 3%), When cross-validated, Then PriceDeviationDetected
2. Given official exchange price available, When multiple sources present, Then official price takes priority
3. Given single source only, When validated, Then accepted with source_count = 1 flag

**Tests**: cross_validate_pass · deviation_detected · source_priority · single_source · threshold_configurable · event_emission · multi_instrument

**Dependencies**: STORY-D05-011, K-02, K-05

---

# EPIC D-08: MARKET SURVEILLANCE (16 Stories)

## Feature D08-F01 — Wash Trade Detection (3 Stories)

---

### STORY-D08-001: Implement wash trade detection engine

**Feature**: D08-F01 · **Points**: 5 · **Sprint**: 9 · **Team**: Gamma

Detect wash trades: trades where beneficial owner is on both sides. Detection rules: same client buy + sell same instrument within time window (configurable, default 5 min), no change in beneficial ownership, price near market (not arm's length). Listen to OrderFilled events. Alert generation: WashTradeAlert with confidence score, matched trades, rule violated.

**ACs**:

1. Given client buys 100 shares then sells 100 shares same instrument within 5 min, When detected, Then WashTradeAlert with confidence > 0.9
2. Given buy and sell by different clients with same beneficial owner, When detected, Then alert raised
3. Given genuine trade with time gap > configured window, When analyzed, Then no alert

**Tests**: same_client_buy_sell · beneficial_owner_match · time_window_configurable · genuine_trade_no_alert · price_proximity · confidence_scoring · perf_10k_trades_under_10sec

**Dependencies**: D-01, D-02, K-05, K-01

---

### STORY-D08-002: Implement wash trade pattern analysis

**Feature**: D08-F01 · **Points**: 3 · **Sprint**: 9 · **Team**: Gamma

Advanced pattern detection beyond simple same-client: ring trades (A→B→C→A), pre-arranged trades (large orders submitted simultaneously). Graph analysis of trade relationships. Configurable pattern rules via K-03 rules engine. Pattern complexity scoring: simple (2 parties) to complex (5+ parties ring). Historical pattern storage for trend analysis.

**ACs**:

1. Given trades A→B, B→C, C→A for same instrument within 1 hour, When analyzed, Then RingTradeAlert
2. Given pattern rules in K-03, When new rule added, Then detection engine applies immediately via hot-reload
3. Given historical pattern data, When trend queried, Then frequency of wash patterns over time

**Tests**: ring_trade_3_parties · ring_trade_5_parties · pre_arranged_simultaneous · k03_rule_hot_reload · historical_trend · false_positive_filtering · perf_pattern_analysis

**Dependencies**: STORY-D08-001, K-03

---

### STORY-D08-003: Implement volume manipulation detection

**Feature**: D08-F01 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Detect artificial volume inflation: client generates high trade volume without genuine economic purpose. Metrics: trade_volume / normal_volume ratio, order-to-trade ratio, self-trade percentage. Threshold-based alerting: if volume ratio > 5x normal (10-day average) for a client-instrument pair, generate alert. Exclude market makers (configurable exception list).

**ACs**:

1. Given client trades 10x normal volume in single session, When detected, Then VolumeManipulationAlert
2. Given market maker on exception list, When high volume detected, Then no alert
3. Given client's order-to-trade ratio > 10:1, When analyzed, Then potential manipulation flag

**Tests**: volume_spike_detection · market_maker_exception · order_to_trade_ratio · normal_volume_baseline · multi_day_pattern · configurable_threshold · perf_analysis

**Dependencies**: STORY-D08-001, D-04, K-02

---

## Feature D08-F02 — Spoofing/Layering Detection (3 Stories)

---

### STORY-D08-004: Implement spoofing detection engine

**Feature**: D08-F02 · **Points**: 5 · **Sprint**: 9 · **Team**: Gamma

Detect spoofing: placing large orders with intent to cancel before execution to manipulate price. Detection: 1) Large order placed, 2) Price moves in desired direction, 3) Original order cancelled, 4) Opposite trade placed at better price. Time correlation between order/cancel/trade events. Alert: SpoofingAlert with event timeline and confidence. Listen to OrderPlaced, OrderCancelled, OrderFilled events.

**ACs**:

1. Given large buy order → price rises → order cancelled → sell executed, When pattern detected, Then SpoofingAlert
2. Given order placed and filled (no cancel), When analyzed, Then no alert (genuine trade)
3. Given alert, When generated, Then includes full event timeline with timestamps

**Tests**: spoof_place_cancel_trade · genuine_trade_no_alert · time_correlation · confidence_scoring · event_timeline · multi_instrument · perf_realtime_detection

**Dependencies**: D-01, K-05

---

### STORY-D08-005: Implement layering detection

**Feature**: D08-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Detect layering: placing multiple orders at different price levels to create artificial depth. Pattern: multiple limit orders at progressively worse prices on one side, while executing on other side. Detection: analyze order book depth contribution per client. If single client represents >X% of depth on one side and actively trading on other, flag. Order book data from D-04.

**ACs**:

1. Given client places 5 buy limit orders at decreasing prices representing 40% of buy-side depth, When selling simultaneously, Then LayeringAlert
2. Given client depth contribution < threshold, When analyzed, Then no alert
3. Given layering detected, When alert generated, Then includes order book snapshot and client's orders highlighted

**Tests**: layering_multiple_levels · below_threshold_no_alert · order_book_snapshot · depth_contribution · cancel_after_execution · configurable_threshold · perf_orderbook_analysis

**Dependencies**: STORY-D08-004, D-04

---

### STORY-D08-006: Implement order-to-cancel ratio monitoring

**Feature**: D08-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Gamma

Monitor cancel ratio per client: cancelled_orders / total_orders. High cancel ratio (>80%) is indicator of potential manipulation (spoofing/layering). Time-windowed calculation: rolling 1-hour, daily. Alert when cancel ratio exceeds threshold for sustained period. Exceptions: market makers (higher cancel ratio normal). Dashboard: real-time cancel ratio per client.

**ACs**:

1. Given client cancels 90% of orders in 1 hour, When monitored, Then CancelRatioAlert
2. Given market maker with 95% cancel ratio, When on exception list, Then no alert
3. Given rolling 1-hour window, When queried, Then real-time cancel ratio per client

**Tests**: high_cancel_ratio_alert · market_maker_exception · rolling_window · sustained_period · dashboard_realtime · configurable_threshold · daily_aggregation

**Dependencies**: D-01, K-02, K-06

---

## Feature D08-F03 — Front-Running Detection (2 Stories)

---

### STORY-D08-007: Implement front-running detection engine

**Feature**: D08-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Detect front-running: employee/broker trades ahead of known client orders. Pattern: employee buys → client large buy order → price rises → employee sells. Requires cross-referencing employee personal accounts with client order flow. Time window: employee trade within configurable period before client order. Alert: FrontRunningAlert with trade pair and timeline.

**ACs**:

1. Given employee buys instrument, followed by client large buy order within 1 hour, When detected, Then FrontRunningAlert
2. Given employee trade on instrument with no subsequent client activity, When analyzed, Then no alert
3. Given alert, When generated, Then includes employee trade, client order, time gap, price movement

**Tests**: employee_before_client · no_client_activity_no_alert · time_window_configurable · price_movement_correlation · multi_employee · account_linkage · perf_realtime

**Dependencies**: D-01, K-01, K-05

---

### STORY-D08-008: Implement information barrier monitoring

**Feature**: D08-F03 · **Points**: 2 · **Sprint**: 10 · **Team**: Gamma

Monitor information barriers (Chinese walls): detect potential breaches where restricted information may have crossed. Track: restricted list changes (D-07), employee trading activity, communication patterns. Alert when employee trades in instrument shortly after it appears on restricted list or after material non-public information (MNPI) event.

**ACs**:

1. Given instrument added to restricted list, When employee trades it within 24h, Then InformationBarrierAlert
2. Given employee authorized for restricted trading, When trades restricted stock, Then no alert (authorized)
3. Given barrier monitoring, When compliance queried, Then full timeline of restricted list vs employee trades

**Tests**: restricted_trade_alert · authorized_no_alert · timeline_query · mnpi_event_correlation · multi_employee_check · configurable_window

**Dependencies**: STORY-D08-007, D-07, K-01

---

## Feature D08-F04 — AI Anomaly Detection (3 Stories)

---

### STORY-D08-009: Implement ML-based anomaly detection model

**Feature**: D08-F04 · **Points**: 5 · **Sprint**: 10 · **Team**: Gamma

Machine learning anomaly detection for trading patterns. Feature engineering: trade volume, price impact, order frequency, cancel ratio, time-of-day, instrument volatility. Model: Isolation Forest or Autoencoder (unsupervised) trained on historical normal trading. Anomaly score: 0-1 (>0.8 = alert). Model governed by K-09 (registry, explainability, HITL). Runs on aggregated daily features per client-instrument.

**ACs**:

1. Given normal trading pattern, When scored, Then anomaly_score < 0.5
2. Given unusual pattern (e.g., sudden instrument not previously traded), When scored, Then anomaly_score > 0.8, alert
3. Given model registered in K-09, When explainability requested, Then SHAP values show feature contributions

**Tests**: normal_pattern_low_score · unusual_pattern_high_score · shap_explainability · model_registration_k09 · retraining_drift · feature_engineering · perf_scoring_under_100ms

**Dependencies**: K-09, D-01, D-04

---

### STORY-D08-010: Implement anomaly model training pipeline

**Feature**: D08-F04 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Training pipeline: extract historical features (90 days rolling), train model (nightly batch), register in K-09 with version, deploy to scoring service. Pipeline: feature extraction (SQL) → data preprocessing → model training (Python/scikit-learn) → evaluation (AUC-ROC on known bad actors) → registration → deployment. Drift detection (K-09): if PSI > 0.2, trigger retraining.

**ACs**:

1. Given 90 days of data, When training pipeline runs, Then new model version registered in K-09
2. Given model evaluation, When AUC-ROC < 0.7, Then model rejected, previous version retained
3. Given PSI > 0.2 detected by K-09, When drift flag raised, Then automatic retraining triggered

**Tests**: pipeline_end_to_end · model_evaluation_threshold · drift_detection · automatic_retraining · version_rollback · feature_extraction_sql · perf_training_under_30min

**Dependencies**: STORY-D08-009, K-09

---

### STORY-D08-011: Implement HITL override for AI alerts

**Feature**: D08-F04 · **Points**: 2 · **Sprint**: 10 · **Team**: Gamma

Human-in-the-loop override for AI-generated alerts. Analyst reviews alert → classifies: TRUE_POSITIVE, FALSE_POSITIVE, NEEDS_INVESTIGATION. Feedback loop: analyst classifications fed back to improve model. Override requires reason and is audit-logged. Dashboard: AI alert accuracy over time, false positive rate, analyst agreement rate.

**ACs**:

1. Given AI alert classified as FALSE_POSITIVE, When analyst overrides, Then feedback stored for model retraining
2. Given override, When logged, Then audit trail includes analyst, reason, original score, classification
3. Given 30 days of feedback, When accuracy calculated, Then true positive rate displayed in dashboard

**Tests**: override_false_positive · override_true_positive · feedback_loop · audit_logging · accuracy_dashboard · analyst_agreement_rate

**Dependencies**: STORY-D08-009, K-01, K-07, K-09

---

## Feature D08-F05 — Case Management (3 Stories)

---

### STORY-D08-012: Implement surveillance case lifecycle

**Feature**: D08-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Surveillance case management: alerts escalated to cases for investigation. Case lifecycle: OPENED → INVESTIGATING → EVIDENCE_GATHERED → DECISION → CLOSED (SUBSTANTIATED/UNSUBSTANTIATED). Case entity: case_id, alert_ids[], assigned_analyst, subject_client, instruments, opened_date, status, priority (LOW/MEDIUM/HIGH/CRITICAL). SLA: cases must reach DECISION within configurable days (K-02).

**ACs**:

1. Given alert escalated to case, When opened, Then case created with alert linked, assigned to analyst
2. Given case overdue (past SLA), When SLA check runs, Then escalation to compliance manager
3. Given case substantiated, When closed, Then regulatory reporting flagged (D-10)

**Tests**: case_open_from_alert · case_assignment · status_transitions · sla_escalation · close_substantiated · close_unsubstantiated · regulatory_flag · perf_case_query

**Dependencies**: STORY-D08-001, K-01, K-02, K-07

---

### STORY-D08-013: Implement evidence collection and packaging

**Feature**: D08-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Evidence collection for surveillance cases: trade records, order history, communication records (reference only), account details, beneficial ownership. Evidence package: PDF report with timeline, trades, alerts, analyst notes. Package stored in S3/MinIO with hash for integrity. Export for regulatory submission. Evidence linkage: each piece references source system and record.

**ACs**:

1. Given case with 3 alerts and 50 trades, When evidence packaged, Then PDF with complete timeline generated
2. Given evidence package, When hash verified, Then integrity confirmed
3. Given regulatory submission required, When exported, Then format matches regulator requirements

**Tests**: evidence_collection · pdf_generation · hash_integrity · regulatory_format · trade_records · order_history · s3_storage · perf_package_under_60sec

**Dependencies**: STORY-D08-012, K-07, D-01

---

### STORY-D08-014: Implement surveillance dashboard and reporting

**Feature**: D08-F05 · **Points**: 2 · **Sprint**: 10 · **Team**: Gamma

Real-time surveillance dashboard: active alerts count by type, open cases by priority, analyst workload, SLA compliance rate, false positive rate. Historical metrics: alerts/week trend, case resolution time, substantiation rate. Drill-down from dashboard to alert/case details. Export: daily/weekly/monthly surveillance reports for compliance.

**ACs**:

1. Given dashboard query, When loaded, Then real-time alert counts, case counts, SLA rate displayed
2. Given weekly trend, When queried, Then alerts per type over last 12 weeks charted
3. Given compliance report, When exported, Then includes all required regulatory metrics

**Tests**: dashboard_realtime · alert_counts_by_type · case_counts_by_priority · sla_compliance · weekly_trend · export_report · drilldown_navigation · perf_dashboard_under_3sec

**Dependencies**: STORY-D08-012, K-06

---

# EPIC D-10: REGULATORY REPORTING (13 Stories)

## Feature D10-F01 — Report Definition & Template Management (2 Stories)

---

### STORY-D10-001: Implement report definition registry

**Feature**: D10-F01 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Report definition registry: store report templates, schedules, and recipient configurations. ReportDefinition: id, name, regulator, frequency (DAILY/WEEKLY/MONTHLY/QUARTERLY/ANNUAL/ADHOC), format (PDF/CSV/XBRL/JSON), datasource_query, template_ref, deadline_offset (days before/after period end), status. CRUD API for definitions. Maker-checker for new/modified report definitions.

**ACs**:

1. Given new report definition, When created with maker-checker, Then stored and scheduled
2. Given report definition, When queried, Then returns template, schedule, format, deadline
3. Given yearly report, When schedule calculated, Then deadline based on fiscal year (K-15 BS)

**Tests**: create_definition · query_definition · maker_checker · schedule_daily · schedule_monthly · bs_fiscal_year · soft_delete

**Dependencies**: K-01, K-02, K-07, K-15

---

### STORY-D10-002: Implement report template engine

**Feature**: D10-F01 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Template engine: define report layout with data bindings. Template language: Handlebars or Jinja2 style placeholders. Templates stored in S3/MinIO. Template sections: header (regulator info, period), body (data tables, charts), footer (signatures, disclaimers). Versioned templates. Preview mode: render with sample data before activation.

**ACs**:

1. Given template with data bindings, When rendered with actual data, Then complete report generated
2. Given template preview, When sample data applied, Then visual preview shown without submission
3. Given template version 2, When activated, Then new submissions use v2, historical reports retain v1

**Tests**: render_with_data · preview_sample · versioning · template_storage_s3 · handlebars_syntax · data_binding · performance_render_under_10sec

**Dependencies**: STORY-D10-001

---

## Feature D10-F02 — Multi-Format Rendering (3 Stories)

---

### STORY-D10-003: Implement PDF report renderer

**Feature**: D10-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

PDF rendering engine: convert report data + template to formatted PDF. Support: tables, headers/footers, page numbers, charts (embedded as images), dual-calendar dates, Nepali/Devanagari script support. Page layout: A4, letter (configurable). Digital signature: sign PDF with organization certificate. Watermark support (DRAFT/FINAL).

**ACs**:

1. Given report data, When rendered to PDF, Then formatted document with tables and headers
2. Given Nepali text, When rendered, Then Devanagari script displays correctly
3. Given FINAL report, When digitally signed, Then certificate embedded, tamper-evident

**Tests**: pdf_basic_render · nepali_script · tables_formatting · digital_signature · watermark_draft · page_numbers · perf_50_page_under_30sec

**Dependencies**: STORY-D10-002

---

### STORY-D10-004: Implement CSV/Excel report renderer

**Feature**: D10-F02 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

CSV and Excel rendering: tabular data export. CSV: configurable delimiter (comma, tab, pipe), UTF-8 BOM for Excel compatibility, header row. Excel (.xlsx): multiple sheets, formatted headers, column widths, number formatting, sheet names from report sections. Large dataset streaming: generate without loading all data in memory.

**ACs**:

1. Given tabular report data, When rendered as CSV, Then proper escaping, UTF-8 BOM, headers
2. Given multi-section report, When rendered as Excel, Then each section as separate sheet
3. Given 100K row dataset, When streamed to CSV, Then memory usage < 100MB

**Tests**: csv_basic · csv_escaping · csv_utf8_bom · excel_multi_sheet · excel_formatting · streaming_large · perf_100k_rows_under_60sec

**Dependencies**: STORY-D10-002

---

### STORY-D10-005: Implement XBRL report renderer

**Feature**: D10-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

XBRL rendering for regulatory taxonomies. Map report data to XBRL taxonomy elements. Inline XBRL (iXBRL) for human-readable + machine-readable. Taxonomy validation: report must conform to specified taxonomy. Namespace management. Support for extensible taxonomy elements. T3 plugin for jurisdiction-specific taxonomies (e.g., Nepal SEBON taxonomy).

**ACs**:

1. Given report data mapped to taxonomy, When rendered as XBRL, Then valid XML with correct namespace
2. Given XBRL document, When taxonomically validated, Then no validation errors
3. Given T3 Nepal taxonomy plugin, When loaded, Then SEBON-specific elements available

**Tests**: xbrl_render_basic · taxonomy_validation · ixbrl_human_readable · namespace_correct · t3_nepal_taxonomy · extension_elements · perf_render_under_30sec

**Dependencies**: STORY-D10-002, K-04

---

## Feature D10-F03 — Regulatory Portal Submission (2 Stories)

---

### STORY-D10-006: Implement T3 regulatory submission adapter

**Feature**: D10-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

T3 plugin interface for regulatory portal submission. IRegulatorAdapter: submitReport(report, format), checkStatus(submissionId), downloadAcknowledgment(submissionId). Plugin per regulator: SEBON adapter (Nepal), with extensibility for other jurisdictions. Submission tracking: PREPARED → SUBMITTED → ACKNOWLEDGED → ACCEPTED/REJECTED. Credential management via K-14.

**ACs**:

1. Given rendered report, When submitted via SEBON adapter, Then report delivered to regulator portal
2. Given submission, When status checked, Then current status returned (ACKNOWLEDGED/REJECTED)
3. Given new jurisdiction adapter, When registered as T3, Then submissions route to correct regulator

**Tests**: submit_to_sebon · check_status · download_acknowledgment · retry_on_failure · credential_rotation · multi_jurisdiction · t3_isolation

**Dependencies**: STORY-D10-003, K-04, K-14

---

### STORY-D10-007: Implement submission scheduling and deadline tracking

**Feature**: D10-F03 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Automated submission scheduling: based on ReportDefinition schedule, trigger report generation → rendering → submission at configured time before deadline. Deadline tracking: dashboard showing upcoming deadlines, overdue submissions, submission status. Reminder notifications: T-5 days, T-2 days, T-1 day before deadline. Deadline calendar respects BS calendar (K-15).

**ACs**:

1. Given daily report due at 10:00 AM, When schedule triggers, Then report generated and submitted by 09:00 AM
2. Given deadline T-2 days, When reminder triggers, Then notification to reporting team
3. Given overdue submission, When detected, Then escalation to compliance manager

**Tests**: schedule_daily · schedule_monthly · reminder_t5 · reminder_t2 · overdue_escalation · bs_calendar_deadline · dashboard_upcoming

**Dependencies**: STORY-D10-006, K-15, K-05

---

## Feature D10-F04 — ACK/NACK Tracking (2 Stories)

---

### STORY-D10-008: Implement ACK/NACK processing engine

**Feature**: D10-F04 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Process regulator acknowledgments: parse ACK (accepted) and NACK (rejected) responses. NACK includes rejection reason and field-level errors. On NACK: parse errors, flag in dashboard, notify reporting team. On ACK: mark submission as ACCEPTED, store acknowledgment reference. Resubmission workflow for NACKed reports with corrections.

**ACs**:

1. Given ACK from regulator, When processed, Then submission status → ACCEPTED, reference stored
2. Given NACK with errors, When parsed, Then field-level errors displayed, resubmission workflow initiated
3. Given resubmission after NACK, When corrected and resubmitted, Then new submission linked to original

**Tests**: ack_processing · nack_field_errors · resubmission_link · notification_on_nack · status_update_accepted · multi_format_ack · perf_processing_under_5sec

**Dependencies**: STORY-D10-006, K-05

---

### STORY-D10-009: Implement submission audit trail

**Feature**: D10-F04 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Complete audit trail for regulatory submissions: report generation timestamp, renderer used, file hash, submission timestamp, submission channel, ACK/NACK timestamp, response content, resubmission history. Immutable via K-07. Evidence package export for regulatory inspection showing full submission lifecycle.

**ACs**:

1. Given submission lifecycle, When audited, Then every step timestamped and logged
2. Given evidence package request, When exported, Then includes report file, submission proof, ACK/NACK
3. Given file hash, When verified, Then confirms report not modified post-submission

**Tests**: audit_generation · audit_submission · audit_ack_nack · evidence_export · file_hash_verification · immutable_trail · perf_export_under_30sec

**Dependencies**: STORY-D10-008, K-07

---

## Feature D10-F05 — Real-Time Trade Reporting (2 Stories)

---

### STORY-D10-010: Implement real-time trade report generation

**Feature**: D10-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Real-time trade reporting: generate and submit trade reports as trades execute (regulatory requirement for some jurisdictions). Listen to OrderFilled events. Transform trade data to regulatory report format. Submit within configurable window (e.g., T+15 minutes). Batch optimization: aggregate trades for same reporting period into single submission.

**ACs**:

1. Given OrderFilled event, When processed, Then trade report generated within 1 minute
2. Given T+15min reporting window, When multiple trades occur, Then batched into single submission
3. Given submission failure, When retried, Then idempotent resubmission (no duplicate reports)

**Tests**: realtime_from_event · batch_optimization · submission_within_window · retry_idempotent · format_compliance · multi_instrument · perf_1000_trades_per_hour

**Dependencies**: D-01, K-05, STORY-D10-006

---

### STORY-D10-011: Implement trade report reconciliation

**Feature**: D10-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Reconcile submitted trade reports against internal trade records. Daily recon: count of trades submitted vs recorded, value totals match. Identify missing reports (trade exists, no report submitted) and orphan reports (report submitted, no matching trade). ReconciliationBreak events for mismatches.

**ACs**:

1. Given all trades reported, When reconciled, Then MATCHED — zero breaks
2. Given trade without corresponding report, When detected, Then MISSING_REPORT break
3. Given orphan report, When detected, Then ORPHAN_REPORT break flagged

**Tests**: recon_match · missing_report · orphan_report · value_reconciliation · daily_schedule · break_event · report_generation

**Dependencies**: STORY-D10-010, K-05

---

## Feature D10-F06 extra — Reporting dashboard (2 Stories)

---

### STORY-D10-012: Implement regulatory reporting dashboard

**Feature**: D10-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Dashboard: upcoming submissions, overdue submissions, ACK/NACK status, submission success rate, rejection reasons analysis. Calendar view showing reporting deadlines (dual-calendar). Alert widgets for approaching deadlines and failed submissions. Drill-down to individual submission details.

**ACs**:

1. Given dashboard loaded, When viewed, Then upcoming/overdue/success metrics displayed
2. Given calendar view, When dual-calendar toggled, Then deadlines shown in BS or Gregorian
3. Given failed submission, When drill-down clicked, Then full submission detail with errors

**Tests**: dashboard_metrics · calendar_view_bs · calendar_view_greg · drilldown_detail · alert_widgets · success_rate · perf_under_3sec

**Dependencies**: STORY-D10-007, K-06, K-15

---

### STORY-D10-013: Implement regulatory reporting analytics

**Feature**: D10-F06 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Analytics for reporting: submission volumes per period, rejection rates by report type, average time to submission, deadline compliance percentage. Trend analysis: improving/deteriorating submission quality. Regulator-specific analytics: per-regulator rejection patterns. Export analytics as management report.

**ACs**:

1. Given 6 months data, When analytics queried, Then rejection rate trend displayed
2. Given per-regulator view, When filtered, Then specific regulator metrics shown
3. Given management report, When exported, Then PDF with charts and statistics

**Tests**: volume_analytics · rejection_trend · deadline_compliance · regulator_filter · management_report · perf_6month_under_10sec

**Dependencies**: STORY-D10-012, K-06

---

# EPIC D-12: CORPORATE ACTIONS (14 Stories)

## Feature D12-F01 — Corporate Action Lifecycle Engine (3 Stories)

---

### STORY-D12-001: Implement corporate action data model and lifecycle

**Feature**: D12-F01 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

CorporateAction entity: ca_id, instrument_id, type (CASH_DIVIDEND, STOCK_DIVIDEND, BONUS, RIGHTS, SPLIT, MERGER), status (ANNOUNCED → EX_DATE → RECORD_DATE → PAYMENT_DATE → COMPLETED), announcement_date, ex_date, record_date, payment_date, details JSONB. All dates in dual-calendar. CRUD API with maker-checker. CorporateActionAnnounced event emission.

**ACs**:

1. Given cash dividend announced, When created, Then lifecycle starts at ANNOUNCED, event emitted
2. Given ex_date reached, When date passes, Then status → EX_DATE, eligible holders snapshot captured
3. Given all dates in BS format, When stored, Then Gregorian equivalent auto-calculated via K-15

**Tests**: create_cash_dividend · lifecycle_announced · lifecycle_ex_date · lifecycle_record_date · lifecycle_completed · dual_calendar · maker_checker · event_emission

**Dependencies**: D-11, K-05, K-15, K-07

---

### STORY-D12-002: Implement holder snapshot at record date

**Feature**: D12-F01 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

At record_date, capture snapshot of all holders and their positions for the instrument. Snapshot from D-09 position data as-of record_date. Holder record: client_id, position_quantity, account_type. Snapshot is immutable after capture — used for entitlement calculation. Handle ex-date adjustments: positions settled after ex_date excluded.

**ACs**:

1. Given record date, When snapshot taken, Then all holders with positions as-of that date captured
2. Given trade settled after ex-date, When checked, Then buyer excluded from snapshot
3. Given snapshot, When queried later, Then immutable, returns same data

**Tests**: snapshot_record_date · exclude_post_ex · immutable_snapshot · large_holder_list · position_accuracy · audit_trail

**Dependencies**: STORY-D12-001, D-09

---

### STORY-D12-003: Implement corporate action notification engine

**Feature**: D12-F01 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Notify affected holders when corporate action announced. Notification channels: portal, email, SMS (configurable). Content: action type, dates, terms, required action (for elections). Reminder notifications before key dates (ex_date - 2 days, election_deadline - 1 day). Notification preferences per client (K-02). Event: CorporateActionNotified.

**ACs**:

1. Given CA announced, When notification sent, Then all holders of instrument notified
2. Given election required (rights issue), When deadline approaching, Then reminder sent
3. Given client prefers email only, When notified, Then email sent, no SMS

**Tests**: notify_on_announcement · reminder_ex_date · reminder_election · channel_preferences · notification_event · bulk_notify_10k_holders

**Dependencies**: STORY-D12-001, K-02, K-05

---

## Feature D12-F02 — Entitlement Calculation (3 Stories)

---

### STORY-D12-004: Implement cash dividend entitlement engine

**Feature**: D12-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Calculate cash dividend entitlement per holder. Formula: entitlement = position_quantity × dividend_per_share. Handle fractional shares: round down, remainder to fractional pool. Multi-currency: dividend may be in different currency than position. Tax withholding applied (STORY-D12-007). Net entitlement = gross - tax. EntitlementCalculated event with per-holder amounts.

**ACs**:

1. Given holder with 1000 shares, dividend NPR 10/share, When calculated, Then gross = NPR 10,000
2. Given fractional result (e.g., 1001 shares × NPR 0.50 = NPR 500.50), When rounded, Then NPR 500.00, remainder to pool
3. Given multi-currency dividend, When calculated, Then amount in dividend currency, not position currency

**Tests**: basic_cash_dividend · fractional_rounding · multi_currency · large_holder_list · zero_position_excluded · event_emission · perf_50k_holders_under_60sec

**Dependencies**: STORY-D12-002

---

### STORY-D12-005: Implement stock dividend and bonus entitlement

**Feature**: D12-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Stock dividend/bonus entitlement: position_quantity × ratio. E.g., 2:1 bonus = each share gets 2 additional shares. Fractional shares: configurable (round down + cash-in-lieu, or round to nearest). New shares creation: reference data update (D-11 if new ISIN). Position update: add entitled shares to holder position via D-09. Cost basis adjustment: total cost unchanged, per-share cost reduced.

**ACs**:

1. Given 1000 shares, 2:1 bonus, When calculated, Then 2000 new shares entitled
2. Given 100 shares, 3:2 ratio, When 150 expected but fractional handling, Then 150 shares (or 100 + cash-in-lieu for 50)
3. Given bonus applied, When position updated, Then avg_cost per share adjusted (total cost / new total qty)

**Tests**: bonus_2_to_1 · fractional_cash_in_lieu · cost_basis_adjustment · position_update · new_shares_d11 · event_emission · large_volume

**Dependencies**: STORY-D12-002, D-09, D-11

---

### STORY-D12-006: Implement rights issue entitlement and election

**Feature**: D12-F02 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Rights issue entitlement: holders get right to purchase new shares at exercise price. Entitlement: rights_qty = position_qty × rights_ratio. Election: holder can EXERCISE (buy at exercise price), SELL (rights tradeable), or LAPSE (do nothing). Election deadline enforcement. If elected: generate order for new shares. If lapsed: rights expired. ElectionSubmitted event.

**ACs**:

1. Given 1000 shares, 1:5 rights ratio, When entitled, Then 200 rights issued
2. Given holder elects EXERCISE, When submitted before deadline, Then purchase order generated at exercise price
3. Given election deadline passed without election, When checked, Then rights LAPSED

**Tests**: rights_entitlement · election_exercise · election_sell · election_lapse · deadline_enforcement · order_generation · multi_holder_election

**Dependencies**: STORY-D12-004, D-01

---

## Feature D12-F03 — Tax Withholding (2 Stories)

---

### STORY-D12-007: Implement tax withholding calculation

**Feature**: D12-F03 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Calculate tax withholding on corporate action payments. Tax rules per jurisdiction via K-03 T2 rules: Nepal — 5% TDS on dividends for residents, 15% for non-residents. Tax rate depends on: holder type (individual/institutional), residency, instrument type. T2 sandboxed rules per jurisdiction. Gross, tax, net calculation per holder. TaxWithheld event with breakdown.

**ACs**:

1. Given resident individual, NPR 10,000 dividend, 5% TDS, When calculated, Then tax = NPR 500, net = NPR 9,500
2. Given non-resident, When calculated, Then 15% rate applied
3. Given T2 jurisdiction rule change, When hot-reloaded, Then new rate applied from effective date

**Tests**: tds_resident_5pct · tds_nonresident_15pct · institutional_rate · rule_hot_reload · effective_date · multi_currency_tax · perf_50k_holders

**Dependencies**: STORY-D12-004, K-03

---

### STORY-D12-008: Implement tax certificate generation

**Feature**: D12-F03 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Generate TDS certificates for holders after tax withholding. Certificate includes: holder details, CA details, gross amount, tax rate, tax amount, net amount, certificate_number. PDF generation with dual-calendar dates. Annual consolidated TDS statement per holder. Certificates stored for 7-year retention (K-08). Regulator format compliance.

**ACs**:

1. Given tax withheld, When certificate generated, Then PDF with all required fields
2. Given annual request, When consolidated, Then all TDS for year in single statement
3. Given 7-year retention, When stored, Then retention policy via K-08

**Tests**: certificate_pdf · consolidated_annual · retention_policy · certificate_number_unique · dual_calendar · regulator_format · perf_generation

**Dependencies**: STORY-D12-007, K-08, D-10

---

## Feature D12-F04 — Election Management (1 Story)

---

### STORY-D12-009: Implement election management portal

**Feature**: D12-F04 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Client-facing election portal for corporate actions requiring holder decisions (rights issues, tender offers). Display: CA details, entitlement, options, deadline. Submission: select option, confirm with quantity. Deadline countdown. Maker-checker for institutional elections. Confirmation receipt generated. ElectionDeadline event triggers auto-lapse for non-respondents.

**ACs**:

1. Given rights issue with election, When client views portal, Then entitlement, options, and deadline displayed
2. Given client submits election before deadline, When confirmed, Then ElectionSubmitted event emitted
3. Given deadline passed, When auto-lapse runs, Then unelected rights expire, LapseCompleted event

**Tests**: portal_display · election_submission · deadline_countdown · auto_lapse · maker_checker_institutional · confirmation_receipt · multi_option

**Dependencies**: STORY-D12-006, K-01

---

## Feature D12-F05 — Ledger Posting (2 Stories)

---

### STORY-D12-010: Implement CA ledger posting for cash events

**Feature**: D12-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Post ledger entries for cash corporate actions (dividends, interest). Journal: debit issuer_payable account, credit holder_cash accounts (net of tax), credit tax_payable account. Batch posting for all entitled holders. Reconciliation: total debits == total credits. K-16 integration for balanced double-entry posting. PaymentProcessed event.

**ACs**:

1. Given 1000 holders entitled to cash dividend, When posted, Then 1000 credit entries + 1 debit + tax entries, balanced
2. Given ledger posting, When reconciled, Then total_debits == total_credits per currency
3. Given posting complete, When PaymentProcessed emitted, Then downstream systems notified

**Tests**: cash_dividend_posting · balanced_journal · tax_entry · multi_currency · reconciliation · event_emission · perf_1000_holders_under_30sec

**Dependencies**: STORY-D12-004, STORY-D12-007, K-16, K-17

---

### STORY-D12-011: Implement CA ledger posting for securities events

**Feature**: D12-F05 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Post ledger entries for securities-related corporate actions (bonus, rights exercise, splits). Securities ledger: debit issuer's treasury, credit holder's securities accounts. Position updates via D-09. Stock split: adjust quantity and cost basis (total value unchanged). Merger: retire old securities, issue new. All via K-16 balanced posting.

**ACs**:

1. Given bonus issue, When securities posted, Then holder securities accounts credited, treasury debited
2. Given 2:1 stock split, When posted, Then qty doubled, per-share cost halved, total value unchanged
3. Given merger, When old shares retired and new issued, Then balanced journal for both legs

**Tests**: bonus_securities_posting · stock_split · merger_retire_issue · balanced_journal · position_update · cost_basis_adjustment · perf_posting

**Dependencies**: STORY-D12-005, K-16, D-09

---

### STORY-D12-012: Implement corporate action reconciliation

**Feature**: D12-F05 · **Points**: 2 · **Sprint**: 10 · **Team**: Delta

Reconcile corporate action processing: entitled holders vs actual payments/positions. Checks: all entitled holders received payment/shares, amounts match entitlement, tax correctly withheld. Break detection for discrepancies. Reconciliation report per CA event. CAReconciliationBreak events for mismatches.

**ACs**:

1. Given all holders paid correctly, When reconciled, Then status = FULLY_RECONCILED
2. Given holder missing payment, When detected, Then break flagged with details
3. Given reconciliation report, When generated, Then summary + break details included

**Tests**: full_reconciliation · missing_payment_break · amount_mismatch · tax_verification · report_generation · event_emission · multi_ca_batch

**Dependencies**: STORY-D12-010, STORY-D12-011, K-05

---

## Feature D09-F07 — Settlement Failure Prediction (2 Stories)

---

### STORY-D09-017: Implement ML settlement failure prediction model

**Feature**: D09-F07 · **Points**: 5 · **Sprint**: 10 · **Team**: Delta

Deploy an ML binary classifier (XGBoost, governed by K-09 TIER_2 supervised) that predicts settlement failure risk at trade confirmation time — before the settlement window opens. Features: counterparty_fail_rate_30d (historical), instrument_liquidity_score (from D-04), settlement_amount_vs_counterparty_avg, securities_availability_indicator (from CDSC/depository feed), currency_convertibility_risk, T+n_days_until_settlement, market_stress_regime (from D06-020 regime model). Score 0–1: ≥ 0.7 = HIGH failure risk. HIGH-risk trades surfaced to settlement desk immediately for proactive intervention. Model trained monthly on 18 months of historical settlement outcome data. Governed by K-09 with SHAP explainability, fairness monitoring (no bias by instrument type), and HITL for HIGH-risk interventions.

**ACs**:

1. Given a trade with counterparty whose 30-day fail rate is 18%, instrument with low liquidity score, and crisis market regime, When model scores at confirmation, Then failure_probability ≥ 0.70 (HIGH); settlement desk receives alert with SHAP showing "counterparty_fail_rate +0.32, liquidity_score +0.28, market_regime +0.18"
2. Given a low-risk trade (counterparty fail rate < 2%, liquid instrument, normal market), When scored, Then failure_probability < 0.3; no alert — normal settlement pipeline proceeds
3. Given model registered in K-09, When monthly retrain completes, Then AUC-ROC evaluated on holdout; if AUC < 0.75, previous model retained and ConceptDriftAlert raised; deployment requires TIER_2 approval

**Tests**: high_risk_alert · low_risk_pass · shap_per_trade · counterparty_fail_rate_feature · monthly_retrain · aucroc_threshold · hitl_intervention · k09_tier2_approval · perf_scoring_at_confirmation_under_200ms

**Dependencies**: D09-F01 (trade confirmation), D-04 (liquidity), D06-020 (regime model), K-09 (AI governance — K09-001, K09-003, K09-007)

---

### STORY-D09-018: Implement proactive settlement intervention workflow

**Feature**: D09-F07 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Build the intervention workflow triggered by HIGH-risk settlement predictions (D09-017). Actions available: (1) auto-pre-borrow securities from lending desk if securities availability is the risk driver; (2) send early warning to counterparty operations team; (3) escalate to settlement manager for manual decision. Intervention action selected based on top SHAP contributor: securities_availability → pre-borrow; counterparty_fail_rate → early warning; liquidity_risk → escalate. Human override: settlement manager can dismiss alert or override recommended action. All interventions and outcomes (did it prevent failure?) tracked via K-07 and fed back to model training as labelled outcomes. Dashboard: intervention hit rate, failure prevention rate.

**ACs**:

1. Given HIGH-risk prediction with top SHAP driver = securities_availability, When intervention workflow runs, Then pre-borrow request auto-initiated with securities desk; confirmation of pre-borrow sent to settlement desk within 5 minutes
2. Given settlement manager dismisses alert with reason "already confirmed with counterparty", When dismissal logged, Then outcome tracked; if settlement subsequently fails, miss flagged for model review
3. Given 30-day intervention data, When effectiveness dashboard viewed, Then shows: total HIGH-risk predictions, interventions executed, failures prevented (prediction was HIGH AND intervention executed AND settlement succeeded), false positive rate

**Tests**: auto_preborrow_trigger · early_warning_counterparty · escalation_to_manager · manager_override · outcome_tracking · miss_tracking · effectiveness_dashboard · k07_audit · perf_intervention_within_5min

**Dependencies**: STORY-D09-017, K-07, K-09 (AI governance)

---

## Feature D12-F06 — AI Corporate Action Copilot (2 Stories)

---

### STORY-D12-013: Implement AI corporate action break classification

**Feature**: D12-F06 · **Points**: 5 · **Sprint**: 10 · **Team**: Delta

Deploy an ML break classifier (gradient-boosted multiclass, K-09 autonomous tier) that automatically categorizes corporate action reconciliation breaks (STORY-D12-012) into: DATA_FEED_LATENCY (reference data not yet arrived), ENTITLEMENT_CALCULATION_ERROR (wrong quantity calculated), TAX_MISMATCH (incorrect withholding), ELECTION_MISMATCH (client election not applied), CURRENCY_ROUNDING (FX rounding difference), SYSTEMS_BUG (requires engineering), GENUINE_BREAK (requires analyst). Features: break_amount, break_age_hours, ca_event_type, prior_breaks_same_ca, feed_lag_indicator, rounding_residual, client_election_flag. Classification drives automated remediation routing: DATA_FEED_LATENCY → auto-retry in 2h; CURRENCY_ROUNDING → auto-reconcile if < threshold; GENUINE_BREAK → human queue. SHAP explains each classification for analyst audit trail.

**ACs**:

1. Given a break with small residual amount (< 0.01 NPR) on a cash dividend event with currency_rounding indicator = TRUE, When classified, Then CURRENCY_ROUNDING with probability 0.91; auto-reconciliation initiated; analyst not required
2. Given a break classified DATA_FEED_LATENCY, When auto-retry in 2h, Then if resolved: break closed, outcome = auto_resolved; if still breaking: reclassified and escalated
3. Given classification of GENUINE_BREAK, When analyst reviews, Then SHAP contribution shown: "break_amount (+0.42), no prior auto-reconciliation in same CA event (+0.31), client_election_flag (+0.19)"

**Tests**: currency_rounding_auto_reconcile · data_feed_retry · genuine_break_escalation · entitlement_calc_routing · tax_mismatch_routing · shap_classification · auto_resolve_outcome · false_classification_feedback · k09_autonomous_tier · perf_classification_under_500ms

**Dependencies**: STORY-D12-012 (CA reconciliation), K-09 (AI governance — K09-003, K09-006)

---

### STORY-D12-014: Implement LLM-generated corporate action instruction narrative

**Feature**: D12-F06 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Use an LLM (small, locally-hosted model such as Mistral-7B or similar lightweight model running in K-04 T2 sandbox — no external API calls) to auto-draft human-readable corporate action instruction narratives for clients and custodians. Input: CA event struct (event_type, issuer, ratio, record_date_bs, payment_date_bs, election_options[]). Output: 150–300 word natural language description of the CA event with client obligations, key dates in BS and AD calendar, election deadline, and contact. Draft reviewed and approved by ops team (HITL, K-09 advisory). Approved narratives stored in CA event record; sent to clients via W-02 notification. Reduces manual drafting time from ~30 min to ~2 min per event.

**ACs**:

1. Given a Rights Issue CA event (ratio 1:3, subscription price NPR 100, record_date BS 2081-06-15, election_deadline BS 2081-06-25), When LLM draft generated, Then narrative contains: correct ratio, subscription price, record date in BS and AD, election deadline, client obligation (subscribe/renounce), contact details — all factually accurate
2. Given draft generated, When ops team reviews (HITL), Then approves, modifies, or rejects; approved draft published to client notification; all versions stored with K-07 audit trail
3. Given LLM hallucination risk, When draft reviewed by ops, Then any factual discrepancy (wrong date, wrong entity name) caught before publication; factual accuracy error rate < 1% after review validates model utility

**Tests**: rights_issue_narrative_accuracy · dividend_narrative_accuracy · election_deadline_included · bs_and_ad_dates · hitl_approval_workflow · modified_version_stored · k07_audit_trail · local_model_no_external_api · perf_draft_under_10sec

**Dependencies**: STORY-D12-012, K-04 (T2 sandbox for LLM), K-09 (AI governance), W-02 (notifications), K-07

---

## Feature D08-F06 — LLM-Assisted Surveillance Intelligence (2 Stories)

---

### STORY-D08-015: Implement graph anomaly detection for collusion and coordinated trading

**Feature**: D08-F06 · **Points**: 5 · **Sprint**: 10 · **Team**: Gamma

Extend the D-08 surveillance engine with a Graph Neural Network (GNN — GraphSAGE) to detect collusion and coordinated trading patterns that individual-account anomaly scoring (D08-009) misses. Graph: nodes = trading accounts; edges = temporal co-trading relationships (two accounts trading same instrument in same 30-minute window). GNN learns structural patterns associated with known market manipulation cases. Anomaly: cluster of accounts with dense co-trading + synchronized order patterns + common beneficial owner (from D-07-F04) → CollaborativeAbuseDetected event. Runs nightly on 7-day rolling trade graph. SHAP-over-GNN explanation for each flagged cluster. Governed by K-09 TIER_3 supervised.

**ACs**:

1. Given 5 accounts with common beneficial owner (K-08/D07 beneficial ownership) all trading the same illiquid instrument within 15-minute windows on 3 consecutive days (pump pattern), When GNN scores, Then CollaborativeAbuseDetected with cluster members, coordination score ≥ 0.85, SHAP highlighting "synchronized_timing +0.38, common_beneficial_owner +0.34, illiquid_target +0.28"
2. Given accounts with high individual activity but no coordination (legitimate active traders), When GNN scores, Then coordination_score < 0.3, no alert raised
3. Given GNN model and TIER_3 governance, When new version promoted, Then 10-trading-day shadow evaluation period with compliance officer HITL review; promotion requires senior approval

**Tests**: coordinated_pump_detection · legitimate_active_traders_no_false_positive · beneficial_owner_linkage · shap_cluster_explanation · shadow_evaluation · hitl_senior_approval · nightly_7day_graph · k09_tier3 · perf_gnn_inference_under_2min_nightly

**Dependencies**: D08-F04 (AI anomaly detection), D07-F04 (beneficial ownership), K-09 (AI governance — K09-014, K09-012), K-08

---

### STORY-D08-016: Implement LLM-generated surveillance alert narrative

**Feature**: D08-F06 · **Points**: 3 · **Sprint**: 10 · **Team**: Gamma

Use a locally-hosted LLM (K-04 T2 sandbox) to auto-generate natural-language surveillance alert narratives for case management. Input: alert struct (alert_type, accounts[], instrument, anomaly_score, SHAP_features, trade_timeline, regulatory_context). Output: 200–400 word alert narrative structurally matching SEBON/NRB suspicious activity report format — describing observed behaviour, pattern significance, regulatory reference, and recommended next steps. Case analyst reviews and edits draft before filing. Narrative generation governed by K-09 advisory tier. Reduces case write-up time from ~2h to ~20min. All LLM prompts, retrieved context, and drafts logged via K-07.

**ACs**:

1. Given a spoofing alert (D08-009 score 0.92, instrument NLICL), When LLM narrative generated, Then draft correctly describes: order placement pattern, cancellation timing, price impact, SEBON rule reference (Sec. 82 Insider), and recommended HITL action — all within 30 seconds
2. Given analyst edits draft (modifying regulatory section), When final submitted, Then original LLM draft + analyst edits diff + final version stored immutably in K-07; analyst signature recorded
3. Given LLM prompt contains trade identifiers but no client PII (PII stripped by K-08 masking before prompt construction), When narrative generated, Then no PII leak in generated text; K-08 masking audit confirmed

**Tests**: narrative_spoofing_accuracy · narrative_coordinated_trading · sebon_format_compliance · analyst_edit_diff_stored · k07_immutable_log · pii_stripped_before_prompt · local_model_no_external_call · perf_generation_under_30sec · k09_advisory_tier

**Dependencies**: D08-F04 (AI anomaly), D08-F05 (case management), K-04 (T2 LLM sandbox), K-09 (AI governance), K-07, K-08

---

## Feature D13-F07 — AI Reconciliation Break Intelligence (3 Stories)

---

### STORY-D13-016: Implement ML reconciliation break pattern detection

**Feature**: D13-F07 · **Points**: 5 · **Sprint**: 10 · **Team**: Delta

Deploy a time-series ML model (gradient-boosted + rolling window features) that learns recurring client money reconciliation break patterns to enable proactive detection before breaks escalate. Features per recon run: break_count, total_break_amount, break_age_distribution, counterparty_id, instrument_type, settlement_cycle, last_statement_lag_hours. Model classifies each recon run into: CLEAN (no breaks expected), TRANSIENT (breaks will self-resolve within 24h), SYSTEMATIC (underlying system issue — requires investigation), CRITICAL (segregation breach risk). SYSTEMATIC and CRITICAL trigger immediate escalation workflows. Model governed by K-09.

**ACs**:

1. Given a recon run with break pattern matching 5 prior SYSTEMATIC breaks (same counterparty, same time-of-week, small consistent residual), When ML classifies, Then SYSTEMATIC with confidence 0.88; investigation workflow auto-created with pattern history attached
2. Given a recon run with breaks consistent with known same-day settlement lag (TRANSIENT pattern), When classified, Then TRANSIENT; auto-monitoring set to re-check in 24h rather than immediate escalation
3. Given CRITICAL classification (large break ≥ 5% of segregated funds), When detected, Then immediate segregation alert raised, HITL escalation to CFO/Compliance, K-09 SHAP explanation attached

**Tests**: systematic_break_detection · transient_auto_monitor · critical_segregation_alert · hitl_critical · shap_per_run · false_positive_rate_transient · auto_monitoring_24h · k09_model_governance · perf_classification_under_1sec

**Dependencies**: D13-F04 (break detection), K-09 (AI governance)

---

### STORY-D13-017: Implement AI-assisted break resolution recommendation engine

**Feature**: D13-F07 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Recommendation engine using a RAG-based LLM pipeline: retrieves past break resolutions from K-07 audit records (similar break type, counterparty, instrument) and generates ranked resolution recommendations. For each open break, the engine surfaces: top-3 recommended resolution actions (with historical success rate), relevant K-03 reconciliation rules, and ETA estimate for each action. Accepts natural-language query from reconciliation analyst: "How did we resolve the 2081-04 NPR mismatch with Himalayan Bank?". Governed by K-09 advisory; all interactions audited via K-07.

**ACs**:

1. Given an open break with counterparty "Himalayan Bank" NPR 12,500 cash mismatch, When recommendation engine runs, Then top-3 actions returned: (1) "Request corrected statement — used 8/10 times for this counterparty, avg resolution 4h", (2) "Check pending settlement D13-002 — used 5/10", (3) "Manual journal adjustment — 3/10 as last resort" — with historical citations
2. Given analyst natural language query "How did we resolve the 2081-04 NPR mismatch with Himalayan Bank?", When RAG retrieves matching K-07 records, Then specific resolution steps and outcome from that incident returned with source audit record references
3. Given resolution recommendation accepted by analyst and applied, When outcome recorded, Then success/failure stored in K-07 and added to retrieval corpus for future recommendations

**Tests**: top3_recommendations · historical_success_rates · natural_language_query · rag_k07_retrieval · recommendation_accepted_outcome · recommendation_rejected_override · audit_trail · k09_advisory_tier · perf_recommendations_under_5sec

**Dependencies**: D13-F04, K-07 (audit), K-09 (AI governance), K-03

---

### STORY-D13-018: Implement predictive reconciliation failure forecasting

**Feature**: D13-F07 · **Points**: 3 · **Sprint**: 10 · **Team**: Delta

Forecast which upcoming reconciliation runs are likely to produce breaks, enabling the operations team to pre-empt issues. Model: LSTM time-series trained on 18 months of daily recon outcomes. Inputs: time-series of per-counterparty break history + leading indicators (settlement volume spike, counterparty operational events, market stress regime from D06-020, public holiday impact from K-15). Output: break_probability for each scheduled recon run for the next 3 business days. HIGH-probability runs (≥ 0.70) flagged to operations team the day before, enabling: extra statement chasing, manual pre-check, or pre-agreed resolution protocol. Forecast accuracy (RMSE, calibration) tracked in K-09 dashboard.

**ACs**:

1. Given a counterparty with high settlement volume and an upcoming public holiday (K-15) creating a compressed settlement cycle, When LSTM forecasts, Then break_probability for that counterparty's 3-day-out recon ≥ 0.70 (HIGH); ops team receives next-morning alert with identified risk factors
2. Given forecast accuracy evaluation over past 30 days, When calibration checked, Then predicted 0.70+ events breach 65%+ of the time (well-calibrated); if calibration drops below 50%, model retraining triggered
3. Given a low-risk counterparty with stable 3-month clean recon history, When forecasted, Then break_probability < 0.2 (LOW); no alert, operations team can prioritize higher-risk queues

**Tests**: high_prob_alert_day_before · public_holiday_feature · volume_spike_feature · calibration_evaluation · calibration_drift_retrain · low_risk_no_alert · lstm_3day_horizon · perf_forecast_daily_under_5min · k09_forecast_model

**Dependencies**: STORY-D13-016, D06-020 (regime model), K-15 (calendar), K-09 (AI governance)

---

# MILESTONE 2B SUMMARY

| Epic                      | Feature Count | Story Count | Total SP |
| ------------------------- | ------------- | ----------- | -------- |
| D-09 Post-Trade           | 7             | 18          | 62       |
| D-13 Client Money Recon   | 7             | 18          | 50       |
| D-03 Portfolio Mgmt       | 5             | 13          | 42       |
| D-05 Pricing Engine       | 5             | 12          | 35       |
| D-08 Surveillance         | 6             | 16          | 53       |
| D-10 Regulatory Reporting | 6             | 13          | 33       |
| D-12 Corporate Actions    | 6             | 14          | 41       |
| **TOTAL**                 | **42**        | **104**     | **316**  |

**Sprint 8**: D-09 (001-006), D-13 (001-006) (~12 stories)
**Sprint 9**: D-09 (007-011,013-014), D-13 (005-009), D-03 (001-002,004), D-05 (001-004,011) (~20 stories)
**Sprint 10**: D-09 (012,015-018), D-13 (009-018), D-03 (003,005-013), D-05 (005-010,012), D-08 (001-016), D-10 (001-013), D-12 (001-014) (~62 stories — includes all AI intelligence stories)
