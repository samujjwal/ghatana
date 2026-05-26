package com.ghatana.phr.kernel.evidence;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drains regulated PHR event evidence from the durable outbox into Data Cloud.
 */
public final class PhrEvidenceOutboxDispatcher implements KernelLifecycleAware {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final KernelContext context;
    private final PhrEvidenceOutbox outbox;
    private final int maxAttempts;
    private volatile boolean running;
    private volatile String lastError = "";

    public PhrEvidenceOutboxDispatcher(KernelContext context, PhrEvidenceOutbox outbox, int maxAttempts) {
        this.context = context;
        this.outbox = outbox;
        this.maxAttempts = maxAttempts;
    }

    public PhrEvidenceOutboxEntry enqueue(String datasetId, String eventId, byte[] body, Map<String, String> metadata) {
        PhrEvidenceOutboxEntry entry = outbox.enqueue(datasetId, eventId, body, metadata);
        drainPending();
        return entry;
    }

    public void drainPending() {
        Optional<DataCloudKernelAdapter> adapter = dataCloudAdapter();
        if (adapter.isEmpty()) {
            lastError = "DataCloudKernelAdapter unavailable";
            return;
        }

        List<PhrEvidenceOutboxEntry> entries = outbox.pending(DEFAULT_BATCH_SIZE);
        for (PhrEvidenceOutboxEntry entry : entries) {
            drainEntry(adapter.get(), entry);
        }
    }

    private void drainEntry(DataCloudKernelAdapter adapter, PhrEvidenceOutboxEntry originalEntry) {
        PhrEvidenceOutboxEntry entry = originalEntry;
        while (entry.attempts() < maxAttempts) {
            try {
                adapter.writeData(entry.toDataWriteRequest());
                outbox.markDelivered(entry);
                lastError = "";
                return;
            } catch (Exception error) {
                lastError = error.getMessage();
                PhrEvidenceOutboxEntry failed = entry.failed(lastError, maxAttempts, java.time.Instant.now());
                outbox.markFailed(entry, error, maxAttempts);
                entry = failed;
                if (failed.status() == PhrEvidenceOutboxEntry.Status.DEAD_LETTER) {
                    return;
                }
            }
        }
    }

    public HealthStatus getHealthStatus() {
        long pending = outbox.pendingCount();
        long deadLetters = outbox.deadLetterCount();
        Map<String, Object> details = Map.of(
            "pendingEvidenceWrites", pending,
            "deadLetterEvidenceWrites", deadLetters,
            "lastError", lastError
        );
        if (deadLetters > 0) {
            return HealthStatus.unhealthy("PHR regulated evidence outbox has dead-lettered writes", details);
        }
        if (pending > 0 || !lastError.isBlank()) {
            return HealthStatus.builder()
                .withStatus(HealthStatus.Status.DEGRADED)
                .withMessage("PHR regulated evidence writes are pending")
                .withDetails(details)
                .build();
        }
        return HealthStatus.healthy("PHR regulated evidence outbox drained", details);
    }

    @Override
    public Promise<Void> start() {
        running = true;
        drainPending();
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return running && outbox.deadLetterCount() == 0;
    }

    @Override
    public String getName() {
        return "phr-evidence-outbox-dispatcher";
    }

    public long pendingCount() {
        return outbox.pendingCount();
    }

    public long deadLetterCount() {
        return outbox.deadLetterCount();
    }

    private Optional<DataCloudKernelAdapter> dataCloudAdapter() {
        if (!context.hasDependency(DataCloudKernelAdapter.class)) {
            return Optional.empty();
        }
        DataCloudKernelAdapter adapter = context.getDependency(DataCloudKernelAdapter.class);
        return Optional.ofNullable(adapter);
    }
}
