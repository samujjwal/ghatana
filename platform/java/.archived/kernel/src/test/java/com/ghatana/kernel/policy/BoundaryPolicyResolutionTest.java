package com.ghatana.kernel.policy;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.test.TestKernelContextFactory;
import com.ghatana.kernel.registry.KernelRegistryImpl;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Kernel boundary policy resolution.
 * Validates policy enforcement at module boundaries.
 *
 * @doc.type class
 * @doc.purpose Validates Kernel boundary policy resolution and conflict handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Boundary Policy Resolution Tests")
class BoundaryPolicyResolutionTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;
    private TestPolicyResolver policyResolver;

    @BeforeEach
    void setUp() {
        registry = new KernelRegistryImpl();
        context = TestKernelContextFactory.create(registry);
        policyResolver = new TestPolicyResolver();
    }

    @Test
    @DisplayName("Should resolve single policy without conflicts")
    void testSinglePolicyResolution() {
        // GIVEN: Single policy
        BoundaryPolicy policy = new BoundaryPolicy("policy-1", PolicyType.ALLOW);
        policy.addRule("module:finance", "action:read");

        policyResolver.registerPolicy(policy);

        // WHEN: Resolve policy
        PolicyDecision decision = runPromise(() -> 
            policyResolver.resolve("module:finance", "action:read")
        );

        // THEN: Policy allows action
        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getAppliedPolicies()).containsExactly("policy-1");
    }

    @Test
    @DisplayName("Should resolve conflicting policies with priority")
    void testConflictingPolicyResolution() {
        // GIVEN: Conflicting policies with different priorities
        BoundaryPolicy allowPolicy = new BoundaryPolicy("allow-policy", PolicyType.ALLOW);
        allowPolicy.setPriority(10);
        allowPolicy.addRule("module:finance", "action:write");

        BoundaryPolicy denyPolicy = new BoundaryPolicy("deny-policy", PolicyType.DENY);
        denyPolicy.setPriority(20); // Higher priority
        denyPolicy.addRule("module:finance", "action:write");

        policyResolver.registerPolicy(allowPolicy);
        policyResolver.registerPolicy(denyPolicy);

        // WHEN: Resolve conflicting policies
        PolicyDecision decision = runPromise(() -> 
            policyResolver.resolve("module:finance", "action:write")
        );

        // THEN: Higher priority policy wins (deny)
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getAppliedPolicies()).contains("deny-policy");
    }

    @Test
    @DisplayName("Should handle policy inheritance across module boundaries")
    void testPolicyInheritance() {
        // GIVEN: Parent and child module policies
        BoundaryPolicy parentPolicy = new BoundaryPolicy("parent-policy", PolicyType.ALLOW);
        parentPolicy.addRule("module:*", "action:read");

        BoundaryPolicy childPolicy = new BoundaryPolicy("child-policy", PolicyType.DENY);
        childPolicy.addRule("module:finance:sensitive", "action:read");

        policyResolver.registerPolicy(parentPolicy);
        policyResolver.registerPolicy(childPolicy);

        // WHEN: Resolve for child module
        PolicyDecision generalDecision = runPromise(() -> 
            policyResolver.resolve("module:finance", "action:read")
        );
        PolicyDecision sensitiveDecision = runPromise(() -> 
            policyResolver.resolve("module:finance:sensitive", "action:read")
        );

        // THEN: Child policy overrides parent
        assertThat(generalDecision.isAllowed()).isTrue();
        assertThat(sensitiveDecision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Should enforce default deny policy")
    void testDefaultDenyPolicy() {
        // GIVEN: No explicit policies

        // WHEN: Resolve for unspecified resource
        PolicyDecision decision = runPromise(() -> 
            policyResolver.resolve("module:unknown", "action:execute")
        );

        // THEN: Default deny applies
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReason()).contains("No matching policy");
    }

    @Test
    @DisplayName("Should handle wildcard policy patterns")
    void testWildcardPolicyPatterns() {
        // GIVEN: Wildcard policy
        BoundaryPolicy wildcardPolicy = new BoundaryPolicy("wildcard-policy", PolicyType.ALLOW);
        wildcardPolicy.addRule("module:*", "action:read");
        wildcardPolicy.addRule("module:finance", "action:*");

        policyResolver.registerPolicy(wildcardPolicy);

        // WHEN: Resolve with wildcards
        PolicyDecision readAny = runPromise(() -> 
            policyResolver.resolve("module:phr", "action:read")
        );
        PolicyDecision financeAny = runPromise(() -> 
            policyResolver.resolve("module:finance", "action:delete")
        );

        // THEN: Wildcards match correctly
        assertThat(readAny.isAllowed()).isTrue();
        assertThat(financeAny.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should resolve time-based policies")
    void testTimeBasedPolicies() {
        // GIVEN: Time-based policy
        BoundaryPolicy timePolicy = new BoundaryPolicy("time-policy", PolicyType.ALLOW);
        timePolicy.addRule("module:finance", "action:trade");
        timePolicy.setTimeWindow(9, 17); // 9 AM to 5 PM

        policyResolver.registerPolicy(timePolicy);

        // WHEN: Resolve during and outside time window
        PolicyDecision duringHours = runPromise(() -> 
            policyResolver.resolveWithTime("module:finance", "action:trade", 12)
        );
        PolicyDecision afterHours = runPromise(() -> 
            policyResolver.resolveWithTime("module:finance", "action:trade", 20)
        );

        // THEN: Time window enforced
        assertThat(duringHours.isAllowed()).isTrue();
        assertThat(afterHours.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Should handle conditional policies")
    void testConditionalPolicies() {
        // GIVEN: Conditional policy
        ConditionalPolicy conditionalPolicy = new ConditionalPolicy("conditional-policy");
        conditionalPolicy.addCondition("user.role", "admin");
        conditionalPolicy.addRule("module:system", "action:configure");

        policyResolver.registerConditionalPolicy(conditionalPolicy);

        // WHEN: Resolve with and without condition
        Map<String, String> adminContext = Map.of("user.role", "admin");
        Map<String, String> userContext = Map.of("user.role", "user");

        PolicyDecision adminDecision = runPromise(() -> 
            policyResolver.resolveWithContext("module:system", "action:configure", adminContext)
        );
        PolicyDecision userDecision = runPromise(() -> 
            policyResolver.resolveWithContext("module:system", "action:configure", userContext)
        );

        // THEN: Condition enforced
        assertThat(adminDecision.isAllowed()).isTrue();
        assertThat(userDecision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Should audit policy decisions")
    void testPolicyDecisionAuditing() {
        // GIVEN: Policy with auditing enabled
        BoundaryPolicy policy = new BoundaryPolicy("audit-policy", PolicyType.ALLOW);
        policy.addRule("module:finance", "action:transfer");
        policy.setAuditEnabled(true);

        policyResolver.registerPolicy(policy);

        // WHEN: Resolve policy
        runPromise(() -> policyResolver.resolve("module:finance", "action:transfer"));

        // THEN: Decision is audited
        assertThat(policyResolver.getAuditLog()).isNotEmpty();
        assertThat(policyResolver.getAuditLog().get(0))
            .contains("module:finance")
            .contains("action:transfer")
            .contains("ALLOW");
    }

    // Test policy implementations

    private enum PolicyType {
        ALLOW,
        DENY
    }

    private static class BoundaryPolicy {
        private final String name;
        private final PolicyType type;
        private final Map<String, Set<String>> rules = new HashMap<>();
        private int priority = 0;
        private Integer startHour;
        private Integer endHour;
        private boolean auditEnabled = false;

        BoundaryPolicy(String name, PolicyType type) {
            this.name = name;
            this.type = type;
        }

        void addRule(String resource, String action) {
            rules.computeIfAbsent(resource, k -> new HashSet<>()).add(action);
        }

        void setPriority(int priority) {
            this.priority = priority;
        }

        void setTimeWindow(int startHour, int endHour) {
            this.startHour = startHour;
            this.endHour = endHour;
        }

        void setAuditEnabled(boolean enabled) {
            this.auditEnabled = enabled;
        }

        String getName() {
            return name;
        }

        PolicyType getType() {
            return type;
        }

        int getPriority() {
            return priority;
        }

        boolean matches(String resource, String action) {
            for (Map.Entry<String, Set<String>> entry : rules.entrySet()) {
                if (matchesPattern(entry.getKey(), resource)) {
                    for (String ruleAction : entry.getValue()) {
                        if (matchesPattern(ruleAction, action)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean isWithinTimeWindow(int currentHour) {
            if (startHour == null || endHour == null) {
                return true;
            }
            return currentHour >= startHour && currentHour < endHour;
        }

        boolean isAuditEnabled() {
            return auditEnabled;
        }

        private boolean matchesPattern(String pattern, String value) {
            if (pattern.equals("*") || pattern.endsWith(":*")) {
                String prefix = pattern.replace(":*", "");
                return value.startsWith(prefix) || pattern.equals("*");
            }
            return pattern.equals(value);
        }
    }

    private static class ConditionalPolicy extends BoundaryPolicy {
        private final Map<String, String> conditions = new HashMap<>();

        ConditionalPolicy(String name) {
            super(name, PolicyType.ALLOW);
        }

        void addCondition(String key, String value) {
            conditions.put(key, value);
        }

        boolean matchesConditions(Map<String, String> context) {
            for (Map.Entry<String, String> condition : conditions.entrySet()) {
                if (!condition.getValue().equals(context.get(condition.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class PolicyDecision {
        private final boolean allowed;
        private final List<String> appliedPolicies;
        private final String reason;

        PolicyDecision(boolean allowed, List<String> appliedPolicies, String reason) {
            this.allowed = allowed;
            this.appliedPolicies = appliedPolicies;
            this.reason = reason;
        }

        boolean isAllowed() {
            return allowed;
        }

        List<String> getAppliedPolicies() {
            return appliedPolicies;
        }

        String getReason() {
            return reason;
        }
    }

    private static class TestPolicyResolver {
        private final List<BoundaryPolicy> policies = new ArrayList<>();
        private final List<ConditionalPolicy> conditionalPolicies = new ArrayList<>();
        private final List<String> auditLog = new ArrayList<>();

        void registerPolicy(BoundaryPolicy policy) {
            policies.add(policy);
            policies.sort(Comparator.comparingInt(BoundaryPolicy::getPriority).reversed());
        }

        void registerConditionalPolicy(ConditionalPolicy policy) {
            conditionalPolicies.add(policy);
        }

        Promise<PolicyDecision> resolve(String resource, String action) {
            return resolveWithTime(resource, action, 12); // Default to noon
        }

        Promise<PolicyDecision> resolveWithTime(String resource, String action, int currentHour) {
            List<String> appliedPolicies = new ArrayList<>();
            
            for (BoundaryPolicy policy : policies) {
                if (policy.matches(resource, action) && policy.isWithinTimeWindow(currentHour)) {
                    appliedPolicies.add(policy.getName());
                    
                    if (policy.isAuditEnabled()) {
                        auditLog.add(String.format("Policy: %s, Resource: %s, Action: %s, Decision: %s",
                            policy.getName(), resource, action, policy.getType()));
                    }
                    
                    boolean allowed = policy.getType() == PolicyType.ALLOW;
                    return Promise.of(new PolicyDecision(allowed, appliedPolicies, 
                        policy.getType().toString()));
                }
            }
            
            return Promise.of(new PolicyDecision(false, appliedPolicies, 
                "No matching policy - default deny"));
        }

        Promise<PolicyDecision> resolveWithContext(String resource, String action, 
                                                   Map<String, String> context) {
            List<String> appliedPolicies = new ArrayList<>();
            
            for (ConditionalPolicy policy : conditionalPolicies) {
                if (policy.matches(resource, action) && policy.matchesConditions(context)) {
                    appliedPolicies.add(policy.getName());
                    return Promise.of(new PolicyDecision(true, appliedPolicies, "Conditional policy matched"));
                }
            }
            
            return resolve(resource, action);
        }

        List<String> getAuditLog() {
            return new ArrayList<>(auditLog);
        }
    }
}
