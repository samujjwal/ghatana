package com.ghatana.yappc.sdlc.architecture;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ARCHITECTURE / SynthesizeArchitecture.
 *
 * <p>Synthesizes architecture options based on approved requirements, constraints, and NFR targets.
 * Generates C4 views (Context, Container, Component) and deployment topology with ADR drafts for
 * key decisions.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Key Responsibilities:</h3>
 *
 * <ul>
 *   <li>Generate C4 Context view (system boundaries and external dependencies)
 *   <li>Generate C4 Container view (major architectural containers and their interactions)
 *   <li>Generate C4 Component view (internal component structure)
 *   <li>Generate deployment topology (infrastructure and operational view)
 *   <li>Draft ADRs for major architectural decisions
 *   <li>Map NFRs to architectural components and patterns
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Architecture phase synthesis step - generates architecture options
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SynthesizeArchitectureStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  // C4 View Types
  public static final String C4_CONTEXT = "C4_CONTEXT";
  public static final String C4_CONTAINER = "C4_CONTAINER";
  public static final String C4_COMPONENT = "C4_COMPONENT";
  public static final String DEPLOYMENT = "DEPLOYMENT";

  public SynthesizeArchitectureStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "architecture.synthesize";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::generateC4Views)
        .then(this::generateDeploymentTopology)
        .then(this::draftADRs)
        .then(this::mapNFRsToComponents)
        .then(this::persistArchitecture)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for architecture synthesis"));
    }

    if (!data.containsKey("architectureId")) {
      return Promise.ofException(new IllegalArgumentException("Field 'architectureId' required"));
    }

    if (!data.containsKey("qualityAttributes")) {
      return Promise.ofException(
          new IllegalArgumentException("Field 'qualityAttributes' required from intake"));
    }

    return Promise.of(context);
  }

  /**
   * Generates C4 architectural views: Context, Container, Component. Uses deterministic
   * template-based generation with requirement inputs.
   */
  private Promise<Map<String, Object>> generateC4Views(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String architectureId = (String) data.get("architectureId");

    @SuppressWarnings("unchecked")
    List<String> functionalReqs =
        (List<String>) data.getOrDefault("functionalRequirements", List.of());
    @SuppressWarnings("unchecked")
    List<String> qualityAttributes =
        (List<String>) data.getOrDefault("qualityAttributes", List.of());

    List<Map<String, Object>> views = new ArrayList<>();

    // 1. Generate C4 Context View (system boundaries)
    Map<String, Object> contextView = generateContextView(functionalReqs);
    views.add(contextView);

    // 2. Generate C4 Container View (major architectural components)
    Map<String, Object> containerView = generateContainerView(functionalReqs, qualityAttributes);
    views.add(containerView);

    // 3. Generate C4 Component View (internal structure)
    Map<String, Object> componentView = generateComponentView(functionalReqs);
    views.add(componentView);

    Map<String, Object> result = new HashMap<>(data);
    result.put("c4Views", views);
    result.put("viewCount", views.size());

    return Promise.of(result);
  }

  private Map<String, Object> generateContextView(List<String> functionalReqs) {
    // Identify external systems and user types from requirements
    Set<String> externalSystems = new HashSet<>();
    Set<String> userTypes = new HashSet<>();

    for (String req : functionalReqs) {
      String reqLower = req.toLowerCase();

      // Detect external integrations
      if (reqLower.contains("integrate with") || reqLower.contains("connect to")) {
        externalSystems.add(extractSystem(req));
      }

      // Detect user types
      if (reqLower.contains("user")
          || reqLower.contains("admin")
          || reqLower.contains("customer")) {
        userTypes.add(extractUserType(req));
      }
    }

    // Build Mermaid C4 Context diagram
    StringBuilder mermaid = new StringBuilder();
    mermaid.append("C4Context\n");
    mermaid.append("  title System Context Diagram\n\n");
    mermaid.append("  Person(user, \"User\", \"System user\")\n");

    for (String userType : userTypes) {
      if (!userType.equals("user")) {
        mermaid
            .append("  Person(")
            .append(sanitize(userType))
            .append(", \"")
            .append(capitalize(userType))
            .append("\", \"")
            .append(capitalize(userType))
            .append(" user\")\n");
      }
    }

    mermaid.append("  System(system, \"Target System\", \"Core application\")\n\n");

    for (String extSystem : externalSystems) {
      mermaid
          .append("  System_Ext(")
          .append(sanitize(extSystem))
          .append(", \"")
          .append(extSystem)
          .append("\", \"External system\")\n");
    }

    mermaid.append("\n  Rel(user, system, \"Uses\")\n");
    for (String extSystem : externalSystems) {
      mermaid
          .append("  Rel(system, ")
          .append(sanitize(extSystem))
          .append(", \"Integrates with\")\n");
    }

    return Map.of(
        "type", C4_CONTEXT,
        "diagram", mermaid.toString(),
        "metadata",
            Map.of(
                "externalSystemCount", externalSystems.size(),
                "userTypeCount", userTypes.size()));
  }

  private Map<String, Object> generateContainerView(
      List<String> functionalReqs, List<String> qualityAttributes) {
    // Determine container architecture based on quality attributes
    boolean needsHighAvailability = qualityAttributes.contains("availability");
    boolean needsHighPerformance = qualityAttributes.contains("performance");
    boolean needsScalability = qualityAttributes.contains("scalability");
    boolean needsSecurity = qualityAttributes.contains("security");

    // Build Mermaid C4 Container diagram
    StringBuilder mermaid = new StringBuilder();
    mermaid.append("C4Container\n");
    mermaid.append("  title Container Diagram\n\n");
    mermaid.append("  Person(user, \"User\", \"System user\")\n\n");

    // Core containers
    mermaid.append(
        "  Container(web_app, \"Web Application\", \"React/TypeScript\", \"User interface\")\n");
    mermaid.append("  Container(api, \"API Gateway\", \"Fastify/Node\", \"API orchestration\")\n");
    mermaid.append(
        "  Container(backend, \"Backend Services\", \"Java/ActiveJ\", \"Business logic\")\n");
    mermaid.append("  ContainerDb(database, \"Database\", \"PostgreSQL\", \"Data persistence\")\n");

    // Add containers based on quality attributes
    if (needsHighPerformance || needsScalability) {
      mermaid.append("  ContainerDb(cache, \"Cache\", \"Redis\", \"Performance caching\")\n");
    }

    if (needsSecurity) {
      mermaid.append(
          "  Container(auth, \"Auth Service\", \"OAuth2/OIDC\", \"Authentication & authorization\")\n");
    }

    // Relationships
    mermaid.append("\n  Rel(user, web_app, \"Uses\", \"HTTPS\")\n");
    mermaid.append("  Rel(web_app, api, \"Calls\", \"REST/GraphQL\")\n");
    mermaid.append("  Rel(api, backend, \"Invokes\", \"Internal\")\n");
    mermaid.append("  Rel(backend, database, \"Reads/Writes\", \"SQL\")\n");

    if (needsHighPerformance || needsScalability) {
      mermaid.append("  Rel(backend, cache, \"Reads/Writes\", \"Redis Protocol\")\n");
    }

    if (needsSecurity) {
      mermaid.append("  Rel(api, auth, \"Validates tokens\", \"Internal\")\n");
    }

    return Map.of(
        "type", C4_CONTAINER,
        "diagram", mermaid.toString(),
        "metadata",
            Map.of(
                "containerCount",
                needsHighPerformance || needsScalability ? 6 : 4,
                "hasCache",
                needsHighPerformance || needsScalability,
                "hasAuthService",
                needsSecurity));
  }

  private Map<String, Object> generateComponentView(List<String> functionalReqs) {
    // Generate component view for backend services
    StringBuilder mermaid = new StringBuilder();
    mermaid.append("C4Component\n");
    mermaid.append("  title Component Diagram - Backend Services\n\n");
    mermaid.append("  Container(api, \"API Gateway\", \"External\")\n\n");

    // Core components
    mermaid.append("  Component(controller, \"Controllers\", \"REST/GraphQL Endpoints\")\n");
    mermaid.append("  Component(service, \"Business Services\", \"Domain logic\")\n");
    mermaid.append("  Component(repository, \"Repositories\", \"Data access\")\n");
    mermaid.append("  ComponentDb(db, \"Database\", \"PostgreSQL\")\n\n");

    // Relationships
    mermaid.append("  Rel(api, controller, \"Routes requests\")\n");
    mermaid.append("  Rel(controller, service, \"Delegates to\")\n");
    mermaid.append("  Rel(service, repository, \"Uses\")\n");
    mermaid.append("  Rel(repository, db, \"Reads/Writes\")\n");

    return Map.of(
        "type", C4_COMPONENT,
        "diagram", mermaid.toString(),
        "metadata", Map.of("componentCount", 4));
  }

  private Promise<Map<String, Object>> generateDeploymentTopology(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    Map<String, Object> nfrTargets =
        (Map<String, Object>) data.getOrDefault("nfrTargets", Map.of());

    // Extract availability target
    @SuppressWarnings("unchecked")
    Map<String, Object> availabilityTarget =
        (Map<String, Object>) nfrTargets.getOrDefault("availability", Map.of("target", 99.9));
    double availability = ((Number) availabilityTarget.get("target")).doubleValue();

    // Build deployment topology based on NFRs
    StringBuilder deployment = new StringBuilder();
    deployment.append("graph TB\n");
    deployment.append("  subgraph \"Load Balancer\"\n");
    deployment.append("    LB[Load Balancer]\n");
    deployment.append("  end\n\n");

    if (availability >= 99.9) {
      // Multi-AZ deployment for high availability
      deployment.append("  subgraph \"Availability Zone 1\"\n");
      deployment.append("    APP1[App Instance 1]\n");
      deployment.append("    DB1[DB Primary]\n");
      deployment.append("  end\n\n");
      deployment.append("  subgraph \"Availability Zone 2\"\n");
      deployment.append("    APP2[App Instance 2]\n");
      deployment.append("    DB2[DB Replica]\n");
      deployment.append("  end\n\n");
      deployment.append("  LB --> APP1\n");
      deployment.append("  LB --> APP2\n");
      deployment.append("  APP1 --> DB1\n");
      deployment.append("  APP2 --> DB2\n");
      deployment.append("  DB1 -.Replication.-> DB2\n");
    } else {
      // Single-AZ deployment
      deployment.append("  subgraph \"Availability Zone\"\n");
      deployment.append("    APP[App Instance]\n");
      deployment.append("    DB[Database]\n");
      deployment.append("  end\n\n");
      deployment.append("  LB --> APP\n");
      deployment.append("  APP --> DB\n");
    }

    Map<String, Object> deploymentView =
        Map.of(
            "type", DEPLOYMENT,
            "diagram", deployment.toString(),
            "metadata",
                Map.of("multiAZ", availability >= 99.9, "targetAvailability", availability));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> views = (List<Map<String, Object>>) data.get("c4Views");
    views.add(deploymentView);

    data.put("c4Views", views);
    data.put("viewCount", views.size());
    data.put("deploymentTopology", deploymentView);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> draftADRs(Map<String, Object> data) {
    List<Map<String, Object>> adrs = new ArrayList<>();

    // ADR 1: Architecture Style
    adrs.add(
        createADR(
            "ADR-001",
            "Adopt Microservices Architecture Style",
            "Need scalable, independently deployable services",
            "Microservices architecture with event-driven communication",
            List.of("Monolithic architecture", "Service-oriented architecture (SOA)"),
            List.of(
                "+ Independent scaling and deployment",
                "+ Technology heterogeneity",
                "+ Fault isolation",
                "- Increased operational complexity",
                "- Distributed system challenges")));

    // ADR 2: Data Store
    adrs.add(
        createADR(
            "ADR-002",
            "Use PostgreSQL as Primary Data Store",
            "Need relational data model with ACID guarantees",
            "PostgreSQL with read replicas for scalability",
            List.of("MongoDB", "MySQL", "DynamoDB"),
            List.of(
                "+ ACID compliance",
                "+ Rich query capabilities",
                "+ JSON support for flexible schemas",
                "- Write scaling limitations (mitigated by sharding)")));

    // ADR 3: API Style
    adrs.add(
        createADR(
            "ADR-003",
            "Adopt GraphQL for Client-Facing APIs",
            "Need flexible API with client-driven data fetching",
            "GraphQL with DataLoader pattern for efficient batching",
            List.of("REST", "gRPC"),
            List.of(
                "+ Flexible queries reduce over/under-fetching",
                "+ Strong typing with schema",
                "+ Real-time support via subscriptions",
                "- Caching complexity",
                "- Query complexity management needed")));

    data.put("adrs", adrs);
    data.put("adrCount", adrs.size());

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> mapNFRsToComponents(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    Map<String, Object> nfrTargets =
        (Map<String, Object>) data.getOrDefault("nfrTargets", Map.of());

    List<Map<String, Object>> nfrMappings = new ArrayList<>();

    // Map latency NFR to caching strategy
    if (nfrTargets.containsKey("latency")) {
      nfrMappings.add(
          Map.of(
              "nfrAttribute", "latency",
              "target", nfrTargets.get("latency"),
              "architecturalStrategy", "Redis caching layer",
              "componentImpacted", "API Gateway + Backend Services"));
    }

    // Map availability NFR to deployment topology
    if (nfrTargets.containsKey("availability")) {
      nfrMappings.add(
          Map.of(
              "nfrAttribute", "availability",
              "target", nfrTargets.get("availability"),
              "architecturalStrategy", "Multi-AZ deployment with load balancing",
              "componentImpacted", "All services"));
    }

    // Map security NFR to auth service
    if (nfrTargets.containsKey("security")) {
      nfrMappings.add(
          Map.of(
              "nfrAttribute", "security",
              "target", nfrTargets.get("security"),
              "architecturalStrategy", "OAuth2/OIDC authentication + RBAC",
              "componentImpacted", "Auth Service + API Gateway"));
    }

    data.put("nfrMappings", nfrMappings);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistArchitecture(Map<String, Object> data) {
    return dbClient
        .insert("architectures", data)
        .map(
            dbResult -> {
              data.put("persisted", true);
              data.put("collection", "architectures");
              data.put("status", "DRAFT");
              return data;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    Map<String, Object> event =
        Map.of(
            "eventType", "architecture.synthesized",
            "architectureId", data.get("architectureId"),
            "viewCount", data.get("viewCount"),
            "adrCount", data.get("adrCount"),
            "timestamp", Instant.now().toString());

    return eventClient.publish("architecture.synthesized", event).map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> results) {
    return Promise.of(
        new com.ghatana.yappc.sdlc.WorkflowContextAdapter.Builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(results)
            .build());
  }

  private void handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "architecture.synthesis.error",
            "architectureId", context.getData().getOrDefault("architectureId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    eventClient.publish("architecture.errors", errorEvent);
  }

  // --- Helper Methods ---

  private Map<String, Object> createADR(
      String id,
      String title,
      String context,
      String decision,
      List<String> alternatives,
      List<String> consequences) {
    return Map.of(
        "adrId", id,
        "title", title,
        "context", context,
        "decision", decision,
        "alternatives", alternatives,
        "consequences", consequences,
        "status", "PROPOSED",
        "createdAt", Instant.now().toString());
  }

  private String extractSystem(String requirement) {
    // Simple extraction - in production use NLP
    String[] words = requirement.split(" ");
    for (int i = 0; i < words.length - 1; i++) {
      if (words[i].toLowerCase().contains("with") || words[i].toLowerCase().contains("to")) {
        return words[i + 1].replaceAll("[^a-zA-Z0-9]", "");
      }
    }
    return "ExternalSystem";
  }

  private String extractUserType(String requirement) {
    String reqLower = requirement.toLowerCase();
    if (reqLower.contains("admin")) return "admin";
    if (reqLower.contains("customer")) return "customer";
    return "user";
  }

  private String sanitize(String input) {
    return input.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
  }

  private String capitalize(String input) {
    if (input == null || input.isEmpty()) return input;
    return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
  }
}
