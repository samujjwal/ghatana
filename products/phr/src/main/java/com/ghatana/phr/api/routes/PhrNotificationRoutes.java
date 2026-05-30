package com.ghatana.phr.api.routes;

import com.ghatana.phr.kernel.service.DurablePhrNotificationSender;
import com.ghatana.phr.kernel.policy.PhrLogRedactor;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Notification API routes for the PHR product.
 *
 * <p>Provides notification listing with PHI-safe filtering. Notifications never expose
 * PHI in title or body - only metadata and action identifiers are returned.
 *
 * @doc.type class
 * @doc.purpose ActiveJ route adapter for notification feed and read/unread state
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class PhrNotificationRoutes {

    private final Eventloop eventloop;
    private final DurablePhrNotificationSender notificationSender;

    public PhrNotificationRoutes(
            Eventloop eventloop,
            DurablePhrNotificationSender notificationSender) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.notificationSender = Objects.requireNonNull(notificationSender, "notificationSender must not be null");
    }

    /**
     * Returns the routing servlet for notification endpoints.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.GET, "/", this::handleListNotifications)
            .with(HttpMethod.POST, "/:notificationId/read", this::handleMarkAsRead)
            .with(HttpMethod.POST, "/:notificationId/action", this::handleNotificationAction)
            .with(HttpMethod.GET, "/preferences", this::handleGetPreferences)
            .with(HttpMethod.PUT, "/preferences", this::handleUpdatePreferences)
            .build();
    }

    private Promise<HttpResponse> handleListNotifications(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        int limit = 50;
        String limitParam = request.getQueryParameter("limit");
        if (limitParam != null && !limitParam.isBlank()) {
            try {
                limit = Integer.parseInt(limitParam);
                if (limit < 1 || limit > 100) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_LIMIT", 
                        "Limit must be between 1 and 100", context.correlationId());
                }
            } catch (NumberFormatException ex) {
                return PhrRouteSupport.errorResponse(400, "INVALID_LIMIT", 
                    "Limit must be a valid integer", context.correlationId());
            }
        }

        return notificationSender.getPendingNotifications(context.principalId(), limit)
            .then(entries -> {
                List<Map<String, Object>> notifications = entries.stream()
                    .map(entry -> {
                        Map<String, Object> map = new java.util.HashMap<>();
                        map.put("id", entry.id());
                        map.put("type", notificationContractType(entry.notificationType()));
                        // Apply PHI redaction to notification title and body
                        String title = getNotificationTitle(entry.notificationType());
                        String body = getNotificationBody(entry.notificationType());
                        map.put("title", PhrLogRedactor.redactForNotification(title));
                        map.put("body", PhrLogRedactor.redactForNotification(body));
                        map.put("referenceId", entry.referenceId());
                        map.put("referenceType", entry.referenceType());
                        map.put("channel", entry.channel().name());
                        map.put("scheduledFor", entry.scheduledFor() != null ? entry.scheduledFor().toString() : "");
                        map.put("status", entry.status().name());
                        map.put("readAt", entry.readAt() != null ? entry.readAt().toString() : null);
                        map.put("actionable", isActionable(entry.notificationType()));
                        map.put("createdAt", entry.createdAt() != null ? entry.createdAt().toString() : "");
                        return map;
                    })
                    .toList();

                return PhrRouteSupport.jsonResponse(200, Map.of(
                    "items", notifications,
                    "count", notifications.size(, correlationId),
                    "principalId", context.principalId()
                ), context.correlationId());
            })
            .whenException(ex -> PhrRouteSupport.errorResponse(500, "NOTIFICATION_FETCH_ERROR", 
                "Failed to fetch notifications", context.correlationId()));
    }

    private Promise<HttpResponse> handleMarkAsRead(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String notificationId = request.getPathParameter("notificationId");
        if (notificationId == null || notificationId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_NOTIFICATION_ID", 
                "Notification ID is required", context.correlationId());
        }

        return notificationSender.markAsRead(notificationId, context.principalId())
            .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                "notificationId", notificationId,
                "read", true
            , correlationId), context.correlationId()))
            .whenException(ex -> PhrRouteSupport.errorResponse(500, "MARK_READ_ERROR", 
                "Failed to mark notification as read", context.correlationId()));
    }

    private Promise<HttpResponse> handleNotificationAction(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        String notificationId = request.getPathParameter("notificationId");
        if (notificationId == null || notificationId.isBlank()) {
            return PhrRouteSupport.errorResponse(400, "INVALID_NOTIFICATION_ID", 
                "Notification ID is required", context.correlationId());
        }

        return request.loadBody()
            .then(body -> {
                String action = request.getQueryParameter("action");
                if (action == null || action.isBlank()) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_ACTION", 
                        "Action parameter is required", context.correlationId());
                }

                return notificationSender.handleNotificationAction(notificationId, context.principalId(), action)
                    .then(result -> PhrRouteSupport.jsonResponse(200, Map.of(
                        "notificationId", notificationId,
                        "action", action,
                        "result", result
                    , correlationId), context.correlationId()))
                    .whenException(ex -> PhrRouteSupport.errorResponse(500, "ACTION_ERROR", 
                        "Failed to handle notification action", context.correlationId()));
            });
    }

    private String getNotificationTitle(String notificationType) {
        return switch (notificationType) {
            case "APPOINTMENT_REMINDER_SCHEDULED" -> "Appointment Reminder";
            case "APPOINTMENT_REMINDER_CANCELLED" -> "Appointment Cancelled";
            case "CONSENT_CHANGE_GRANTED" -> "Consent Granted";
            case "CONSENT_CHANGE_REVOKED" -> "Consent Revoked";
            case "EMERGENCY_ACCESS_GRANTED" -> "Emergency Access Granted";
            case "EMERGENCY_ACCESS_REVIEW_REQUIRED" -> "Emergency Access Review Required";
            case "TELEMEDICINE_SESSION_SCHEDULED" -> "Telemedicine Session Scheduled";
            case "TELEMEDICINE_SESSION_STARTED" -> "Telemedicine Session Started";
            case "LAB_RESULT_AVAILABLE" -> "Lab Result Available";
            case "SYSTEM" -> "System Notification";
            default -> "Notification";
        };
    }

    private String notificationContractType(String notificationType) {
        return switch (notificationType) {
            case "CONSENT_CHANGE_GRANTED", "CONSENT_CHANGE_REVOKED", "CONSENT_EXPIRY" -> "consent_expiry";
            case "APPOINTMENT_REMINDER_SCHEDULED", "APPOINTMENT_REMINDER_CANCELLED" -> "appointment_reminder";
            case "LAB_RESULT_AVAILABLE" -> "lab_result";
            case "EMERGENCY_ACCESS_GRANTED", "EMERGENCY_ACCESS_REVIEW_REQUIRED" -> "emergency_access";
            default -> "system";
        };
    }

    private String getNotificationBody(String notificationType) {
        return switch (notificationType) {
            case "APPOINTMENT_REMINDER_SCHEDULED" -> "You have an upcoming appointment.";
            case "APPOINTMENT_REMINDER_CANCELLED" -> "Your appointment has been cancelled.";
            case "CONSENT_CHANGE_GRANTED" -> "A new consent has been granted for your records.";
            case "CONSENT_CHANGE_REVOKED" -> "A consent has been revoked.";
            case "EMERGENCY_ACCESS_GRANTED" -> "Emergency access was granted to your records.";
            case "EMERGENCY_ACCESS_REVIEW_REQUIRED" -> "Emergency access requires review.";
            case "TELEMEDICINE_SESSION_SCHEDULED" -> "A telemedicine session has been scheduled.";
            case "TELEMEDICINE_SESSION_STARTED" -> "Your telemedicine session is starting.";
            case "LAB_RESULT_AVAILABLE" -> "New lab results are available.";
            case "SYSTEM" -> "A system notification is available.";
            default -> "Please check your notifications for details.";
        };
    }

    private boolean isActionable(String notificationType) {
        return switch (notificationType) {
            case "APPOINTMENT_REMINDER_SCHEDULED", 
                 "EMERGENCY_ACCESS_REVIEW_REQUIRED",
                 "TELEMEDICINE_SESSION_SCHEDULED" -> true;
            default -> false;
        };
    }

    private Promise<HttpResponse> handleGetPreferences(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        return notificationSender.getNotificationPreferences(context.principalId())
            .then(preferences -> PhrRouteSupport.jsonResponse(200, Map.of(
                "principalId", context.principalId(, correlationId),
                "preferences", preferences
            ), context.correlationId()))
            .whenException(ex -> PhrRouteSupport.errorResponse(500, "PREFERENCES_FETCH_ERROR", 
                "Failed to fetch notification preferences", context.correlationId()));
    }

    private Promise<HttpResponse> handleUpdatePreferences(HttpRequest request) {
        String correlationId = PhrRouteSupport.extractCorrelationId(request);
        PhrRouteSupport.PhrRequestContext context;
        try {
            context = PhrRouteSupport.requireContext(request);
        } catch (IllegalArgumentException ex) {
            return PhrRouteSupport.errorResponse(400, "MISSING_CONTEXT", ex.getMessage(, correlationId));
        }

        return request.loadBody()
            .then(body -> {
                try {
                    String json = body.getString(java.nio.charset.StandardCharsets.UTF_8);
                    var node = PhrRouteSupport.JSON.readTree(json);
                    
                    boolean emailEnabled = node.path("emailEnabled").asBoolean(true);
                    boolean smsEnabled = node.path("smsEnabled").asBoolean(false);
                    boolean inAppEnabled = node.path("inAppEnabled").asBoolean(true);
                    
                    Map<String, Object> preferences = Map.of(
                        "emailEnabled", emailEnabled,
                        "smsEnabled", smsEnabled,
                        "inAppEnabled", inAppEnabled
                    );
                    
                    return notificationSender.updateNotificationPreferences(context.principalId(), preferences)
                        .then($ -> PhrRouteSupport.jsonResponse(200, Map.of(
                            "principalId", context.principalId(, correlationId),
                            "preferences", preferences,
                            "updated", true
                        ), context.correlationId()))
                        .whenException(ex -> PhrRouteSupport.errorResponse(500, "PREFERENCES_UPDATE_ERROR", 
                            "Failed to update notification preferences", context.correlationId()));
                } catch (Exception ex) {
                    return PhrRouteSupport.errorResponse(400, "INVALID_PREFERENCES", 
                        "Invalid preferences format", context.correlationId());
                }
            });
    }
}
