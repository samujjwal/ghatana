package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed memory provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle and agent memory records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudMemoryProvider extends DataCloudKernelProviderSupport {

    public DataCloudMemoryProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.memory." + context.getTenantId(), "memory");
    }

    public Promise<Void> remember(String memoryId, Map<String, Object> memory) {
        return persistRecord(memoryId, memory);
    }
}
