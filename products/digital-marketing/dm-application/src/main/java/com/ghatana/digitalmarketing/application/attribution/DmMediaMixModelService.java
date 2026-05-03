package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel.DmChannelContribution;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for advanced attribution and media mix modeling.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for media mix model management (DMOS-F5-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmMediaMixModelService {

    Promise<DmMediaMixModel> submit(DmOperationContext ctx, SubmitMediaMixModelCommand cmd);

    Promise<DmMediaMixModel> markFitting(DmOperationContext ctx, String modelId);

    Promise<DmMediaMixModel> markReady(DmOperationContext ctx, String modelId, double rSquared, List<DmChannelContribution> contributions);

    Promise<DmMediaMixModel> markFailed(DmOperationContext ctx, String modelId, String reason);

    Promise<Optional<DmMediaMixModel>> findById(DmOperationContext ctx, String modelId);

    Promise<List<DmMediaMixModel>> listByTenant(DmOperationContext ctx);

    record SubmitMediaMixModelCommand(
            String modelName,
            String workspaceId,
            List<String> channelIds,
            Instant dataFrom,
            Instant dataTo
    ) {
        public SubmitMediaMixModelCommand {
            if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be blank");
            if (channelIds == null || channelIds.isEmpty()) throw new IllegalArgumentException("channelIds must not be empty");
            if (dataFrom == null) throw new IllegalArgumentException("dataFrom must not be null");
            if (dataTo == null) throw new IllegalArgumentException("dataTo must not be null");
            if (!dataTo.isAfter(dataFrom)) throw new IllegalArgumentException("dataTo must be after dataFrom");
        }
    }
}
