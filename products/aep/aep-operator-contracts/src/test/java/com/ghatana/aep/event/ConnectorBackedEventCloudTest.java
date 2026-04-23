package com.ghatana.aep.event;

import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConnectorBackedEventCloud")
@ExtendWith(MockitoExtension.class) // GH-90000
class ConnectorBackedEventCloudTest extends EventloopTestBase {

    @Mock
    private EventCloudConnector connector;

    @Captor
    private ArgumentCaptor<EventCloudConnector.EventPayloadHandler> handlerCaptor;

    private ConnectorBackedEventCloud eventCloud;

    @BeforeEach
    void setUp() { // GH-90000
        eventCloud = new ConnectorBackedEventCloud(connector); // GH-90000
    }

    @Test
    void appendReturnsGeneratedEventIdImmediately() { // GH-90000
        SettablePromise<String> publishPromise = new SettablePromise<>(); // GH-90000
        when(connector.publish(eq("order.created"), any(byte[].class))).thenReturn(publishPromise);

        String eventId = eventCloud.append( // GH-90000
            "tenant-1",
            "order.created",
            "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8)); // GH-90000

        assertThat(eventId).matches( // GH-90000
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        verify(connector).publish(eq("order.created"), any(byte[].class));

        publishPromise.set("remote-42");
    }

    @Test
    void subscribeForwardsConnectorEventsToHandler() { // GH-90000
        AtomicReference<String> eventIdRef = new AtomicReference<>(); // GH-90000
        AtomicReference<String> topicRef = new AtomicReference<>(); // GH-90000
        AtomicReference<byte[]> payloadRef = new AtomicReference<>(); // GH-90000

        when(connector.subscribe(eq("order.created"), eq("aep-default"), handlerCaptor.capture()))
            .thenReturn(Promise.of(new EventCloudConnector.ConnectorSubscription() { // GH-90000
                @Override
                public void cancel() { // GH-90000
                }

                @Override
                public boolean isCancelled() { // GH-90000
                    return false;
                }
            }));

        eventCloud.subscribe("tenant-1", "order.created", (eventId, eventType, payload) -> { // GH-90000
            eventIdRef.set(eventId); // GH-90000
            topicRef.set(eventType); // GH-90000
            payloadRef.set(payload); // GH-90000
        });

        handlerCaptor.getValue().onEvent("evt-1", "order.created", "payload".getBytes(StandardCharsets.UTF_8)); // GH-90000

        assertThat(eventIdRef.get()).isEqualTo("evt-1");
        assertThat(topicRef.get()).isEqualTo("order.created");
        assertThat(new String(payloadRef.get(), StandardCharsets.UTF_8)).isEqualTo("payload");
    }

    @Test
    void cancelBeforeAsyncSubscriptionResolvesCancelsDelegateLater() { // GH-90000
        SettablePromise<EventCloudConnector.ConnectorSubscription> subscribePromise = new SettablePromise<>(); // GH-90000
        EventCloudConnector.ConnectorSubscription delegate = mock(EventCloudConnector.ConnectorSubscription.class); // GH-90000
        when(connector.subscribe(eq("order.created"), eq("aep-default"), any()))
            .thenReturn(subscribePromise); // GH-90000

        EventCloud.Subscription subscription = eventCloud.subscribe( // GH-90000
            "tenant-1",
            "order.created",
            (eventId, eventType, payload) -> { // GH-90000
            });

        subscription.cancel(); // GH-90000
        assertThat(subscription.isCancelled()).isTrue(); // GH-90000

        subscribePromise.set(delegate); // GH-90000

        verify(delegate).cancel(); // GH-90000
    }
}
