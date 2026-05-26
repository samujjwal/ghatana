package com.ghatana.yappc.services.lifecycle;

import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.PhasePacket;
import com.ghatana.yappc.services.capability.CapabilityEvaluationService;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.phase.PhaseActionAuthorizationService;
import com.ghatana.yappc.storage.YappcArtifactRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdvancePhaseUseCase")
class AdvancePhaseUseCaseTest extends EventloopTestBase {

    @Mock private TransitionConfigLoader transitionConfigLoader;
    @Mock private PolicyEngine policyEngine;
    @Mock private YappcArtifactRepository artifactRepository;
    @Mock private DlqPublisher dlqPublisher;
    @Mock private CapabilityEvaluationService capabilityEvaluationService;

    @Test
    @DisplayName("blocks transition before policy when backend capability denies phase advance")
    void blocksWhenCapabilityDeniesPhaseAdvance() {
        when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allDenied("viewer")));
        when(dlqPublisher.publish(any(), any(), any(), any(), anyMap(), any(), any()))
                .thenReturn(Promise.complete());

        TransitionResult result = runPromise(() -> useCase().execute(request("VALIDATE", "GENERATE")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.blockCode()).isEqualTo("PHASE_ACTION_UNAUTHORIZED");
        assertThat(result.blockReason()).isEqualTo("phaseAction.disabled.updateCapabilityRequired");
        verify(transitionConfigLoader, never()).findTransition(any(), any());
        verify(policyEngine, never()).evaluate(any(), anyMap());
    }

    @Test
    @DisplayName("blocks transition before policy when phase advance flag is missing")
    void blocksWhenPhaseAdvanceFlagMissing() {
        when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));
        when(dlqPublisher.publish(any(), any(), any(), any(), anyMap(), any(), any()))
                .thenReturn(Promise.complete());

        TransitionRequest request = new TransitionRequest(
                "project-1",
                "VALIDATE",
                "GENERATE",
                "tenant-1",
                "user-1",
                "workspace-1",
                PhasePacket.TenantTier.PRO,
                java.util.Set.of());

        TransitionResult result = runPromise(() -> useCase().execute(request));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.blockCode()).isEqualTo("PHASE_ACTION_UNAUTHORIZED");
        assertThat(result.blockReason()).isEqualTo("phaseAction.disabled.phaseAdvanceEntitlementMissing");
        verify(transitionConfigLoader, never()).findTransition(any(), any());
        verify(policyEngine, never()).evaluate(any(), anyMap());
    }

    @Test
    @DisplayName("blocks transition when required artifact is missing")
    void blocksWhenRequiredArtifactMissing() {
        TransitionSpec spec = transition("INTENT", "SHAPE", List.of("intent-spec"));
        when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));
        when(transitionConfigLoader.findTransition("INTENT", "SHAPE")).thenReturn(Optional.of(spec));
        when(artifactRepository.list("project-1/intent-spec")).thenReturn(Promise.of(List.of()));
        when(dlqPublisher.publish(any(), any(), any(), any(), anyMap(), any(), any()))
                .thenReturn(Promise.complete());

        TransitionResult result = runPromise(() -> useCase().execute(request("INTENT", "SHAPE")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.blockCode()).isEqualTo("MISSING_ARTIFACT");
        assertThat(result.missingArtifacts()).containsExactly("intent-spec");
        verify(policyEngine, never()).evaluate(any(), anyMap());
        verify(dlqPublisher).publish(
                eq("tenant-1"),
                eq("lifecycle-management-v1"),
                eq("advance-phase"),
                eq("PHASE_ADVANCE_BLOCKED"),
                anyMap(),
                eq("MISSING_ARTIFACT"),
                eq("project-1"));
    }

    @Test
    @DisplayName("blocks transition when policy denies")
    void blocksWhenPolicyDenies() {
        TransitionSpec spec = transition("VALIDATE", "GENERATE", List.of());
        when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));
        when(transitionConfigLoader.findTransition("VALIDATE", "GENERATE")).thenReturn(Optional.of(spec));
        when(policyEngine.evaluate(eq("phase_advance_policy"), anyMap())).thenReturn(Promise.of(false));
        when(dlqPublisher.publish(any(), any(), any(), any(), anyMap(), any(), any()))
                .thenReturn(Promise.complete());

        TransitionResult result = runPromise(() -> useCase().execute(request("VALIDATE", "GENERATE")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.blockCode()).isEqualTo("POLICY_GATE");
        verify(dlqPublisher).publish(
                eq("tenant-1"),
                eq("lifecycle-management-v1"),
                eq("advance-phase"),
                eq("PHASE_ADVANCE_BLOCKED"),
                anyMap(),
                eq("POLICY_GATE"),
                eq("project-1"));
    }

    @Test
    @DisplayName("allows transition only after artifact and policy gates pass")
    void allowsWhenArtifactsAndPolicyPass() {
        TransitionSpec spec = transition("GENERATE", "RUN", List.of("generated-artifact"));
        when(capabilityEvaluationService.evaluate(any()))
                .thenReturn(Promise.of(CapabilityEvaluationService.CapabilityModel.allGranted()));
        when(transitionConfigLoader.findTransition("GENERATE", "RUN")).thenReturn(Optional.of(spec));
        when(artifactRepository.list("project-1/generated-artifact"))
                .thenReturn(Promise.of(List.of("project-1/generated-artifact/v1")));
        when(policyEngine.evaluate(eq("phase_advance_policy"), anyMap())).thenReturn(Promise.of(true));

        TransitionResult result = runPromise(() -> useCase().execute(request("GENERATE", "RUN")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.toPhase()).isEqualTo("RUN");
        verify(dlqPublisher, never()).publish(any(), any(), any(), any(), anyMap(), any(), any());
    }

    private AdvancePhaseUseCase useCase() {
        return new AdvancePhaseUseCase(
                transitionConfigLoader,
                policyEngine,
                artifactRepository,
                dlqPublisher,
                capabilityEvaluationService,
                new PhaseActionAuthorizationService(),
                null);
    }

    private static TransitionRequest request(String from, String to) {
        return new TransitionRequest(
                "project-1",
                from,
                to,
                "tenant-1",
                "user-1",
                "workspace-1",
                PhasePacket.TenantTier.PRO,
                java.util.Set.of("phase.advance"));
    }

    private static TransitionSpec transition(String from, String to, List<String> requiredArtifacts) {
        TransitionSpec spec = new TransitionSpec();
        set(spec, "from", from);
        set(spec, "to", to);
        set(spec, "type", "forward");
        set(spec, "requiredArtifacts", requiredArtifacts);
        return spec;
    }

    private static void set(TransitionSpec spec, String fieldName, Object value) {
        try {
            Field field = TransitionSpec.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(spec, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to configure transition spec field " + fieldName, e);
        }
    }
}
