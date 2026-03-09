/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.domain.Notification;
import com.ghatana.yappc.api.domain.Notification.*;
import com.ghatana.yappc.api.repository.NotificationRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for managing notifications.
 *
 * @doc.type class
 * @doc.purpose Business logic for notification operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    @Inject
    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a notification.
     */
    public Promise<Notification> createNotification(String tenantId, CreateNotificationInput input) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setUserId(input.userId());
        notification.setType(input.type());
        notification.setCategory(determineCategory(input.type()));
        notification.setTitle(input.title());
        notification.setMessage(input.message());
        notification.setActionUrl(input.actionUrl());
        notification.setPriority(input.priority() != null ? input.priority() : NotificationPriority.NORMAL);
        notification.setSourceType(input.sourceType());
        notification.setSourceId(input.sourceId());
        notification.setActorId(input.actorId());
        notification.setActorName(input.actorName());

        // Set expiration (default 30 days)
        if (input.expiresAt() != null) {
            notification.setExpiresAt(input.expiresAt());
        } else {
            notification.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        }

        return repository.save(notification);
    }

    /**
     * Creates notifications for multiple users.
     */
    public Promise<List<Notification>> notifyUsers(String tenantId, List<String> userIds, NotifyUsersInput input) {
        if (userIds.isEmpty()) {
            return Promise.of(new ArrayList<>());
        }

        // Create notifications sequentially to avoid Promise.all issues
        List<Notification> results = new ArrayList<>();
        Promise<Notification> chain = Promise.of(null);

        for (String userId : userIds) {
            CreateNotificationInput notifInput = new CreateNotificationInput(
                    userId,
                    input.type(),
                    input.title(),
                    input.message(),
                    input.actionUrl(),
                    input.priority(),
                    input.sourceType(),
                    input.sourceId(),
                    input.actorId(),
                    input.actorName(),
                    input.expiresAt()
            );
            chain = chain.then(prev -> {
                if (prev != null) results.add(prev);
                return createNotification(tenantId, notifInput);
            });
        }

        return chain.map(last -> {
            if (last != null) results.add(last);
            return results;
        });
    }

    /**
     * Gets a notification by ID.
     */
    public Promise<Optional<Notification>> getNotification(String tenantId, UUID notificationId) {
        return repository.findById(tenantId, notificationId);
    }

    /**
     * Lists notifications for a user.
     */
    public Promise<List<Notification>> listUserNotifications(
            String tenantId, String userId, int limit, int offset) {
        return repository.findByUser(tenantId, userId, limit, offset);
    }

    /**
     * Lists unread notifications for a user.
     */
    public Promise<List<Notification>> listUnreadNotifications(String tenantId, String userId) {
        return repository.findUnreadByUser(tenantId, userId);
    }

    /**
     * Lists notifications by category.
     */
    public Promise<List<Notification>> listByCategory(
            String tenantId, String userId, NotificationCategory category) {
        return repository.findByCategory(tenantId, userId, category);
    }

    /**
     * Gets unread count for a user.
     */
    public Promise<Long> getUnreadCount(String tenantId, String userId) {
        return repository.countUnread(tenantId, userId);
    }

    /**
     * Marks a notification as read.
     */
    public Promise<Boolean> markAsRead(String tenantId, UUID notificationId) {
        return repository.markAsRead(tenantId, notificationId);
    }

    /**
     * Marks all notifications as read for a user.
     */
    public Promise<Boolean> markAllAsRead(String tenantId, String userId) {
        return repository.markAllAsRead(tenantId, userId);
    }

    /**
     * Deletes a notification.
     */
    public Promise<Boolean> deleteNotification(String tenantId, UUID notificationId) {
        return repository.delete(tenantId, notificationId);
    }

    /**
     * Cleans up expired notifications.
     */
    public Promise<Integer> cleanupExpired(String tenantId) {
        return repository.deleteExpired(tenantId);
    }

    // ========== Notification Builders ==========

    /**
     * Notifies about story assignment.
     */
    public Promise<Notification> notifyStoryAssigned(
            String tenantId, String userId, String storyId, String storyTitle, 
            String actorId, String actorName) {
        CreateNotificationInput input = new CreateNotificationInput(
                userId,
                NotificationType.STORY_ASSIGNED,
                "Story Assigned",
                actorName + " assigned you to: " + storyTitle,
                "/stories/" + storyId,
                NotificationPriority.HIGH,
                "story",
                storyId,
                actorId,
                actorName,
                null
        );
        return createNotification(tenantId, input);
    }

    /**
     * Notifies about review requested.
     */
    public Promise<Notification> notifyReviewRequested(
            String tenantId, String userId, String reviewId, String reviewTitle,
            String actorId, String actorName) {
        CreateNotificationInput input = new CreateNotificationInput(
                userId,
                NotificationType.REVIEW_REQUESTED,
                "Review Requested",
                actorName + " requested your review on: " + reviewTitle,
                "/reviews/" + reviewId,
                NotificationPriority.HIGH,
                "review",
                reviewId,
                actorId,
                actorName,
                null
        );
        return createNotification(tenantId, input);
    }

    /**
     * Notifies about review approved.
     */
    public Promise<Notification> notifyReviewApproved(
            String tenantId, String userId, String reviewId, String reviewTitle,
            String actorId, String actorName) {
        CreateNotificationInput input = new CreateNotificationInput(
                userId,
                NotificationType.REVIEW_APPROVED,
                "Review Approved",
                actorName + " approved your review: " + reviewTitle,
                "/reviews/" + reviewId,
                NotificationPriority.NORMAL,
                "review",
                reviewId,
                actorId,
                actorName,
                null
        );
        return createNotification(tenantId, input);
    }

    /**
     * Notifies about sprint started.
     */
    public Promise<List<Notification>> notifySprintStarted(
            String tenantId, List<String> teamMemberIds, String sprintId, String sprintName) {
        NotifyUsersInput input = new NotifyUsersInput(
                NotificationType.SPRINT_STARTED,
                "Sprint Started",
                "Sprint '" + sprintName + "' has started",
                "/sprints/" + sprintId,
                NotificationPriority.HIGH,
                "sprint",
                sprintId,
                null,
                "System",
                null
        );
        return notifyUsers(tenantId, teamMemberIds, input);
    }

    /**
     * Notifies about team invitation.
     */
    public Promise<Notification> notifyTeamInvitation(
            String tenantId, String userId, String teamId, String teamName,
            String actorId, String actorName) {
        CreateNotificationInput input = new CreateNotificationInput(
                userId,
                NotificationType.TEAM_INVITATION,
                "Team Invitation",
                actorName + " invited you to join team: " + teamName,
                "/teams/" + teamId + "/join",
                NotificationPriority.HIGH,
                "team",
                teamId,
                actorId,
                actorName,
                Instant.now().plus(7, ChronoUnit.DAYS) // Invitation expires in 7 days
        );
        return createNotification(tenantId, input);
    }

    // ========== Helper Methods ==========

    private NotificationCategory determineCategory(NotificationType type) {
        return switch (type) {
            case STORY_ASSIGNED -> NotificationCategory.ASSIGNMENT;
            case STORY_UPDATED, STORY_COMPLETED, STORY_BLOCKED,
                 SPRINT_STARTED, SPRINT_ENDING, SPRINT_COMPLETED -> NotificationCategory.UPDATE;
            case REVIEW_REQUESTED, REVIEW_APPROVED, REVIEW_CHANGES_REQUESTED,
                 REVIEW_COMMENT, PR_MERGED -> NotificationCategory.REVIEW;
            case MENTION, REVIEW_MENTION, COMMENT -> NotificationCategory.MENTION;
            case TEAM_INVITATION, TEAM_MEMBER_JOINED, TEAM_MEMBER_LEFT -> NotificationCategory.TEAM;
            case REMINDER, SYSTEM, ALERT -> NotificationCategory.SYSTEM;
        };
    }

    // ========== Input DTOs ==========

    public record CreateNotificationInput(
            String userId,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            NotificationPriority priority,
            String sourceType,
            String sourceId,
            String actorId,
            String actorName,
            Instant expiresAt
    ) {}

    public record NotifyUsersInput(
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            NotificationPriority priority,
            String sourceType,
            String sourceId,
            String actorId,
            String actorName,
            Instant expiresAt
    ) {}
}
