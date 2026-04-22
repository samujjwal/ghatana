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
@DisplayName("CacheInvalidationEvent Tests [GH-90000]")
class CacheInvalidationEventTest {

    @Test
    @DisplayName("ofKey creates single-key invalidation event [GH-90000]")
    void ofKeyCreatesSingleKeyEvent() { // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("finance.risk", "trader-1"); // GH-90000
        assertThat(event.namespace()).isEqualTo("finance.risk [GH-90000]");
        assertThat(event.key()).isEqualTo("trader-1 [GH-90000]");
        assertThat(event.invalidateAll()).isFalse(); // GH-90000
        assertThat(event.triggeredAt()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("ofNamespace creates full-namespace invalidation event [GH-90000]")
    void ofNamespaceCreatesFullNamespaceEvent() { // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofNamespace("phr.consent [GH-90000]");
        assertThat(event.namespace()).isEqualTo("phr.consent [GH-90000]");
        assertThat(event.key()).isNull(); // GH-90000
        assertThat(event.invalidateAll()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("EVENT_TYPE constant is stable [GH-90000]")
    void eventTypeConstantIsStable() { // GH-90000
        assertThat(CacheInvalidationEvent.EVENT_TYPE).isEqualTo("platform.cache.invalidation.v1 [GH-90000]");
    }

    @Test
    @DisplayName("constructor rejects null namespace [GH-90000]")
    void constructorRejectsNullNamespace() { // GH-90000
        assertThatThrownBy(() -> new CacheInvalidationEvent(null, "key", false, Instant.now())) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects null triggeredAt [GH-90000]")
    void constructorRejectsNullTriggeredAt() { // GH-90000
        assertThatThrownBy(() -> new CacheInvalidationEvent("ns", "key", false, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("triggeredAt is recent for factory methods [GH-90000]")
    void triggeredAtIsRecentForFactoryMethods() { // GH-90000
        Instant before = Instant.now(); // GH-90000
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("ns", "k"); // GH-90000
        Instant after = Instant.now(); // GH-90000
        assertThat(event.triggeredAt()).isBetween(before, after); // GH-90000
    }
}
