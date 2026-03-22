package com.ghatana.tutorputor.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Curriculum management service for organizing and tracking educational content.
 * 
 * <p>Features:
 * <ul>
 *   <li>Learning path creation and management</li>
 *   <li>Prerequisite tracking and validation</li>
 *   <li>Standard alignment (Common Core, NGSS)</li>
 *   <li>Progress tracking per learner</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Curriculum and learning path management
 * @doc.layer product
 * @doc.pattern Repository
 */
public class CurriculumService {

    private static final Logger LOG = LoggerFactory.getLogger(CurriculumService.class);

    private final ConcurrentMap<String, LearningPath> learningPaths;
    private final ConcurrentMap<String, Topic> topics;
    private final ConcurrentMap<String, Map<String, LearnerProgress>> learnerProgress;
    private final MeterRegistry meterRegistry;

    /**
     * Creates a new curriculum service.
     *
     * @param meterRegistry the metrics registry
     */
    public CurriculumService(@NotNull MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.learningPaths = new ConcurrentHashMap<>();
        this.topics = new ConcurrentHashMap<>();
        this.learnerProgress = new ConcurrentHashMap<>();
        
        LOG.info("CurriculumService initialized");
    }

    // =========================================================================
    // Topic Management
    // =========================================================================

    /**
     * Creates a new topic.
     *
     * @param topic the topic to create
     */
    public void createTopic(@NotNull Topic topic) {
        if (topics.containsKey(topic.id())) {
            throw new IllegalArgumentException("Topic already exists: " + topic.id());
        }
        
        // Validate prerequisites exist
        for (String prereqId : topic.prerequisites()) {
            if (!topics.containsKey(prereqId)) {
                throw new IllegalArgumentException("Prerequisite not found: " + prereqId);
            }
        }
        
        topics.put(topic.id(), topic);
        LOG.info("Created topic: {} ({})", topic.id(), topic.name());
    }

    /**
     * Gets a topic by ID.
     *
     * @param topicId the topic ID
     * @return the topic, or null if not found
     */
    public Topic getTopic(@NotNull String topicId) {
        return topics.get(topicId);
    }

    /**
     * Lists all topics.
     *
     * @return list of topics
     */
    public List<Topic> listTopics() {
        return new ArrayList<>(topics.values());
    }

    /**
     * Lists topics by subject area.
     *
     * @param subject the subject area
     * @return list of matching topics
     */
    public List<Topic> listTopicsBySubject(@NotNull String subject) {
        return topics.values().stream()
            .filter(t -> t.subject().equalsIgnoreCase(subject))
            .toList();
    }

    /**
     * Gets prerequisites for a topic.
     *
     * @param topicId the topic ID
     * @return list of prerequisite topics
     */
    public List<Topic> getPrerequisites(@NotNull String topicId) {
        Topic topic = topics.get(topicId);
        if (topic == null) return List.of();
        
        return topic.prerequisites().stream()
            .map(topics::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Gets topics that depend on a given topic.
     *
     * @param topicId the topic ID
     * @return list of dependent topics
     */
    public List<Topic> getDependentTopics(@NotNull String topicId) {
        return topics.values().stream()
            .filter(t -> t.prerequisites().contains(topicId))
            .toList();
    }

    // =========================================================================
    // Learning Path Management
    // =========================================================================

    /**
     * Creates a learning path.
     *
     * @param path the learning path
     */
    public void createLearningPath(@NotNull LearningPath path) {
        if (learningPaths.containsKey(path.id())) {
            throw new IllegalArgumentException("Learning path already exists: " + path.id());
        }
        
        // Validate all topics exist
        for (String topicId : path.topicIds()) {
            if (!topics.containsKey(topicId)) {
                throw new IllegalArgumentException("Topic not found: " + topicId);
            }
        }
        
        // Validate topological order (prerequisites come before dependents)
        validateTopologyOrder(path.topicIds());
        
        learningPaths.put(path.id(), path);
        LOG.info("Created learning path: {} with {} topics", path.id(), path.topicIds().size());
    }

    /**
     * Gets a learning path by ID.
     *
     * @param pathId the path ID
     * @return the learning path, or null if not found
     */
    public LearningPath getLearningPath(@NotNull String pathId) {
        return learningPaths.get(pathId);
    }

    /**
     * Lists all learning paths.
     *
     * @return list of learning paths
     */
    public List<LearningPath> listLearningPaths() {
        return new ArrayList<>(learningPaths.values());
    }

    /**
     * Generates an optimal learning path for a set of target topics.
     *
     * @param targetTopicIds the target topics to learn
     * @param learnerId the learner ID (to consider existing knowledge)
     * @return the generated learning path
     */
    public LearningPath generateLearningPath(
            @NotNull List<String> targetTopicIds,
            @NotNull String learnerId) {
        
        Set<String> allRequired = new LinkedHashSet<>();
        
        // Collect all prerequisites recursively
        for (String targetId : targetTopicIds) {
            collectPrerequisites(targetId, allRequired);
            allRequired.add(targetId);
        }
        
        // Filter out mastered topics
        Map<String, LearnerProgress> progress = learnerProgress.get(learnerId);
        if (progress != null) {
            allRequired.removeIf(topicId -> {
                LearnerProgress lp = progress.get(topicId);
                return lp != null && lp.mastery() >= 0.8;
            });
        }
        
        // Sort in topological order
        List<String> ordered = topologicalSort(allRequired);
        
        String pathId = "generated-" + UUID.randomUUID();
        return new LearningPath(
            pathId,
            "Custom Learning Path",
            "Generated path for topics: " + targetTopicIds,
            ordered,
            estimateDuration(ordered),
            Map.of("generated", "true", "learnerId", learnerId)
        );
    }

    // =========================================================================
    // Progress Tracking
    // =========================================================================

    /**
     * Records learner progress on a topic.
     *
     * @param learnerId the learner ID
     * @param topicId the topic ID
     * @param mastery the mastery level (0-1)
     * @param timeSpentMinutes time spent in minutes
     */
    public void recordProgress(
            @NotNull String learnerId,
            @NotNull String topicId,
            double mastery,
            int timeSpentMinutes) {
        
        learnerProgress.computeIfAbsent(learnerId, k -> new ConcurrentHashMap<>())
            .compute(topicId, (k, existing) -> {
                if (existing == null) {
                    return new LearnerProgress(
                        topicId,
                        mastery,
                        timeSpentMinutes,
                        1,
                        Instant.now()
                    );
                } else {
                    return new LearnerProgress(
                        topicId,
                        Math.max(existing.mastery(), mastery),
                        existing.timeSpentMinutes() + timeSpentMinutes,
                        existing.attempts() + 1,
                        Instant.now()
                    );
                }
            });
        
        LOG.debug("Recorded progress: learner={}, topic={}, mastery={}", 
            learnerId, topicId, mastery);
    }

    /**
     * Gets learner progress for a topic.
     *
     * @param learnerId the learner ID
     * @param topicId the topic ID
     * @return the progress, or null if none recorded
     */
    public LearnerProgress getProgress(@NotNull String learnerId, @NotNull String topicId) {
        Map<String, LearnerProgress> progress = learnerProgress.get(learnerId);
        return progress != null ? progress.get(topicId) : null;
    }

    /**
     * Gets all progress for a learner.
     *
     * @param learnerId the learner ID
     * @return map of topic ID to progress
     */
    public Map<String, LearnerProgress> getAllProgress(@NotNull String learnerId) {
        return learnerProgress.getOrDefault(learnerId, Map.of());
    }

    /**
     * Gets recommended next topics for a learner.
     *
     * @param learnerId the learner ID
     * @param maxTopics maximum number of topics to recommend
     * @return list of recommended topics
     */
    public List<Topic> getRecommendedTopics(@NotNull String learnerId, int maxTopics) {
        Map<String, LearnerProgress> progress = learnerProgress.get(learnerId);
        Set<String> masteredTopics = progress != null ?
            progress.entrySet().stream()
                .filter(e -> e.getValue().mastery() >= 0.8)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()) :
            Set.of();
        
        // Find topics whose prerequisites are mastered but aren't mastered themselves
        return topics.values().stream()
            .filter(topic -> !masteredTopics.contains(topic.id()))
            .filter(topic -> masteredTopics.containsAll(topic.prerequisites()))
            .sorted(Comparator.comparingInt(t -> t.gradeLevel()))
            .limit(maxTopics)
            .toList();
    }

    /**
     * Checks if a learner is ready for a topic.
     *
     * @param learnerId the learner ID
     * @param topicId the topic ID
     * @return readiness assessment
     */
    public TopicReadiness checkReadiness(@NotNull String learnerId, @NotNull String topicId) {
        Topic topic = topics.get(topicId);
        if (topic == null) {
            return new TopicReadiness(false, List.of(), "Topic not found");
        }
        
        Map<String, LearnerProgress> progress = learnerProgress.get(learnerId);
        
        List<String> missingPrereqs = new ArrayList<>();
        for (String prereqId : topic.prerequisites()) {
            LearnerProgress prereqProgress = progress != null ? progress.get(prereqId) : null;
            if (prereqProgress == null || prereqProgress.mastery() < 0.6) {
                missingPrereqs.add(prereqId);
            }
        }
        
        if (missingPrereqs.isEmpty()) {
            return new TopicReadiness(true, List.of(), "Ready to learn");
        } else {
            List<Topic> missingTopics = missingPrereqs.stream()
                .map(topics::get)
                .filter(Objects::nonNull)
                .toList();
            return new TopicReadiness(false, missingTopics, 
                "Missing prerequisites: " + missingPrereqs);
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void validateTopologyOrder(List<String> topicIds) {
        Set<String> seen = new HashSet<>();
        for (String topicId : topicIds) {
            Topic topic = topics.get(topicId);
            if (topic != null) {
                for (String prereq : topic.prerequisites()) {
                    if (topicIds.contains(prereq) && !seen.contains(prereq)) {
                        throw new IllegalArgumentException(
                            "Invalid order: " + prereq + " must come before " + topicId);
                    }
                }
            }
            seen.add(topicId);
        }
    }

    private void collectPrerequisites(String topicId, Set<String> collected) {
        Topic topic = topics.get(topicId);
        if (topic == null) return;
        
        for (String prereq : topic.prerequisites()) {
            if (!collected.contains(prereq)) {
                collectPrerequisites(prereq, collected);
                collected.add(prereq);
            }
        }
    }

    private List<String> topologicalSort(Set<String> topicIds) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        
        for (String id : topicIds) {
            inDegree.put(id, 0);
            graph.put(id, new ArrayList<>());
        }
        
        for (String id : topicIds) {
            Topic topic = topics.get(id);
            if (topic != null) {
                for (String prereq : topic.prerequisites()) {
                    if (topicIds.contains(prereq)) {
                        graph.get(prereq).add(id);
                        inDegree.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        
        List<String> result = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        
        for (String id : topicIds) {
            if (inDegree.get(id) == 0) {
                queue.offer(id);
            }
        }
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            
            for (String neighbor : graph.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        return result;
    }

    private int estimateDuration(List<String> topicIds) {
        return topicIds.stream()
            .map(topics::get)
            .filter(Objects::nonNull)
            .mapToInt(Topic::estimatedMinutes)
            .sum();
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * A topic in the curriculum.
     */
    public record Topic(
        String id,
        String name,
        String description,
        String subject,
        int gradeLevel,
        List<String> prerequisites,
        List<String> standards,  // e.g., Common Core, NGSS standards
        int estimatedMinutes,
        Map<String, String> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String id;
            private String name;
            private String description = "";
            private String subject;
            private int gradeLevel = 5;
            private List<String> prerequisites = new ArrayList<>();
            private List<String> standards = new ArrayList<>();
            private int estimatedMinutes = 30;
            private Map<String, String> metadata = new HashMap<>();

            public Builder id(String id) { this.id = id; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder subject(String subject) { this.subject = subject; return this; }
            public Builder gradeLevel(int gradeLevel) { this.gradeLevel = gradeLevel; return this; }
            public Builder prerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; return this; }
            public Builder addPrerequisite(String prereq) { this.prerequisites.add(prereq); return this; }
            public Builder standards(List<String> standards) { this.standards = standards; return this; }
            public Builder addStandard(String standard) { this.standards.add(standard); return this; }
            public Builder estimatedMinutes(int minutes) { this.estimatedMinutes = minutes; return this; }
            public Builder metadata(String key, String value) { this.metadata.put(key, value); return this; }

            public Topic build() {
                return new Topic(id, name, description, subject, gradeLevel, 
                    List.copyOf(prerequisites), List.copyOf(standards), 
                    estimatedMinutes, Map.copyOf(metadata));
            }
        }
    }

    /**
     * A learning path through the curriculum.
     */
    public record LearningPath(
        String id,
        String name,
        String description,
        List<String> topicIds,
        int estimatedMinutes,
        Map<String, String> metadata
    ) {}

    /**
     * Learner progress on a topic.
     */
    public record LearnerProgress(
        String topicId,
        double mastery,
        int timeSpentMinutes,
        int attempts,
        Instant lastAttempt
    ) {}

    /**
     * Topic readiness assessment.
     */
    public record TopicReadiness(
        boolean ready,
        List<Topic> missingPrerequisites,
        String message
    ) {}
}
