package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed lifecycle health provider.
 *
 * @doc.type class
 * @doc.purpose Persist provider and lifecycle health snapshots in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudHealthProvider extends DataCloudKernelProviderSupport {

    public DataCloudHealthProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.health." + context.getTenantId(), "health");
    }

    public Promise<Void> persistHealthSnapshot(String snapshotId, String status, Map<String, Object> details) {
        return persistRecord(snapshotId, Map.of(
            "snapshotId", snapshotId,
            "status", status,
            "details", details != null ? details : Map.of(),
            "tenantId", context().getTenantId(),
            "capturedAt", Instant.now().toString()
        ));
    }
}
