package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    DomainService
 * @doc.purpose Detects statistical drift in ML model input features by comparing the
 *              current production distribution against the training reference distribution.
 *              Computes Population Stability Index (PSI), Kolmogorov-Smirnov (KS) test
 *              statistic, and Jensen-Shannon divergence (JSD). PSI > 0.2 triggers a
 *              FeatureDriftDetected event. Results persisted daily per model+feature.
 *              Satisfies STORY-K09-009.
 * @doc.layer   Kernel
 * @doc.pattern Statistical drift detection; PSI/KS/JSD; EventPort alerts; daily
 *              ON CONFLICT DO UPDATE upsert; drifted-features Gauge.
 */
public class FeatureDriftDetectionService {

    private static final double PSI_THRESHOLD = 0.2;
    private static final double KS_ALERT_THRESHOLD = 0.15;
    private static final double JSD_ALERT_THRESHOLD = 0.1;
    private static final int    NUM_BINS = 10;

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          driftEventsCounter;
    private final AtomicInteger    driftedFeaturesCount = new AtomicInteger(0);

    public FeatureDriftDetectionService(HikariDataSource dataSource, Executor executor,
                                         EventPort eventPort, MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.eventPort         = eventPort;
        this.driftEventsCounter = Counter.builder("ai.drift.events_total").register(registry);
        Gauge.builder("ai.drift.drifted_features", driftedFeaturesCount, AtomicInteger::get)
             .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record DriftResult(String modelId, String featureName, double psi,
                               double ksStat, double jsd, boolean isDrifted,
                               LocalDateTime detectedAt) {}

    public record ReferenceDistribution(String modelId, String featureName,
                                         double[] binEdges, double[] frequencies) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Void> registerReference(ReferenceDistribution ref) {
        return Promise.ofBlocking(executor, () -> {
            persistReference(ref);
            return null;
        });
    }

    public Promise<List<DriftResult>> detectDrift(String modelId,
                                                    Map<String, double[]> currentValues) {
        return Promise.ofBlocking(executor, () -> {
            List<ReferenceDistribution> refs = loadReferences(modelId);
            List<DriftResult> results = new ArrayList<>();

            for (ReferenceDistribution ref : refs) {
                double[] current = currentValues.get(ref.featureName());
                if (current == null || current.length == 0) continue;

                double[] currentFreq = histogramFrequencies(current, ref.binEdges());
                double psi   = computePsi(ref.frequencies(), currentFreq);
                double ks    = computeKs(ref.frequencies(), currentFreq);
                double jsd   = computeJsd(ref.frequencies(), currentFreq);
                boolean drift = psi > PSI_THRESHOLD || ks > KS_ALERT_THRESHOLD
                                || jsd > JSD_ALERT_THRESHOLD;

                DriftResult result = new DriftResult(modelId, ref.featureName(),
                        psi, ks, jsd, drift, LocalDateTime.now());
                persistDriftResult(result);
                results.add(result);

                if (drift) {
                    driftEventsCounter.increment();
                    driftedFeaturesCount.incrementAndGet();
                    eventPort.publish("model-governance", "FeatureDriftDetected",
                            Map.of("modelId", modelId, "feature", ref.featureName(),
                                   "psi", psi, "ks", ks, "jsd", jsd));
                }
            }
            return results;
        });
    }

    public Promise<List<DriftResult>> getDriftHistory(String modelId, String featureName,
                                                       LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> {
            List<DriftResult> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM feature_drift_results " +
                         "WHERE model_id=? AND feature_name=? " +
                         "AND detected_at BETWEEN ? AND ? ORDER BY detected_at")) {
                ps.setString(1, modelId); ps.setString(2, featureName);
                ps.setDate(3, Date.valueOf(from)); ps.setDate(4, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    // ─── Statistical computations ─────────────────────────────────────────────

    /** Population Stability Index */
    private double computePsi(double[] reference, double[] current) {
        double psi = 0.0;
        for (int i = 0; i < reference.length; i++) {
            double r = Math.max(reference[i], 1e-10);
            double c = Math.max(current[i], 1e-10);
            psi += (c - r) * Math.log(c / r);
        }
        return psi;
    }

    /** Kolmogorov-Smirnov maximum CDF difference */
    private double computeKs(double[] reference, double[] current) {
        double refCum = 0.0, curCum = 0.0, maxDiff = 0.0;
        for (int i = 0; i < reference.length; i++) {
            refCum += reference[i]; curCum += current[i];
            maxDiff = Math.max(maxDiff, Math.abs(refCum - curCum));
        }
        return maxDiff;
    }

    /** Jensen-Shannon divergence (square of JS distance) */
    private double computeJsd(double[] p, double[] q) {
        double jsd = 0.0;
        for (int i = 0; i < p.length; i++) {
            double pi = Math.max(p[i], 1e-10);
            double qi = Math.max(q[i], 1e-10);
            double m = (pi + qi) / 2.0;
            jsd += 0.5 * pi * Math.log(pi / m) + 0.5 * qi * Math.log(qi / m);
        }
        return jsd;
    }

    /** Bin continuous values into NUM_BINS and return relative frequencies. */
    private double[] histogramFrequencies(double[] values, double[] binEdges) {
        int bins = binEdges.length - 1;
        double[] freq = new double[bins];
        for (double v : values) {
            for (int i = 0; i < bins; i++) {
                if (v >= binEdges[i] && (v < binEdges[i + 1]
                        || (i == bins - 1 && v <= binEdges[i + 1]))) {
                    freq[i]++;
                    break;
                }
            }
        }
        for (int i = 0; i < bins; i++) freq[i] /= values.length;
        return freq;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persistReference(ReferenceDistribution ref) throws SQLException {
        String sql = """
                INSERT INTO feature_reference_distributions
                    (model_id, feature_name, bin_edges, frequencies, created_at)
                VALUES (?, ?, ?::double precision[], ?::double precision[], NOW())
                ON CONFLICT (model_id, feature_name) DO UPDATE
                    SET bin_edges=EXCLUDED.bin_edges, frequencies=EXCLUDED.frequencies
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ref.modelId()); ps.setString(2, ref.featureName());
            ps.setString(3, toSqlArray(ref.binEdges()));
            ps.setString(4, toSqlArray(ref.frequencies()));
            ps.executeUpdate();
        }
    }

    private void persistDriftResult(DriftResult r) throws SQLException {
        String sql = """
                INSERT INTO feature_drift_results
                    (model_id, feature_name, psi, ks_stat, jsd, is_drifted, detected_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (model_id, feature_name, (detected_at::date)) DO UPDATE
                    SET psi=EXCLUDED.psi, ks_stat=EXCLUDED.ks_stat,
                        jsd=EXCLUDED.jsd, is_drifted=EXCLUDED.is_drifted
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.modelId()); ps.setString(2, r.featureName());
            ps.setDouble(3, r.psi()); ps.setDouble(4, r.ksStat());
            ps.setDouble(5, r.jsd()); ps.setBoolean(6, r.isDrifted());
            ps.executeUpdate();
        }
    }

    private List<ReferenceDistribution> loadReferences(String modelId) throws SQLException {
        List<ReferenceDistribution> list = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM feature_reference_distributions WHERE model_id=?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ReferenceDistribution(rs.getString("model_id"),
                            rs.getString("feature_name"),
                            parseDoubleArray(rs.getString("bin_edges")),
                            parseDoubleArray(rs.getString("frequencies"))));
                }
            }
        }
        return list;
    }

    private DriftResult mapRow(ResultSet rs) throws SQLException {
        return new DriftResult(rs.getString("model_id"), rs.getString("feature_name"),
                rs.getDouble("psi"), rs.getDouble("ks_stat"), rs.getDouble("jsd"),
                rs.getBoolean("is_drifted"),
                rs.getObject("detected_at", LocalDateTime.class));
    }

    private String toSqlArray(double[] arr) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < arr.length; i++) { if (i > 0) sb.append(','); sb.append(arr[i]); }
        sb.append('}'); return sb.toString();
    }

    private double[] parseDoubleArray(String s) {
        if (s == null) return new double[0];
        String[] parts = s.replaceAll("[{}]", "").split(",");
        double[] result = new double[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Double.parseDouble(parts[i].strip());
        return result;
    }
}
