package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.bridge.port.BridgeContext;

import java.util.Map;
import java.util.Objects;

/**
 * Request for opening a data stream.
 *
 * @doc.type class
 * @doc.purpose DataCloud stream request
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public class DataStreamRequest {
    private final BridgeContext context;
    private final String datasetId;
    private final Map<String, String> options;

    public DataStreamRequest(BridgeContext context, String datasetId, Map<String, String> options) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.datasetId = Objects.requireNonNull(datasetId, "datasetId cannot be null");
        this.options = options != null ? Map.copyOf(options) : Map.of();
    }

    public DataStreamRequest(String datasetId, Map<String, String> options) {
        this(DataReadRequest.defaultContext(datasetId), datasetId, options);
    }

    public BridgeContext getContext() { return context; }
    public String getDatasetId() { return datasetId; }
    public Map<String, String> getOptions() { return options; }
}
