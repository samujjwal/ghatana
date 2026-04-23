package com.ghatana.aep.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TTL-based eviction in {@link InMemoryEventCloud}.
 *
 * @doc.type class
 * @doc.purpose Verify automatic TTL enforcement (Risk ICR-003 remediation) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryEventCloud TTL enforcement")
class InMemoryEventCloudTtlTest {

    private static final String TENANT = "tenant-1";

    @Test
    @DisplayName("events survive when TTL has not elapsed")
    void shouldRetainEventsWithinTtl() { // GH-90000
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofHours(1)); // GH-90000

        cloud.append(TENANT, "user.click", "{}".getBytes()); // GH-90000
        cloud.append(TENANT, "user.click", "{}".getBytes()); // GH-90000

        assertThat(cloud.getEvents(TENANT)).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("purgeExpired() with zero TTL removes nothing")
    void zeroTtlShouldRemoveNothing() { // GH-90000
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ZERO); // GH-90000

        cloud.append(TENANT, "page.view", "{}".getBytes()); // GH-90000
        int removed = cloud.purgeExpired(); // GH-90000

        assertThat(removed).isZero(); // GH-90000
        assertThat(cloud.getEvents(TENANT)).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("purgeExpired() removes events older than TTL")
    void shouldEvictExpiredEvents() throws InterruptedException { // GH-90000
        // Use a very short TTL so we can test eviction without waiting long
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofMillis(50)); // GH-90000

        cloud.append(TENANT, "order.placed", "{}".getBytes()); // GH-90000
        assertThat(cloud.getEvents(TENANT)).hasSize(1); // GH-90000

        // Wait for TTL to elapse
        Thread.sleep(60); // GH-90000

        int removed = cloud.purgeExpired(); // GH-90000
        assertThat(removed).isEqualTo(1); // GH-90000
        assertThat(cloud.getEvents(TENANT)).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("append() triggers automatic purge when TTL is configured")
    void appendShouldAutoPurgeExpiredEvents() throws InterruptedException { // GH-90000
        InMemoryEventCloud cloud = new InMemoryEventCloud(Duration.ofMillis(50)); // GH-90000

        // Append first event and let it expire
        cloud.append(TENANT, "order.placed", "{}".getBytes()); // GH-90000
        Thread.sleep(60); // GH-90000

        // Second append triggers auto-purge of the first event
        cloud.append(TENANT, "order.shipped", "{}".getBytes()); // GH-90000

        assertThat(cloud.getEvents(TENANT)).hasSize(1); // GH-90000
        assertThat(cloud.getEvents(TENANT).get(0).eventType()).isEqualTo("order.shipped");
    }

    @Test
    @DisplayName("no TTL instance never evicts events")
    void noTtlShouldNeverEvict() { // GH-90000
        InMemoryEventCloud cloud = new InMemoryEventCloud(); // GH-90000

        cloud.append(TENANT, "session.start", "{}".getBytes()); // GH-90000
        cloud.append(TENANT, "session.end", "{}".getBytes()); // GH-90000

        int removed = cloud.purgeExpired(); // GH-90000
        assertThat(removed).isZero(); // GH-90000
        assertThat(cloud.size()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("size() returns total event count across all tenants")
    void sizeShouldCountAllTenants() { // GH-90000
        InMemoryEventCloud cloud = new InMemoryEventCloud(); // GH-90000

        cloud.append("tenant-a", "event.a", "{}".getBytes()); // GH-90000
        cloud.append("tenant-b", "event.b", "{}".getBytes()); // GH-90000
        cloud.append("tenant-b", "event.c", "{}".getBytes()); // GH-90000

        assertThat(cloud.size()).isEqualTo(3); // GH-90000
    }
}
