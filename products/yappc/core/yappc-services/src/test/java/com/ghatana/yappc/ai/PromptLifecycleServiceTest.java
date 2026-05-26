package com.ghatana.yappc.ai;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Tests audited prompt lifecycle promotion, rollback, scoring, and rebalancing
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("PromptLifecycleService")
class PromptLifecycleServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("promotes, rolls back, scores, rebalances, and audits prompt versions")
    void promptLifecycleIsAuditedEndToEnd() {
        AuditLogger auditLogger = mock(AuditLogger.class);
        when(auditLogger.log(any())).thenReturn(Promise.complete());
        PromptTemplateRegistry registry = new PromptTemplateRegistry();
        PromptLifecycleService service = new PromptLifecycleService(registry, auditLogger);

        service.register(PromptTemplateVersion.of("intent.capture", "v1", "baseline", "v1", 50));
        service.register(PromptTemplateVersion.of("intent.capture", "v2", "baseline", "v2-a", 50));
        service.register(PromptTemplateVersion.of("intent.capture", "v2", "concise", "v2-b", 50));

        PromptLifecycleService.PromptLifecycleDecision promoted = runPromise(() ->
                service.promote("intent.capture", "v2", "admin-1", "winner passed eval"));

        assertThat(promoted.applied()).isTrue();
        assertThat(promoted.action()).isEqualTo("PROMOTED");
        assertThat(registry.activeVersion("intent.capture")).contains("v2");
        assertThat(service.active("intent.capture")).map(PromptTemplateVersion::version).contains("v2");

        service.recordScore("intent.capture", "v2", "baseline", 0.9);
        service.recordScore("intent.capture", "v2", "baseline", 0.85);
        service.recordScore("intent.capture", "v2", "concise", 0.3);
        service.recordScore("intent.capture", "v2", "concise", 0.25);
        PromptLifecycleService.PromptLifecycleDecision rebalanced = runPromise(() ->
                service.rebalanceWeights("intent.capture", "v2", 2, "admin-1", "rebalance by score"));

        assertThat(rebalanced.applied()).isTrue();
        assertThat(registry.find("intent.capture", "v2", "baseline").orElseThrow().weight())
                .isGreaterThan(registry.find("intent.capture", "v2", "concise").orElseThrow().weight());

        PromptLifecycleService.PromptLifecycleDecision rolledBack = runPromise(() ->
                service.rollback("intent.capture", "v1", "admin-1", "quality regression"));

        assertThat(rolledBack.applied()).isTrue();
        assertThat(rolledBack.previousVersion()).isEqualTo("v2");
        assertThat(registry.activeVersion("intent.capture")).contains("v1");

        verify(auditLogger, times(3)).log(argThat(event ->
                String.valueOf(event.get("eventType")).startsWith("prompt.lifecycle.")
                        && "intent.capture".equals(event.get("promptKey"))
                        && "admin-1".equals(event.get("actorId"))
                        && event.containsKey("reason")));
    }
}
