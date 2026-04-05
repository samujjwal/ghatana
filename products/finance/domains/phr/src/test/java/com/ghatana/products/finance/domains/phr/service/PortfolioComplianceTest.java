package com.ghatana.products.finance.domains.phr.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Portfolio Compliance Tests")
class PortfolioComplianceTest {
    private ComplianceService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceService();
    }

    @Test
    @DisplayName("Should validate investment policy compliance")
    void shouldValidateInvestmentPolicyCompliance() {
        service.setPolicy("MAX_EQUITY_ALLOCATION", BigDecimal.valueOf(80.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(75.00));
        boolean compliant = service.checkPolicyCompliance();
        assertThat(compliant).isTrue();
    }

    @Test
    @DisplayName("Should detect policy violations")
    void shouldDetectPolicyViolations() {
        service.setPolicy("MAX_EQUITY_ALLOCATION", BigDecimal.valueOf(70.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(85.00));
        List<ComplianceViolation> violations = service.detectViolations();
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Should enforce concentration limits")
    void shouldEnforceConcentrationLimits() {
        service.setConcentrationLimit(BigDecimal.valueOf(10.00));
        service.addPosition("AAPL", BigDecimal.valueOf(15.00));
        boolean compliant = service.checkConcentrationCompliance();
        assertThat(compliant).isFalse();
    }

    @Test
    @DisplayName("Should validate sector limits")
    void shouldValidateSectorLimits() {
        service.setSectorLimit("Technology", BigDecimal.valueOf(30.00));
        service.addSectorAllocation("Technology", BigDecimal.valueOf(25.00));
        boolean compliant = service.checkSectorCompliance();
        assertThat(compliant).isTrue();
    }

    @Test
    @DisplayName("Should enforce prohibited securities")
    void shouldEnforceProhibitedSecurities() {
        service.addProhibitedSecurity("TOBACCO-1");
        service.addPosition("TOBACCO-1", BigDecimal.valueOf(5.00));
        boolean compliant = service.checkProhibitedSecurities();
        assertThat(compliant).isFalse();
    }

    @Test
    @DisplayName("Should validate ESG criteria")
    void shouldValidateESGCriteria() {
        service.setESGMinScore(70);
        service.addPosition("AAPL", BigDecimal.valueOf(10.00), 85);
        service.addPosition("OIL-1", BigDecimal.valueOf(5.00), 45);
        boolean compliant = service.checkESGCompliance();
        assertThat(compliant).isFalse();
    }

    @Test
    @DisplayName("Should track compliance history")
    void shouldTrackComplianceHistory() {
        service.recordComplianceCheck(true);
        service.recordComplianceCheck(false);
        List<ComplianceRecord> history = service.getComplianceHistory();
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Should generate compliance alerts")
    void shouldGenerateComplianceAlerts() {
        service.setPolicy("MAX_EQUITY_ALLOCATION", BigDecimal.valueOf(70.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(85.00));
        List<ComplianceAlert> alerts = service.generateAlerts();
        assertThat(alerts).isNotEmpty();
    }

    @Test
    @DisplayName("Should validate regulatory requirements")
    void shouldValidateRegulatoryRequirements() {
        service.setRegulatoryRequirement("ERISA", "MAX_SINGLE_ISSUER", BigDecimal.valueOf(10.00));
        service.addPosition("AAPL", BigDecimal.valueOf(8.00));
        boolean compliant = service.checkRegulatoryCompliance("ERISA");
        assertThat(compliant).isTrue();
    }

    @Test
    @DisplayName("Should generate compliance report")
    void shouldGenerateComplianceReport() {
        service.setPolicy("MAX_EQUITY_ALLOCATION", BigDecimal.valueOf(80.00));
        service.setCurrentAllocation("EQUITY", BigDecimal.valueOf(75.00));
        ComplianceReport report = service.generateReport();
        assertThat(report.overallCompliance()).isTrue();
    }

    record ComplianceViolation(String rule, String description, String severity) {}
    record ComplianceRecord(java.time.Instant timestamp, boolean compliant) {}
    record ComplianceAlert(String type, String message, String priority) {}
    record ComplianceReport(boolean overallCompliance, int violationCount, List<String> issues) {}

    static class ComplianceService {
        private final java.util.Map<String, BigDecimal> policies = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> currentAllocations = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> sectorLimits = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> sectorAllocations = new java.util.HashMap<>();
        private final java.util.Set<String> prohibitedSecurities = new java.util.HashSet<>();
        private final java.util.Map<String, Position> positions = new java.util.HashMap<>();
        private final List<ComplianceRecord> history = new java.util.ArrayList<>();
        private final java.util.Map<String, java.util.Map<String, BigDecimal>> regulatoryRequirements = new java.util.HashMap<>();
        private BigDecimal concentrationLimit = BigDecimal.valueOf(10.00);
        private int esgMinScore = 0;

        void setPolicy(String policyName, BigDecimal value) {
            policies.put(policyName, value);
        }

        void setCurrentAllocation(String assetClass, BigDecimal percentage) {
            currentAllocations.put(assetClass, percentage);
        }

        void setConcentrationLimit(BigDecimal limit) {
            this.concentrationLimit = limit;
        }

        void setSectorLimit(String sector, BigDecimal limit) {
            sectorLimits.put(sector, limit);
        }

        void addSectorAllocation(String sector, BigDecimal percentage) {
            sectorAllocations.put(sector, percentage);
        }

        void addProhibitedSecurity(String symbol) {
            prohibitedSecurities.add(symbol);
        }

        void addPosition(String symbol, BigDecimal percentage) {
            positions.put(symbol, new Position(symbol, percentage, 0));
        }

        void addPosition(String symbol, BigDecimal percentage, int esgScore) {
            positions.put(symbol, new Position(symbol, percentage, esgScore));
        }

        void setESGMinScore(int minScore) {
            this.esgMinScore = minScore;
        }

        void setRegulatoryRequirement(String regulation, String requirement, BigDecimal value) {
            regulatoryRequirements.computeIfAbsent(regulation, k -> new java.util.HashMap<>())
                .put(requirement, value);
        }

        boolean checkPolicyCompliance() {
            return policies.entrySet().stream()
                .allMatch(entry -> {
                    String assetClass = entry.getKey().replace("MAX_", "").replace("_ALLOCATION", "");
                    BigDecimal current = currentAllocations.getOrDefault(assetClass, BigDecimal.ZERO);
                    return current.compareTo(entry.getValue()) <= 0;
                });
        }

        List<ComplianceViolation> detectViolations() {
            List<ComplianceViolation> violations = new java.util.ArrayList<>();
            
            policies.forEach((policyName, limit) -> {
                String assetClass = policyName.replace("MAX_", "").replace("_ALLOCATION", "");
                BigDecimal current = currentAllocations.getOrDefault(assetClass, BigDecimal.ZERO);
                if (current.compareTo(limit) > 0) {
                    violations.add(new ComplianceViolation(
                        policyName,
                        String.format("%s allocation %.2f%% exceeds limit %.2f%%", assetClass, current, limit),
                        "HIGH"
                    ));
                }
            });
            
            return violations;
        }

        boolean checkConcentrationCompliance() {
            return positions.values().stream()
                .allMatch(p -> p.percentage().compareTo(concentrationLimit) <= 0);
        }

        boolean checkSectorCompliance() {
            return sectorLimits.entrySet().stream()
                .allMatch(entry -> {
                    BigDecimal current = sectorAllocations.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    return current.compareTo(entry.getValue()) <= 0;
                });
        }

        boolean checkProhibitedSecurities() {
            return positions.keySet().stream()
                .noneMatch(prohibitedSecurities::contains);
        }

        boolean checkESGCompliance() {
            return positions.values().stream()
                .allMatch(p -> p.esgScore() >= esgMinScore);
        }

        void recordComplianceCheck(boolean compliant) {
            history.add(new ComplianceRecord(java.time.Instant.now(), compliant));
        }

        List<ComplianceRecord> getComplianceHistory() {
            return history;
        }

        List<ComplianceAlert> generateAlerts() {
            List<ComplianceAlert> alerts = new java.util.ArrayList<>();
            List<ComplianceViolation> violations = detectViolations();
            
            violations.forEach(v -> {
                alerts.add(new ComplianceAlert(
                    "POLICY_VIOLATION",
                    v.description(),
                    v.severity()
                ));
            });
            
            return alerts;
        }

        boolean checkRegulatoryCompliance(String regulation) {
            java.util.Map<String, BigDecimal> requirements = regulatoryRequirements.get(regulation);
            if (requirements == null) return true;
            
            BigDecimal maxSingleIssuer = requirements.get("MAX_SINGLE_ISSUER");
            if (maxSingleIssuer != null) {
                return positions.values().stream()
                    .allMatch(p -> p.percentage().compareTo(maxSingleIssuer) <= 0);
            }
            
            return true;
        }

        ComplianceReport generateReport() {
            List<ComplianceViolation> violations = detectViolations();
            List<String> issues = violations.stream()
                .map(ComplianceViolation::description)
                .toList();
            
            return new ComplianceReport(violations.isEmpty(), violations.size(), issues);
        }

        record Position(String symbol, BigDecimal percentage, int esgScore) {}
    }
}
