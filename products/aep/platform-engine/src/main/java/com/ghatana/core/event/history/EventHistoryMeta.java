package com.ghatana.core.event.history;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Metadata about an event history instance.
 * Contains essential information about the history's identity, type, and state.
 */
@Data
@AllArgsConstructor
public class EventHistoryMeta {
    /**
     * The unique identifier of the event history.
     */
    private final String id;
    
    /**
     * The type of the event history, which determines its behavior and storage characteristics.
     */
    private final String type;
    
    /**
     * Indicates whether the event history is currently open and available for operations.
     */
    private final boolean open;
}
