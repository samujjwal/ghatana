/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Framework Module
 */
package com.ghatana.yappc.framework.core.plugin.audit;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records a BEFORE/AFTER audit event for every plugin method invocation.
 *
 * <p>Wrapping pipeline (from outer to inner as seen by callers):
 * <ol>
 *   <li>{@link PluginAuditInterceptor} — outermost, emits audit events</li>
 *   <li>{@link com.ghatana.yappc.framework.core.plugin.sandbox.PermissionProxy} — security</li>
 *   <li>Real plugin instance</li>
 * </ol>
 *
 * <p>Audit consumers receive a {@code Map<String, Object>} with:
 * <ul>
 *   <li>{@code agentId}    — calling agent identifier</li>
 *   <li>{@code pluginId}   — plugin descriptor id</li>
 *   <li>{@code action}     — INIT | GENERATE | &lt;methodName&gt;</li>
 *   <li>{@code phase}      — BEFORE | AFTER | ERROR</li>
 *   <li>{@code inputHash}  — SHA-256 prefix of concatenated string args</li>
 *   <li>{@code outputHash} — SHA-256 prefix of return value toString() (AFTER only)</li>
 *   <li>{@code durationMs} — wall-clock milliseconds (AFTER / ERROR only)</li>
 *   <li>{@code status}     — OK | ERROR (AFTER / ERROR only)</li>
 *   <li>{@code timestamp}  — ISO-8601 instant</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Records BEFORE/AFTER audit events for every plugin method invocation
 * @doc.layer product
 * @doc.pattern Decorator / Interceptor
 */
public final class PluginAuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PluginAuditInterceptor.class);

    private PluginAuditInterceptor() {}

    /**
     * Wraps {@code instance} with an audit interceptor that records every method call.
     *
     * @param <T>          contract type
     * @param instance     the plugin instance (may already be permission-proxy-wrapped)
     * @param contract     plugin interface the proxy must implement
     * @param pluginId     stable plugin id for audit records
     * @param agentId      calling agent id for audit records
     * @param auditSink    receives audit event maps; must be thread-safe
     * @return interceptor-wrapped proxy
     */
    @SuppressWarnings("unchecked")
    public static <T> T wrap(
            T instance,
            Class<T> contract,
            String pluginId,
            String agentId,
            Consumer<Map<String, Object>> auditSink) {

        if (!contract.isInterface()) {
            throw new IllegalArgumentException("contract must be an interface, got: " + contract.getName());
        }

        return (T) Proxy.newProxyInstance(
                contract.getClassLoader(),
                new Class<?>[] {contract},
                new AuditingHandler(instance, pluginId, agentId, auditSink));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static final class AuditingHandler implements InvocationHandler {

        private final Object target;
        private final String pluginId;
        private final String agentId;
        private final Consumer<Map<String, Object>> auditSink;

        AuditingHandler(
                Object target,
                String pluginId,
                String agentId,
                Consumer<Map<String, Object>> auditSink) {
            this.target = target;
            this.pluginId = pluginId;
            this.agentId = agentId;
            this.auditSink = auditSink;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String action = deriveAction(method.getName());
            String inputHash = hashArgs(args);
            long startMs = System.currentTimeMillis();

            // BEFORE record
            emit("BEFORE", action, inputHash, null, null, null);

            Object result;
            try {
                result = method.invoke(target, args);
            } catch (Throwable t) {
                long durationMs = System.currentTimeMillis() - startMs;
                emit("ERROR", action, inputHash, null, durationMs, "ERROR");
                throw t;
            }

            long durationMs = System.currentTimeMillis() - startMs;
            String outputHash = hashValue(result);
            emit("AFTER", action, inputHash, outputHash, durationMs, "OK");
            return result;
        }

        private void emit(
                String phase, String action, String inputHash,
                String outputHash, Long durationMs, String status) {
            Map<String, Object> record = new HashMap<>();
            record.put("agentId", agentId);
            record.put("pluginId", pluginId);
            record.put("action", action);
            record.put("phase", phase);
            record.put("inputHash", inputHash);
            record.put("timestamp", Instant.now().toString());
            if (outputHash != null) record.put("outputHash", outputHash);
            if (durationMs != null) record.put("durationMs", durationMs);
            if (status != null) record.put("status", status);
            try {
                auditSink.accept(record);
            } catch (Exception e) {
                log.warn("Plugin audit sink failed for plugin '{}' phase {}: {}", pluginId, phase, e.getMessage());
            }
        }

        private static String deriveAction(String methodName) {
            if (methodName.contains("init") || methodName.contains("Init")) return "INIT";
            if (methodName.contains("generate") || methodName.contains("Generate")) return "GENERATE";
            return methodName.toUpperCase();
        }

        private static String hashArgs(Object[] args) {
            if (args == null || args.length == 0) return "";
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) {
                if (arg != null) sb.append(arg);
            }
            return sha256Prefix(sb.toString());
        }

        private static String hashValue(Object value) {
            if (value == null) return "";
            return sha256Prefix(value.toString());
        }

        private static String sha256Prefix(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) {
                    hex.append(String.format("%02x", b));
                }
                return hex.substring(0, Math.min(16, hex.length()));
            } catch (Exception e) {
                return "hash-error";
            }
        }
    }
}
