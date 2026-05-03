package com.ghatana.digitalmarketing.application.googleads;

import com.ghatana.digitalmarketing.application.DmosFeatureFlags;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmGoogleAdsAuthService}.
 *
 * @doc.type class
 * @doc.purpose Implements Google Ads OAuth account connection flow with tenant-safe authorization checks (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmGoogleAdsAuthServiceImpl implements DmGoogleAdsAuthService {

    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsOAuthClient oAuthClient;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmGoogleAdsAuthServiceImpl(
            DmGoogleAdsCredentialRepository credentialRepository,
            DmConnectorRepository connectorRepository,
            DmGoogleAdsOAuthClient oAuthClient,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.oAuthClient = Objects.requireNonNull(oAuthClient, "oAuthClient must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<String> initiateOAuthFlow(DmOperationContext ctx, String connectorId, String redirectUri) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (connectorId == null || connectorId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("connectorId must not be blank"));
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("redirectUri must not be blank"));
        }

        return requireAuthorizedConnector(ctx, connectorId)
            .then(connector -> kernelAdapter.isFeatureEnabled(ctx, DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)
                .then(enabled -> {
                    if (!enabled) {
                        return Promise.<String>ofException(
                            new DmosConnectorDisabledException("Google Ads", DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED));
                    }
                    return Promise.of(
                        oAuthClient.buildAuthorizationUrl(redirectUri, buildState(ctx, connector.getId())));
                }));
    }

    @Override
    public Promise<DmGoogleAdsCredential> exchangeCode(
            DmOperationContext ctx,
            String connectorId,
            String code,
            String redirectUri) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (connectorId == null || connectorId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("connectorId must not be blank"));
        }
        if (code == null || code.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("code must not be blank"));
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("redirectUri must not be blank"));
        }

        return requireAuthorizedConnector(ctx, connectorId)
            .then(_connector -> oAuthClient.exchangeAuthorizationCode(code, redirectUri))
            .then(tokens -> upsertCredential(ctx, connectorId, tokens));
    }

    @Override
    public Promise<DmGoogleAdsCredential> refreshToken(DmOperationContext ctx, String credentialId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (credentialId == null || credentialId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("credentialId must not be blank"));
        }

        return requireAuthorizedCredential(ctx, credentialId)
            .then(credential -> oAuthClient.refreshAccessToken(credential.getRefreshToken())
                .then(tokens -> {
                    DmGoogleAdsCredential updated = credential.toBuilder()
                        .accessToken(tokens.accessToken())
                        .refreshToken(tokens.refreshToken())
                        .expiresAt(Instant.now().plusSeconds(tokens.expiresInSeconds()))
                        .scopes(tokens.scopes())
                        .updatedAt(Instant.now())
                        .build();
                    return credentialRepository.update(updated);
                }));
    }

    @Override
    public Promise<Void> revokeAccess(DmOperationContext ctx, String credentialId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (credentialId == null || credentialId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("credentialId must not be blank"));
        }

        return requireAuthorizedCredential(ctx, credentialId)
            .then(credential -> oAuthClient.revokeAccessToken(credential.getAccessToken())
                .then(_ignored -> credentialRepository.delete(credential.getId())));
    }

    private Promise<DmGoogleAdsCredential> upsertCredential(
            DmOperationContext ctx,
            String connectorId,
            DmGoogleAdsOAuthClient.OAuthTokenResponse tokens) {
        Instant now = Instant.now();

        return credentialRepository.findByConnectorId(connectorId)
            .then(existing -> {
                DmGoogleAdsCredential credential;
                if (existing.isPresent()) {
                    DmGoogleAdsCredential current = existing.get();
                    if (!current.getTenantId().equals(ctx.getTenantId().getValue())) {
                        return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
                    }
                    credential = current.toBuilder()
                        .accessToken(tokens.accessToken())
                        .refreshToken(tokens.refreshToken())
                        .expiresAt(now.plusSeconds(tokens.expiresInSeconds()))
                        .scopes(tokens.scopes())
                        .updatedAt(now)
                        .build();
                    return credentialRepository.update(credential);
                }

                credential = DmGoogleAdsCredential.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .connectorId(connectorId)
                    .accessToken(tokens.accessToken())
                    .refreshToken(tokens.refreshToken())
                    .expiresAt(now.plusSeconds(tokens.expiresInSeconds()))
                    .scopes(tokens.scopes())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
                return credentialRepository.save(credential);
            });
    }

    private Promise<DmConnectorConfig> requireAuthorizedConnector(DmOperationContext ctx, String connectorId) {
        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to manage connector OAuth"));
                }

                return connectorRepository.findById(connectorId)
                    .then(opt -> validateOwnedGoogleAdsConnector(ctx, connectorId, opt));
            });
    }

    private Promise<DmGoogleAdsCredential> requireAuthorizedCredential(DmOperationContext ctx, String credentialId) {
        return kernelAdapter.isAuthorized(ctx, "connectors/*", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Not authorized to manage connector OAuth"));
                }
                return credentialRepository.findById(credentialId)
                    .then(opt -> {
                        if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                            return Promise.ofException(new NoSuchElementException("Credential not found: " + credentialId));
                        }
                        return Promise.of(opt.get());
                    });
            });
    }

    private Promise<DmConnectorConfig> validateOwnedGoogleAdsConnector(
            DmOperationContext ctx,
            String connectorId,
            Optional<DmConnectorConfig> opt) {
        if (opt.isEmpty()) {
            return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
        }

        DmConnectorConfig connector = opt.get();
        if (!connector.getTenantId().equals(ctx.getTenantId().getValue())) {
            return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
        }
        if (connector.getConnectorType() != DmConnectorType.GOOGLE_ADS) {
            return Promise.ofException(new IllegalArgumentException("Connector must be GOOGLE_ADS"));
        }
        return Promise.of(connector);
    }

    private String buildState(DmOperationContext ctx, String connectorId) {
        return ctx.getTenantId().getValue() + ":" + connectorId + ":" + ctx.getCorrelationId().getValue();
    }
}