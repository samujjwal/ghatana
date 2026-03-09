package com.ghatana.eventlog.adapters;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Interface for event backup operations.
 */
public interface EventBackup {
    
    /**
     * Creates a backup of events before the specified cutoff time.
     */
    void createBackup(Path backupDir, String backupName, Instant cutoff) throws IOException;
    
    /**
     * Verifies the integrity of a backup.
     */
    boolean verifyBackup(Path backupFile) throws IOException;
}
