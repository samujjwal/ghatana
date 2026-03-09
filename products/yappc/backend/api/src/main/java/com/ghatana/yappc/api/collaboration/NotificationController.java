/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.common.ApiResponse;
import com.ghatana.yappc.api.common.TenantContextExtractor;
import com.ghatana.yappc.api.domain.Notification.NotificationCategory;
import com.ghatana.yappc.api.service.NotificationService;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

/**
 * HTTP controller for notification endpoints.
 *
 * @doc.type class
 * @doc.purpose HTTP endpoints for notifications
 * @doc.layer api
 * @doc.pattern Controller
 */
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService service;
    private final ObjectMapper mapper;

    @Inject
    public NotificationController(NotificationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** GET /api/notifications */
    public Promise<HttpResponse> listNotifications(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    int limit = getIntParam(request, "limit", 50);
                    int offset = getIntParam(request, "offset", 0);
                    return service.listUserNotifications(ctx.tenantId(), ctx.userId(), limit, offset)
                            .map(ApiResponse::ok);
                })
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/notifications/unread */
    public Promise<HttpResponse> listUnreadNotifications(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.listUnreadNotifications(ctx.tenantId(), ctx.userId())
                        .map(ApiResponse::ok))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/notifications/count */
    public Promise<HttpResponse> getUnreadCount(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getUnreadCount(ctx.tenantId(), ctx.userId())
                        .map(count -> ApiResponse.ok(Map.of("unreadCount", count))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/notifications/category/:category */
    public Promise<HttpResponse> listByCategory(HttpRequest request, String category) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> {
                    try {
                        NotificationCategory cat = NotificationCategory.valueOf(category.toUpperCase());
                        return service.listByCategory(ctx.tenantId(), ctx.userId(), cat)
                                .map(ApiResponse::ok);
                    } catch (IllegalArgumentException e) {
                        return Promise.of(ApiResponse.badRequest("Invalid category: " + category));
                    }
                })
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** GET /api/notifications/:id */
    public Promise<HttpResponse> getNotification(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.getNotification(ctx.tenantId(), UUID.fromString(id))
                        .map(opt -> opt.map(ApiResponse::ok)
                                .orElse(ApiResponse.notFound("Notification not found"))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/notifications/:id/read */
    public Promise<HttpResponse> markAsRead(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.markAsRead(ctx.tenantId(), UUID.fromString(id))
                        .map(success -> success ? ApiResponse.ok(Map.of("marked", true)) 
                                : ApiResponse.notFound("Notification not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** POST /api/notifications/read-all */
    public Promise<HttpResponse> markAllAsRead(HttpRequest request) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.markAllAsRead(ctx.tenantId(), ctx.userId())
                        .map(success -> ApiResponse.ok(Map.of("marked", true))))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    /** DELETE /api/notifications/:id */
    public Promise<HttpResponse> deleteNotification(HttpRequest request, String id) {
        return TenantContextExtractor.requireAuthenticated(request)
                .then(ctx -> service.deleteNotification(ctx.tenantId(), UUID.fromString(id))
                        .map(deleted -> deleted ? ApiResponse.noContent() 
                                : ApiResponse.notFound("Notification not found")))
                .then(r -> Promise.of(r), e -> Promise.of(ApiResponse.fromException(e)));
    }

    // ========== Helper Methods ==========

    private int getIntParam(HttpRequest request, String name, int defaultValue) {
        String value = request.getQueryParameter(name);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
