package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.domain.optimization.AnomalyDetectionResult;
import com.ghatana.digitalmarketing.domain.optimization.AnomalySeverity;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for anomaly detection result persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for anomaly detection results (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AnomalyDetectionRepository {

    /**
     * Save an anomaly detection result.
     *
     * @param anomaly the anomaly to save
     * @return Promise containing the saved anomaly
     */
    Promise<AnomalyDetectionResult> save(AnomalyDetectionResult anomaly);

    /**
     * Update an anomaly detection result.
     *
     * @param anomaly the anomaly to update
     * @return Promise containing the updated anomaly
     */
    Promise<AnomalyDetectionResult> update(AnomalyDetectionResult anomaly);

    /**
     * Find an anomaly by ID.
     *
     * @param id the anomaly ID
     * @return Promise containing optional anomaly
     */
    Promise<Optional<AnomalyDetectionResult>> findById(String id);

    /**
     * List anomalies by tenant.
     *
     * @param tenantId the tenant ID
     * @return Promise containing list of anomalies
     */
    Promise<List<AnomalyDetectionResult>> listByTenant(String tenantId);

    /**
     * List anomalies by workspace.
     *
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @return Promise containing list of anomalies
     */
    Promise<List<AnomalyDetectionResult>> listByWorkspace(String tenantId, String workspaceId);

    /**
     * List anomalies by campaign.
     *
     * @param tenantId the tenant ID
     * @param campaignId the campaign ID
     * @return Promise containing list of anomalies
     */
    Promise<List<AnomalyDetectionResult>> listByCampaign(String tenantId, String campaignId);

    /**
     * List anomalies by severity.
     *
     * @param tenantId the tenant ID
     * @param severity the severity to filter by
     * @return Promise containing list of anomalies
     */
    Promise<List<AnomalyDetectionResult>> listBySeverity(String tenantId, AnomalySeverity severity);

    /**
     * List anomalies by status.
     *
     * @param tenantId the tenant ID
     * @param status the status to filter by
     * @return Promise containing list of anomalies
     */
    Promise<List<AnomalyDetectionResult>> listByStatus(String tenantId, AnomalyStatus status);
}
