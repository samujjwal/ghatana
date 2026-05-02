/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.ai.llm;

import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Privacy control tests for AI/LLM request and response flows.
 *
 * <p>Verifies that {@link PolicyGuardRail} enforces privacy controls by blocking
 * LLM calls when policy evaluation detects sensitive content (PII, secrets, credentials,
 * etc.) in prompt input, tool invocations, and evaluation datasets. Also ensures that
 * denial exceptions do not expose raw sensitive payloads to callers.</p>
 *
 * @doc.type    class
 * @doc.purpose Verify AI/LLM privacy controls via PolicyGuardRail
 * @doc.layer   platform
 * @doc.pattern Test
 */
@DisplayName("LLM Privacy Controls")
class LlmPrivacyControlsTest {

    private LLMGateway delegate;
    private PolicyAsCodeEngine policyEngine;
    private PolicyEvalResult denyResult;

    @BeforeEach
    void setUp() { 
        delegate = mock(LLMGateway.class);
        policyEngine = mock(PolicyAsCodeEngine.class);

        // Configurable deny result shared across tests that need a policy denial
        denyResult = mock(PolicyEvalResult.class);
        when(denyResult.allowed()).thenReturn(false);
        when(denyResult.reasons()).thenReturn(List.of("PII detected in input"));
    }

    // ── Prompt Input Redaction ─────────────────────────────────────────────────

    @Nested
    @DisplayName("prompt input redaction")
    class PromptInputRedaction {

        @Test
        @DisplayName("prompt containing PII-like secret is blocked before reaching the LLM")
        void promptContainingPiiIsBlockedBeforeLlmCall() { 
            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(denyResult));

            CompletionRequest request = CompletionRequest.builder()
                    .prompt("Please process: ssn=123-45-6789 belongs to Alice Johnson")
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "pii.guard"); 
            Promise<CompletionResult> result = guardRail.complete(request); 

            assertThat(result.isException()).isTrue(); 
            // LLM gateway must never be called when policy denies
            verify(delegate, never()).complete(any());
        }

        @Test
        @DisplayName("prompt containing hardcoded credential is blocked before reaching the LLM")
        void promptContainingCredentialIsBlocked() { 
            PolicyEvalResult credentialDeny = mock(PolicyEvalResult.class);
            when(credentialDeny.allowed()).thenReturn(false);
            when(credentialDeny.reasons()).thenReturn(List.of("Hardcoded credential detected"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(credentialDeny));

            CompletionRequest request = CompletionRequest.builder()
                    .prompt("api_key='sk-prod-abc123xyz' — summarize this code")
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "secret.guard"); 
            Promise<CompletionResult> actual = guardRail.complete(request); 

            assertThat(actual.isException()).isTrue();
            verify(delegate, never()).complete(any());
        }

        @Test
        @DisplayName("policy input context receives the prompt text for evaluation")
        void policyEngineReceivesPromptTextForEvaluation() { 
            // Capture the context map passed to the policy engine and verify
            // the prompt is included — this is what the policy evaluates.
            PolicyEvalResult allowResult = mock(PolicyEvalResult.class);
            when(allowResult.allowed()).thenReturn(true);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
            when(policyEngine.evaluate(anyString(), anyString(), contextCaptor.capture()))
                    .thenReturn(Promise.of(allowResult));

            CompletionResult completionResult = CompletionResult.builder().text("safe output").build();
            when(delegate.complete(any())).thenReturn(Promise.of(completionResult));

            String promptText = "Explain quantum computing in simple terms";
            CompletionRequest request = CompletionRequest.builder().prompt(promptText).build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "llm.guard"); 
            guardRail.complete(request); 

            Map<String, Object> capturedContext = contextCaptor.getValue();
            assertThat(capturedContext).containsKey("prompt");
            assertThat(capturedContext.get("prompt")).isEqualTo(promptText);
        }
    }

    // ── Denial Exception Confidentiality ──────────────────────────────────────

    @Nested
    @DisplayName("denial exception confidentiality")
    class DenialExceptionConfidentiality {

        @Test
        @DisplayName("denied request exception message does not expose the raw prompt payload")
        void deniedRequestExceptionDoesNotExposeRawPromptPayload() { 
            // The exception message must include the denial reason from policy
            // but must NOT include the sensitive content of the original prompt.
            PolicyEvalResult piiDeny = mock(PolicyEvalResult.class);
            when(piiDeny.allowed()).thenReturn(false);
            when(piiDeny.reasons()).thenReturn(List.of("Credit card number detected"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(piiDeny));

            String sensitiveContent = "Card: 4111-1111-1111-1111 for Alice Smith, CVV 123";
            CompletionRequest request = CompletionRequest.builder()
                    .prompt(sensitiveContent)
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "pci.guard"); 
            Promise<CompletionResult> result = guardRail.complete(request); 

            assertThat(result.isException()).isTrue();
            Throwable exception = result.getException();

            // Denial reason is surfaced (observable, actionable)
            assertThat(exception.getMessage()).contains("Credit card number detected");

            // Raw PII is never echoed back in the exception
            assertThat(exception.getMessage()).doesNotContain("4111-1111-1111-1111");
            assertThat(exception.getMessage()).doesNotContain("Alice Smith");
            assertThat(exception.getMessage()).doesNotContain("CVV 123");
        }

        @Test
        @DisplayName("denied tool request exception does not expose tool argument payload")
        void deniedToolRequestExceptionDoesNotExposeToolPayload() { 
            PolicyEvalResult toolDeny = mock(PolicyEvalResult.class);
            when(toolDeny.allowed()).thenReturn(false);
            when(toolDeny.reasons()).thenReturn(List.of("Sensitive data in tool invocation"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(toolDeny));

            String sensitiveArg = "patient_dob=1990-05-22&ssn=987-65-4321";
            CompletionRequest request = CompletionRequest.builder()
                    .prompt("Look up patient record with params: " + sensitiveArg)
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "hipaa.guard"); 
            Promise<CompletionResult> result = guardRail.completeWithTools(request, List.of()); 

            assertThat(result.isException()).isTrue();
            assertThat(result.getException().getMessage())
                    .doesNotContain("987-65-4321")
                    .doesNotContain("1990-05-22");
        }

        @Test
        @DisplayName("trace payload in metadata is not exposed in denial exception")
        void deniedRequestDoesNotExposeTracePayload() { 
            PolicyEvalResult traceDeny = mock(PolicyEvalResult.class);
            when(traceDeny.allowed()).thenReturn(false);
            when(traceDeny.reasons()).thenReturn(List.of("Trace payload contains secret token"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(traceDeny));

            String tracePayload = "traceToken=ghp_very_secret_trace_token";
            CompletionRequest request = CompletionRequest.builder()
                    .prompt("Process this trace metadata")
                    .metadata(Map.of("tracePayload", tracePayload))
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "trace.guard"); 
            Promise<CompletionResult> result = guardRail.complete(request); 

            assertThat(result.isException()).isTrue();
            assertThat(result.getException().getMessage())
                    .contains("Trace payload contains secret token")
                    .doesNotContain("ghp_very_secret_trace_token");
            verify(delegate, never()).complete(any());
        }

        @Test
        @DisplayName("audit metadata values are not exposed in denial exception")
        void deniedRequestDoesNotExposeAuditMetadata() { 
            PolicyEvalResult auditDeny = mock(PolicyEvalResult.class);
            when(auditDeny.allowed()).thenReturn(false);
            when(auditDeny.reasons()).thenReturn(List.of("Audit payload contains regulated identifier"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(auditDeny));

            String regulatedId = "patient-ssn-321-54-9876";
            CompletionRequest request = CompletionRequest.builder()
                    .prompt("Summarize audit event")
                    .metadata(Map.of("auditRecord", Map.of("regulatedId", regulatedId)))
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "audit.guard"); 
            Promise<CompletionResult> result = guardRail.complete(request); 

            assertThat(result.isException()).isTrue();
            assertThat(result.getException().getMessage())
                    .contains("Audit payload contains regulated identifier")
                    .doesNotContain("321-54-9876")
                    .doesNotContain(regulatedId);
            verify(delegate, never()).complete(any());
        }
    }

    // ── Tool Output Redaction ──────────────────────────────────────────────────

    @Nested
    @DisplayName("tool output redaction")
    class ToolOutputRedaction {

        @Test
        @DisplayName("tool-enabled request is blocked when policy detects sensitive tool context")
        void toolEnabledRequestBlockedWhenPolicyDetectsSensitiveContext() { 
            PolicyEvalResult toolOutputDeny = mock(PolicyEvalResult.class);
            when(toolOutputDeny.allowed()).thenReturn(false);
            when(toolOutputDeny.reasons()).thenReturn(List.of("Tool invocation with PII parameters"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(toolOutputDeny));

            CompletionRequest request = CompletionRequest.builder()
                    .prompt("Retrieve sensitive customer record")
                    .build(); 

            ToolDefinition sensitiveDataTool = mock(ToolDefinition.class);
            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "tool.output.guard"); 
            Promise<CompletionResult> result =
                    guardRail.completeWithTools(request, List.of(sensitiveDataTool)); 

            assertThat(result.isException()).isTrue();
            verify(delegate, never()).completeWithTools(any(), any());
        }

        @Test
        @DisplayName("allowed tool request is forwarded to delegate without modification")
        void allowedToolRequestIsForwardedUnmodified() { 
            PolicyEvalResult allowResult = mock(PolicyEvalResult.class);
            when(allowResult.allowed()).thenReturn(true);

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(allowResult));

            CompletionResult expectedResult = CompletionResult.builder().text("safe result").build();
            when(delegate.completeWithTools(any(), any())).thenReturn(Promise.of(expectedResult));

            CompletionRequest request = CompletionRequest.builder()
                    .prompt("What is the weather in London?")
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "tool.guard"); 
            Promise<CompletionResult> result =
                    guardRail.completeWithTools(request, List.of()); 

            assertThat(result.getResult()).isEqualTo(expectedResult);
            verify(delegate).completeWithTools(request, List.of());
        }
    }

    // ── Evaluation Dataset Redaction ───────────────────────────────────────────

    @Nested
    @DisplayName("evaluation dataset redaction")
    class EvaluationDatasetRedaction {

        @Test
        @DisplayName("eval dataset entry with PII is blocked before reaching the LLM")
        void evalDatasetEntryWithPiiIsBlocked() { 
            // Simulates: an eval runner submits dataset entries to the LLM for scoring.
            // If the entry contains PII, the policy guard rail must block it.
            PolicyEvalResult evalDeny = mock(PolicyEvalResult.class);
            when(evalDeny.allowed()).thenReturn(false);
            when(evalDeny.reasons()).thenReturn(List.of("Evaluation prompt contains email address"));

            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(evalDeny));

            // Eval entry formatted as a prompt for LLM-as-judge scoring
            CompletionRequest evalRequest = CompletionRequest.builder()
                    .prompt("[EVAL] Prompt: 'email customer@sensitive.org about their account'")
                    .metadata(Map.of("source", "eval-dataset", "datasetId", "ds-001"))
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "eval.pii.guard"); 
            Promise<CompletionResult> result = guardRail.complete(evalRequest); 

            assertThat(result.isException()).isTrue();
            assertThat(result.getException().getMessage())
                    .contains("Evaluation prompt contains email address");
            verify(delegate, never()).complete(any());
        }

        @Test
        @DisplayName("eval entry with tenant metadata uses policy path from metadata")
        void evalEntryWithTenantMetadataRoutesPolicyCorrectly() { 
            PolicyEvalResult allowResult = mock(PolicyEvalResult.class);
            when(allowResult.allowed()).thenReturn(true);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> policyPathCaptor = ArgumentCaptor.forClass(String.class);
            when(policyEngine.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(allowResult));

            CompletionResult expectedResult = CompletionResult.builder().text("scored").build();
            when(delegate.complete(any())).thenReturn(Promise.of(expectedResult));

            CompletionRequest evalRequest = CompletionRequest.builder()
                    .prompt("[EVAL] What is 2 + 2?")
                    .metadata(Map.of("policyPath", "eval.safe.guard", "tenantId", "eval-tenant"))
                    .build(); 

            PolicyGuardRail guardRail = new PolicyGuardRail(delegate, policyEngine, "default.guard"); 
            Promise<CompletionResult> result = guardRail.complete(evalRequest); 

            assertThat(result.getResult()).isEqualTo(expectedResult);
            // Verify policy was evaluated with metadata-specified path, not default
            verify(policyEngine).evaluate(anyString(), policyPathCaptor.capture(), any());
            assertThat(policyPathCaptor.getValue()).isEqualTo("eval.safe.guard");
        }
    }

    // ── Concurrent Request Isolation ───────────────────────────────────────────

    @Nested
    @DisplayName("concurrent request isolation")
    class ConcurrentRequestIsolation {

        @Test
        @DisplayName("simultaneous requests with different policies are independently evaluated")
        void simultaneousRequestsWithDifferentPoliciesAreIndependent() { 
            PolicyAsCodeEngine policyEngineAllow = mock(PolicyAsCodeEngine.class);
            PolicyAsCodeEngine policyEngineDeny = mock(PolicyAsCodeEngine.class);

            PolicyEvalResult allow = mock(PolicyEvalResult.class);
            when(allow.allowed()).thenReturn(true);

            PolicyEvalResult deny = mock(PolicyEvalResult.class);
            when(deny.allowed()).thenReturn(false);
            when(deny.reasons()).thenReturn(List.of("Blocked by pii.guard"));

            when(policyEngineAllow.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(allow));
            when(policyEngineDeny.evaluate(anyString(), anyString(), any()))
                    .thenReturn(Promise.of(deny));

            CompletionResult expected = CompletionResult.builder().text("safe").build();
            when(delegate.complete(any())).thenReturn(Promise.of(expected));

            PolicyGuardRail allowedRail = new PolicyGuardRail(delegate, policyEngineAllow, "open.guard"); 
            PolicyGuardRail blockedRail = new PolicyGuardRail(delegate, policyEngineDeny, "pii.guard"); 

            CompletionRequest safeRequest = CompletionRequest.builder().prompt("Safe prompt").build();
            CompletionRequest sensitiveRequest = CompletionRequest.builder().prompt("Sensitive prompt").build();

            Promise<CompletionResult> allowed = allowedRail.complete(safeRequest); 
            Promise<CompletionResult> blocked = blockedRail.complete(sensitiveRequest); 

            assertThat(allowed.isResult()).isTrue();
            assertThat(allowed.getResult()).isEqualTo(expected);

            assertThat(blocked.isException()).isTrue();
            assertThat(blocked.getException().getMessage()).contains("Blocked by pii.guard");
        }
    }
}
