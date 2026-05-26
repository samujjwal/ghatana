package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.connector.googleads.GoogleAdsConnectorReadinessState;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Production implementation of Google Ads connector readiness checks.
 *
 * <p>The service persists readiness outcomes on the connector record so cockpit,
 * health, and launch paths read the same runtime truth.</p>
 *
 * @doc.type class
 * @doc.purpose Persists Google Ads connector readiness from credential and API checks
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmGoogleAdsConnectorReadinessServiceImpl implements DmGoogleAdsConnectorReadinessService {

    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmGoogleAdsCampaignApiClient apiClient;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmGoogleAdsConnectorReadinessServiceImpl(
            DmConnectorRepository connectorRepository,
            DmGoogleAdsCredentialRepository credentialRepository,
            DmGoogleAdsCampaignApiClient apiClient,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmGoogleAdsConnectorReadiness> checkReadiness(DmOperationContext ctx, String connectorId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (connectorId == null || connectorId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("connectorId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "connectors/google-ads", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to read Google Ads connector readiness"));
                }
                return loadOwnedGoogleAdsConnector(ctx, connectorId);
            })
            .then(connector -> {
                if (connector.getStatus() == DmConnectorStatus.DISABLED) {
                    return Promise.of(new DmGoogleAdsConnectorReadiness(
                        connector.getId(),
                        GoogleAdsConnectorReadinessState.ENVIRONMENT_BLOCKED,
                        connector.getStatus(),
                        "connector disabled",
                        Instant.now()
                    ));
                }
                return evaluateCredential(connector);
            });
    }

    private Promise<DmGoogleAdsConnectorReadiness> evaluateCredential(DmConnectorConfig connector) {
        return credentialRepository.findByConnectorId(connector.getId())
            .then(optCredential -> {
                if (optCredential.isEmpty()) {
                    DmConnectorConfig updated = connector.markPending("missing Google Ads OAuth credential");
                    return connectorRepository.update(updated)
                        .map(saved -> readiness(saved, GoogleAdsConnectorReadinessState.NOT_READY, saved.getFailureReason()));
                }

                DmGoogleAdsCredential credential = optCredential.get();
                if (!credential.getTenantId().equals(connector.getTenantId())) {
                    DmConnectorConfig updated = connector.markAuthFailed("credential tenant mismatch");
                    return connectorRepository.update(updated)
                        .map(saved -> readiness(saved, GoogleAdsConnectorReadinessState.AUTH_FAILED, saved.getFailureReason()));
                }
                if (credential.isRevoked()) {
                    DmConnectorConfig updated = connector.markAuthFailed("credential revoked");
                    return connectorRepository.update(updated)
                        .map(saved -> readiness(saved, GoogleAdsConnectorReadinessState.AUTH_FAILED, saved.getFailureReason()));
                }
                if (credential.isExpired()) {
                    DmConnectorConfig updated = connector.markAuthFailed("credential expired");
                    return connectorRepository.update(updated)
                        .map(saved -> readiness(saved, GoogleAdsConnectorReadinessState.AUTH_FAILED, saved.getFailureReason()));
                }

                return apiClient.checkReadiness(credential.getAccessToken())
                    .then(state -> persistState(connector, state));
            });
    }

    private Promise<DmGoogleAdsConnectorReadiness> persistState(DmConnectorConfig connector, GoogleAdsConnectorReadinessState state) {
        DmConnectorConfig updated = switch (state) {
            case READY -> connector.activate();
            case AUTH_FAILED -> connector.markAuthFailed("Google Ads OAuth validation failed");
            case NOT_READY -> connector.markPending("Google Ads connector configuration is incomplete");
            case RATE_LIMITED -> connector.markPending("Google Ads API rate limited readiness check");
            case REMOTE_VALIDATION_FAILED -> connector.markPending("Google Ads remote validation failed");
            case PUBLISH_FAILED -> connector.markPending("Google Ads publish validation failed");
            case ENVIRONMENT_BLOCKED -> connector.markPending("Google Ads connector blocked for environment");
        };
        return connectorRepository.update(updated)
            .map(saved -> readiness(saved, state, saved.getFailureReason()));
    }

    private Promise<DmConnectorConfig> loadOwnedGoogleAdsConnector(DmOperationContext ctx, String connectorId) {
        return connectorRepository.findById(connectorId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
                }
                DmConnectorConfig connector = opt.get();
                if (!connector.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
                }
                if (connector.getConnectorType() != DmConnectorType.GOOGLE_ADS) {
                    return Promise.ofException(new IllegalArgumentException("Connector is not GOOGLE_ADS: " + connectorId));
                }
                return Promise.of(connector);
            });
    }

    private static DmGoogleAdsConnectorReadiness readiness(
            DmConnectorConfig connector,
            GoogleAdsConnectorReadinessState state,
            String reason) {
        return new DmGoogleAdsConnectorReadiness(
            connector.getId(),
            state,
            connector.getStatus(),
            reason,
            connector.getLastHealthCheckAt()
        );
    }
}
