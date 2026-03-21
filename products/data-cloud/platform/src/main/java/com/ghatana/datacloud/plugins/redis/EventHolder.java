package com.ghatana.datacloud.plugins.redis;

import com.ghatana.datacloud.event.model.Event;

/**
 * Event holder for the Disruptor ring buffer.
 *
 * <p><b>Purpose</b><br>
 * Provides a mutable container for events flowing through the LMAX Disruptor
 * ring buffer. The holder is pre-allocated and reused to minimize GC pressure.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EventHolder holder = new EventHolder();
 * holder.setEvent(event);
 * // ... process event ...
 * holder.clear();  // Reset for reuse
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Disruptor ring buffer event container
 * @doc.layer plugin
 * @doc.pattern ValueHolder, Flyweight
 */
public final class EventHolder {

    private Event event;
    private String tenantId;
    private String streamName;
    private long sequenceNumber;
    private long timestampNanos;

    public EventHolder() {
        clear();
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
        if (event != null) {
            this.tenantId = event.getTenantId();
            this.streamName = event.getStreamName();
            this.timestampNanos = System.nanoTime();
        }
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getStreamName() {
        return streamName;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    /**
     * Clears the holder for reuse.
     */
    public void clear() {
        this.event = null;
        this.tenantId = null;
        this.streamName = null;
        this.sequenceNumber = -1;
        this.timestampNanos = 0;
    }

    /**
     * Factory for Disruptor event pre-allocation.
     */
    public static final com.lmax.disruptor.EventFactory<EventHolder> FACTORY = EventHolder::new;
}
