package com.ghatana.appplatform.compliance.domain;

import java.time.Instant;

/**
 * @doc.type    Record (Immutable Value Object)
 * @doc.purpose Restricted/watch list entry per D07-011.
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public record RestrictedListEntry(
        String entryId,
        RestrictedListType listType,
        String instrumentId,
        String entityGroupId,       // applies to this group (client group, broker, etc.)
        String reason,
        String startDateBs,
        String endDateBs,           // null = indefinite
        Instant createdAt
) {
    public boolean isActive(String todayBs) {
        boolean started = startDateBs.compareTo(todayBs) <= 0;
        boolean notExpired = endDateBs == null || endDateBs.compareTo(todayBs) >= 0;
        return started && notExpired;
    }
}
