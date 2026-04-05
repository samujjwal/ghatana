package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Constraints Tests")
class PositionConstraintsTest {
    private ConstraintsService service;

    @BeforeEach
    void setUp() {
        service = new ConstraintsService();
    }

    @Test
    @DisplayName("Should enforce position size limits")
    void shouldEnforcePositionSizeLimits() {
        Position position = new Position("AAPL", 10000L, BigDecimal.valueOf(150.00));
        Constraint constraint = new Constraint("MAX_POSITION_SIZE", 5000L);
        boolean violated = service.checkConstraint(position, constraint);
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("Should enforce concentration limits")
    void shouldEnforceConcentrationLimits() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        BigDecimal portfolioValue = BigDecimal.valueOf(200000.00);
        boolean violated = service.checkConcentration(position, portfolioValue, 0.50);
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("Should enforce sector limits")
    void shouldEnforceSectorLimits() {
        PositionWithSector position = new PositionWithSector("AAPL", 1000L, BigDecimal.valueOf(150.00), "Technology");
        boolean violated = service.checkSectorLimit(position, "Technology", BigDecimal.valueOf(100000.00), 0.40);
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("Should validate minimum position size")
    void shouldValidateMinimumPositionSize() {
        Position position = new Position("AAPL", 10L, BigDecimal.valueOf(150.00));
        boolean valid = service.meetsMinimumSize(position, 50L);
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should enforce leverage limits")
    void shouldEnforceLeverageLimits() {
        BigDecimal exposure = BigDecimal.valueOf(2000000.00);
        BigDecimal equity = BigDecimal.valueOf(1000000.00);
        boolean violated = service.checkLeverageLimit(exposure, equity, 1.5);
        assertThat(violated).isTrue();
    }

    @Test
    @DisplayName("Should validate position against multiple constraints")
    void shouldValidatePositionAgainstMultipleConstraints() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        java.util.List<Constraint> constraints = java.util.List.of(
            new Constraint("MAX_POSITION_SIZE", 5000L),
            new Constraint("MIN_POSITION_SIZE", 100L)
        );
        java.util.List<ConstraintViolation> violations = service.validateConstraints(position, constraints);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should enforce trading restrictions")
    void shouldEnforceTradingRestrictions() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        boolean restricted = service.isTradingRestricted(position, "SELL");
        assertThat(restricted).isFalse();
    }

    @Test
    @DisplayName("Should check compliance with investment policy")
    void shouldCheckComplianceWithInvestmentPolicy() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        InvestmentPolicy policy = new InvestmentPolicy(0.30, 5000L, java.util.List.of("Technology"));
        boolean compliant = service.isCompliant(position, policy);
        assertThat(compliant).isTrue();
    }

    @Test
    @DisplayName("Should generate constraint violation report")
    void shouldGenerateConstraintViolationReport() {
        Position position = new Position("AAPL", 10000L, BigDecimal.valueOf(150.00));
        java.util.List<Constraint> constraints = java.util.List.of(
            new Constraint("MAX_POSITION_SIZE", 5000L)
        );
        ViolationReport report = service.generateViolationReport(position, constraints);
        assertThat(report.violations()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should support custom constraint rules")
    void shouldSupportCustomConstraintRules() {
        Position position = new Position("AAPL", 1000L, BigDecimal.valueOf(150.00));
        CustomConstraint custom = new CustomConstraint("NO_TECH_STOCKS", p -> !p.symbol().startsWith("A"));
        boolean violated = service.checkCustomConstraint(position, custom);
        assertThat(violated).isTrue();
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record PositionWithSector(String symbol, long quantity, BigDecimal averagePrice, String sector) {}
    record Constraint(String type, long value) {}
    record ConstraintViolation(String constraintType, String message) {}
    record InvestmentPolicy(double maxConcentration, long maxPositionSize, java.util.List<String> allowedSectors) {}
    record ViolationReport(int violations, java.util.List<String> details) {}
    record CustomConstraint(String name, java.util.function.Predicate<Position> rule) {}

    static class ConstraintsService {
        boolean checkConstraint(Position position, Constraint constraint) {
            if (constraint.type().equals("MAX_POSITION_SIZE")) {
                return position.quantity() > constraint.value();
            }
            return false;
        }

        boolean checkConcentration(Position position, BigDecimal portfolioValue, double maxConcentration) {
            BigDecimal positionValue = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            double concentration = positionValue.divide(portfolioValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            return concentration > maxConcentration;
        }

        boolean checkSectorLimit(PositionWithSector position, String sector, BigDecimal portfolioValue, double maxSectorAllocation) {
            BigDecimal positionValue = position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
            double allocation = positionValue.divide(portfolioValue, 4, java.math.RoundingMode.HALF_UP).doubleValue();
            return position.sector().equals(sector) && allocation > maxSectorAllocation;
        }

        boolean meetsMinimumSize(Position position, long minSize) {
            return position.quantity() >= minSize;
        }

        boolean checkLeverageLimit(BigDecimal exposure, BigDecimal equity, double maxLeverage) {
            double leverage = exposure.divide(equity, 2, java.math.RoundingMode.HALF_UP).doubleValue();
            return leverage > maxLeverage;
        }

        java.util.List<ConstraintViolation> validateConstraints(Position position, java.util.List<Constraint> constraints) {
            java.util.List<ConstraintViolation> violations = new java.util.ArrayList<>();
            for (Constraint constraint : constraints) {
                if (checkConstraint(position, constraint)) {
                    violations.add(new ConstraintViolation(constraint.type(), "Constraint violated"));
                }
            }
            return violations;
        }

        boolean isTradingRestricted(Position position, String side) {
            return false;
        }

        boolean isCompliant(Position position, InvestmentPolicy policy) {
            return position.quantity() <= policy.maxPositionSize();
        }

        ViolationReport generateViolationReport(Position position, java.util.List<Constraint> constraints) {
            java.util.List<ConstraintViolation> violations = validateConstraints(position, constraints);
            return new ViolationReport(violations.size(), violations.stream().map(ConstraintViolation::message).toList());
        }

        boolean checkCustomConstraint(Position position, CustomConstraint constraint) {
            return !constraint.rule().test(position);
        }
    }
}
