package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.campaign.CampaignRepository;
import com.ghatana.digitalmarketing.application.connector.DmConnectorRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import io.activej.promise.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Command handler for Google Ads campaign creation.
 *
 * <p>This handler executes the Google Ads campaign creation flow:
 * validates connector, credential, and campaign state; calls the Google Ads API;
 * persists the external ID mapping; and records the execution for audit/rollback.</p>
 *
 * <p>Emits structured logs and metrics for observability (DMOS-P1-008, DMOS-P1-011).</p>
 *
 * @doc.type class
 * @doc.purpose Command handler for Google Ads campaign creation (DMOS-P1-008)
 * @doc.layer product
 * @doc.pattern CommandHandler
 */
public final class GoogleAdsCampaignCreateCommandHandler implements DmCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAdsCampaignCreateCommandHandler.class);

    private final DmConnectorRepository connectorRepository;
    private final DmGoogleAdsCredentialRepository credentialRepository;
    private final DmGoogleAdsCampaignLinkRepository linkRepository;
    private final CampaignRepository campaignRepository;
    private final DmGoogleAdsCampaignApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final DmosObservability observability;
    
    // P1-8: Retry configuration for transient failures
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public GoogleAdsCampaignCreateCommandHandler(
            DmConnectorRepository connectorRepository,
            DmGoogleAdsCredentialRepository credentialRepository,
            DmGoogleAdsCampaignLinkRepository linkRepository,
            CampaignRepository campaignRepository,
            DmGoogleAdsCampaignApiClient apiClient,
            ObjectMapper objectMapper,
            DmosObservability observability) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository, "connectorRepository must not be null");
        this.credentialRepository = Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository must not be null");
        this.campaignRepository = Objects.requireNonNull(campaignRepository, "campaignRepository must not be null");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
    }

    @Override
    public Promise<Void> handle(DmCommand command) {
        MDC.put("commandId", command.getId());
        MDC.put("tenantId", command.getTenantId());
        MDC.put("workspaceId", command.getWorkspaceId());
        MDC.put("correlationId", command.getCorrelationId());

        // Create span for this command execution (DMOS-P1-011)
        Span span = observability.createSpan("GOOGLE_ADS_CAMPAIGN_CREATE", "command.id", command.getId());
        span.setAttribute("tenant.id", command.getTenantId());
        span.setAttribute("workspace.id", command.getWorkspaceId());
        span.setAttribute("correlation.id", command.getCorrelationId());

        Instant startTime = Instant.now();
        LOG.info("[DMOS-HANDLER] Starting GOOGLE_ADS_CAMPAIGN_CREATE command");

        if (command.getCommandType() != DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE) {
            LOG.error("[DMOS-HANDLER] Wrong command type: expected GOOGLE_ADS_CAMPAIGN_CREATE, got {}", command.getCommandType());
            span.recordException(new IllegalArgumentException("Wrong command type"));
            span.end();
            MDC.clear();
            return Promise.ofException(new IllegalArgumentException(
                "This handler only supports GOOGLE_ADS_CAMPAIGN_CREATE, got: " + command.getCommandType()));
        }

        try (Scope scope = span.makeCurrent()) {
            GoogleAdsCampaignCreatePayload payload = objectMapper.readValue(
                command.getSerializedPayload(),
                GoogleAdsCampaignCreatePayload.class);

            LOG.info("[DMOS-HANDLER] Parsed payload: connectorId={}, internalCampaignId={}",
                payload.connectorId(), payload.internalCampaignId());

            return validateConnector(command, payload.connectorId())
                .then(connector -> {
                    LOG.info("[DMOS-HANDLER] Connector validated: {}", connector.getId());
                    return validateCredential(command, connector.getId())
                        .then(credential -> Promise.of(new Object[]{connector, credential}));
                })
                .then(arr -> {
                    Object[] parts = (Object[]) arr;
                    var connector = (DmConnectorConfig) parts[0];
                    var credential = (DmGoogleAdsCredential) parts[1];
                    return validateCampaign(command, payload.internalCampaignId())
                        .then(campaign -> Promise.of(new Object[]{connector, credential, campaign}));
                })
                .then(arr -> {
                    Object[] parts = (Object[]) arr;
                    var connector = (DmConnectorConfig) parts[0];
                    var credential = (DmGoogleAdsCredential) parts[1];
                    var campaign = (Campaign) parts[2];
                    LOG.info("[DMOS-HANDLER] Campaign validated: {}", campaign.getName());
                    
                    // P1-8: Idempotency check - if link already exists, return early
                    return linkRepository.findByInternalCampaignId(campaign.getId())
                        .then(existingLink -> {
                            if (existingLink.isPresent()) {
                                LOG.info("[DMOS-HANDLER] Campaign link already exists (idempotent): externalId={}",
                                    existingLink.get().getExternalCampaignId());
                                span.setAttribute("idempotent", true);
                                span.setAttribute("external.campaign.id", existingLink.get().getExternalCampaignId());
                                return Promise.<Void>of(null);
                            }
                            
                            // Proceed with creation
                            DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest providerRequest =
                                new DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest(
                                    campaign.getName(),
                                    new BigDecimal(payload.dailyBudget()),
                                    payload.serviceArea(),
                                    payload.keywordTheme()
                                );

                            LOG.info("[DMOS-HANDLER] Calling Google Ads API to create campaign");
                            return createCampaignWithRetry(credential, providerRequest, span, 0)
                                .then(externalCampaignId -> {
                                    span.setAttribute("external.campaign.id", externalCampaignId);
                                    LOG.info("[DMOS-HANDLER] Google Ads API returned external campaign ID: {}", externalCampaignId);
                                    return linkRepository.save(
                                        DmGoogleAdsCampaignLink.builder()
                                            .id(UUID.randomUUID().toString())
                                            .tenantId(command.getTenantId())
                                            .connectorId(connector.getId())
                                            .internalCampaignId(campaign.getId())
                                            .externalCampaignId(externalCampaignId)
                                            .createdAt(Instant.now())
                                            .build()
                                    ).then(link -> {
                                        LOG.info("[DMOS-HANDLER] Campaign link persisted: internalId={} externalId={}",
                                            link.getInternalCampaignId(), link.getExternalCampaignId());
                                        return Promise.<Void>of(null);
                                    });
                                });
                        });
                })
                .whenComplete((result, e) -> {
                    long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
                    observability.recordCommandSuccess("GOOGLE_ADS_CAMPAIGN_CREATE");
                    observability.recordCommandDuration("GOOGLE_ADS_CAMPAIGN_CREATE", duration);
                    span.setAttribute("duration.ms", duration);
                    LOG.info("[DMOS-HANDLER] GOOGLE_ADS_CAMPAIGN_CREATE command completed successfully in {}ms", duration);
                    span.end();
                    MDC.clear();
                })
                .whenException(e -> {
                    long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
                    observability.recordCommandFailure("GOOGLE_ADS_CAMPAIGN_CREATE", e.getMessage());
                    span.recordException(e);
                    span.setAttribute("duration.ms", duration);
                    LOG.error("[DMOS-HANDLER] GOOGLE_ADS_CAMPAIGN_CREATE command failed: {}", e.getMessage(), e);
                    span.end();
                    MDC.clear();
                });
        } catch (Exception e) {
            long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
            observability.recordCommandFailure("GOOGLE_ADS_CAMPAIGN_CREATE", e.getMessage());
            span.recordException(e);
            span.setAttribute("duration.ms", duration);
            LOG.error("[DMOS-HANDLER] Failed to parse command payload: {}", e.getMessage(), e);
            span.end();
            MDC.clear();
            return Promise.ofException(new IllegalStateException("Failed to parse command payload", e));
        }
    }

    private Promise<DmConnectorConfig> validateConnector(DmCommand command, String connectorId) {
        return connectorRepository.findById(connectorId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(command.getTenantId())) {
                    LOG.warn("[DMOS-HANDLER] Connector not found or tenant mismatch: connectorId={}", connectorId);
                    return Promise.ofException(new NoSuchElementException("Connector not found: " + connectorId));
                }
                DmConnectorConfig connector = opt.get();
                if (connector.getConnectorType() != DmConnectorType.GOOGLE_ADS) {
                    LOG.warn("[DMOS-HANDLER] Connector type mismatch: expected GOOGLE_ADS, got {}", connector.getConnectorType());
                    return Promise.ofException(new IllegalArgumentException("Connector must be GOOGLE_ADS"));
                }
                if (connector.getStatus() != DmConnectorStatus.ACTIVE) {
                    LOG.warn("[DMOS-HANDLER] Connector not ACTIVE: status={}", connector.getStatus());
                    return Promise.ofException(new IllegalStateException("Connector must be ACTIVE"));
                }
                return Promise.of(connector);
            });
    }

    private Promise<DmGoogleAdsCredential> validateCredential(DmCommand command, String connectorId) {
        return credentialRepository.findByConnectorId(connectorId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(command.getTenantId())) {
                    LOG.warn("[DMOS-HANDLER] Credential not found or tenant mismatch: connectorId={}", connectorId);
                    return Promise.ofException(new NoSuchElementException("Credential not found for connector: " + connectorId));
                }
                DmGoogleAdsCredential credential = opt.get();
                if (credential.isExpired()) {
                    LOG.warn("[DMOS-HANDLER] Credential expired: connectorId={}", connectorId);
                    return Promise.ofException(new IllegalStateException("Credential is expired"));
                }
                return Promise.of(credential);
            });
    }

    private Promise<Campaign> validateCampaign(DmCommand command, String campaignId) {
        return campaignRepository.findById(DmWorkspaceId.of(command.getWorkspaceId()), campaignId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    LOG.warn("[DMOS-HANDLER] Campaign not found: campaignId={}", campaignId);
                    return Promise.ofException(new NoSuchElementException("Campaign not found: " + campaignId));
                }
                Campaign campaign = opt.get();
                if (campaign.getType() != CampaignType.PAID_SEARCH) {
                    LOG.warn("[DMOS-HANDLER] Campaign type mismatch: expected PAID_SEARCH, got {}", campaign.getType());
                    return Promise.ofException(new IllegalArgumentException("Campaign must be PAID_SEARCH"));
                }
                if (campaign.getStatus() != CampaignStatus.LAUNCHED) {
                    LOG.warn("[DMOS-HANDLER] Campaign not LAUNCHED: status={}", campaign.getStatus());
                    return Promise.ofException(new IllegalStateException("Campaign must be LAUNCHED"));
                }
                return Promise.of(campaign);
            });
    }

    /**
     * P1-8: Create campaign with retry logic for transient failures.
     */
    private Promise<String> createCampaignWithRetry(
            DmGoogleAdsCredential credential,
            DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest request,
            Span span,
            int retryCount) {
        Instant apiStartTime = Instant.now();
        return apiClient.createSearchCampaign(credential.getAccessToken(), request)
            .then(externalCampaignId -> {
                long apiDuration = ChronoUnit.MILLIS.between(apiStartTime, Instant.now());
                observability.recordConnectorDuration("GOOGLE_ADS", "createSearchCampaign", apiDuration);
                if (retryCount > 0) {
                    span.setAttribute("retry.count", retryCount);
                    LOG.info("[DMOS-HANDLER] Campaign creation succeeded after {} retries", retryCount);
                }
                return Promise.of(externalCampaignId);
            })
            .whenException(e -> {
                // P1-8: Retry on transient failures
                if (isTransientFailure(e) && retryCount < MAX_RETRIES) {
                    LOG.warn("[DMOS-HANDLER] Transient failure, retrying ({}/{}): {}",
                        retryCount + 1, MAX_RETRIES, e.getMessage());
                    return Promise.of(null)
                        .delay(RETRY_DELAY_MS)
                        .then(ignored -> createCampaignWithRetry(credential, request, span, retryCount + 1));
                }
                LOG.error("[DMOS-HANDLER] Campaign creation failed after {} retries: {}", retryCount, e.getMessage());
                return Promise.ofException(e);
            });
    }

    /**
     * P1-8: Determine if a failure is transient and should be retried.
     */
    private boolean isTransientFailure(Exception e) {
        // Retry on network timeouts, rate limits, and temporary service errors
        String message = e.getMessage();
        if (message == null) return false;
        return message.contains("timeout") ||
               message.contains("rate limit") ||
               message.contains("503") ||
               message.contains("502") ||
               message.contains("429");
    }

    /**
     * Payload for Google Ads campaign creation command.
     */
    public record GoogleAdsCampaignCreatePayload(
            String connectorId,
            String internalCampaignId,
            String dailyBudget,
            String serviceArea,
            String keywordTheme
    ) {
        public GoogleAdsCampaignCreatePayload {
            Objects.requireNonNull(connectorId, "connectorId must not be null");
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(dailyBudget, "dailyBudget must not be null");
            Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            Objects.requireNonNull(keywordTheme, "keywordTheme must not be null");
        }
    }
}
