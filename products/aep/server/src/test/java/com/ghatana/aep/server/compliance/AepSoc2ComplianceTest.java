/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.compliance;

import com.ghatana.aep.compliance.AepSoc2ControlFramework;
import com.ghatana.aep.compliance.SOC2EvidenceCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the SOC 2 Type II evidence collection pipeline and control framework.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Evidence is collected and stored under the correct control ID.</li>
 *   <li>Evidence can be retrieved by control and by time range.</li>
 *   <li>Evidence summaries correctly report counts and type breakdowns.</li>
 *   <li>The control framework produces a report that reflects collected evidence.</li>
 *   <li>Controls without evidence are flagged as {@code WARNING}, not {@code PASS}.</li>
 *   <li>Controls with evidence are flagged as {@code PASS}.</li>
 *   <li>The overall status is {@code COMPLIANT} when all controls pass.</li>
 *   <li>The overall status is {@code WARNING} when any control lacks evidence.</li>
 *   <li>{@code newestEvidenceTimestamp()} is empty before any evidence is collected.</li>
 *   <li>{@code newestEvidenceTimestamp()} returns the most recent evidence timestamp.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose SOC 2 Type II evidence pipeline and control framework tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP SOC 2 compliance evidence pipeline")
class AepSoc2ComplianceTest {

    private SOC2EvidenceCollector collector;
    private AepSoc2ControlFramework framework;

    @BeforeEach
    void setUp() {
        collector  = new SOC2EvidenceCollector();
        framework  = new AepSoc2ControlFramework(collector);
    }

    @AfterEach
    void tearDown() {
        collector.clearAllEvidence();
    }

    // ── Evidence collection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Evidence collection")
    class EvidenceCollection {

        @Test
        @DisplayName("collectEvidence() stores entry under the given control ID")
        void collectEvidence_storesUnderControlId() {
            collector.collectEvidence("CC6.1", "access_log",
                    "User login event recorded", Map.of("userId", "u-001", "result", "success"));

            List<SOC2EvidenceCollector.EvidenceEntry> entries = collector.getEvidence("CC6.1");

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).controlId()).isEqualTo("CC6.1");
            assertThat(entries.get(0).evidenceType()).isEqualTo("access_log");
            assertThat(entries.get(0).description()).isEqualTo("User login event recorded");
        }

        @Test
        @DisplayName("collectEvidence() assigns a unique id to each entry")
        void collectEvidence_assignsUniqueIds() {
            collector.collectEvidence("CC7.1", "metric", "CPU usage OK", Map.of("cpu", "30%"));
            collector.collectEvidence("CC7.1", "metric", "Memory OK",    Map.of("mem", "60%"));

            List<SOC2EvidenceCollector.EvidenceEntry> entries = collector.getEvidence("CC7.1");

            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).id()).isNotEqualTo(entries.get(1).id());
        }

        @Test
        @DisplayName("collectEvidence() assigns a timestamp close to now")
        void collectEvidence_assignsCurrentTimestamp() {
            Instant before = Instant.now().minusSeconds(1);
            collector.collectEvidence("CC8.1", "configuration", "Change ticket TKT-001", Map.of());
            Instant after  = Instant.now().plusSeconds(1);

            SOC2EvidenceCollector.EvidenceEntry entry = collector.getEvidence("CC8.1").get(0);

            assertThat(entry.timestamp()).isAfter(before).isBefore(after);
        }

        @Test
        @DisplayName("collectEvidence() stores an immutable copy of the details map")
        void collectEvidence_storesImmutableDetails() {
            Map<String, Object> details = new java.util.HashMap<>();
            details.put("key", "original");
            collector.collectEvidence("CC6.2", "audit", "Detail immutability test", details);

            details.put("key", "mutated");

            SOC2EvidenceCollector.EvidenceEntry entry = collector.getEvidence("CC6.2").get(0);
            assertThat(entry.details().get("key")).isEqualTo("original");
        }

        @Test
        @DisplayName("evidence for different controls is stored independently")
        void collectEvidence_separateControlsAreIndependent() {
            collector.collectEvidence("CC6.1", "access_log", "Login", Map.of());
            collector.collectEvidence("CC7.2", "incident",   "IR-001", Map.of());

            assertThat(collector.getEvidence("CC6.1")).hasSize(1);
            assertThat(collector.getEvidence("CC7.2")).hasSize(1);
            assertThat(collector.getEvidence("CC6.3")).isEmpty();
        }

        @Test
        @DisplayName("getAllEvidence() returns evidence for all controls that have entries")
        void getAllEvidence_returnsAllControls() {
            collector.collectEvidence("CC6.1", "access_log",    "Login",    Map.of());
            collector.collectEvidence("CC7.1", "metric",        "CPU OK",   Map.of());
            collector.collectEvidence("CC8.1", "configuration", "Change",   Map.of());

            Map<String, List<SOC2EvidenceCollector.EvidenceEntry>> allEvidence =
                    collector.getAllEvidence();

            assertThat(allEvidence).containsKeys("CC6.1", "CC7.1", "CC8.1");
        }
    }

    // ── Time-range evidence retrieval ──────────────────────────────────────────

    @Nested
    @DisplayName("Time-range evidence retrieval")
    class TimeRangeRetrieval {

        @Test
        @DisplayName("getEvidence(controlId, from, to) returns entries within the range")
        void getEvidence_timeRange_includesEntriesWithinRange() {
            Instant t0 = Instant.now().minusSeconds(100);
            Instant t1 = Instant.now().plusSeconds(100);

            collector.collectEvidence("CC7.1", "metric", "In-range entry", Map.of());

            List<SOC2EvidenceCollector.EvidenceEntry> entries =
                    collector.getEvidence("CC7.1", t0, t1);

            assertThat(entries).hasSize(1);
        }

        @Test
        @DisplayName("getEvidence(controlId, from, to) excludes entries outside the range")
        void getEvidence_timeRange_excludesEntriesOutsideRange() {
            Instant future = Instant.now().plusSeconds(1000);

            collector.collectEvidence("CC7.1", "metric", "Old entry", Map.of());

            // Query with a range in the far future — should exclude the entry just added
            List<SOC2EvidenceCollector.EvidenceEntry> entries =
                    collector.getEvidence("CC7.1", future, future.plusSeconds(60));

            assertThat(entries).isEmpty();
        }
    }

    // ── Evidence summary ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Evidence summary")
    class EvidenceSummary {

        @Test
        @DisplayName("summarizeEvidence() returns zero entries for a control with no evidence")
        void summarizeEvidence_noEvidence_returnsZero() {
            SOC2EvidenceCollector.EvidenceSummary summary =
                    collector.summarizeEvidence("CC6.3");

            assertThat(summary.totalEntries()).isZero();
            assertThat(summary.summary()).contains("No evidence collected");
        }

        @Test
        @DisplayName("summarizeEvidence() counts total entries correctly")
        void summarizeEvidence_countsTotal() {
            collector.collectEvidence("CC6.1", "access_log", "Login 1", Map.of());
            collector.collectEvidence("CC6.1", "access_log", "Login 2", Map.of());
            collector.collectEvidence("CC6.1", "metric",     "Metric",  Map.of());

            SOC2EvidenceCollector.EvidenceSummary summary =
                    collector.summarizeEvidence("CC6.1");

            assertThat(summary.totalEntries()).isEqualTo(3);
        }

        @Test
        @DisplayName("summarizeEvidence() groups entries by evidence type")
        void summarizeEvidence_groupsByType() {
            collector.collectEvidence("CC6.1", "access_log", "Login 1", Map.of());
            collector.collectEvidence("CC6.1", "access_log", "Login 2", Map.of());
            collector.collectEvidence("CC6.1", "metric",     "Metric",  Map.of());

            SOC2EvidenceCollector.EvidenceSummary summary =
                    collector.summarizeEvidence("CC6.1");

            assertThat(summary.evidenceByType())
                    .containsEntry("access_log", 2L)
                    .containsEntry("metric",     1L);
        }
    }

    // ── SOC 2 report generation ────────────────────────────────────────────────

    @Nested
    @DisplayName("SOC 2 report generation")
    class ReportGeneration {

        @Test
        @DisplayName("report title is 'AEP SOC 2 Type II Report'")
        void report_titleIsCorrect() {
            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.title()).isEqualTo("AEP SOC 2 Type II Report");
        }

        @Test
        @DisplayName("report lists exactly 6 tracked controls")
        void report_listsExactlySixControls() {
            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.controls()).hasSize(6);
        }

        @Test
        @DisplayName("controls without evidence have WARNING status")
        void report_noEvidence_controlStatusIsWarning() {
            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.controls())
                    .allMatch(c -> c.status().equals("WARNING"));
        }

        @Test
        @DisplayName("control with evidence has PASS status")
        void report_withEvidence_controlStatusIsPass() {
            collector.collectEvidence("CC6.1", "access_log", "Login event", Map.of());

            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.controls())
                    .filteredOn(c -> c.controlId().equals("CC6.1"))
                    .singleElement()
                    .extracting(AepSoc2ControlFramework.ControlStatus::status)
                    .isEqualTo("PASS");
        }

        @Test
        @DisplayName("overall status is WARNING when any control lacks evidence")
        void report_anyControlNoEvidence_overallIsWarning() {
            // Only add evidence for some controls, leaving others without
            collector.collectEvidence("CC6.1", "access_log", "Login",  Map.of());
            collector.collectEvidence("CC7.1", "metric",     "CPU OK", Map.of());

            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.overallStatus()).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("overall status is COMPLIANT when all controls have evidence")
        void report_allControlsHaveEvidence_overallIsCompliant() {
            for (String controlId : List.of("CC6.1", "CC6.2", "CC6.3", "CC7.1", "CC7.2", "CC8.1")) {
                collector.collectEvidence(controlId, "audit",
                        "Evidence for " + controlId, Map.of("source", "system"));
            }

            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.overallStatus()).isEqualTo("COMPLIANT");
        }

        @Test
        @DisplayName("report metadata includes the evidence count")
        void report_metadataIncludesEvidenceCount() {
            collector.collectEvidence("CC6.1", "access_log", "Event A", Map.of());
            collector.collectEvidence("CC7.1", "metric",     "Event B", Map.of());

            AepSoc2ControlFramework.Soc2Report report = framework.generateReport();

            assertThat(report.metadata()).containsEntry("evidenceCollected", "2");
        }
    }

    // ── newestEvidenceTimestamp ────────────────────────────────────────────────

    @Nested
    @DisplayName("newestEvidenceTimestamp()")
    class NewestEvidenceTimestamp {

        @Test
        @DisplayName("returns empty when no evidence has been collected")
        void newestEvidenceTimestamp_noEvidence_isEmpty() {
            Optional<Instant> result = framework.newestEvidenceTimestamp();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns the timestamp of the most recently collected entry")
        void newestEvidenceTimestamp_returnsNewestTimestamp() throws InterruptedException {
            collector.collectEvidence("CC6.1", "access_log", "Earlier entry", Map.of());
            Thread.sleep(2); // Ensure distinct timestamps
            collector.collectEvidence("CC7.1", "metric",     "Later entry",   Map.of());

            Instant latestEntry = collector.getEvidence("CC7.1").get(0).timestamp();

            Optional<Instant> newest = framework.newestEvidenceTimestamp();

            assertThat(newest).isPresent();
            assertThat(newest.get()).isEqualTo(latestEntry);
        }

        @Test
        @DisplayName("returns a value when evidence exists across multiple controls")
        void newestEvidenceTimestamp_multipleControls_returnsLatest() {
            for (String controlId : List.of("CC6.1", "CC6.2", "CC7.1")) {
                collector.collectEvidence(controlId, "audit", "Evidence", Map.of());
            }

            assertThat(framework.newestEvidenceTimestamp()).isPresent();
        }
    }

    // ── clearEvidence ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearEvidence()")
    class ClearEvidence {

        @Test
        @DisplayName("clearEvidence(controlId) removes all entries for that control")
        void clearEvidence_removesEntriesForControl() {
            collector.collectEvidence("CC6.1", "access_log", "Login", Map.of());
            collector.collectEvidence("CC7.1", "metric",     "CPU",   Map.of());

            collector.clearEvidence("CC6.1");

            assertThat(collector.getEvidence("CC6.1")).isEmpty();
            assertThat(collector.getEvidence("CC7.1")).hasSize(1);
        }

        @Test
        @DisplayName("clearAllEvidence() removes entries for every control")
        void clearAllEvidence_removesAllEntries() {
            collector.collectEvidence("CC6.1", "access_log", "Login", Map.of());
            collector.collectEvidence("CC7.1", "metric",     "CPU",   Map.of());

            collector.clearAllEvidence();

            assertThat(collector.getAllEvidence()).isEmpty();
        }
    }
}
