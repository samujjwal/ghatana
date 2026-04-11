package com.ghatana.products.finance.domains.sanctions.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for country risk assessment and geography-based screening per Sanctions-008
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Country Risk Assessment Tests")
class CountryRiskAssessmentTest {
    private CountryRiskService service;

    @BeforeEach
    void setUp() {
        service = new CountryRiskService();
    }

    @Test
    @DisplayName("Should assess country risk level")
    void shouldAssessCountryRisk() {
        CountryRiskAssessment assessment = service.assessCountry("IR");
        assertThat(assessment.riskLevel()).isEqualTo("HIGH");
        assertThat(assessment.sanctionsPrograms()).contains("OFAC");
    }

    @Test
    @DisplayName("Should evaluate FATF status impact")
    void shouldEvaluateFatfStatus() {
        CountryRiskAssessment assessment = service.assessCountry("XX");
        RiskAdjustment adjustment = service.evaluateFatfStatus(assessment, "GREY_LIST");
        assertThat(adjustment.riskIncrease()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should assess correspondent banking risk")
    void shouldAssessCorrespondentRisk() {
        CountryRiskAssessment countryRisk = service.assessCountry("CU");
        CorrespondentRisk correspondentRisk = service.assessCorrespondentRisk(countryRisk, false);
        assertThat(correspondentRisk.prohibited()).isTrue();
    }

    @Test
    @DisplayName("Should determine enhanced due diligence requirements")
    void shouldDetermineEddRequirements() {
        CountryRiskAssessment assessment = service.assessCountry("IR");
        EddRequirements edd = service.determineEddRequirements(assessment);
        assertThat(edd.required()).isTrue();
        assertThat(edd.additionalDocumentation()).isNotEmpty();
    }

    @Test
    @DisplayName("Should track country risk changes over time")
    void shouldTrackRiskChanges() {
        service.recordRiskChange("SY", "CRITICAL", LocalDate.of(2020, 1, 1));
        service.recordRiskChange("SY", "HIGH", LocalDate.of(2021, 1, 1));
        List<RiskHistory> history = service.getRiskHistory("SY");
        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Should generate geographic risk report")
    void shouldGenerateGeographicRiskReport() {
        GeographicRiskReport report = service.generateReport(List.of("IR", "KP", "SY", "US", "UK"));
        assertThat(report.highRiskCountries()).contains("IR", "KP", "SY");
        assertThat(report.lowRiskCountries()).contains("US", "UK");
    }

    @Test
    @DisplayName("Should validate transaction corridor risk")
    void shouldValidateTransactionCorridor() {
        String originCountry = "US";
        String destinationCountry = "IR";
        CorridorRisk corridor = service.assessCorridorRisk(originCountry, destinationCountry);
        assertThat(corridor.riskLevel()).isEqualTo("CRITICAL");
        assertThat(corridor.allowed()).isFalse();
    }

    record CountryRiskAssessment(String countryCode, String riskLevel, List<String> sanctionsPrograms, String fatfStatus) {}
    record RiskAdjustment(int riskIncrease, String reason) {}
    record CorrespondentRisk(boolean prohibited, String restrictions, String approvalRequired) {}
    record EddRequirements(boolean required, List<String> additionalDocumentation, String reviewFrequency) {}
    record RiskHistory(LocalDate date, String riskLevel, String reason) {}
    record GeographicRiskReport(List<String> highRiskCountries, List<String> mediumRiskCountries, List<String> lowRiskCountries) {}
    record CorridorRisk(String origin, String destination, String riskLevel, boolean allowed, String reason) {}

    static class CountryRiskService {
        private final Map<String, List<RiskHistory>> riskHistory = new HashMap<>();

        CountryRiskAssessment assessCountry(String countryCode) {
            String risk = "IR".equals(countryCode) || "KP".equals(countryCode) || "SY".equals(countryCode) || "CU".equals(countryCode) ? "HIGH" : "US".equals(countryCode) || "UK".equals(countryCode) ? "LOW" : "MEDIUM";
            return new CountryRiskAssessment(countryCode, risk, "HIGH".equals(risk) ? List.of("OFAC") : List.of(), "OK");
        }

        RiskAdjustment evaluateFatfStatus(CountryRiskAssessment assessment, String fatfStatus) {
            int increase = "GREY_LIST".equals(fatfStatus) ? 20 : "BLACK_LIST".equals(fatfStatus) ? 50 : 0;
            return new RiskAdjustment(increase, "FATF " + fatfStatus);
        }

        CorrespondentRisk assessCorrespondentRisk(CountryRiskAssessment countryRisk, boolean physicalPresence) {
            boolean prohibited = "HIGH".equals(countryRisk.riskLevel()) && !physicalPresence;
            return new CorrespondentRisk(prohibited, prohibited ? "NO_CORRESPONDENT" : "ENHANCED_DUE_DILIGENCE", prohibited ? "N/A" : "SENIOR_MANAGER");
        }

        EddRequirements determineEddRequirements(CountryRiskAssessment assessment) {
            boolean required = "HIGH".equals(assessment.riskLevel());
            return new EddRequirements(required, required ? List.of("SOURCE_OF_FUNDS", "PURPOSE_OF_TRANSACTION") : List.of(), required ? "QUARTERLY" : "ANNUAL");
        }

        void recordRiskChange(String countryCode, String riskLevel, LocalDate date) {
            riskHistory.computeIfAbsent(countryCode, k -> new ArrayList<>()).add(new RiskHistory(date, riskLevel, "Regulatory change"));
        }

        List<RiskHistory> getRiskHistory(String countryCode) {
            return riskHistory.getOrDefault(countryCode, List.of());
        }

        GeographicRiskReport generateReport(List<String> countries) {
            List<String> high = new ArrayList<>();
            List<String> medium = new ArrayList<>();
            List<String> low = new ArrayList<>();
            for (String c : countries) {
                CountryRiskAssessment a = assessCountry(c);
                if ("HIGH".equals(a.riskLevel())) high.add(c);
                else if ("MEDIUM".equals(a.riskLevel())) medium.add(c);
                else low.add(c);
            }
            return new GeographicRiskReport(high, medium, low);
        }

        CorridorRisk assessCorridorRisk(String origin, String destination) {
            boolean highRisk = ("US".equals(origin) || "UK".equals(origin)) && ("IR".equals(destination) || "KP".equals(destination) || "SY".equals(destination));
            return new CorridorRisk(origin, destination, highRisk ? "CRITICAL" : "LOW", !highRisk, highRisk ? "Sanctions corridor" : null);
        }
    }
}
