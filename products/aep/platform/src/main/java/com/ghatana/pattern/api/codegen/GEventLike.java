package com.ghatana.pattern.api.codegen;

import java.util.Collections;
import java.util.Map;

/**
 * Minimal contract implemented by generated records when record mode is selected.
 *
 * <p>Records cannot extend {@code GEvent}, so this interface captures the fields that
 * downstream processors expect for lightweight analytics workloads.</p>
 */
public interface GEventLike {

    /**
     * @return fully qualified event type name.
     */
    String eventType();

    /**
     * @return semantic version associated with the event type.
     */
    String eventVersion();

    /**
     * @return stable event identifier (UUID/string).
     */
    String id();

    /**
     * @return detection/occurrence timestamp expressed in epoch milliseconds.
     */
    long timestampMillis();

    /**
     * @return immutable payload map backing the generated record.
     */
    Map<String, Object> payload();

    /**
     * @return immutable headers associated with the event.
     */
    Map<String, String> headers();

    /**
     * Convenience variant that never returns {@code null}.
     */
    default Map<String, Object> payloadOrEmpty() {
        Map<String, Object> map = payload();
        return map == null ? Collections.emptyMap() : map;
    }

    /**
     * Convenience variant that never returns {@code null}.
     */
    default Map<String, String> headersOrEmpty() {
        Map<String, String> map = headers();
        return map == null ? Collections.emptyMap() : map;
    }
}
