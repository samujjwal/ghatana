package com.ghatana.eventlog.domain;

import com.ghatana.contracts.event.v1.EventProto;

/**
 * Domain model representing a stored event with storage metadata.
 */
public final class EventRecord {
    private final long offset;
    private final long checksum;
    private final int sizeBytes;
    private final EventProto event;

    public EventRecord(long offset, long checksum, int sizeBytes, EventProto event) {
        this.offset = offset;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.event = event;
    }

    public long getOffset() { 
        return offset; 
    }
    
    public long getChecksum() { 
        return checksum; 
    }
    
    public int getSizeBytes() { 
        return sizeBytes; 
    }
    
    public EventProto getEvent() { 
        return event; 
    }
}
