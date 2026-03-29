package com.ghatana.kernel.adapter.datacloud;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractKernelService;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Shared Data-Cloud lifecycle and audit helpers for product services
 * @doc.layer adapter
 * @doc.pattern Service
 */
public abstract class AbstractDataCloudService extends AbstractKernelService {

    private final DataCloudKernelAdapter dataCloud;

    protected AbstractDataCloudService(String serviceName, KernelContext context) {
        super(serviceName);
        this.dataCloud = Objects.requireNonNull(context, "context cannot be null")
            .getDependency(DataCloudKernelAdapter.class);
    }

    protected final DataCloudKernelAdapter dataCloud() {
        return dataCloud;
    }

    protected final Promise<Void> writeAuditRecord(String datasetId, String action, String entityId, String details) {
        byte[] payload = (action + ":" + entityId + ":" + details).getBytes(StandardCharsets.UTF_8);
        return dataCloud.writeData(new DataWriteRequest(
            datasetId,
            UUID.randomUUID().toString(),
            payload,
            Map.of(
                "action", action,
                "entityId", entityId,
                "timestamp", Instant.now().toString()
            )
        ));
    }
}
