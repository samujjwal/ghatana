package com.ghatana.appplatform.eventstore.consumer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC-backed consumer checkpoint store using the {@code consumer_checkpoints} table
 * created by V006__idempotency_keys.sql.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL consumer offset checkpoint adapter (STORY-K05-011)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class PostgresConsumerCheckpoint implements ConsumerCheckpoint {

    private final DataSource dataSource;

    public PostgresConsumerCheckpoint(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(String groupId, String topic, int partition, long offset) {
        String sql = """
            INSERT INTO consumer_checkpoints (group_id, topic, partition, committed_offset)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (group_id, topic, partition)
            DO UPDATE SET committed_offset = EXCLUDED.committed_offset,
                          updated_at = NOW()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, topic);
            ps.setInt(3, partition);
            ps.setLong(4, offset);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save checkpoint for group=" + groupId
                + " topic=" + topic + " partition=" + partition, e);
        }
    }

    @Override
    public long load(String groupId, String topic, int partition) {
        String sql = """
            SELECT committed_offset FROM consumer_checkpoints
             WHERE group_id = ? AND topic = ? AND partition = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, groupId);
            ps.setString(2, topic);
            ps.setInt(3, partition);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("committed_offset") : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load checkpoint for group=" + groupId
                + " topic=" + topic + " partition=" + partition, e);
        }
    }
}
