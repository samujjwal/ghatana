package com.ghatana.appplatform.observability;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-local structured log context carrier.
 *
 * <p>Attach key-value pairs at the start of a request and propagate them
 * through the call stack. The log publisher enriches JSON log output with these fields.
 *
 * <p>Usage:
 * <pre>
 *   StructuredLogContext.put("tenantId", tenantId);
 *   StructuredLogContext.put("traceId", traceId);
 *   LOG.info("Processing payment"); // log framework reads context
 *   StructuredLogContext.clear();   // always clear on request completion
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Thread-local structured log context for request correlation (STORY-K06-001)
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class StructuredLogContext {

    private static final ThreadLocal<Map<String, String>> CONTEXT =
        ThreadLocal.withInitial(HashMap::new);

    private StructuredLogContext() {}

    /** Attach a key-value pair to the current thread's log context. */
    public static void put(String key, String value) {
        if (key != null && value != null) {
            CONTEXT.get().put(key, value);
        }
    }

    /** Retrieve the current value for a key, or null if absent. */
    public static String get(String key) {
        return CONTEXT.get().get(key);
    }

    /** Return an immutable snapshot of the current context. */
    public static Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(CONTEXT.get()));
    }

    /** Remove a specific key from the context. */
    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /** Clear all context entries. Must be called at the end of every request. */
    public static void clear() {
        CONTEXT.get().clear();
    }

    /**
     * Build a JSON representation of the current context for embedding in log records.
     * Output: {@code {"traceId":"abc","tenantId":"t1"}}
     */
    public static String toJson() {
        Map<String, String> ctx = CONTEXT.get();
        if (ctx.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"")
              .append(e.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}
