package com.ghatana.appplatform.refdata.port;

import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.InstrumentStatus;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type       Port (Secondary)
 * @doc.purpose    Persistence port for the instrument master.  Implementations
 *                 write SCD Type-2 rows and support point-in-time "as-of" reads.
 * @doc.layer      Application / Port
 * @doc.pattern    Repository Port (Hexagonal)
 */
public interface InstrumentStore {

    /** Insert a new SCD Type-2 version row. */
    Promise<Void> save(Instrument instrument);

    /**
     * Close the current version of an instrument (set effectiveTo) and insert a
     * new version carrying the updated status.  Must be atomic.
     */
    Promise<Void> saveNewVersion(Instrument closedPrevious, Instrument newVersion);

    /** Return the current (effectiveTo IS NULL) version for the given ID. */
    Promise<Optional<Instrument>> findCurrentById(UUID id);

    /**
     * Point-in-time query: return the row valid on {@code asOf}.
     * effectiveFrom ≤ asOf AND (effectiveTo IS NULL OR effectiveTo > asOf)
     */
    Promise<Optional<Instrument>> findByIdAsOf(UUID id, LocalDate asOf);

    /** Full-text search on symbol, isin, or name; returns current versions only. */
    Promise<List<Instrument>> search(String query, int limit);

    /** List all current instruments with optional status filter. */
    Promise<List<Instrument>> listCurrent(InstrumentStatus statusFilter);

    /** Unique constraint check: does symbol+exchange already exist as a current row? */
    Promise<Boolean> existsBySymbolAndExchange(String symbol, String exchange);
}
