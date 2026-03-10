package com.ghatana.yappc.agent.requirements;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.agent.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * AEP Step: REQUIREMENTS / PolicyCheck.
 *
 * <p>Validates requirements against organizational policies and compliance rules. Applies policy
 * packs for: security, privacy, compliance, quality gates.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Policy Actions:</h3>
 *
 * <ul>
 *   <li>PASS - Requirement meets all policies
 *   <li>WARN - Minor policy concerns, proceed with note
 *   <li>REQUIRE_REVIEW - Must go through HITL review
 *   <li>BLOCK - Critical policy violation, cannot proceed
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Requirements phase policy check step - validates against policies
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PolicyCheckStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  // Policy patterns for content validation
  private static final Pattern PII_PATTERN =
      Pattern.compile(
          "(?i)(ssn|social.?security|credit.?card|passport|driver.?license|\\b\\d{3}-\\d{2}-\\d{4}\\b)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern SECRETS_PATTERN =
      Pattern.compile(
          "(?i)(password|secret|api.?key|token|credential|private.?key)", Pattern.CASE_INSENSITIVE);
  private static final Pattern FORBIDDEN_CONTENT_PATTERN =
      Pattern.compile(
          "(?i)(disable.?audit|bypass.?security|skip.?validation|ignore.?policy)",
          Pattern.CASE_INSENSITIVE);

  public PolicyCheckStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.policycheck";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::runPolicyChecks)
        .then(this::persistPolicyResults)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for policy check"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(new IllegalArgumentException("Field 'requirementId' required"));
    }

    return Promise.of(context);
  }

  /**
   * Runs comprehensive policy checks on requirements. Checks: security, privacy, compliance,
   * quality, forbidden content.
   */
  private Promise<Map<String, Object>> runPolicyChecks(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String requirementId = (String) data.get("requirementId");

    List<String> functionalReqs = getListOrEmpty(data, "functionalRequirements");
    List<String> nonFunctionalReqs = getListOrEmpty(data, "nonFunctionalRequirements");
    List<String> acceptanceCriteria = getListOrEmpty(data, "acceptanceCriteria");
    String description = (String) data.getOrDefault("description", "");

    // Combine all text for policy scanning
    String allContent =
        String.join(" ", functionalReqs)
            + " "
            + String.join(" ", nonFunctionalReqs)
            + " "
            + String.join(" ", acceptanceCriteria)
            + " "
            + description;

    List<Map<String, Object>> policyFindings = new ArrayList<>();
    String overallAction = "PASS";

    // 1. Security policy checks
    Map<String, Object> securityCheck = checkSecurityPolicy(allContent);
    policyFindings.add(securityCheck);
    overallAction = escalateAction(overallAction, (String) securityCheck.get("action"));

    // 2. Privacy policy checks (PII detection)
    Map<String, Object> privacyCheck = checkPrivacyPolicy(allContent);
    policyFindings.add(privacyCheck);
    overallAction = escalateAction(overallAction, (String) privacyCheck.get("action"));

    // 3. Forbidden content checks
    Map<String, Object> forbiddenCheck = checkForbiddenContent(allContent);
    policyFindings.add(forbiddenCheck);
    overallAction = escalateAction(overallAction, (String) forbiddenCheck.get("action"));

    // 4. Quality gate checks
    Map<String, Object> qualityCheck = checkQualityGates(data);
    policyFindings.add(qualityCheck);
    overallAction = escalateAction(overallAction, (String) qualityCheck.get("action"));

    // 5. Compliance checks (NFR-specific)
    Map<String, Object> complianceCheck = checkComplianceRequirements(nonFunctionalReqs);
    policyFindings.add(complianceCheck);
    overallAction = escalateAction(overallAction, (String) complianceCheck.get("action"));

    // Build result
    Map<String, Object> policyResults = new HashMap<>();
    policyResults.put("requirementId", requirementId);
    policyResults.put("policyFindings", policyFindings);
    policyResults.put("overallAction", overallAction);
    policyResults.put("requiresHITL", "REQUIRE_REVIEW".equals(overallAction));
    policyResults.put("isBlocked", "BLOCK".equals(overallAction));
    policyResults.put("checkedAt", Instant.now().toString());
    policyResults.put("tenantId", context.getTenantId());
    policyResults.put("policyPackVersion", "requirements-v1.0");

    // Copy forward important fields
    policyResults.put("functionalRequirements", functionalReqs);
    policyResults.put("nonFunctionalRequirements", nonFunctionalReqs);
    policyResults.put("acceptanceCriteria", acceptanceCriteria);
    policyResults.put("overallValid", data.getOrDefault("overallValid", true));

    return Promise.of(policyResults);
  }

  private Promise<Map<String, Object>> persistPolicyResults(Map<String, Object> results) {
    return dbClient
        .insert("requirements_policy_checked", results)
        .map(
            dbResult -> {
              results.put("persisted", true);
              results.put("collection", "requirements_policy_checked");
              return results;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    String overallAction = (String) data.get("overallAction");
    boolean requiresHITL = (boolean) data.get("requiresHITL");
    boolean isBlocked = (boolean) data.get("isBlocked");

    String eventType;
    if (isBlocked) {
      eventType = "requirements.policy.blocked";
    } else if (requiresHITL) {
      eventType = "requirements.policy.review_required";
    } else {
      eventType = "requirements.policy.passed";
    }

    Map<String, Object> event =
        Map.of(
            "eventType",
            eventType,
            "requirementId",
            data.get("requirementId"),
            "overallAction",
            overallAction,
            "requiresHITL",
            requiresHITL,
            "findingCount",
            ((List<?>) data.get("policyFindings")).size(),
            "timestamp",
            Instant.now().toString());

    return eventClient.publish("requirements.policy.checked", event).map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> results) {
    return Promise.of(
        new WorkflowContextAdapter.Builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(results)
            .build());
  }

  private void handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "requirements.policy.error",
            "requirementId", context.getData().getOrDefault("requirementId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    eventClient.publish("requirements.errors", errorEvent);
  }

  // --- Policy Check Helper Methods ---

  private Map<String, Object> checkSecurityPolicy(String content) {
    boolean hasSecrets = SECRETS_PATTERN.matcher(content).find();

    return Map.of(
        "policyName",
        "security",
        "policyVersion",
        "v1.0",
        "action",
        hasSecrets ? "REQUIRE_REVIEW" : "PASS",
        "passed",
        !hasSecrets,
        "message",
        hasSecrets
            ? "Potential secrets/credentials detected - requires security review"
            : "No security concerns detected");
  }

  private Map<String, Object> checkPrivacyPolicy(String content) {
    boolean hasPII = PII_PATTERN.matcher(content).find();

    return Map.of(
        "policyName",
        "privacy",
        "policyVersion",
        "v1.0",
        "action",
        hasPII ? "REQUIRE_REVIEW" : "PASS",
        "passed",
        !hasPII,
        "message",
        hasPII ? "Potential PII detected - requires privacy review" : "No PII concerns detected");
  }

  private Map<String, Object> checkForbiddenContent(String content) {
    boolean hasForbidden = FORBIDDEN_CONTENT_PATTERN.matcher(content).find();

    return Map.of(
        "policyName",
        "forbidden_content",
        "policyVersion",
        "v1.0",
        "action",
        hasForbidden ? "BLOCK" : "PASS",
        "passed",
        !hasForbidden,
        "message",
        hasForbidden
            ? "Forbidden content detected - requirement blocked"
            : "No forbidden content detected");
  }

  private Map<String, Object> checkQualityGates(Map<String, Object> data) {
    List<String> acceptanceCriteria = getListOrEmpty(data, "acceptanceCriteria");
    boolean hasAcceptanceCriteria = !acceptanceCriteria.isEmpty();

    // Check if all acceptance criteria are testable (contain measurable terms)
    boolean areTestable =
        acceptanceCriteria.stream()
            .allMatch(
                ac -> ac.matches(".*\\b(must|should|shall|will|can|verify|validate|ensure)\\b.*"));

    String action = "PASS";
    String message = "Quality gates passed";

    if (!hasAcceptanceCriteria) {
      action = "REQUIRE_REVIEW";
      message = "Missing acceptance criteria - requires review";
    } else if (!areTestable) {
      action = "WARN";
      message = "Some acceptance criteria may not be testable";
    }

    return Map.of(
        "policyName", "quality_gates",
        "policyVersion", "v1.0",
        "action", action,
        "passed", "PASS".equals(action),
        "hasAcceptanceCriteria", hasAcceptanceCriteria,
        "areTestable", areTestable,
        "message", message);
  }

  private Map<String, Object> checkComplianceRequirements(List<String> nfrs) {
    // Check for required NFR categories
    boolean hasSecurityNFR =
        nfrs.stream()
            .anyMatch(
                nfr ->
                    nfr.toLowerCase().contains("security")
                        || nfr.toLowerCase().contains("authentication"));
    boolean hasPerformanceNFR =
        nfrs.stream()
            .anyMatch(
                nfr ->
                    nfr.toLowerCase().contains("performance")
                        || nfr.toLowerCase().contains("latency"));
    boolean hasAvailabilityNFR =
        nfrs.stream()
            .anyMatch(
                nfr ->
                    nfr.toLowerCase().contains("availability")
                        || nfr.toLowerCase().contains("uptime"));

    int missingCount = 0;
    List<String> missingCategories = new ArrayList<>();

    if (!hasSecurityNFR) {
      missingCount++;
      missingCategories.add("security");
    }
    if (!hasPerformanceNFR) {
      missingCount++;
      missingCategories.add("performance");
    }
    if (!hasAvailabilityNFR) {
      missingCount++;
      missingCategories.add("availability");
    }

    String action = missingCount == 0 ? "PASS" : (missingCount >= 2 ? "REQUIRE_REVIEW" : "WARN");
    String message =
        missingCount == 0
            ? "All required NFR categories present"
            : "Missing NFR categories: " + String.join(", ", missingCategories);

    return Map.of(
        "policyName",
        "compliance",
        "policyVersion",
        "v1.0",
        "action",
        action,
        "passed",
        "PASS".equals(action),
        "missingCategories",
        missingCategories,
        "message",
        message);
  }

  /** Escalates action to highest severity: BLOCK > REQUIRE_REVIEW > WARN > PASS */
  private String escalateAction(String current, String newAction) {
    Map<String, Integer> priority =
        Map.of(
            "PASS", 0,
            "WARN", 1,
            "REQUIRE_REVIEW", 2,
            "BLOCK", 3);

    int currentPriority = priority.getOrDefault(current, 0);
    int newPriority = priority.getOrDefault(newAction, 0);

    return newPriority > currentPriority ? newAction : current;
  }

  @SuppressWarnings("unchecked")
  private List<String> getListOrEmpty(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    return List.of();
  }
}
