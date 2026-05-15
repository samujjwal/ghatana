package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed knowledge provider.
 *
 * @doc.type class
 * @doc.purpose Persist kernel knowledge evidence in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudKnowledgeProvider extends DataCloudKernelProviderSupport {

    public DataCloudKnowledgeProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.knowledge." + context.getTenantId(), "knowledge");
    }

    public Promise<Void> persistKnowledge(String knowledgeId, Map<String, Object> knowledge) {
        return persistRecord(knowledgeId, knowledge);
    }
}
