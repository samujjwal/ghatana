package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manages workflow trigger registrations and evaluates incoming signals
 *              against registered triggers to fire new workflow instances.
 *              Trigger types: EVENT (K-05 topic subscription with CEL filter),
 *              SCHEDULE (cron with K-15 BS calendar), MANUAL (REST API), API_WEBHOOK (HMAC auth).
 *              Trigger evaluation under 10ms; each trigger maps to exactly one workflow version.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class WorkflowTriggerService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface EventBusSubscriberPort {
        /** Subscribe to a K-05 topic and deliver messages to this service's trigger handler. */
        void subscribe(String topic, String consumerGroup, EventHandler handler);

        @FunctionalInterface
        interface EventHandler {
            Promise<Void> handle(String eventId, String eventType, String payloadJson);
        }
    }

    public interface SchedulerPort {
        /** Register a cron schedule (with optional K-15 BS calendar awareness). */
        void scheduleCron(String triggerId, String cronExpression, String calendarId, Runnable callback);

        /** Unregister a cron schedule. */
        void unscheduleCron(String triggerId);
    }

    public interface CelEvaluatorPort {
        /** Evaluate a CEL filter expression against event payload. Returns true if filter passes. */
        boolean evaluate(String celExpression, String payloadJson);
    }

    public interface WorkflowInstanceLaunchPort {
        /** Launch a new workflow instance for the given definition + trigger payload. */
        Promise<String> launch(String workflowId, int version, String contextJson, String triggerId);
    }

    public interface HmacVerifierPort {
        /** Verify an HMAC-SHA256 signature of a webhook payload. Returns true if valid. */
        boolean verify(String payload, String signature, String secret);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record TriggerRegistration(
        String triggerId,
        String workflowId,
        int workflowVersion,
        String triggerType,      // EVENT | SCHEDULE | MANUAL | API_WEBHOOK
        String topicFilter,      // for EVENT: "topic::eventType"
        String celFilter,        // for EVENT: optional CEL expression
        String cronExpression,   // for SCHEDULE
        String calendarId,       // for SCHEDULE: K-15 calendar
        String webhookPath,      // for API_WEBHOOK: URL path suffix
        String status,           // ACTIVE | PAUSED | DELETED
        String createdAt
    ) {}

    public record TriggerFiring(
        String firingId,
        String triggerId,
        String workflowId,
        String instanceId,
        String payloadJson,
        String firedAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final EventBusSubscriberPort eventBusSubscriber;
    private final SchedulerPort scheduler;
    private final CelEvaluatorPort cel;
    private final WorkflowInstanceLaunchPort launcher;
    private final HmacVerifierPort hmacVerifier;
    private final AuditPort auditPort;

    private final Counter triggerFiredTotal;
    private final Counter eventFilterPassedTotal;
    private final Counter eventFilterRejectedTotal;
    private final Counter webhookHmacFailedTotal;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public WorkflowTriggerService(DataSource dataSource,
                                   Executor executor,
                                   MeterRegistry meterRegistry,
                                   EventBusSubscriberPort eventBusSubscriber,
                                   SchedulerPort scheduler,
                                   CelEvaluatorPort cel,
                                   WorkflowInstanceLaunchPort launcher,
                                   HmacVerifierPort hmacVerifier,
                                   AuditPort auditPort) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.eventBusSubscriber  = eventBusSubscriber;
        this.scheduler           = scheduler;
        this.cel                 = cel;
        this.launcher            = launcher;
        this.hmacVerifier        = hmacVerifier;
        this.auditPort           = auditPort;

        this.triggerFiredTotal          = Counter.builder("workflow.trigger.fired_total")
                .description("Total workflow trigger firings")
                .register(meterRegistry);
        this.eventFilterPassedTotal     = Counter.builder("workflow.trigger.event_filter_passed_total")
                .description("Events that passed CEL filter and triggered a workflow")
                .register(meterRegistry);
        this.eventFilterRejectedTotal   = Counter.builder("workflow.trigger.event_filter_rejected_total")
                .description("Events rejected by CEL filter")
                .register(meterRegistry);
        this.webhookHmacFailedTotal     = Counter.builder("workflow.trigger.webhook_hmac_failed_total")
                .description("Webhook triggers rejected due to HMAC verification failure")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Register a new workflow trigger.
     * For EVENT triggers, also subscribes to the K-05 topic.
     * For SCHEDULE triggers, also registers the cron.
     */
    public Promise<TriggerRegistration> registerTrigger(String workflowId, int workflowVersion,
                                                         String triggerType, String topicFilter,
                                                         String celFilter, String cronExpression,
                                                         String calendarId, String webhookPath,
                                                         String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            String triggerId = insertTriggerBlocking(workflowId, workflowVersion, triggerType,
                topicFilter, celFilter, cronExpression, calendarId, webhookPath);

            if ("EVENT".equals(triggerType) && topicFilter != null) {
                wireEventTrigger(triggerId, workflowId, workflowVersion, topicFilter, celFilter);
            } else if ("SCHEDULE".equals(triggerType) && cronExpression != null) {
                scheduler.scheduleCron(triggerId, cronExpression, calendarId,
                    () -> fireTrigger(triggerId, workflowId, workflowVersion, "{}"));
            }
            auditPort.log("TRIGGER_REGISTERED", createdBy, triggerId, "WORKFLOW_TRIGGER", null,
                          buildTriggerJson(workflowId, triggerType));
            return queryTriggerById(triggerId);
        });
    }

    /** Handle an incoming API webhook trigger (with HMAC verification). */
    public Promise<String> handleWebhook(String webhookPath, String payload, String signature,
                                          String secret) {
        if (!hmacVerifier.verify(payload, signature, secret)) {
            webhookHmacFailedTotal.increment();
            return Promise.ofException(new SecurityException("Invalid HMAC signature for webhook: " + webhookPath));
        }
        return Promise.ofBlocking(executor, () -> queryTriggerByWebhookPath(webhookPath))
            .then(registration -> fireTrigger(registration.triggerId(), registration.workflowId(),
                                              registration.workflowVersion(), payload));
    }

    /** Handle a MANUAL trigger via REST API call. */
    public Promise<String> triggerManually(String workflowId, int version, String contextJson,
                                           String triggeredBy) {
        return Promise.ofBlocking(executor, () -> queryManualTrigger(workflowId, version))
            .then(registration -> fireTrigger(registration.triggerId(), workflowId, version, contextJson));
    }

    /** Pause a trigger (stops delivering event-based or schedule-based triggers). */
    public Promise<Void> pauseTrigger(String triggerId, String pausedBy) {
        return Promise.ofBlocking(executor, () -> {
            setTriggerStatus(triggerId, "PAUSED");
            scheduler.unscheduleCron(triggerId);
            auditPort.log("TRIGGER_PAUSED", pausedBy, triggerId, "WORKFLOW_TRIGGER", null, null);
            return null;
        });
    }

    /** List all active triggers for a workflow. */
    public Promise<List<TriggerRegistration>> listTriggers(String workflowId) {
        return Promise.ofBlocking(executor, () -> queryTriggersByWorkflow(workflowId));
    }

    /** List recent trigger firings. */
    public Promise<List<TriggerFiring>> listFirings(String triggerId, int limit) {
        return Promise.ofBlocking(executor, () -> queryFirings(triggerId, limit));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void wireEventTrigger(String triggerId, String workflowId, int version,
                                   String topicFilter, String celFilter) {
        String[] parts  = topicFilter.split("::", 2);
        String topic    = parts[0];
        String eventTypeFilter = parts.length > 1 ? parts[1] : null;

        eventBusSubscriber.subscribe(topic, "workflow-trigger-" + triggerId,
            (eventId, eventType, payloadJson) -> {
                if (eventTypeFilter != null && !eventTypeFilter.equals(eventType)) {
                    return Promise.complete();
                }
                if (celFilter != null && !celFilter.isBlank() && !cel.evaluate(celFilter, payloadJson)) {
                    eventFilterRejectedTotal.increment();
                    return Promise.complete();
                }
                eventFilterPassedTotal.increment();
                return fireTrigger(triggerId, workflowId, version, payloadJson).toVoid();
            });
    }

    private Promise<String> fireTrigger(String triggerId, String workflowId,
                                        int version, String payloadJson) {
        return launcher.launch(workflowId, version, payloadJson, triggerId)
            .then(instanceId -> Promise.ofBlocking(executor, () -> {
                insertFiringBlocking(triggerId, workflowId, instanceId, payloadJson);
                triggerFiredTotal.increment();
                return instanceId;
            }));
    }

    private String insertTriggerBlocking(String workflowId, int version, String type,
                                          String topicFilter, String celFilter,
                                          String cron, String calendarId, String webhookPath) {
        String sql = """
            INSERT INTO workflow_trigger_registrations
                (trigger_id, workflow_id, workflow_version, trigger_type, topic_filter,
                 cel_filter, cron_expression, calendar_id, webhook_path, status, created_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now())
            RETURNING trigger_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, version);
            ps.setString(3, type);
            ps.setString(4, topicFilter);
            ps.setString(5, celFilter);
            ps.setString(6, cron);
            ps.setString(7, calendarId);
            ps.setString(8, webhookPath);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("trigger_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert trigger", e);
        }
    }

    private void insertFiringBlocking(String triggerId, String workflowId,
                                      String instanceId, String payloadJson) {
        String sql = """
            INSERT INTO workflow_trigger_firings
                (firing_id, trigger_id, workflow_id, instance_id, payload_json, fired_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?::jsonb, now())
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, triggerId);
            ps.setString(2, workflowId);
            ps.setString(3, instanceId);
            ps.setString(4, payloadJson);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to record trigger firing", e);
        }
    }

    private void setTriggerStatus(String triggerId, String status) {
        String sql = "UPDATE workflow_trigger_registrations SET status = ? WHERE trigger_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, triggerId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set trigger status for " + triggerId, e);
        }
    }

    private TriggerRegistration queryTriggerById(String triggerId) {
        String sql = """
            SELECT trigger_id, workflow_id, workflow_version, trigger_type, topic_filter,
                   cel_filter, cron_expression, calendar_id, webhook_path, status, created_at::text
              FROM workflow_trigger_registrations
             WHERE trigger_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, triggerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Trigger not found: " + triggerId);
                return mapTrigger(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query trigger " + triggerId, e);
        }
    }

    private TriggerRegistration queryTriggerByWebhookPath(String webhookPath) {
        String sql = """
            SELECT trigger_id, workflow_id, workflow_version, trigger_type, topic_filter,
                   cel_filter, cron_expression, calendar_id, webhook_path, status, created_at::text
              FROM workflow_trigger_registrations
             WHERE webhook_path = ? AND trigger_type = 'API_WEBHOOK' AND status = 'ACTIVE'
             LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, webhookPath);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("No webhook trigger for path: " + webhookPath);
                return mapTrigger(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query webhook trigger for " + webhookPath, e);
        }
    }

    private TriggerRegistration queryManualTrigger(String workflowId, int version) {
        String sql = """
            SELECT trigger_id, workflow_id, workflow_version, trigger_type, topic_filter,
                   cel_filter, cron_expression, calendar_id, webhook_path, status, created_at::text
              FROM workflow_trigger_registrations
             WHERE workflow_id = ? AND workflow_version = ? AND trigger_type = 'MANUAL' AND status = 'ACTIVE'
             LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException(
                    "No MANUAL trigger for " + workflowId + "@" + version);
                return mapTrigger(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query manual trigger", e);
        }
    }

    private List<TriggerRegistration> queryTriggersByWorkflow(String workflowId) {
        String sql = """
            SELECT trigger_id, workflow_id, workflow_version, trigger_type, topic_filter,
                   cel_filter, cron_expression, calendar_id, webhook_path, status, created_at::text
              FROM workflow_trigger_registrations
             WHERE workflow_id = ? AND status != 'DELETED'
             ORDER BY created_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TriggerRegistration> result = new ArrayList<>();
                while (rs.next()) result.add(mapTrigger(rs));
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list triggers for " + workflowId, e);
        }
    }

    private List<TriggerFiring> queryFirings(String triggerId, int limit) {
        String sql = """
            SELECT firing_id, trigger_id, workflow_id, instance_id, payload_json::text, fired_at::text
              FROM workflow_trigger_firings
             WHERE trigger_id = ?
             ORDER BY fired_at DESC
             LIMIT ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, triggerId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TriggerFiring> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new TriggerFiring(
                        rs.getString("firing_id"), rs.getString("trigger_id"),
                        rs.getString("workflow_id"), rs.getString("instance_id"),
                        rs.getString("payload_json"), rs.getString("fired_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query firings for trigger " + triggerId, e);
        }
    }

    private TriggerRegistration mapTrigger(ResultSet rs) throws Exception {
        return new TriggerRegistration(
            rs.getString("trigger_id"), rs.getString("workflow_id"),
            rs.getInt("workflow_version"), rs.getString("trigger_type"),
            rs.getString("topic_filter"), rs.getString("cel_filter"),
            rs.getString("cron_expression"), rs.getString("calendar_id"),
            rs.getString("webhook_path"), rs.getString("status"),
            rs.getString("created_at")
        );
    }

    private String buildTriggerJson(String workflowId, String triggerType) {
        return String.format("{\"workflowId\":\"%s\",\"triggerType\":\"%s\"}", workflowId, triggerType);
    }
}
