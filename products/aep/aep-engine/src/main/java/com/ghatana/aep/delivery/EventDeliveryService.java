/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.delivery;

import com.ghatana.aep.AepEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service that delivers processed events and pattern detections to registered
 * external destinations via pluggable {@link MessageSender} callbacks.
 *
 * <p>Callers in {@code platform:java:messaging} (which depends on this module) can adapt
 * any {@code QueueProducerStrategy} to the {@link MessageSender} functional interface:
 * <pre>{@code
 * MessageSender sender = (tenantId, eventType, payload, headers) -> {
 *     QueueMessage msg = new QueueMessage(eventType, payload, headers);
 *     return kafkaStrategy.send(msg);
 * };
 * }</pre>
 *
 * <p>Destinations are registered at construction time. Each call to
 * {@link #deliver} attempts delivery to all destinations, logging per-destination
 * failures without propagating exceptions so that partial success does not block
 * the processing pipeline.
 *
 * <p>Usage:
 * <pre>{@code
 * EventDeliveryService deliveryService = EventDeliveryService.withDestinations(
 *     new EventDestination("downstream-kafka", (tenantId, type, payload, headers) ->
 *         kafkaStrategy.send(new QueueMessage(type, payload, headers)))
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Event delivery service for routing processed events to external destinations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class EventDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(EventDeliveryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final List<EventDestination> destinations;

    public EventDeliveryService(List<EventDestination> destinations) {
        this.destinations = new CopyOnWriteArrayList<>(destinations);
    }

    /**
     * Create a delivery service with the given named destinations.
     *
     * @param destinations one or more event destinations
     * @return configured delivery service
     */
    public static EventDeliveryService withDestinations(EventDestination... destinations) {
        return new EventDeliveryService(List.of(destinations));
    }

    /**
     * Create a no-op delivery service (useful for testing or single-node deployments
     * where downstream delivery is not required).
     *
     * @return no-op delivery service
     */
    public static EventDeliveryService noOp() {
        return new EventDeliveryService(List.of());
    }

    /**
     * Deliver the event and any associated detections to all registered destinations.
     *
     * @param tenantId   owning tenant
     * @param event      the processed event
     * @param detections pattern detections from this processing cycle
     * @return promise of delivery result summary
     */
    public Promise<DeliveryResult> deliver(
            String tenantId, AepEngine.Event event, List<AepEngine.Detection> detections) {
        if (destinations.isEmpty()) {
            return Promise.of(DeliveryResult.noDestinations());
        }

        List<String> delivered = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, DeliveryFailure> failureDetails = new HashMap<>();

        for (EventDestination dest : destinations) {
            try {
                Map<String, Object> payload = buildDeliveryPayload(tenantId, event, detections);
                String payloadJson = MAPPER.writeValueAsString(payload);
                boolean success = dest.sender().send(
                    tenantId, event.type(), payloadJson,
                    Map.of("x-tenant-id", tenantId, "x-event-type", event.type())
                );
                if (success) {
                    delivered.add(dest.name());
                    log.debug("Delivered event type={} to destination={}", event.type(), dest.name());
                } else {
                    failed.add(dest.name());
                    failureDetails.put(dest.name(), new DeliveryFailure(
                        DeliveryFailureCategory.RETRYABLE,
                        "destination returned false"));
                    log.warn("Delivery to destination={} returned false for event type={}",
                        dest.name(), event.type());
                }
            } catch (JsonProcessingException e) {
                failed.add(dest.name());
                failureDetails.put(dest.name(), new DeliveryFailure(
                    DeliveryFailureCategory.NON_RETRYABLE,
                    e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage()));
                log.error("Failed to serialize event for delivery to {}: {}", dest.name(), e.getMessage());
            } catch (UncheckedIOException e) {
                failed.add(dest.name());
                failureDetails.put(dest.name(), new DeliveryFailure(
                    DeliveryFailureCategory.RETRYABLE,
                    e.getMessage()));
                log.error("Retryable delivery failure for destination={}: {}",
                    dest.name(), e.getMessage(), e);
            } catch (IllegalArgumentException e) {
                failed.add(dest.name());
                failureDetails.put(dest.name(), new DeliveryFailure(
                    DeliveryFailureCategory.NON_RETRYABLE,
                    e.getMessage()));
                log.error("Non-retryable delivery failure for destination={}: {}",
                    dest.name(), e.getMessage(), e);
            } catch (Exception e) {
                failed.add(dest.name());
                failureDetails.put(dest.name(), new DeliveryFailure(
                    DeliveryFailureCategory.UNKNOWN,
                    e.getMessage()));
                log.error("Failed to deliver event type={} to destination={}: {}",
                    event.type(), dest.name(), e.getMessage(), e);
            }
        }

        return Promise.of(new DeliveryResult(delivered, failed, failureDetails));
    }

    private Map<String, Object> buildDeliveryPayload(
            String tenantId, AepEngine.Event event, List<AepEngine.Detection> detections) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("eventType", event.type());
        payload.put("eventVersion", event.version());
        payload.put("timestamp", event.timestamp().toString());
        payload.put("payload", event.payload());
        payload.put("detections", detections.stream().map(d -> Map.of(
            "patternId", d.patternId(),
            "patternName", d.patternName(),
            "confidence", d.confidence(),
            "detectedAt", d.detectedAt().toString()
        )).toList());
        event.identityContext().stitchedId()
            .ifPresent(id -> payload.put("stitchedId", id));
        return payload;
    }

    // ==================== Supporting Types ====================

    /**
     * Functional interface for sending a pre-serialized event payload to an external system.
     * Implementations live in {@code platform:java:messaging}; {@code aep-engine} only depends on this
     * abstraction so the module graph stays acyclic.
     */
    @FunctionalInterface
    public interface MessageSender {
        /**
         * Send the event to the underlying transport.
         *
         * @param tenantId    owning tenant
         * @param eventType   event type discriminator
         * @param payloadJson JSON-serialized delivery payload
         * @param headers     transport-level headers
         * @return {@code true} if the message was accepted, {@code false} on a soft failure
         */
        boolean send(String tenantId, String eventType, String payloadJson,
                     Map<String, String> headers);
    }

    /**
     * A named external destination backed by a {@link MessageSender}.
     *
     * @param name   human-readable destination identifier (for logging/metrics)
     * @param sender the sender to use for delivery
     */
    public record EventDestination(String name, MessageSender sender) {
        public EventDestination {
            Objects.requireNonNull(name, "destination name required");
            Objects.requireNonNull(sender, "sender required");
        }
    }

    /**
     * Summary of a delivery attempt across all destinations.
     *
     * @param delivered names of destinations that received the event successfully
     * @param failed    names of destinations where delivery failed
     * @param failureDetails failure category and message by destination
     */
    public record DeliveryResult(
        List<String> delivered,
        List<String> failed,
        Map<String, DeliveryFailure> failureDetails
    ) {
        public DeliveryResult {
            delivered = delivered != null ? List.copyOf(delivered) : List.of();
            failed    = failed    != null ? List.copyOf(failed)    : List.of();
            failureDetails = failureDetails != null ? Map.copyOf(failureDetails) : Map.of();
        }

        public DeliveryResult(List<String> delivered, List<String> failed) {
            this(delivered, failed, Map.of());
        }

        /** True when all destinations received the event. */
        public boolean isFullyDelivered() {
            return failed.isEmpty();
        }

        /** True when at least one destination failed. */
        public boolean hasFailures() {
            return !failed.isEmpty();
        }

        static DeliveryResult noDestinations() {
            return new DeliveryResult(List.of(), List.of(), Map.of());
        }
    }

    public enum DeliveryFailureCategory {
        RETRYABLE,
        NON_RETRYABLE,
        UNKNOWN
    }

    public record DeliveryFailure(DeliveryFailureCategory category, String message) {
        public DeliveryFailure {
            category = category != null ? category : DeliveryFailureCategory.UNKNOWN;
            message = message != null ? message : "delivery failure";
        }
    }
}
