package com.ghatana.eventlog.config;

import java.nio.file.Path;
import java.time.Duration;

public class RetentionConfig {
    private Duration period = Duration.ofDays(30);
    private Path backupDir = Path.of("backups");
    
    // Getters and setters
    public Duration getPeriod() {
        return period;
    }

    public void setPeriod(Duration period) {
        this.period = period;
    }

    public Path getBackupDir() {
        return backupDir;
    }

    public void setBackupDir(Path backupDir) {
        this.backupDir = backupDir;
    }
}
