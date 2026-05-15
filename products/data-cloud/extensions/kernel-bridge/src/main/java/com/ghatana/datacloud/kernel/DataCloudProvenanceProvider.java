package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed provenance provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle provenance records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudProvenanceProvider extends DataCloudKernelProviderSupport {

    public DataCloudProvenanceProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.provenance." + context.getTenantId(), "provenance");
    }

    public Promise<Void> persistProvenance(String provenanceId, Map<String, Object> provenance) {
        return persistRecord(provenanceId, provenance);
    }
}
