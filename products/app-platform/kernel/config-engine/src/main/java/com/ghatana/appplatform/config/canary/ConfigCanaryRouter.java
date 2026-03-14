package com.ghatana.appplatform.config.canary;

import com.ghatana.appplatform.config.domain.ConfigEntry;
import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;
import com.ghatana.appplatform.config.port.ConfigStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;

/**
 * Percentage-based canary router for config rollouts (STORY-K02-008).
 *
 * <p>Allows a new config value to be rolled out gradually by routing a
 * configurable percentage of sessions/tenants to the new value while the
 * remainder continue seeing the existing (stable) value. This enables safe,
 * incremental rollout of config changes without a hard cut-over.
 *
 * <h2>Bucketing strategy</h2>
 * Session-hash bucketing using SHA-256 ensures determinism: the same session (or
 * tenant) always maps to the same bucket, so a user's experience is consistent
 * across requests within the same rollout. The bucket is computed as:
 * <pre>
 *   bucket = SHA-256(namespace + ":" + key + ":" + bucketKey) mod 100
 * </pre>
 * If {@code bucket < canaryPercentage}, the session receives the canary value;
 * otherwise it receives the stable value.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConfigCanaryRouter router = new ConfigCanaryRouter(configStore);
 * Promise<ConfigValue> value = router.resolve(
 *     "payments", "max_transfer_limit",
 *     tenantId, sessionId,
 *     "10000",      // stable value
 *     "50000",      // canary value (rolled out to 10% of sessions)
 *     10            // canaryPercentage
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Percentage-based canary config router for gradual rollouts (K02-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConfigCanaryRouter {

    private static final Logger log = LoggerFactory.getLogger(ConfigCanaryRouter.class);

    private final ConfigStore configStore;

    /**
     * @param configStore the backing config store; used to persist canary values at rollout completion
     */
    public ConfigCanaryRouter(ConfigStore configStore) {
        this.configStore = Objects.requireNonNull(configStore, "configStore");
    }

    /**
     * Resolves a config value using canary bucketing.
     *
     * <p>The caller supplies both the current stable value and the candidate canary value,
     * along with the rollout percentage. The router deterministically assigns the session
     * to either the stable or canary bucket based on a SHA-256 hash of the routing key.
     *
     * @param namespace         config namespace (e.g. "payments")
     * @param key               config key within the namespace
     * @param tenantId          tenant scope identifier
     * @param bucketKey         the session or user ID used for deterministic bucketing;
     *                          typically sessionId or userId for per-user consistency
     * @param stableValue       JSON-encoded current stable value
     * @param canaryValue       JSON-encoded new canary value being rolled out
     * @param canaryPercentage  integer 0–100; fraction of traffic receiving the canary value
     * @return the stable or canary {@link ConfigValue}, depending on the bucket assignment
     */
    public ConfigValue resolve(
            String namespace,
            String key,
            String tenantId,
            String bucketKey,
            String stableValue,
            String canaryValue,
            int canaryPercentage) {

        if (canaryPercentage < 0 || canaryPercentage > 100) {
            throw new IllegalArgumentException("canaryPercentage must be between 0 and 100");
        }
        if (canaryPercentage == 0) {
            return toConfigValue(key, tenantId, stableValue);
        }
        if (canaryPercentage == 100) {
            return toConfigValue(key, tenantId, canaryValue);
        }

        int bucket = bucket(namespace, key, bucketKey);
        boolean isCanary = bucket < canaryPercentage;
        log.debug("Canary routing: namespace={} key={} bucket={} pct={} → {}",
            namespace, key, bucket, canaryPercentage, isCanary ? "canary" : "stable");

        return isCanary
            ? toConfigValue(key, tenantId, canaryValue)
            : toConfigValue(key, tenantId, stableValue);
    }

    /**
     * Promotes a canary to the stable value for a given percentage bracket,
     * writing the canary value as the new TENANT-level config entry.
     *
     * <p>Call this when the rollout has completed and you want to make the canary
     * value permanent for all sessions (100% traffic).
     *
     * @param namespace  config namespace
     * @param key        config key
     * @param levelId    level ID (e.g. tenantId) to promote in
     * @param value      the value to promote (should be the former canaryValue)
     * @param promotedBy actor performing the promotion
     * @return promise that resolves when the entry is persisted
     */
    public Promise<Void> promote(String namespace, String key, String levelId,
                                  String value, String promotedBy) {
        log.info("Promoting canary to stable: namespace={} key={} levelId={} by={}",
            namespace, key, levelId, promotedBy);
        ConfigEntry entry = new ConfigEntry(namespace, key, value,
            ConfigHierarchyLevel.TENANT, levelId, namespace);
        return configStore.setEntry(entry);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Computes a deterministic bucket [0, 100) for the given routing key.
     * Uses SHA-256 to produce a stable hash from name+key+bucketKey.
     */
    static int bucket(String namespace, String key, String bucketKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            String input = namespace + ":" + key + ":" + bucketKey;
            byte[] hash = sha256.digest(input.getBytes(StandardCharsets.UTF_8));
            // Use the first 4 bytes as an unsigned integer, then mod 100
            int value = ((hash[0] & 0xFF) << 24)
                      | ((hash[1] & 0xFF) << 16)
                      | ((hash[2] & 0xFF) << 8)
                      |  (hash[3] & 0xFF);
            return Math.abs(value) % 100;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static ConfigValue toConfigValue(String key, String tenantId, String value) {
        return new ConfigValue(key, value, ConfigHierarchyLevel.TENANT, tenantId);
    }
}
