package com.ghatana.eventlog.adapters.jdbc;

import com.ghatana.eventlog.adapters.EventBackup;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

/**
 * JDBC implementation of event backup using database exports.
 */
public class JdbcEventBackup implements EventBackup {
    
    private final DataSource dataSource;
    
    public JdbcEventBackup(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public void createBackup(Path backupDir, String backupName, Instant cutoff) throws IOException {
        Path backupFile = backupDir.resolve(backupName + ".sql");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM event_log WHERE timestamp < ?");
             var writer = Files.newBufferedWriter(backupFile)) {
            
            stmt.setObject(1, cutoff);
            ResultSet rs = stmt.executeQuery();
            
            // Write SQL insert statements
            while (rs.next()) {
                writer.write(String.format(
                    "INSERT INTO event_log VALUES(%d, '%s', '%s', '%s');\n",
                    rs.getLong("id"),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    rs.getTimestamp("timestamp")
                ));
            }
        } catch (Exception e) {
            throw new IOException("Failed to create JDBC backup", e);
        }
    }

    @Override
    public boolean verifyBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile) || Files.size(backupFile) == 0) {
            return false;
        }
        // Verify the backup file contains valid SQL INSERT statements
        // and count the number of records for integrity check
        long insertCount = 0;
        try (var reader = Files.newBufferedReader(backupFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("INSERT INTO event_log VALUES(")) {
                    insertCount++;
                } else if (!line.isBlank()) {
                    // Unexpected line format — backup may be corrupted
                    return false;
                }
            }
        }
        return insertCount > 0;
    }
}
