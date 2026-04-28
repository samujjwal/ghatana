/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — DataCloud Project Provider
 */
package com.ghatana.yappc.services.lifecycle.scheduler;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.yappc.domain.PhaseType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Project provider that fetches active projects from Data Cloud.
 *
 * <p>This implementation queries the Data Cloud for projects with status 'ACTIVE'
 * and returns their basic information for gate checking.</p>
 *
 * @doc.type class
 * @doc.purpose Data Cloud integration for project discovery
 * @doc.layer product
 * @doc.pattern Data Access
 */
public final class DataCloudProjectProvider implements PhaseGateSchedulerService.ProjectProvider {

    private static final Logger log = LoggerFactory.getLogger(DataCloudProjectProvider.class);

    private static final String PROJECT_COLLECTION = "projects";
    private static final String STATUS_FIELD = "status";
    private static final String PHASE_FIELD = "lifecyclePhase";
    private static final String UPDATED_FIELD = "updatedAt";

    private final DataCloudClient dataCloudClient;

    /**
     * Constructs the Data Cloud project provider.
     *
     * @param dataCloudClient the Data Cloud client
     */
    public DataCloudProjectProvider(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
    }

    @Override
    public Promise<List<PhaseGateSchedulerService.ProjectInfo>> getActiveProjects() {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq(STATUS_FIELD, "ACTIVE"))
            .limit(1000)
                .build();

        return dataCloudClient.query(TenantContext.getCurrentTenantId(), PROJECT_COLLECTION, query)
                .map(this::mapResults)
            .whenException(ex -> log.error("Failed to fetch active projects from Data Cloud", ex));
    }

        private List<PhaseGateSchedulerService.ProjectInfo> mapResults(List<DataCloudClient.Entity> result) {
        List<PhaseGateSchedulerService.ProjectInfo> projects = new ArrayList<>();

        for (DataCloudClient.Entity entity : result) {
            try {
            Map<String, Object> doc = entity.data();
            String projectId = entity.id();
                String phaseStr = getString(doc, PHASE_FIELD);
                String updatedStr = getString(doc, UPDATED_FIELD);

                if (projectId == null || phaseStr == null) {
                    log.warn("Skipping project with missing required fields: {}", doc);
                    continue;
                }

                PhaseType phase = parsePhase(phaseStr);
                Instant updatedAt = updatedStr != null ? Instant.parse(updatedStr) : Instant.now();

                projects.add(new PhaseGateSchedulerService.ProjectInfo(
                        projectId,
                        phase,
                        updatedAt
                ));
            } catch (Exception ex) {
                log.warn("Failed to map project entity: {}", entity.id(), ex);
            }
        }

        log.debug("Mapped {} active projects from Data Cloud", projects.size());
        return projects;
    }

    private String getString(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        return value != null ? value.toString() : null;
    }

    private PhaseType parsePhase(String phaseStr) {
        try {
            return PhaseType.valueOf(phaseStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown phase type '{}', defaulting to INTENT", phaseStr);
            return PhaseType.INTENT;
        }
    }
}
