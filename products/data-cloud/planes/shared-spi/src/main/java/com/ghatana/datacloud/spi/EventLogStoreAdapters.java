package com.ghatana.datacloud.spi;

import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Migration adapters between legacy Data-Cloud SPI event-store contracts and
 * platform-owned event-store contracts.
 *
 * <p>The Data-Cloud SPI {@link EventLogStore} interface is now aligned with the
 * platform {@code com.ghatana.platform.domain.eventstore.EventLogStore} contract.
 * This adapter provides a type-safe bridge for code that needs to convert between
 * the two namespaces.
 *
 * @doc.type class
 * @doc.purpose Bridge between Data-Cloud SPI and platform event-store contracts
 * @doc.layer spi
 * @doc.pattern Adapter
 */
public final class EventLogStoreAdapters {

    private EventLogStoreAdapters() {
    }

    /**
     * Adapts a Data-Cloud SPI {@link EventLogStore} to the platform
     * {@code com.ghatana.platform.domain.eventstore.EventLogStore} interface.
     *
     * <p>This creates a wrapper that implements the platform interface and delegates
     * all calls to the Data-Cloud SPI implementation.
     *
     * @param dataCloudStore Data-Cloud SPI event log store
     * @return platform event log store interface
     */
    public static @NotNull com.ghatana.platform.domain.eventstore.EventLogStore toPlatformStore(@NotNull EventLogStore dataCloudStore) {
        return new PlatformEventLogStoreAdapter(dataCloudStore);
    }

    /**
     * Adapts a platform {@code com.ghatana.platform.domain.eventstore.EventLogStore}
     * to the Data-Cloud SPI {@link EventLogStore} interface.
     *
     * <p>This creates a wrapper that implements the SPI interface and delegates
     * all calls to the platform implementation.
     *
     * @param platformStore platform event log store
     * @return Data-Cloud SPI event log store interface
     */
    public static @NotNull EventLogStore fromPlatformStore(@NotNull com.ghatana.platform.domain.eventstore.EventLogStore platformStore) {
        return new SpiEventLogStoreAdapter(platformStore);
    }

    /**
     * Converts a Data-Cloud SPI TenantContext to a platform TenantContext.
     *
     * @param tenant Data-Cloud SPI tenant context
     * @return platform tenant context
     */
    public static com.ghatana.platform.domain.eventstore.TenantContext toPlatformTenantContext(@NotNull TenantContext tenant) {
        return new com.ghatana.platform.domain.eventstore.TenantContext(tenant.tenantId(), tenant.workspaceId(), tenant.metadata());
    }

    /**
     * Converts a platform TenantContext to a Data-Cloud SPI TenantContext.
     *
     * @param tenant platform tenant context
     * @return Data-Cloud SPI tenant context
     */
    public static TenantContext toDataCloudTenantContext(@NotNull com.ghatana.platform.domain.eventstore.TenantContext tenant) {
        return new TenantContext(tenant.tenantId(), tenant.workspaceId(), tenant.metadata());
    }

    /**
     * Converts a Data-Cloud SPI EventEntry to a platform EventEntry.
     *
     * @param entry Data-Cloud SPI event entry
     * @return platform event entry
     */
    public static com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry toPlatformEventEntry(@NotNull EventLogStore.EventEntry entry) {
        return new com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry(
            entry.eventId(),
            entry.eventType(),
            entry.eventVersion(),
            entry.timestamp(),
            entry.payload(),
            entry.contentType(),
            entry.headers(),
            entry.idempotencyKey()
        );
    }

    /**
     * Converts a platform EventEntry to a Data-Cloud SPI EventEntry.
     *
     * @param entry platform event entry
     * @return Data-Cloud SPI event entry
     */
    public static EventLogStore.EventEntry toDataCloudEventEntry(@NotNull com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry) {
        return new EventLogStore.EventEntry(
            entry.eventId(),
            entry.eventType(),
            entry.eventVersion(),
            entry.timestamp(),
            entry.payload(),
            entry.contentType(),
            entry.headers(),
            entry.idempotencyKey()
        );
    }

    /**
     * Adapter that wraps a Data-Cloud SPI EventLogStore and implements the platform interface.
     */
    private static class PlatformEventLogStoreAdapter implements com.ghatana.platform.domain.eventstore.EventLogStore {

        private final EventLogStore spiStore;

        PlatformEventLogStoreAdapter(EventLogStore spiStore) {
            this.spiStore = spiStore;
        }

        @Override
        public Promise<Offset> append(com.ghatana.platform.domain.eventstore.TenantContext tenant, com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry) {
            return spiStore.append(
                convertToSpiTenantContext(tenant),
                convertToSpiEventEntry(entry)
            );
        }

        @Override
        public Promise<List<Offset>> appendBatch(com.ghatana.platform.domain.eventstore.TenantContext tenant, List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> entries) {
            return spiStore.appendBatch(
                convertToSpiTenantContext(tenant),
                entries.stream().map(e -> convertToSpiEventEntry(e)).toList()
            );
        }

        @Override
        public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> read(com.ghatana.platform.domain.eventstore.TenantContext tenant, Offset from, int limit) {
            return spiStore.read(convertToSpiTenantContext(tenant), from, limit)
                .map(entries -> entries.stream().map(e -> convertToPlatformEventEntry(e)).toList());
        }

        @Override
        public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByTimeRange(
            com.ghatana.platform.domain.eventstore.TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit
        ) {
            return spiStore.readByTimeRange(convertToSpiTenantContext(tenant), startTime, endTime, limit)
                .map(entries -> entries.stream().map(e -> convertToPlatformEventEntry(e)).toList());
        }

        @Override
        public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByType(
            com.ghatana.platform.domain.eventstore.TenantContext tenant,
            String eventType,
            Offset from,
            int limit
        ) {
            return spiStore.readByType(convertToSpiTenantContext(tenant), eventType, from, limit)
                .map(entries -> entries.stream().map(e -> convertToPlatformEventEntry(e)).toList());
        }

        @Override
        public Promise<Offset> getLatestOffset(com.ghatana.platform.domain.eventstore.TenantContext tenant) {
            return spiStore.getLatestOffset(convertToSpiTenantContext(tenant));
        }

        @Override
        public Promise<Offset> getEarliestOffset(com.ghatana.platform.domain.eventstore.TenantContext tenant) {
            return spiStore.getEarliestOffset(convertToSpiTenantContext(tenant));
        }

        @Override
        public Promise<com.ghatana.platform.domain.eventstore.EventLogStore.Subscription> tail(com.ghatana.platform.domain.eventstore.TenantContext tenant, Offset from, java.util.function.Consumer<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> handler) {
            return spiStore.tail(convertToSpiTenantContext(tenant), from, spiEntry -> {
                handler.accept(convertToPlatformEventEntry(spiEntry));
            }).map(s -> convertSubscription(s));
        }

        private static TenantContext convertToSpiTenantContext(com.ghatana.platform.domain.eventstore.TenantContext tenant) {
            return new TenantContext(tenant.tenantId(), tenant.workspaceId(), tenant.metadata());
        }

        private static EventLogStore.EventEntry convertToSpiEventEntry(com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry) {
            return new EventLogStore.EventEntry(
                entry.eventId(),
                entry.eventType(),
                entry.eventVersion(),
                entry.timestamp(),
                entry.payload(),
                entry.contentType(),
                entry.headers(),
                entry.idempotencyKey()
            );
        }

        private static com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry convertToPlatformEventEntry(EventLogStore.EventEntry entry) {
            return new com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry(
                entry.eventId(),
                entry.eventType(),
                entry.eventVersion(),
                entry.timestamp(),
                entry.payload(),
                entry.contentType(),
                entry.headers(),
                entry.idempotencyKey()
            );
        }

        private static com.ghatana.platform.domain.eventstore.EventLogStore.Subscription convertSubscription(EventLogStore.Subscription subscription) {
            return new com.ghatana.platform.domain.eventstore.EventLogStore.Subscription() {
                @Override
                public void cancel() {
                    subscription.cancel();
                }

                @Override
                public boolean isCancelled() {
                    return subscription.isCancelled();
                }
            };
        }
    }

    /**
     * Adapter that wraps a platform EventLogStore and implements the SPI interface.
     */
    private static class SpiEventLogStoreAdapter implements EventLogStore {

        private final com.ghatana.platform.domain.eventstore.EventLogStore platformStore;

        SpiEventLogStoreAdapter(com.ghatana.platform.domain.eventstore.EventLogStore platformStore) {
            this.platformStore = platformStore;
        }

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
            return platformStore.append(
                convertToPlatformTenantContext(tenant),
                convertToPlatformEventEntry(entry)
            );
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
            return platformStore.appendBatch(
                convertToPlatformTenantContext(tenant),
                entries.stream().map(e -> convertToPlatformEventEntry(e)).toList()
            );
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            return platformStore.read(convertToPlatformTenantContext(tenant), from, limit)
                .map(entries -> entries.stream().map(e -> convertToSpiEventEntry(e)).toList());
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(
            TenantContext tenant,
            Instant startTime,
            Instant endTime,
            int limit
        ) {
            return platformStore.readByTimeRange(convertToPlatformTenantContext(tenant), startTime, endTime, limit)
                .map(entries -> entries.stream().map(e -> convertToSpiEventEntry(e)).toList());
        }

        @Override
        public Promise<List<EventEntry>> readByType(
            TenantContext tenant,
            String eventType,
            Offset from,
            int limit
        ) {
            return platformStore.readByType(convertToPlatformTenantContext(tenant), eventType, from, limit)
                .map(entries -> entries.stream().map(e -> convertToSpiEventEntry(e)).toList());
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            return platformStore.getLatestOffset(convertToPlatformTenantContext(tenant));
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return platformStore.getEarliestOffset(convertToPlatformTenantContext(tenant));
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            return platformStore.tail(convertToPlatformTenantContext(tenant), from, platformEntry -> {
                handler.accept(convertToSpiEventEntry(platformEntry));
            }).map(s -> convertSubscription(s));
        }

        private static com.ghatana.platform.domain.eventstore.TenantContext convertToPlatformTenantContext(TenantContext tenant) {
            return new com.ghatana.platform.domain.eventstore.TenantContext(tenant.tenantId(), tenant.workspaceId(), tenant.metadata());
        }

        private static com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry convertToPlatformEventEntry(EventEntry entry) {
            return new com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry(
                entry.eventId(),
                entry.eventType(),
                entry.eventVersion(),
                entry.timestamp(),
                entry.payload(),
                entry.contentType(),
                entry.headers(),
                entry.idempotencyKey()
            );
        }

        private static EventEntry convertToSpiEventEntry(com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry) {
            return new EventEntry(
                entry.eventId(),
                entry.eventType(),
                entry.eventVersion(),
                entry.timestamp(),
                entry.payload(),
                entry.contentType(),
                entry.headers(),
                entry.idempotencyKey()
            );
        }

        private static Subscription convertSubscription(com.ghatana.platform.domain.eventstore.EventLogStore.Subscription subscription) {
            return new Subscription() {
                @Override
                public void cancel() {
                    subscription.cancel();
                }

                @Override
                public boolean isCancelled() {
                    return subscription.isCancelled();
                }
            };
        }
    }
}
