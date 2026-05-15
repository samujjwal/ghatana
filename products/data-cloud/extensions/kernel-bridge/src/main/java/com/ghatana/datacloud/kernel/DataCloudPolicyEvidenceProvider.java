package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Data Cloud-backed policy evidence provider.
 *
 * @doc.type class
 * @doc.purpose Persist policy evidence records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudPolicyEvidenceProvider extends DataCloudKernelProviderSupport {

    public DataCloudPolicyEvidenceProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.policy-evidence." + context.getTenantId(), "policy-evidence");
    }

    public Promise<Void> persistPolicyEvidence(String evidenceId, Map<String, Object> evidence) {
        return persistRecord(evidenceId, evidence);
    }
}
