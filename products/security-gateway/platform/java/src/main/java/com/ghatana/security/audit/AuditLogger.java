package com.ghatana.security.audit;

import com.ghatana.platform.audit.AuditEvent;
import io.activej.async.service.ReactiveService;
import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service responsible for logging audit events asynchronously.
 * <p>
 * This service ensures that audit events are processed in a non-blocking manner
 * using a dedicated single-threaded executor. It implements {@link ReactiveService}
 * for proper lifecycle management within the ActiveJ framework.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create repository implementation
 * AuditEventRepository repository = event -> {
 *     // Save event to storage
 * };
 * 
 * // Create and start the logger
 * AuditLogger logger = new AuditLogger(reactor, repository);
 * logger.start().get();
 * 
 * // Log an event
 * AuditEvent event = AuditEvent.builder()
 *     .eventType("USER_LOGIN")
 *     .principal("user123")
 *     .resource("/api/login")
 *     .action("AUTHENTICATE")
 *     .status("SUCCESS")
 *     .build();
 *     
 * logger.log(event);
 * }</pre>
 * </p>
 */
public class AuditLogger implements ReactiveService {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogger.class);
    
    private final Reactor reactor;
    private final Executor executor;
    private final AuditEventRepository repository;
    private volatile boolean running = false;

    /**
     * Creates a new AuditLogger with the specified reactor and repository.
     *
     * @param reactor The reactor to use for scheduling tasks
     * @param repository The repository to store audit events
     */
    public AuditLogger(Reactor reactor, AuditEventRepository repository) {
        this.reactor = Objects.requireNonNull(reactor);
        this.repository = Objects.requireNonNull(repository);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "audit-logger");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Logs an audit event asynchronously.
     * If the logger is not running, the event will be dropped and a warning will be logged.
     *
     * @param event The audit event to log
     */
    public void log(AuditEvent event) {
        if (!running) {
            logger.warn("AuditLogger is not running, dropping event: {}", event);
            return;
        }

        Promise.of(event)
            .async()
            .whenComplete((e, error) -> {
                if (error != null) {
                    logger.error("Failed to log audit event: {}", event, error);
                }
                save(e);
            });
    }

    /**
     * Logs an audit event synchronously.
     * This method blocks until the event is processed.
     *
     * @param event The audit event to log
     */
    public void logSync(AuditEvent event) {
        if (!running) {
            logger.warn("AuditLogger is not running, dropping event: {}", event);
            return;
        }
        
        save(event);
    }

    @Override
    public Reactor getReactor() {
        return reactor;
    }

    @Override
    public Promise<?> start() {
        running = true;
        logger.info("AuditLogger started");
        return Promise.complete();
    }

    @Override
    public Promise<?> stop() {
        running = false;
        logger.info("AuditLogger stopped");
        return Promise.complete();
    }

    private void save(AuditEvent event) {
        try {
            repository.save(event);
        } catch (Exception e) {
            logger.error("Failed to save audit event: {}", event, e);
        }
    }

    /**
     * Repository interface for persisting audit events.
     * Implementations should handle the actual storage of audit events.
     */
/**
 * Audit event repository.
 *
 * @doc.type interface
 * @doc.purpose Audit event repository
 * @doc.layer core
 * @doc.pattern Repository
 */
    @FunctionalInterface
    public interface AuditEventRepository {
        /**
         * Saves an audit event to the underlying storage.
         *
         * @param event The audit event to save
         * @throws RuntimeException if the event cannot be saved
         */
        void save(AuditEvent event);
    }
}
