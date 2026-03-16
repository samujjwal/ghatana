package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Stores ML model artifacts (serialized model files, feature definitions,
 *              hyperparameters) in S3/MinIO via StoragePort. Artifact integrity enforced
 *              via SHA-256 hash on upload, verified on download. Hash mismatch throws
 *              ArtifactIntegrityException. Artifact linked to ModelRecord version.
 *              Satisfies STORY-K09-002.
 * @doc.layer   Kernel
 * @doc.pattern SHA-256 integrity; S3/MinIO StoragePort; artifact-to-model-version link;
 *              ON CONFLICT DO NOTHING; Counter + Timer.
 */
public class ModelArtifactStorageService {

    private static final String BUCKET = "model-artifacts";

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final StoragePort      storagePort;
    private final Counter          uploadsCounter;
    private final Counter          integrityFailuresCounter;
    private final Timer            uploadTimer;

    public ModelArtifactStorageService(HikariDataSource dataSource, Executor executor,
                                        StoragePort storagePort, MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.storagePort             = storagePort;
        this.uploadsCounter          = Counter.builder("ai.artifacts.uploads_total").register(registry);
        this.integrityFailuresCounter = Counter.builder("ai.artifacts.integrity_failures_total").register(registry);
        this.uploadTimer             = Timer.builder("ai.artifacts.upload_duration").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface StoragePort {
        String store(String bucket, String key, byte[] content, String contentType);
        byte[] retrieve(String bucket, String key);
    }

    // ─── Exceptions ──────────────────────────────────────────────────────────

    public static class ArtifactIntegrityException extends RuntimeException {
        public ArtifactIntegrityException(String msg) { super(msg); }
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record ArtifactRecord(String artifactId, String modelId, String artifactType,
                                  String storageKey, String contentHash, long sizeBytes,
                                  LocalDateTime uploadedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ArtifactRecord> uploadArtifact(String modelId, String artifactType,
                                                   byte[] content, String contentType) {
        return Promise.ofBlocking(executor, () -> uploadTimer.recordCallable(() -> {
            String hash = sha256Hex(content);
            String artifactId = UUID.randomUUID().toString();
            String storageKey = "models/" + modelId + "/" + artifactType + "/" + artifactId;

            storagePort.store(BUCKET, storageKey, content, contentType);

            ArtifactRecord record = persistArtifact(artifactId, modelId, artifactType,
                    storageKey, hash, content.length);
            uploadsCounter.increment();
            return record;
        }));
    }

    public Promise<byte[]> downloadArtifact(String artifactId) {
        return Promise.ofBlocking(executor, () -> {
            ArtifactRecord record = loadArtifact(artifactId);
            byte[] content = storagePort.retrieve(BUCKET, record.storageKey());
            String actualHash = sha256Hex(content);
            if (!actualHash.equals(record.contentHash())) {
                integrityFailuresCounter.increment();
                throw new ArtifactIntegrityException(
                        "SHA-256 mismatch for artifact " + artifactId
                                + ": expected=" + record.contentHash() + " actual=" + actualHash);
            }
            return content;
        });
    }

    public Promise<ArtifactRecord> getArtifactRecord(String artifactId) {
        return Promise.ofBlocking(executor, () -> loadArtifact(artifactId));
    }

    // ─── Hash computation ─────────────────────────────────────────────────────

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private ArtifactRecord persistArtifact(String artifactId, String modelId, String artifactType,
                                            String storageKey, String hash,
                                            long sizeBytes) throws SQLException {
        String sql = """
                INSERT INTO model_artifacts
                    (artifact_id, model_id, artifact_type, storage_key, content_hash,
                     size_bytes, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (model_id, artifact_type) DO UPDATE
                    SET storage_key=EXCLUDED.storage_key, content_hash=EXCLUDED.content_hash,
                        size_bytes=EXCLUDED.size_bytes, uploaded_at=NOW()
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, artifactId); ps.setString(2, modelId);
            ps.setString(3, artifactType); ps.setString(4, storageKey);
            ps.setString(5, hash); ps.setLong(6, sizeBytes);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); return mapRow(rs);
            }
        }
    }

    private ArtifactRecord loadArtifact(String artifactId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM model_artifacts WHERE artifact_id=?")) {
            ps.setString(1, artifactId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Artifact not found: " + artifactId);
                return mapRow(rs);
            }
        }
    }

    private ArtifactRecord mapRow(ResultSet rs) throws SQLException {
        return new ArtifactRecord(rs.getString("artifact_id"), rs.getString("model_id"),
                rs.getString("artifact_type"), rs.getString("storage_key"),
                rs.getString("content_hash"), rs.getLong("size_bytes"),
                rs.getObject("uploaded_at", LocalDateTime.class));
    }
}
