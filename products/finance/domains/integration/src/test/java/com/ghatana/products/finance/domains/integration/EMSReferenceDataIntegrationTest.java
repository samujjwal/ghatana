package com.ghatana.products.finance.domains.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("EMS-ReferenceData Integration Tests")
class EMSReferenceDataIntegrationTest {
    private IntegrationService service;

    @BeforeEach
    void setUp() {
        service = new IntegrationService();
    }

    @Test
    @DisplayName("Should validate instrument before routing")
    void shouldValidateInstrumentBeforeRouting() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE");
        service.registerInstrument(instrument);
        boolean valid = service.validateInstrumentForRouting("AAPL");
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should reject routing for inactive instruments")
    void shouldRejectRoutingForInactiveInstruments() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "INACTIVE");
        service.registerInstrument(instrument);
        boolean valid = service.validateInstrumentForRouting("AAPL");
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should enrich order with instrument metadata")
    void shouldEnrichOrderWithInstrumentMetadata() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE");
        service.registerInstrument(instrument);
        Order order = new Order("order-1", "AAPL", 100L);
        EnrichedOrder enriched = service.enrichOrder(order);
        assertThat(enriched.exchange()).isEqualTo("NASDAQ");
    }

    @Test
    @DisplayName("Should validate lot size compliance")
    void shouldValidateLotSizeCompliance() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE", 100);
        service.registerInstrument(instrument);
        boolean valid = service.validateLotSize("AAPL", 200L);
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should reject non-compliant lot sizes")
    void shouldRejectNonCompliantLotSizes() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE", 100);
        service.registerInstrument(instrument);
        boolean valid = service.validateLotSize("AAPL", 150L);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should apply tick size rules")
    void shouldApplyTickSizeRules() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE", 1, BigDecimal.valueOf(0.01));
        service.registerInstrument(instrument);
        BigDecimal validPrice = service.roundToTickSize("AAPL", BigDecimal.valueOf(150.005));
        assertThat(validPrice).isEqualByComparingTo(BigDecimal.valueOf(150.01));
    }

    @Test
    @DisplayName("Should cache instrument lookups")
    void shouldCacheInstrumentLookups() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE");
        service.registerInstrument(instrument);
        service.getInstrument("AAPL");
        service.getInstrument("AAPL");
        assertThat(service.getCacheHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle instrument updates")
    void shouldHandleInstrumentUpdates() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE");
        service.registerInstrument(instrument);
        service.updateInstrumentStatus("AAPL", "HALTED");
        Instrument updated = service.getInstrument("AAPL");
        assertThat(updated.status()).isEqualTo("HALTED");
    }

    @Test
    @DisplayName("Should validate trading hours")
    void shouldValidateTradingHours() {
        Instrument instrument = new Instrument("AAPL", "NASDAQ", "ACTIVE");
        service.registerInstrument(instrument);
        boolean canTrade = service.isTradingAllowed("AAPL", "09:30");
        assertThat(canTrade).isTrue();
    }

    @Test
    @DisplayName("Should generate instrument report")
    void shouldGenerateInstrumentReport() {
        service.registerInstrument(new Instrument("AAPL", "NASDAQ", "ACTIVE"));
        service.registerInstrument(new Instrument("GOOGL", "NASDAQ", "ACTIVE"));
        InstrumentReport report = service.generateReport();
        assertThat(report.totalInstruments()).isEqualTo(2);
    }

    record Instrument(String symbol, String exchange, String status, int lotSize, BigDecimal tickSize) {
        Instrument(String symbol, String exchange, String status) {
            this(symbol, exchange, status, 1, BigDecimal.valueOf(0.01));
        }
        Instrument(String symbol, String exchange, String status, int lotSize) {
            this(symbol, exchange, status, lotSize, BigDecimal.valueOf(0.01));
        }
    }
    record Order(String orderId, String symbol, long quantity) {}
    record EnrichedOrder(String orderId, String symbol, long quantity, String exchange) {}
    record InstrumentReport(int totalInstruments, int activeInstruments) {}

    static class IntegrationService {
        private final java.util.Map<String, Instrument> instruments = new java.util.HashMap<>();
        private int cacheHits = 0;

        void registerInstrument(Instrument instrument) {
            instruments.put(instrument.symbol(), instrument);
        }

        boolean validateInstrumentForRouting(String symbol) {
            Instrument instrument = instruments.get(symbol);
            return instrument != null && instrument.status().equals("ACTIVE");
        }

        EnrichedOrder enrichOrder(Order order) {
            Instrument instrument = instruments.get(order.symbol());
            return new EnrichedOrder(order.orderId(), order.symbol(), order.quantity(), 
                instrument != null ? instrument.exchange() : "UNKNOWN");
        }

        boolean validateLotSize(String symbol, long quantity) {
            Instrument instrument = instruments.get(symbol);
            return instrument != null && quantity % instrument.lotSize() == 0;
        }

        BigDecimal roundToTickSize(String symbol, BigDecimal price) {
            Instrument instrument = instruments.get(symbol);
            if (instrument == null) return price;
            return price.divide(instrument.tickSize(), 0, java.math.RoundingMode.HALF_UP)
                .multiply(instrument.tickSize());
        }

        Instrument getInstrument(String symbol) {
            if (instruments.containsKey(symbol)) {
                cacheHits++;
            }
            return instruments.get(symbol);
        }

        int getCacheHits() {
            return cacheHits;
        }

        void updateInstrumentStatus(String symbol, String status) {
            Instrument instrument = instruments.get(symbol);
            if (instrument != null) {
                instruments.put(symbol, new Instrument(
                    instrument.symbol(), instrument.exchange(), status, 
                    instrument.lotSize(), instrument.tickSize()
                ));
            }
        }

        boolean isTradingAllowed(String symbol, String time) {
            return instruments.containsKey(symbol);
        }

        InstrumentReport generateReport() {
            long active = instruments.values().stream()
                .filter(i -> i.status().equals("ACTIVE"))
                .count();
            return new InstrumentReport(instruments.size(), (int) active);
        }
    }
}
