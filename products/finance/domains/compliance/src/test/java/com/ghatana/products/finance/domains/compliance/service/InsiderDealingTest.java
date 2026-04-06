package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for insider dealing and MNPI detection per Compliance-008
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Insider Dealing Tests")
class InsiderDealingTest {
    private InsiderDealingService service;

    @BeforeEach
    void setUp() {
        service = new InsiderDealingService();
    }

    @Test
    @DisplayName("Should detect pre-announcement trading")
    void shouldDetectPreAnnouncementTrading() {
        MaterialEvent event = new MaterialEvent("EVT001", "MERGER", "ACME Corp", LocalDateTime.now());
        List<Trade> trades = List.of(
            new Trade("T1", "TRADER_A", "ACME", 10000, LocalDateTime.now().minusDays(2)),
            new Trade("T2", "TRADER_A", "ACME", 15000, LocalDateTime.now().minusDays(1))
        );
        Alert alert = service.detectPreAnnouncementTrading("TRADER_A", trades, event);
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should monitor restricted list compliance")
    void shouldMonitorRestrictedList() {
        RestrictedList restricted = new RestrictedList(List.of("ACME", "GLOB"), LocalDate.now().minusDays(30), LocalDate.now().plusDays(30));
        Trade trade = new Trade("T3", "EMPLOYEE_1", "ACME", 100, LocalDateTime.now());
        Violation violation = service.checkRestrictedList(trade, restricted);
        assertThat(violation.violation()).isTrue();
    }

    @Test
    @DisplayName("Should detect unusual options activity")
    void shouldDetectUnusualOptionsActivity() {
        List<OptionTrade> options = List.of(
            new OptionTrade("OPT1", "CALL", "ACME", 1000, LocalDateTime.now().minusDays(1)),
            new OptionTrade("OPT2", "CALL", "ACME", 2000, LocalDateTime.now().minusHours(2))
        );
        Alert alert = service.detectUnusualOptionsActivity("TRADER_B", options, "ACME");
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should track employee trading window compliance")
    void shouldTrackTradingWindow() {
        TradingWindow window = new TradingWindow("CLOSED", LocalDate.now().minusDays(5), LocalDate.now().plusDays(10));
        Trade trade = new Trade("T4", "EMP_2", "SYM", 50, LocalDateTime.now());
        Violation violation = service.checkTradingWindow(trade, window);
        assertThat(violation.violation()).isTrue();
    }

    @Test
    @DisplayName("Should detect tipper-tippee relationships")
    void shouldDetectTipperTippee() {
        List<Trade> sourceTrades = List.of(new Trade("T5", "INSIDER", "ACME", 50000, LocalDateTime.now().minusDays(3)));
        List<Trade> recipientTrades = List.of(new Trade("T6", "RECIPIENT", "ACME", 30000, LocalDateTime.now().minusDays(2)));
        Alert alert = service.detectTipperTippee("INSIDER", sourceTrades, "RECIPIENT", recipientTrades);
        assertThat(alert.triggered()).isTrue();
    }

    record MaterialEvent(String id, String type, String issuer, LocalDateTime announcementTime) {}
    record Trade(String id, String trader, String symbol, int quantity, LocalDateTime timestamp) {}
    record Alert(boolean triggered, String type, String description) {}
    record RestrictedList(List<String> symbols, LocalDate effectiveFrom, LocalDate effectiveTo) {}
    record Violation(boolean violation, String rule, String details) {}
    record OptionTrade(String id, String type, String underlying, int quantity, LocalDateTime timestamp) {}
    record TradingWindow(String status, LocalDate openDate, LocalDate closeDate) {}

    static class InsiderDealingService {
        Alert detectPreAnnouncementTrading(String trader, List<Trade> trades, MaterialEvent event) {
            boolean hasPreEvent = trades.stream().anyMatch(t -> t.timestamp().isBefore(event.announcementTime()));
            return new Alert(hasPreEvent, "PRE_ANNOUNCEMENT", "Trading before material event");
        }

        Violation checkRestrictedList(Trade trade, RestrictedList restricted) {
            boolean violation = restricted.symbols().contains(trade.symbol());
            return new Violation(violation, "RESTRICTED_LIST", violation ? "Trading restricted security" : null);
        }

        Alert detectUnusualOptionsActivity(String trader, List<OptionTrade> options, String symbol) {
            int total = options.stream().filter(o -> o.underlying().equals(symbol)).mapToInt(OptionTrade::quantity).sum();
            return new Alert(total > 1000, "UNUSUAL_OPTIONS", "Large options volume");
        }

        Violation checkTradingWindow(Trade trade, TradingWindow window) {
            boolean closed = "CLOSED".equals(window.status());
            return new Violation(closed, "TRADING_WINDOW", closed ? "Trading during closed window" : null);
        }

        Alert detectTipperTippee(String source, List<Trade> sourceTrades, String recipient, List<Trade> recipientTrades) {
            boolean correlation = !sourceTrades.isEmpty() && !recipientTrades.isEmpty() &&
                sourceTrades.get(0).symbol().equals(recipientTrades.get(0).symbol());
            return new Alert(correlation, "TIPPER_TIPPEE", "Potential information sharing");
        }
    }
}
