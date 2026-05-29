package com.ghatana.phr.kernel.evidence;

import java.util.List;
import java.util.Map;

/**
 * Persistence port for PHR regulated event evidence.
 *
 * @doc.type interface
 * @doc.purpose Persistence port for PHR regulated event evidence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PhrEvidenceOutbox {

    PhrEvidenceOutboxEntry enqueue(String datasetId, String eventId, byte[] body, Map<String, String> metadata);

    List<PhrEvidenceOutboxEntry> pending(int limit);

    void markDelivered(PhrEvidenceOutboxEntry entry);

    void markFailed(PhrEvidenceOutboxEntry entry, Throwable error, int maxAttempts);

    long pendingCount();

    long deadLetterCount();
}
