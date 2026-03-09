/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.yappc.api.domain.Story;
import com.ghatana.yappc.api.domain.Story.*;
import com.ghatana.yappc.api.repository.StoryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;

/**
 * Service for managing stories.
 *
 * <p><b>Purpose</b><br>
 * Implements business logic for story management, including creation, lifecycle
 * transitions, task management, and AI-assisted features.
 *
 * @doc.type class
 * @doc.purpose Story management business logic
 * @doc.layer service
 * @doc.pattern Service
 */
public class StoryService {

    private static final Logger logger = LoggerFactory.getLogger(StoryService.class);

    private final StoryRepository repository;
    private final AuditService auditService;
    private final LLMGateway llmGateway;

    @Inject
    public StoryService(StoryRepository repository, AuditService auditService, LLMGateway llmGateway) {
        this.repository = repository;
        this.auditService = auditService;
        this.llmGateway = llmGateway;
    }

    /**
     * Creates a new story.
     */
    public Promise<Story> createStory(String tenantId, CreateStoryInput input) {
        logger.info("Creating story '{}' for project {}", input.title(), input.projectId());

        Story story = new Story();
        story.setId(UUID.randomUUID());
        story.setTenantId(tenantId);
        story.setProjectId(input.projectId().toString());
        story.setSprintId(input.sprintId() != null ? input.sprintId().toString() : null);
        story.setStoryKey(generateStoryKey(input.projectId().toString()));
        story.setTitle(input.title());
        story.setDescription(input.description());
        story.setType(input.type() != null ? input.type() : StoryType.FEATURE);
        story.setPriority(input.priority() != null ? input.priority() : Priority.P2);
        story.setStatus(input.sprintId() != null ? StoryStatus.TODO : StoryStatus.BACKLOG);
        story.setStoryPoints(input.storyPoints() != null ? input.storyPoints() : 0);
        story.setCreatedBy(input.createdBy());
        story.setCreatedAt(Instant.now());
        story.setUpdatedAt(Instant.now());

        Promise<List<Task>> tasksPromise = input.generateTasks()
                ? generateTasksFromDescription(input.title(), input.description())
                : Promise.of(new ArrayList<>());

        Promise<List<AcceptanceCriterion>> criteriaPromise = input.generateAcceptanceCriteria()
                ? generateAcceptanceCriteria(input.title(), input.description())
                : Promise.of(new ArrayList<>());

        return tasksPromise.then(tasks -> {
            if (input.generateTasks()) {
                story.setTasks(tasks);
            }
            return criteriaPromise.then(criteria -> {
                if (input.generateAcceptanceCriteria()) {
                    story.setAcceptanceCriteria(criteria);
                }
                return repository.save(story);
            });
        });
    }

    /**
     * Gets a story by ID.
     */
    public Promise<Optional<Story>> getStory(String tenantId, UUID storyId) {
        return repository.findById(tenantId, storyId);
    }

    /**
     * Gets a story by key.
     */
    public Promise<Optional<Story>> getStoryByKey(String tenantId, String storyKey) {
        return repository.findByKey(tenantId, storyKey);
    }

    /**
     * Lists stories in a sprint.
     */
    public Promise<List<Story>> listSprintStories(String tenantId, UUID sprintId) {
        return repository.findBySprint(tenantId, sprintId.toString());
    }

    /**
     * Lists backlog stories for a project.
     */
    public Promise<List<Story>> listBacklog(String tenantId, UUID projectId) {
        return repository.findBacklog(tenantId, projectId.toString());
    }

    /**
     * Lists stories assigned to a user.
     */
    public Promise<List<Story>> listAssignedStories(String tenantId, String userId) {
        return repository.findByAssignee(tenantId, userId);
    }

    /**
     * Lists blocked stories for a project.
     */
    public Promise<List<Story>> listBlockedStories(String tenantId, UUID projectId) {
        return repository.findBlocked(tenantId, projectId.toString());
    }

    /**
     * Updates a story.
     */
    public Promise<Story> updateStory(String tenantId, UUID storyId, UpdateStoryInput input) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();

                    if (input.title() != null) {
                        story.setTitle(input.title());
                    }
                    if (input.description() != null) {
                        story.setDescription(input.description());
                    }
                    if (input.type() != null) {
                        story.setType(input.type());
                    }
                    if (input.priority() != null) {
                        story.setPriority(input.priority());
                    }
                    if (input.storyPoints() != null) {
                        story.setStoryPoints(input.storyPoints());
                    }

                    story.setUpdatedAt(Instant.now());
                    return repository.save(story);
                });
    }

    /**
     * Moves a story to a new status.
     */
    public Promise<Story> moveStory(String tenantId, UUID storyId, StoryStatus newStatus) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();

                    if (!story.canTransitionTo(newStatus)) {
                        return Promise.ofException(new IllegalStateException(
                                "Invalid transition from " + story.getStatus() + " to " + newStatus));
                    }

                    story.transitionTo(newStatus);
                    return repository.save(story);
                });
    }

    /**
     * Assigns a story to a user.
     */
    public Promise<Story> assignStory(String tenantId, UUID storyId, String userId) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.assign(userId);
                    return repository.save(story);
                });
    }

    /**
     * Unassigns a user from a story.
     */
    public Promise<Story> unassignStory(String tenantId, UUID storyId, String userId) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.unassign(userId);
                    return repository.save(story);
                });
    }

    /**
     * Moves a story to a sprint.
     */
    public Promise<Story> moveToSprint(String tenantId, UUID storyId, UUID sprintId) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.setSprintId(sprintId != null ? sprintId.toString() : null);
                    if (story.getStatus() == StoryStatus.BACKLOG && sprintId != null) {
                        story.setStatus(StoryStatus.TODO);
                    } else if (story.getStatus() == StoryStatus.TODO && sprintId == null) {
                        story.setStatus(StoryStatus.BACKLOG);
                    }
                    story.setUpdatedAt(Instant.now());
                    return repository.save(story);
                });
    }

    /**
     * Adds a task to a story.
     */
    public Promise<Story> addTask(String tenantId, UUID storyId, Task task) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    if (task.getId() == null) {
                        task.setId(UUID.randomUUID());
                    }
                    task.setStoryId(storyId.toString());
                    story.addTask(task);
                    return repository.save(story);
                });
    }

    /**
     * Updates a task status.
     */
    public Promise<Story> updateTaskStatus(String tenantId, UUID storyId, String taskId, String status) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.getTasks().stream()
                            .filter(t -> t.getId().toString().equals(taskId))
                            .findFirst()
                            .ifPresent(t -> {
                                t.setStatus(Task.TaskStatus.valueOf(status.toUpperCase()));
                                if (t.getStatus() == Task.TaskStatus.DONE) {
                                    t.setCompletedAt(Instant.now());
                                }
                            });
                    story.setUpdatedAt(Instant.now());
                    return repository.save(story);
                });
    }

    /**
     * Adds an acceptance criterion.
     */
    public Promise<Story> addAcceptanceCriterion(String tenantId, UUID storyId, AcceptanceCriterion criterion) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    if (criterion.getId() == null) {
                        criterion.setId(UUID.randomUUID());
                    }
                    criterion.setStoryId(storyId.toString());
                    story.addAcceptanceCriterion(criterion);
                    return repository.save(story);
                });
    }

    /**
     * Updates an acceptance criterion.
     */
    public Promise<Story> updateAcceptanceCriterion(String tenantId, UUID storyId, 
                                                     String criterionId, boolean completed) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.getAcceptanceCriteria().stream()
                            .filter(c -> c.getId().toString().equals(criterionId))
                            .findFirst()
                            .ifPresent(c -> {
                                c.setCompleted(completed);
                                if (completed) {
                                    c.setCompletedAt(Instant.now());
                                }
                            });
                    story.setUpdatedAt(Instant.now());
                    return repository.save(story);
                });
    }

    /**
     * Links a pull request to a story.
     */
    public Promise<Story> linkPullRequest(String tenantId, UUID storyId, PullRequest pullRequest) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.setPullRequest(pullRequest);
                    story.setUpdatedAt(Instant.now());
                    logger.info("Linked PR #{} to story {}", pullRequest.getNumber(), storyId);
                    return repository.save(story);
                });
    }

    /**
     * Adds a blocker.
     */
    public Promise<Story> addBlocker(String tenantId, UUID storyId, String blockingStoryId) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    if (!story.getBlockedBy().contains(blockingStoryId)) {
                        story.getBlockedBy().add(blockingStoryId);
                        story.setStatus(StoryStatus.BLOCKED);
                        story.setUpdatedAt(Instant.now());
                    }
                    return repository.save(story);
                });
    }

    /**
     * Removes a blocker.
     */
    public Promise<Story> removeBlocker(String tenantId, UUID storyId, String blockerId) {
        return repository.findById(tenantId, storyId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalArgumentException(
                                "Story not found: " + storyId));
                    }
                    Story story = opt.get();
                    story.getBlockedBy().remove(blockerId);
                    if (story.getBlockedBy().isEmpty() && story.getStatus() == StoryStatus.BLOCKED) {
                        story.setStatus(StoryStatus.TODO);
                    }
                    story.setUpdatedAt(Instant.now());
                    return repository.save(story);
                });
    }

    /**
     * Deletes a story.
     */
    public Promise<Boolean> deleteStory(String tenantId, UUID storyId) {
        logger.info("Deleting story: {}", storyId);
        return repository.delete(tenantId, storyId);
    }

    // ========== Helper Methods ==========

    private String generateStoryKey(String projectId) {
        // Simple key generation - in production would be project-based sequence
        String prefix = projectId.substring(0, Math.min(4, projectId.length())).toUpperCase();
        return prefix + "-" + (System.currentTimeMillis() % 10000);
    }

    private Promise<List<Task>> generateTasksFromDescription(String title, String description) {
        CompletionRequest request = CompletionRequest.builder()
                .prompt("You are a software project manager. Generate implementation tasks for the following user story.\n\n" +
                        "Title: " + title + "\nDescription: " + description +
                        "\n\nReturn exactly 3-5 tasks, one per line, in the format: TITLE|||DESCRIPTION")
                .maxTokens(512)
                .temperature(0.5)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            logger.warn("LLM gateway returned null completion promise for task generation, using fallback");
            completionPromise = Promise.ofException(
                    new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .map(resultText -> {
                    List<Task> tasks = new ArrayList<>();
                    for (String line : resultText.split("\n")) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String[] parts = line.split("\\|\\|\\|", 2);
                        Task task = new Task();
                        task.setTitle(parts[0].replaceAll("^\\d+\\.?\\s*", "").trim());
                        task.setDescription(parts.length > 1 ? parts[1].trim() : "");
                        task.setAiGenerated(true);
                        tasks.add(task);
                    }
                    return tasks.isEmpty() ? createFallbackTasks(title) : tasks;
                })
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for task generation, using fallback", e);
                            return Promise.of(createFallbackTasks(title));
                        });
    }

    private Promise<List<AcceptanceCriterion>> generateAcceptanceCriteria(String title, String description) {
        CompletionRequest request = CompletionRequest.builder()
                .prompt("You are a QA engineer. Generate acceptance criteria for the following user story.\n\n" +
                        "Title: " + title + "\nDescription: " + description +
                        "\n\nReturn exactly 3-5 criteria, one per line. Each line is a clear, testable statement.")
                .maxTokens(512)
                .temperature(0.5)
                .build();

        Promise<com.ghatana.ai.llm.CompletionResult> completionPromise = llmGateway.complete(request);
        if (completionPromise == null) {
            logger.warn("LLM gateway returned null completion promise for criteria generation, using fallback");
            completionPromise = Promise.ofException(
                    new IllegalStateException("LLM gateway returned null completion promise"));
        }

        return completionPromise
                .map(result -> result.getText() != null ? result.getText() : "")
                .map(resultText -> {
                    List<AcceptanceCriterion> criteria = new ArrayList<>();
                    for (String line : resultText.split("\n")) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        AcceptanceCriterion criterion = new AcceptanceCriterion();
                        criterion.setDescription(line.replaceAll("^\\d+\\.?\\s*", "").trim());
                        criterion.setAiGenerated(true);
                        criteria.add(criterion);
                    }
                    return criteria.isEmpty() ? createFallbackCriteria() : criteria;
                })
                .then(
                        Promise::of,
                        e -> {
                            logger.warn("LLM call failed for acceptance criteria generation, using fallback", e);
                            return Promise.of(createFallbackCriteria());
                        });
    }

    private List<Task> createFallbackTasks(String title) {
        List<Task> tasks = new ArrayList<>();
        Task implementTask = new Task();
        implementTask.setTitle("Implement " + title);
        implementTask.setDescription("Implement the core functionality");
        implementTask.setAiGenerated(true);
        tasks.add(implementTask);

        Task testTask = new Task();
        testTask.setTitle("Write tests for " + title);
        testTask.setDescription("Write unit and integration tests");
        testTask.setAiGenerated(true);
        tasks.add(testTask);

        Task reviewTask = new Task();
        reviewTask.setTitle("Code review");
        reviewTask.setDescription("Review implementation and tests");
        reviewTask.setAiGenerated(true);
        tasks.add(reviewTask);
        return tasks;
    }

    private List<AcceptanceCriterion> createFallbackCriteria() {
        List<AcceptanceCriterion> criteria = new ArrayList<>();
        AcceptanceCriterion functional = new AcceptanceCriterion();
        functional.setDescription("Feature is functional as described");
        functional.setAiGenerated(true);
        criteria.add(functional);

        AcceptanceCriterion tested = new AcceptanceCriterion();
        tested.setDescription("All test cases pass");
        tested.setAiGenerated(true);
        criteria.add(tested);

        AcceptanceCriterion documented = new AcceptanceCriterion();
        documented.setDescription("Feature is documented");
        documented.setAiGenerated(true);
        criteria.add(documented);
        return criteria;
    }

    // ========== Input Records ==========

    public record CreateStoryInput(
            UUID projectId,
            UUID sprintId,
            String title,
            String description,
            StoryType type,
            Priority priority,
            Integer storyPoints,
            String createdBy,
            boolean generateTasks,
            boolean generateAcceptanceCriteria
    ) {}

    public record UpdateStoryInput(
            String title,
            String description,
            StoryType type,
            Priority priority,
            Integer storyPoints
    ) {}
}
