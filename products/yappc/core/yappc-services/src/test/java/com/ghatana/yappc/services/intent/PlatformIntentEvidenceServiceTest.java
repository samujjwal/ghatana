package com.ghatana.yappc.services.intent;

import com.ghatana.yappc.api.PlatformEvidence;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.services.platform.PlatformIntegrationClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies intent lifecycle operations emit platform evidence
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformIntentEvidenceService")
class PlatformIntentEvidenceServiceTest extends EventloopTestBase {

    @Mock private PlatformIntegrationClient platformIntegrationClient;

    @Test
    @DisplayName("recordCapture stores searchable INTENT phase evidence")
    void recordCapture_storesPhaseEvidence() {
        when(platformIntegrationClient.storeEvidence(any())).thenReturn(true);
        PlatformIntentEvidenceService service = new PlatformIntentEvidenceService(platformIntegrationClient);

        String evidenceId = runPromise(() -> service.recordCapture(input(), spec()));

        assertThat(evidenceId).startsWith("intent-capture-intent-1-");
        ArgumentCaptor<PlatformEvidence> evidenceCaptor = ArgumentCaptor.forClass(PlatformEvidence.class);
        verify(platformIntegrationClient).storeEvidence(evidenceCaptor.capture());
        PlatformEvidence evidence = evidenceCaptor.getValue();
        assertThat(evidence.evidenceId()).isEqualTo(evidenceId);
        assertThat(evidence.tenantId()).isEqualTo("tenant-1");
        assertThat(evidence.workspaceId()).isEqualTo("workspace-1");
        assertThat(evidence.projectId()).isEqualTo("project-1");
        assertThat(evidence.record().evidenceType()).isEqualTo("INTENT_EVIDENCE");
        assertThat(evidence.record().content()).contains("INTENT phase evidence");
        assertThat(evidence.metadata().lifecyclePhase()).isEqualTo("INTENT");
    }

    @Test
    @DisplayName("recordAnalysis stores analysis evidence with intent metadata")
    void recordAnalysis_storesAnalysisEvidence() {
        when(platformIntegrationClient.storeEvidence(any())).thenReturn(true);
        PlatformIntentEvidenceService service = new PlatformIntentEvidenceService(platformIntegrationClient);

        String evidenceId = runPromise(() -> service.recordAnalysis(
                spec(),
                analysis(),
                Map.of(
                        "promptKey", "intent.analyze",
                        "promptVersion", "v1",
                        "modelUsed", "gpt-4",
                        "prompt", "Full user prompt with private context",
                        "generatedContent", "private generated summary",
                        "confidence", 0.82)));

        assertThat(evidenceId).startsWith("intent-analysis-intent-1-");
        ArgumentCaptor<PlatformEvidence> evidenceCaptor = ArgumentCaptor.forClass(PlatformEvidence.class);
        verify(platformIntegrationClient).storeEvidence(evidenceCaptor.capture());
        PlatformEvidence evidence = evidenceCaptor.getValue();
        assertThat(evidence.record().attributes())
                .containsEntry("operation", "intent.analyze")
                .containsEntry("intentId", "intent-1")
                .containsEntry("feasible", "true")
                .containsEntry("promptVersion", "v1")
                .containsEntry("modelUsed", "gpt-4")
                .containsEntry("prompt", "[REDACTED]")
                .containsEntry("generatedContent", "[REDACTED]")
                .containsEntry("confidence", 0.82);
        assertThat(evidence.record().attributes().toString())
                .doesNotContain("Full user prompt", "private generated summary");
    }

    @Test
    @DisplayName("recordCapture fails when platform evidence adapter rejects write")
    void recordCapture_rejectsAdapterFailure() {
        when(platformIntegrationClient.storeEvidence(any())).thenReturn(false);
        PlatformIntentEvidenceService service = new PlatformIntentEvidenceService(platformIntegrationClient);

        assertThatThrownBy(() -> runPromise(() -> service.recordCapture(input(), spec())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected evidence");
    }

    private static IntentInput input() {
        return IntentInput.builder()
                .rawText("Build a task manager")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .userId("user-1")
                .build();
    }

    private static IntentSpec spec() {
        return IntentSpec.builder()
                .id("intent-1")
                .productName("Task Manager")
                .description("Team collaboration")
                .tenantId("tenant-1")
                .metadata(Map.of(
                        "workspaceId", "workspace-1",
                        "projectId", "project-1",
                        "userId", "user-1"))
                .createdAt(Instant.parse("2026-05-26T12:00:00Z"))
                .build();
    }

    private static IntentAnalysis analysis() {
        return IntentAnalysis.builder()
                .intentId("intent-1")
                .feasible(true)
                .summary("Ready")
                .build();
    }
}
