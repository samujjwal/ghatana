package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Internal package registry managing SDK artifact publishing, semver,
 *              compatibility matrix, deprecation notices, and downstream publish triggers.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-K12-013: SDK package registry and versioning
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS sdk_packages (
 *   package_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   package_name      TEXT NOT NULL,
 *   version           TEXT NOT NULL,
 *   channel           TEXT NOT NULL,   -- alpha/beta/rc/stable
 *   artifact_url      TEXT NOT NULL,
 *   min_platform_ver  TEXT,
 *   max_platform_ver  TEXT,
 *   deprecated        BOOLEAN NOT NULL DEFAULT false,
 *   deprecation_notice TEXT,
 *   published_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (package_name, version)
 * );
 * </pre>
 */
public class SdkPackageRegistryService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface ArtifactStorePort {
        /** Upload artifact bytes and return a download URL. */
        String upload(String packageName, String version, byte[] artifact) throws Exception;
        /** Download artifact bytes for a package/version. */
        byte[] download(String packageName, String version) throws Exception;
    }

    public interface DownstreamPublishPort {
        /** Trigger a publish to an external registry (npm, PyPI, Maven). */
        void publish(String registry, String packageName, String version, String artifactUrl) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public enum Channel { ALPHA, BETA, RC, STABLE }

    public record SdkPackage(
        String packageId,
        String packageName,
        String version,
        Channel channel,
        String artifactUrl,
        String minPlatformVersion,
        String maxPlatformVersion,
        boolean deprecated,
        String deprecationNotice,
        String publishedAt
    ) {}

    public record PublishRequest(
        String packageName,
        String version,
        Channel channel,
        byte[] artifactBytes,
        String minPlatformVersion,
        String maxPlatformVersion
    ) {}

    public record CompatibilityMatrix(
        String packageName,
        List<CompatibilityRow> rows
    ) {}

    public record CompatibilityRow(
        String sdkVersion,
        String minPlatformVersion,
        String maxPlatformVersion,
        Channel channel
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ArtifactStorePort artifactStore;
    private final DownstreamPublishPort downstreamPublish;
    private final Executor executor;
    private final Counter publishCounter;
    private final Counter deprecationCounter;

    public SdkPackageRegistryService(
        javax.sql.DataSource ds,
        ArtifactStorePort artifactStore,
        DownstreamPublishPort downstreamPublish,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.artifactStore    = artifactStore;
        this.downstreamPublish = downstreamPublish;
        this.executor         = executor;
        this.publishCounter    = Counter.builder("sdk.registry.publish").register(registry);
        this.deprecationCounter = Counter.builder("sdk.registry.deprecations").register(registry);

        Gauge.builder("sdk.registry.package.count", ds, d -> {
            try (Connection c = d.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sdk_packages");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            } catch (Exception e) { return 0; }
        }).register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Publish a new SDK package version to the internal registry. */
    public Promise<SdkPackage> publish(PublishRequest req) {
        return Promise.ofBlocking(executor, () -> {
            String artifactUrl = artifactStore.upload(req.packageName(), req.version(), req.artifactBytes());

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sdk_packages (package_name, version, channel, artifact_url, min_platform_ver, max_platform_ver) " +
                     "VALUES (?,?,?,?,?,?) RETURNING package_id, published_at"
                 )) {
                ps.setString(1, req.packageName());
                ps.setString(2, req.version());
                ps.setString(3, req.channel().name());
                ps.setString(4, artifactUrl);
                ps.setString(5, req.minPlatformVersion());
                ps.setString(6, req.maxPlatformVersion());

                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String packageId = rs.getString("package_id");
                    String publishedAt = rs.getTimestamp("published_at").toString();

                    publishCounter.increment();

                    // Trigger downstream publish for stable
                    if (req.channel() == Channel.STABLE) {
                        for (String registry : List.of("npm", "pypi", "maven")) {
                            downstreamPublish.publish(registry, req.packageName(), req.version(), artifactUrl);
                        }
                    }

                    return new SdkPackage(packageId, req.packageName(), req.version(), req.channel(),
                        artifactUrl, req.minPlatformVersion(), req.maxPlatformVersion(), false, null, publishedAt);
                }
            }
        });
    }

    /** Look up a specific version. */
    public Promise<Optional<SdkPackage>> findVersion(String packageName, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM sdk_packages WHERE package_name=? AND version=?"
                 )) {
                ps.setString(1, packageName);
                ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(map(rs));
                    return Optional.empty();
                }
            }
        });
    }

    /** List all versions of a package (newest first). */
    public Promise<List<SdkPackage>> listVersions(String packageName) {
        return Promise.ofBlocking(executor, () -> {
            List<SdkPackage> result = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM sdk_packages WHERE package_name=? ORDER BY published_at DESC"
                 )) {
                ps.setString(1, packageName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(map(rs));
                }
            }
            return result;
        });
    }

    /** Deprecate a version with a migration notice. */
    public Promise<Void> deprecate(String packageName, String version, String notice) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE sdk_packages SET deprecated=true, deprecation_notice=? WHERE package_name=? AND version=?"
                 )) {
                ps.setString(1, notice);
                ps.setString(2, packageName);
                ps.setString(3, version);
                ps.executeUpdate();
                deprecationCounter.increment();
                return null;
            }
        });
    }

    /** Return the compatibility matrix for a package. */
    public Promise<CompatibilityMatrix> getCompatibilityMatrix(String packageName) {
        return Promise.ofBlocking(executor, () -> {
            List<SdkPackage> versions = (List<SdkPackage>) listVersions(packageName).get();
            List<CompatibilityRow> rows = versions.stream()
                .map(p -> new CompatibilityRow(p.version(), p.minPlatformVersion(), p.maxPlatformVersion(), p.channel()))
                .toList();
            return new CompatibilityMatrix(packageName, rows);
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private SdkPackage map(ResultSet rs) throws SQLException {
        return new SdkPackage(
            rs.getString("package_id"),
            rs.getString("package_name"),
            rs.getString("version"),
            Channel.valueOf(rs.getString("channel")),
            rs.getString("artifact_url"),
            rs.getString("min_platform_ver"),
            rs.getString("max_platform_ver"),
            rs.getBoolean("deprecated"),
            rs.getString("deprecation_notice"),
            rs.getTimestamp("published_at").toString()
        );
    }
}
