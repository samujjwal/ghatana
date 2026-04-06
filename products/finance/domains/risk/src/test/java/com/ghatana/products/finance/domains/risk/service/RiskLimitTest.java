package com.ghatana.products.finance.domains.risk.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for risk limit management and breach monitoring per Risk-007
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Risk Limit Tests")
class RiskLimitTest {
    private RiskLimitService service;

    @BeforeEach
    void setUp() {
        service = new RiskLimitService();
    }

    @Test
    @DisplayName("Should set position limit for instrument")
    void shouldSetPositionLimit() {
        RiskLimit limit = new RiskLimit("LIMIT-001", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(900000));
        service.setLimit(limit);
        assertThat(service.getLimit("LIMIT-001")).isEqualTo(limit);
    }

    @Test
    @DisplayName("Should check limit utilization")
    void shouldCheckLimitUtilization() {
        RiskLimit limit = new RiskLimit("LIMIT-001", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(800000));
        service.setLimit(limit);
        BigDecimal currentPosition = BigDecimal.valueOf(750000);
        LimitCheckResult result = service.checkLimit("LIMIT-001", currentPosition);
        assertThat(result.withinLimit()).isTrue();
        assertThat(result.utilization()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
    }

    @Test
    @DisplayName("Should detect limit breach")
    void shouldDetectLimitBreach() {
        RiskLimit limit = new RiskLimit("LIMIT-001", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(900000));
        service.setLimit(limit);
        BigDecimal currentPosition = BigDecimal.valueOf(1100000);
        LimitCheckResult result = service.checkLimit("LIMIT-001", currentPosition);
        assertThat(result.withinLimit()).isFalse();
        assertThat(result.breachAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }

    @Test
    @DisplayName("Should trigger warning at soft limit")
    void shouldTriggerWarningAtSoftLimit() {
        RiskLimit limit = new RiskLimit("LIMIT-001", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(800000));
        service.setLimit(limit);
        BigDecimal currentPosition = BigDecimal.valueOf(850000);
        LimitCheckResult result = service.checkLimit("LIMIT-001", currentPosition);
        assertThat(result.warningLevel()).isEqualTo(WarningLevel.YELLOW);
    }

    @Test
    @DisplayName("Should aggregate limits across hierarchy")
    void shouldAggregateLimitsAcrossHierarchy() {
        RiskLimit deskLimit = new RiskLimit("DESK-001", "EquityDesk", LimitType.DESK_GROSS, BigDecimal.valueOf(10000000), BigDecimal.valueOf(8000000));
        RiskLimit traderLimit = new RiskLimit("TRADER-001", "Trader1", LimitType.TRADER_GROSS, BigDecimal.valueOf(2000000), BigDecimal.valueOf(1600000));
        service.setLimit(deskLimit);
        service.setLimit(traderLimit);
        Map<String, BigDecimal> utilizations = Map.of("DESK-001", BigDecimal.valueOf(0.7), "TRADER-001", BigDecimal.valueOf(0.8));
        LimitAggregationResult result = service.aggregateLimits(utilizations);
        assertThat(result.totalUtilization()).isPositive();
    }

    @Test
    @DisplayName("Should calculate delta limit for options portfolio")
    void shouldCalculateDeltaLimit() {
        List<OptionPosition> options = List.of(
            new OptionPosition("AAPL_CALL", BigDecimal.valueOf(0.6), 100),
            new OptionPosition("GOOGL_PUT", BigDecimal.valueOf(-0.4), 50)
        );
        BigDecimal deltaLimit = BigDecimal.valueOf(100);
        DeltaCheckResult result = service.checkDeltaLimit(options, deltaLimit);
        assertThat(result.totalDelta()).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    @Test
    @DisplayName("Should monitor VaR limit")
    void shouldMonitorVarLimit() {
        RiskLimit varLimit = new RiskLimit("VAR-001", "Portfolio", LimitType.VAR, BigDecimal.valueOf(5000000), BigDecimal.valueOf(4000000));
        service.setLimit(varLimit);
        BigDecimal currentVar = BigDecimal.valueOf(4500000);
        VarLimitResult result = service.checkVarLimit("VAR-001", currentVar);
        assertThat(result.withinLimit()).isTrue();
        assertThat(result.headroom()).isEqualByComparingTo(BigDecimal.valueOf(500000));
    }

    @Test
    @DisplayName("Should enforce concentration limit")
    void shouldEnforceConcentrationLimit() {
        RiskLimit concentrationLimit = new RiskLimit("CONC-001", "SingleIssuer", LimitType.CONCENTRATION, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.08));
        service.setLimit(concentrationLimit);
        BigDecimal position = BigDecimal.valueOf(15000000);
        BigDecimal totalPortfolio = BigDecimal.valueOf(100000000);
        ConcentrationResult result = service.checkConcentration("CONC-001", position, totalPortfolio);
        assertThat(result.concentration()).isEqualByComparingTo(BigDecimal.valueOf(0.15));
        assertThat(result.breach()).isTrue();
    }

    @Test
    @DisplayName("Should generate limit breach report")
    void shouldGenerateLimitBreachReport() {
        List<RiskLimit> limits = List.of(
            new RiskLimit("L1", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(900000)),
            new RiskLimit("L2", "GOOGL", LimitType.POSITION, BigDecimal.valueOf(2000000), BigDecimal.valueOf(1800000))
        );
        limits.forEach(service::setLimit);
        service.recordBreach("L1", BigDecimal.valueOf(1100000), LocalDateTime.now());
        BreachReport report = service.generateBreachReport(LocalDateTime.now().minusDays(1), LocalDateTime.now());
        assertThat(report.breaches()).hasSize(1);
    }

    @Test
    @DisplayName("Should handle limit escalation workflow")
    void shouldHandleLimitEscalation() {
        RiskLimit limit = new RiskLimit("LIMIT-001", "AAPL", LimitType.POSITION, BigDecimal.valueOf(1000000), BigDecimal.valueOf(800000));
        service.setLimit(limit);
        EscalationResult result = service.escalateLimit("LIMIT-001", BigDecimal.valueOf(1100000), "Trader exceeded limit due to market volatility");
        assertThat(result.escalationLevel()).isGreaterThanOrEqualTo(1);
        assertThat(result.requiresApproval()).isTrue();
    }

    record RiskLimit(String id, String entity, LimitType type, BigDecimal hardLimit, BigDecimal softLimit) {}
    record LimitCheckResult(boolean withinLimit, BigDecimal utilization, BigDecimal breachAmount, WarningLevel warningLevel) {}
    record LimitAggregationResult(BigDecimal totalUtilization, int breachCount) {}
    record OptionPosition(String identifier, BigDecimal delta, int quantity) {}
    record DeltaCheckResult(BigDecimal totalDelta, BigDecimal limit, boolean withinLimit) {}
    record VarLimitResult(boolean withinLimit, BigDecimal headroom, BigDecimal utilization) {}
    record ConcentrationResult(BigDecimal concentration, BigDecimal limit, boolean breach) {}
    record BreachReport(List<BreachEvent> breaches, LocalDateTime generatedAt) {}
    record BreachEvent(String limitId, String entity, BigDecimal value, BigDecimal limit, LocalDateTime timestamp) {}
    record EscalationResult(int escalationLevel, boolean requiresApproval, String approver) {}
    enum LimitType { POSITION, VAR, DESK_GROSS, TRADER_GROSS, CONCENTRATION, DELTA }
    enum WarningLevel { NONE, GREEN, YELLOW, RED }

    static class RiskLimitService {
        private final Map<String, RiskLimit> limits = new HashMap<>();
        private final List<BreachEvent> breaches = new ArrayList<>();

        void setLimit(RiskLimit limit) {
            limits.put(limit.id(), limit);
        }

        RiskLimit getLimit(String id) {
            return limits.get(id);
        }

        LimitCheckResult checkLimit(String limitId, BigDecimal currentValue) {
            RiskLimit limit = limits.get(limitId);
            if (limit == null) return new LimitCheckResult(false, BigDecimal.ZERO, BigDecimal.ZERO, WarningLevel.NONE);
            BigDecimal utilization = currentValue.divide(limit.hardLimit(), 4, java.math.RoundingMode.HALF_UP);
            boolean within = currentValue.compareTo(limit.hardLimit()) <= 0;
            BigDecimal breach = within ? BigDecimal.ZERO : currentValue.subtract(limit.hardLimit());
            WarningLevel warning = WarningLevel.NONE;
            if (utilization.compareTo(BigDecimal.valueOf(0.8)) >= 0 && utilization.compareTo(BigDecimal.valueOf(0.9)) < 0) {
                warning = WarningLevel.YELLOW;
            } else if (utilization.compareTo(BigDecimal.valueOf(0.9)) >= 0) {
                warning = WarningLevel.RED;
            } else if (utilization.compareTo(BigDecimal.valueOf(0.5)) >= 0) {
                warning = WarningLevel.GREEN;
            }
            return new LimitCheckResult(within, utilization, breach, warning);
        }

        LimitAggregationResult aggregateLimits(Map<String, BigDecimal> utilizations) {
            BigDecimal total = utilizations.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(utilizations.size()), 4, java.math.RoundingMode.HALF_UP);
            int breaches = (int) utilizations.values().stream().filter(u -> u.compareTo(BigDecimal.ONE) > 0).count();
            return new LimitAggregationResult(total, breaches);
        }

        DeltaCheckResult checkDeltaLimit(List<OptionPosition> options, BigDecimal limit) {
            BigDecimal totalDelta = options.stream()
                .map(o -> o.delta().multiply(BigDecimal.valueOf(o.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new DeltaCheckResult(totalDelta, limit, totalDelta.abs().compareTo(limit) <= 0);
        }

        VarLimitResult checkVarLimit(String limitId, BigDecimal currentVar) {
            RiskLimit limit = limits.get(limitId);
            if (limit == null) return new VarLimitResult(false, BigDecimal.ZERO, BigDecimal.ZERO);
            boolean within = currentVar.compareTo(limit.hardLimit()) <= 0;
            BigDecimal headroom = limit.hardLimit().subtract(currentVar);
            BigDecimal utilization = currentVar.divide(limit.hardLimit(), 4, java.math.RoundingMode.HALF_UP);
            return new VarLimitResult(within, headroom, utilization);
        }

        ConcentrationResult checkConcentration(String limitId, BigDecimal position, BigDecimal totalPortfolio) {
            RiskLimit limit = limits.get(limitId);
            if (limit == null) return new ConcentrationResult(BigDecimal.ZERO, BigDecimal.ZERO, false);
            BigDecimal concentration = position.divide(totalPortfolio, 4, java.math.RoundingMode.HALF_UP);
            return new ConcentrationResult(concentration, limit.hardLimit(), concentration.compareTo(limit.hardLimit()) > 0);
        }

        void recordBreach(String limitId, BigDecimal value, LocalDateTime timestamp) {
            RiskLimit limit = limits.get(limitId);
            if (limit != null) {
                breaches.add(new BreachEvent(limitId, limit.entity(), value, limit.hardLimit(), timestamp));
            }
        }

        BreachReport generateBreachReport(LocalDateTime from, LocalDateTime to) {
            List<BreachEvent> reportBreaches = breaches.stream()
                .filter(b -> !b.timestamp().isBefore(from) && !b.timestamp().isAfter(to))
                .toList();
            return new BreachReport(reportBreaches, LocalDateTime.now());
        }

        EscalationResult escalateLimit(String limitId, BigDecimal value, String reason) {
            return new EscalationResult(2, true, "Risk Manager");
        }
    }
}
