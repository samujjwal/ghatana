package com.ghatana.appplatform.ems.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Replaces static venue scoring in SorConfigurationService with a real-time
 *              ML venue opportunity model. Features (rolling 5-trade window): fill_rate,
 *              average_slippage_bps, adverse_selection_ratio, latency_percentile.
 *              Model: LightGBM classifier, output opportunity_score 0–1.
 *              Governed by K-09: SHAP explanations exposed; score advisory-only;
 *              drift triggers PENDING_REVIEW status. Updated every 5 seconds from D-04.
 * @doc.layer   Domain
 * @doc.pattern Promise.ofBlocking; inner VenueScoringModelPort (K-09 LightGBM); advisory flag
 *              persisted; HITL via recordOverride(); NO direct routing decisions made here.
 */
public class VenueToxicityScoringService {

    private static final Logger log = LoggerFactory.getLogger(VenueToxicityScoringService.class);

    private static final double TOXIC_THRESHOLD           = 0.70;  // adverse selection above this → toxic
    private static final int    ROLLING_WINDOW            = 5;     // trades window for feature computation
    private static final long   SCORE_REFRESH_INTERVAL_MS = 5_000; // 5 seconds

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final VenueScoringModelPort modelPort;
    private final Counter             scoringCounter;
    private final Counter             toxicVenueCounter;
    private final Counter             overrideCounter;

    public VenueToxicityScoringService(HikariDataSource dataSource, Executor executor,
                                       VenueScoringModelPort modelPort, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.modelPort        = modelPort;
        this.scoringCounter   = registry.counter("ems.venue.scoring.runs");
        this.toxicVenueCounter = registry.counter("ems.venue.toxic.detected");
        this.overrideCounter  = registry.counter("ems.venue.scoring.override");
    }

    // ─── Inner port (K-09 governed) ───────────────────────────────────────────

    /**
     * K-09 AI Governance port: LightGBM venue scoring model.
     * Returns advisory opportunity score and SHAP explanation map.
     */
    public interface VenueScoringModelPort {
        VenueScoreOutput score(VenueFeatureVector features);
        boolean hasModelDrift(String venueId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record VenueFeatureVector(
        String venueId,
        String instrumentSegment,
        double fillRatePct,
        double avgSlippageBps,
        double adverseSelectionRatio,  // fills followed by unfavorable move within 100ms
        double latencyPercentile90ms,
        double queuePositionEstimate
    ) {}

    public record VenueScoreOutput(
        double  opportunityScore,         // 0–1, higher = better venue
        boolean isAdvisory,               // always true (K-09)
        String  shapExplanation           // JSON: {"fill_rate": 0.3, "slippage": -0.15, ...}
    ) {}

    public record VenueScore(
        String venueId,
        String instrumentSegment,
        double opportunityScore,
        boolean isToxic,
        boolean pendingReview,
        String shapExplanation
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Score all active venues for a given instrument segment.
     * Called every {@link #SCORE_REFRESH_INTERVAL_MS} ms.
     */
    public Promise<List<VenueScore>> scoreVenues(String instrumentSegment) {
        return Promise.ofBlocking(executor, () -> {
            List<VenueFeatureVector> features = buildFeatureVectors(instrumentSegment);
            List<VenueScore>         scores   = new ArrayList<>();
            for (VenueFeatureVector fv : features) {
                VenueScore score = scoreVenue(fv);
                persistScore(score);
                scores.add(score);
                scoringCounter.increment();
            }
            return scores;
        });
    }

    /**
     * Record a human override of the model's venue assessment (K-09 HITL).
     */
    public Promise<Void> recordOverride(String venueId, String instrumentSegment,
                                        double overrideScore, String overriddenBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            persistOverride(venueId, instrumentSegment, overrideScore, overriddenBy, reason);
            overrideCounter.increment();
            log.info("K-09 override recorded: venueId={} by={} score={}", venueId, overriddenBy, overrideScore);
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private VenueScore scoreVenue(VenueFeatureVector fv) {
        boolean hasDrift    = modelPort.hasModelDrift(fv.venueId());
        VenueScoreOutput out = modelPort.score(fv);
        boolean isToxic     = fv.adverseSelectionRatio() > TOXIC_THRESHOLD;

        if (isToxic) toxicVenueCounter.increment();
        if (hasDrift) log.warn("K-09 model drift detected for venueId={}", fv.venueId());

        return new VenueScore(
            fv.venueId(),
            fv.instrumentSegment(),
            out.opportunityScore(),
            isToxic,
            hasDrift,  // pending review when drift detected
            out.shapExplanation()
        );
    }

    private List<VenueFeatureVector> buildFeatureVectors(String instrumentSegment) {
        String sql = """
            SELECT v.venue_id,
                   100.0 * COUNT(CASE WHEN f.status = 'FILLED' THEN 1 END) / NULLIF(COUNT(*), 0) AS fill_rate,
                   AVG(COALESCE(f.slippage_bps, 0))                                              AS avg_slippage_bps,
                   AVG(COALESCE(f.adverse_select_ratio, 0))                                     AS adverse_sel_ratio,
                   PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY f.latency_ms)                   AS lat_p90
            FROM order_fills f
            JOIN venue_configs v ON v.venue_id = f.venue_id
            WHERE f.instrument_segment = ?
              AND f.created_at         >= now() - INTERVAL '1 hour'
            GROUP BY v.venue_id
            """;
        List<VenueFeatureVector> result = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentSegment);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new VenueFeatureVector(
                        rs.getString("venue_id"),
                        instrumentSegment,
                        rs.getDouble("fill_rate"),
                        rs.getDouble("avg_slippage_bps"),
                        rs.getDouble("adverse_sel_ratio"),
                        rs.getDouble("lat_p90"),
                        0  // queue position requires order book data — set 0 for now
                    ));
                }
            }
        } catch (SQLException ex) {
            log.error("Failed to build venue feature vectors for segment={}", instrumentSegment, ex);
        }
        return result;
    }

    private void persistScore(VenueScore score) {
        String sql = """
            INSERT INTO venue_scores
                (score_id, venue_id, instrument_segment, opportunity_score,
                 is_toxic, pending_review, shap_explanation, scored_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (venue_id, instrument_segment) DO UPDATE
                SET opportunity_score = EXCLUDED.opportunity_score,
                    is_toxic          = EXCLUDED.is_toxic,
                    pending_review    = EXCLUDED.pending_review,
                    shap_explanation  = EXCLUDED.shap_explanation,
                    scored_at         = EXCLUDED.scored_at
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, score.venueId());
            ps.setString(3, score.instrumentSegment());
            ps.setDouble(4, score.opportunityScore());
            ps.setBoolean(5, score.isToxic());
            ps.setBoolean(6, score.pendingReview());
            ps.setString(7, score.shapExplanation());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist venue score venueId={}", score.venueId(), ex);
        }
    }

    private void persistOverride(String venueId, String segment, double score,
                                  String by, String reason) {
        String sql = """
            INSERT INTO venue_score_overrides
                (override_id, venue_id, instrument_segment, override_score,
                 overridden_by, reason, overridden_at)
            VALUES (?, ?, ?, ?, ?, ?, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, venueId);
            ps.setString(3, segment);
            ps.setDouble(4, score);
            ps.setString(5, by);
            ps.setString(6, reason);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("Failed to persist venue score override venueId={}", venueId, ex);
        }
    }
}
