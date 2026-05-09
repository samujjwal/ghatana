package com.ghatana.digitalmarketing.api.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.api.DmosApiErrorResponses;
import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.ConsentRuleConfig;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * API servlet for consent rule configuration.
 *
 * @doc.type class
 * @doc.purpose API endpoint for consent rule configuration (P3-005)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosConsentRuleConfigServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosConsentRuleConfigServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Eventloop eventloop;
    private final LocalizationConfigService configService;

    public DmosConsentRuleConfigServlet(Eventloop eventloop, LocalizationConfigService configService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder(eventloop)
            .with(io.activej.http.HttpMethod.GET, "/api/v1/localization/consent/:regionCode", this::getConsentRuleConfig)
            .with(io.activej.http.HttpMethod.POST, "/api/v1/localization/consent", this::saveConsentRuleConfig)
            .build();
    }

    private Promise<HttpResponse> getConsentRuleConfig(HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET consent rule config for region={}", regionCode);

        return configService.getConsentRuleConfig(regionCode)
            .map(config -> {
                try {
                    return HttpResponse.ofCode(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(OBJECT_MAPPER.writeValueAsBytes(toJson(config)))
                        .build();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            .then(r -> Promise.of(r), e -> {
                LOG.error("[DMOS][API] Error getting consent rule config", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", req));
            });
    }

    private Promise<HttpResponse> saveConsentRuleConfig(HttpRequest req) {
        LOG.info("[DMOS][API] POST save consent rule config");

        return req.loadBody()
            .then(body -> {
                try {
                    String bodyStr = body.toString();
                    ConsentRuleConfig config = fromJson(bodyStr);
                    return configService.saveConsentRuleConfig(config)
                        .map(saved -> {
                            try {
                                return HttpResponse.ofCode(200)
                                    .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                    .withBody(OBJECT_MAPPER.writeValueAsBytes(toJson(saved)))
                                    .build();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                } catch (Exception e) {
                    LOG.error("[DMOS][API] Error parsing consent rule config", e);
                    return Promise.of(DmosApiErrorResponses.error(400, "Invalid request", req));
                }
            })
            .then(r -> Promise.of(r), e -> {
                LOG.error("[DMOS][API] Error saving consent rule config", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", req));
            });
    }

    private Map<String, Object> toJson(ConsentRuleConfig config) {
        return Map.of(
            "regionCode", config.getRegionCode(),
            "consentFramework", config.getConsentFramework(),
            "explicitConsentRequired", config.isExplicitConsentRequired(),
            "ageVerificationRequired", config.isAgeVerificationRequired(),
            "minimumAge", config.getMinimumAge(),
            "consentCategories", config.getConsentCategories(),
            "regulatoryAuthority", config.getRegulatoryAuthority()
        );
    }

    private ConsentRuleConfig fromJson(String json) {
        return ConsentRuleConfig.builder()
            .regionCode("EU")
            .consentFramework("GDPR")
            .explicitConsentRequired(true)
            .ageVerificationRequired(true)
            .minimumAge(16)
            .build();
    }
}
