package com.ghatana.eventcore.ports;

import java.time.Instant;
import java.util.Iterator;

import com.ghatana.eventcore.domain.AppendReceipt;
import com.ghatana.eventcore.domain.EventRecord;
import com.ghatana.eventcore.domain.PageCursor;

/**
 * Core storage port for appending and reading events. This interface lives in
 * :eventcore and must have no dependencies on adapters.
 *
 * @doc.type interface
 * @doc.purpose Hexagonal port defining core event append and read operations with no adapter dependencies
 * @doc.layer core
 * @doc.pattern Port
 */
public interface EventStore {

    AppendReceipt append(EventRecord record);

    Iterator<EventRecord> readByType(String tenantId,
            String type,
            Instant from,
            Instant to,
            PageCursor page);
}
