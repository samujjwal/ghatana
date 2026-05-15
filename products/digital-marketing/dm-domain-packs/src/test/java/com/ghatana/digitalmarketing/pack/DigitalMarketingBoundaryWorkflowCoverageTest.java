package com.ghatana.digitalmarketing.pack;

import com.ghatana.kernel.policy.BoundaryPolicyResolver;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.policy.ClassificationDescriptor.SensitivityLevel;
import com.ghatana.kernel.policy.DefaultBoundaryPolicyResolver;
import com.ghatana.kernel.scope.ScopeDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow-level coverage for every DMOS boundary rule.
 *
 * @doc.type test
 * @doc.purpose Proves each DM-BP rule has an allowed/blocked product workflow scenario
 * @doc.layer product
 * @doc.pattern ContractTest
 */
@DisplayName("Digital Marketing boundary workflow coverage")
class DigitalMarketingBoundaryWorkflowCoverageTest {

    private static final ScopeDescriptor DMOS_WORKFLOW = ScopeDescriptor.product("digital-marketing.workflow");
    private static final ScopeDescriptor DMOS_TARGET = ScopeDescriptor.product("digital-marketing.domain");
    private static final ClassificationDescriptor MARKETING_DATA =
        ClassificationDescriptor.of("marketing", SensitivityLevel.CONFIDENTIAL, "marketing-consent");

    private final BoundaryPolicyResolver resolver =
        new DefaultBoundaryPolicyResolver(new DigitalMarketingBoundaryPolicyStore());

    @Test
    @DisplayName("every DM-BP rule is exercised by a product workflow allow/deny scenario")
    void shouldExerciseEveryBoundaryRuleWithWorkflowScenarios() {
        List<WorkflowScenario> scenarios = List.of(
            allow("DM-BP-001", "workspace dashboard read", "digital-marketing:workspaces/ws-1", "read", false, false),
            allow("DM-BP-002", "contact profile read", "digital-marketing:contacts/contact-1", "read", true, true),
            approval("DM-BP-003", "contact export mutation", "digital-marketing:contacts/contact-1", "export"),
            approval("DM-BP-004", "audience sync operation", "digital-marketing:audiences/audience-1", "digital-marketing:sync"),
            approval("DM-BP-005", "campaign launch", "digital-marketing:campaigns/campaign-1", "digital-marketing:launch"),
            approval("DM-BP-006", "budget increase", "digital-marketing:budgets/budget-1", "digital-marketing:increase"),
            approval("DM-BP-007", "content publish", "digital-marketing:content/version-1", "digital-marketing:publish"),
            approval("DM-BP-008", "connector execute", "digital-marketing:connectors/google-ads", "digital-marketing:execute"),
            deny("DM-BP-999", "unknown product workflow", "marketplace/listing-1", "delete")
        );

        scenarios.forEach(this::assertScenario);
    }

    @Test
    @DisplayName("every DM-BP workflow has a blocked direct variant")
    void shouldDenyDirectOrMismatchedWorkflowVariants() {
        List<WorkflowScenario> blockedVariants = List.of(
            deny("DM-BP-001", "workspace write without mutation rule", "digital-marketing:workspaces/ws-1", "write"),
            deny("DM-BP-002", "contact read from untrusted scope", ScopeDescriptor.product("external.partner"), "digital-marketing:contacts/contact-1", "read"),
            deny("DM-BP-003", "contact archive without registered action", "digital-marketing:contacts/contact-1", "archive"),
            deny("DM-BP-004", "audience read without export/sync action", "digital-marketing:audiences/audience-1", "read"),
            deny("DM-BP-005", "campaign delete without lifecycle action", "digital-marketing:campaigns/campaign-1", "delete"),
            deny("DM-BP-006", "budget read without mutation action", "digital-marketing:budgets/budget-1", "read"),
            deny("DM-BP-007", "content draft read without publish action", "digital-marketing:content/version-1", "read"),
            deny("DM-BP-008", "connector read without write/execute action", "digital-marketing:connectors/google-ads", "read"),
            deny("DM-BP-999", "unknown product workflow remains denied", "unknown/resource-1", "execute")
        );

        blockedVariants.forEach(this::assertScenario);
    }

    private void assertScenario(WorkflowScenario scenario) {
        BoundaryPolicyResolver.BoundaryDecision decision = resolver.resolve(
            scenario.source(),
            DMOS_TARGET,
            scenario.resource(),
            scenario.action(),
            MARKETING_DATA
        );

        if (scenario.expectedAllowed()) {
            assertThat(decision.allowed())
                .as("%s should be allowed by %s", scenario.name(), scenario.ruleId())
                .isTrue();
            assertThat(decision.requiresConsent()).isEqualTo(scenario.expectedConsent());
            assertThat(decision.requiresAudit()).isEqualTo(scenario.expectedAudit());
            assertThat(decision.decisionMetadata()).containsEntry("matched_rule", scenario.ruleId());
            return;
        }

        assertThat(decision.allowed())
            .as("%s should be blocked by %s", scenario.name(), scenario.ruleId())
            .isFalse();
        String reason = decision.decisionMetadata().get("reason");
        assertThat(reason)
            .as("%s should expose a blocking rule or fail-closed reason", scenario.name())
            .isNotBlank();
        if (scenario.expectedApproval()) {
            assertThat(reason).contains("requires approval").contains(scenario.ruleId());
        }
    }

    private static WorkflowScenario allow(
            String ruleId,
            String name,
            String resource,
            String action,
            boolean expectedConsent,
            boolean expectedAudit) {
        return new WorkflowScenario(ruleId, name, DMOS_WORKFLOW, resource, action, true, false, expectedConsent, expectedAudit);
    }

    private static WorkflowScenario approval(String ruleId, String name, String resource, String action) {
        return new WorkflowScenario(ruleId, name, DMOS_WORKFLOW, resource, action, false, true, false, true);
    }

    private static WorkflowScenario deny(String ruleId, String name, String resource, String action) {
        return deny(ruleId, name, DMOS_WORKFLOW, resource, action);
    }

    private static WorkflowScenario deny(
            String ruleId,
            String name,
            ScopeDescriptor source,
            String resource,
            String action) {
        return new WorkflowScenario(ruleId, name, source, resource, action, false, false, false, true);
    }

    private record WorkflowScenario(
        String ruleId,
        String name,
        ScopeDescriptor source,
        String resource,
        String action,
        boolean expectedAllowed,
        boolean expectedApproval,
        boolean expectedConsent,
        boolean expectedAudit
    ) {
    }
}
