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
@ExtendWith(MockitoExtension.class)
class ConnectorBackedEventCloudTest extends EventloopTestBase {

    @Mock
    private EventCloudConnector connector;

    @Captor
    private ArgumentCaptor<EventCloudConnector.EventPayloadHandler> handlerCaptor;

    private ConnectorBackedEventCloud eventCloud;

    @BeforeEach
    void setUp() {
        eventCloud = new ConnectorBackedEventCloud(connector);
    }

    @Test
    void appendReturnsGeneratedEventIdImmediately() {
        SettablePromise<String> publishPromise = new SettablePromise<>();
        when(connector.publish(eq("order.created"), any(byte[].class))).thenReturn(publishPromise);

        String eventId = eventCloud.append(
            "tenant-1",
            "order.created",
            "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(eventId).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        verify(connector).publish(eq("order.created"), any(byte[].class));

        publishPromise.set("remote-42");
    }

    @Test
    void subscribeForwardsConnectorEventsToHandler() {
        AtomicReference<String> eventIdRef = new AtomicReference<>();
        AtomicReference<String> topicRef = new AtomicReference<>();
        AtomicReference<byte[]> payloadRef = new AtomicReference<>();

        when(connector.subscribe(eq("order.created"), eq("aep-default"), handlerCaptor.capture()))
            .thenReturn(Promise.of(new EventCloudConnector.ConnectorSubscription() {
                @Override
                public void cancel() {
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            }));

        eventCloud.subscribe("tenant-1", "order.created", (eventId, eventType, payload) -> {
            eventIdRef.set(eventId);
            topicRef.set(eventType);
            payloadRef.set(payload);
        });

        handlerCaptor.getValue().onEvent("evt-1", "order.created", "payload".getBytes(StandardCharsets.UTF_8));

        assertThat(eventIdRef.get()).isEqualTo("evt-1");
        assertThat(topicRef.get()).isEqualTo("order.created");
        assertThat(new String(payloadRef.get(), StandardCharsets.UTF_8)).isEqualTo("payload");
    }

    @Test
    void cancelBeforeAsyncSubscriptionResolvesCancelsDelegateLater() {
        SettablePromise<EventCloudConnector.ConnectorSubscription> subscribePromise = new SettablePromise<>();
        EventCloudConnector.ConnectorSubscription delegate = mock(EventCloudConnector.ConnectorSubscription.class);
        when(connector.subscribe(eq("order.created"), eq("aep-default"), any()))
            .thenReturn(subscribePromise);

        EventCloud.Subscription subscription = eventCloud.subscribe(
            "tenant-1",
            "order.created",
            (eventId, eventType, payload) -> {
            });

        subscription.cancel();
        assertThat(subscription.isCancelled()).isTrue();

        subscribePromise.set(delegate);

        verify(delegate).cancel();
    }
}
