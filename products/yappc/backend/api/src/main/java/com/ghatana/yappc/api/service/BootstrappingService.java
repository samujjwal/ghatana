/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.BootstrappingSession;
import com.ghatana.yappc.api.domain.BootstrappingSession.*;
import com.ghatana.yappc.api.repository.BootstrappingSessionRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing bootstrapping sessions.
 *
 * @doc.type class
 * @doc.purpose Business logic for bootstrapping operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class BootstrappingService {

    private static final Logger logger = LoggerFactory.getLogger(BootstrappingService.class);

    private final BootstrappingSessionRepository sessionRepository;
    private final AuditService auditService;
    private final LLMGateway llmGateway;

    @Inject
    public BootstrappingService(
            BootstrappingSessionRepository sessionRepository,
            AuditService auditService,
            LLMGateway llmGateway) {
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
        this.llmGateway = llmGateway;
    }

    /**
     * Start a new bootstrapping session.
     */
    public Promise<BootstrappingSession> startSession(String tenantId, StartSessionInput input) {
        logger.info("Starting bootstrapping session for tenant: {}", tenantId);

        BootstrappingSession session = new BootstrappingSession();
        session.setTenantId(tenantId);
        session.setWorkspaceId(input.workspaceId());
        session.setUserId(input.userId());
        session.setInitialIdea(input.initialPrompt());
        session.setStatus(SessionStatus.CONVERSING);

        // Add initial user turn
        if (input.initialPrompt() == null || input.initialPrompt().isEmpty()) {
            return sessionRepository.save(session);
        }

        session.addConversationTurn("user", input.initialPrompt());
        return generateInitialResponse(input.initialPrompt())
                .map(aiResponse -> {
                    session.addConversationTurn("assistant", aiResponse);
                    return session;
                })
                .then(sessionRepository::save);
    }

    /**
     * Get a session by ID.
     */
    public Promise<Optional<BootstrappingSession>> getSession(String tenantId, UUID sessionId) {
        return sessionRepository.findById(tenantId, sessionId);
    }

    /**
     * List sessions for a workspace (via user's sessions filtered by workspace).
     */
    public Promise<List<BootstrappingSession>> listWorkspaceSessions(String tenantId, String workspaceId) {
        return sessionRepository.findAllByTenant(tenantId)
                .map(sessions -> sessions.stream()
                        .filter(s -> workspaceId.equals(s.getWorkspaceId()))
                        .toList());
    }

    /**
     * Add a conversation turn.
     */
    public Promise<BootstrappingSession> addConversationTurn(
            String tenantId, UUID sessionId, String userMessage, String userId) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    if (session.getStatus() != SessionStatus.CONVERSING) {
                        return Promise.ofException(new IllegalStateException(
                                "Cannot add conversation turn - session is not in CONVERSING state"));
                    }

                    // Add user message
                    session.addConversationTurn("user", userMessage);

                    return generateConversationResponse(session)
                            .map(aiResponse -> {
                                session.addConversationTurn("assistant", aiResponse);

                                // Check if we should transition to PLANNING
                                if (shouldTransitionToPlanning(session)) {
                                    session.transitionTo(SessionStatus.PLANNING);
                                }
                                return session;
                            })
                            .then(sessionRepository::save);
                });
    }

    /**
     * Set the project definition.
     */
    public Promise<BootstrappingSession> setProjectDefinition(
            String tenantId, UUID sessionId, ProjectDefinition definition) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    session.setProjectDefinition(definition);
                    if (session.getStatus() == SessionStatus.CONVERSING) {
                        session.transitionTo(SessionStatus.PLANNING);
                    }

                    return sessionRepository.save(session);
                });
    }

    /**
     * Refine the project based on user feedback.
     */
    public Promise<BootstrappingSession> refineProject(
            String tenantId, UUID sessionId, String refinementPrompt) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    // Add refinement as conversation turn
                    session.addConversationTurn("user", refinementPrompt);

                    return generateRefinementResponse(session, refinementPrompt)
                            .map(refinedResponse -> {
                                session.addConversationTurn("assistant", refinedResponse);
                                return session;
                            })
                            .then(updatedSession -> refineProjectDefinition(updatedSession, refinementPrompt)
                                    .map($ -> updatedSession))
                            .then(sessionRepository::save);
                });
    }

    /**
     * Generate the project graph for visualization.
     */
    public Promise<ProjectGraph> generateProjectGraph(String tenantId, UUID sessionId) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    ProjectDefinition definition = session.getProjectDefinition();
                    if (definition == null) {
                        return Promise.ofException(new IllegalStateException(
                                "Cannot generate graph - project definition not set"));
                    }

                    // Build graph from project definition
                    ProjectGraph graph = buildProjectGraph(definition, session);
                    session.setProjectGraph(graph);

                    return sessionRepository.save(session)
                            .map(BootstrappingSession::getProjectGraph);
                });
    }

    /**
     * Validate the project.
     */
    public Promise<ValidationReport> validateProject(String tenantId, UUID sessionId) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    if (session.getProjectDefinition() == null) {
                        return Promise.ofException(new IllegalStateException(
                                "Cannot validate - project definition not set"));
                    }

                    // Perform validation (placeholder for AI validation)
                    ValidationReport report = performValidation(session);
                    session.setValidationReport(report);

                    // Transition to validating if not already
                    if (session.getStatus() == SessionStatus.PLANNING) {
                        session.transitionTo(SessionStatus.VALIDATING);
                    }

                    return sessionRepository.save(session)
                            .map(BootstrappingSession::getValidationReport);
                });
    }

    /**
     * Approve the project. This marks the session as approved and prepares
     * transition data. The actual project creation is delegated to the caller.
     */
    public Promise<BootstrappingSession> approveProject(
            String tenantId, UUID sessionId, ApproveProjectInput input) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException("Session not found"));
                    }
                    BootstrappingSession session = opt.get();

                    ValidationReport report = session.getValidationReport();
                    if (report != null && !report.getBlockers().isEmpty()) {
                        return Promise.ofException(new IllegalStateException(
                                "Cannot approve - validation has blockers"));
                    }

                    // Transition to approved
                    session.transitionTo(SessionStatus.APPROVED);

                    // Set transition data for the caller to use
                    TransitionData transitionData = new TransitionData();
                    transitionData.setWorkspaceId(session.getWorkspaceId());
                    transitionData.setNextSteps(List.of(
                            "Create project using ProjectService",
                            "Create first sprint",
                            "Import backlog items",
                            "Invite team members"
                    ));
                    session.setTransitionData(transitionData);

                    return sessionRepository.save(session);
                });
    }

    /**
     * Abandon the session.
     */
    public Promise<Boolean> abandonSession(String tenantId, UUID sessionId) {
        return sessionRepository.findById(tenantId, sessionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.of(false);
                    }
                    BootstrappingSession session = opt.get();

                    if (session.isTerminal()) {
                        return Promise.of(false);
                    }

                    session.transitionTo(SessionStatus.ABANDONED);
                    return sessionRepository.save(session)
                            .map(s -> true);
                });
    }

    // ========== Helper Methods ==========

    private Promise<String> generateInitialResponse(String prompt) {
        CompletionRequest request = CompletionRequest.builder()
                .prompt("You are a project bootstrapping assistant. A user has described their project idea. " +
                        "Ask clarifying questions to understand the project scope, target users, tech stack, " +
                        "and key features.\n\nUser's idea: " + prompt)
                .maxTokens(512)
                .temperature(0.7)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            completionPromise =
                    Promise.ofException(new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for initial response, using fallback", e);
                            return Promise.of("I'd be happy to help you with your project idea! " +
                                    "Let me ask a few questions to better understand your requirements. " +
                                    "What is the primary goal of your project?");
                        });
    }

    private Promise<String> generateConversationResponse(BootstrappingSession session) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(
                "You are a project bootstrapping assistant. Help the user define their project by " +
                "asking about scope, users, tech stack, features, and constraints. " +
                "If you have enough information (usually after 8-10 turns), suggest transitioning to planning."));
        for (var turn : session.getConversationHistory()) {
            ChatMessage.Role role = "user".equals(turn.getRole()) ? ChatMessage.Role.USER : ChatMessage.Role.ASSISTANT;
            messages.add(ChatMessage.of(role, turn.getContent()));
        }

        CompletionRequest request = CompletionRequest.builder()
                .messages(messages)
                .maxTokens(512)
                .temperature(0.7)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            completionPromise =
                    Promise.ofException(new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for conversation response, using fallback", e);
                            int turnCount = session.getConversationHistory().size();
                            if (turnCount < 6) {
                                return Promise.of("Great, that helps! Can you tell me more about the target users for this project?");
                            } else if (turnCount < 10) {
                                return Promise.of("Excellent! I think I have a good understanding now. Would you like me to create a project definition?");
                            } else {
                                return Promise.of("I've gathered enough information. Let me create a project definition for you.");
                            }
                        });
    }

    private boolean shouldTransitionToPlanning(BootstrappingSession session) {
        // Transition after enough conversation turns
        return session.getConversationHistory().size() >= 10;
    }

    private Promise<String> generateRefinementResponse(BootstrappingSession session, String refinementPrompt) {
        String context = session.getProjectDefinition() != null
                ? "Current project: " + session.getProjectDefinition().getName()
                : "No project definition yet";
        CompletionRequest request = CompletionRequest.builder()
                .prompt("You are a project bootstrapping assistant. The user wants to refine their project.\n\n" +
                        context + "\n\nUser's refinement request: " + refinementPrompt +
                        "\n\nSummarize what you changed and ask if anything else needs adjustment.")
                .maxTokens(512)
                .temperature(0.7)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            completionPromise =
                    Promise.ofException(new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for refinement response, using fallback", e);
                            return Promise.of("I've updated the project definition based on your feedback. " +
                                    "Please review the changes and let me know if anything else needs adjustment.");
                        });
    }

    private Promise<Void> refineProjectDefinition(BootstrappingSession session, String refinementPrompt) {
        ProjectDefinition definition = session.getProjectDefinition();
        if (definition == null) {
            definition = new ProjectDefinition();
            session.setProjectDefinition(definition);
        }
        final ProjectDefinition targetDefinition = definition;
        String currentDef = targetDefinition.getName() != null
                ? "Name: " + targetDefinition.getName() + ", Description: " + targetDefinition.getDescription()
                : "Empty project definition";
        CompletionRequest request = CompletionRequest.builder()
                .prompt("You are a project definition assistant. Update the project definition based on feedback.\n\n" +
                        "Current definition: " + currentDef + "\n\nRefinement: " + refinementPrompt +
                        "\n\nRespond with just the updated project name and description, separated by '|||'.")
                .maxTokens(256)
                .temperature(0.3)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            completionPromise =
                    Promise.ofException(new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .map(resultText -> {
                    String[] parts = resultText.split("\\|\\|\\|", 2);
                    if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
                        targetDefinition.setName(parts[0].trim());
                    }
                    if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                        targetDefinition.setDescription(parts[1].trim());
                    }
                    return (Void) null;
                })
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for project refinement, skipping AI update", e);
                            return Promise.complete();
                        });
    }

    private ProjectGraph buildProjectGraph(ProjectDefinition definition, BootstrappingSession session) {
        ProjectGraph graph = new ProjectGraph();

        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // Create root node for project
        GraphNode rootNode = new GraphNode();
        rootNode.setId("root");
        rootNode.setType("project");
        rootNode.setLabel(definition.getName() != null ? definition.getName() : "Project");
        rootNode.setPhase("mvp");
        Position rootPos = new Position();
        rootPos.setX(400);
        rootPos.setY(50);
        rootNode.setPosition(rootPos);
        nodes.add(rootNode);

        // Create nodes for features
        List<Feature> features = definition.getCoreFeatures();
        if (features != null) {
            double startX = 100;
            double yOffset = 150;
            double xSpacing = 200;

            for (int i = 0; i < features.size(); i++) {
                Feature feature = features.get(i);

                GraphNode featureNode = new GraphNode();
                featureNode.setId(feature.getId() != null ? feature.getId() : "feature-" + i);
                featureNode.setType("feature");
                featureNode.setLabel(feature.getName());
                featureNode.setPhase(feature.getPhase() != null ? feature.getPhase() : "mvp");
                featureNode.setConfidence(feature.getConfidence());

                Position pos = new Position();
                pos.setX(startX + (i * xSpacing));
                pos.setY(yOffset);
                featureNode.setPosition(pos);

                nodes.add(featureNode);

                // Create edge from root to feature
                GraphEdge edge = new GraphEdge();
                edge.setId("edge-root-" + featureNode.getId());
                edge.setSource("root");
                edge.setTarget(featureNode.getId());
                edge.setType("contains");
                edges.add(edge);

                // Create sub-feature nodes
                if (feature.getSubFeatures() != null) {
                    for (int j = 0; j < feature.getSubFeatures().size(); j++) {
                        Feature subFeature = feature.getSubFeatures().get(j);

                        GraphNode subNode = new GraphNode();
                        subNode.setId(subFeature.getId() != null ? subFeature.getId() : "sub-" + i + "-" + j);
                        subNode.setType("sub-feature");
                        subNode.setLabel(subFeature.getName());
                        subNode.setPhase(subFeature.getPhase() != null ? subFeature.getPhase() : feature.getPhase());

                        Position subPos = new Position();
                        subPos.setX(pos.getX() + (j * 80) - 40);
                        subPos.setY(yOffset + 100);
                        subNode.setPosition(subPos);

                        nodes.add(subNode);

                        GraphEdge subEdge = new GraphEdge();
                        subEdge.setId("edge-" + featureNode.getId() + "-" + subNode.getId());
                        subEdge.setSource(featureNode.getId());
                        subEdge.setTarget(subNode.getId());
                        subEdge.setType("contains");
                        edges.add(subEdge);
                    }
                }
            }
        }

        graph.setNodes(nodes);
        graph.setEdges(edges);

        // Set metadata
        GraphMetadata metadata = new GraphMetadata();
        metadata.setTotalNodes(nodes.size());
        metadata.setTotalEdges(edges.size());
        metadata.setLastModified(Instant.now());
        graph.setMetadata(metadata);

        return graph;
    }

    private ValidationReport performValidation(BootstrappingSession session) {
        ValidationReport report = new ValidationReport();
        List<ValidationCheck> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        ProjectDefinition definition = session.getProjectDefinition();

        // Check project name
        ValidationCheck nameCheck = new ValidationCheck();
        nameCheck.setId("check-name");
        nameCheck.setName("Project Name");
        nameCheck.setCategory("business");
        if (definition.getName() == null || definition.getName().isEmpty()) {
            nameCheck.setStatus("failed");
            nameCheck.setMessage("Project name is required");
            blockers.add("Project name is required");
        } else {
            nameCheck.setStatus("passed");
            nameCheck.setMessage("Project name is valid");
        }
        checks.add(nameCheck);

        // Check features
        ValidationCheck featuresCheck = new ValidationCheck();
        featuresCheck.setId("check-features");
        featuresCheck.setName("Core Features");
        featuresCheck.setCategory("business");
        List<Feature> features = definition.getCoreFeatures();
        if (features == null || features.isEmpty()) {
            featuresCheck.setStatus("warning");
            featuresCheck.setMessage("No core features defined");
            warnings.add("Consider adding core features");
        } else {
            featuresCheck.setStatus("passed");
            featuresCheck.setMessage(features.size() + " features defined");
        }
        checks.add(featuresCheck);

        // Check tech stack
        ValidationCheck techCheck = new ValidationCheck();
        techCheck.setId("check-tech");
        techCheck.setName("Tech Stack");
        techCheck.setCategory("technical");
        if (definition.getTechStack() == null) {
            techCheck.setStatus("warning");
            techCheck.setMessage("Tech stack not specified");
            warnings.add("Consider specifying tech stack");
        } else {
            techCheck.setStatus("passed");
            techCheck.setMessage("Tech stack is defined");
        }
        checks.add(techCheck);

        report.setChecks(checks);
        report.setBlockers(blockers);
        report.setWarnings(warnings);
        report.setOverallScore(blockers.isEmpty() ? 0.85 : 0.4);

        return report;
    }

    // ========== Input/Output DTOs ==========

    public record StartSessionInput(
            String workspaceId,
            String initialPrompt,
            String userId
    ) {}

    public record ApproveProjectInput(
            String projectName,
            String projectDescription,
            Integer sprintDuration,
            Integer teamSize
    ) {}
}
