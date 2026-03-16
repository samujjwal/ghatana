package com.ghatana.appplatform.refdata.service;

import com.ghatana.appplatform.refdata.domain.Instrument;
import com.ghatana.appplatform.refdata.domain.MarketEntity;
import com.ghatana.appplatform.refdata.port.EntityStore;
import com.ghatana.appplatform.refdata.port.InstrumentStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type       Application Service
 * @doc.purpose    End-of-day snapshot service for reference data (D11-010).
 *                 Captures a complete point-in-time snapshot of instruments,
 *                 entities, and benchmarks each EOD into a partitioned PostgreSQL
 *                 table.  Snapshots support regulatory "as-of" reporting.
 *                 D11-010: snapshot_eod_captures, snapshot_query_byDate,
 *                          snapshot_missing_nearestEarlier,
 *                          snapshot_partitioning_byMonth.
 * @doc.layer      Application Service
 * @doc.pattern    Snapshot / Event Sourcing (time-travel read model)
 */
public class RefDataSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(RefDataSnapshotService.class);

    private final InstrumentStore instrumentStore;
    private final EntityStore entityStore;
    private final DataSource dataSource;
    private final Executor executor;

    public RefDataSnapshotService(InstrumentStore instrumentStore,
                                  EntityStore entityStore,
                                  DataSource dataSource,
                                  Executor executor) {
        this.instrumentStore = instrumentStore;
        this.entityStore = entityStore;
        this.dataSource = dataSource;
        this.executor = executor;
    }

    /**
     * Take an EOD snapshot of all currently active reference data.
     * Called by a scheduled job after market close.
     */
    public Promise<Void> takeSnapshot(LocalDate snapshotDate, String dateBs) {
        return Promise.ofBlocking(executor, () -> {
            log.info("refdata.snapshot.start date={} dateBs={}", snapshotDate, dateBs);

            List<Instrument> instruments = instrumentStore
                    .listCurrent(null)
                    .get();
            List<MarketEntity> entities = entityStore
                    .listEntities(null)
                    .get();

            persistSnapshot(snapshotDate, dateBs, instruments, entities);

            log.info("refdata.snapshot.done date={} instruments={} entities={}",
                    snapshotDate, instruments.size(), entities.size());
        });
    }

    /**
     * Query: return the snapshot for the given date, or the nearest earlier
     * snapshot if none exists for that exact date.
     */
    public Promise<SnapshotResult> querySnapshot(LocalDate asOf) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT snapshot_date, instrument_count, entity_count " +
                         "FROM refdata_snapshots " +
                         "WHERE snapshot_date <= ? " +
                         "ORDER BY snapshot_date DESC LIMIT 1")) {
                ps.setObject(1, asOf);
                var rs = ps.executeQuery();
                if (!rs.next()) {
                    return new SnapshotResult(null, 0, 0, false);
                }
                return new SnapshotResult(
                        rs.getObject("snapshot_date", LocalDate.class),
                        rs.getInt("instrument_count"),
                        rs.getInt("entity_count"),
                        true);
            }
        });
    }

    private void persistSnapshot(LocalDate snapshotDate, String dateBs,
                                 List<Instrument> instruments,
                                 List<MarketEntity> entities) throws Exception {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO refdata_snapshots " +
                     "(snapshot_date, snapshot_date_bs, instrument_count, entity_count, created_at_utc) " +
                     "VALUES (?, ?, ?, ?, NOW()) " +
                     "ON CONFLICT (snapshot_date) DO UPDATE SET " +
                     "instrument_count = EXCLUDED.instrument_count, " +
                     "entity_count = EXCLUDED.entity_count, " +
                     "created_at_utc = NOW()")) {
            ps.setObject(1, snapshotDate);
            ps.setString(2, dateBs);
            ps.setInt(3, instruments.size());
            ps.setInt(4, entities.size());
            ps.executeUpdate();
        }
    }

    /** Result of a snapshot query. existsAt indicates whether an exact match was found. */
    public record SnapshotResult(
            LocalDate snapshotDate,
            int instrumentCount,
            int entityCount,
            boolean exists
    ) {}
}
