package com.ghatana.appplatform.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StructuredLogContext}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for thread-local structured log context
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("StructuredLogContext — Unit Tests")
class StructuredLogContextTest {

    @AfterEach
    void clearContext() {
        StructuredLogContext.clear();
    }

    @Test
    @DisplayName("put_and_get — stores and retrieves a value on the current thread")
    void putAndGet() {
        StructuredLogContext.put("tenantId", "t-123");
        assertThat(StructuredLogContext.get("tenantId")).isEqualTo("t-123");
    }

    @Test
    @DisplayName("put_nullKey — ignored silently, no exception")
    void putNullKeyIgnored() {
        StructuredLogContext.put(null, "value");
        assertThat(StructuredLogContext.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("put_nullValue — ignored silently, no exception")
    void putNullValueIgnored() {
        StructuredLogContext.put("key", null);
        assertThat(StructuredLogContext.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("get_absentKey — returns null")
    void getAbsentKey() {
        assertThat(StructuredLogContext.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("snapshot — returns immutable copy of current context")
    void snapshotIsImmutable() {
        StructuredLogContext.put("k1", "v1");
        StructuredLogContext.put("k2", "v2");

        Map<String, String> snap = StructuredLogContext.snapshot();

        assertThat(snap).containsEntry("k1", "v1").containsEntry("k2", "v2");
        // Mutating snapshot must not affect the context
        snap.put("k3", "v3");
        assertThat(StructuredLogContext.get("k3")).isNull();
    }

    @Test
    @DisplayName("remove — deletes a specific key, leaves others intact")
    void removeSpecificKey() {
        StructuredLogContext.put("k1", "v1");
        StructuredLogContext.put("k2", "v2");

        StructuredLogContext.remove("k1");

        assertThat(StructuredLogContext.get("k1")).isNull();
        assertThat(StructuredLogContext.get("k2")).isEqualTo("v2");
    }

    @Test
    @DisplayName("clear — empties the context")
    void clearEmptiesContext() {
        StructuredLogContext.put("tenantId", "t-abc");
        StructuredLogContext.clear();
        assertThat(StructuredLogContext.snapshot()).isEmpty();
    }

    @Test
    @DisplayName("threadIsolation — context on one thread is not visible on another")
    void threadIsolation() throws InterruptedException {
        StructuredLogContext.put("main", "mainValue");

        AtomicReference<String> childValue = new AtomicReference<>();
        Thread child = new Thread(() -> childValue.set(StructuredLogContext.get("main")));
        child.start();
        child.join();

        assertThat(childValue.get()).isNull();
    }
}
