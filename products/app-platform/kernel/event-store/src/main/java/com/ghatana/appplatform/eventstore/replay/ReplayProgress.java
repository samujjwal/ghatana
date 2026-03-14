package com.ghatana.appplatform.eventstore.replay;

import java.time.Instant;

/**
 * Tracks progress of an event replay session.
 *
 * @doc.type record
 * @doc.purpose Replay session progress snapshot (STORY-K05-022)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReplayProgress(
    String replayId,
    long eventsReplayed,
    long eventsSkipped,
    long eventsFailed,
    long totalEstimate,    // -1 if unknown
    Instant startedAt,
    Instant lastEventAt,   // timestamp of last replayed event
    boolean completed
) {
    public static ReplayProgress start(String replayId, long totalEstimate) {
        Instant now = Instant.now();
        return new ReplayProgress(replayId, 0, 0, 0, totalEstimate, now, now, false);
    }

    public ReplayProgress withReplayed(long count, Instant lastAt) {
        return new ReplayProgress(replayId, count, eventsSkipped, eventsFailed,
            totalEstimate, startedAt, lastAt, completed);
    }

    public ReplayProgress withSkipped(long skipped) {
        return new ReplayProgress(replayId, eventsReplayed, skipped, eventsFailed,
            totalEstimate, startedAt, lastEventAt, completed);
    }

    public ReplayProgress complete() {
        return new ReplayProgress(replayId, eventsReplayed, eventsSkipped, eventsFailed,
            totalEstimate, startedAt, lastEventAt, true);
    }

    public double percentComplete() {
        if (totalEstimate <= 0) return -1.0;
        return (eventsReplayed + eventsSkipped + eventsFailed) * 100.0 / totalEstimate;
    }
}
