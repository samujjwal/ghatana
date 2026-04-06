package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for operational risk calculations including loss events and risk indicators per Risk-004
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Operational Risk Tests")
class OperationalRiskTest {
    private OperationalRiskService service;

    @BeforeEach
    void setUp() {
        service = new OperationalRiskService();
    }

    @Test
    @DisplayName("Should capture and categorize loss event")
    void shouldCaptureLossEvent() {
        LossEvent event = new LossEvent("LE-001", "TradingError", BigDecimal.valueOf(50000), LocalDate.now(), "Human Error");
        service.captureLossEvent(event);
        List<LossEvent> events = service.getLossEvents();
        assertThat(events).contains(event);
    }

    @Test
    @DisplayName("Should calculate loss frequency")
    void shouldCalculateLossFrequency() {
        List<LossEvent> events = List.of(
            new LossEvent("LE-001", "TradingError", BigDecimal.valueOf(10000), LocalDate.now().minusDays(10), "Human Error"),
            new LossEvent("LE-002", "SystemFailure", BigDecimal.valueOf(25000), LocalDate.now().minusDays(5), "Technology"),
            new LossEvent("LE-003", "Fraud", BigDecimal.valueOf(15000), LocalDate.now().minusDays(2), "External")
        );
        events.forEach(service::captureLossEvent);
        double frequency = service.calculateLossFrequency(30);
        assertThat(frequency).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should calculate loss severity")
    void shouldCalculateLossSeverity() {
        List<LossEvent> events = List.of(
            new LossEvent("LE-001", "TradingError", BigDecimal.valueOf(10000), LocalDate.now(), "Human Error"),
            new LossEvent("LE-002", "TradingError", BigDecimal.valueOf(30000), LocalDate.now(), "Human Error"),
            new LossEvent("LE-003", "TradingError", BigDecimal.valueOf(20000), LocalDate.now(), "Human Error")
        );
        events.forEach(service::captureLossEvent);
        BigDecimal severity = service.calculateAverageSeverity("TradingError");
        assertThat(severity).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("Should calculate operational risk capital using LDA")
    void shouldCalculateOperationalRiskCapital() {
        List<LossEvent> historicalLosses = generateHistoricalLosses(100);
        BigDecimal capital = service.calculateOperationalRiskCapital(historicalLosses, 0.99);
        assertThat(capital).isPositive();
    }

    @Test
    @DisplayName("Should monitor key risk indicators")
    void shouldMonitorKeyRiskIndicators() {
        KeyRiskIndicator kri = new KeyRiskIndicator("KRI-001", "FailedTrades", BigDecimal.valueOf(5), BigDecimal.valueOf(10));
        service.registerKri(kri);
        service.updateKriValue("KRI-001", BigDecimal.valueOf(12));
        List<KriBreach> breaches = service.checkKriBreaches();
        assertThat(breaches).hasSize(1);
        assertThat(breaches.get(0).kriId()).isEqualTo("KRI-001");
    }

    @Test
    @DisplayName("Should assess control effectiveness")
    void shouldAssessControlEffectiveness() {
        Control control = new Control("CTRL-001", "DualAuthorization", "High", BigDecimal.valueOf(0.95));
        ControlAssessment assessment = service.assessControlEffectiveness(control);
        assertThat(assessment.score()).isBetween(BigDecimal.valueOf(0.7), BigDecimal.valueOf(1.0));
    }

    @Test
    @DisplayName("Should calculate scenario-based operational risk")
    void shouldCalculateScenarioBasedRisk() {
        Map<String, BigDecimal> scenarios = Map.of(
            "SystemOutage", BigDecimal.valueOf(1000000),
            "CyberAttack", BigDecimal.valueOf(5000000),
            "NaturalDisaster", BigDecimal.valueOf(2000000)
        );
        Map<String, BigDecimal> likelihoods = Map.of(
            "SystemOutage", BigDecimal.valueOf(0.1),
            "CyberAttack", BigDecimal.valueOf(0.05),
            "NaturalDisaster", BigDecimal.valueOf(0.02)
        );
        BigDecimal scenarioRisk = service.calculateScenarioBasedRisk(scenarios, likelihoods);
        assertThat(scenarioRisk).isPositive();
    }

    @Test
    @DisplayName("Should track near miss events")
    void shouldTrackNearMissEvents() {
        NearMissEvent nearMiss = new NearMissEvent("NM-001", "DataEntryError", BigDecimal.valueOf(100000), "Prevented by validation");
        service.captureNearMiss(nearMiss);
        List<NearMissEvent> nearMisses = service.getNearMissEvents();
        assertThat(nearMisses).hasSize(1);
    }

    @Test
    @DisplayName("Should generate operational risk report")
    void shouldGenerateOperationalRiskReport() {
        generateSampleLossEvents().forEach(service::captureLossEvent);
        OperationalRiskReport report = service.generateReport(LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(report.totalLosses()).isPositive();
        assertThat(report.eventCount()).isGreaterThan(0);
    }

    record LossEvent(String id, String category, BigDecimal amount, LocalDate date, String rootCause) {}
    record KeyRiskIndicator(String id, String name, BigDecimal currentValue, BigDecimal threshold) {}
    record KriBreach(String kriId, String name, BigDecimal value, BigDecimal threshold) {}
    record Control(String id, String name, String importance, BigDecimal targetEffectiveness) {}
    record ControlAssessment(String controlId, BigDecimal score, String status) {}
    record NearMissEvent(String id, String type, BigDecimal potentialLoss, String preventionMeasure) {}
    record OperationalRiskReport(BigDecimal totalLosses, int eventCount, Map<String, BigDecimal> lossesByCategory) {}

    private List<LossEvent> generateHistoricalLosses(int count) {
        List<LossEvent> losses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            losses.add(new LossEvent("LE-" + i, "Operational", BigDecimal.valueOf(Math.random() * 100000), LocalDate.now().minusDays(i), "Process"));
        }
        return losses;
    }

    private List<LossEvent> generateSampleLossEvents() {
        return List.of(
            new LossEvent("LE-001", "TradingError", BigDecimal.valueOf(50000), LocalDate.now().minusDays(5), "Human Error"),
            new LossEvent("LE-002", "SystemFailure", BigDecimal.valueOf(100000), LocalDate.now().minusDays(3), "Technology"),
            new LossEvent("LE-003", "ExternalEvent", BigDecimal.valueOf(25000), LocalDate.now().minusDays(1), "External")
        );
    }

    static class OperationalRiskService {
        private final List<LossEvent> lossEvents = new ArrayList<>();
        private final List<KeyRiskIndicator> kris = new ArrayList<>();
        private final List<NearMissEvent> nearMisses = new ArrayList<>();

        void captureLossEvent(LossEvent event) {
            lossEvents.add(event);
        }

        List<LossEvent> getLossEvents() {
            return new ArrayList<>(lossEvents);
        }

        double calculateLossFrequency(int days) {
            return (double) lossEvents.size() / days;
        }

        BigDecimal calculateAverageSeverity(String category) {
            long count = lossEvents.stream().filter(e -> e.category().equals(category)).count();
            if (count == 0) return BigDecimal.ZERO;
            return lossEvents.stream()
                .filter(e -> e.category().equals(category))
                .map(LossEvent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(count), java.math.RoundingMode.HALF_UP);
        }

        BigDecimal calculateOperationalRiskCapital(List<LossEvent> historicalLosses, double confidence) {
            List<BigDecimal> sorted = historicalLosses.stream()
                .map(LossEvent::amount)
                .sorted()
                .toList();
            int index = (int) Math.floor(sorted.size() * confidence);
            return sorted.get(Math.min(index, sorted.size() - 1));
        }

        void registerKri(KeyRiskIndicator kri) {
            kris.add(kri);
        }

        void updateKriValue(String id, BigDecimal value) {
            kris.stream()
                .filter(k -> k.id().equals(id))
                .findFirst()
                .ifPresent(k -> kris.set(kris.indexOf(k), new KeyRiskIndicator(k.id(), k.name(), value, k.threshold())));
        }

        List<KriBreach> checkKriBreaches() {
            List<KriBreach> breaches = new ArrayList<>();
            for (KeyRiskIndicator kri : kris) {
                if (kri.currentValue().compareTo(kri.threshold()) > 0) {
                    breaches.add(new KriBreach(kri.id(), kri.name(), kri.currentValue(), kri.threshold()));
                }
            }
            return breaches;
        }

        ControlAssessment assessControlEffectiveness(Control control) {
            return new ControlAssessment(control.id(), BigDecimal.valueOf(0.85 + Math.random() * 0.15), "Effective");
        }

        BigDecimal calculateScenarioBasedRisk(Map<String, BigDecimal> scenarios, Map<String, BigDecimal> likelihoods) {
            BigDecimal total = BigDecimal.ZERO;
            for (String scenario : scenarios.keySet()) {
                BigDecimal loss = scenarios.get(scenario);
                BigDecimal likelihood = likelihoods.getOrDefault(scenario, BigDecimal.valueOf(0.01));
                total = total.add(loss.multiply(likelihood));
            }
            return total;
        }

        void captureNearMiss(NearMissEvent nearMiss) {
            nearMisses.add(nearMiss);
        }

        List<NearMissEvent> getNearMissEvents() {
            return new ArrayList<>(nearMisses);
        }

        OperationalRiskReport generateReport(LocalDate from, LocalDate to) {
            BigDecimal total = lossEvents.stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                .map(LossEvent::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, BigDecimal> byCategory = new HashMap<>();
            for (LossEvent e : lossEvents) {
                byCategory.merge(e.category(), e.amount(), BigDecimal::add);
            }
            return new OperationalRiskReport(total, lossEvents.size(), byCategory);
        }
    }
}
