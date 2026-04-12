package com.ghatana.datacloud.spi;

import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Migration adapters between legacy Data-Cloud SPI event-store contracts and
 * platform-owned event-store contracts.
 *
 * <p>This bridge enables incremental migration of consumers to
 * {@code com.ghatana.platform.domain.eventstore} without a flag day.
 *
 * @doc.type class
 * @doc.purpose Bridge between Data-Cloud SPI and platform event-store contracts
 * @doc.layer spi
 * @doc.pattern Adapter
 */
public final class EventLogStoreAdapters {

    private EventLogStoreAdapters() {
    }

    public static com.ghatana.platform.domain.eventstore.TenantContext toPlatformTenantContext(
        TenantContext tenant
    ) {
        return new com.ghatana.platform.domain.eventstore.TenantContext(
            tenant.tenantId(),
            tenant.workspaceId(),
            tenant.metadata()
        );
    }

    public static TenantContext toDataCloudTenantContext(
        com.ghatana.platform.domain.eventstore.TenantContext tenant
    ) {
        return new TenantContext(
            tenant.tenantId(),
            tenant.workspaceId(),
            tenant.metadata()
        );
    }

    public static com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry toPlatformEventEntry(
        EventLogStore.EventEntry entry
    ) {
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

    public static EventLogStore.EventEntry toDataCloudEventEntry(
        com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry
    ) {
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

    public static com.ghatana.platform.domain.eventstore.EventLogStore toPlatformStore(EventLogStore legacy) {
        return new com.ghatana.platform.domain.eventstore.EventLogStore() {
            @Override
            public Promise<Offset> append(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry entry
            ) {
                return legacy.append(toDataCloudTenantContext(tenant), toDataCloudEventEntry(entry));
            }

            @Override
            public Promise<List<Offset>> appendBatch(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> entries
            ) {
                final List<EventLogStore.EventEntry> legacyEntries = entries.stream()
                    .map(EventLogStoreAdapters::toDataCloudEventEntry)
                    .toList();
                return legacy.appendBatch(toDataCloudTenantContext(tenant), legacyEntries);
            }

            @Override
            public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> read(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                Offset from,
                int limit
            ) {
                return legacy.read(toDataCloudTenantContext(tenant), from, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toPlatformEventEntry).toList());
            }

            @Override
            public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByTimeRange(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                Instant startTime,
                Instant endTime,
                int limit
            ) {
                return legacy.readByTimeRange(toDataCloudTenantContext(tenant), startTime, endTime, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toPlatformEventEntry).toList());
            }

            @Override
            public Promise<List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry>> readByType(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                String eventType,
                Offset from,
                int limit
            ) {
                return legacy.readByType(toDataCloudTenantContext(tenant), eventType, from, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toPlatformEventEntry).toList());
            }

            @Override
            public Promise<Offset> getLatestOffset(com.ghatana.platform.domain.eventstore.TenantContext tenant) {
                return legacy.getLatestOffset(toDataCloudTenantContext(tenant));
            }

            @Override
            public Promise<Offset> getEarliestOffset(com.ghatana.platform.domain.eventstore.TenantContext tenant) {
                return legacy.getEarliestOffset(toDataCloudTenantContext(tenant));
            }

            @Override
            public Promise<Subscription> tail(
                com.ghatana.platform.domain.eventstore.TenantContext tenant,
                Offset from,
                Consumer<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> handler
            ) {
                return legacy.tail(
                        toDataCloudTenantContext(tenant),
                        from,
                        event -> handler.accept(toPlatformEventEntry(event))
                    )
                    .map(legacySubscription -> new Subscription() {
                        @Override
                        public void cancel() {
                            legacySubscription.cancel();
                        }

                        @Override
                        public boolean isCancelled() {
                            return legacySubscription.isCancelled();
                        }
                    });
            }
        };
    }

    public static EventLogStore toDataCloudStore(com.ghatana.platform.domain.eventstore.EventLogStore platformStore) {
        return new EventLogStore() {
            @Override
            public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
                return platformStore.append(toPlatformTenantContext(tenant), toPlatformEventEntry(entry));
            }

            @Override
            public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
                final List<com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry> platformEntries = entries.stream()
                    .map(EventLogStoreAdapters::toPlatformEventEntry)
                    .toList();
                return platformStore.appendBatch(toPlatformTenantContext(tenant), platformEntries);
            }

            @Override
            public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
                return platformStore.read(toPlatformTenantContext(tenant), from, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toDataCloudEventEntry).toList());
            }

            @Override
            public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, Instant startTime, Instant endTime, int limit) {
                return platformStore.readByTimeRange(toPlatformTenantContext(tenant), startTime, endTime, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toDataCloudEventEntry).toList());
            }

            @Override
            public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
                return platformStore.readByType(toPlatformTenantContext(tenant), eventType, from, limit)
                    .map(entries -> entries.stream().map(EventLogStoreAdapters::toDataCloudEventEntry).toList());
            }

            @Override
            public Promise<Offset> getLatestOffset(TenantContext tenant) {
                return platformStore.getLatestOffset(toPlatformTenantContext(tenant));
            }

            @Override
            public Promise<Offset> getEarliestOffset(TenantContext tenant) {
                return platformStore.getEarliestOffset(toPlatformTenantContext(tenant));
            }

            @Override
            public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
                return platformStore.tail(
                        toPlatformTenantContext(tenant),
                        from,
                        event -> handler.accept(toDataCloudEventEntry(event))
                    )
                    .map(platformSubscription -> new Subscription() {
                        @Override
                        public void cancel() {
                            platformSubscription.cancel();
                        }

                        @Override
                        public boolean isCancelled() {
                            return platformSubscription.isCancelled();
                        }
                    });
            }
        };
    }
}
