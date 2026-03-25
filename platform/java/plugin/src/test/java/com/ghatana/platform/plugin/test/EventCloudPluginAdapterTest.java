package com.ghatana.platform.plugin.test;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.adapter.EventCloudPluginAdapter;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for EventCloudPluginAdapter.
 *
 * @doc.type class
 * @doc.purpose Verify EventLogStore adapter behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
public class EventCloudPluginAdapterTest extends PluginTestBase {

    @Test
    public void shouldPublishToEventLogStore() {
        EventLogStore eventLogStore = mock(EventLogStore.class);
        when(eventLogStore.append(any(), any())).thenReturn(Promise.of(Offset.zero()));

        EventCloudPluginAdapter adapter = new EventCloudPluginAdapter(eventLogStore);
        TenantId tenantId = TenantId.random();
        EventLogStore.EventEntry entry = mock(EventLogStore.EventEntry.class);

        runPromise(() -> registerAndInit(adapter)
                .then(() -> registry.startAll())
                .then(() -> adapter.publish("topic", entry, tenantId))
                .whenResult(() -> {
                    ArgumentCaptor<EventLogStore.EventEntry> captor = ArgumentCaptor.forClass(EventLogStore.EventEntry.class);
                    verify(eventLogStore).append(any(TenantContext.class), captor.capture());
                    assertEquals(entry, captor.getValue());
                }));
    }
}
