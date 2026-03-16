package com.ghatana.appplatform.refdata.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   NLP-based automatic classification of financial instruments into asset class
 *                (EQUITY, FIXED_INCOME, DERIVATIVE, ETF, MUTUAL_FUND, ALTERNATIVE) and
 *                sector (BANKING, HYDROPOWER, INSURANCE, MANUFACTURING, etc.) using a
 *                fine-tuned text classification model. Human override is supported and
 *                tracked for model improvement. K-09 advisory tier.
 * @doc.layer     Application
 * @doc.pattern   K-09 advisory; HITL override tracking; confidence-gated auto-apply
 *
 * Story: D11-013
 */
public class AiInstrumentClassificationService {

    private static final Logger log = LoggerFactory.getLogger(AiInstrumentClassificationService.class);

    /** Confidence level above which the classification is auto-applied without human review. */
    private static final double AUTO_APPLY_THRESHOLD = 0.90;

    private final ClassificationModelPort modelPort;
    private final DataSource              dataSource;
    private final Consumer<Object>        eventPublisher;
    private final Counter                 autoApplied;
    private final Counter                 pendingReview;
    private final Counter                 overrides;

    public AiInstrumentClassificationService(ClassificationModelPort modelPort,
                                              DataSource dataSource,
                                              Consumer<Object> eventPublisher,
                                              MeterRegistry meterRegistry) {
        this.modelPort     = modelPort;
        this.dataSource    = dataSource;
        this.eventPublisher = eventPublisher;
        this.autoApplied   = meterRegistry.counter("refdata.classification.auto_applied");
        this.pendingReview = meterRegistry.counter("refdata.classification.pending_review");
        this.overrides     = meterRegistry.counter("refdata.classification.overrides");
    }

    /**
     * Classifies an instrument based on its name and prospectus text.
     * If confidence ≥ AUTO_APPLY_THRESHOLD, the classification is persisted automatically.
     * Otherwise, it is saved as PENDING_REVIEW for a data manager to confirm.
     *
     * @param instrumentId   instrument identifier
     * @param instrumentName instrument name / ticker
     * @param prospectusText prospectus or description text for NLP input
     * @return classification result (may be pending review)
     */
    public ClassificationResult classify(String instrumentId, String instrumentName,
                                          String prospectusText) {
        String inputText = instrumentName + " " + prospectusText;
        ModelPrediction prediction = modelPort.classify(inputText);

        boolean autoApply = prediction.confidence() >= AUTO_APPLY_THRESHOLD;
        String status = autoApply ? "APPLIED" : "PENDING_REVIEW";

        saveClassification(instrumentId, prediction, status);

        if (autoApply) {
            autoApplied.increment();
            log.info("Classification AUTO_APPLIED instrumentId={} assetClass={} sector={} conf={}",
                    instrumentId, prediction.assetClass(), prediction.sector(), prediction.confidence());
            eventPublisher.accept(new InstrumentClassifiedEvent(instrumentId, prediction.assetClass(),
                    prediction.sector(), prediction.confidence(), true));
        } else {
            pendingReview.increment();
            log.info("Classification PENDING_REVIEW instrumentId={} conf={}", instrumentId, prediction.confidence());
            eventPublisher.accept(new InstrumentClassificationPendingEvent(instrumentId,
                    prediction.assetClass(), prediction.sector(), prediction.confidence()));
        }

        return new ClassificationResult(instrumentId, prediction.assetClass(), prediction.sector(),
                prediction.confidence(), status, prediction.shapContributions(), Instant.now());
    }

    /**
     * Records a human override of the model classification.
     * Used by data managers to correct wrong classifications and feed back to training.
     *
     * @param instrumentId        instrument to correct
     * @param overrideAssetClass  correct asset class
     * @param overrideSector      correct sector
     * @param reviewerId          data manager identifier
     * @param justification       reason for override
     */
    public void recordOverride(String instrumentId, String overrideAssetClass, String overrideSector,
                                String reviewerId, String justification) {
        String sql = "UPDATE instrument_classifications "
                   + "SET asset_class=?, sector=?, status='OVERRIDDEN', "
                   + "reviewer_id=?, justification=?, reviewed_at=? "
                   + "WHERE instrument_id=?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, overrideAssetClass);
            ps.setString(2, overrideSector);
            ps.setString(3, reviewerId);
            ps.setString(4, justification);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.setString(6, instrumentId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                log.warn("recordOverride: no classification for instrumentId={}", instrumentId);
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException("recordOverride DB error for " + instrumentId, e);
        }

        // Persist override feedback for model retraining pipeline
        String fbSql = "INSERT INTO classification_feedback(instrument_id, override_asset_class, "
                     + "override_sector, reviewer_id, justification, flagged_at) VALUES(?,?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(fbSql)) {
            ps.setString(1, instrumentId);
            ps.setString(2, overrideAssetClass);
            ps.setString(3, overrideSector);
            ps.setString(4, reviewerId);
            ps.setString(5, justification);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("recordOverride feedback DB error instrumentId={}", instrumentId, e);
        }

        overrides.increment();
        log.info("Classification OVERRIDDEN instrumentId={} assetClass={} sector={}",
                instrumentId, overrideAssetClass, overrideSector);
        eventPublisher.accept(new ClassificationOverriddenEvent(instrumentId, overrideAssetClass,
                overrideSector, reviewerId));
    }

    /**
     * Approves a pending review classification as-is (no change to prediction).
     */
    public void approve(String instrumentId, String reviewerId) {
        String sql = "UPDATE instrument_classifications SET status='APPLIED', reviewer_id=?, reviewed_at=? "
                   + "WHERE instrument_id=? AND status='PENDING_REVIEW'";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, reviewerId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, instrumentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("approve DB error instrumentId={}", instrumentId, e);
        }
        log.info("Classification APPROVED instrumentId={} reviewer={}", instrumentId, reviewerId);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private void saveClassification(String instrumentId, ModelPrediction prediction, String status) {
        String sql = "INSERT INTO instrument_classifications"
                   + "(instrument_id, asset_class, sector, confidence, status, classified_at) "
                   + "VALUES(?,?,?,?,?,?) ON CONFLICT(instrument_id) DO UPDATE "
                   + "SET asset_class=EXCLUDED.asset_class, sector=EXCLUDED.sector, "
                   + "confidence=EXCLUDED.confidence, status=EXCLUDED.status, classified_at=EXCLUDED.classified_at";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setString(2, prediction.assetClass());
            ps.setString(3, prediction.sector());
            ps.setDouble(4, prediction.confidence());
            ps.setString(5, status);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveClassification DB error instrumentId={}", instrumentId, e);
        }
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface ClassificationModelPort {
        ModelPrediction classify(String text);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record ModelPrediction(String assetClass, String sector, double confidence,
                                   Map<String, Double> shapContributions) {}

    public record ClassificationResult(String instrumentId, String assetClass, String sector,
                                        double confidence, String status,
                                        Map<String, Double> shapContributions, Instant classifiedAt) {
        public boolean isPendingReview() { return "PENDING_REVIEW".equals(status); }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record InstrumentClassifiedEvent(String instrumentId, String assetClass, String sector,
                                             double confidence, boolean autoApplied) {}
    public record InstrumentClassificationPendingEvent(String instrumentId, String assetClass,
                                                        String sector, double confidence) {}
    public record ClassificationOverriddenEvent(String instrumentId, String overrideAssetClass,
                                                 String overrideSector, String reviewerId) {}
}
