/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.Notification;
import com.ghatana.yappc.api.domain.Notification.*;
import com.ghatana.yappc.api.repository.NotificationRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of NotificationRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for notifications
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<String, Map<UUID, Notification>> tenantNotifications = new ConcurrentHashMap<>();

    private Map<UUID, Notification> getNotificationMap(String tenantId) {
        return tenantNotifications.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<Notification> save(Notification notification) {
        if (notification.getId() == null) {
            notification.setId(UUID.randomUUID());
        }
        getNotificationMap(notification.getTenantId()).put(notification.getId(), notification);
        return Promise.of(notification);
    }

    @Override
    public Promise<Optional<Notification>> findById(String tenantId, UUID id) {
        return Promise.of(Optional.ofNullable(getNotificationMap(tenantId).get(id)));
    }

    @Override
    public Promise<List<Notification>> findByUser(String tenantId, String userId, int limit, int offset) {
        return Promise.of(
            getNotificationMap(tenantId).values().stream()
                .filter(n -> userId.equals(n.getUserId()))
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Notification>> findUnreadByUser(String tenantId, String userId) {
        return Promise.of(
            getNotificationMap(tenantId).values().stream()
                .filter(n -> userId.equals(n.getUserId()) && !n.isRead())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Notification>> findByCategory(String tenantId, String userId, NotificationCategory category) {
        return Promise.of(
            getNotificationMap(tenantId).values().stream()
                .filter(n -> userId.equals(n.getUserId()) && category == n.getCategory())
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Long> countUnread(String tenantId, String userId) {
        long count = getNotificationMap(tenantId).values().stream()
            .filter(n -> userId.equals(n.getUserId()) && !n.isRead())
            .count();
        return Promise.of(count);
    }

    @Override
    public Promise<Boolean> markAsRead(String tenantId, UUID id) {
        Notification notification = getNotificationMap(tenantId).get(id);
        if (notification != null) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            return Promise.of(true);
        }
        return Promise.of(false);
    }

    @Override
    public Promise<Boolean> markAllAsRead(String tenantId, String userId) {
        boolean marked = false;
        for (Notification n : getNotificationMap(tenantId).values()) {
            if (userId.equals(n.getUserId()) && !n.isRead()) {
                n.setRead(true);
                n.setReadAt(Instant.now());
                marked = true;
            }
        }
        return Promise.of(marked);
    }

    @Override
    public Promise<Boolean> delete(String tenantId, UUID id) {
        Notification removed = getNotificationMap(tenantId).remove(id);
        return Promise.of(removed != null);
    }

    @Override
    public Promise<Integer> deleteExpired(String tenantId) {
        Instant now = Instant.now();
        Map<UUID, Notification> map = getNotificationMap(tenantId);
        List<UUID> expiredIds = map.values().stream()
            .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(now))
            .map(Notification::getId)
            .collect(Collectors.toList());
        expiredIds.forEach(map::remove);
        return Promise.of(expiredIds.size());
    }
}
