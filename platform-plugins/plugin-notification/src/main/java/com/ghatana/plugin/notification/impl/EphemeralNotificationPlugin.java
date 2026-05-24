package com.ghatana.plugin.notification.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.plugin.notification.NotificationPlugin;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Compatibility adapter that exposes ephemeral naming while delegating
 * behavior to the legacy in-memory notification plugin implementation.
 *
 * @doc.type class
 * @doc.purpose Ephemeral notification plugin alias for product wiring
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public final class EphemeralNotificationPlugin implements NotificationPlugin {

    private final InMemoryNotificationPlugin delegate = new InMemoryNotificationPlugin();

    @Override
    public PluginMetadata metadata() {
        return delegate.metadata();
    }

    @Override
    public PluginState getState() {
        return delegate.getState();
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        return delegate.initialize(context);
    }

    @Override
    public Promise<Void> start() {
        return delegate.start();
    }

    @Override
    public Promise<Void> stop() {
        return delegate.stop();
    }

    @Override
    public Promise<String> dispatch(String recipientId, String template, Map<String, String> attributes) {
        return delegate.dispatch(recipientId, template, attributes);
    }

    @Override
    public Promise<DeliveryStatus> getDeliveryStatus(String notificationId) {
        return delegate.getDeliveryStatus(notificationId);
    }

    @Override
    public Promise<Void> retry(String notificationId) {
        return delegate.retry(notificationId);
    }

    @Override
    public Promise<List<DeadLetterEntry>> listDeadLetterQueue(int limit, int offset) {
        return delegate.listDeadLetterQueue(limit, offset);
    }

    @Override
    public Promise<Void> reprocessDeadLetter(String notificationId) {
        return delegate.reprocessDeadLetter(notificationId);
    }
}
