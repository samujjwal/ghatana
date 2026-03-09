package com.ghatana.pipeline.registry.config;

import com.ghatana.platform.security.session.SessionManager;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ActiveJ module for session cleanup scheduling.
 *
 * <p>Purpose: Configures and provides a scheduled executor service that
 * periodically removes expired sessions from Redis. Ensures session
 * data hygiene and prevents unbounded memory growth.</p>
 *
 * @doc.type class
 * @doc.purpose Schedules periodic cleanup of expired user sessions
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class SessionCleanupConfig extends AbstractModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupConfig.class);
    
    @Provides
    ScheduledExecutorService sessionCleanupScheduler(SessionManager sessionManager) {
        LOG.info("Configuring session cleanup scheduler");
        
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "session-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        
        // Schedule cleanup task to run every 15 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                LOG.debug("Running session cleanup task");
                sessionManager.deleteExpiredSessions()
                    .whenResult(count -> LOG.info("Cleaned up {} expired sessions", count))
                    .whenException(e -> LOG.error("Error cleaning up expired sessions", e));
            } catch (Exception e) {
                LOG.error("Unexpected error in session cleanup task", e);
            }
        }, 15, 15, TimeUnit.MINUTES);
        
        // Add shutdown hook to clean up scheduler
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down session cleanup scheduler");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
        
        return scheduler;
    }
}
