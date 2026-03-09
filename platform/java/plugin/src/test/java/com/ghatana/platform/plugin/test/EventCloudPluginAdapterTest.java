package com.ghatana.platform.plugin.test;

import com.ghatana.core.event.cloud.*;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.types.identity.PartitionId;
import com.ghatana.platform.plugin.adapter.EventCloudPluginAdapter;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for EventCloudPluginAdapter.
 *
 * @doc.type class
 * @doc.purpose Verify EventCloud adapter behavior
 * @doc.layer platform
 * @doc.pattern Test
 */
public class EventCloudPluginAdapterTest extends PluginTestBase {

    @Test
    public void shouldPublishToEventCloud() {
        EventCloud eventCloud = mock(EventCloud.class);
        AppendResult mockResult = new AppendResult(
            new PartitionId("0"), 
            Offset.zero(), 
            Instant.now()
        );
        when(eventCloud.append(any())).thenReturn(Promise.of(mockResult));

        EventCloudPluginAdapter adapter = new EventCloudPluginAdapter(eventCloud);
        TenantId tenantId = TenantId.random();
        EventRecord record = mock(EventRecord.class);

        runPromise(() -> registerAndInit(adapter)
                .then(() -> registry.startAll())
                .then(() -> adapter.publish("topic", record, tenantId))
                .whenResult(() -> {
                    ArgumentCaptor<EventCloud.AppendRequest> captor = ArgumentCaptor.forClass(EventCloud.AppendRequest.class);
                    verify(eventCloud).append(captor.capture());
                    assertEquals(record, captor.getValue().event());
                }));
    }
}
