package com.ghatana.ai.vectorstore;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL pgvector adapter for VectorStore interface.
 * 
 * Requires the pgvector extension to be installed in PostgreSQL.
 * Table schema:
 * <pre>
 * CREATE TABLE vectors (
 *     id TEXT PRIMARY KEY,
 *     content TEXT NOT NULL,
 *     vector vector(1536),
 *     metadata JSONB,
 *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * );
 * CREATE INDEX ON vectors USING ivfflat (vector vector_cosine_ops);
 * </pre>
 * 
 * @doc.type class
 * @doc.purpose Provides PostgreSQL pgvector-based implementation of the VectorStore interface.
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public class PgVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);
    
    /** Only allows alphanumeric characters, underscores, and dots in identifiers. */
    private static final java.util.regex.Pattern SAFE_IDENTIFIER =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]{0,127}$");

    private final DataSource dataSource;
    private final MetricsCollector metricsCollector;
    private final String tableName;
    private final int vectorDimension;
    private static final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    
    /**
     * Creates a new PgVectorStore instance.
     *
     * @param dataSource The JDBC DataSource
     * @param metricsCollector The metrics collector for monitoring
     * @param tableName The name of the vectors table
     * @param vectorDimension The dimension of vectors (e.g., 1536 for OpenAI embeddings)
     */
    public PgVectorStore(
            DataSource dataSource,
            MetricsCollector metricsCollector,
            String tableName,
            int vectorDimension) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        this.tableName = validateTableName(Objects.requireNonNull(tableName, "tableName cannot be null"));
        this.vectorDimension = vectorDimension;
        
        if (vectorDimension <= 0) {
            throw new IllegalArgumentException("vectorDimension must be positive");
        }
    }
    
    @Override
    public Promise<Void> store(
            String id,
            String content,
            float[] vector,
            Map<String, String> metadata) {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            final long startTime = System.currentTimeMillis();
            
            try {
                if (vector.length != vectorDimension) {
                    throw new IllegalArgumentException(
                            String.format("vector dimension mismatch: expected %d, got %d",
                                    vectorDimension, vector.length)
                    );
                }
                
                String sql = String.format(
                        "INSERT INTO %s (id, content, vector, metadata) VALUES (?, ?, ?::vector, ?::jsonb) " +
                        "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, vector = EXCLUDED.vector, metadata = EXCLUDED.metadata",
                        tableName
                );
                
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    
                    stmt.setString(1, id);
                    stmt.setString(2, content);
                    stmt.setString(3, vectorToString(vector));
                    stmt.setString(4, metadataToJson(metadata));
                    
                    stmt.executeUpdate();
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordTimer("ai.vectorstore.store_latency", duration);
                metricsCollector.incrementCounter("ai.vectorstore.store_operations", "store", tableName);
                
                log.debug("Stored vector {} (took {} ms)", id, duration);
                return null;
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.store_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error storing vector {}: {}", id, e.getMessage(), e);
                throw e;
            }
        });
    }
    
    @Override
    public Promise<List<VectorSearchResult>> search(
            float[] queryVector,
            int limit,
            double threshold) {
        return search(queryVector, limit, threshold, Collections.emptyMap());
    }

    @Override
    public Promise<List<VectorSearchResult>> search(
            float[] queryVector,
            int limit,
            double threshold,
            Map<String, String> filterMetadata) {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            final long startTime = System.currentTimeMillis();
            
            try {
                if (queryVector.length != vectorDimension) {
                    throw new IllegalArgumentException(
                            String.format("vector dimension mismatch: expected %d, got %d",
                                    vectorDimension, queryVector.length)
                    );
                }
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append(String.format(
                        "SELECT id, content, vector, metadata, 1 - (vector <=> ?::vector) as similarity " +
                        "FROM %s WHERE 1 - (vector <=> ?::vector) >= ? ",
                        tableName
                ));

                if (filterMetadata != null && !filterMetadata.isEmpty()) {
                    sqlBuilder.append("AND metadata @> ?::jsonb ");
                }

                sqlBuilder.append("ORDER BY similarity DESC LIMIT ?");
                
                List<VectorSearchResult> results = new ArrayList<>();
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                    
                    String vectorStr = vectorToString(queryVector);
                    int paramIndex = 1;
                    stmt.setString(paramIndex++, vectorStr);
                    stmt.setString(paramIndex++, vectorStr);
                    stmt.setDouble(paramIndex++, threshold);
                    
                    if (filterMetadata != null && !filterMetadata.isEmpty()) {
                        stmt.setString(paramIndex++, metadataToJson(filterMetadata));
                    }
                    
                    stmt.setInt(paramIndex++, limit);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        int rank = 0;
                        while (rs.next()) {
                            results.add(new VectorSearchResult(
                                    rs.getString("id"),
                                    rs.getString("content"),
                                    stringToVector(rs.getString("vector")),
                                    rs.getDouble("similarity"),
                                    rank++,
                                    jsonToMetadata(rs.getString("metadata"))
                            ));
                        }
                    }
                }
                
                long duration = System.currentTimeMillis() - startTime;
                metricsCollector.recordTimer("ai.vectorstore.search_latency", duration);
                metricsCollector.incrementCounter("ai.vectorstore.search_operations", "store", tableName);
                
                log.debug("Found {} results in {} ms", results.size(), duration);
                return results;
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.search_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error searching vectors: {}", e.getMessage(), e);
                throw e;
            }
        });
    }
    
    @Override
    public Promise<List<VectorSearchResult>> searchById(
            String queryId,
            int limit,
            double threshold) {
        return getById(queryId)
                .then(result -> search(result.getVector(), limit, threshold));
    }
    
    @Override
    public Promise<VectorSearchResult> getById(String id) {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            String sql = String.format(
                    "SELECT id, content, vector, metadata FROM %s WHERE id = ?",
                    tableName
            );
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new VectorSearchResult(
                                rs.getString("id"),
                                rs.getString("content"),
                                stringToVector(rs.getString("vector")),
                                1.0,  // exact match
                                0,    // rank 0 for exact match
                                jsonToMetadata(rs.getString("metadata"))
                        );
                    }
                }
            }
            
            throw new IllegalArgumentException("Vector not found: " + id);
        });
    }
    
    @Override
    public Promise<Void> delete(String id) {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            String sql = String.format("DELETE FROM %s WHERE id = ?", tableName);
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                stmt.executeUpdate();
                
                metricsCollector.incrementCounter("ai.vectorstore.delete_operations", "store", tableName);
                log.debug("Deleted vector {}", id);
                return null;
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.delete_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error deleting vector {}: {}", id, e.getMessage(), e);
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Void> clear() {
        log.warn("DESTRUCTIVE OPERATION: clear() called on vector store '{}'. "
                + "This will TRUNCATE all embeddings across ALL tenants.", tableName);

        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            // tableName is pre-validated in the constructor via SAFE_IDENTIFIER regex,
            // so it is safe to use in a DDL statement where parameterized queries
            // are not supported.
            String sql = "TRUNCATE TABLE " + tableName;
            
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.execute(sql);
                
                metricsCollector.incrementCounter("ai.vectorstore.clear_operations", "store", tableName);
                log.warn("DESTRUCTIVE OPERATION COMPLETED: Cleared all vectors from '{}'", tableName);
                return null;
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.clear_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error clearing vectors: {}", e.getMessage(), e);
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Long> count() {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            String sql = String.format("SELECT COUNT(*) as count FROM %s", tableName);
            
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                if (rs.next()) {
                    return rs.getLong("count");
                }
                return 0L;
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.count_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error counting vectors: {}", e.getMessage(), e);
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Boolean> exists(String id) {
        return Promise.ofBlocking(new java.util.concurrent.ForkJoinPool(), () -> {
            String sql = String.format("SELECT EXISTS(SELECT 1 FROM %s WHERE id = ?)", tableName);
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, id);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean(1);
                    }
                    return false;
                }
                
            } catch (Exception e) {
                metricsCollector.incrementCounter("ai.vectorstore.exists_errors", "store", tableName, "error", e.getClass().getSimpleName());
                log.error("Error checking vector existence: {}", e.getMessage(), e);
                throw e;
            }
        });
    }
    
    // Helper methods
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
    
    private float[] stringToVector(String str) {
        String cleaned = str.replaceAll("[\\[\\]]", "");
        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
    
    private String metadataToJson(Map<String, String> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata != null ? metadata : Collections.emptyMap());
        } catch (Exception e) {
            log.error("Error converting metadata to JSON", e);
            return "{}";
        }
    }

    private Map<String, String> jsonToMetadata(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Error converting JSON to metadata", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Validates that a table name matches the safe identifier pattern.
     * Prevents SQL injection via table name manipulation.
     *
     * @param name candidate table name
     * @return the same name if valid
     * @throws IllegalArgumentException if the name contains disallowed characters
     */
    private static String validateTableName(String name) {
        if (!SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Unsafe table name: '" + name + "'. "
                + "Must match [a-zA-Z_][a-zA-Z0-9_.]{0,127}");
        }
        return name;
    }
}
