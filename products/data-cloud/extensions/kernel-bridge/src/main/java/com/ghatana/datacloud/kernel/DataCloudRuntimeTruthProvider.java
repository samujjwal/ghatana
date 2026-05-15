package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed runtime truth provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle runtime truth snapshots in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudRuntimeTruthProvider extends DataCloudKernelProviderSupport {

    public DataCloudRuntimeTruthProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.runtime-truth." + context.getTenantId(), "runtime-truth");
    }

    public Promise<Void> persistRuntimeTruth(String snapshotId, Map<String, Object> snapshot) {
        return persistRecord(snapshotId, snapshot);
    }
}
