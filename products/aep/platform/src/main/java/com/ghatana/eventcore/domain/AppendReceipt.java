package com.ghatana.eventcore.domain;

import java.time.Instant;

/**
 * Receipt returned after appending an event to the store.
 */
public record AppendReceipt(int partition, long offset, Instant ts) { }
