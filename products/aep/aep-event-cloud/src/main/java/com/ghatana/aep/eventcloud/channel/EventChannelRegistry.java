/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.channel;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry of named {@link EventChannel}s backed by Data-Cloud's {@link EventLogStore}.
 *
 * <p>Manages channel lifecycle and provides pub/sub operations routed through
 * the underlying event log. Each channel maps to a set of event types in the
 * store, enabling tenant-isolated routing.
 *
 * <p>This is the AEP-side abstraction for event pipes and channels. All event
 * routing in AEP flows through registered channels rather than directly
 * accessing the event log store.
 *
 * @doc.type class
 * @doc.purpose Event channel registry and pub/sub router
 * @doc.layer product
 * @doc.pattern Registry, Mediator
 */
public final class EventChannelRegistry {

    private static final Logger log = LoggerFactory.getLogger(EventChannelRegistry.class);

    private final EventLogStore eventLogStore;
    private final Map<String, EventChannel> channels = new ConcurrentHashMap<>();

    public EventChannelRegistry(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
    }

    /**
     * Registers a channel. Idempotent — re-registering an existing channel is a no-op.
     *
     * @param channel the channel to register
     */
    public void registerChannel(EventChannel channel) {
        Objects.requireNonNull(channel, "channel required");
        channels.putIfAbsent(channel.name(), channel);
        log.debug("[channel-registry] Registered channel: {}", channel.name());
    }

    /**
     * Returns a registered channel by name.
     *
     * @param name channel name
     * @return the channel, or empty if not registered
     */
    public Optional<EventChannel> getChannel(String name) {
        return Optional.ofNullable(channels.get(name));
    }

    /**
     * Returns all registered channels.
     */
    public List<EventChannel> listChannels() {
        return List.copyOf(channels.values());
    }

    /**
     * Returns the number of registered channels.
     */
    public int channelCount() {
        return channels.size();
    }

    /**
     * Publishes an event to a named channel.
     *
     * @param channelName channel name (must be registered)
     * @param tenantId    tenant identifier
     * @param eventType   specific event type within the channel
     * @param payload     event payload bytes
     * @return promise of the event ID
     * @throws IllegalArgumentException if the channel is not registered
     */
    public Promise<String> publish(
            String channelName,
            String tenantId,
            String eventType,
            byte[] payload) {
        EventChannel channel = channels.get(channelName);
        if (channel == null) {
            return Promise.ofException(
                new IllegalArgumentException("Channel not registered: " + channelName));
        }

        String qualifiedType = channel.eventTypePrefix() + "." + eventType;
        UUID eventId = UUID.randomUUID();

        EventEntry entry = EventEntry.builder()
            .eventId(eventId)
            .eventType(qualifiedType)
            .payload(ByteBuffer.wrap(payload))
            .headers(Map.of("channel", channelName))
            .build();

        return eventLogStore.append(TenantContext.of(tenantId), entry)
            .map(offset -> {
                log.debug("[channel-registry] Published to channel={} type={} tenant={} offset={}",
                    channelName, qualifiedType, tenantId, offset);
                return eventId.toString();
            });
    }

    /**
     * Subscribes to events on a named channel.
     *
     * @param channelName channel name (must be registered)
     * @param tenantId    tenant identifier
     * @param handler     callback for each received event entry
     * @return promise of a subscription handle
     * @throws IllegalArgumentException if the channel is not registered
     */
    public Promise<EventLogStore.Subscription> subscribe(
            String channelName,
            String tenantId,
            Consumer<EventEntry> handler) {
        EventChannel channel = channels.get(channelName);
        if (channel == null) {
            return Promise.ofException(
                new IllegalArgumentException("Channel not registered: " + channelName));
        }

        TenantContext tenant = TenantContext.of(tenantId);
        String prefix = channel.eventTypePrefix();

        return eventLogStore.getLatestOffset(tenant)
            .then(latestOffset ->
                eventLogStore.tail(tenant, latestOffset, entry -> {
                    if (entry.eventType().startsWith(prefix)) {
                        handler.accept(entry);
                    }
                }));
    }

    /**
     * Removes a channel from the registry.
     *
     * @param channelName channel name
     * @return true if the channel was removed
     */
    public boolean removeChannel(String channelName) {
        EventChannel removed = channels.remove(channelName);
        if (removed != null) {
            log.debug("[channel-registry] Removed channel: {}", channelName);
        }
        return removed != null;
    }

    /**
     * Closes the registry and clears all channels.
     */
    public void close() {
        channels.clear();
        log.debug("[channel-registry] All channels cleared");
    }
}
