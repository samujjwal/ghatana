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
 * Domain entity representing a development sprint.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates sprint attributes including goals, capacity, stories, and velocity tracking.
 * This is the primary entity for time-boxed development iterations.
 *
 * <p><b>Lifecycle States</b><br>
 *
 * <pre>
 * PLANNING → ACTIVE → COMPLETED
 *              ↓
 *          CANCELLED
 * </pre>
 *
 * <p><b>Sprint Metrics</b><br>
 * - Team Capacity: Total story points the team can handle
 * - Committed Points: Sum of all story points when sprint starts
 * - Completed Points: Sum of completed story points
 * - Velocity: completedPoints / committedPoints
 *
 * @doc.type class
 * @doc.purpose Sprint domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Sprint {

    private UUID id;
    private String tenantId;
    private String projectId;
    private int sprintNumber;
    private String name;
    private List<String> goals;
    private SprintStatus status;
    private Instant startDate;
    private Instant endDate;
    private int teamCapacity;
    private int plannedVelocity;
    private int actualVelocity;
    private int committedPoints;
    private int completedPoints;
    private double velocity;
    private List<Story> stories;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private SprintRetrospective retrospective;
    private Map<String, Object> metadata;

    public Sprint() {
        this.id = UUID.randomUUID();
        this.status = SprintStatus.PLANNING;
        this.goals = new ArrayList<>();
        this.stories = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ========== Enums ==========

    /**
     * Sprint lifecycle status.
     */
    public enum SprintStatus {
        PLANNING,   // Sprint being planned, stories can be added/removed
        ACTIVE,     // Sprint in progress, committed points locked
        COMPLETED,  // Sprint finished normally
        CANCELLED   // Sprint cancelled before completion
    }

    // ========== Nested Classes ==========

    /**
     * Sprint retrospective data.
     */
    public static class SprintRetrospective {
        private List<String> wentWell;
        private List<String> needsImprovement;
        private List<String> actionItems;
        private String summary;
        private Instant conductedAt;

        public SprintRetrospective() {
            this.wentWell = new ArrayList<>();
            this.needsImprovement = new ArrayList<>();
            this.actionItems = new ArrayList<>();
        }

        public List<String> getWentWell() { return wentWell; }
        public void setWentWell(List<String> wentWell) { this.wentWell = wentWell; }

        public List<String> getNeedsImprovement() { return needsImprovement; }
        public void setNeedsImprovement(List<String> needsImprovement) { 
            this.needsImprovement = needsImprovement; 
        }

        public List<String> getActionItems() { return actionItems; }
        public void setActionItems(List<String> actionItems) { this.actionItems = actionItems; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public Instant getConductedAt() { return conductedAt; }
        public void setConductedAt(Instant conductedAt) { this.conductedAt = conductedAt; }
    }

    // ========== Domain Methods ==========

    /**
     * Start the sprint, locking committed points.
     *
     * @throws IllegalStateException if sprint is not in PLANNING status
     */
    public void start() {
        if (status != SprintStatus.PLANNING) {
            throw new IllegalStateException("Cannot start sprint in status: " + status);
        }
        if (stories.isEmpty()) {
            throw new IllegalStateException("Cannot start sprint with no stories");
        }
        this.committedPoints = stories.stream()
                .mapToInt(Story::getStoryPoints)
                .sum();
        this.status = SprintStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    /**
     * Complete the sprint and calculate velocity.
     *
     * @throws IllegalStateException if sprint is not ACTIVE
     */
    public void complete() {
        if (status != SprintStatus.ACTIVE) {
            throw new IllegalStateException("Cannot complete sprint in status: " + status);
        }
        this.completedPoints = stories.stream()
                .filter(s -> s.getStatus() == Story.StoryStatus.DONE)
                .mapToInt(Story::getStoryPoints)
                .sum();
        this.velocity = committedPoints > 0 ? (double) completedPoints / committedPoints : 0.0;
        this.status = SprintStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Cancel the sprint.
     *
     * @throws IllegalStateException if sprint is already completed
     */
    public void cancel() {
        if (status == SprintStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel completed sprint");
        }
        this.status = SprintStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    /**
     * Add a story to the sprint.
     *
     * @param story the story to add
     * @throws IllegalStateException if sprint is not in PLANNING status
     */
    public void addStory(Story story) {
        if (status != SprintStatus.PLANNING) {
            throw new IllegalStateException("Cannot add stories to sprint in status: " + status);
        }
        story.setSprintId(this.id.toString());
        this.stories.add(story);
        this.updatedAt = Instant.now();
    }

    /**
     * Remove a story from the sprint.
     *
     * @param storyId the story ID to remove
     * @throws IllegalStateException if sprint is not in PLANNING status
     */
    public void removeStory(String storyId) {
        if (status != SprintStatus.PLANNING) {
            throw new IllegalStateException("Cannot remove stories from sprint in status: " + status);
        }
        this.stories.removeIf(s -> s.getId().toString().equals(storyId));
        this.updatedAt = Instant.now();
    }

    /**
     * Calculate current progress percentage.
     *
     * @return progress as 0-100
     */
    public int calculateProgress() {
        if (committedPoints == 0) return 0;
        int currentCompleted = stories.stream()
                .filter(s -> s.getStatus() == Story.StoryStatus.DONE)
                .mapToInt(Story::getStoryPoints)
                .sum();
        return (int) ((currentCompleted * 100.0) / committedPoints);
    }

    /**
     * Get remaining capacity in story points.
     *
     * @return remaining capacity
     */
    public int getRemainingCapacity() {
        int assigned = stories.stream()
                .mapToInt(Story::getStoryPoints)
                .sum();
        return teamCapacity - assigned;
    }

    /**
     * Check if sprint has exceeded its end date.
     *
     * @return true if past end date
     */
    public boolean isOverdue() {
        return status == SprintStatus.ACTIVE && Instant.now().isAfter(endDate);
    }

    /**
     * Get days remaining in sprint.
     *
     * @return days remaining, negative if overdue
     */
    public long getDaysRemaining() {
        if (status != SprintStatus.ACTIVE) return 0;
        long seconds = endDate.getEpochSecond() - Instant.now().getEpochSecond();
        return seconds / (24 * 60 * 60);
    }

    // ========== Getters and Setters ==========

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getGoals() { return goals; }
    public void setGoals(List<String> goals) { this.goals = goals; }

    public SprintStatus getStatus() { return status; }
    public void setStatus(SprintStatus status) { this.status = status; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public int getTeamCapacity() { return teamCapacity; }
    public void setTeamCapacity(int teamCapacity) { this.teamCapacity = teamCapacity; }

    public int getPlannedVelocity() { return plannedVelocity; }
    public void setPlannedVelocity(int plannedVelocity) { this.plannedVelocity = plannedVelocity; }

    public int getActualVelocity() { return actualVelocity; }
    public void setActualVelocity(int actualVelocity) { this.actualVelocity = actualVelocity; }

    public int getSprintNumber() { return sprintNumber; }
    public void setSprintNumber(int sprintNumber) { this.sprintNumber = sprintNumber; }

    public int getCommittedPoints() { return committedPoints; }
    public void setCommittedPoints(int committedPoints) { this.committedPoints = committedPoints; }

    public int getCompletedPoints() { return completedPoints; }
    public void setCompletedPoints(int completedPoints) { this.completedPoints = completedPoints; }

    public double getVelocity() { return velocity; }
    public void setVelocity(double velocity) { this.velocity = velocity; }

    public List<Story> getStories() { return stories; }
    public void setStories(List<Story> stories) { this.stories = stories; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public SprintRetrospective getRetrospective() { return retrospective; }
    public void setRetrospective(SprintRetrospective retrospective) { this.retrospective = retrospective; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sprint)) return false;
        Sprint sprint = (Sprint) o;
        return Objects.equals(id, sprint.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Sprint{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
