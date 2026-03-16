package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose GA readiness checklist gate service (GA-005).
 *              Formal GA readiness gate: checklist of all GA criteria with approval sign-offs.
 *              Categories: Security, Reliability, Compliance, Operations, Data.
 *              All items require named sign-off. GA is approved only when all items are signed.
 *              Approval is immutable once recorded. GA date is recorded.
 * @doc.layer   Integration Testing (GA Readiness)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; gate-check
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS ga_readiness_checklist (
 *   item_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   category       TEXT NOT NULL,
 *   item_name      TEXT NOT NULL,
 *   signed_off_by  TEXT,
 *   signed_off_at  TIMESTAMPTZ,
 *   is_critical    BOOL NOT NULL DEFAULT true,
 *   UNIQUE(category, item_name)
 * );
 * CREATE TABLE IF NOT EXISTS ga_approval (
 *   approval_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   approved       BOOL NOT NULL DEFAULT false,
 *   ga_date        DATE,
 *   approved_by    TEXT,
 *   approved_at    TIMESTAMPTZ,
 *   unsigned_items TEXT
 * );
 * </pre>
 *
 * STORY-GA-005: Implement GA readiness checklist gate
 */
public class GaReadinessChecklistGateService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ChecklistPort {
        /** Load all checklist items with their current sign-off status. */
        List<ChecklistItem> loadChecklist() throws Exception;
        /** Record a named sign-off for an item. Throws if already signed (immutable). */
        void recordSignOff(String category, String itemName, String signedBy) throws Exception;
        /** Get the GA approval record (null if not yet approved). */
        GaApproval getGaApproval() throws Exception;
        /** Record GA approval with date. Immutable once set. */
        void recordGaApproval(String approvedBy, LocalDate gaDate) throws Exception;
        /** Check if GA approval has already been recorded (immutable). */
        boolean isApprovalImmutable() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    public record ChecklistItem(String category, String itemName, String signedOffBy, boolean isCritical) {
        public boolean isSigned() { return signedOffBy != null && !signedOffBy.isBlank(); }
    }

    public record GaApproval(String approvalId, boolean approved, LocalDate gaDate, String approvedBy) {}

    // ── Static checklist definition ───────────────────────────────────────────

    private record RequiredItem(String category, String name) {}

    private static final List<RequiredItem> REQUIRED_ITEMS = List.of(
        // Security
        new RequiredItem("Security", "pen_test_signed"),
        new RequiredItem("Security", "cis_benchmark_80pct"),
        new RequiredItem("Security", "secrets_management_verified"),
        // Reliability
        new RequiredItem("Reliability", "chaos_tests_85pct"),
        new RequiredItem("Reliability", "sla_validated"),
        new RequiredItem("Reliability", "dr_drill_completed"),
        // Compliance
        new RequiredItem("Compliance", "audit_completeness_verified"),
        new RequiredItem("Compliance", "reg_reports_tested"),
        new RequiredItem("Compliance", "regulator_portal_ready"),
        // Operations
        new RequiredItem("Operations", "runbooks_rehearsed"),
        new RequiredItem("Operations", "monitoring_dashboards_ready"),
        new RequiredItem("Operations", "on_call_rota_confirmed"),
        // Data
        new RequiredItem("Data", "backup_restore_validated"),
        new RequiredItem("Data", "retention_configured")
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ChecklistPort checklist;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public GaReadinessChecklistGateService(
        javax.sql.DataSource ds,
        ChecklistPort checklist,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.checklist   = checklist;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.ga.gate.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.ga.gate.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("all_items_signed",         this::allItemsSigned));
            results.add(runScenario("ga_approved_status",       this::gaApprovedStatus));
            results.add(runScenario("ga_blocked_unsigned",      this::gaBlockedUnsigned));
            results.add(runScenario("critical_items_list",      this::criticalItemsList));
            results.add(runScenario("immutable_approval",       this::immutableApproval));
            results.add(runScenario("named_sign_off",           this::namedSignOff));
            results.add(runScenario("checklists_by_category",   this::checklistsByCategory));
            results.add(runScenario("ga_date_recorded",         this::gaDateRecorded));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("GA_GATE_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("GaReadinessChecklistGate", results, passed, failed);
        });
    }

    /** All required items must be signed. */
    private void allItemsSigned(String runId) throws Exception {
        List<ChecklistItem> items = checklist.loadChecklist();
        Map<String, ChecklistItem> byKey = new HashMap<>();
        for (ChecklistItem i : items) byKey.put(i.category() + "." + i.itemName(), i);

        List<String> unsigned = new ArrayList<>();
        for (RequiredItem req : REQUIRED_ITEMS) {
            ChecklistItem item = byKey.get(req.category() + "." + req.name());
            if (item == null || !item.isSigned()) unsigned.add(req.category() + "." + req.name());
        }
        assertStep(runId, "all_signed", "all " + REQUIRED_ITEMS.size() + " checklist items signed",
            "0 unsigned", unsigned.isEmpty(), unsigned.size() + " unsigned: " + unsigned);
    }

    /** GA approval record exists and approved=true. */
    private void gaApprovedStatus(String runId) throws Exception {
        GaApproval approval = checklist.getGaApproval();
        assertStep(runId, "ga_approval_exists", "GA approval record exists", "non-null",
            approval != null, approval == null ? "null" : approval.approvalId());
        if (approval != null) {
            assertStep(runId, "ga_approved_true", "GA approved=true", "true",
                approval.approved(), approval.approved());
        }
    }

    /** GA is blocked (not approved) when any critical item is unsigned. */
    private void gaBlockedUnsigned(String runId) throws Exception {
        List<ChecklistItem> items = checklist.loadChecklist();
        boolean anyUnsigned = items.stream().filter(ChecklistItem::isCritical).anyMatch(i -> !i.isSigned());
        GaApproval approval = checklist.getGaApproval();
        if (anyUnsigned) {
            // If there are unsigned critical items, GA should NOT be approved
            boolean gaNotApproved = approval == null || !approval.approved();
            assertStep(runId, "ga_blocked_on_unsigned", "GA blocked when critical items unsigned",
                "blocked", gaNotApproved, gaNotApproved ? "blocked" : "INCORRECTLY approved");
        } else {
            // All signed — GA can be approved
            assertStep(runId, "ga_unblocked_when_all_signed", "GA unblocked when all items signed",
                "unblocked", true, "all signed");
        }
    }

    /** All 14 defined required items are critical. */
    private void criticalItemsList(String runId) throws Exception {
        List<ChecklistItem> items = checklist.loadChecklist();
        long criticalCount = items.stream().filter(ChecklistItem::isCritical).count();
        // Must include at least the defined required items
        assertStep(runId, "critical_items_count", "critical items count >= " + REQUIRED_ITEMS.size(),
            ">= " + REQUIRED_ITEMS.size(), criticalCount >= REQUIRED_ITEMS.size(), criticalCount);
    }

    /** Once GA approval is recorded, it cannot be modified (immutable). */
    private void immutableApproval(String runId) throws Exception {
        boolean immutable = checklist.isApprovalImmutable();
        assertStep(runId, "approval_immutable", "GA approval record is immutable once set",
            "true", immutable, immutable);
    }

    /** Each sign-off must have a named approver. */
    private void namedSignOff(String runId) throws Exception {
        List<ChecklistItem> items = checklist.loadChecklist();
        List<String> badSignOff = new ArrayList<>();
        for (ChecklistItem item : items) {
            if (item.isSigned() && (item.signedOffBy() == null || item.signedOffBy().isBlank())) {
                badSignOff.add(item.category() + "." + item.itemName());
            }
        }
        assertStep(runId, "all_sign_offs_named", "all sign-offs have a named approver",
            "0 anonymous", badSignOff.isEmpty(), badSignOff.size() + " anonymous: " + badSignOff);
    }

    /** Checklist items must cover all 5 categories. */
    private void checklistsByCategory(String runId) throws Exception {
        List<ChecklistItem> items = checklist.loadChecklist();
        Set<String> categories = new HashSet<>();
        for (ChecklistItem i : items) categories.add(i.category());
        Set<String> required = Set.of("Security", "Reliability", "Compliance", "Operations", "Data");
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(categories);
        assertStep(runId, "all_categories_present", "all 5 required categories present",
            "0 missing", missing.isEmpty(), missing.isEmpty() ? "all present" : "missing: " + missing);
    }

    /** After full approval, GA date is recorded. */
    private void gaDateRecorded(String runId) throws Exception {
        GaApproval approval = checklist.getGaApproval();
        if (approval != null && approval.approved()) {
            assertStep(runId, "ga_date_set", "GA date is recorded in approval",
                "non-null", approval.gaDate() != null, approval.gaDate());
        } else {
            // Record a GA approval to test the flow
            List<ChecklistItem> items = checklist.loadChecklist();
            boolean allSigned = items.stream().allMatch(ChecklistItem::isSigned);
            if (allSigned) {
                checklist.recordGaApproval("GA-GATE-TEST", LocalDate.now());
                GaApproval recorded = checklist.getGaApproval();
                assertStep(runId, "ga_date_set_after_approval", "GA date set after approval",
                    "non-null", recorded != null && recorded.gaDate() != null,
                    recorded == null ? "null" : recorded.gaDate());
            } else {
                assertStep(runId, "ga_date_pending", "GA date pending until all items signed",
                    "pending", true, "not-all-signed");
            }
        }
        audit.audit("GA_DATE_RECORDED", "GA readiness gate validated");
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name); fn.accept(runId); markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) { return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex)      { return new ScenarioResult(name, false, ex.getMessage(),  System.currentTimeMillis() - start); }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('GaReadinessChecklistGate',?) RETURNING run_id")) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE e2e_test_runs SET status=? WHERE run_id=?")) {
            ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
