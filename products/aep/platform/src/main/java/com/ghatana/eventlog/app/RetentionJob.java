package com.ghatana.eventlog.app;

import com.ghatana.eventlog.adapters.EventBackup;
import com.ghatana.eventlog.adapters.EventLogRepository;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Handles event retention and purging based on retention policies.
 */
public class RetentionJob {
    
    private final EventLogRepository repository;
    private final EventBackup backupService;
    private final Path backupDir;
    private final MetricsCollector metrics;
    
    public RetentionJob(EventLogRepository repository, EventBackup backupService, Path backupDir, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.backupService = backupService;
        this.backupDir = backupDir;
        this.metrics = MetricsCollectorFactory.create(meterRegistry);
    }
    
    /**
     * Purges events older than the specified cutoff time.
     */
    public void purgeOldEvents(Instant cutoff) {
        List<Event> events = repository.getEventsBefore(cutoff);
        repository.purgeEventsBefore(cutoff);
        // Note: incrementCounter doesn't support increment(value) yet, so we call it events.size() times
        for (int i = 0; i < events.size(); i++) {
            metrics.incrementCounter("eventlog.retention.purged", "cutoff", cutoff.toString());
        }
    }
    
    /**
     * Runs the full retention job including purging and backups.
     */
    public void run() {
        // Default retention period: 30 days
        Instant cutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        
        // Step 1: Purge old events
        purgeOldEvents(cutoff);
        
        // Step 2: Create backup
        try {
            String backupName = "backup_" + Instant.now().toString().replace(":", "-");
            backupService.createBackup(backupDir, backupName, cutoff);
            
            // Step 3: Verify backup
            if (!backupService.verifyBackup(backupDir.resolve(backupName + ".zip"))) {
                metrics.incrementCounter("eventlog.retention.backup_failed");
            }
        } catch (Exception e) {
            metrics.incrementCounter("eventlog.retention.backup_error");
        }
    }
}
