package com.ghatana.kernel.observability;

import java.util.List;

/**
 * @doc.type interface
 * @doc.purpose Product-pluggable persistence for kernel audit trail events
 * @doc.layer core
 * @doc.pattern Adapter
 */
public interface AuditTrailPersistence {

    void persist(DefaultAuditTrailService.StoredAuditEvent event);

    List<DefaultAuditTrailService.StoredAuditEvent> loadAll();
}
