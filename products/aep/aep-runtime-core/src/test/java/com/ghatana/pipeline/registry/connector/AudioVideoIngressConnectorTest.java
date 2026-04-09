package com.ghatana.pipeline.registry.connector;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for AudioVideoIngressConnector utilizing real Eventloop bindings
 * @doc.layer product
 * @doc.pattern Test
 */
class AudioVideoIngressConnectorTest extends EventloopTestBase {

    private AudioVideoIngressConnector connector;

    @BeforeEach
    void setUp() {
        ConnectorSpec spec = ConnectorSpec.builder()
                .id("av-connector-001")
                .type(ConnectorSpec.ConnectorType.HTTP_INGRESS)
                .tenantId("tenant-av")
                .build();

        connector = new AudioVideoIngressConnector(spec, Executors.newSingleThreadExecutor());
    }

    @Test
    void shouldProcessAudioVideoStreams() {
        // Initialize
        runPromise(() -> connector.initialize());

        // Assert unhealthy before connect
        assertFalse(runPromise(() -> connector.isHealthy()));

        // Cannot process stream when unhealthy
        assertThrows(IllegalStateException.class, () -> runPromise(() -> connector.processStream(new byte[100])));

        // Connect
        runPromise(() -> connector.connect());
        assertTrue(runPromise(() -> connector.isHealthy()));

        // Process Stream
        byte[] payload = "dummy-video-frame".getBytes();
        Event resultEvent = runPromise(() -> connector.processStream(payload));

        assertNotNull(resultEvent);
        assertEquals("tenant-av", resultEvent.getTenantId());
        assertEquals("audio.video.chunk.embedded", resultEvent.getType());

        // Disconnect
        runPromise(() -> connector.disconnect());
        assertFalse(runPromise(() -> connector.isHealthy()));
    }
}
