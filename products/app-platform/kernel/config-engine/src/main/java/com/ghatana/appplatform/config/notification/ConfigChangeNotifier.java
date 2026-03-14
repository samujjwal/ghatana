package com.ghatana.appplatform.config.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to the PostgreSQL {@code config_changes} channel and dispatches change events
 * to registered {@link ConfigChangeListener}s.
 *
 * <p>Uses a dedicated JDBC connection with {@code LISTEN config_changes} and a
 * background polling thread (every 500 ms by default). The {@code SELECT 1} keepalive
 * forces the PostgreSQL driver to deliver any queued notifications.
 *
 * <p><strong>Lifecycle:</strong> call {@link #start()} once to begin receiving
 * notifications, and {@link #stop()} to release the connection and background thread.
 * Both methods are idempotent.
 *
 * <p>Listeners are invoked on the polling thread and must be non-blocking.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL LISTEN/NOTIFY consumer for config hot-reload
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeNotifier.class);
    private static final String CHANNEL = "config_changes";

    private final DataSource dataSource;
    private final long pollIntervalMs;
    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private Connection listenConnection;

    /**
     * Constructs a notifier with the default 500 ms polling interval.
     *
     * @param dataSource source for the dedicated LISTEN connection
     */
    public ConfigChangeNotifier(DataSource dataSource) {
        this(dataSource, 500L);
    }

    /**
     * Constructs a notifier with a custom polling interval.
     *
     * @param dataSource      source for the dedicated LISTEN connection
     * @param pollIntervalMs  how often (milliseconds) to poll for notifications
     */
    public ConfigChangeNotifier(DataSource dataSource, long pollIntervalMs) {
        this.dataSource    = dataSource;
        this.pollIntervalMs = pollIntervalMs;
    }

    /**
     * Register a listener to receive config change events.
     *
     * <p>Safe to call before or after {@link #start()}.
     *
     * @param listener listener to add
     */
    public void addListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Unregister a previously added listener.
     *
     * @param listener listener to remove
     */
    public void removeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Start listening for config change notifications.
     *
     * <p>Opens a dedicated PostgreSQL connection, issues {@code LISTEN config_changes},
     * and begins polling on a background thread.
     *
     * @throws SQLException if the LISTEN connection cannot be established
     */
    public synchronized void start() throws SQLException {
        if (running.get()) {
            return;
        }
        listenConnection = dataSource.getConnection();
        listenConnection.setAutoCommit(true);
        try (Statement stmt = listenConnection.createStatement()) {
            stmt.execute("LISTEN " + CHANNEL);
        }
        log.info("ConfigChangeNotifier started LISTEN on channel '{}'", CHANNEL);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-change-notifier");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(
            this::pollNotifications,
            0,
            pollIntervalMs,
            TimeUnit.MILLISECONDS);

        running.set(true);
    }

    /**
     * Stop listening and release resources.
     *
     * <p>Idempotent — safe to call even if not started.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (listenConnection != null) {
            try {
                listenConnection.close();
            } catch (SQLException e) {
                log.warn("Error closing LISTEN connection", e);
            }
            listenConnection = null;
        }
        log.info("ConfigChangeNotifier stopped.");
    }

    private void pollNotifications() {
        try (Statement stmt = listenConnection.createStatement()) {
            // Keepalive — forces the driver to deliver queued PG notifications.
            stmt.execute("SELECT 1");
        } catch (SQLException e) {
            log.error("Keepalive query failed on LISTEN connection; stopping notifier.", e);
            stop();
            return;
        }

        try {
            PGConnection pgConn = listenConnection.unwrap(PGConnection.class);
            PGNotification[] notifications = pgConn.getNotifications();
            if (notifications == null) return;

            for (PGNotification notification : notifications) {
                if (CHANNEL.equals(notification.getName())) {
                    dispatch(notification.getParameter());
                }
            }
        } catch (SQLException e) {
            log.error("Error retrieving PostgreSQL notifications", e);
        }
    }

    private void dispatch(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return;
        try {
            JsonNode node      = mapper.readTree(payloadJson);
            String namespace   = node.path("namespace").asText();
            String key         = node.path("key").asText();
            String level       = node.path("level").asText();
            String levelId     = node.path("level_id").asText();

            for (ConfigChangeListener listener : listeners) {
                try {
                    listener.onConfigChange(namespace, key, level, levelId);
                } catch (Exception e) {
                    log.warn("ConfigChangeListener threw an exception; it will be ignored.", e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse config change notification payload: {}", payloadJson, e);
        }
    }

    /** Returns true if the notifier is currently running. */
    public boolean isRunning() {
        return running.get();
    }
}
