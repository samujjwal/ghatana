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
 * Domain entity representing a user story.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates story attributes including tasks, acceptance criteria, and AI-generated
 * content. This is the primary work unit in agile development.
 *
 * <p><b>Lifecycle States</b><br>
 *
 * <pre>
 * BACKLOG → TODO → IN_PROGRESS → REVIEW → DONE
 *                       ↓
 *                   BLOCKED
 * </pre>
 *
 * <p><b>AI Features</b><br>
 * - Story points estimation based on title/description
 * - Automatic task breakdown (4-8 tasks)
 * - Acceptance criteria generation (3-5 criteria)
 * - Dependency identification
 *
 * @doc.type class
 * @doc.purpose Story domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Story {

    private UUID id;
    private String tenantId;
    private String projectId;
    private String sprintId;  // null if in backlog
    private String storyKey;  // e.g., "PROJ-123"
    private String title;
    private String description;
    private StoryType type;
    private Priority priority;
    private StoryStatus status;
    private int storyPoints;
    private Integer estimatedHours;
    private Integer actualHours;
    private List<String> assignedTo;
    private List<Task> tasks;
    private List<AcceptanceCriterion> acceptanceCriteria;
    private String branch;
    private PullRequest pullRequest;
    private List<String> blockedBy;
    private List<String> blocks;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private Map<String, Object> metadata;

    public Story() {
        this.id = UUID.randomUUID();
        this.status = StoryStatus.BACKLOG;
        this.priority = Priority.P2;
        this.storyPoints = 0;
        this.assignedTo = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.acceptanceCriteria = new ArrayList<>();
        this.blockedBy = new ArrayList<>();
        this.blocks = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ========== Enums ==========

    /**
     * Story type classification.
     */
    public enum StoryType {
        FEATURE,    // New functionality
        BUG,        // Defect fix
        CHORE,      // Technical debt, maintenance
        SPIKE       // Research, exploration
    }

    /**
     * Story priority (P0 = highest).
     */
    public enum Priority {
        P0,  // Critical - blocks release
        P1,  // High - must have for release
        P2,  // Medium - should have
        P3   // Low - nice to have
    }

    /**
     * Story lifecycle status.
     */
    public enum StoryStatus {
        BACKLOG,      // Not in any sprint
        TODO,         // In sprint, not started
        IN_PROGRESS,  // Development in progress
        REVIEW,       // Code review / QA
        DONE,         // Completed
        BLOCKED       // Blocked by dependency
    }

    // ========== Nested Classes ==========

    /**
     * Task within a story.
     */
    public static class Task {
        private UUID id;
        private String storyId;
        private String title;
        private String description;
        private TaskStatus status;
        private String assignedTo;
        private Integer estimatedMinutes;
        private Instant completedAt;
        private boolean aiGenerated;
        private List<String> aiSuggestions;

        public Task() {
            this.id = UUID.randomUUID();
            this.status = TaskStatus.TODO;
            this.aiSuggestions = new ArrayList<>();
        }

        public enum TaskStatus {
            TODO,
            IN_PROGRESS,
            DONE
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getStoryId() { return storyId; }
        public void setStoryId(String storyId) { this.storyId = storyId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public TaskStatus getStatus() { return status; }
        public void setStatus(TaskStatus status) { this.status = status; }

        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

        public Integer getEstimatedMinutes() { return estimatedMinutes; }
        public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }

        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

        public boolean isAiGenerated() { return aiGenerated; }
        public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

        public List<String> getAiSuggestions() { return aiSuggestions; }
        public void setAiSuggestions(List<String> aiSuggestions) { this.aiSuggestions = aiSuggestions; }

        /**
         * Complete the task.
         */
        public void complete() {
            this.status = TaskStatus.DONE;
            this.completedAt = Instant.now();
        }
    }

    /**
     * Acceptance criterion for a story.
     */
    public static class AcceptanceCriterion {
        private UUID id;
        private String storyId;
        private String description;
        private boolean completed;
        private Instant completedAt;
        private String completedBy;
        private boolean aiGenerated;

        public AcceptanceCriterion() {
            this.id = UUID.randomUUID();
            this.completed = false;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getStoryId() { return storyId; }
        public void setStoryId(String storyId) { this.storyId = storyId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        public Instant getCompletedAt() { return completedAt; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

        public String getCompletedBy() { return completedBy; }
        public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }

        public boolean isAiGenerated() { return aiGenerated; }
        public void setAiGenerated(boolean aiGenerated) { this.aiGenerated = aiGenerated; }

        /**
         * Mark criterion as completed.
         *
         * @param userId the user who completed it
         */
        public void markCompleted(String userId) {
            this.completed = true;
            this.completedAt = Instant.now();
            this.completedBy = userId;
        }
    }

    /**
     * Pull request associated with a story.
     */
    public static class PullRequest {
        private String id;
        private int number;
        private String url;
        private String title;
        private PRStatus status;
        private String author;
        private List<String> reviewers;
        private Instant createdAt;
        private Instant mergedAt;
        private int additions;
        private int deletions;
        private int changedFiles;

        public PullRequest() {
            this.reviewers = new ArrayList<>();
        }

        public enum PRStatus {
            DRAFT,
            OPEN,
            CHANGES_REQUESTED,
            APPROVED,
            MERGED,
            CLOSED
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public PRStatus getStatus() { return status; }
        public void setStatus(PRStatus status) { this.status = status; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public List<String> getReviewers() { return reviewers; }
        public void setReviewers(List<String> reviewers) { this.reviewers = reviewers; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

        public Instant getMergedAt() { return mergedAt; }
        public void setMergedAt(Instant mergedAt) { this.mergedAt = mergedAt; }

        public int getAdditions() { return additions; }
        public void setAdditions(int additions) { this.additions = additions; }

        public int getDeletions() { return deletions; }
        public void setDeletions(int deletions) { this.deletions = deletions; }

        public int getChangedFiles() { return changedFiles; }
        public void setChangedFiles(int changedFiles) { this.changedFiles = changedFiles; }
    }

    // ========== Domain Methods ==========

    /**
     * Transition to a new status.
     *
     * @param newStatus the target status
     * @throws IllegalStateException if transition is not allowed
     */
    public void transitionTo(StoryStatus newStatus) {
        validateTransition(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
        if (newStatus == StoryStatus.DONE) {
            this.completedAt = Instant.now();
        }
    }

    private void validateTransition(StoryStatus newStatus) {
        switch (this.status) {
            case BACKLOG:
                if (newStatus != StoryStatus.TODO) {
                    throw new IllegalStateException("From BACKLOG, can only transition to TODO");
                }
                break;
            case TODO:
                if (newStatus != StoryStatus.IN_PROGRESS && newStatus != StoryStatus.BLOCKED 
                        && newStatus != StoryStatus.BACKLOG) {
                    throw new IllegalStateException("Invalid transition from TODO to " + newStatus);
                }
                break;
            case IN_PROGRESS:
                if (newStatus != StoryStatus.REVIEW && newStatus != StoryStatus.BLOCKED 
                        && newStatus != StoryStatus.TODO) {
                    throw new IllegalStateException("Invalid transition from IN_PROGRESS to " + newStatus);
                }
                break;
            case REVIEW:
                if (newStatus != StoryStatus.DONE && newStatus != StoryStatus.IN_PROGRESS) {
                    throw new IllegalStateException("Invalid transition from REVIEW to " + newStatus);
                }
                break;
            case BLOCKED:
                if (newStatus != StoryStatus.TODO && newStatus != StoryStatus.IN_PROGRESS) {
                    throw new IllegalStateException("Invalid transition from BLOCKED to " + newStatus);
                }
                break;
            case DONE:
                throw new IllegalStateException("Cannot transition from DONE status");
            default:
                throw new IllegalStateException("Unknown status: " + this.status);
        }
    }

    /**
     * Add a task to the story.
     *
     * @param task the task to add
     */
    public void addTask(Task task) {
        task.setStoryId(this.id.toString());
        this.tasks.add(task);
        this.updatedAt = Instant.now();
    }

    /**
     * Add an acceptance criterion to the story.
     *
     * @param criterion the criterion to add
     */
    public void addAcceptanceCriterion(AcceptanceCriterion criterion) {
        criterion.setStoryId(this.id.toString());
        this.acceptanceCriteria.add(criterion);
        this.updatedAt = Instant.now();
    }

    /**
     * Calculate task completion percentage.
     *
     * @return completion as 0-100
     */
    public int calculateTaskProgress() {
        if (tasks.isEmpty()) return 0;
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == Task.TaskStatus.DONE)
                .count();
        return (int) ((completed * 100) / tasks.size());
    }

    /**
     * Calculate acceptance criteria completion percentage.
     *
     * @return completion as 0-100
     */
    public int calculateAcceptanceProgress() {
        if (acceptanceCriteria.isEmpty()) return 0;
        long completed = acceptanceCriteria.stream()
                .filter(AcceptanceCriterion::isCompleted)
                .count();
        return (int) ((completed * 100) / acceptanceCriteria.size());
    }

    /**
     * Check if story is blocked.
     *
     * @return true if status is BLOCKED or has blockers
     */
    public boolean isBlocked() {
        return status == StoryStatus.BLOCKED || !blockedBy.isEmpty();
    }

    /**
     * Check if story is in backlog (not assigned to sprint).
     *
     * @return true if no sprint assigned
     */
    public boolean isInBacklog() {
        return sprintId == null || sprintId.isEmpty();
    }

    /**
     * Assign user to story.
     *
     * @param userId the user ID to assign
     */
    public void assign(String userId) {
        if (!this.assignedTo.contains(userId)) {
            this.assignedTo.add(userId);
            this.updatedAt = Instant.now();
        }
    }

    /**
     * Unassign user from story.
     *
     * @param userId the user ID to unassign
     */
    public void unassign(String userId) {
        this.assignedTo.remove(userId);
        this.updatedAt = Instant.now();
    }

    /**
     * Check if story can transition to a new status.
     * 
     * @param newStatus the target status
     * @return true if transition is valid
     */
    public boolean canTransitionTo(StoryStatus newStatus) {
        if (newStatus == null || newStatus == this.status) {
            return false;
        }
        
        // Define valid transitions
        return switch (this.status) {
            case BACKLOG -> newStatus == StoryStatus.TODO || newStatus == StoryStatus.BLOCKED;
            case TODO -> newStatus == StoryStatus.IN_PROGRESS || newStatus == StoryStatus.BLOCKED || newStatus == StoryStatus.BACKLOG;
            case IN_PROGRESS -> newStatus == StoryStatus.REVIEW || newStatus == StoryStatus.BLOCKED || newStatus == StoryStatus.TODO;
            case REVIEW -> newStatus == StoryStatus.DONE || newStatus == StoryStatus.IN_PROGRESS || newStatus == StoryStatus.BLOCKED;
            case DONE -> false; // Cannot transition from DONE
            case BLOCKED -> newStatus == StoryStatus.TODO || newStatus == StoryStatus.IN_PROGRESS || newStatus == StoryStatus.BACKLOG;
        };
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSprintId() { return sprintId; }
    public void setSprintId(String sprintId) { this.sprintId = sprintId; }

    public String getStoryKey() { return storyKey; }
    public void setStoryKey(String storyKey) { this.storyKey = storyKey; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public StoryType getType() { return type; }
    public void setType(StoryType type) { this.type = type; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public StoryStatus getStatus() { return status; }
    public void setStatus(StoryStatus status) { this.status = status; }

    public int getStoryPoints() { return storyPoints; }
    public void setStoryPoints(int storyPoints) { this.storyPoints = storyPoints; }

    public Integer getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Integer estimatedHours) { this.estimatedHours = estimatedHours; }

    public Integer getActualHours() { return actualHours; }
    public void setActualHours(Integer actualHours) { this.actualHours = actualHours; }

    public List<String> getAssignedTo() { return assignedTo; }
    public void setAssignedTo(List<String> assignedTo) { this.assignedTo = assignedTo; }

    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> tasks) { this.tasks = tasks; }

    public List<AcceptanceCriterion> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<AcceptanceCriterion> acceptanceCriteria) { 
        this.acceptanceCriteria = acceptanceCriteria; 
    }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public PullRequest getPullRequest() { return pullRequest; }
    public void setPullRequest(PullRequest pullRequest) { this.pullRequest = pullRequest; }

    public List<String> getBlockedBy() { return blockedBy; }
    public void setBlockedBy(List<String> blockedBy) { this.blockedBy = blockedBy; }

    public List<String> getBlocks() { return blocks; }
    public void setBlocks(List<String> blocks) { this.blocks = blocks; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Story)) return false;
        Story story = (Story) o;
        return Objects.equals(id, story.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Story{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", storyPoints=" + storyPoints +
                '}';
    }
}
