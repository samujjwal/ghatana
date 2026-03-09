package com.ghatana.core.connectors.impl;

import com.ghatana.core.connectors.BaseConnector;
import com.ghatana.core.connectors.Connector;
import com.ghatana.platform.observability.util.PromisesCompat;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * File connector implementation supporting both source and sink operations.
 * Reads from and writes to files with metrics, health checks, and standardized configuration.
 *
 * @doc.type class
 * @doc.purpose File-based connector for reading/writing observability data to local filesystem
 * @doc.layer observability
 * @doc.pattern Adapter, Implementation
 *
 * <p>Provides file-based data integration with watch service for monitoring file changes
 * (source mode) and buffered writing with configurable append mode (sink mode). Supports
 * bidirectional operation as both source and sink simultaneously.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Source mode: File watcher monitors changes via Java NIO WatchService</li>
 *   <li>Sink mode: Buffered writing with append/overwrite support</li>
 *   <li>Automatic parent directory creation</li>
 *   <li>Metrics tracking per message (size, latency)</li>
 *   <li>Health checks verify file existence, permissions, watcher status</li>
 *   <li>Graceful shutdown with resource cleanup</li>
 * </ul>
 *
 * <h2>Configuration:</h2>
 * Required properties:
 * - {@code file.path}: Absolute or relative file path
 *
 * Optional properties:
 * - {@code file.append}: true (default) or false (overwrite)
 * - {@code source.enabled}: Enable source (reading) mode
 * - {@code sink.enabled}: Enable sink (writing) mode
 * - {@code timeout}: Operation timeout (default: 30s)
 * - {@code retryAttempts}: Retry count (default: 3)
 * - {@code retryDelay}: Delay between retries (default: 1s)
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create sink connector for audit log
 * FileConnector auditLog = new FileConnector("audit-sink");
 * ConnectorConfig config = FileConnectorConfig.builder()
 *     .name("audit-sink")
 *     .filePath("/var/log/audit.log")
 *     .append(true)
 *     .sinkEnabled(true)
 *     .build();
 * 
 * auditLog.initialize(config)
 *     .then(() -> auditLog.start())
 *     .whenResult(() -> auditLog.writeMessage("User login: admin"));
 * 
 * // Create source connector for monitoring
 * FileConnector monitor = new FileConnector("file-watcher");
 * ConnectorConfig watchConfig = FileConnectorConfig.builder()
 *     .name("file-watcher")
 *     .filePath("/tmp/events.json")
 *     .sourceEnabled(true)
 *     .build();
 * 
 * monitor.initialize(watchConfig)
 *     .then(() -> monitor.start());
 * // Connector will detect file changes and process them
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * Thread-safe. Watcher runs on dedicated daemon thread. Writer is accessed
 * serially via Promise chain.
 *
 * <h2>Performance Characteristics:</h2>
 * - Write: O(1) - buffered, flushed immediately
 * - Watch: Event-driven (no polling overhead)
 * - Shutdown: Graceful with thread interruption
 *
 * <h2>Limitations:</h2>
 * - Watch service monitors parent directory (not individual file)
 * - Large files are read entirely into memory on change
 * - No built-in file rotation (external logrotate recommended)
 *
 * @since 1.0.0
 */
public class FileConnector extends BaseConnector {
    
    private static final Logger logger = LoggerFactory.getLogger(FileConnector.class);
    
    private final AtomicReference<Path> filePath = new AtomicReference<>();
    private final AtomicReference<WatchService> watchService = new AtomicReference<>();
    private final AtomicReference<BufferedWriter> writer = new AtomicReference<>();
    private final AtomicReference<Thread> watchThread = new AtomicReference<>();
    private volatile boolean running = false;
    
    public FileConnector(String name) {
        super(name, "file");
    }
    
    @Override
    protected Promise<Void> doInitialize(ConnectorConfig config) {
        return PromisesCompat.runBlocking(() -> {
            try {
                String path = config.getProperty("file.path", String.class);
                if (path == null) {
                    throw new IllegalArgumentException("file.path is required");
                }
                
                filePath.set(Paths.get(path));
                
                // Create parent directories if they don't exist
                if (isSinkCapable()) {
                    Files.createDirectories(filePath.get().getParent());
                }
                
                // Initialize watch service if source-capable
                if (isSourceCapable()) {
                    watchService.set(FileSystems.getDefault().newWatchService());
                    filePath.get().getParent().register(
                        watchService.get(),
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE
                    );
                    logger.info("Watch service initialized for directory: {}", filePath.get().getParent());
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to initialize File connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<Void> doStart() {
        return PromisesCompat.runBlocking(() -> {
            try {
                // Open writer if sink-capable
                if (isSinkCapable()) {
                    boolean append = getConfig().getProperty("file.append", Boolean.class, true);
                    writer.set(new BufferedWriter(new FileWriter(filePath.get().toFile(), append)));
                    logger.info("File writer opened for path: {}", filePath.get());
                }
                
                // Start watch thread if source-capable
                if (isSourceCapable() && watchService.get() != null) {
                    running = true;
                    Thread thread = new Thread(this::watchFile);
                    thread.setName("file-connector-" + getName() + "-watcher");
                    thread.setDaemon(true);
                    thread.start();
                    watchThread.set(thread);
                    logger.info("File watch thread started for path: {}", filePath.get());
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to start File connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<Void> doStop() {
        return PromisesCompat.runBlocking(() -> {
            try {
                // Stop watch thread if running
                running = false;
                if (watchThread.get() != null) {
                    watchThread.get().interrupt();
                    watchThread.set(null);
                    logger.info("File watch thread stopped");
                }
                
                // Close watch service if initialized
                if (watchService.get() != null) {
                    watchService.get().close();
                    watchService.set(null);
                    logger.info("Watch service closed");
                }
                
                // Close writer if initialized
                if (writer.get() != null) {
                    writer.get().close();
                    writer.set(null);
                    logger.info("File writer closed");
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to stop File connector", e);
                throw e;
            }
        });
    }
    
    @Override
    protected Promise<HealthCheckResult> doHealthCheck() {
        return PromisesCompat.runBlocking(() -> {
            try {
                boolean fileExists = Files.exists(filePath.get());
                boolean fileReadable = Files.isReadable(filePath.get());
                boolean fileWritable = !isSinkCapable() || Files.isWritable(filePath.get());
                boolean watcherRunning = !isSourceCapable() || (watchThread.get() != null && watchThread.get().isAlive());
                
                if (fileExists && fileReadable && fileWritable && watcherRunning) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("path", filePath.get().toString());
                    details.put("exists", fileExists);
                    details.put("readable", fileReadable);
                    details.put("writable", fileWritable);
                    details.put("watcherRunning", watcherRunning);
                    details.put("messagesProcessed", getMetrics().getMessagesProcessed());
                    
                    return HealthCheckResult.healthy("File connector is healthy", details, Duration.ZERO);
                } else {
                    Map<String, Object> details = new HashMap<>();
                    details.put("path", filePath.get().toString());
                    details.put("exists", fileExists);
                    details.put("readable", fileReadable);
                    details.put("writable", fileWritable);
                    details.put("watcherRunning", watcherRunning);
                    
                    return HealthCheckResult.unhealthy("File connector is unhealthy", details, Duration.ZERO, null);
                }
            } catch (Exception e) {
                return HealthCheckResult.unhealthy("File health check failed", e);
            }
        });
    }
    
    @Override
    public boolean isSourceCapable() {
        ConnectorConfig cfg = getConfig();
        return cfg != null && cfg.getProperty("source.enabled", Boolean.class, false);
    }
    
    @Override
    public boolean isSinkCapable() {
        ConnectorConfig cfg = getConfig();
        return cfg != null && cfg.getProperty("sink.enabled", Boolean.class, false);
    }
    
    /**
     * Write a message to the file.
     */
    public Promise<Void> writeMessage(String message) {
        return PromisesCompat.runBlocking(() -> {
            if (!isSinkCapable()) {
                throw new IllegalStateException("Connector is not sink-capable");
            }
            
            if (writer.get() == null) {
                throw new IllegalStateException("Writer is not initialized");
            }
            
            try {
                long startTime = System.currentTimeMillis();
                writer.get().write(message);
                writer.get().newLine();
                writer.get().flush();
                
                long processingTime = System.currentTimeMillis() - startTime;
                recordMessage(true, message.getBytes(StandardCharsets.UTF_8).length, processingTime);
                
                return null;
            } catch (Exception e) {
                recordMessage(false, 0, 0);
                throw e;
            }
        });
    }
    
    /**
     * Watch file for changes.
     */
    private void watchFile() {
        try {
            while (running) {
                WatchKey key = watchService.get().take(); // Blocking
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = pathEvent.context();
                    
                    // Check if the event is for our file
                    if (filePath.get().getFileName().equals(changed)) {
                        processFileChange();
                    }
                }
                
                if (!key.reset()) {
                    logger.warn("Watch key is no longer valid");
                    break;
                }
            }
        } catch (InterruptedException e) {
            logger.info("Watch thread interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error in watch thread", e);
        }
    }
    
    /**
     * Process file change event.
     */
    private void processFileChange() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Read the file
            byte[] content = Files.readAllBytes(filePath.get());
            
            // Process the content (in a real implementation, you'd call a handler)
            logger.debug("File changed: {} ({} bytes)", filePath.get(), content.length);
            
            long processingTime = System.currentTimeMillis() - startTime;
            recordMessage(true, content.length, processingTime);
            
        } catch (Exception e) {
            logger.error("Failed to process file change", e);
            recordMessage(false, 0, 0);
        }
    }
    
    /**
     * Create a default configuration for the File connector.
     */
    public static class FileConnectorConfig implements Connector.ConnectorConfig {
        private final String name;
        private final Map<String, Object> properties;
        private final Duration timeout;
        private final int retryAttempts;
        private final Duration retryDelay;
        private final boolean enabled;
        
        public FileConnectorConfig(String name, Map<String, Object> properties, 
                                 Duration timeout, int retryAttempts, 
                                 Duration retryDelay, boolean enabled) {
            this.name = name;
            this.properties = new HashMap<>(properties);
            this.timeout = timeout;
            this.retryAttempts = retryAttempts;
            this.retryDelay = retryDelay;
            this.enabled = enabled;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getType() {
            return "file";
        }
        
        @Override
        public Map<String, Object> getProperties() {
            return new HashMap<>(properties);
        }
        
        @Override
        public <T> T getProperty(String key, Class<T> type) {
            Object value = properties.get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            throw new IllegalArgumentException("Property " + key + " is not of type " + type.getName());
        }
        
        @Override
        public <T> T getProperty(String key, Class<T> type, T defaultValue) {
            T value = getProperty(key, type);
            return value != null ? value : defaultValue;
        }
        
        @Override
        public Duration getTimeout() {
            return timeout;
        }
        
        @Override
        public int getRetryAttempts() {
            return retryAttempts;
        }
        
        @Override
        public Duration getRetryDelay() {
            return retryDelay;
        }
        
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * Builder for FileConnectorConfig.
         */
        public static class Builder {
            private String name;
            private final Map<String, Object> properties = new HashMap<>();
            private Duration timeout = Duration.ofSeconds(30);
            private int retryAttempts = 3;
            private Duration retryDelay = Duration.ofSeconds(1);
            private boolean enabled = true;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder property(String key, Object value) {
                properties.put(key, value);
                return this;
            }
            
            public Builder properties(Map<String, Object> properties) {
                this.properties.putAll(properties);
                return this;
            }
            
            public Builder timeout(Duration timeout) {
                this.timeout = timeout;
                return this;
            }
            
            public Builder retryAttempts(int retryAttempts) {
                this.retryAttempts = retryAttempts;
                return this;
            }
            
            public Builder retryDelay(Duration retryDelay) {
                this.retryDelay = retryDelay;
                return this;
            }
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder filePath(String filePath) {
                properties.put("file.path", filePath);
                return this;
            }
            
            public Builder append(boolean append) {
                properties.put("file.append", append);
                return this;
            }
            
            public Builder sourceEnabled(boolean enabled) {
                properties.put("source.enabled", enabled);
                return this;
            }
            
            public Builder sinkEnabled(boolean enabled) {
                properties.put("sink.enabled", enabled);
                return this;
            }
            
            public FileConnectorConfig build() {
                if (name == null || name.isEmpty()) {
                    throw new IllegalArgumentException("Connector name is required");
                }
                if (!properties.containsKey("file.path")) {
                    throw new IllegalArgumentException("file.path is required");
                }
                return new FileConnectorConfig(name, properties, timeout, retryAttempts, retryDelay, enabled);
            }
        }
        
        public static Builder builder() {
            return new Builder();
        }
    }
}
