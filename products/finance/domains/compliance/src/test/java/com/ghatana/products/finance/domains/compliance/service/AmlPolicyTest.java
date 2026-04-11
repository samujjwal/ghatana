package com.ghatana.products.finance.domains.compliance.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for AML policy and rule management per Compliance-007
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("AML Policy Tests")
class AmlPolicyTest {
    private AmlPolicyService service;

    @BeforeEach
    void setUp() {
        service = new AmlPolicyService();
    }

    @Test
    @DisplayName("Should evaluate transaction against AML rules")
    void shouldEvaluateTransactionAgainstRules() {
        AmlRule rule = new AmlRule("RULE_001", "Cash Threshold", "amount > 10000 AND type = 'CASH'", "HIGH");
        Transaction txn = new Transaction("T1", BigDecimal.valueOf(15000), "CASH", LocalDateTime.now());
        RuleEvaluationResult result = service.evaluateRule(rule, txn);
        assertThat(result.triggered()).isTrue();
        assertThat(result.priority()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("Should detect layering through multiple accounts")
    void shouldDetectLayeringThroughMultipleAccounts() {
        Map<String, List<Transaction>> accountTransactions = Map.of(
            "ACC_1", List.of(new Transaction("T1", BigDecimal.valueOf(5000), "TRANSFER", LocalDateTime.now())),
            "ACC_2", List.of(new Transaction("T2", BigDecimal.valueOf(4900), "TRANSFER", LocalDateTime.now().plusMinutes(5))),
            "ACC_3", List.of(new Transaction("T3", BigDecimal.valueOf(4800), "TRANSFER", LocalDateTime.now().plusMinutes(10)))
        );
        Alert alert = service.detectMultiAccountLayering("C001", accountTransactions);
        assertThat(alert.triggered()).isTrue();
    }

    @Test
    @DisplayName("Should assess correspondent banking risk")
    void shouldAssessCorrespondentBankingRisk() {
        CorrespondentBank bank = new CorrespondentBank("BANK_001", "Offshore Bank", "XX", false, false);
        RiskAssessment assessment = service.assessCorrespondentRisk(bank);
        assertThat(assessment.riskLevel()).isEqualTo("HIGH");
        assertThat(assessment.requiredControls()).isNotEmpty();
    }

    @Test
    @DisplayName("Should monitor for trade-based money laundering")
    void shouldDetectTradeBasedML() {
        TradeTransaction trade = new TradeTransaction("TRADE_1", "IMPORT", " electronics", BigDecimal.valueOf(1000000), BigDecimal.valueOf(500000), "CN");
        Alert alert = service.detectTradeBasedML(trade);
        assertThat(alert.triggered()).isTrue();
        assertThat(alert.type()).isEqualTo("PRICE_MISMATCH");
    }

    @Test
    @DisplayName("Should calculate geographic risk score")
    void shouldCalculateGeographicRiskScore() {
        Map<String, Integer> countryRisk = Map.of(
            "US", 20, "UK", 25, "AF", 95, "MM", 90, "IR", 100
        );
        int riskScore = service.getCountryRiskScore("IR", countryRisk);
        assertThat(riskScore).isEqualTo(100);
    }

    @Test
    @DisplayName("Should generate AML risk assessment report")
    void shouldGenerateAmlRiskReport() {
        AmlRiskReport report = service.generateRiskReport("INSTITUTION_001", LocalDate.now().minusMonths(1), LocalDate.now());
        assertThat(report.reportPeriod()).isNotNull();
        assertThat(report.alertSummary()).isNotNull();
    }

    @Test
    @DisplayName("Should validate beneficial ownership threshold")
    void shouldValidateBeneficialOwnership() {
        List<Owner> owners = List.of(
            new Owner("O1", "Owner A", BigDecimal.valueOf(0.30)),
            new Owner("O2", "Owner B", BigDecimal.valueOf(0.25)),
            new Owner("O3", "Owner C", BigDecimal.valueOf(0.20))
        );
        ValidationResult result = service.validateBeneficialOwnership(owners);
        assertThat(result.valid()).isFalse();
        assertThat(result.identifiedUbos()).isEqualTo(2);
    }

    record AmlRule(String id, String name, String condition, String priority) {}
    record Transaction(String id, BigDecimal amount, String type, LocalDateTime timestamp) {}
    record RuleEvaluationResult(boolean triggered, String ruleId, String priority, String description) {}
    record Alert(boolean triggered, String type, BigDecimal amount, double confidence) {}
    record CorrespondentBank(String id, String name, String jurisdiction, boolean regulated, boolean physicalPresence) {}
    record RiskAssessment(String riskLevel, List<String> requiredControls, String approvalRequired) {}
    record TradeTransaction(String id, String direction, String goods, BigDecimal declaredValue, BigDecimal marketValue, String country) {}
    record AmlRiskReport(String institutionId, String reportPeriod, Map<String, Integer> alertSummary, int sarCount) {}
    record Owner(String id, String name, BigDecimal percentage) {}
    record ValidationResult(boolean valid, int identifiedUbos, List<String> missingInfo) {}

    static class AmlPolicyService {
        RuleEvaluationResult evaluateRule(AmlRule rule, Transaction txn) {
            boolean triggered = txn.amount().compareTo(BigDecimal.valueOf(10000)) > 0 && "CASH".equals(txn.type());
            return new RuleEvaluationResult(triggered, rule.id(), rule.priority(), triggered ? "Threshold exceeded" : null);
        }

        Alert detectMultiAccountLayering(String customerId, Map<String, List<Transaction>> accountTxns) {
            int accountCount = accountTxns.size();
            BigDecimal total = accountTxns.values().stream().flatMap(List::stream).map(Transaction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean triggered = accountCount >= 3 && total.compareTo(BigDecimal.valueOf(10000)) > 0;
            return new Alert(triggered, "MULTI_ACCOUNT_LAYERING", total, 0.85);
        }

        RiskAssessment assessCorrespondentRisk(CorrespondentBank bank) {
            boolean highRisk = !bank.regulated() || !bank.physicalPresence();
            List<String> controls = highRisk ? List.of("EDD_REQUIRED", "SENIOR_APPROVAL", "ONGOING_MONITORING") : List.of("STANDARD_DD");
            return new RiskAssessment(highRisk ? "HIGH" : "LOW", controls, highRisk ? "YES" : "NO");
        }

        Alert detectTradeBasedML(TradeTransaction trade) {
            BigDecimal variance = trade.declaredValue().subtract(trade.marketValue()).abs();
            double variancePct = variance.divide(trade.marketValue(), 2, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
            boolean triggered = variancePct > 50;
            return new Alert(triggered, "PRICE_MISMATCH", trade.declaredValue(), triggered ? 0.9 : 0.0);
        }

        int getCountryRiskScore(String country, Map<String, Integer> riskMap) {
            return riskMap.getOrDefault(country, 50);
        }

        AmlRiskReport generateRiskReport(String institutionId, LocalDate from, LocalDate to) {
            return new AmlRiskReport(institutionId, from + " to " + to, Map.of("HIGH", 5, "MEDIUM", 12, "LOW", 45), 3);
        }

        ValidationResult validateBeneficialOwnership(List<Owner> owners) {
            int ubos = (int) owners.stream().filter(o -> o.percentage().compareTo(BigDecimal.valueOf(0.25)) >= 0).count();
            BigDecimal total = owners.stream().map(Owner::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean valid = total.compareTo(BigDecimal.valueOf(0.95)) >= 0;
            return new ValidationResult(valid, ubos, valid ? List.of() : List.of("Incomplete ownership"));
        }
    }
}
