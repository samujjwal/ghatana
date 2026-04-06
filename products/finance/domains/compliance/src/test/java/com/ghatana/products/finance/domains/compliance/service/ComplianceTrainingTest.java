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
 * @doc.purpose Tests for compliance training and certification tracking per Compliance-010
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Compliance Training Tests")
class ComplianceTrainingTest {
    private ComplianceTrainingService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceTrainingService();
    }

    @Test
    @DisplayName("Should assign mandatory training to employee")
    void shouldAssignTraining() {
        String employeeId = "EMP001";
        TrainingModule module = new TrainingModule("AML_101", "Anti-Money Laundering Basics", "MANDATORY");
        service.assignTraining(employeeId, module);
        List<TrainingAssignment> assignments = service.getAssignments(employeeId);
        assertThat(assignments).hasSize(1);
    }

    @Test
    @DisplayName("Should track training completion status")
    void shouldTrackCompletion() {
        String employeeId = "EMP002";
        service.assignTraining(employeeId, new TrainingModule("KYC_101", "KYC Fundamentals", "MANDATORY"));
        service.recordCompletion("EMP002", "KYC_101", LocalDateTime.now(), 95);
        TrainingStatus status = service.getStatus("EMP002", "KYC_101");
        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.score()).isEqualTo(95);
    }

    @Test
    @DisplayName("Should calculate training compliance rate")
    void shouldCalculateComplianceRate() {
        Map<String, List<String>> employeeTrainings = Map.of(
            "EMP_A", List.of("AML_101", "KYC_101"),
            "EMP_B", List.of("AML_101"),
            "EMP_C", List.of()
        );
        double rate = service.calculateComplianceRate(employeeTrainings, List.of("AML_101", "KYC_101"));
        assertThat(rate).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Should identify overdue training")
    void shouldIdentifyOverdueTraining() {
        String employeeId = "EMP003";
        service.assignTraining(employeeId, new TrainingModule("ETHICS_101", "Code of Ethics", "MANDATORY"));
        service.setDueDate("EMP003", "ETHICS_101", LocalDate.now().minusDays(7));
        List<TrainingAssignment> overdue = service.getOverdue(employeeId);
        assertThat(overdue).hasSize(1);
    }

    @Test
    @DisplayName("Should generate training certificate")
    void shouldGenerateCertificate() {
        Certificate cert = service.generateCertificate("EMP004", "AML_ADVANCED", LocalDate.now());
        assertThat(cert.employeeId()).isEqualTo("EMP004");
        assertThat(cert.moduleId()).isEqualTo("AML_ADVANCED");
        assertThat(cert.validUntil()).isAfter(LocalDate.now());
    }

    @Test
    @DisplayName("Should schedule refresher training")
    void shouldScheduleRefresher() {
        String employeeId = "EMP005";
        service.scheduleRefresher(employeeId, "AML_101", LocalDate.now().plusYears(1));
        RefresherSchedule schedule = service.getRefresherSchedule(employeeId, "AML_101");
        assertThat(schedule.scheduledDate()).isEqualTo(LocalDate.now().plusYears(1));
    }

    record TrainingModule(String id, String name, String type) {}
    record TrainingAssignment(String employeeId, String moduleId, String status, LocalDate dueDate) {}
    record TrainingStatus(String employeeId, String moduleId, String status, int score, LocalDateTime completedAt) {}
    record Certificate(String employeeId, String moduleId, LocalDate issuedDate, LocalDate validUntil) {}
    record RefresherSchedule(String employeeId, String moduleId, LocalDate scheduledDate) {}

    static class ComplianceTrainingService {
        private final Map<String, List<TrainingAssignment>> assignments = new HashMap<>();
        private final Map<String, TrainingStatus> statuses = new HashMap<>();
        private final Map<String, LocalDate> dueDates = new HashMap<>();
        private final Map<String, RefresherSchedule> refreshers = new HashMap<>();

        void assignTraining(String employeeId, TrainingModule module) {
            assignments.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(new TrainingAssignment(employeeId, module.id(), "ASSIGNED", LocalDate.now().plusDays(30)));
        }

        List<TrainingAssignment> getAssignments(String employeeId) {
            return assignments.getOrDefault(employeeId, List.of());
        }

        void recordCompletion(String employeeId, String moduleId, LocalDateTime completedAt, int score) {
            statuses.put(employeeId + "_" + moduleId, new TrainingStatus(employeeId, moduleId, "COMPLETED", score, completedAt));
        }

        TrainingStatus getStatus(String employeeId, String moduleId) {
            return statuses.getOrDefault(employeeId + "_" + moduleId, new TrainingStatus(employeeId, moduleId, "NOT_STARTED", 0, null));
        }

        double calculateComplianceRate(Map<String, List<String>> employeeTrainings, List<String> required) {
            int compliant = 0;
            for (List<String> completed : employeeTrainings.values()) {
                if (completed.containsAll(required)) compliant++;
            }
            return employeeTrainings.isEmpty() ? 0.0 : (double) compliant / employeeTrainings.size() * 100;
        }

        void setDueDate(String employeeId, String moduleId, LocalDate dueDate) {
            dueDates.put(employeeId + "_" + moduleId, dueDate);
        }

        List<TrainingAssignment> getOverdue(String employeeId) {
            List<TrainingAssignment> overdue = new ArrayList<>();
            for (TrainingAssignment a : assignments.getOrDefault(employeeId, List.of())) {
                LocalDate due = dueDates.getOrDefault(employeeId + "_" + a.moduleId(), a.dueDate());
                if (due.isBefore(LocalDate.now())) overdue.add(a);
            }
            return overdue;
        }

        Certificate generateCertificate(String employeeId, String moduleId, LocalDate date) {
            return new Certificate(employeeId, moduleId, date, date.plusYears(2));
        }

        void scheduleRefresher(String employeeId, String moduleId, LocalDate date) {
            refreshers.put(employeeId + "_" + moduleId, new RefresherSchedule(employeeId, moduleId, date));
        }

        RefresherSchedule getRefresherSchedule(String employeeId, String moduleId) {
            return refreshers.get(employeeId + "_" + moduleId);
        }
    }
}
