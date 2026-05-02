package com.ghatana.kernel.policy;

import com.ghatana.kernel.scope.ScopeDescriptor;

import java.util.Objects;

/**
 * Encapsulates a cross-scope access request for boundary policy evaluation.
 *
 * <p>Passed to {@link BoundaryPolicyResolver} and internally to
 * {@link BoundaryPolicyRule} pattern matching. Carrying the request as a
 * value object keeps the resolver API clean and makes rule matching testable
 * without wiring real scope or classification objects.</p>
 *
 * @doc.type class
 * @doc.purpose Immutable cross-scope access request for rule evaluation
 * @doc.layer core
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.2.0
 */
public final class BoundaryPolicyEvaluationRequest {

    private final ScopeDescriptor source;
    private final ScopeDescriptor target;
    private final String resource;
    private final String action;
    private final ClassificationDescriptor classification;

    private BoundaryPolicyEvaluationRequest(ScopeDescriptor source, ScopeDescriptor target,
                                            String resource, String action,
                                            ClassificationDescriptor classification) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.resource = Objects.requireNonNull(resource, "resource cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.classification = Objects.requireNonNull(classification, "classification cannot be null");
        if (resource.isBlank()) throw new IllegalArgumentException("resource cannot be blank");
        if (action.isBlank()) throw new IllegalArgumentException("action cannot be blank");
    }

    public static BoundaryPolicyEvaluationRequest of(ScopeDescriptor source, ScopeDescriptor target,
                                                      String resource, String action,
                                                      ClassificationDescriptor classification) {
        return new BoundaryPolicyEvaluationRequest(source, target, resource, action, classification);
    }

    public ScopeDescriptor getSource() { return source; }
    public ScopeDescriptor getTarget() { return target; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public ClassificationDescriptor getClassification() { return classification; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundaryPolicyEvaluationRequest that = (BoundaryPolicyEvaluationRequest) o;
        return Objects.equals(source, that.source) && Objects.equals(target, that.target) &&
               Objects.equals(resource, that.resource) && Objects.equals(action, that.action) &&
               Objects.equals(classification, that.classification);
    }

    @Override
    public int hashCode() { return Objects.hash(source, target, resource, action, classification); }

    @Override
    public String toString() {
        return "BoundaryPolicyEvaluationRequest{source=" + source + ", target=" + target +
               ", resource='" + resource + "', action='" + action + "'}";
    }
}
