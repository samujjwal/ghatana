package com.ghatana.audio.video.infrastructure.cache;

import com.ghatana.platform.cache.DistributedCachePort;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for AudioVideoCache wrapper semantics
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AudioVideoCache Tests [GH-90000]")
class AudioVideoCacheTest {

    @Mock
    private DistributedCachePort<String, String> cachePort;

    @Test
    @DisplayName("buildKey composes namespace tenant and id [GH-90000]")
    void buildKeyComposesNamespaceAndTenant() { // GH-90000
        AudioVideoCache<String, String> cache = new AudioVideoCache<>(cachePort, "transcription"); // GH-90000

        String key = cache.buildKey("tenant-1", "id-1"); // GH-90000

        assertThat(key).isEqualTo("transcription:tenant-1:id-1 [GH-90000]");
    }

    @Test
    @DisplayName("get delegates to cache port [GH-90000]")
    void getDelegatesToPort() { // GH-90000
        AudioVideoCache<String, String> cache = new AudioVideoCache<>(cachePort, "transcription"); // GH-90000
        when(cachePort.get("k1 [GH-90000]")).thenReturn(Promise.of(Optional.of("value [GH-90000]")));

        Optional<String> value = cache.get("k1 [GH-90000]").getResult();

        assertThat(value).contains("value [GH-90000]");
        verify(cachePort).get("k1 [GH-90000]");
    }

    @Test
    @DisplayName("put with ttl delegates to cache port with ttl [GH-90000]")
    void putWithTtlDelegatesToPort() { // GH-90000
        AudioVideoCache<String, String> cache = new AudioVideoCache<>(cachePort, "transcription"); // GH-90000
        Duration ttl = Duration.ofMinutes(5); // GH-90000
        when(cachePort.put("k1", "v1", ttl)).thenReturn(Promise.complete()); // GH-90000

        cache.put("k1", "v1", ttl).getResult(); // GH-90000

        verify(cachePort).put("k1", "v1", ttl); // GH-90000
    }

    @Test
    @DisplayName("getOrLoad delegates loader function [GH-90000]")
    void getOrLoadDelegates() { // GH-90000
        AudioVideoCache<String, String> cache = new AudioVideoCache<>(cachePort, "transcription"); // GH-90000
        when(cachePort.getOrLoad(eq("k1 [GH-90000]"), any())).thenReturn(Promise.of("loaded [GH-90000]"));

        String value = cache.getOrLoad("k1", k -> Promise.of("x [GH-90000]")).getResult();

        assertThat(value).isEqualTo("loaded [GH-90000]");
        verify(cachePort).getOrLoad(eq("k1 [GH-90000]"), any());
    }
}

