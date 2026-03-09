package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.products.yappc.domain.repository.DashboardRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating DashboardRepository instances.
 * 
 * <p>Supports both data-cloud and JPA implementations for gradual migration.
 * 
 * @doc.type class
 * @doc.purpose Factory for dashboard repositories
 * @doc.layer infrastructure
 * @doc.pattern Factory
 */
public class DashboardRepositoryFactory {
    
    /**
     * Creates a data-cloud backed repository.
     */
    @NotNull
    public static DashboardRepository createDataCloudRepository(
        @NotNull EntityRepository entityRepository,
        @NotNull YappcEntityMapper mapper
    ) {
        return new DashboardDataCloudAdapter(entityRepository, mapper);
    }
    
}
