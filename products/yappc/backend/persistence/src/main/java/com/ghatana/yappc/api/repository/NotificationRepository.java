/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Notification;
import com.ghatana.yappc.api.domain.Notification.NotificationCategory;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Notification entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for notification persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface NotificationRepository {

    Promise<Notification> save(Notification notification);

    Promise<Optional<Notification>> findById(String tenantId, UUID id);

    Promise<List<Notification>> findByUser(String tenantId, String userId, int limit, int offset);

    Promise<List<Notification>> findUnreadByUser(String tenantId, String userId);

    Promise<List<Notification>> findByCategory(String tenantId, String userId, NotificationCategory category);

    Promise<Long> countUnread(String tenantId, String userId);

    Promise<Boolean> markAsRead(String tenantId, UUID id);

    Promise<Boolean> markAllAsRead(String tenantId, String userId);

    Promise<Boolean> delete(String tenantId, UUID id);

    Promise<Integer> deleteExpired(String tenantId);
}
