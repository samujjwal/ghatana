package com.ghatana.appplatform.posttrade.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Manages delivery of trade confirmations to clients across channels (D09-002).
 *              Tracks status lifecycle: PENDING → SENT → DELIVERED → ACKNOWLEDGED.
 *              Escalates PENDING confirmations to ESCALATED after configurable timeout.
 * @doc.layer   Domain — Post-Trade operations
 * @doc.pattern Command pattern for delivery; plug-in port for channel adapters; lifecycle FSM
 */
public class ConfirmationDeliveryService {

    private static final Duration ESCALATION_TIMEOUT = Duration.ofHours(4);

    /** Port for actual delivery — adapter per channel (email, portal, FIX). */
    public interface DeliveryChannelPort {
        boolean deliver(String confirmationId, String clientId, String jsonPayload, String pdfPath);
    }

    public enum DeliveryStatus { PENDING, SENT, DELIVERED, ACKNOWLEDGED, ESCALATED }

    public record DeliveryAttempt(
        String attemptId,
        String confirmationId,
        String channel,       // EMAIL / PORTAL / FIX
        DeliveryStatus status,
        Instant attemptedAt,
        String failureReason
    ) {}

    private final DataSource dataSource;
    private final DeliveryChannelPort deliveryChannel;
    private final Executor executor;
    private final Counter sentCounter;
    private final Counter escalatedCounter;

    public ConfirmationDeliveryService(DataSource dataSource, DeliveryChannelPort deliveryChannel,
                                        Executor executor, MeterRegistry registry) {
        this.dataSource = dataSource;
        this.deliveryChannel = deliveryChannel;
        this.executor = executor;
        this.sentCounter = Counter.builder("posttrade.confirmations.sent_total").register(registry);
        this.escalatedCounter = Counter.builder("posttrade.confirmations.escalated_total").register(registry);
    }

    /**
     * Attempt delivery of a PENDING confirmation.
     * Updates status to SENT on success, records failure reason on failure.
     */
    public Promise<DeliveryAttempt> deliver(String confirmationId) {
        return Promise.ofBlocking(executor, () -> {
            String[] details = loadConfirmationDetails(confirmationId);
            // details: [clientId, jsonPayload, pdfPath]
            boolean success = deliveryChannel.deliver(confirmationId, details[0], details[1], details[2]);
            String status = success ? "SENT" : "PENDING";
            DeliveryAttempt attempt = new DeliveryAttempt(UUID.randomUUID().toString(),
                confirmationId, "PORTAL", DeliveryStatus.valueOf(status), Instant.now(),
                success ? null : "Channel delivery failed");

            persistAttempt(attempt);
            if (success) {
                updateConfirmationStatus(confirmationId, "SENT");
                sentCounter.increment();
            }
            return attempt;
        });
    }

    /** Mark a confirmation as acknowledged by the client. */
    public Promise<Void> acknowledge(String confirmationId) {
        return Promise.ofBlocking(executor, () -> {
            updateConfirmationStatus(confirmationId, "ACKNOWLEDGED");
            return null;
        });
    }

    /**
     * Escalation sweep: move confirmations still PENDING after ESCALATION_TIMEOUT to ESCALATED.
     * Should be called periodically (e.g., every 30 minutes by scheduler).
     */
    public Promise<List<String>> runEscalationSweep() {
        return Promise.ofBlocking(executor, () -> {
            List<String> escalated = new ArrayList<>();
            Instant cutoff = Instant.now().minus(ESCALATION_TIMEOUT);
            String sql = "UPDATE trade_confirmations SET status = 'ESCALATED' " +
                         "WHERE status = 'PENDING' AND created_at < ? RETURNING id";
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, java.sql.Timestamp.from(cutoff));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        escalated.add(rs.getString("id"));
                        escalatedCounter.increment();
                    }
                }
            }
            return escalated;
        });
    }

    private String[] loadConfirmationDetails(String confirmationId) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT client_id, json_payload, pdf_path FROM trade_confirmations WHERE id = ?")) {
            ps.setObject(1, UUID.fromString(confirmationId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new String[]{rs.getString("client_id"),
                        rs.getString("json_payload"), rs.getString("pdf_path")};
                }
                throw new IllegalStateException("Confirmation not found: " + confirmationId);
            }
        }
    }

    private void updateConfirmationStatus(String confirmationId, String status) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE trade_confirmations SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, UUID.fromString(confirmationId));
            ps.executeUpdate();
        }
    }

    private void persistAttempt(DeliveryAttempt a) throws Exception {
        String sql = "INSERT INTO confirmation_delivery_attempts(id, confirmation_id, channel, " +
                     "status, attempted_at, failure_reason) VALUES(?,?,?,?,NOW(),?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(a.attemptId()));
            ps.setObject(2, UUID.fromString(a.confirmationId()));
            ps.setString(3, a.channel());
            ps.setString(4, a.status().name());
            ps.setString(5, a.failureReason());
            ps.executeUpdate();
        }
    }
}
