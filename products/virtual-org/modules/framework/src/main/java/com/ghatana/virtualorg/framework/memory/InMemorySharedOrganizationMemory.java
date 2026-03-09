package com.ghatana.virtualorg.framework.memory;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SharedOrganizationMemory for development/testing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a simple in-memory implementation of the shared organization memory
 * interface. Suitable for development, testing, and single-instance
 * deployments.
 *
 * <p>
 * <b>Limitations</b><br>
 * <ul>
 * <li>Data is not persisted across restarts</li>
 * <li>No distributed support (single instance only)</li>
 * <li>Basic text matching for search (no semantic search)</li>
 * </ul>
 *
 * <p>
 * <b>Production Alternative</b><br>
 * For production, use PostgresSharedOrganizationMemory with pgvector for
 * semantic search capabilities.
 *
 * @doc.type class
 * @doc.purpose In-memory shared memory implementation
 * @doc.layer product
 * @doc.pattern Repository
 */
public class InMemorySharedOrganizationMemory implements SharedOrganizationMemory {

    private final Map<String, List<Knowledge>> knowledgeByTopic;
    private final List<OrgDecision> decisions;
    private final Map<String, Object> activeContext;
    private final Map<String, ProjectStatus> projects;

    public InMemorySharedOrganizationMemory() {
        this.knowledgeByTopic = new ConcurrentHashMap<>();
        this.decisions = Collections.synchronizedList(new ArrayList<>());
        this.activeContext = new ConcurrentHashMap<>();
        this.projects = new ConcurrentHashMap<>();
    }

    @Override
    public Promise<Void> shareKnowledge(String topic, String content, String contributor) {
        Knowledge knowledge = Knowledge.builder()
                .topic(topic)
                .content(content)
                .contributor(contributor)
                .build();

        knowledgeByTopic
                .computeIfAbsent(topic.toLowerCase(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(knowledge);

        return Promise.complete();
    }

    @Override
    public Promise<List<Knowledge>> getKnowledge(String topic, int limit) {
        List<Knowledge> topicKnowledge = knowledgeByTopic.getOrDefault(
                topic.toLowerCase(),
                Collections.emptyList()
        );

        List<Knowledge> result = topicKnowledge.stream()
                .sorted(Comparator.comparing(Knowledge::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return Promise.of(result);
    }

    @Override
    public Promise<List<Knowledge>> searchKnowledge(String query, int limit) {
        String lowerQuery = query.toLowerCase();

        List<Knowledge> result = knowledgeByTopic.values().stream()
                .flatMap(List::stream)
                .filter(k -> k.content().toLowerCase().contains(lowerQuery)
                || k.topic().toLowerCase().contains(lowerQuery))
                .sorted(Comparator.comparing(Knowledge::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return Promise.of(result);
    }

    @Override
    public Promise<Void> recordOrgDecision(OrgDecision decision) {
        decisions.add(decision);
        return Promise.complete();
    }

    @Override
    public Promise<List<OrgDecision>> getRecentDecisions(int limit) {
        List<OrgDecision> result = decisions.stream()
                .sorted(Comparator.comparing(OrgDecision::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return Promise.of(result);
    }

    @Override
    public Promise<List<OrgDecision>> getDecisionsByTopic(String topic, int limit) {
        String lowerTopic = topic.toLowerCase();

        List<OrgDecision> result = decisions.stream()
                .filter(d -> d.topic() != null && d.topic().toLowerCase().contains(lowerTopic))
                .sorted(Comparator.comparing(OrgDecision::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        return Promise.of(result);
    }

    @Override
    public Promise<Void> setActiveContext(String key, Object value) {
        activeContext.put(key, value);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<Object>> getActiveContext(String key) {
        return Promise.of(Optional.ofNullable(activeContext.get(key)));
    }

    @Override
    public Promise<Map<String, Object>> getAllActiveContext() {
        return Promise.of(new HashMap<>(activeContext));
    }

    @Override
    public Promise<Void> clearActiveContext(String key) {
        activeContext.remove(key);
        return Promise.complete();
    }

    @Override
    public Promise<Void> updateProjectStatus(String projectId, ProjectStatus status) {
        projects.put(projectId, status);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<ProjectStatus>> getProjectStatus(String projectId) {
        return Promise.of(Optional.ofNullable(projects.get(projectId)));
    }

    @Override
    public Promise<List<ProjectStatus>> listActiveProjects() {
        List<ProjectStatus> activeProjects = projects.values().stream()
                .filter(p -> "active".equalsIgnoreCase(p.status())
                || "in-progress".equalsIgnoreCase(p.status()))
                .sorted(Comparator.comparing(ProjectStatus::lastUpdated).reversed())
                .collect(Collectors.toList());

        return Promise.of(activeProjects);
    }

    /**
     * Gets the total count of knowledge entries.
     *
     * @return Total knowledge count
     */
    public int getKnowledgeCount() {
        return knowledgeByTopic.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Gets the total count of decisions.
     *
     * @return Total decision count
     */
    public int getDecisionCount() {
        return decisions.size();
    }

    /**
     * Gets the count of active projects.
     *
     * @return Active project count
     */
    public int getActiveProjectCount() {
        return (int) projects.values().stream()
                .filter(p -> "active".equalsIgnoreCase(p.status())
                || "in-progress".equalsIgnoreCase(p.status()))
                .count();
    }

    /**
     * Clears all data (for testing).
     */
    public void clear() {
        knowledgeByTopic.clear();
        decisions.clear();
        activeContext.clear();
        projects.clear();
    }
}
