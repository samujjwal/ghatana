package com.ghatana.platform.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CacheInvalidationEvent}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CacheInvalidationEvent value object
 * @doc.layer platform
 * @doc.pattern TestClass
 */
@DisplayName("CacheInvalidationEvent Tests")
class CacheInvalidationEventTest {

    @Test
    @DisplayName("ofKey creates single-key invalidation event")
    void ofKeyCreatesSingleKeyEvent() { // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("finance.risk", "trader-1"); // GH-90000
        assertThat(event.namespace()).isEqualTo("finance.risk");
        assertThat(event.key()).isEqualTo("trader-1");
        assertThat(event.invalidateAll()).isFalse(); // GH-90000
        assertThat(event.triggeredAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("ofNamespace creates full-namespace invalidation event")
    void ofNamespaceCreatesFullNamespaceEvent() { // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofNamespace("phr.consent");
        assertThat(event.namespace()).isEqualTo("phr.consent");
        assertThat(event.key()).isNull(); // GH-90000
        assertThat(event.invalidateAll()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("EVENT_TYPE constant is stable")
    void eventTypeConstantIsStable() { // GH-90000
        assertThat(CacheInvalidationEvent.EVENT_TYPE).isEqualTo("platform.cache.invalidation.v1");
    }

    @Test
    @DisplayName("constructor rejects null namespace")
    void constructorRejectsNullNamespace() { // GH-90000
        assertThatThrownBy(() -> new CacheInvalidationEvent(null, "key", false, Instant.now())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null triggeredAt")
    void constructorRejectsNullTriggeredAt() { // GH-90000
        assertThatThrownBy(() -> new CacheInvalidationEvent("ns", "key", false, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("triggeredAt is recent for factory methods")
    void triggeredAtIsRecentForFactoryMethods() { // GH-90000
        Instant before = Instant.now(); // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("ns", "k"); // GH-90000
        Instant after = Instant.now(); // GH-90000
        assertThat(event.triggeredAt()).isBetween(before, after); // GH-90000
    }
}
