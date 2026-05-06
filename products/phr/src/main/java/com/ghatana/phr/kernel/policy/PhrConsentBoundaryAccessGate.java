package com.ghatana.phr.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.BoundaryPolicyResolver;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;
import com.ghatana.phr.healthcare.domain.ConsentAction;
import com.ghatana.phr.healthcare.domain.DataClassification;
import com.ghatana.phr.healthcare.service.ConsentEnforcementService;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Product-owned access gate that composes PHR boundary policy resolution with
 * healthcare consent enforcement.
 *
 * <p>This is the single decision point for cross-scope or consent-sensitive PHR
 * reads. Kernel boundary rules decide whether a resource/action is structurally
 * allowed; the healthcare consent service then decides whether the actor is
 * personally authorized to proceed. Both layers must allow access.</p>
 *
 * @doc.type class
 * @doc.purpose Composed PHR boundary + consent access gate for sensitive read flows
 * @doc.layer product
 * @doc.pattern Service, PolicyEnforcement
 */
public final class PhrConsentBoundaryAccessGate {

    private final BoundaryPolicyResolver boundaryPolicyResolver;
    private final ConsentEnforcementService consentEnforcementService;
    private final AuditRecorder auditRecorder;

    public PhrConsentBoundaryAccessGate(
            ConsentEnforcementService consentEnforcementService,
            AuditRecorder auditRecorder) {
        this(
            new PhrRuleBasedBoundaryPolicyResolver(new PhrBoundaryPolicyStore(), BoundaryPolicyLoadContext.global()),
            consentEnforcementService,
            auditRecorder
        );
    }

    public PhrConsentBoundaryAccessGate(
            BoundaryPolicyResolver boundaryPolicyResolver,
            ConsentEnforcementService consentEnforcementService,
            AuditRecorder auditRecorder) {
        this.boundaryPolicyResolver = Objects.requireNonNull(boundaryPolicyResolver, "boundaryPolicyResolver must not be null");
        this.consentEnforcementService = Objects.requireNonNull(
            consentEnforcementService,
            "consentEnforcementService must not be null"
        );
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    }

    public Promise<AccessOutcome> authorize(AccessRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        BoundaryPolicyResolver.BoundaryDecision boundaryDecision = boundaryPolicyResolver.resolve(
            request.sourceScope(),
            request.targetScope(),
            request.resource(),
            request.boundaryAction(),
            ClassificationDescriptor.of(
                "healthcare",
                request.classification().toKernelLevel(),
                "nepal-2081"
            )
        );

        if (!boundaryDecision.allowed()) {
            return Promise.of(recordAndReturn(AccessOutcome.denied(
                request,
                boundaryDecision,
                null,
                "BOUNDARY_DENY",
                true
            )));
        }

        if (!request.enabledFeatures().containsAll(boundaryDecision.requiredFeatures())) {
            return Promise.of(recordAndReturn(AccessOutcome.denied(
                request,
                boundaryDecision,
                null,
                "MISSING_REQUIRED_FEATURE",
                true
            )));
        }

        if (!boundaryDecision.requiresConsent() && !request.emergencyOverride()) {
            return Promise.of(recordAndReturn(AccessOutcome.allowed(
                request,
                boundaryDecision,
                null,
                boundaryDecision.requiresAudit()
            )));
        }

        return consentEnforcementService.checkAccess(toConsentRequest(request))
            .map(consentDecision -> {
                if (!consentDecision.allowed()) {
                    return recordAndReturn(AccessOutcome.denied(
                        request,
                        boundaryDecision,
                        consentDecision,
                        consentDecision.reasonCode().name(),
                        true
                    ));
                }

                return recordAndReturn(AccessOutcome.allowed(
                    request,
                    boundaryDecision,
                    consentDecision,
                    boundaryDecision.requiresAudit() || consentDecision.auditRequired()
                ));
            });
    }

    private ConsentEnforcementService.ConsentCheckRequest toConsentRequest(AccessRequest request) {
        return new ConsentEnforcementService.ConsentCheckRequest(
            request.requestId(),
            request.tenantId(),
            request.patientId(),
            request.actorId(),
            request.actorType(),
            request.consentAction(),
            toHealthcareClassification(request.classification()),
            request.purposeOfUse(),
            request.emergencyOverride(),
            request.emergencyJustification()
        );
    }

    private DataClassification toHealthcareClassification(PhrDataClassification classification) {
        return switch (classification) {
            case C1 -> DataClassification.C1;
            case C2 -> DataClassification.C2;
            case C3 -> DataClassification.C3;
            case C4 -> DataClassification.C4;
        };
    }

    private AccessOutcome recordAndReturn(AccessOutcome outcome) {
        auditRecorder.record(new AuditEvent(
            outcome.request().requestId(),
            outcome.request().resource(),
            outcome.request().boundaryAction(),
            outcome.allowed(),
            outcome.reasonCode(),
            outcome.requiresAudit(),
            outcome.boundaryDecision().decisionMetadata().get("matched_rule"),
            outcome.request().emergencyOverride()
        ));
        return outcome;
    }

    public interface AuditRecorder {
        void record(AuditEvent event);
    }

    public record AuditEvent(
        String requestId,
        String resource,
        String action,
        boolean allowed,
        String reasonCode,
        boolean auditRequired,
        String matchedRuleId,
        boolean emergencyOverride
    ) {}

    public record AccessRequest(
        String requestId,
        String tenantId,
        java.util.UUID patientId,
        String actorId,
        ConsentEnforcementService.ConsentCheckRequest.ActorType actorType,
        ScopeDescriptor sourceScope,
        ScopeDescriptor targetScope,
        String resource,
        String boundaryAction,
        ConsentAction consentAction,
        PhrDataClassification classification,
        String purposeOfUse,
        Set<String> enabledFeatures,
        boolean emergencyOverride,
        String emergencyJustification
    ) {
        public AccessRequest {
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(patientId, "patientId must not be null");
            Objects.requireNonNull(actorId, "actorId must not be null");
            Objects.requireNonNull(actorType, "actorType must not be null");
            Objects.requireNonNull(sourceScope, "sourceScope must not be null");
            Objects.requireNonNull(targetScope, "targetScope must not be null");
            Objects.requireNonNull(resource, "resource must not be null");
            Objects.requireNonNull(boundaryAction, "boundaryAction must not be null");
            Objects.requireNonNull(consentAction, "consentAction must not be null");
            Objects.requireNonNull(classification, "classification must not be null");
            Objects.requireNonNull(purposeOfUse, "purposeOfUse must not be null");
            enabledFeatures = enabledFeatures == null ? Set.of() : Set.copyOf(enabledFeatures);
        }
    }

    public record AccessOutcome(
        AccessRequest request,
        boolean allowed,
        String reasonCode,
        boolean requiresAudit,
        BoundaryPolicyResolver.BoundaryDecision boundaryDecision,
        ConsentEnforcementService.AccessDecision consentDecision
    ) {
        static AccessOutcome allowed(
                AccessRequest request,
                BoundaryPolicyResolver.BoundaryDecision boundaryDecision,
                ConsentEnforcementService.AccessDecision consentDecision,
                boolean requiresAudit) {
            return new AccessOutcome(
                request,
                true,
                consentDecision != null ? consentDecision.reasonCode().name() : "BOUNDARY_ALLOW",
                requiresAudit,
                boundaryDecision,
                consentDecision
            );
        }

        static AccessOutcome denied(
                AccessRequest request,
                BoundaryPolicyResolver.BoundaryDecision boundaryDecision,
                ConsentEnforcementService.AccessDecision consentDecision,
                String reasonCode,
                boolean requiresAudit) {
            return new AccessOutcome(
                request,
                false,
                reasonCode,
                requiresAudit,
                boundaryDecision,
                consentDecision
            );
        }
    }

    private static final class PhrRuleBasedBoundaryPolicyResolver implements BoundaryPolicyResolver {

        private final BoundaryPolicyStore store;
        private final BoundaryPolicyLoadContext loadContext;

        private PhrRuleBasedBoundaryPolicyResolver(
                BoundaryPolicyStore store,
                BoundaryPolicyLoadContext loadContext) {
            this.store = Objects.requireNonNull(store, "store must not be null");
            this.loadContext = Objects.requireNonNull(loadContext, "loadContext must not be null");
        }

        @Override
        public BoundaryDecision resolve(
                ScopeDescriptor source,
                ScopeDescriptor target,
                String resource,
                String action,
                ClassificationDescriptor classification) {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(resource, "resource must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(classification, "classification must not be null");

            List<BoundaryPolicyRule> rules = store.loadRules(loadContext);
            for (BoundaryPolicyRule rule : rules) {
                if (!matchesRule(rule, source, target, resource, action, classification)) {
                    continue;
                }
                return effectToDecision(rule, classification);
            }

            return BoundaryDecision.deny(
                "No matching allow rule for "
                    + source.getScopeId() + " -> " + target.getScopeId()
                    + " [" + resource + ":" + action + "]"
            );
        }

        private boolean matchesRule(
                BoundaryPolicyRule rule,
                ScopeDescriptor source,
                ScopeDescriptor target,
                String resource,
                String action,
                ClassificationDescriptor classification) {
            if ("same".equalsIgnoreCase(rule.getTargetScopePattern())) {
                if (!source.equals(target)) {
                    return false;
                }
            } else if (!globMatches(rule.getTargetScopePattern(), target.getScopeId())) {
                return false;
            }

            if (!globMatches(rule.getSourceScopePattern(), source.getScopeId())) {
                return false;
            }
            if (!globMatches(rule.getResourcePattern(), resource)) {
                return false;
            }
            if (!(rule.getActions().contains("*") || rule.getActions().contains(action))) {
                return false;
            }

            return matchesClassification(rule.getClassificationCondition(), classification);
        }

        private boolean matchesClassification(
                String condition,
                ClassificationDescriptor classification) {
            if ("*".equals(condition)) {
                return true;
            }
            String lowerCondition = condition.toLowerCase(Locale.ROOT);
            if (lowerCondition.startsWith("sensitivity:")) {
                String level = lowerCondition.substring("sensitivity:".length()).toUpperCase(Locale.ROOT);
                return classification.getSensitivityLevel().name().equals(level);
            }
            if (lowerCondition.startsWith("compliance-tag:")) {
                String tag = condition.substring("compliance-tag:".length());
                return classification.hasComplianceTag(tag);
            }
            if (lowerCondition.startsWith("domain:")) {
                String domain = condition.substring("domain:".length());
                return classification.getDomain().equalsIgnoreCase(domain);
            }
            return false;
        }

        private BoundaryDecision effectToDecision(
                BoundaryPolicyRule rule,
                ClassificationDescriptor classification) {
            return switch (rule.getEffect()) {
                case DENY -> BoundaryDecision.deny("Access denied by rule '" + rule.getRuleId() + "'");
                case REQUIRE_APPROVAL -> BoundaryDecision.deny(
                    "Access requires approval workflow (rule: " + rule.getRuleId() + ")"
                );
                case ALLOW -> new BoundaryDecision(
                    true,
                    rule.isRequiresConsent(),
                    rule.isRequiresAudit(),
                    rule.getRequiredFeatures(),
                    Map.of(
                        "matched_rule", rule.getRuleId(),
                        "source_sensitivity", classification.getSensitivityLevel().name()
                    )
                );
            };
        }

        private boolean globMatches(String pattern, String value) {
            if ("*".equals(pattern) || "**".equals(pattern)) {
                return true;
            }
            if (pattern.equals(value)) {
                return true;
            }
            if (pattern.startsWith("**/")) {
                return value.endsWith(pattern.substring(3));
            }
            if (pattern.endsWith("/**")) {
                return value.startsWith(pattern.substring(0, pattern.length() - 3));
            }
            if (pattern.contains("/**")) {
                String[] parts = pattern.split("/\\*\\*/", 2);
                if (parts.length == 2) {
                    return value.startsWith(parts[0]) && value.endsWith(parts[1]);
                }
            }
            if (pattern.startsWith("*") && pattern.endsWith("*") && pattern.length() > 2) {
                return value.contains(pattern.substring(1, pattern.length() - 1));
            }
            if (pattern.startsWith("*")) {
                return value.endsWith(pattern.substring(1));
            }
            if (pattern.endsWith("*")) {
                return value.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return false;
        }
    }
}
