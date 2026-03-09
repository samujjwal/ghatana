/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a bootstrapping session.
 *
 * <p><b>Purpose</b><br>
 * Tracks the state of a bootstrapping session from initial idea to approved project definition.
 * This is the primary entity for the "Start New Project" user journey.
 *
 * <p><b>Lifecycle States</b><br>
 *
 * <pre>
 * CREATED → CONVERSING → PLANNING → VALIDATING → APPROVED
 *                     ↓             ↓
 *                   ABANDONED   REJECTED
 * </pre>
 *
 * <p><b>Session Duration</b><br>
 * - Target: 15-20 minutes
 * - Complex projects: up to 60 minutes
 * - Sessions auto-archive after 7 days of inactivity
 *
 * @doc.type class
 * @doc.purpose Bootstrapping session domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class BootstrappingSession {

    private UUID id;
    private String tenantId;
    private String userId;
    private String workspaceId;
    private SessionStatus status;
    private String initialIdea;
    private UserProfile userProfile;
    private ProjectHints projectHints;
    private OrganizationContext organizationContext;
    private CollaborationSettings collaborationSettings;
    private ProjectDefinition projectDefinition;
    private ProjectGraph projectGraph;
    private ValidationReport validationReport;
    private List<ConversationTurn> conversationHistory;
    private TransitionData transitionData;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant approvedAt;
    private Instant lastActivityAt;
    private Map<String, Object> metadata;

    public BootstrappingSession() {
        this.id = UUID.randomUUID();
        this.status = SessionStatus.CREATED;
        this.conversationHistory = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    // ========== Enums ==========

    /**
     * Session lifecycle status.
     */
    public enum SessionStatus {
        CREATED,        // Initial state
        CONVERSING,     // AI conversation in progress
        PLANNING,       // Building project definition
        VALIDATING,     // Validation checks running
        APPROVED,       // User approved, ready for initialization
        ABANDONED,      // User left without completion
        REJECTED        // Validation failed critically
    }

    /**
     * User experience level.
     */
    public enum ExperienceLevel {
        BEGINNER,
        INTERMEDIATE,
        EXPERT
    }

    /**
     * User role in the organization.
     */
    public enum UserRole {
        DEVELOPER,
        DESIGNER,
        PM,
        BUSINESS,
        ANALYST,
        OTHER
    }

    /**
     * Target platform for the project.
     */
    public enum TargetPlatform {
        WEB,
        MOBILE,
        DESKTOP,
        API,
        DATA,
        ML
    }

    /**
     * Expected project scale.
     */
    public enum ProjectScale {
        PERSONAL,
        STARTUP,
        ENTERPRISE
    }

    /**
     * Project timeline type.
     */
    public enum TimelineType {
        HACKATHON,
        MVP,
        PRODUCTION
    }

    // ========== Nested Classes ==========

    /**
     * User profile context for AI personalization.
     */
    public static class UserProfile {
        private ExperienceLevel experience;
        private UserRole role;
        private Integer teamSize;
        private String industry;

        public UserProfile() {
            this.experience = ExperienceLevel.INTERMEDIATE;
            this.role = UserRole.DEVELOPER;
        }

        public ExperienceLevel getExperience() { return experience; }
        public void setExperience(ExperienceLevel experience) { this.experience = experience; }

        public UserRole getRole() { return role; }
        public void setRole(UserRole role) { this.role = role; }

        public Integer getTeamSize() { return teamSize; }
        public void setTeamSize(Integer teamSize) { this.teamSize = teamSize; }

        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
    }

    /**
     * Project hints to guide AI recommendations.
     */
    public static class ProjectHints {
        private TargetPlatform targetPlatform;
        private ProjectScale expectedScale;
        private TimelineType timeline;
        private String budget;
        private String teamType;
        private List<String> existingResources;

        public ProjectHints() {
            this.existingResources = new ArrayList<>();
        }

        public TargetPlatform getTargetPlatform() { return targetPlatform; }
        public void setTargetPlatform(TargetPlatform targetPlatform) { this.targetPlatform = targetPlatform; }

        public ProjectScale getExpectedScale() { return expectedScale; }
        public void setExpectedScale(ProjectScale expectedScale) { this.expectedScale = expectedScale; }

        public TimelineType getTimeline() { return timeline; }
        public void setTimeline(TimelineType timeline) { this.timeline = timeline; }

        public String getBudget() { return budget; }
        public void setBudget(String budget) { this.budget = budget; }

        public String getTeamType() { return teamType; }
        public void setTeamType(String teamType) { this.teamType = teamType; }

        public List<String> getExistingResources() { return existingResources; }
        public void setExistingResources(List<String> existingResources) { this.existingResources = existingResources; }
    }

    /**
     * Enterprise organization context.
     */
    public static class OrganizationContext {
        private String organizationId;
        private String methodology;
        private List<String> complianceRequirements;
        private List<String> techStackConstraints;
        private String securityLevel;
        private ApprovalWorkflow approvalWorkflow;

        public OrganizationContext() {
            this.complianceRequirements = new ArrayList<>();
            this.techStackConstraints = new ArrayList<>();
        }

        public String getOrganizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

        public String getMethodology() { return methodology; }
        public void setMethodology(String methodology) { this.methodology = methodology; }

        public List<String> getComplianceRequirements() { return complianceRequirements; }
        public void setComplianceRequirements(List<String> complianceRequirements) { 
            this.complianceRequirements = complianceRequirements; 
        }

        public List<String> getTechStackConstraints() { return techStackConstraints; }
        public void setTechStackConstraints(List<String> techStackConstraints) { 
            this.techStackConstraints = techStackConstraints; 
        }

        public String getSecurityLevel() { return securityLevel; }
        public void setSecurityLevel(String securityLevel) { this.securityLevel = securityLevel; }

        public ApprovalWorkflow getApprovalWorkflow() { return approvalWorkflow; }
        public void setApprovalWorkflow(ApprovalWorkflow approvalWorkflow) { this.approvalWorkflow = approvalWorkflow; }
    }

    /**
     * Approval workflow configuration.
     */
    public static class ApprovalWorkflow {
        private boolean required;
        private List<String> approvers;
        private List<String> gates;

        public ApprovalWorkflow() {
            this.approvers = new ArrayList<>();
            this.gates = new ArrayList<>();
        }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public List<String> getApprovers() { return approvers; }
        public void setApprovers(List<String> approvers) { this.approvers = approvers; }

        public List<String> getGates() { return gates; }
        public void setGates(List<String> gates) { this.gates = gates; }
    }

    /**
     * Real-time collaboration settings.
     */
    public static class CollaborationSettings {
        private boolean enabled;
        private boolean allowGuestAccess;
        private boolean requireApproval;
        private int maxParticipants;
        private Permissions permissions;

        public CollaborationSettings() {
            this.enabled = false;
            this.maxParticipants = 10;
            this.permissions = new Permissions();
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isAllowGuestAccess() { return allowGuestAccess; }
        public void setAllowGuestAccess(boolean allowGuestAccess) { this.allowGuestAccess = allowGuestAccess; }

        public boolean isRequireApproval() { return requireApproval; }
        public void setRequireApproval(boolean requireApproval) { this.requireApproval = requireApproval; }

        public int getMaxParticipants() { return maxParticipants; }
        public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }

        public Permissions getPermissions() { return permissions; }
        public void setPermissions(Permissions permissions) { this.permissions = permissions; }
    }

    /**
     * Collaboration permissions.
     */
    public static class Permissions {
        private List<String> canEdit;
        private List<String> canComment;
        private List<String> canView;

        public Permissions() {
            this.canEdit = new ArrayList<>();
            this.canComment = new ArrayList<>();
            this.canView = new ArrayList<>();
        }

        public List<String> getCanEdit() { return canEdit; }
        public void setCanEdit(List<String> canEdit) { this.canEdit = canEdit; }

        public List<String> getCanComment() { return canComment; }
        public void setCanComment(List<String> canComment) { this.canComment = canComment; }

        public List<String> getCanView() { return canView; }
        public void setCanView(List<String> canView) { this.canView = canView; }
    }

    /**
     * Project definition output from bootstrapping.
     */
    public static class ProjectDefinition {
        private String name;
        private String description;
        private String vision;
        private List<String> targetUsers;
        private List<Feature> coreFeatures;
        private TechStack techStack;
        private List<ArchitectureDecision> architectureDecisions;
        private Roadmap roadmap;
        private List<String> assumptions;
        private List<String> constraints;
        private List<Risk> risks;
        private List<String> successCriteria;

        public ProjectDefinition() {
            this.targetUsers = new ArrayList<>();
            this.coreFeatures = new ArrayList<>();
            this.architectureDecisions = new ArrayList<>();
            this.assumptions = new ArrayList<>();
            this.constraints = new ArrayList<>();
            this.risks = new ArrayList<>();
            this.successCriteria = new ArrayList<>();
        }

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getVision() { return vision; }
        public void setVision(String vision) { this.vision = vision; }

        public List<String> getTargetUsers() { return targetUsers; }
        public void setTargetUsers(List<String> targetUsers) { this.targetUsers = targetUsers; }

        public List<Feature> getCoreFeatures() { return coreFeatures; }
        public void setCoreFeatures(List<Feature> coreFeatures) { this.coreFeatures = coreFeatures; }

        public TechStack getTechStack() { return techStack; }
        public void setTechStack(TechStack techStack) { this.techStack = techStack; }

        public List<ArchitectureDecision> getArchitectureDecisions() { return architectureDecisions; }
        public void setArchitectureDecisions(List<ArchitectureDecision> architectureDecisions) { 
            this.architectureDecisions = architectureDecisions; 
        }

        public Roadmap getRoadmap() { return roadmap; }
        public void setRoadmap(Roadmap roadmap) { this.roadmap = roadmap; }

        public List<String> getAssumptions() { return assumptions; }
        public void setAssumptions(List<String> assumptions) { this.assumptions = assumptions; }

        public List<String> getConstraints() { return constraints; }
        public void setConstraints(List<String> constraints) { this.constraints = constraints; }

        public List<Risk> getRisks() { return risks; }
        public void setRisks(List<Risk> risks) { this.risks = risks; }

        public List<String> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(List<String> successCriteria) { this.successCriteria = successCriteria; }
    }

    /**
     * Feature specification.
     */
    public static class Feature {
        private String id;
        private String name;
        private String description;
        private String priority;  // must-have, should-have, nice-to-have
        private String phase;     // mvp, v2, v3, future
        private double confidence;
        private List<Feature> subFeatures;
        private List<String> dependencies;
        private String estimatedEffort;
        private List<String> acceptance;
        private String technicalNotes;

        public Feature() {
            this.subFeatures = new ArrayList<>();
            this.dependencies = new ArrayList<>();
            this.acceptance = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }

        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public List<Feature> getSubFeatures() { return subFeatures; }
        public void setSubFeatures(List<Feature> subFeatures) { this.subFeatures = subFeatures; }

        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

        public String getEstimatedEffort() { return estimatedEffort; }
        public void setEstimatedEffort(String estimatedEffort) { this.estimatedEffort = estimatedEffort; }

        public List<String> getAcceptance() { return acceptance; }
        public void setAcceptance(List<String> acceptance) { this.acceptance = acceptance; }

        public String getTechnicalNotes() { return technicalNotes; }
        public void setTechnicalNotes(String technicalNotes) { this.technicalNotes = technicalNotes; }
    }

    /**
     * Tech stack specification.
     */
    public static class TechStack {
        private FrontendStack frontend;
        private BackendStack backend;
        private InfrastructureStack infrastructure;
        private Map<String, String> rationale;

        public TechStack() {
            this.rationale = new HashMap<>();
        }

        public FrontendStack getFrontend() { return frontend; }
        public void setFrontend(FrontendStack frontend) { this.frontend = frontend; }

        public BackendStack getBackend() { return backend; }
        public void setBackend(BackendStack backend) { this.backend = backend; }

        public InfrastructureStack getInfrastructure() { return infrastructure; }
        public void setInfrastructure(InfrastructureStack infrastructure) { this.infrastructure = infrastructure; }

        public Map<String, String> getRationale() { return rationale; }
        public void setRationale(Map<String, String> rationale) { this.rationale = rationale; }
    }

    public static class FrontendStack {
        private String framework;
        private String stateManagement;
        private String styling;
        private String testing;

        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }

        public String getStateManagement() { return stateManagement; }
        public void setStateManagement(String stateManagement) { this.stateManagement = stateManagement; }

        public String getStyling() { return styling; }
        public void setStyling(String styling) { this.styling = styling; }

        public String getTesting() { return testing; }
        public void setTesting(String testing) { this.testing = testing; }
    }

    public static class BackendStack {
        private String language;
        private String framework;
        private String database;
        private String cache;
        private String queue;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getFramework() { return framework; }
        public void setFramework(String framework) { this.framework = framework; }

        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }

        public String getCache() { return cache; }
        public void setCache(String cache) { this.cache = cache; }

        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
    }

    public static class InfrastructureStack {
        private String hosting;
        private String ciCd;
        private String monitoring;

        public String getHosting() { return hosting; }
        public void setHosting(String hosting) { this.hosting = hosting; }

        public String getCiCd() { return ciCd; }
        public void setCiCd(String ciCd) { this.ciCd = ciCd; }

        public String getMonitoring() { return monitoring; }
        public void setMonitoring(String monitoring) { this.monitoring = monitoring; }
    }

    /**
     * Architecture decision record.
     */
    public static class ArchitectureDecision {
        private String id;
        private String title;
        private String context;
        private String decision;
        private List<String> alternatives;
        private List<String> consequences;
        private String status;  // proposed, accepted, deprecated

        public ArchitectureDecision() {
            this.alternatives = new ArrayList<>();
            this.consequences = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }

        public String getDecision() { return decision; }
        public void setDecision(String decision) { this.decision = decision; }

        public List<String> getAlternatives() { return alternatives; }
        public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }

        public List<String> getConsequences() { return consequences; }
        public void setConsequences(List<String> consequences) { this.consequences = consequences; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Project roadmap.
     */
    public static class Roadmap {
        private List<Phase> phases;
        private List<Milestone> milestones;
        private String estimatedDuration;

        public Roadmap() {
            this.phases = new ArrayList<>();
            this.milestones = new ArrayList<>();
        }

        public List<Phase> getPhases() { return phases; }
        public void setPhases(List<Phase> phases) { this.phases = phases; }

        public List<Milestone> getMilestones() { return milestones; }
        public void setMilestones(List<Milestone> milestones) { this.milestones = milestones; }

        public String getEstimatedDuration() { return estimatedDuration; }
        public void setEstimatedDuration(String estimatedDuration) { this.estimatedDuration = estimatedDuration; }
    }

    public static class Phase {
        private String name;
        private List<String> features;
        private String duration;
        private List<String> goals;

        public Phase() {
            this.features = new ArrayList<>();
            this.goals = new ArrayList<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getFeatures() { return features; }
        public void setFeatures(List<String> features) { this.features = features; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public List<String> getGoals() { return goals; }
        public void setGoals(List<String> goals) { this.goals = goals; }
    }

    public static class Milestone {
        private String name;
        private String date;
        private List<String> deliverables;

        public Milestone() {
            this.deliverables = new ArrayList<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public List<String> getDeliverables() { return deliverables; }
        public void setDeliverables(List<String> deliverables) { this.deliverables = deliverables; }
    }

    /**
     * Identified risk.
     */
    public static class Risk {
        private String id;
        private String description;
        private String probability;  // low, medium, high
        private String impact;       // low, medium, high
        private String mitigation;
        private String owner;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getProbability() { return probability; }
        public void setProbability(String probability) { this.probability = probability; }

        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }

        public String getMitigation() { return mitigation; }
        public void setMitigation(String mitigation) { this.mitigation = mitigation; }

        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
    }

    /**
     * Project graph for canvas visualization.
     */
    public static class ProjectGraph {
        private List<GraphNode> nodes;
        private List<GraphEdge> edges;
        private GraphMetadata metadata;
        private List<GraphVersion> versions;

        public ProjectGraph() {
            this.nodes = new ArrayList<>();
            this.edges = new ArrayList<>();
            this.versions = new ArrayList<>();
        }

        public List<GraphNode> getNodes() { return nodes; }
        public void setNodes(List<GraphNode> nodes) { this.nodes = nodes; }

        public List<GraphEdge> getEdges() { return edges; }
        public void setEdges(List<GraphEdge> edges) { this.edges = edges; }

        public GraphMetadata getMetadata() { return metadata; }
        public void setMetadata(GraphMetadata metadata) { this.metadata = metadata; }

        public List<GraphVersion> getVersions() { return versions; }
        public void setVersions(List<GraphVersion> versions) { this.versions = versions; }
    }

    public static class GraphNode {
        private String id;
        private String type;     // feature, component, module, service
        private String label;
        private String phase;    // mvp, v2, v3, future
        private double confidence;
        private Position position;
        private Map<String, Object> data;

        public GraphNode() {
            this.data = new HashMap<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public Position getPosition() { return position; }
        public void setPosition(Position position) { this.position = position; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    public static class Position {
        private double x;
        private double y;

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }

        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
    }

    public static class GraphEdge {
        private String id;
        private String source;
        private String target;
        private String type;     // depends-on, implements, uses, extends
        private String label;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    public static class GraphMetadata {
        private int totalNodes;
        private int totalEdges;
        private Instant lastModified;

        public int getTotalNodes() { return totalNodes; }
        public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

        public int getTotalEdges() { return totalEdges; }
        public void setTotalEdges(int totalEdges) { this.totalEdges = totalEdges; }

        public Instant getLastModified() { return lastModified; }
        public void setLastModified(Instant lastModified) { this.lastModified = lastModified; }
    }

    public static class GraphVersion {
        private int version;
        private Instant timestamp;
        private String changedBy;    // user or agent
        private String changeType;   // node-added, node-edited, etc.

        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public String getChangedBy() { return changedBy; }
        public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

        public String getChangeType() { return changeType; }
        public void setChangeType(String changeType) { this.changeType = changeType; }
    }

    /**
     * Validation report.
     */
    public static class ValidationReport {
        private double overallScore;
        private List<ValidationCheck> checks;
        private List<Recommendation> recommendations;
        private List<String> blockers;
        private List<String> warnings;
        private Instant timestamp;

        public ValidationReport() {
            this.checks = new ArrayList<>();
            this.recommendations = new ArrayList<>();
            this.blockers = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.timestamp = Instant.now();
        }

        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }

        public List<ValidationCheck> getChecks() { return checks; }
        public void setChecks(List<ValidationCheck> checks) { this.checks = checks; }

        public List<Recommendation> getRecommendations() { return recommendations; }
        public void setRecommendations(List<Recommendation> recommendations) { 
            this.recommendations = recommendations; 
        }

        public List<String> getBlockers() { return blockers; }
        public void setBlockers(List<String> blockers) { this.blockers = blockers; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    }

    public static class ValidationCheck {
        private String id;
        private String name;
        private String category;   // technical, business, compliance, security
        private String status;     // passed, failed, warning
        private String message;
        private boolean autoFixable;
        private String autoFixAction;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public boolean isAutoFixable() { return autoFixable; }
        public void setAutoFixable(boolean autoFixable) { this.autoFixable = autoFixable; }

        public String getAutoFixAction() { return autoFixAction; }
        public void setAutoFixAction(String autoFixAction) { this.autoFixAction = autoFixAction; }
    }

    public static class Recommendation {
        private String id;
        private String type;       // suggestion, best-practice, optimization
        private String title;
        private String description;
        private String impact;     // high, medium, low
        private String effort;     // easy, moderate, hard

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getImpact() { return impact; }
        public void setImpact(String impact) { this.impact = impact; }

        public String getEffort() { return effort; }
        public void setEffort(String effort) { this.effort = effort; }
    }

    /**
     * Conversation turn for AI dialogue.
     */
    public static class ConversationTurn {
        private String id;
        private String role;       // user or assistant
        private String content;
        private Instant timestamp;
        private Map<String, Object> metadata;

        public ConversationTurn() {
            this.id = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.metadata = new HashMap<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    /**
     * Transition data for next phase.
     */
    public static class TransitionData {
        private String workspaceId;
        private String projectId;
        private String repositoryUrl;
        private List<BacklogItem> initialBacklog;
        private List<String> nextSteps;

        public TransitionData() {
            this.initialBacklog = new ArrayList<>();
            this.nextSteps = new ArrayList<>();
        }

        public String getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }

        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String repositoryUrl) { this.repositoryUrl = repositoryUrl; }

        public List<BacklogItem> getInitialBacklog() { return initialBacklog; }
        public void setInitialBacklog(List<BacklogItem> initialBacklog) { this.initialBacklog = initialBacklog; }

        public List<String> getNextSteps() { return nextSteps; }
        public void setNextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; }
    }

    public static class BacklogItem {
        private String id;
        private String title;
        private String description;
        private String type;       // story, task, bug, epic
        private int priority;
        private Integer estimatedPoints;
        private List<String> acceptance;
        private List<String> dependencies;

        public BacklogItem() {
            this.acceptance = new ArrayList<>();
            this.dependencies = new ArrayList<>();
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public Integer getEstimatedPoints() { return estimatedPoints; }
        public void setEstimatedPoints(Integer estimatedPoints) { this.estimatedPoints = estimatedPoints; }

        public List<String> getAcceptance() { return acceptance; }
        public void setAcceptance(List<String> acceptance) { this.acceptance = acceptance; }

        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    }

    // ========== Domain Methods ==========

    /**
     * Add a conversation turn.
     *
     * @param role    the role (user or assistant)
     * @param content the message content
     */
    public void addConversationTurn(String role, String content) {
        ConversationTurn turn = new ConversationTurn();
        turn.setRole(role);
        turn.setContent(content);
        this.conversationHistory.add(turn);
        this.lastActivityAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Transition to a new status.
     *
     * @param newStatus the target status
     * @throws IllegalStateException if transition is not allowed
     */
    public void transitionTo(SessionStatus newStatus) {
        validateTransition(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
        if (newStatus == SessionStatus.APPROVED) {
            this.approvedAt = Instant.now();
        }
    }

    private void validateTransition(SessionStatus newStatus) {
        switch (this.status) {
            case CREATED:
                if (newStatus != SessionStatus.CONVERSING && newStatus != SessionStatus.ABANDONED) {
                    throw new IllegalStateException("Cannot transition from CREATED to " + newStatus);
                }
                break;
            case CONVERSING:
                if (newStatus != SessionStatus.PLANNING && newStatus != SessionStatus.ABANDONED) {
                    throw new IllegalStateException("Cannot transition from CONVERSING to " + newStatus);
                }
                break;
            case PLANNING:
                if (newStatus != SessionStatus.VALIDATING && newStatus != SessionStatus.ABANDONED) {
                    throw new IllegalStateException("Cannot transition from PLANNING to " + newStatus);
                }
                break;
            case VALIDATING:
                if (newStatus != SessionStatus.APPROVED && newStatus != SessionStatus.REJECTED 
                        && newStatus != SessionStatus.PLANNING) {
                    throw new IllegalStateException("Cannot transition from VALIDATING to " + newStatus);
                }
                break;
            case APPROVED:
            case ABANDONED:
            case REJECTED:
                throw new IllegalStateException("Cannot transition from terminal state " + this.status);
            default:
                throw new IllegalStateException("Unknown status: " + this.status);
        }
    }

    /**
     * Check if session is in a terminal state.
     *
     * @return true if approved, abandoned, or rejected
     */
    public boolean isTerminal() {
        return status == SessionStatus.APPROVED 
            || status == SessionStatus.ABANDONED 
            || status == SessionStatus.REJECTED;
    }

    /**
     * Check if session has exceeded inactivity timeout.
     *
     * @param timeoutDays number of days before timeout
     * @return true if inactive longer than timeout
     */
    public boolean isInactive(int timeoutDays) {
        return lastActivityAt.isBefore(Instant.now().minusSeconds(timeoutDays * 24 * 60 * 60L));
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }

    public String getInitialIdea() { return initialIdea; }
    public void setInitialIdea(String initialIdea) { this.initialIdea = initialIdea; }

    public UserProfile getUserProfile() { return userProfile; }
    public void setUserProfile(UserProfile userProfile) { this.userProfile = userProfile; }

    public ProjectHints getProjectHints() { return projectHints; }
    public void setProjectHints(ProjectHints projectHints) { this.projectHints = projectHints; }

    public OrganizationContext getOrganizationContext() { return organizationContext; }
    public void setOrganizationContext(OrganizationContext organizationContext) { 
        this.organizationContext = organizationContext; 
    }

    public CollaborationSettings getCollaborationSettings() { return collaborationSettings; }
    public void setCollaborationSettings(CollaborationSettings collaborationSettings) { 
        this.collaborationSettings = collaborationSettings; 
    }

    public ProjectDefinition getProjectDefinition() { return projectDefinition; }
    public void setProjectDefinition(ProjectDefinition projectDefinition) { 
        this.projectDefinition = projectDefinition; 
    }

    public ProjectGraph getProjectGraph() { return projectGraph; }
    public void setProjectGraph(ProjectGraph projectGraph) { this.projectGraph = projectGraph; }

    public ValidationReport getValidationReport() { return validationReport; }
    public void setValidationReport(ValidationReport validationReport) { 
        this.validationReport = validationReport; 
    }

    public List<ConversationTurn> getConversationHistory() { return conversationHistory; }
    public void setConversationHistory(List<ConversationTurn> conversationHistory) { 
        this.conversationHistory = conversationHistory; 
    }

    public TransitionData getTransitionData() { return transitionData; }
    public void setTransitionData(TransitionData transitionData) { this.transitionData = transitionData; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BootstrappingSession)) return false;
        BootstrappingSession that = (BootstrappingSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BootstrappingSession{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", userId='" + userId + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
