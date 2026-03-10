/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.orchestrator.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates reports for polyfix runs in various formats. */
@Slf4j
/**
 * @doc.type class
 * @doc.purpose Handles report generator operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class ReportGenerator implements AutoCloseable {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Path outputDir;
    private final String runId;
    private final ObjectMapper objectMapper;
    private ReportServer reportServer;
    private Promise<Void> serverFuture;

    /**
     * Creates a new ReportGenerator with the specified output directory. A random run ID will be
     * generated.
     *
     * @param outputDir the directory where reports will be generated
     */
    public ReportGenerator(Path outputDir) {
        this(outputDir, UUID.randomUUID().toString());
    }

    /**
     * Creates a new ReportGenerator with the specified output directory and run ID.
     *
     * @param outputDir the directory where reports will be generated
     * @param runId the unique identifier for this report run
     */
    public ReportGenerator(Path outputDir, String runId) {
        this.outputDir = outputDir != null ? outputDir.toAbsolutePath().normalize() : null;
        this.runId = runId != null ? runId : UUID.randomUUID().toString();
        this.objectMapper =
                JsonUtils.getDefaultMapper()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
    }

    /**
     * Generates a report from a RunSummary object.
     *
     * @param summary the run summary to generate a report from
     * @return the path to the generated report
     * @throws IOException if an I/O error occurs
     */
    public Path generateReportFromSummary(Object summary) throws IOException {
        if (summary == null) {
            throw new IllegalArgumentException("Summary cannot be null");
        }
        return generateReport(convertToMap(summary));
    }

    /**
     * Generates a report with the given data.
     *
     * @param reportData the data to include in the report
     * @return the path to the generated report
     * @throws IOException if an I/O error occurs
     */
    public Path generateReport(Map<String, Object> reportData) throws IOException {
        // Create output directory if it doesn't exist
        if (outputDir == null) {
            throw new IllegalStateException("Output directory is not set");
        }
        Files.createDirectories(outputDir);

        // Add metadata to the report
        Map<String, Object> fullReport = new HashMap<>();
        fullReport.put("runId", runId);
        fullReport.put("timestamp", Instant.now().toString());
        fullReport.putAll(reportData);

        // Write the report data as JSON with a unique filename based on runId
        String reportFilename = String.format("report-%s.json", runId);
        Path jsonReportPath = outputDir.resolve(reportFilename);
        objectMapper.writeValue(jsonReportPath.toFile(), fullReport);

        // Start the report server if not already running
        startReportServer(jsonReportPath);

        log.info("Generated report at: {}", jsonReportPath);
        log.info("Report server running at: http://localhost:8080");

        return jsonReportPath;
    }

    /**
     * Converts a summary object to a Map for JSON serialization. Uses reflection to handle
     * different types of summary objects.
     *
     * @param summary the summary object to convert
     * @return a map containing the summary data
     */
    private Map<String, Object> convertToMap(Object summary) {
        Map<String, Object> map = new HashMap<>();
        if (summary == null) {
            return map;
        }

        if (summary.getClass().isRecord()) {
            for (RecordComponent component : summary.getClass().getRecordComponents()) {
                String fieldName = component.getName();
                try {
                    Object value = component.getAccessor().invoke(summary);
                    if (value != null) {
                        if (fieldName.equals("metrics")) {
                            map.put(fieldName, convertMetricsToMap(value));
                        } else {
                            map.put(fieldName, value);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error getting value for record field: " + fieldName, e);
                }
            }
            return map;
        }

        try {
            // Use reflection to get all getter methods and add them to the map
            java.lang.reflect.Method[] methods = summary.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                if ((methodName.startsWith("get") || methodName.startsWith("is"))
                        && method.getParameterCount() == 0
                        && !methodName.equals("getClass")) {

                    String fieldName =
                            methodName.startsWith("get")
                                    ? methodName.substring(3)
                                    : methodName.substring(2);
                    fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);

                    try {
                        Object value = method.invoke(summary);
                        if (value != null) {
                            // Handle special cases for known types
                            if (fieldName.equals("metrics")) {
                                map.put(fieldName, convertMetricsToMap(value));
                            } else {
                                map.put(fieldName, value);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Error getting value for field: " + fieldName, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error converting summary to map", e);
        }

        return map;
    }

    /**
     * Converts RunMetrics to a Map for JSON serialization. This is a placeholder implementation
     * that can be expanded based on the actual RunMetrics fields.
     *
     * @param metrics the metrics to convert (can be null)
     * @return a map containing the metrics data
     */
    private Map<String, Object> convertMetricsToMap(Object metrics) {
        Map<String, Object> map = new HashMap<>();
        if (metrics != null) {
            try {
                // Use reflection to get all getter methods and add them to the map
                java.lang.reflect.Method[] methods = metrics.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                        String fieldName = method.getName().substring(3);
                        fieldName =
                                fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                        try {
                            Object value = method.invoke(metrics);
                            if (value != null) {
                                if (value instanceof java.time.Instant instant) {
                                    map.put(fieldName, instant.toString());
                                } else {
                                    map.put(fieldName, value);
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Error getting value for field: " + fieldName, e);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error converting metrics to map", e);
            }
        }
        return map;
    }

    private void startReportServer(Path reportDataPath) {
        if (reportServer != null) {
            return; // Server already running
        }

        boolean isTest = System.getProperty("test.environment") != null;
        if (isTest) {
            log.debug("Skipping report server startup in test environment");
            return;
        }

        try {
            Path staticFilesDir = findStaticFilesDir();

            ReportServer.ReportServerConfig config =
                    ReportServer.ReportServerConfig.builder()
                            .reportDataPath(reportDataPath)
                            .staticFilesDir(staticFilesDir)
                            .port(8080)
                            .build();

            try {
                reportServer = new ReportServer(config);
            } catch (IOException ioe) {
                if (isAddressAlreadyInUse(ioe)) {
                    log.warn(
                            "Report server port {} unavailable; retrying with random port",
                            config.getPort());
                    config =
                            ReportServer.ReportServerConfig.builder()
                                    .reportDataPath(reportDataPath)
                                    .staticFilesDir(staticFilesDir)
                                    .port(0)
                                    .build();
                    reportServer = new ReportServer(config);
                } else {
                    throw ioe;
                }
            }

            serverFuture =
                    Promise.ofBlocking(BLOCKING_EXECUTOR, 
                            () -> {
                                try {
                                    reportServer.start();
                                } catch (Exception e) {
                                    log.error("Failed to start report server", e);
                                    throw new RuntimeException("Failed to start report server", e);
                                }
                            });

            Runtime.getRuntime().addShutdownHook(new Thread(this::close));

        } catch (Exception e) {
            log.error("Failed to initialize report server: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize report server", e);
        }
    }

    private Path findStaticFilesDir() throws IOException {
        // Check common locations for static files
        Path[] possibleDirs = {
            Paths.get("polyfix-ui", "build").toAbsolutePath(),
            Paths.get("polyfix-ui", "dist").toAbsolutePath(),
            Paths.get("polyfix-ui", "out").toAbsolutePath(),
            Paths.get("build", "resources", "main", "static").toAbsolutePath(),
            Paths.get("src", "main", "resources", "static").toAbsolutePath()
        };

        for (Path dir : possibleDirs) {
            if (Files.isDirectory(dir) && Files.isReadable(dir)) {
                log.debug("Found static files directory: {}", dir);
                return dir;
            }
        }

        // If no directory found, create a temporary one with a basic message
        Path tempDir = Files.createTempDirectory("polyfix-static-");
        Files.writeString(
                tempDir.resolve("index.html"),
                "<html><body><h1>Polyfix Report</h1><p>Static files not found. Please build the"
                        + " UI.</p></body></html>");
        return tempDir;
    }

    @Override
    public void close() {
        if (reportServer != null) {
            try {
                reportServer.close();
            } catch (Exception e) {
                log.error("Error closing report server", e);
            } finally {
                reportServer = null;
                serverFuture = null;
            }
        }
    }

    private boolean isAddressAlreadyInUse(Exception error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BindException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
