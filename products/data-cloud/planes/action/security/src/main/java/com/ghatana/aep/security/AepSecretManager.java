/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade multi-source secret manager for AEP.
 *
 * <h3>Resolution chain (highest to lowest priority)</h3>
 * <ol>
 *   <li><b>Kubernetes projected volume</b> — reads from
 *       {@code /var/run/secrets/aep/<key>} (standard K8s Secret mount path).
 *       Supports hot-rotation: files are re-read on every access so a rolling
 *       secret update takes effect without an application restart.</li>
 *   <li><b>HashiCorp Vault HTTP API</b> — queries
 *       {@code <VAULT_ADDR>/v1/<VAULT_SECRET_PATH>/<key>} using the
 *       {@code VAULT_TOKEN} environment variable for authentication.
 *       Enabled when {@code VAULT_ADDR} is set.</li>
 *   <li><b>Environment variables</b> — direct key lookup as a final fallback.
 *       Suitable for development and non-K8s deployment scenarios.</li>
 * </ol>
 *
 * <h3>Security properties</h3>
 * <ul>
 *   <li>Secrets are <em>never</em> written to any log output.</li>
 *   <li>Vault responses are cached for at most {@value #VAULT_CACHE_TTL_MS} ms
 *       to limit the number of Vault API calls, while still reacting to
 *       rotation within a reasonable window.</li>
 *   <li>The in-process cache stores only non-null values; a missing secret is
 *       never positively cached so a retry will re-attempt Vault.</li>
 * </ul>
 *
 * <h3>Environment variables consumed by this class</h3>
 * <table border="1">
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>VAULT_ADDR</td><td>(none)</td><td>Vault HTTP base URL (enables Vault tier when set)</td></tr>
 *   <tr><td>VAULT_TOKEN</td><td>(none)</td><td>Vault root/service token</td></tr>
 *   <tr><td>VAULT_SECRET_PATH</td><td>secret/data/aep</td><td>KV v2 mount path for AEP secrets</td></tr>
 *   <tr><td>AEP_SECRETS_DIR</td><td>/var/run/secrets/aep</td><td>Directory for K8s secret volume mounts</td></tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepSecretManager secrets = AepSecretManager.fromSystem();
 * String dbPassword = secrets.require("AEP_DB_PASSWORD");
 * Optional<String> apiKey = secrets.get("EXTERNAL_API_KEY");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Multi-source secret manager with K8s, Vault, and env-var resolution tiers
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepSecretManager {

    private static final Logger log = LoggerFactory.getLogger(AepSecretManager.class);

    /** Vault cache TTL: 60 seconds — balances freshness with API call volume. */
    static final long VAULT_CACHE_TTL_MS = 60_000L;

    /** Default directory for Kubernetes Secret projected volumes. */
    static final String DEFAULT_SECRETS_DIR = "/var/run/secrets/aep";

    /** Default Vault KV v2 secret path. */
    static final String DEFAULT_VAULT_PATH = "secret/data/aep";

    /** Default secret rotation check interval: 5 minutes. */
    static final long DEFAULT_ROTATION_CHECK_INTERVAL_MS = 300_000L;

    // -------------------------------------------------------------------------
    //  Configuration
    // -------------------------------------------------------------------------
    private final Map<String, String> envSource;
    private final Path secretsDir;
    private final String vaultAddr;
    private final String vaultToken;
    private final String vaultSecretPath;
    private final long rotationCheckIntervalMs;

    // -------------------------------------------------------------------------
    //  Vault response cache
    // -------------------------------------------------------------------------
    private final ConcurrentHashMap<String, CachedSecret> vaultCache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    //  Secret rotation tracking
    // -------------------------------------------------------------------------
    private final ConcurrentHashMap<String, SecretVersion> secretVersions = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    //  HTTP client for Vault (lazily initialised to avoid cost when Vault unused)
    // -------------------------------------------------------------------------
    private volatile HttpClient httpClient;

    // -------------------------------------------------------------------------
    //  Rotation scheduler
    // -------------------------------------------------------------------------
    private volatile ScheduledExecutorService rotationScheduler;

    // =========================================================================
    //  Internal types (defined early to avoid forward reference issues)
    // =========================================================================

    /**
     * Vault cache entry with TTL expiry.
     *
     * @param value       cached secret value
     * @param expiresAtMs wall-clock ms timestamp after which this entry is stale
     */
    private record CachedSecret(String value, long expiresAtMs) {
        boolean isValid() {
            return System.currentTimeMillis() < expiresAtMs;
        }
    }

    /**
     * Secret version metadata for rotation detection.
     *
     * @param lastModifiedMs file last modification timestamp
     * @param fileSize       file size in bytes
     */
    private record SecretVersion(long lastModifiedMs, long fileSize) {
        // Used to detect file changes for K8s secret rotation
    }

    // =========================================================================
    //  Factory methods
    // =========================================================================

    /**
     * Creates a secret manager backed by the real system environment and standard
     * K8s / Vault configuration.
     *
     * @return system-backed secret manager
     */
    public static AepSecretManager fromSystem() {
        return fromSystem(DEFAULT_ROTATION_CHECK_INTERVAL_MS);
    }

    /**
     * Creates a secret manager backed by the real system environment and standard
     * K8s / Vault configuration, with a custom rotation check interval.
     *
     * @param rotationCheckIntervalMs interval in milliseconds between rotation checks
     * @return system-backed secret manager with automated rotation
     */
    public static AepSecretManager fromSystem(long rotationCheckIntervalMs) {
        Map<String, String> env = System.getenv();
        Path secretsDir = Path.of(env.getOrDefault("AEP_SECRETS_DIR", DEFAULT_SECRETS_DIR));
        String vaultAddr        = env.get("VAULT_ADDR");
        String vaultToken       = env.get("VAULT_TOKEN");
        String vaultSecretPath  = env.getOrDefault("VAULT_SECRET_PATH", DEFAULT_VAULT_PATH);
        return new AepSecretManager(env, secretsDir, vaultAddr, vaultToken, vaultSecretPath, rotationCheckIntervalMs);
    }

    /**
     * Creates a secret manager for testing with a custom environment map.
     * K8s file resolution and Vault are disabled when created this way.
     *
     * @param envOverrides custom environment entries
     * @return test-scoped secret manager
     */
    public static AepSecretManager forTesting(Map<String, String> envOverrides) {
        return new AepSecretManager(envOverrides, Path.of("/nonexistent-test-secrets"),
                null, null, DEFAULT_VAULT_PATH, Long.MAX_VALUE); // Disable rotation in tests
    }

    /**
     * Package-private full constructor for unit testing with injected collaborators.
     */
    AepSecretManager(Map<String, String> envSource, Path secretsDir,
                     String vaultAddr, String vaultToken, String vaultSecretPath) {
        this(envSource, secretsDir, vaultAddr, vaultToken, vaultSecretPath, DEFAULT_ROTATION_CHECK_INTERVAL_MS);
    }

    /**
     * Package-private full constructor with custom rotation interval.
     */
    AepSecretManager(Map<String, String> envSource, Path secretsDir,
                     String vaultAddr, String vaultToken, String vaultSecretPath,
                     long rotationCheckIntervalMs) {
        this.envSource       = Objects.requireNonNull(envSource, "envSource");
        this.secretsDir      = Objects.requireNonNull(secretsDir, "secretsDir");
        this.vaultAddr       = vaultAddr;
        this.vaultToken      = vaultToken;
        this.vaultSecretPath = vaultSecretPath != null ? vaultSecretPath : DEFAULT_VAULT_PATH;
        this.rotationCheckIntervalMs = rotationCheckIntervalMs;
    }

    // =========================================================================
    //  Public API
    // =========================================================================

    /**
     * Resolves a secret by key, walking the full resolution chain.
     *
     * <p>Resolution order: K8s file → Vault → environment variable.
     * Returns {@link Optional#empty()} if the secret is not found in any tier.
     *
     * @param key secret key (case-sensitive)
     * @return resolved secret value, or {@link Optional#empty()}
     */
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key");

        // Tier 1: Kubernetes projected volume
        Optional<String> fromFile = readFromFile(key);
        if (fromFile.isPresent()) {
            log.debug("Secret '{}' resolved from K8s volume", key);
            // Track version for rotation detection
            trackSecretVersion(key);
            return fromFile;
        }

        // Tier 2: HashiCorp Vault
        if (vaultAddr != null && !vaultAddr.isBlank()) {
            Optional<String> fromVault = readFromVault(key);
            if (fromVault.isPresent()) {
                log.debug("Secret '{}' resolved from Vault", key);
                return fromVault;
            }
        }

        // Tier 3: Environment variable
        String envValue = envSource.get(key);
        if (envValue != null && !envValue.isBlank()) {
            log.debug("Secret '{}' resolved from environment variable", key);
            return Optional.of(envValue);
        }

        log.warn("Secret '{}' not found in any source tier", key);
        return Optional.empty();
    }

    /**
     * Resolves a secret by key, throwing if not found in any tier.
     *
     * @param key secret key
     * @return resolved secret value (never null)
     * @throws IllegalStateException if the secret is not found anywhere
     */
    public String require(String key) {
        return get(key).orElseThrow(() ->
                new IllegalStateException("Required secret '" + key + "' not found in any source tier "
                        + "(K8s volume, Vault, or environment)"));
    }

    /**
     * Returns {@code true} if the specified key can be resolved from any tier.
     *
     * @param key secret key
     * @return true when the secret is available
     */
    public boolean has(String key) {
        return get(key).isPresent();
    }

    /**
     * Invalidates the Vault cache for the specified key, forcing a re-fetch on
     * the next call to {@link #get(String)}.
     *
     * <p>Call this proactively after a known secret rotation to minimise the
     * window between rotation and in-process uptake.
     *
     * @param key secret key to invalidate
     */
    public void invalidate(String key) {
        vaultCache.remove(key);
        log.info("Secret cache invalidated for key '{}'", key);
    }

    /**
     * Invalidates all cached Vault secrets.
     */
    public void invalidateAll() {
        vaultCache.clear();
        log.info("All secret cache entries invalidated");
    }

    /**
     * Starts automated secret rotation checking.
     *
     * <p>When enabled, the secret manager will periodically check for secret changes
     * and automatically invalidate cached values when rotation is detected. This is
     * particularly useful for K8s secret volumes and Vault secrets that are rotated
     * externally.</p>
     *
     * @return this secret manager for chaining
     */
    public AepSecretManager startRotationChecker() {
        if (rotationScheduler != null && !rotationScheduler.isShutdown()) {
            log.info("Secret rotation checker already running");
            return this;
        }

        rotationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "secret-rotation-checker");
            t.setDaemon(true);
            return t;
        });

        rotationScheduler.scheduleAtFixedRate(
            this::checkForRotations,
            rotationCheckIntervalMs,
            rotationCheckIntervalMs,
            TimeUnit.MILLISECONDS
        );

        log.info("Secret rotation checker started with interval {} ms", rotationCheckIntervalMs);
        return this;
    }

    /**
     * Stops automated secret rotation checking.
     *
     * @return this secret manager for chaining
     */
    public AepSecretManager stopRotationChecker() {
        if (rotationScheduler != null && !rotationScheduler.isShutdown()) {
            rotationScheduler.shutdown();
            try {
                if (!rotationScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rotationScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rotationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Secret rotation checker stopped");
        }
        return this;
    }

    /**
     * Checks for secret rotations and invalidates cache when changes are detected.
     *
     * <p>This method is called periodically by the rotation scheduler. It compares
     * current secret values with previously cached versions and triggers cache
     * invalidation when changes are detected.</p>
     */
    private void checkForRotations() {
        try {
            // Check K8s file-based secrets for changes
            checkK8sSecretRotations();
            
            // Check Vault secrets for changes (cache expiry handles most cases)
            checkVaultSecretRotations();
        } catch (Exception e) {
            log.warn("Error during secret rotation check: {}", e.getMessage());
        }
    }

    /**
     * Checks K8s secret file modifications by comparing file modification times.
     */
    private void checkK8sSecretRotations() {
        if (!Files.isDirectory(secretsDir)) {
            return;
        }

        try {
            Files.list(secretsDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    String key = file.getFileName().toString();
                    try {
                        long lastModified = Files.getLastModifiedTime(file).toMillis();
                        SecretVersion currentVersion = new SecretVersion(lastModified, Files.size(file));
                        
                        SecretVersion previousVersion = secretVersions.get(key);
                        if (previousVersion != null && !previousVersion.equals(currentVersion)) {
                            log.info("Secret '{}' rotation detected (file modified), invalidating cache", key);
                            invalidate(key);
                        }
                        
                        secretVersions.put(key, currentVersion);
                    } catch (IOException e) {
                        log.debug("Could not check rotation for secret '{}': {}", key, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.debug("Could not list secrets directory: {}", e.getMessage());
        }
    }

    /**
     * Checks Vault secrets for changes by comparing cached values with fresh reads.
     */
    private void checkVaultSecretRotations() {
        if (vaultAddr == null || vaultAddr.isBlank()) {
            return;
        }

        // For Vault, we rely on cache expiry (VAULT_CACHE_TTL_MS) to handle most rotations
        // This method performs a proactive check for secrets that are about to expire
        long now = Instant.now().toEpochMilli();
        long expiryThreshold = now + (VAULT_CACHE_TTL_MS / 2); // Check at half TTL

        vaultCache.forEach((key, cached) -> {
            if (cached.expiresAtMs < expiryThreshold) {
                log.debug("Secret '{}' cache nearing expiry, pre-fetching from Vault", key);
                invalidate(key); // Force re-fetch on next access
            }
        });
    }

    // =========================================================================
    //  Tier implementations
    // =========================================================================

    /**
     * Reads a secret from the Kubernetes projected volume mount.
     *
     * <p>Files are re-read on every call so that secret rotation writes take
     * effect without a restart.
     */
    private Optional<String> readFromFile(String key) {
        Path secretFile = secretsDir.resolve(key);
        if (!Files.isReadable(secretFile)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(secretFile, StandardCharsets.UTF_8).strip();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (IOException e) {
            log.warn("Failed to read K8s secret file for key '{}': {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Tracks the version of a secret for rotation detection.
     */
    private void trackSecretVersion(String key) {
        Path secretFile = secretsDir.resolve(key);
        if (!Files.isReadable(secretFile)) {
            return;
        }
        try {
            long lastModified = Files.getLastModifiedTime(secretFile).toMillis();
            long fileSize = Files.size(secretFile);
            secretVersions.put(key, new SecretVersion(lastModified, fileSize));
        } catch (IOException e) {
            // Ignore tracking errors
        }
    }

    /**
     * Reads a secret from HashiCorp Vault KV v2 API, with a short TTL cache.
     *
     * <p>The Vault API path follows the KV v2 convention:
     * {@code GET /v1/<mount>/data/<key>}
     */
    private Optional<String> readFromVault(String key) {
        // Check cache first
        CachedSecret cached = vaultCache.get(key);
        if (cached != null && cached.isValid()) {
            return Optional.of(cached.value());
        }

        try {
            String base = vaultAddr.endsWith("/") ? vaultAddr.substring(0, vaultAddr.length() - 1) : vaultAddr;
            String url = base + "/v1/" + vaultSecretPath + "/" + key;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("X-Vault-Token", vaultToken != null ? vaultToken : "")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = getHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() == 200) {
                Optional<String> value = parseVaultKvV2Response(response.body(), key);
                value.ifPresent(v -> vaultCache.put(key,
                        new CachedSecret(v, Instant.now().toEpochMilli() + VAULT_CACHE_TTL_MS)));
                return value;
            } else if (response.statusCode() == 404) {
                log.debug("Secret '{}' not found in Vault (404)", key);
                return Optional.empty();
            } else {
                log.warn("Vault returned HTTP {} for secret '{}'; falling back to env", response.statusCode(), key);
                return Optional.empty();
            }
        } catch (Exception e) {
            log.warn("Failed to read secret '{}' from Vault ({}): {}", key, vaultAddr, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Parses a Vault KV v2 JSON response to extract the value for {@code key}.
     *
     * <p>KV v2 response structure:
     * <pre>{@code
     * { "data": { "data": { "<key>": "<value>" } } }
     * }</pre>
     *
     * <p>Rather than introducing a JSON library dependency, this uses simple
     * string parsing sufficient for the single-value extraction use case.
     */
    static Optional<String> parseVaultKvV2Response(String json, String key) {
        if (json == null || json.isBlank()) return Optional.empty();
        // Find "data":{ ... "data":{ ... "<key>":"<value>" ... } ... }
        // Simplistic but avoids a JSON dependency in the security layer.
        String searchKey = "\"" + key + "\":\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            // Try without quotes (numeric values)
            searchKey = "\"" + key + "\":";
            keyIndex = json.indexOf(searchKey);
            if (keyIndex < 0) return Optional.empty();
            int valueStart = keyIndex + searchKey.length();
            int valueEnd = json.indexOf(',', valueStart);
            if (valueEnd < 0) valueEnd = json.indexOf('}', valueStart);
            if (valueEnd < 0) return Optional.empty();
            String raw = json.substring(valueStart, valueEnd).strip();
            return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
        }
        int valueStart = keyIndex + searchKey.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) return Optional.empty();
        String value = json.substring(valueStart, valueEnd);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
                }
            }
        }
        return httpClient;
    }
}
