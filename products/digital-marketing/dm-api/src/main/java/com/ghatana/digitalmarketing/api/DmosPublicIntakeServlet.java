package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.abuse.IntakeAbuseControlService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.lead.LeadService;
import com.ghatana.digitalmarketing.application.suppression.SuppressionService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import com.ghatana.kernel.security.TenantSecurityContext;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Public intake servlet for self-marketing lead capture.
 *
 * <p>P1-038: Enhanced with comprehensive abuse controls including rate limiting,
 * honeypot validation, duplicate detection, and suspicious pattern detection.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS public intake API for lead form submissions with suppression and abuse enforcement
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosPublicIntakeServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosPublicIntakeServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final LeadService leadService;
    private final SuppressionService suppressionService;
    private final IntakeAbuseControlService abuseControlService;
    private final Eventloop eventloop;

    public DmosPublicIntakeServlet(
            LeadService leadService,
            SuppressionService suppressionService,
            IntakeAbuseControlService abuseControlService,
            Eventloop eventloop) {
        this.leadService = Objects.requireNonNull(leadService, "leadService must not be null");
        this.suppressionService = Objects.requireNonNull(suppressionService, "suppressionService must not be null");
        this.abuseControlService = Objects.requireNonNull(abuseControlService, "abuseControlService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    public DmosPublicIntakeServlet(
            LeadService leadService,
            SuppressionService suppressionService,
            Eventloop eventloop) {
        this(
            leadService,
            suppressionService,
            new IntakeAbuseControlService() {
                @Override
                public Promise<IntakeAbuseControlService.AbuseCheckResult> checkAbuse(
                        DmOperationContext ctx,
                        String clientIp,
                        String email,
                        Map<String, String> formData) {
                    return Promise.of(IntakeAbuseControlService.AbuseCheckResult.allowed());
                }

                @Override
                public Promise<Void> recordSubmission(DmOperationContext ctx, String clientIp, String email) {
                    return Promise.complete();
                }

                @Override
                public boolean validateHoneypot(String honeypotValue) {
                    return true;
                }

                @Override
                public Promise<Boolean> isDuplicateSubmission(DmOperationContext ctx, String email, String formHash) {
                    return Promise.of(false);
                }
            },
            eventloop
        );
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/public/v1/workspaces/:workspaceId/intake/leads", this::handleCaptureLead)
            .build(),
            DmosMetricsCollector.disabled(),
            "public-intake"
        );
    }

    /**
     * P1-038: Resolve client IP from request headers.
     */
    private String resolveClientIp(HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIdx = forwarded.indexOf(',');
            return commaIdx > 0 ? forwarded.substring(0, commaIdx).trim() : forwarded.trim();
        }
        try {
            return request.getRemoteAddress().getHostAddress();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private Promise<HttpResponse> handleCaptureLead(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId);
                CaptureLeadRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CaptureLeadRequest.class
                );

                String clientIp = resolveClientIp(request);

                // P1-038: Abuse control check
                Map<String, String> formData = new HashMap<>();
                if (body.email() != null) {
                    formData.put("email", body.email());
                }
                if (body.firstName() != null) {
                    formData.put("firstName", body.firstName());
                }
                if (body.lastName() != null) {
                    formData.put("lastName", body.lastName());
                }
                if (body.phone() != null) {
                    formData.put("phone", body.phone());
                }

                return abuseControlService.checkAbuse(ctx, clientIp, body.email(), formData)
                    .then(abuseResult -> {
                        if (abuseResult.blocked()) {
                            LOG.warn("[P1-038] Abuse blocked: {} - {}", abuseResult.blockCode(), abuseResult.blockReason());
                            return Promise.of(DmosApiErrorResponses.error(429, abuseResult.blockReason(), request));
                        }

                        return suppressionService.isSuppressed(ctx, body.email())
                            .then(suppressed -> {
                                if (suppressed) {
                                    return Promise.of(DmosApiErrorResponses.error(409, "Lead email is suppressed", request));
                                }

                                LeadService.CaptureLeadCommand command = new LeadService.CaptureLeadCommand(
                                    body.campaignId(),
                                    body.email(),
                                    body.firstName(),
                                    body.lastName(),
                                    body.phone(),
                                    body.source()
                                );

                                return leadService.captureLead(ctx, command)
                                    .map(lead -> jsonResponse(201, LeadResponse.from(lead)))
                                    .then(response -> {
                                        // Record submission for rate limiting
                                        return abuseControlService.recordSubmission(ctx, clientIp, body.email())
                                            .then(ignored -> Promise.of(response));
                                    });
                            });
                    })
                            .then(r -> Promise.of(r), e -> {
                                if (e instanceof SecurityException) {
                                    return Promise.of(DmosApiErrorResponses.error(403, "Access denied", request));
                                }
                                if (e instanceof IllegalArgumentException) {
                                    return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
                                }
                                LOG.error("[DMOS] Failed to capture lead", e);
                                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
                            });
            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to handle intake request", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal error", request));
            }
        });
    }

    private DmOperationContext buildContext(HttpRequest request, String workspaceId) {
        String tenantId = getRequiredHeader(request, "X-Tenant-ID");
        String principal = getHeader(request, "X-Principal-ID", "public-intake");
        String correlId = getHeader(request, "X-Correlation-ID", DmCorrelationId.generate().getValue());
        String idkValue = getHeader(request, "X-Idempotency-Key", null);
        String sessionId = getHeader(request, "X-Session-ID", "session-public-intake");
        Set<String> roles = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> permissions = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        if (idkValue == null || idkValue.isBlank()) {
            throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
        }

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);
        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlId))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext,
            sessionId,
            roles,
            permissions,
            null
        );

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            workspace,
            DmCorrelationId.of(correlId),
            DmIdempotencyKey.of(idkValue)
        );
    }

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getRequiredHeader(HttpRequest request, String name) {
        String value = request.getHeader(HttpHeaders.of(name));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required header missing: " + name);
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            LOG.error("[DMOS] Serialization failure", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    record CaptureLeadRequest(
        String campaignId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String source
    ) {
    }

    record LeadResponse(String id, String campaignId, String email, String status, String capturedAt) {
        static LeadResponse from(Lead lead) {
            return new LeadResponse(
                lead.getId(),
                lead.getCampaignId(),
                lead.getEmail(),
                lead.getStatus().name(),
                lead.getCapturedAt().toString()
            );
        }
    }

}
