package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed lifecycle event provider.
 *
 * @doc.type class
 * @doc.purpose Persist kernel lifecycle events in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudEventProvider extends DataCloudKernelProviderSupport {

    public DataCloudEventProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.events." + context.getTenantId(), "events");
    }

    public Promise<Void> appendEvent(String eventId, Map<String, Object> event) {
        return persistRecord(eventId, event);
    }
}
