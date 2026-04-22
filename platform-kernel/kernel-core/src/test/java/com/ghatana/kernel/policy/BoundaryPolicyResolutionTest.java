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
@DisplayName("Boundary Policy Resolution Tests [GH-90000]")
class BoundaryPolicyResolutionTest extends EventloopTestBase {

    private KernelRegistryImpl registry;
    private KernelContext context;
    private TestPolicyResolver policyResolver;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new KernelRegistryImpl(); // GH-90000
        context = TestKernelContextFactory.create(registry); // GH-90000
        policyResolver = new TestPolicyResolver(); // GH-90000
    }

    @Test
    @DisplayName("Should resolve single policy without conflicts [GH-90000]")
    void testSinglePolicyResolution() { // GH-90000
        // GIVEN: Single policy
        BoundaryPolicy policy = new BoundaryPolicy("policy-1", PolicyType.ALLOW); // GH-90000
        policy.addRule("module:finance", "action:read"); // GH-90000

        policyResolver.registerPolicy(policy); // GH-90000

        // WHEN: Resolve policy
        PolicyDecision decision = runPromise(() -> // GH-90000
            policyResolver.resolve("module:finance", "action:read") // GH-90000
        );

        // THEN: Policy allows action
        assertThat(decision.isAllowed()).isTrue(); // GH-90000
        assertThat(decision.getAppliedPolicies()).containsExactly("policy-1 [GH-90000]");
    }

    @Test
    @DisplayName("Should resolve conflicting policies with priority [GH-90000]")
    void testConflictingPolicyResolution() { // GH-90000
        // GIVEN: Conflicting policies with different priorities
        BoundaryPolicy allowPolicy = new BoundaryPolicy("allow-policy", PolicyType.ALLOW); // GH-90000
        allowPolicy.setPriority(10); // GH-90000
        allowPolicy.addRule("module:finance", "action:write"); // GH-90000

        BoundaryPolicy denyPolicy = new BoundaryPolicy("deny-policy", PolicyType.DENY); // GH-90000
        denyPolicy.setPriority(20); // Higher priority // GH-90000
        denyPolicy.addRule("module:finance", "action:write"); // GH-90000

        policyResolver.registerPolicy(allowPolicy); // GH-90000
        policyResolver.registerPolicy(denyPolicy); // GH-90000

        // WHEN: Resolve conflicting policies
        PolicyDecision decision = runPromise(() -> // GH-90000
            policyResolver.resolve("module:finance", "action:write") // GH-90000
        );

        // THEN: Higher priority policy wins (deny) // GH-90000
        assertThat(decision.isAllowed()).isFalse(); // GH-90000
        assertThat(decision.getAppliedPolicies()).contains("deny-policy [GH-90000]");
    }

    @Test
    @DisplayName("Should handle policy inheritance across module boundaries [GH-90000]")
    void testPolicyInheritance() { // GH-90000
        // GIVEN: Parent and child module policies
        BoundaryPolicy parentPolicy = new BoundaryPolicy("parent-policy", PolicyType.ALLOW); // GH-90000
        parentPolicy.addRule("module:*", "action:read"); // GH-90000

        BoundaryPolicy childPolicy = new BoundaryPolicy("child-policy", PolicyType.DENY); // GH-90000
        childPolicy.setPriority(1); // GH-90000
        childPolicy.addRule("module:finance:sensitive", "action:read"); // GH-90000

        policyResolver.registerPolicy(parentPolicy); // GH-90000
        policyResolver.registerPolicy(childPolicy); // GH-90000

        // WHEN: Resolve for child module
        PolicyDecision generalDecision = runPromise(() -> // GH-90000
            policyResolver.resolve("module:finance", "action:read") // GH-90000
        );
        PolicyDecision sensitiveDecision = runPromise(() -> // GH-90000
            policyResolver.resolve("module:finance:sensitive", "action:read") // GH-90000
        );

        // THEN: Child policy overrides parent
        assertThat(generalDecision.isAllowed()).isTrue(); // GH-90000
        assertThat(sensitiveDecision.isAllowed()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should enforce default deny policy [GH-90000]")
    void testDefaultDenyPolicy() { // GH-90000
        // GIVEN: No explicit policies

        // WHEN: Resolve for unspecified resource
        PolicyDecision decision = runPromise(() -> // GH-90000
            policyResolver.resolve("module:unknown", "action:execute") // GH-90000
        );

        // THEN: Default deny applies
        assertThat(decision.isAllowed()).isFalse(); // GH-90000
        assertThat(decision.getReason()).contains("No matching policy [GH-90000]");
    }

    @Test
    @DisplayName("Should handle wildcard policy patterns [GH-90000]")
    void testWildcardPolicyPatterns() { // GH-90000
        // GIVEN: Wildcard policy
        BoundaryPolicy wildcardPolicy = new BoundaryPolicy("wildcard-policy", PolicyType.ALLOW); // GH-90000
        wildcardPolicy.addRule("module:*", "action:read"); // GH-90000
        wildcardPolicy.addRule("module:finance", "action:*"); // GH-90000

        policyResolver.registerPolicy(wildcardPolicy); // GH-90000

        // WHEN: Resolve with wildcards
        PolicyDecision readAny = runPromise(() -> // GH-90000
            policyResolver.resolve("module:phr", "action:read") // GH-90000
        );
        PolicyDecision financeAny = runPromise(() -> // GH-90000
            policyResolver.resolve("module:finance", "action:delete") // GH-90000
        );

        // THEN: Wildcards match correctly
        assertThat(readAny.isAllowed()).isTrue(); // GH-90000
        assertThat(financeAny.isAllowed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should resolve time-based policies [GH-90000]")
    void testTimeBasedPolicies() { // GH-90000
        // GIVEN: Time-based policy
        BoundaryPolicy timePolicy = new BoundaryPolicy("time-policy", PolicyType.ALLOW); // GH-90000
        timePolicy.addRule("module:finance", "action:trade"); // GH-90000
        timePolicy.setTimeWindow(9, 17); // 9 AM to 5 PM // GH-90000

        policyResolver.registerPolicy(timePolicy); // GH-90000

        // WHEN: Resolve during and outside time window
        PolicyDecision duringHours = runPromise(() -> // GH-90000
            policyResolver.resolveWithTime("module:finance", "action:trade", 12) // GH-90000
        );
        PolicyDecision afterHours = runPromise(() -> // GH-90000
            policyResolver.resolveWithTime("module:finance", "action:trade", 20) // GH-90000
        );

        // THEN: Time window enforced
        assertThat(duringHours.isAllowed()).isTrue(); // GH-90000
        assertThat(afterHours.isAllowed()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should handle conditional policies [GH-90000]")
    void testConditionalPolicies() { // GH-90000
        // GIVEN: Conditional policy
        ConditionalPolicy conditionalPolicy = new ConditionalPolicy("conditional-policy [GH-90000]");
        conditionalPolicy.addCondition("user.role", "admin"); // GH-90000
        conditionalPolicy.addRule("module:system", "action:configure"); // GH-90000

        policyResolver.registerConditionalPolicy(conditionalPolicy); // GH-90000

        // WHEN: Resolve with and without condition
        Map<String, String> adminContext = Map.of("user.role", "admin"); // GH-90000
        Map<String, String> userContext = Map.of("user.role", "user"); // GH-90000

        PolicyDecision adminDecision = runPromise(() -> // GH-90000
            policyResolver.resolveWithContext("module:system", "action:configure", adminContext) // GH-90000
        );
        PolicyDecision userDecision = runPromise(() -> // GH-90000
            policyResolver.resolveWithContext("module:system", "action:configure", userContext) // GH-90000
        );

        // THEN: Condition enforced
        assertThat(adminDecision.isAllowed()).isTrue(); // GH-90000
        assertThat(userDecision.isAllowed()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should audit policy decisions [GH-90000]")
    void testPolicyDecisionAuditing() { // GH-90000
        // GIVEN: Policy with auditing enabled
        BoundaryPolicy policy = new BoundaryPolicy("audit-policy", PolicyType.ALLOW); // GH-90000
        policy.addRule("module:finance", "action:transfer"); // GH-90000
        policy.setAuditEnabled(true); // GH-90000

        policyResolver.registerPolicy(policy); // GH-90000

        // WHEN: Resolve policy
        runPromise(() -> policyResolver.resolve("module:finance", "action:transfer")); // GH-90000

        // THEN: Decision is audited
        assertThat(policyResolver.getAuditLog()).isNotEmpty(); // GH-90000
        assertThat(policyResolver.getAuditLog().get(0)) // GH-90000
            .contains("module:finance [GH-90000]")
            .contains("action:transfer [GH-90000]")
            .contains("ALLOW [GH-90000]");
    }

    // Test policy implementations

    private enum PolicyType {
        ALLOW,
        DENY
    }

    private static class BoundaryPolicy {
        private final String name;
        private final PolicyType type;
        private final Map<String, Set<String>> rules = new HashMap<>(); // GH-90000
        private int priority = 0;
        private Integer startHour;
        private Integer endHour;
        private boolean auditEnabled = false;

        BoundaryPolicy(String name, PolicyType type) { // GH-90000
            this.name = name;
            this.type = type;
        }

        void addRule(String resource, String action) { // GH-90000
            rules.computeIfAbsent(resource, k -> new HashSet<>()).add(action); // GH-90000
        }

        void setPriority(int priority) { // GH-90000
            this.priority = priority;
        }

        void setTimeWindow(int startHour, int endHour) { // GH-90000
            this.startHour = startHour;
            this.endHour = endHour;
        }

        void setAuditEnabled(boolean enabled) { // GH-90000
            this.auditEnabled = enabled;
        }

        String getName() { // GH-90000
            return name;
        }

        PolicyType getType() { // GH-90000
            return type;
        }

        int getPriority() { // GH-90000
            return priority;
        }

        boolean matches(String resource, String action) { // GH-90000
            for (Map.Entry<String, Set<String>> entry : rules.entrySet()) { // GH-90000
                if (matchesPattern(entry.getKey(), resource)) { // GH-90000
                    for (String ruleAction : entry.getValue()) { // GH-90000
                        if (matchesPattern(ruleAction, action)) { // GH-90000
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean isWithinTimeWindow(int currentHour) { // GH-90000
            if (startHour == null || endHour == null) { // GH-90000
                return true;
            }
            return currentHour >= startHour && currentHour < endHour;
        }

        boolean isAuditEnabled() { // GH-90000
            return auditEnabled;
        }

        private boolean matchesPattern(String pattern, String value) { // GH-90000
            if (pattern.equals("* [GH-90000]") || pattern.endsWith(":* [GH-90000]")) {
                String prefix = pattern.replace(":*", ""); // GH-90000
                return value.startsWith(prefix) || pattern.equals("* [GH-90000]");
            }
            return pattern.equals(value); // GH-90000
        }
    }

    private static class ConditionalPolicy extends BoundaryPolicy {
        private final Map<String, String> conditions = new HashMap<>(); // GH-90000

        ConditionalPolicy(String name) { // GH-90000
            super(name, PolicyType.ALLOW); // GH-90000
        }

        void addCondition(String key, String value) { // GH-90000
            conditions.put(key, value); // GH-90000
        }

        boolean matchesConditions(Map<String, String> context) { // GH-90000
            for (Map.Entry<String, String> condition : conditions.entrySet()) { // GH-90000
                if (!condition.getValue().equals(context.get(condition.getKey()))) { // GH-90000
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

        PolicyDecision(boolean allowed, List<String> appliedPolicies, String reason) { // GH-90000
            this.allowed = allowed;
            this.appliedPolicies = appliedPolicies;
            this.reason = reason;
        }

        boolean isAllowed() { // GH-90000
            return allowed;
        }

        List<String> getAppliedPolicies() { // GH-90000
            return appliedPolicies;
        }

        String getReason() { // GH-90000
            return reason;
        }
    }

    private static class TestPolicyResolver {
        private final List<BoundaryPolicy> policies = new ArrayList<>(); // GH-90000
        private final List<ConditionalPolicy> conditionalPolicies = new ArrayList<>(); // GH-90000
        private final List<String> auditLog = new ArrayList<>(); // GH-90000

        void registerPolicy(BoundaryPolicy policy) { // GH-90000
            policies.add(policy); // GH-90000
            policies.sort(Comparator.comparingInt(BoundaryPolicy::getPriority).reversed()); // GH-90000
        }

        void registerConditionalPolicy(ConditionalPolicy policy) { // GH-90000
            conditionalPolicies.add(policy); // GH-90000
        }

        Promise<PolicyDecision> resolve(String resource, String action) { // GH-90000
            return resolveWithTime(resource, action, 12); // Default to noon // GH-90000
        }

        Promise<PolicyDecision> resolveWithTime(String resource, String action, int currentHour) { // GH-90000
            List<String> appliedPolicies = new ArrayList<>(); // GH-90000

            for (BoundaryPolicy policy : policies) { // GH-90000
                if (policy.matches(resource, action) && policy.isWithinTimeWindow(currentHour)) { // GH-90000
                    appliedPolicies.add(policy.getName()); // GH-90000

                    if (policy.isAuditEnabled()) { // GH-90000
                        auditLog.add(String.format("Policy: %s, Resource: %s, Action: %s, Decision: %s", // GH-90000
                            policy.getName(), resource, action, policy.getType())); // GH-90000
                    }

                    boolean allowed = policy.getType() == PolicyType.ALLOW; // GH-90000
                    return Promise.of(new PolicyDecision(allowed, appliedPolicies, // GH-90000
                        policy.getType().toString())); // GH-90000
                }
            }

            return Promise.of(new PolicyDecision(false, appliedPolicies, // GH-90000
                "No matching policy - default deny"));
        }

        Promise<PolicyDecision> resolveWithContext(String resource, String action, // GH-90000
                                                   Map<String, String> context) {
            List<String> appliedPolicies = new ArrayList<>(); // GH-90000

            for (ConditionalPolicy policy : conditionalPolicies) { // GH-90000
                if (policy.matches(resource, action) && policy.matchesConditions(context)) { // GH-90000
                    appliedPolicies.add(policy.getName()); // GH-90000
                    return Promise.of(new PolicyDecision(true, appliedPolicies, "Conditional policy matched")); // GH-90000
                }
            }

            return resolve(resource, action); // GH-90000
        }

        List<String> getAuditLog() { // GH-90000
            return new ArrayList<>(auditLog); // GH-90000
        }
    }
}
