package com.ghatana.aep.pattern.spec;

import java.util.Map;

/**
 * Typed model for PatternSpec governance section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern governance
 * @doc.layer product
 * @doc.pattern Model
 */
/**
 * AEP-P1-005: All five governance controls are required for side-effecting capabilities
 * in production:
 * <ul>
 *   <li>{@code approvalPolicy} or {@code reviewPolicy} — human oversight</li>
 *   <li>{@code commitSha} — immutable truth binding</li>
 *   <li>{@code toolPolicy} — allowed-tool declaration</li>
 *   <li>{@code auditPolicy} — audit sink specification</li>
 *   <li>{@code rollbackPolicy} — compensation or rollback strategy</li>
 * </ul>
 */
 public record PatternGovernance(
         String commitSha,
         String approvalPolicy,
         String reviewPolicy,
         String toolPolicy,
         String auditPolicy,
         String rollbackPolicy,
         String idempotency,
         Map<String, Object> options) {

     /**
      * Backward-compatible constructor for callers that do not yet supply the new governance fields.
      * Defaults all AEP-P1-005 fields to {@code null}.
      */
     public PatternGovernance(String commitSha, String approvalPolicy, String reviewPolicy, Map<String, Object> options) {
         this(commitSha, approvalPolicy, reviewPolicy, null, null, null, null, options);
     }

     public Map<String, Object> toMap() {
         java.util.HashMap<String, Object> map = new java.util.HashMap<>();
         map.put("commitSha", commitSha != null ? commitSha : "");
         map.put("approvalPolicy", approvalPolicy != null ? approvalPolicy : "");
         map.put("reviewPolicy", reviewPolicy != null ? reviewPolicy : "");
         map.put("toolPolicy", toolPolicy != null ? toolPolicy : "");
         map.put("auditPolicy", auditPolicy != null ? auditPolicy : "");
         map.put("rollbackPolicy", rollbackPolicy != null ? rollbackPolicy : "");
         map.put("idempotency", idempotency != null ? idempotency : "");
         map.put("options", options != null ? options : Map.of());
         return java.util.Collections.unmodifiableMap(map);
     }
 }
