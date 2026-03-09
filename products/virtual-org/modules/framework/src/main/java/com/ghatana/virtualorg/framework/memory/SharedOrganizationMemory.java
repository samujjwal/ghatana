package com.ghatana.virtualorg.framework.memory;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared memory accessible by all agents in an organization.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides cross-agent knowledge sharing capabilities including:
 * <ul>
 * <li>Shared knowledge base by topic</li>
 * <li>Organization-wide decision log</li>
 * <li>Active context for ongoing work</li>
 * <li>Project status and assignments</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * SharedOrganizationMemory sharedMemory = new InMemorySharedOrganizationMemory();
 *
 * // Share knowledge
 * sharedMemory.shareKnowledge("deployment", "Production uses blue-green", "devops-001");
 *
 * // Record org decision
 * sharedMemory.recordOrgDecision(OrgDecision.builder()
 *     .topic("architecture")
 *     .decision("Adopt microservices for new features")
 *     .decidedBy("cto-001")
 *     .build());
 *
 * // Set active context
 * sharedMemory.setActiveContext("current_sprint", "Sprint 42");
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Cross-agent knowledge sharing
 * @doc.layer product
 * @doc.pattern Shared State
 */
public interface SharedOrganizationMemory {

    /**
     * Shares knowledge on a topic.
     *
     * @param topic Topic/category
     * @param content Knowledge content
     * @param contributor Agent ID who contributed
     * @return Promise completing when stored
     */
    Promise<Void> shareKnowledge(String topic, String content, String contributor);

    /**
     * Retrieves knowledge on a topic.
     *
     * @param topic Topic to query
     * @param limit Maximum entries to return
     * @return Promise resolving to list of knowledge entries
     */
    Promise<List<Knowledge>> getKnowledge(String topic, int limit);

    /**
     * Searches knowledge across all topics.
     *
     * @param query Search query
     * @param limit Maximum entries to return
     * @return Promise resolving to matching knowledge
     */
    Promise<List<Knowledge>> searchKnowledge(String query, int limit);

    /**
     * Records an organization-wide decision.
     *
     * @param decision The decision to record
     * @return Promise completing when recorded
     */
    Promise<Void> recordOrgDecision(OrgDecision decision);

    /**
     * Gets recent organization decisions.
     *
     * @param limit Maximum decisions to return
     * @return Promise resolving to recent decisions
     */
    Promise<List<OrgDecision>> getRecentDecisions(int limit);

    /**
     * Gets decisions by topic.
     *
     * @param topic Topic to filter by
     * @param limit Maximum decisions to return
     * @return Promise resolving to matching decisions
     */
    Promise<List<OrgDecision>> getDecisionsByTopic(String topic, int limit);

    /**
     * Sets an active context value.
     *
     * @param key Context key
     * @param value Context value
     * @return Promise completing when set
     */
    Promise<Void> setActiveContext(String key, Object value);

    /**
     * Gets an active context value.
     *
     * @param key Context key
     * @return Promise resolving to optional value
     */
    Promise<Optional<Object>> getActiveContext(String key);

    /**
     * Gets all active context.
     *
     * @return Promise resolving to context map
     */
    Promise<Map<String, Object>> getAllActiveContext();

    /**
     * Clears an active context key.
     *
     * @param key Context key to clear
     * @return Promise completing when cleared
     */
    Promise<Void> clearActiveContext(String key);

    /**
     * Records a project status update.
     *
     * @param projectId Project identifier
     * @param status Status update
     * @return Promise completing when recorded
     */
    Promise<Void> updateProjectStatus(String projectId, ProjectStatus status);

    /**
     * Gets current project status.
     *
     * @param projectId Project identifier
     * @return Promise resolving to optional status
     */
    Promise<Optional<ProjectStatus>> getProjectStatus(String projectId);

    /**
     * Lists all active projects.
     *
     * @return Promise resolving to project statuses
     */
    Promise<List<ProjectStatus>> listActiveProjects();

    /**
     * Shared knowledge entry.
     */
    record Knowledge(
            String id,
            String topic,
            String content,
            String contributor,
            Instant timestamp,
            Map<String, String> metadata
    ) {
        

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String topic;
        private String content;
        private String contributor;
        private Instant timestamp = Instant.now();
        private Map<String, String> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder contributor(String contributor) {
            this.contributor = contributor;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Knowledge build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new Knowledge(id, topic, content, contributor, timestamp, metadata);
        }
    }
}

/**
 * Organization decision record.
 */
record OrgDecision(
        String id,
        String topic,
        String decision,
        String rationale,
        String decidedBy,
        List<String> participants,
        Instant timestamp,
        Map<String, String> metadata
        ) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String id;
        private String topic;
        private String decision;
        private String rationale;
        private String decidedBy;
        private List<String> participants = new ArrayList<>();
        private Instant timestamp = Instant.now();
        private Map<String, String> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public Builder decidedBy(String decidedBy) {
            this.decidedBy = decidedBy;
            return this;
        }

        public Builder participants(List<String> participants) {
            this.participants = participants;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public OrgDecision build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new OrgDecision(id, topic, decision, rationale, decidedBy, participants, timestamp, metadata);
        }
    }
}

/**
 * Project status record.
 */
record ProjectStatus(
        String projectId,
        String name,
        String status,
        String phase,
        String owner,
        List<String> assignees,
        Instant lastUpdated,
        Map<String, Object> metrics
        ) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String projectId;
        private String name;
        private String status = "active";
        private String phase;
        private String owner;
        private List<String> assignees = new ArrayList<>();
        private Instant lastUpdated = Instant.now();
        private Map<String, Object> metrics = new HashMap<>();

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder phase(String phase) {
            this.phase = phase;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder assignees(List<String> assignees) {
            this.assignees = assignees;
            return this;
        }

        public Builder lastUpdated(Instant lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }

        public ProjectStatus build() {
            return new ProjectStatus(projectId, name, status, phase, owner, assignees, lastUpdated, metrics);
        }
    }
}
}
