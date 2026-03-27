package com.ghatana.aep.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TTL-based eviction in {@link InMemoryEventCloud}.
 *
 * @doc.type class
 * @doc.purpose Verify automatic TTL enforcement (Risk ICR-003 remediation)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryEventCloud TTL enforcement")
class InMemoryEventCloudTtlTest {

    private static final String TENANT = "tenant-1";

    @Test
    @DisplayName("events survive when TTL has not elapsed")
    void shouldRetainEventsWithinTtl() {
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofHours(1));

        cloud.append(TENANT, "user.click", "{}".getBytes());
        cloud.append(TENANT, "user.click", "{}".getBytes());

        assertThat(cloud.getEvents(TENANT)).hasSize(2);
    }

    @Test
    @DisplayName("purgeExpired() with zero TTL removes nothing")
    void zeroTtlShouldRemoveNothing() {
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ZERO);

        cloud.append(TENANT, "page.view", "{}".getBytes());
        int removed = cloud.purgeExpired();

        assertThat(removed).isZero();
        assertThat(cloud.getEvents(TENANT)).hasSize(1);
    }

    @Test
    @DisplayName("purgeExpired() removes events older than TTL")
    void shouldEvictExpiredEvents() throws InterruptedException {
        // Use a very short TTL so we can test eviction without waiting long
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofMillis(50));

        cloud.append(TENANT, "order.placed", "{}".getBytes());
        assertThat(cloud.getEvents(TENANT)).hasSize(1);

        // Wait for TTL to elapse
        Thread.sleep(60);

        int removed = cloud.purgeExpired();
        assertThat(removed).isEqualTo(1);
        assertThat(cloud.getEvents(TENANT)).isEmpty();
    }

    @Test
    @DisplayName("append() triggers automatic purge when TTL is configured")
    void appendShouldAutoPurgeExpiredEvents() throws InterruptedException {
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofMillis(50));

        // Append first event and let it expire
        cloud.append(TENANT, "order.placed", "{}".getBytes());
        Thread.sleep(60);

        // Second append triggers auto-purge of the first event
        cloud.append(TENANT, "order.shipped", "{}".getBytes());

        assertThat(cloud.getEvents(TENANT)).hasSize(1);
        assertThat(cloud.getEvents(TENANT).get(0).eventType()).isEqualTo("order.shipped");
    }

    @Test
    @DisplayName("no TTL instance never evicts events")
    void noTtlShouldNeverEvict() {
        InMemoryEventCloud cloud = new InMemoryEventCloud();

        cloud.append(TENANT, "session.start", "{}".getBytes());
        cloud.append(TENANT, "session.end", "{}".getBytes());

        int removed = cloud.purgeExpired();
        assertThat(removed).isZero();
        assertThat(cloud.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("size() returns total event count across all tenants")
    void sizeShouldCountAllTenants() {
        InMemoryEventCloud cloud = new InMemoryEventCloud();

        cloud.append("tenant-a", "event.a", "{}".getBytes());
        cloud.append("tenant-b", "event.b", "{}".getBytes());
        cloud.append("tenant-b", "event.c", "{}".getBytes());

        assertThat(cloud.size()).isEqualTo(3);
    }
}
