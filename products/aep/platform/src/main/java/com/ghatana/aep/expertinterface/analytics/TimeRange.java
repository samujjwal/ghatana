package com.ghatana.aep.expertinterface.analytics;

import java.time.Instant;

/**
 * Time range for metric collection.
 * 
 * @doc.type class
 * @doc.purpose Time range specification
 * @doc.layer analytics
 */
public class TimeRange {
    private final Instant start;
    private final Instant end;
    
    public TimeRange(Instant start, Instant end) {
        this.start = start;
        this.end = end;
    }
    
    public Instant getStart() { return start; }
    public Instant getEnd() { return end; }
    
    public long getDurationMillis() {
        return end.toEpochMilli() - start.toEpochMilli();
    }
}
