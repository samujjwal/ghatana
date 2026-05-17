/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.server.response.ErrorResponse;
import io.activej.http.HttpHeader;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP filter that checks kill switch status before allowing requests to proceed.
 *
 * <p>This filter integrates with the incident-service via HTTP to block
 * authentication requests when a kill switch is active for a tenant or globally.
 * This provides emergency incident response capabilities for the authentication gateway.</p>
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Checks global kill switch first - blocks all requests if active</li>
 *   <li>If global is inactive, checks tenant-specific kill switch</li>
 *   <li>Returns 503 Service Unavailable when kill switch is active</li>
 *   <li>Includes incident context in error response</li>
 *   <li>Fails open if incident-service is unavailable (allows requests to proceed)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP filter for kill switch integration with incident-service
 * @doc.layer shared-service
 * @doc.pattern Filter
 */
public class KillSwitchFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(KillSwitchFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpHeader INCIDENT_ACTIVE_HEADER = HttpHeaders.of("X-Incident-Active");
    private static final HttpHeader INCIDENT_REASON_HEADER = HttpHeaders.of("X-Incident-Reason");

    private final HttpClient httpClient;
    private final String incidentServiceUrl;

    /**
     * Creates a new kill switch filter.
     *
     * @param httpClient          the ActiveJ HTTP client
     * @param incidentServiceUrl  the base URL of the incident-service (e.g., http://incident-service:8080)
     */
    public KillSwitchFilter(HttpClient httpClient, String incidentServiceUrl) {
        this.httpClient = httpClient;
        this.incidentServiceUrl = incidentServiceUrl;
    }

    /**
     * Filters the request, blocking it if a kill switch is active.
     *
     * @param request the HTTP request to filter
     * @param tenantId the tenant ID from the request context
     * @return promise that completes with the response (either blocked or allowed to proceed)
     */
    public Promise<HttpResponse> filter(HttpRequest request, String tenantId) {
        // First check global kill switch
        return checkGlobalKillSwitch()
            .then(globalActive -> {
                if (globalActive) {
                    LOGGER.warn("Global kill switch is active - blocking auth request");
                    return Promise.of(createKillSwitchResponse(
                        "Global kill switch is active",
                        "All authentication requests are blocked due to a platform-wide incident"
                    ));
                }

                // Global is inactive, check tenant-specific kill switch
                if (tenantId == null || tenantId.isBlank()) {
                    // No tenant context, allow to proceed (will be handled by other filters)
                    return Promise.of((HttpResponse) null);
                }

                return checkTenantKillSwitch(tenantId)
                    .then(tenantActive -> {
                        if (tenantActive) {
                            LOGGER.warn("Kill switch is active for tenant {} - blocking auth request", tenantId);
                            return Promise.of(createKillSwitchResponse(
                                "Tenant kill switch is active",
                                String.format("Authentication requests for tenant '%s' are blocked due to an incident", tenantId)
                            ));
                        }

                        // Kill switch not active, allow request to proceed
                        return Promise.of((HttpResponse) null);
                    });
            });
    }

    /**
     * Checks if the global kill switch is active via HTTP call to incident-service.
     *
     * @return promise resolving to true if global kill switch is active, false otherwise
     */
    private Promise<Boolean> checkGlobalKillSwitch() {
        String url = incidentServiceUrl + "/api/v1/incident/kill-switch/global/status";
        HttpRequest request = HttpRequest.get(url).build();
        
        return httpClient.request(request)
            .then((response, e) -> {
                if (e != null) {
                    LOGGER.error("Failed to check global kill switch status", e);
                    return Promise.of(false); // Fail open - don't block auth if incident-service is down
                }
                
                if (response.getCode() == 200) {
                    return response.loadBody()
                        .map(body -> {
                            try {
                                KillSwitchStatus status = OBJECT_MAPPER.readValue(body.toString(), KillSwitchStatus.class);
                                return status.active;
                            } catch (Exception ex) {
                                LOGGER.error("Failed to parse kill switch status response", ex);
                                return false; // Fail open
                            }
                        });
                }
                // If incident-service is unavailable or returns error, fail open
                LOGGER.warn("Incident-service returned {} for global kill switch check", response.getCode());
                return Promise.of(false);
            });
    }

    /**
     * Checks if the tenant-specific kill switch is active via HTTP call to incident-service.
     *
     * @param tenantId the tenant ID to check
     * @return promise resolving to true if tenant kill switch is active, false otherwise
     */
    private Promise<Boolean> checkTenantKillSwitch(String tenantId) {
        String url = incidentServiceUrl + "/api/v1/incident/kill-switch/status/" + tenantId;
        HttpRequest request = HttpRequest.get(url).build();
        
        return httpClient.request(request)
            .then((response, e) -> {
                if (e != null) {
                    LOGGER.error("Failed to check tenant kill switch status", e);
                    return Promise.of(false); // Fail open - don't block auth if incident-service is down
                }
                
                if (response.getCode() == 200) {
                    return response.loadBody()
                        .map(body -> {
                            try {
                                KillSwitchStatus status = OBJECT_MAPPER.readValue(body.toString(), KillSwitchStatus.class);
                                return status.active;
                            } catch (Exception ex) {
                                LOGGER.error("Failed to parse kill switch status response", ex);
                                return false; // Fail open
                            }
                        });
                }
                // If incident-service is unavailable or returns error, fail open
                LOGGER.warn("Incident-service returned {} for tenant kill switch check", response.getCode());
                return Promise.of(false);
            });
    }

    /**
     * Creates a 503 Service Unavailable response when kill switch is active.
     *
     * @param title   short title for the error
     * @param detail  detailed explanation
     * @return HTTP response with 503 status and error body
     */
    private HttpResponse createKillSwitchResponse(String title, String detail) {
        ErrorResponse error = ErrorResponse.of(503, "SERVICE_UNAVAILABLE", title);
        
        return HttpResponse.ofCode(503)
            .withBody(toJson(error))
            .withHeader(INCIDENT_ACTIVE_HEADER, "true")
            .withHeader(INCIDENT_REASON_HEADER, title)
            .build();
    }

    /**
     * Converts error response to JSON string.
     *
     * @param error the error response
     * @return JSON string representation
     */
    private String toJson(ErrorResponse error) {
        try {
            return OBJECT_MAPPER.writer().writeValueAsString(error);
        } catch (Exception e) {
            LOGGER.error("Failed to serialize error response", e);
            return "{\"error\":\"SERVICE_UNAVAILABLE\",\"title\":\"Service Unavailable\",\"detail\":\"Authentication is temporarily unavailable\"}";
        }
    }

    /**
     * DTO for kill switch status response from incident-service.
     */
    private static class KillSwitchStatus {
        public boolean active;
    }
}
