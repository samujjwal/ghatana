package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Cross-validate prices across multiple sources: primary feed, secondary feed,
 *              and model price. Source priority: exchange official &gt; primary feed &gt; secondary
 *              feed &gt; model. K-02 configurable deviation threshold per instrument.
 *              K-05 adapter ports for each price source. Flags for price challenge workflow
 *              when deviation exceeds threshold. Satisfies STORY-D05-012.
 * @doc.layer   Domain
 * @doc.pattern Multi-source validation; source priority ranking; K-02 thresholds;
 *              K-05 adapter; Counter for warnings and flags.
 */
public class PriceCrossValidationService {

    private static final double DEFAULT_THRESHOLD = 0.02; // 2%

    private final HikariDataSource    dataSource;
    private final Executor            executor;
    private final ConfigPort          configPort;
    private final PriceChallengeService challengeService;
    private final Counter             warningCounter;
    private final Counter             flaggedCounter;

    public PriceCrossValidationService(HikariDataSource dataSource, Executor executor,
                                        ConfigPort configPort,
                                        PriceChallengeService challengeService,
                                        MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.configPort       = configPort;
        this.challengeService = challengeService;
        this.warningCounter   = Counter.builder("pricing.cross_val.warnings_total").register(registry);
        this.flaggedCounter   = Counter.builder("pricing.cross_val.flagged_total").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** K-02 per-instrument cross-validation threshold. */
    public interface ConfigPort {
        double getCrossValThreshold(String instrumentId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum PriceSource { EXCHANGE_OFFICIAL, PRIMARY_FEED, SECONDARY_FEED, MODEL }

    public record SourcePrice(PriceSource source, BigDecimal price, boolean available) {}

    public record CrossValResult(String instrumentId, LocalDate priceDate,
                                  List<SourcePrice> sources, BigDecimal selectedPrice,
                                  PriceSource selectedSource, boolean deviationFlagged,
                                  double maxDeviationPct, String challengeId) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<List<CrossValResult>> validateAll(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> crossValidate(runDate));
    }

    public Promise<CrossValResult> validateInstrument(String instrumentId, LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> crossValidateInstrument(instrumentId, runDate));
    }

    // ─── Validation logic ────────────────────────────────────────────────────

    private List<CrossValResult> crossValidate(LocalDate runDate) throws SQLException {
        List<String> instruments = loadInstruments();
        List<CrossValResult> results = new ArrayList<>();
        for (String id : instruments) {
            try {
                results.add(crossValidateInstrument(id, runDate));
            } catch (Exception ignored) {}
        }
        return results;
    }

    private CrossValResult crossValidateInstrument(String instrumentId, LocalDate runDate)
            throws Exception {
        List<SourcePrice> sources = loadSourcePrices(instrumentId, runDate);
        BigDecimal selectedPrice = selectPrice(sources);
        PriceSource selectedSource = getSelectedSource(sources);

        // Compute max deviation between available sources
        double maxDev = 0.0;
        List<SourcePrice> available = sources.stream().filter(SourcePrice::available).toList();
        for (int i = 0; i < available.size(); i++) {
            for (int j = i + 1; j < available.size(); j++) {
                BigDecimal a = available.get(i).price();
                BigDecimal b = available.get(j).price();
                if (a.compareTo(BigDecimal.ZERO) == 0) continue;
                double dev = a.subtract(b).abs().divide(a, 6, RoundingMode.HALF_UP).doubleValue();
                if (dev > maxDev) maxDev = dev;
            }
        }

        double threshold = configPort.getCrossValThreshold(instrumentId);
        if (threshold == 0.0) threshold = DEFAULT_THRESHOLD;

        boolean flagged = maxDev > threshold;
        String challengeId = null;

        if (flagged) {
            flaggedCounter.increment();
            CrossValResult cv = new CrossValResult(instrumentId, runDate, sources,
                    selectedPrice, selectedSource, true, maxDev, null);
            // Trigger challenge
            var ch = challengeService.manualFlag(instrumentId, runDate, "MODEL_DEVIATION", "SYSTEM").get();
            challengeId = ch.challengeId();
        } else if (maxDev > threshold * 0.5) {
            warningCounter.increment(); // near-threshold warning
        }

        return new CrossValResult(instrumentId, runDate, sources, selectedPrice,
                selectedSource, flagged, maxDev, challengeId);
    }

    private List<SourcePrice> loadSourcePrices(String instrumentId, LocalDate runDate)
            throws SQLException {
        List<SourcePrice> prices = new ArrayList<>();
        String sql = """
                SELECT source, price FROM instrument_prices_multi_source
                WHERE instrument_id = ? AND price_date = ?
                ORDER BY source_priority
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instrumentId);
            ps.setObject(2, runDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PriceSource src = PriceSource.valueOf(rs.getString("source"));
                    BigDecimal price = rs.getBigDecimal("price");
                    prices.add(new SourcePrice(src, price != null ? price : BigDecimal.ZERO, price != null));
                }
            }
        }
        // Ensure all 4 sources represented
        for (PriceSource s : PriceSource.values()) {
            if (prices.stream().noneMatch(p -> p.source() == s)) {
                prices.add(new SourcePrice(s, BigDecimal.ZERO, false));
            }
        }
        return prices;
    }

    private BigDecimal selectPrice(List<SourcePrice> sources) {
        // Priority order
        for (PriceSource src : new PriceSource[]{
                PriceSource.EXCHANGE_OFFICIAL, PriceSource.PRIMARY_FEED,
                PriceSource.SECONDARY_FEED, PriceSource.MODEL}) {
            sources.stream().filter(p -> p.source() == src && p.available())
                    .findFirst().map(SourcePrice::price).ifPresent(p -> {
                        // return from lambda — handled below
                    });
        }
        return sources.stream().filter(SourcePrice::available)
                .sorted((a, b) -> Integer.compare(a.source().ordinal(), b.source().ordinal()))
                .map(SourcePrice::price).findFirst().orElse(BigDecimal.ZERO);
    }

    private PriceSource getSelectedSource(List<SourcePrice> sources) {
        return sources.stream().filter(SourcePrice::available)
                .sorted((a, b) -> Integer.compare(a.source().ordinal(), b.source().ordinal()))
                .map(SourcePrice::source).findFirst().orElse(PriceSource.MODEL);
    }

    private List<String> loadInstruments() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT instrument_id FROM instrument_prices_eod WHERE price_date = CURRENT_DATE";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(rs.getString("instrument_id"));
        }
        return list;
    }
}
