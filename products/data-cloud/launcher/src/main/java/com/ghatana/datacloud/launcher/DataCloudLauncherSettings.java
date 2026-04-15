package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.DataCloud;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Encapsulates launcher config and transport startup settings resolution
 * @doc.layer product
 * @doc.pattern Settings
 */
public final class DataCloudLauncherSettings {

    private static final int DEFAULT_HTTP_PORT = 8082;

    private DataCloudLauncherSettings() {
        // Utility class
    }

    public static DataCloud.DataCloudConfig parseClientConfig(String[] args) {
        return parseClientConfig(args, System.getenv());
    }

    static DataCloud.DataCloudConfig parseClientConfig(String[] args, Map<String, String> env) {
        DataCloud.DataCloudConfig.Builder builder = DataCloud.DataCloudConfig.builder();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) {
                        builder.instanceId(args[++i]);
                    }
                }
                case "--max-connections" -> {
                    if (i + 1 < args.length) {
                        builder.maxConnectionsPerTenant(Integer.parseInt(args[++i]));
                    }
                }
                case "--enable-caching" -> builder.enableCaching(true);
                case "--disable-caching" -> builder.enableCaching(false);
                case "--enable-metrics" -> builder.enableMetrics(true);
                default -> {
                    // Ignore non-config flags here; transport toggles are handled separately.
                }
            }
        }

        String instanceId = env.get("DATACLOUD_INSTANCE_ID");
        if (instanceId != null) {
            builder.instanceId(instanceId);
        }

        String maxConnections = env.get("DATACLOUD_MAX_CONNECTIONS");
        if (maxConnections != null) {
            builder.maxConnectionsPerTenant(Integer.parseInt(maxConnections));
        }

        return builder.build();
    }

    public static boolean shouldStartGrpcServer(String[] args) {
        return shouldStartGrpcServer(args, System.getenv());
    }

    static boolean shouldStartGrpcServer(String[] args, Map<String, String> env) {
        for (String arg : args) {
            if ("--grpc".equals(arg)) {
                return true;
            }
        }

        return isEnabled(env.get("DATACLOUD_GRPC_ENABLED"))
                || env.containsKey("DATACLOUD_GRPC_PORT");
    }

    public static boolean shouldStartHttpServer(String[] args) {
        return shouldStartHttpServer(args, System.getenv());
    }

    static boolean shouldStartHttpServer(String[] args, Map<String, String> env) {
        for (String arg : args) {
            if ("--http".equals(arg) || "--server".equals(arg)) {
                return true;
            }
        }

        return isEnabled(env.get("DATACLOUD_HTTP_ENABLED"));
    }

    static boolean hasEnabledTransport(String[] args, Map<String, String> env) {
        return shouldStartHttpServer(args, env) || shouldStartGrpcServer(args, env);
    }

    public static int resolveHttpPort(Map<String, String> env) {
        String rawPort = env.get("DATACLOUD_HTTP_PORT");
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_HTTP_PORT;
        }
        return Integer.parseInt(rawPort);
    }

    public static boolean isBrainEnabled(Map<String, String> env) {
        return isEnabled(env.get("DATACLOUD_BRAIN_ENABLED"));
    }

    public static boolean isAnalyticsEnabled(Map<String, String> env) {
        return isEnabled(env.get("DATACLOUD_ANALYTICS_ENABLED"));
    }

    public static boolean isDatabaseEnabled(Map<String, String> env) {
        return isEnabled(env.get("DATACLOUD_DB_ENABLED"));
    }

    public static boolean isAiEnabled(Map<String, String> env) {
        return isEnabled(env.get("DATACLOUD_AI_ENABLED"));
    }

    /**
     * Resolves the maximum number of requests per IP allowed in a rate-limit window.
     * Reads {@code DATACLOUD_RATE_LIMIT_REQUESTS}; defaults to {@code 200}.
     */
    public static int resolveRateLimitRequests(Map<String, String> env) {
        String raw = env.get("DATACLOUD_RATE_LIMIT_REQUESTS");
        if (raw == null || raw.isBlank()) {
            return 200;
        }
        return Integer.parseInt(raw);
    }

    /**
     * Resolves the sliding-window size in seconds for the rate limiter.
     * Reads {@code DATACLOUD_RATE_LIMIT_WINDOW_SECONDS}; defaults to {@code 60}.
     */
    public static long resolveRateLimitWindowSeconds(Map<String, String> env) {
        String raw = env.get("DATACLOUD_RATE_LIMIT_WINDOW_SECONDS");
        if (raw == null || raw.isBlank()) {
            return 60L;
        }
        return Long.parseLong(raw);
    }

    private static boolean isEnabled(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        return Boolean.parseBoolean(rawValue);
    }
}
