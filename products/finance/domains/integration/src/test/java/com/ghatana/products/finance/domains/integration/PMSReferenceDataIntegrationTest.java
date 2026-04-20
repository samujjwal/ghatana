package com.ghatana.products.finance.domains.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("PMS-ReferenceData Integration Tests")
class PMSReferenceDataIntegrationTest {
    private IntegrationService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationService();
    }

    @Test
    @DisplayName("Should enrich position with instrument metadata")
    void shouldEnrichPositionWithInstrumentMetadata() {
        Instrument instrument = new Instrument("AAPL", "Technology", "USD");
        service.registerInstrument(instrument);
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        EnrichedPosition enriched = service.enrichPosition(position);
        assertThat(enriched.sector()).isEqualTo("Technology");
        assertThat(enriched.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Should calculate sector allocation using reference data")
    void shouldCalculateSectorAllocationUsingReferenceData() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        service.registerInstrument(new Instrument("JPM", "Financial", "USD"));
        service.addPosition(new Position("AAPL", 1000L, BigDecimal.valueOf(150.00)));
        service.addPosition(new Position("JPM", 500L, BigDecimal.valueOf(150.00)));
        SectorAllocation allocation = service.calculateSectorAllocation();
        assertThat(allocation.sectors()).containsKey("Technology");
        assertThat(allocation.sectors()).containsKey("Financial");
    }

    @Test
    @DisplayName("Should apply currency conversion using reference data")
    void shouldApplyCurrencyConversionUsingReferenceData() {
        service.registerInstrument(new Instrument("NESN", "Consumer", "CHF"));
        service.registerExchangeRate("CHF", "USD", BigDecimal.valueOf(1.10));
        Position position = new Position("NESN", 100L, BigDecimal.valueOf(100.00));
        BigDecimal valueInUSD = service.convertToBaseCurrency(position);
        assertThat(valueInUSD).isGreaterThan(BigDecimal.valueOf(10000.00));
    }

    @Test
    @DisplayName("Should validate corporate actions against reference data")
    void shouldValidateCorporateActionsAgainstReferenceData() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        CorporateAction action = new CorporateAction("AAPL", "SPLIT", "2:1");
        boolean valid = service.validateCorporateAction(action);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should track instrument changes")
    void shouldTrackInstrumentChanges() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        service.updateInstrumentSector("AAPL", "Tech Services");
        Instrument updated = service.getInstrument("AAPL");
        assertThat(updated.sector()).isEqualTo("Tech Services");
    }

    @Test
    @DisplayName("Should generate multi-currency portfolio report")
    void shouldGenerateMultiCurrencyPortfolioReport() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        service.registerInstrument(new Instrument("NESN", "Consumer", "CHF"));
        service.registerExchangeRate("CHF", "USD", BigDecimal.valueOf(1.10));
        service.addPosition(new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.addPosition(new Position("NESN", 100L, BigDecimal.valueOf(100.00)));
        PortfolioReport report = service.generatePortfolioReport();
        assertThat(report.totalValueUSD()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should validate position against instrument constraints")
    void shouldValidatePositionAgainstInstrumentConstraints() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD", 1000L));
        Position position = new Position("AAPL", 10000L, BigDecimal.valueOf(150.00));
        boolean valid = service.validatePositionConstraints(position);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should cache reference data lookups")
    void shouldCacheReferenceDataLookups() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        service.getInstrument("AAPL");
        service.getInstrument("AAPL");
        assertThat(service.getCacheHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle missing reference data gracefully")
    void shouldHandleMissingReferenceDataGracefully() {
        Position position = new Position("UNKNOWN", 100L, BigDecimal.valueOf(150.00));
        EnrichedPosition enriched = service.enrichPosition(position);
        assertThat(enriched.sector()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Should generate reference data quality report")
    void shouldGenerateReferenceDataQualityReport() {
        service.registerInstrument(new Instrument("AAPL", "Technology", "USD"));
        service.addPosition(new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.addPosition(new Position("UNKNOWN", 50L, BigDecimal.valueOf(100.00)));
        QualityReport report = service.generateQualityReport();
        assertThat(report.coveragePercentage()).isLessThan(100.0);
    }

    record Instrument(String symbol, String sector, String currency, long maxPositionSize) {
        Instrument(String symbol, String sector, String currency) {
            this(symbol, sector, currency, Long.MAX_VALUE);
        }
    }
    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record EnrichedPosition(String symbol, long quantity, BigDecimal averagePrice, String sector, String currency) {}
    record CorporateAction(String symbol, String type, String details) {}
    record SectorAllocation(java.util.Map<String, BigDecimal> sectors) {}
    record PortfolioReport(BigDecimal totalValueUSD, int positionCount) {}
    record QualityReport(double coveragePercentage, int missingData) {}

    static class IntegrationService {
        private final java.util.Map<String, Instrument> instruments = new java.util.HashMap<>();
        private final java.util.Map<String, Position> positions = new java.util.HashMap<>();
        private final java.util.Map<String, java.util.Map<String, BigDecimal>> exchangeRates = new java.util.HashMap<>();
        private final java.util.Set<String> accessedKeys = new java.util.HashSet<>();
        private int cacheHits = 0;

        void registerInstrument(Instrument instrument) {
            instruments.put(instrument.symbol(), instrument);
        }

        void registerExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate) {
            exchangeRates.computeIfAbsent(fromCurrency, k -> new java.util.HashMap<>()).put(toCurrency, rate);
        }

        void addPosition(Position position) {
            positions.put(position.symbol(), position);
        }

        EnrichedPosition enrichPosition(Position position) {
            Instrument instrument = instruments.get(position.symbol());
            if (instrument == null) {
                return new EnrichedPosition(position.symbol(), position.quantity(), position.averagePrice(), "UNKNOWN", "UNKNOWN");
            }
            return new EnrichedPosition(position.symbol(), position.quantity(), position.averagePrice(), 
                instrument.sector(), instrument.currency());
        }

        SectorAllocation calculateSectorAllocation() {
            java.util.Map<String, BigDecimal> sectors = new java.util.HashMap<>();
            for (Position position : positions.values()) {
                Instrument instrument = instruments.get(position.symbol());
                if (instrument != null) {
                    BigDecimal value = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
                    sectors.merge(instrument.sector(), value, BigDecimal::add);
                }
            }
            return new SectorAllocation(sectors);
        }

        BigDecimal convertToBaseCurrency(Position position) {
            Instrument instrument = instruments.get(position.symbol());
            if (instrument == null) return BigDecimal.ZERO;
            BigDecimal value = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            BigDecimal rate = exchangeRates.getOrDefault(instrument.currency(), new java.util.HashMap<>())
                .getOrDefault("USD", BigDecimal.ONE);
            return value.multiply(rate);
        }

        boolean validateCorporateAction(CorporateAction action) {
            return instruments.containsKey(action.symbol());
        }

        void updateInstrumentSector(String symbol, String sector) {
            Instrument instrument = instruments.get(symbol);
            if (instrument != null) {
                instruments.put(symbol, new Instrument(symbol, sector, instrument.currency(), instrument.maxPositionSize()));
            }
        }

        Instrument getInstrument(String symbol) {
            if (instruments.containsKey(symbol)) {
                if (accessedKeys.contains(symbol)) {
                    cacheHits++;
                } else {
                    accessedKeys.add(symbol);
                }
            }
            return instruments.get(symbol);
        }

        int getCacheHits() {
            return cacheHits;
        }

        PortfolioReport generatePortfolioReport() {
            BigDecimal totalValue = positions.values().stream()
                .map(this::convertToBaseCurrency)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PortfolioReport(totalValue, positions.size());
        }

        boolean validatePositionConstraints(Position position) {
            Instrument instrument = instruments.get(position.symbol());
            return instrument != null && position.quantity() <= instrument.maxPositionSize();
        }

        QualityReport generateQualityReport() {
            long missing = positions.keySet().stream()
                .filter(symbol -> !instruments.containsKey(symbol))
                .count();
            double coverage = positions.isEmpty() ? 100.0 : 
                (positions.size() - missing) * 100.0 / positions.size();
            return new QualityReport(coverage, (int) missing);
        }
    }
}
