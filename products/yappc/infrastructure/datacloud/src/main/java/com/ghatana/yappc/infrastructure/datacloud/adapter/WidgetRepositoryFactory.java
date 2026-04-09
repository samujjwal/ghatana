package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating WidgetDataCloudAdapter instances.
 *
 * <p>Supports both data-cloud and JPA implementations for gradual migration.
 *
 * @doc.type class
 * @doc.purpose Factory for widget adapters
 * @doc.layer infrastructure
 * @doc.pattern Factory
 */
public class WidgetRepositoryFactory {

    /**
     * Creates a data-cloud backed widget adapter.
     */
    @NotNull
    public static WidgetDataCloudAdapter createDataCloudRepository(
        @NotNull DataCloudClient client,
        @NotNull YappcEntityMapper mapper
    ) {
        return new WidgetDataCloudAdapter(client, mapper);
    }

}
