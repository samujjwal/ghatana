package com.ghatana.platform.plugin.test;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.plugin.PluginRegistry;
import com.ghatana.platform.plugin.adapter.EventCloudPluginAdapter;
import com.ghatana.platform.plugin.impl.DefaultPluginContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verify EventLogStore adapter behavior in launcher ownership
 * @doc.layer product
 * @doc.pattern Test
 */
class EventCloudPluginAdapterTest extends EventloopTestBase {

    @Test
    void shouldPublishToEventLogStore() { // GH-90000
        EventLogStore eventLogStore = mock(EventLogStore.class); // GH-90000
        when(eventLogStore.append(any(), any())).thenReturn(Promise.of(Offset.zero())); // GH-90000

        EventCloudPluginAdapter adapter = new EventCloudPluginAdapter(eventLogStore); // GH-90000
        PluginRegistry registry = new PluginRegistry(); // GH-90000
        registry.register(adapter); // GH-90000

        TenantId tenantId = TenantId.random(); // GH-90000
        EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class); // GH-90000

        runPromise(() -> registry.initializeAll(new DefaultPluginContext(registry, Map.of())) // GH-90000
                .then(() -> registry.startAll()) // GH-90000
                .then(() -> adapter.publish("topic", entry, tenantId)) // GH-90000
                .whenResult(() -> { // GH-90000
                    ArgumentCaptor<EventLogStore.EventEntry> captor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class); // GH-90000
                    verify(eventLogStore).append(any(TenantContext.class), captor.capture()); // GH-90000
                    assertEquals(entry, captor.getValue()); // GH-90000
                }));
    }
}
