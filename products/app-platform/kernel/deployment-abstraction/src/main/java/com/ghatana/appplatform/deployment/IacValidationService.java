package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Validates Infrastructure-as-Code (IaC) templates — Terraform plans and Helm
 *              charts — before they are applied. Runs three gate types in sequence: syntax
 *              linting, security policy checks (no root containers, resource limits required,
 *              no privileged mode, etc.), and cost estimation guard. A validation run must
 *              pass all enabled gates before the deployment pipeline proceeds.
 *              Satisfies STORY-K10-009.
 * @doc.layer   Kernel
 * @doc.pattern Gate pipeline; IacLinterPort; SecurityPolicyPort; CostEstimatorPort;
 *              validation results persisted for audit; iac_validations_failed Counter.
 */
public class IacValidationService {

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final IacLinterPort      linterPort;
    private final SecurityPolicyPort securityPolicyPort;
    private final CostEstimatorPort  costEstimatorPort;
    private final Counter            validationsRunCounter;
    private final Counter            validationsPassedCounter;
    private final Counter            validationsFailedCounter;

    public IacValidationService(HikariDataSource dataSource, Executor executor,
                                 IacLinterPort linterPort, SecurityPolicyPort securityPolicyPort,
                                 CostEstimatorPort costEstimatorPort, MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.linterPort               = linterPort;
        this.securityPolicyPort       = securityPolicyPort;
        this.costEstimatorPort        = costEstimatorPort;
        this.validationsRunCounter     = Counter.builder("deployment.iac.validations_run_total").register(registry);
        this.validationsPassedCounter  = Counter.builder("deployment.iac.validations_passed_total").register(registry);
        this.validationsFailedCounter  = Counter.builder("deployment.iac.validations_failed_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface IacLinterPort {
        LintResult lint(String templateType, String templateContent);
    }

    public interface SecurityPolicyPort {
        List<PolicyViolation> evaluate(String templateType, String templateContent);
    }

    public interface CostEstimatorPort {
        CostEstimate estimate(String templateType, String templateContent);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum TemplateType { TERRAFORM, HELM }
    public enum ValidationStatus { PASSED, FAILED }
    public enum ViolationSeverity { WARNING, BLOCKING }

    public record LintResult(boolean valid, List<String> errors) {}

    public record PolicyViolation(String rule, String description, ViolationSeverity severity,
                                   String resourcePath) {}

    public record CostEstimate(double estimatedMonthlyCostUsd, boolean exceedsBudgetThreshold,
                                double budgetThresholdUsd) {}

    public record ValidationRun(
        String validationId, String templateType, String envId, String triggerRef,
        ValidationStatus status,
        boolean lintPassed, boolean securityPassed, boolean costPassed,
        List<String> lintErrors,
        List<PolicyViolation> policyViolations,
        CostEstimate costEstimate,
        Instant validatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Validate an IaC template through the full gate pipeline.
     * Returns the full ValidationRun so callers can check status and inspect violations.
     */
    public Promise<ValidationRun> validate(TemplateType templateType, String templateContent,
                                            String envId, String triggerRef) {
        return Promise.ofBlocking(executor, () -> {
            String validationId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            validationsRunCounter.increment();

            LintResult lintResult = linterPort.lint(templateType.name(), templateContent);
            boolean lintPassed = lintResult.valid();

            List<PolicyViolation> violations = lintPassed
                ? securityPolicyPort.evaluate(templateType.name(), templateContent)
                : List.of();
            boolean securityPassed = violations.stream()
                .noneMatch(v -> v.severity() == ViolationSeverity.BLOCKING);

            CostEstimate costEstimate = (lintPassed && securityPassed)
                ? costEstimatorPort.estimate(templateType.name(), templateContent)
                : new CostEstimate(0, false, 0);
            boolean costPassed = !costEstimate.exceedsBudgetThreshold();

            boolean overallPassed = lintPassed && securityPassed && costPassed;
            ValidationStatus status = overallPassed ? ValidationStatus.PASSED : ValidationStatus.FAILED;

            persistValidationRun(validationId, templateType.name(), envId, triggerRef,
                status, lintPassed, securityPassed, costPassed,
                lintResult.errors(), violations, costEstimate, now);

            if (overallPassed) validationsPassedCounter.increment();
            else validationsFailedCounter.increment();

            return new ValidationRun(validationId, templateType.name(), envId, triggerRef,
                status, lintPassed, securityPassed, costPassed,
                lintResult.errors(), violations, costEstimate, now);
        });
    }

    /** Retrieve a previous validation run by ID. */
    public Promise<Map<String, Object>> getValidationRun(String validationId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT validation_id, template_type, env_id, trigger_ref, status, " +
                     "lint_passed, security_passed, cost_passed, validated_at " +
                     "FROM iac_validation_runs WHERE validation_id = ?")) {
                ps.setString(1, validationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new NoSuchElementException("Validation not found: " + validationId);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("validationId",   rs.getString("validation_id"));
                    row.put("templateType",   rs.getString("template_type"));
                    row.put("envId",          rs.getString("env_id"));
                    row.put("triggerRef",     rs.getString("trigger_ref"));
                    row.put("status",         rs.getString("status"));
                    row.put("lintPassed",     rs.getBoolean("lint_passed"));
                    row.put("securityPassed", rs.getBoolean("security_passed"));
                    row.put("costPassed",     rs.getBoolean("cost_passed"));
                    row.put("validatedAt",    rs.getTimestamp("validated_at").toInstant().toString());
                    return row;
                }
            }
        });
    }

    /** List recent validation runs for an environment, newest first. */
    public Promise<List<Map<String, Object>>> listRecentRuns(String envId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT validation_id, template_type, trigger_ref, status, validated_at " +
                     "FROM iac_validation_runs WHERE env_id = ? " +
                     "ORDER BY validated_at DESC LIMIT ?")) {
                ps.setString(1, envId);
                ps.setInt(2, Math.min(limit, 100));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("validationId", rs.getString("validation_id"));
                        row.put("templateType", rs.getString("template_type"));
                        row.put("triggerRef",   rs.getString("trigger_ref"));
                        row.put("status",       rs.getString("status"));
                        row.put("validatedAt",  rs.getTimestamp("validated_at").toInstant().toString());
                        results.add(row);
                    }
                }
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void persistValidationRun(String id, String templateType, String envId,
                                       String triggerRef, ValidationStatus status,
                                       boolean lint, boolean security, boolean cost,
                                       List<String> lintErrors, List<PolicyViolation> violations,
                                       CostEstimate costEstimate, Instant now) throws SQLException {
        int blockingViolations = (int) violations.stream()
            .filter(v -> v.severity() == ViolationSeverity.BLOCKING).count();

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO iac_validation_runs " +
                 "(validation_id, template_type, env_id, trigger_ref, status, " +
                 "lint_passed, security_passed, cost_passed, lint_error_count, " +
                 "blocking_violation_count, estimated_cost_usd, validated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, templateType);
            ps.setString(3, envId);
            ps.setString(4, triggerRef);
            ps.setString(5, status.name());
            ps.setBoolean(6, lint);
            ps.setBoolean(7, security);
            ps.setBoolean(8, cost);
            ps.setInt(9, lintErrors == null ? 0 : lintErrors.size());
            ps.setInt(10, blockingViolations);
            ps.setDouble(11, costEstimate.estimatedMonthlyCostUsd());
            ps.setTimestamp(12, Timestamp.from(now));
            ps.executeUpdate();
        }
    }
}
