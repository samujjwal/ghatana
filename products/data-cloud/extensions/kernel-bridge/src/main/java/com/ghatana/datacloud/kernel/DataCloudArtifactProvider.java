package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed artifact manifest provider.
 *
 * @doc.type class
 * @doc.purpose Persist kernel artifact manifests in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudArtifactProvider extends DataCloudKernelProviderSupport {

    public DataCloudArtifactProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.artifacts." + context.getTenantId(), "artifacts");
    }

    public Promise<Void> persistArtifactManifest(String artifactId, Map<String, Object> manifest) {
        return persistRecord(artifactId, manifest);
    }
}
