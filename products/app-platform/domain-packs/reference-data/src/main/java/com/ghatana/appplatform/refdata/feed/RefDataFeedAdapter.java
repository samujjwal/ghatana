package com.ghatana.appplatform.refdata.feed;

import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.MarketEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type       Port (Secondary / Plugin Interface)
 * @doc.purpose    T3 plugin interface for external reference data feed adapters.
 *                 Implementations receive a sandboxed capability set (network
 *                 access to the declared feed URL only) and must not exfiltrate data.
 *                 D11-008: adapter_sandbox_noExfiltration.
 * @doc.layer      Domain / Port
 * @doc.pattern    Plugin Port (T3 network-tier plugin)
 */
public interface RefDataFeedAdapter {

    /** Unique adapter identifier, e.g. "nepse-cdsc", "bloomberg-reference". */
    String adapterId();

    /** Feed source URL (declared capability; must match plugin manifest). */
    String feedUrl();

    /** Open connection to the external feed. */
    void connect();

    /** Close connection gracefully. */
    void disconnect();

    /**
     * Fetch the full instrument list from the feed.
     * Called on initial sync and incremental refresh.
     */
    List<Instrument> fetchInstruments();

    /**
     * Fetch the full entity list from the feed.
     */
    List<MarketEntity> fetchEntities();

    /**
     * Register a callback for real-time update notifications.
     * The platform calls this when subscribing to live updates.
     */
    void onUpdate(Consumer<RefDataUpdateEvent> callback);

    /** Update event carrying the changed/new record. */
    record RefDataUpdateEvent(
            String updateType,          // "INSTRUMENT" | "ENTITY"
            Object updatedRecord        // Instrument or MarketEntity
    ) {}
}
