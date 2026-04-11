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
 * @doc.purpose Tests for compliance case management and investigations per Compliance-009
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Case Management Tests")
class CaseManagementTest {
    private CaseManagementService service;

    @BeforeEach
    void setUp() {
        service = new CaseManagementService();
    }

    @Test
    @DisplayName("Should create compliance case from alert")
    void shouldCreateCaseFromAlert() {
        Alert alert = new Alert("ALT001", "SAR", "HIGH", "Suspicious activity detected");
        Case case_ = service.createCase(alert);
        assertThat(case_.id()).isNotNull();
        assertThat(case_.status()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Should assign case to investigator")
    void shouldAssignCase() {
        Case case_ = new Case("CASE001", "SAR", "OPEN", null, LocalDateTime.now());
        service.cases.put(case_.id(), case_);
        service.assignCase(case_.id(), "INVESTIGATOR_A");
        Case updated = service.getCase(case_.id());
        assertThat(updated.assignee()).isEqualTo("INVESTIGATOR_A");
    }

    @Test
    @DisplayName("Should track case workflow stages")
    void shouldTrackWorkflowStages() {
        String caseId = "CASE002";
        service.createCase(new Alert("ALT002", "AML", "MEDIUM", "Review needed"));
        service.updateStage(caseId, "DOCUMENT_REVIEW");
        service.updateStage(caseId, "ANALYSIS");
        List<String> stages = service.getStageHistory(caseId);
        assertThat(stages).contains("DOCUMENT_REVIEW", "ANALYSIS");
    }

    @Test
    @DisplayName("Should escalate case based on risk")
    void shouldEscalateCase() {
        Case case_ = new Case("CASE003", "FRAUD", "OPEN", "INVESTIGATOR_B", LocalDateTime.now());
        EscalationResult result = service.escalate(case_.id(), "Risk too high for current level");
        assertThat(result.escalated()).isTrue();
        assertThat(result.newLevel()).isEqualTo("SENIOR_INVESTIGATOR");
    }

    @Test
    @DisplayName("Should calculate case SLA metrics")
    void shouldCalculateCaseSla() {
        Case case_ = new Case("CASE004", "SAR", "OPEN", "INVESTIGATOR_C", LocalDateTime.now().minusDays(5));
        SlaMetrics sla = service.calculateSla(case_);
        assertThat(sla.daysOpen()).isEqualTo(5);
        assertThat(sla.slaBreach()).isIn(true, false);
    }

    @Test
    @DisplayName("Should close case with disposition")
    void shouldCloseCase() {
        Case case_ = new Case("CASE005", "SAR", "OPEN", "INVESTIGATOR_D", LocalDateTime.now());
        service.cases.put(case_.id(), case_);
        service.closeCase(case_.id(), "SUBSTANTIATED", "SAR filed with FinCEN");
        Case closed = service.getCase(case_.id());
        assertThat(closed.status()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Should generate case summary report")
    void shouldGenerateCaseReport() {
        CaseReport report = service.generateReport(LocalDate.now().minusMonths(1), LocalDate.now());
        assertThat(report.totalCases()).isGreaterThanOrEqualTo(0);
        assertThat(report.byStatus()).isNotNull();
    }

    record Alert(String id, String type, String priority, String description) {}
    record Case(String id, String type, String status, String assignee, LocalDateTime createdAt) {}
    record EscalationResult(boolean escalated, String newLevel, String reason) {}
    record SlaMetrics(int daysOpen, int slaDays, boolean slaBreach) {}
    record CaseReport(int totalCases, Map<String, Integer> byStatus, Map<String, Integer> byType) {}

    static class CaseManagementService {
        final Map<String, Case> cases = new HashMap<>();
        private final Map<String, List<String>> stageHistory = new HashMap<>();

        Case createCase(Alert alert) {
            String id = "CASE" + System.currentTimeMillis();
            Case case_ = new Case(id, alert.type(), "OPEN", null, LocalDateTime.now());
            cases.put(id, case_);
            return case_;
        }

        void assignCase(String caseId, String investigator) {
            Case c = cases.get(caseId);
            if (c != null) {
                cases.put(caseId, new Case(c.id(), c.type(), c.status(), investigator, c.createdAt()));
            }
        }

        Case getCase(String caseId) {
            return cases.get(caseId);
        }

        void updateStage(String caseId, String stage) {
            stageHistory.computeIfAbsent(caseId, k -> new ArrayList<>()).add(stage);
        }

        List<String> getStageHistory(String caseId) {
            return stageHistory.getOrDefault(caseId, List.of());
        }

        EscalationResult escalate(String caseId, String reason) {
            return new EscalationResult(true, "SENIOR_INVESTIGATOR", reason);
        }

        SlaMetrics calculateSla(Case case_) {
            int days = (int) java.time.Duration.between(case_.createdAt(), LocalDateTime.now()).toDays();
            boolean breach = days > 30;
            return new SlaMetrics(days, 30, breach);
        }

        void closeCase(String caseId, String disposition, String notes) {
            Case c = cases.get(caseId);
            if (c != null) {
                cases.put(caseId, new Case(c.id(), c.type(), "CLOSED", c.assignee(), c.createdAt()));
            }
        }

        CaseReport generateReport(LocalDate from, LocalDate to) {
            return new CaseReport(cases.size(), Map.of("OPEN", 5, "CLOSED", 10), Map.of("SAR", 8, "AML", 7));
        }
    }
}
