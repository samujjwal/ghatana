package com.ghatana.pattern.codegen.audit;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.pattern.api.codegen.GeneratedTypeKey;
import com.ghatana.pattern.api.codegen.GeneratedTypeMode;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Emits audit events for runtime compilation activities.
 */
public final class CompilationAuditLogger {
    private final Consumer<AuditEvent> sink;

    private CompilationAuditLogger(Consumer<AuditEvent> sink) {
        this.sink = sink;
    }

    /**
     * @return logger that ignores all events (useful for tests).
     */
    public static CompilationAuditLogger noop() {
        return new CompilationAuditLogger(event -> {});
    }

    /**
     * @param sink consumer invoked for every audit event.
     */
    public static CompilationAuditLogger of(Consumer<AuditEvent> sink) {
        return new CompilationAuditLogger(Objects.requireNonNull(sink, "sink"));
    }

    /**
     * Emits a successful compile audit entry.
     *
     * @param key            identity of the generated class
     * @param mode           generation mode
     * @param durationMillis elapsed time in milliseconds
     */
    public void logSuccess(GeneratedTypeKey key,
                           GeneratedTypeMode mode,
                           long durationMillis) {
        AuditEvent event = AuditEvent.builder()
                .eventType("EVENT_CLASS_COMPILED")
                .timestamp(Instant.now())
                .resourceType("pattern")
                .resourceId(key.describe())
                .success(true)
                .detail("mode", mode.name())
                .detail("durationMillis", durationMillis)
                .build();
        sink.accept(event);
    }

    /**
     * Emits a failed compile audit entry.
     *
     * @param key   identity of the generated class
     * @param mode  generation mode
     * @param cause failure reason
     */
    public void logFailure(GeneratedTypeKey key,
                           GeneratedTypeMode mode,
                           Exception cause) {
        AuditEvent event = AuditEvent.builder()
                .eventType("EVENT_CLASS_COMPILE_FAILED")
                .timestamp(Instant.now())
                .resourceType("pattern")
                .resourceId(key.describe())
                .success(false)
                .detail("mode", mode.name())
                .detail("error", cause.getMessage())
                .build();
        sink.accept(event);
    }
}
