package com.ghatana.eventcore.domain;

import java.time.Instant;

/**
 * Receipt returned after appending an event to the store.
  * @doc.type record
 * @doc.purpose Provides append receipt functionality.
 * @doc.layer product
 * @doc.pattern Component
*/
public record AppendReceipt(int partition, long offset, Instant ts) { }
