package com.ghatana.audit.integrity.adapters.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.audit.integrity.domain.AuditBatch;
import com.ghatana.audit.integrity.ports.AuditBatchRepository;
import com.ghatana.platform.domain.domain.audit.AuditEntry;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of AuditBatchRepository
 * Day 42: Audit Integrity - JDBC Adapter
 */
@Slf4j
public class JdbcAuditBatchRepository implements AuditBatchRepository {
    
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    // SQL queries
    private static final String INSERT_BATCH = """
        INSERT INTO audit_batches (
            batch_id, created_at, start_sequence, end_sequence, 
            previous_batch_hash, batch_hash, entries_json, 
            hash_algorithm, verification_status, entry_count
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    
    private static final String SELECT_BY_ID = """
        SELECT batch_id, created_at, start_sequence, end_sequence, 
               previous_batch_hash, batch_hash, entries_json, 
               hash_algorithm, verification_status, entry_count
        FROM audit_batches WHERE batch_id = ?
        """;
    
    private static final String SELECT_BY_TIME_RANGE = """
        SELECT batch_id, created_at, start_sequence, end_sequence, 
               previous_batch_hash, batch_hash, entries_json, 
               hash_algorithm, verification_status, entry_count
        FROM audit_batches 
        WHERE created_at >= ? AND created_at <= ?
        ORDER BY start_sequence ASC
        """;
    
    private static final String SELECT_BY_SEQUENCE_RANGE = """
        SELECT batch_id, created_at, start_sequence, end_sequence, 
               previous_batch_hash, batch_hash, entries_json, 
               hash_algorithm, verification_status, entry_count
        FROM audit_batches 
        WHERE start_sequence >= ? AND end_sequence <= ?
        ORDER BY start_sequence ASC
        """;
    
    private static final String SELECT_LATEST = """
        SELECT batch_id, created_at, start_sequence, end_sequence, 
               previous_batch_hash, batch_hash, entries_json, 
               hash_algorithm, verification_status, entry_count
        FROM audit_batches 
        ORDER BY end_sequence DESC LIMIT 1
        """;
    
    private static final String SELECT_GENESIS = """
        SELECT batch_id, created_at, start_sequence, end_sequence, 
               previous_batch_hash, batch_hash, entries_json, 
               hash_algorithm, verification_status, entry_count
        FROM audit_batches 
        WHERE previous_batch_hash IS NULL OR previous_batch_hash = ''
        ORDER BY start_sequence ASC LIMIT 1
        """;
    
    private static final String COUNT_BATCHES = "SELECT COUNT(*) FROM audit_batches";
    
    private static final String DELETE_OLD_BATCHES = "DELETE FROM audit_batches WHERE created_at < ?";
    
    public JdbcAuditBatchRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public AuditBatch save(AuditBatch batch) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_BATCH)) {
            
            stmt.setString(1, batch.getBatchId());
            stmt.setTimestamp(2, Timestamp.from(batch.getCreatedAt()));
            stmt.setLong(3, batch.getStartSequence());
            stmt.setLong(4, batch.getEndSequence());
            stmt.setString(5, batch.getPreviousBatchHash());
            stmt.setString(6, batch.getBatchHash());
            stmt.setString(7, serializeEntries(batch.getEntries()));
            stmt.setString(8, batch.getHashAlgorithm());
            stmt.setString(9, batch.getVerificationStatus().name());
            stmt.setInt(10, batch.getEntryCount());
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new RuntimeException("Failed to insert audit batch: " + batch.getBatchId());
            }
            
            log.debug("Saved audit batch {} with {} entries", batch.getBatchId(), batch.getEntryCount());
            return batch;
            
        } catch (SQLException e) {
            log.error("Error saving audit batch {}", batch.getBatchId(), e);
            throw new RuntimeException("Failed to save audit batch", e);
        }
    }
    
    @Override
    public Optional<AuditBatch> findById(String batchId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_ID)) {
            
            stmt.setString(1, batchId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToBatch(rs));
                }
                return Optional.empty();
            }
            
        } catch (SQLException e) {
            log.error("Error finding audit batch by ID {}", batchId, e);
            throw new RuntimeException("Failed to find audit batch", e);
        }
    }
    
    @Override
    public List<AuditBatch> findBatchesByTimeRange(Instant startTime, Instant endTime) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_TIME_RANGE)) {
            
            stmt.setTimestamp(1, Timestamp.from(startTime));
            stmt.setTimestamp(2, Timestamp.from(endTime));
            
            List<AuditBatch> batches = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapResultSetToBatch(rs));
                }
            }
            
            log.debug("Found {} audit batches in time range {} to {}", batches.size(), startTime, endTime);
            return batches;
            
        } catch (SQLException e) {
            log.error("Error finding audit batches by time range {} to {}", startTime, endTime, e);
            throw new RuntimeException("Failed to find audit batches by time range", e);
        }
    }
    
    @Override
    public List<AuditBatch> findBatchesBySequenceRange(long startSequence, long endSequence) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_BY_SEQUENCE_RANGE)) {
            
            stmt.setLong(1, startSequence);
            stmt.setLong(2, endSequence);
            
            List<AuditBatch> batches = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    batches.add(mapResultSetToBatch(rs));
                }
            }
            
            return batches;
            
        } catch (SQLException e) {
            log.error("Error finding audit batches by sequence range {} to {}", startSequence, endSequence, e);
            throw new RuntimeException("Failed to find audit batches by sequence range", e);
        }
    }
    
    @Override
    public Optional<AuditBatch> findLatestBatch() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_LATEST);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return Optional.of(mapResultSetToBatch(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Error finding latest audit batch", e);
            throw new RuntimeException("Failed to find latest audit batch", e);
        }
    }
    
    @Override
    public Optional<AuditBatch> findGenesisBatch() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_GENESIS);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return Optional.of(mapResultSetToBatch(rs));
            }
            return Optional.empty();
            
        } catch (SQLException e) {
            log.error("Error finding genesis audit batch", e);
            throw new RuntimeException("Failed to find genesis audit batch", e);
        }
    }
    
    @Override
    public long getTotalBatchCount() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(COUNT_BATCHES);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
            
        } catch (SQLException e) {
            log.error("Error counting audit batches", e);
            throw new RuntimeException("Failed to count audit batches", e);
        }
    }
    
    @Override
    public int deleteBatchesOlderThan(Instant cutoffTime) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_OLD_BATCHES)) {
            
            stmt.setTimestamp(1, Timestamp.from(cutoffTime));
            
            int deletedCount = stmt.executeUpdate();
            log.info("Deleted {} audit batches older than {}", deletedCount, cutoffTime);
            return deletedCount;
            
        } catch (SQLException e) {
            log.error("Error deleting old audit batches", e);
            throw new RuntimeException("Failed to delete old audit batches", e);
        }
    }
    
    private AuditBatch mapResultSetToBatch(ResultSet rs) throws SQLException {
        return AuditBatch.builder()
                .batchId(rs.getString("batch_id"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .startSequence(rs.getLong("start_sequence"))
                .endSequence(rs.getLong("end_sequence"))
                .previousBatchHash(rs.getString("previous_batch_hash"))
                .batchHash(rs.getString("batch_hash"))
                .entries(deserializeEntries(rs.getString("entries_json")))
                .hashAlgorithm(rs.getString("hash_algorithm"))
                .verificationStatus(AuditBatch.VerificationStatus.valueOf(rs.getString("verification_status")))
                .build();
    }
    
    private String serializeEntries(List<AuditEntry> entries) {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit entries", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<AuditEntry> deserializeEntries(String entriesJson) {
        try {
            return objectMapper.readValue(entriesJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AuditEntry.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize audit entries", e);
        }
    }
}