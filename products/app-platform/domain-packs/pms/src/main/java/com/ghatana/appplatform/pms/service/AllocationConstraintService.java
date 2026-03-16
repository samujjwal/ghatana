package com.ghatana.appplatform.pms.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Validates and persists target allocation models for portfolios. Each model specifies
 *              target weight, min weight, and max weight per instrument. Validation rules:
 *              (1) sum of target weights across all instruments must be 100% (tolerance ±0.001),
 *              (2) min ≤ target ≤ max for each instrument,
 *              (3) each instrument must be ACTIVE in the reference catalog.
 *              Maker-checker enforced: new/updated models enter PENDING_REVIEW status and require
 *              approval by a different actor. Each update increments the version number.
 * @doc.layer   Domain
 * @doc.pattern Maker-checker workflow; versioned records; K-02 not needed (static rules).
 */
public class AllocationConstraintService {

    private static final Logger log = LoggerFactory.getLogger(AllocationConstraintService.class);

    private static final double WEIGHT_SUM_TOLERANCE = 0.001;

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final InstrumentCatalogPort catalogPort;
    private final Counter             submittedCounter;
    private final Counter             approvedCounter;
    private final Counter             rejectedCounter;

    public AllocationConstraintService(HikariDataSource dataSource, Executor executor,
                                       InstrumentCatalogPort catalogPort, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.catalogPort      = catalogPort;
        this.submittedCounter = registry.counter("pms.allocation.submitted");
        this.approvedCounter  = registry.counter("pms.allocation.approved");
        this.rejectedCounter  = registry.counter("pms.allocation.rejected");
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface InstrumentCatalogPort {
        boolean isActive(String instrumentId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record AllocationLine(
        String instrumentId,
        double targetWeight,   // 0.0 – 1.0
        double minWeight,      // 0.0 – 1.0
        double maxWeight       // 0.0 – 1.0
    ) {}

    public record SubmitAllocationCommand(
        String             portfolioId,
        String             modelName,
        List<AllocationLine> lines,
        String             submittedBy
    ) {}

    public record ValidationError(String field, String message) {}

    public record AllocationModel(
        String             allocationId,
        String             portfolioId,
        String             modelName,
        List<AllocationLine> lines,
        int                version,
        String             status,       // PENDING_REVIEW | APPROVED | REJECTED
        String             submittedBy,
        LocalDate          submittedDate
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Validate and submit a new (or updated) allocation model for maker-checker review.
     */
    public Promise<AllocationModel> submitAllocation(SubmitAllocationCommand cmd) {
        return Promise.ofBlocking(executor, () -> {
            List<ValidationError> errors = validate(cmd.lines());
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Allocation validation failed: " + errors);
            }
            int nextVersion      = nextVersion(cmd.portfolioId(), cmd.modelName());
            String allocationId  = UUID.randomUUID().toString();
            persistAllocation(allocationId, cmd, nextVersion);
            submittedCounter.increment();
            log.info("Allocation submitted portfolioId={} model={} version={}",
                     cmd.portfolioId(), cmd.modelName(), nextVersion);
            return new AllocationModel(allocationId, cmd.portfolioId(), cmd.modelName(),
                                       cmd.lines(), nextVersion, "PENDING_REVIEW",
                                       cmd.submittedBy(), LocalDate.now());
        });
    }

    /**
     * Approve a PENDING_REVIEW allocation. Approver must differ from submitter.
     */
    public Promise<Void> approve(String allocationId, String approvedBy) {
        return Promise.ofBlocking(executor, () -> {
            String submittedBy = getSubmitter(allocationId);
            if (submittedBy != null && submittedBy.equals(approvedBy)) {
                throw new IllegalStateException(
                    "Maker-checker violation: approver same as submitter for allocationId=" + allocationId);
            }
            updateStatus(allocationId, "APPROVED", approvedBy);
            approvedCounter.increment();
            log.info("Allocation approved allocationId={} by={}", allocationId, approvedBy);
            return null;
        });
    }

    /**
     * Reject a PENDING_REVIEW allocation with a reason.
     */
    public Promise<Void> reject(String allocationId, String rejectedBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(allocationId, "REJECTED", rejectedBy);
            persistRejectionReason(allocationId, reason);
            rejectedCounter.increment();
            log.info("Allocation rejected allocationId={} by={} reason={}", allocationId, rejectedBy, reason);
            return null;
        });
    }

    /**
     * Fetch the latest approved allocation for a portfolio.
     */
    public Promise<AllocationModel> getLatestApproved(String portfolioId) {
        return Promise.ofBlocking(executor, () -> loadLatestApproved(portfolioId));
    }

    // ─── Validation ──────────────────────────────────────────────────────────

    private List<ValidationError> validate(List<AllocationLine> lines) {
        List<ValidationError> errors = new ArrayList<>();

        double totalTarget = lines.stream().mapToDouble(AllocationLine::targetWeight).sum();
        if (Math.abs(totalTarget - 1.0) > WEIGHT_SUM_TOLERANCE) {
            errors.add(new ValidationError("totalWeight",
                "Target weights sum=" + totalTarget + " must equal 1.0 ±" + WEIGHT_SUM_TOLERANCE));
        }

        for (AllocationLine line : lines) {
            if (line.minWeight() > line.targetWeight()) {
                errors.add(new ValidationError("minWeight",
                    "min=" + line.minWeight() + " > target=" + line.targetWeight()
                    + " for instrument=" + line.instrumentId()));
            }
            if (line.targetWeight() > line.maxWeight()) {
                errors.add(new ValidationError("maxWeight",
                    "target=" + line.targetWeight() + " > max=" + line.maxWeight()
                    + " for instrument=" + line.instrumentId()));
            }
            if (!catalogPort.isActive(line.instrumentId())) {
                errors.add(new ValidationError("instrumentId",
                    "Instrument not ACTIVE: " + line.instrumentId()));
            }
        }
        return errors;
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private int nextVersion(String portfolioId, String modelName) throws SQLException {
        String sql = """
            SELECT COALESCE(MAX(version), 0) + 1 FROM target_allocations
            WHERE portfolio_id = ? AND model_name = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            ps.setString(2, modelName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void persistAllocation(String allocationId, SubmitAllocationCommand cmd,
                                   int version) throws SQLException {
        String sql = """
            INSERT INTO target_allocations (
                allocation_id, portfolio_id, model_name, instrument_id,
                target_weight, min_weight, max_weight, version, status, submitted_by, submitted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_REVIEW', ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (AllocationLine line : cmd.lines()) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, cmd.portfolioId());
                ps.setString(3, cmd.modelName());
                ps.setString(4, line.instrumentId());
                ps.setDouble(5, line.targetWeight());
                ps.setDouble(6, line.minWeight());
                ps.setDouble(7, line.maxWeight());
                ps.setInt(8, version);
                ps.setString(9, cmd.submittedBy());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        }
    }

    private String getSubmitter(String allocationId) throws SQLException {
        String sql = "SELECT submitted_by FROM target_allocations WHERE allocation_id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, allocationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("submitted_by") : null;
            }
        }
    }

    private void updateStatus(String allocationId, String status, String actor) throws SQLException {
        String sql = """
            UPDATE target_allocations
            SET status = ?, reviewed_by = ?, reviewed_at = now()
            WHERE allocation_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, actor);
            ps.setString(3, allocationId);
            ps.executeUpdate();
        }
    }

    private void persistRejectionReason(String allocationId, String reason) throws SQLException {
        String sql = "UPDATE target_allocations SET rejection_reason = ? WHERE allocation_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setString(2, allocationId);
            ps.executeUpdate();
        }
    }

    private AllocationModel loadLatestApproved(String portfolioId) throws SQLException {
        String sql = """
            SELECT allocation_id, portfolio_id, model_name, instrument_id,
                   target_weight, min_weight, max_weight, version, status, submitted_by, submitted_at
            FROM target_allocations
            WHERE portfolio_id = ? AND status = 'APPROVED'
            ORDER BY version DESC
            LIMIT 100
            """;
        List<AllocationLine> lines = new ArrayList<>();
        String allocationId = null, modelName = null, submittedBy = null;
        int version = 0;
        LocalDate submittedDate = null;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (allocationId == null) {
                        allocationId  = rs.getString("allocation_id");
                        modelName     = rs.getString("model_name");
                        version       = rs.getInt("version");
                        submittedBy   = rs.getString("submitted_by");
                        submittedDate = rs.getObject("submitted_at", LocalDate.class);
                    }
                    lines.add(new AllocationLine(
                        rs.getString("instrument_id"),
                        rs.getDouble("target_weight"),
                        rs.getDouble("min_weight"),
                        rs.getDouble("max_weight")
                    ));
                }
            }
        }
        if (allocationId == null) return null;
        return new AllocationModel(allocationId, portfolioId, modelName, lines,
                                   version, "APPROVED", submittedBy, submittedDate);
    }
}
