package com.ghatana.platform.plugin.test;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
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
    void shouldPublishToEventLogStore() {
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.append(any(), any())).thenReturn(Promise.of(Offset.zero()));

        EventCloudPluginAdapter adapter = new EventCloudPluginAdapter(eventLogStore);
        PluginRegistry registry = new PluginRegistry();
        registry.register(adapter);

        TenantId tenantId = TenantId.random();
        EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class);

        runPromise(() -> registry.initializeAll(new DefaultPluginContext(registry, Map.of()))
                .then(() -> registry.startAll())
                .then(() -> adapter.publish("topic", entry, tenantId))
                .whenResult(() -> {
                    ArgumentCaptor<EventLogStore.EventEntry> captor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class);
                    verify(eventLogStore).append(any(TenantContext.class), captor.capture());
                    assertEquals(entry, captor.getValue());
                }));
    }
}