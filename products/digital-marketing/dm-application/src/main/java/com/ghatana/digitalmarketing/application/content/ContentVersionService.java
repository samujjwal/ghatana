package com.ghatana.digitalmarketing.application.content;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.content.ContentAssetVersion;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service for immutable content version lifecycle.
 *
 * @doc.type interface
 * @doc.purpose DMOS content asset version-control service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface ContentVersionService {

    Promise<ContentAssetVersion> createInitialVersion(
        DmOperationContext ctx,
        String assetId,
        String contentBody,
        String changeSummary
    );

    Promise<ContentAssetVersion> createNextVersion(
        DmOperationContext ctx,
        String assetId,
        String contentBody,
        String changeSummary
    );

    Promise<List<ContentAssetVersion>> listVersions(DmOperationContext ctx, String assetId);
}
