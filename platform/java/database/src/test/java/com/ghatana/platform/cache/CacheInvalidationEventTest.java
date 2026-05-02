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
    void ofKeyCreatesSingleKeyEvent() { 
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("finance.risk", "trader-1"); 
        assertThat(event.namespace()).isEqualTo("finance.risk");
        assertThat(event.key()).isEqualTo("trader-1");
        assertThat(event.invalidateAll()).isFalse(); 
        assertThat(event.triggeredAt()).isNotNull(); 
    }

    @Test
    @DisplayName("ofNamespace creates full-namespace invalidation event")
    void ofNamespaceCreatesFullNamespaceEvent() { 
        CacheInvalidationEvent event = CacheInvalidationEvent.ofNamespace("phr.consent");
        assertThat(event.namespace()).isEqualTo("phr.consent");
        assertThat(event.key()).isNull(); 
        assertThat(event.invalidateAll()).isTrue(); 
    }

    @Test
    @DisplayName("EVENT_TYPE constant is stable")
    void eventTypeConstantIsStable() { 
        assertThat(CacheInvalidationEvent.EVENT_TYPE).isEqualTo("platform.cache.invalidation.v1");
    }

    @Test
    @DisplayName("constructor rejects null namespace")
    void constructorRejectsNullNamespace() { 
        assertThatThrownBy(() -> new CacheInvalidationEvent(null, "key", false, Instant.now())) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("constructor rejects null triggeredAt")
    void constructorRejectsNullTriggeredAt() { 
        assertThatThrownBy(() -> new CacheInvalidationEvent("ns", "key", false, null)) 
                .isInstanceOf(NullPointerException.class); 
    }

    @Test
    @DisplayName("triggeredAt is recent for factory methods")
    void triggeredAtIsRecentForFactoryMethods() { 
        Instant before = Instant.now(); 
        CacheInvalidationEvent event = CacheInvalidationEvent.ofKey("ns", "k"); 
        Instant after = Instant.now(); 
        assertThat(event.triggeredAt()).isBetween(before, after); 
    }
}
