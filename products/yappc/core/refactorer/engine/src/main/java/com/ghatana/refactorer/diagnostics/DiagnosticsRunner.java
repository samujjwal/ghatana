/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.diagnostics;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.UnifiedDiagnostic;
import com.ghatana.refactorer.shared.service.LanguageService;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Cache key for storing diagnostic results. 
 * @doc.type class
 * @doc.purpose Handles cache key operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
class CacheKey {
    private final Path file;
    private final String languageServiceId;
    private final long lastModified;
    private final int configHash;

    public CacheKey(Path file, String languageServiceId, long lastModified, int configHash) {
        this.file = file;
        this.languageServiceId = languageServiceId;
        this.lastModified = lastModified;
        this.configHash = configHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return lastModified == cacheKey.lastModified
                && configHash == cacheKey.configHash
                && Objects.equals(file, cacheKey.file)
                && Objects.equals(languageServiceId, cacheKey.languageServiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, languageServiceId, lastModified, configHash);
    }
}

/**
 * Discovers and runs diagnostics across all supported languages in the project. Implements caching
 * and incremental analysis for improved performance.
 */
public final class DiagnosticsRunner {
    private static final Logger log = LogManager.getLogger(DiagnosticsRunner.class);
    private static final ServiceLoader<LanguageService> languageServices =
            ServiceLoader.load(LanguageService.class);

    // Cache for diagnostic results
    private static final Map<CacheKey, List<UnifiedDiagnostic>> diagnosticsCache =
            new ConcurrentHashMap<>();

    // Cache for file modification times
    private static final Map<Path, Long> fileModificationCache = new ConcurrentHashMap<>();

    // Cache for configuration hashes
    private static final Map<Object, Integer> configHashes = new ConcurrentHashMap<>();

    private DiagnosticsRunner() {
        // Prevent instantiation
    }

    /**
     * Discovers and returns all available language services.
     *
     * @return List of discovered language services
     */
    public static List<LanguageService> discoverLanguageServices() {
        List<LanguageService> services = new ArrayList<>();
        try {
            for (LanguageService service : languageServices) {
                log.info("Discovered language service: {}", service.id());
                services.add(service);
            }
        } catch (ServiceConfigurationError e) {
            log.error("Error loading language services", e);
            throw new RuntimeException("Failed to load language services", e);
        }
        return services;
    }

    /**
     * Runs diagnostics for all files in the project using all available language services.
     * Implements caching and incremental analysis.
     *
     * @param ctx The project context
     * @return List of diagnostics found
     */
    public static List<UnifiedDiagnostic> runAll(PolyfixProjectContext ctx) {
        Objects.requireNonNull(ctx, "Project context cannot be null");
        log.info("Running diagnostics on directory: {}", ctx.root());

        List<UnifiedDiagnostic> allDiagnostics = new ArrayList<>();
        List<LanguageService> services = discoverLanguageServices();

        if (services.isEmpty()) {
            log.warn("No language services found. No diagnostics will be run.");
            return allDiagnostics;
        }

        try {
            // Group files by language service
            Map<LanguageService, List<Path>> filesByLanguage = groupFilesByLanguage(services, ctx);

            // Get configuration hash for cache invalidation
            int configHash = computeConfigHash(ctx);

            // Run diagnostics for each language service in parallel
            filesByLanguage.entrySet().parallelStream()
                    .forEach(
                            entry -> {
                                LanguageService service = entry.getKey();
                                List<Path> files = entry.getValue();

                                if (files.isEmpty()) {
                                    log.debug(
                                            "No files found for language service: {}",
                                            service.id());
                                    return;
                                }

                                log.info(
                                        "Running {} diagnostics on {} files",
                                        service.id(),
                                        files.size());
                                try {
                                    // Process files in batches for better memory management
                                    int batchSize = 100;
                                    for (int i = 0; i < files.size(); i += batchSize) {
                                        List<Path> batch =
                                                files.subList(
                                                        i, Math.min(i + batchSize, files.size()));
                                        List<UnifiedDiagnostic> batchDiagnostics =
                                                processFileBatch(service, batch, ctx, configHash);

                                        synchronized (allDiagnostics) {
                                            allDiagnostics.addAll(batchDiagnostics);
                                        }

                                        log.debug(
                                                "Processed batch {}/{} for {}",
                                                Math.min(i + batchSize, files.size()),
                                                files.size(),
                                                service.id());
                                    }
                                } catch (Exception e) {
                                    log.error(
                                            "Error running diagnostics for {}: {}",
                                            service.id(),
                                            e.getMessage(),
                                            e);
                                }
                            });

            // Clean up cache
            cleanupCache();

        } catch (IOException e) {
            log.error("Error scanning project files: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to scan project files", e);
        }

        log.info("Completed diagnostics. Found {} total issues.", allDiagnostics.size());
        return allDiagnostics;
    }

    /**
 * Process a batch of files with caching and incremental analysis. */
    private static List<UnifiedDiagnostic> processFileBatch(
            LanguageService service, List<Path> files, PolyfixProjectContext ctx, int configHash) {

        List<UnifiedDiagnostic> results = new ArrayList<>();
        List<Path> filesToAnalyze = new ArrayList<>();

        // Check cache for each file
        for (Path file : files) {
            try {
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                Long cachedModified = fileModificationCache.get(file);

                // If file hasn't changed since last analysis, use cached results
                if (cachedModified != null && cachedModified == lastModified) {
                    CacheKey cacheKey = new CacheKey(file, service.id(), lastModified, configHash);
                    List<UnifiedDiagnostic> cached = diagnosticsCache.get(cacheKey);
                    if (cached != null) {
                        results.addAll(cached);
                        continue;
                    }
                }

                // File needs analysis
                filesToAnalyze.add(file);
                fileModificationCache.put(file, lastModified);

            } catch (IOException e) {
                log.warn("Could not get last modified time for {}: {}", file, e.getMessage());
            }
        }

        // Run diagnostics only on changed or uncached files
        if (!filesToAnalyze.isEmpty()) {
            try {
                List<UnifiedDiagnostic> diagnostics = awaitPromise(
                        service.diagnose(ctx, filesToAnalyze));

                // Cache the results
                for (Path file : filesToAnalyze) {
                    try {
                        long lastModified = Files.getLastModifiedTime(file).toMillis();
                        CacheKey cacheKey =
                                new CacheKey(file, service.id(), lastModified, configHash);

                        // Filter diagnostics for this specific file
                        List<UnifiedDiagnostic> fileDiagnostics =
                                diagnostics.stream()
                                        .filter(d -> d.file().equals(file.toString()))
                                        .collect(Collectors.toList());

                        diagnosticsCache.put(cacheKey, fileDiagnostics);
                        results.addAll(fileDiagnostics);

                    } catch (IOException e) {
                        log.warn("Could not cache results for {}: {}", file, e.getMessage());
                    }
                }

                log.debug(
                        "Found {} new diagnostics from {} for {}",
                        diagnostics.size(),
                        service.id(),
                        filesToAnalyze.size());

            } catch (Exception e) {
                log.error("Error in batch processing for {}: {}", service.id(), e.getMessage(), e);
            }
        }

        return results;
    }

    /**
 * Compute a hash of the configuration for cache invalidation. */
    private static int computeConfigHash(PolyfixProjectContext ctx) {
        return configHashes.computeIfAbsent(
                ctx.config(),
                config -> {
                    // Create a combined hash of all relevant configuration parameters
                    return Objects.hash(
                            ctx.config().hashCode(),
                            // Add other relevant configuration parameters here
                            System.getProperty("os.name"),
                            System.getProperty("os.version"));
                });
    }

    /**
 * Clean up old entries from the cache. */
    private static void cleanupCache() {
        // Remove entries for files that no longer exist
        List<Path> filesToRemove = new ArrayList<>();
        for (Path file : fileModificationCache.keySet()) {
            if (!Files.exists(file)) {
                filesToRemove.add(file);
            }
        }
        filesToRemove.forEach(fileModificationCache::remove);

        // Limit cache size (LRU eviction)
        int maxCacheSize = 10000;
        if (diagnosticsCache.size() > maxCacheSize) {
            // Remove oldest entries (approximate LRU)
            List<CacheKey> keysToRemove =
                    new ArrayList<>(
                            diagnosticsCache.keySet().stream()
                                    .sorted(
                                            Comparator.comparingLong(
                                                    k -> -k.hashCode())) // Simple approximation
                                    .limit(diagnosticsCache.size() - maxCacheSize / 2)
                                    .collect(Collectors.toList()));
            keysToRemove.forEach(diagnosticsCache::remove);
        }
    }

    /**
 * Group files by their corresponding language service in parallel. */
    private static Map<LanguageService, List<Path>> groupFilesByLanguage(
            List<LanguageService> services, PolyfixProjectContext ctx) throws IOException {

        Map<LanguageService, List<Path>> result = new ConcurrentHashMap<>();
        services.forEach(
                service -> result.put(service, Collections.synchronizedList(new ArrayList<>())));

        // Walk the file tree and process files in parallel
        Files.walk(ctx.root())
                .parallel()
                .filter(Files::isRegularFile)
                .forEach(
                        file -> {
                            // First try to match by extension (faster)
                            String fileName = file.getFileName().toString();
                            int dotIndex = fileName.lastIndexOf('.');
                            if (dotIndex > 0) {
                                String ext = fileName.substring(dotIndex).toLowerCase();
                                // Check if any service supports this extension
                                for (LanguageService service : services) {
                                    if (service.getSupportedFileExtensions().contains(ext)) {
                                        result.get(service).add(file);
                                        return;
                                    }
                                }
                            }

                            // Fall back to full supports() check if needed
                            for (LanguageService service : services) {
                                if (service.supports(file)) {
                                    result.get(service).add(file);
                                    break;
                                }
                            }
                        });

        return result;
    }

    /**
     * Blocks on an ActiveJ Promise result by running a temporary Eventloop.
     * Used to bridge from Promise-based LanguageService API to blocking DiagnosticsRunner context.
     */
    @SuppressWarnings("unchecked")
    private static <T> T awaitPromise(Promise<T> promise) throws Exception {
        Object[] result = new Object[1];
        Exception[] error = new Exception[1];
        Eventloop eventloop = Eventloop.builder().build();
        eventloop.keepAlive(true);
        eventloop.post(() -> promise
                .whenResult(r -> { result[0] = r; eventloop.breakEventloop(); })
                .whenException(e -> { error[0] = e; eventloop.breakEventloop(); }));
        eventloop.run();
        if (error[0] != null) throw error[0];
        return (T) result[0];
    }
}
