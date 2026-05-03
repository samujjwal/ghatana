package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.domain.attribution.DmAttributionRecord;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for attribution record persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for last-click attribution record storage (DMOS-F2-017)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmAttributionRecordRepository {

    Promise<DmAttributionRecord> save(DmAttributionRecord record);

    Promise<Optional<DmAttributionRecord>> findById(String id);

    Promise<List<DmAttributionRecord>> listByVisitor(String tenantId, String visitorId);

    Promise<Optional<DmAttributionRecord>> findByConversionEvent(String tenantId, String conversionEventId);
}
