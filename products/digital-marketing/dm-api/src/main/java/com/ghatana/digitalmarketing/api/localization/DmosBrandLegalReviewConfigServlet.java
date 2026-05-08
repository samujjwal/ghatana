package com.ghatana.digitalmarketing.api.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.BrandLegalReviewConfig;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * API servlet for brand/legal review configuration.
 *
 * @doc.type class
 * @doc.purpose API endpoint for brand/legal review configuration (P3-005)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosBrandLegalReviewConfigServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosBrandLegalReviewConfigServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Eventloop eventloop;
    private final LocalizationConfigService configService;

    public DmosBrandLegalReviewConfigServlet(Eventloop eventloop, LocalizationConfigService configService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder(eventloop)
            .with(io.activej.http.HttpMethod.GET, "/api/v1/localization/brand-legal/:regionCode", this::getBrandLegalReviewConfig)
            .with(io.activej.http.HttpMethod.POST, "/api/v1/localization/brand-legal", this::saveBrandLegalReviewConfig)
            .build();
    }

    private Promise<HttpResponse> getBrandLegalReviewConfig(HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET brand legal review config for region={}", regionCode);

        return configService.getBrandLegalReviewConfig(regionCode)
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
                LOG.error("[DMOS][API] Error getting brand legal review config", e);
                return Promise.of(HttpResponse.ofCode(500).withPlainText("Internal server error").build());
            });
    }

    private Promise<HttpResponse> saveBrandLegalReviewConfig(HttpRequest req) {
        LOG.info("[DMOS][API] POST save brand legal review config");

        return req.loadBody()
            .then(body -> {
                try {
                    String bodyStr = body.toString();
                    BrandLegalReviewConfig config = fromJson(bodyStr);
                    return configService.saveBrandLegalReviewConfig(config)
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
                    LOG.error("[DMOS][API] Error parsing brand legal review config", e);
                    return Promise.of(HttpResponse.ofCode(400).withPlainText("Invalid request").build());
                }
            })
            .then(r -> Promise.of(r), e -> {
                LOG.error("[DMOS][API] Error saving brand legal review config", e);
                return Promise.of(HttpResponse.ofCode(500).withPlainText("Internal server error").build());
            });
    }

    private Map<String, Object> toJson(BrandLegalReviewConfig config) {
        return Map.of(
            "regionCode", config.getRegionCode(),
            "reviewFramework", config.getReviewFramework(),
            "legalReviewRequired", config.isLegalReviewRequired(),
            "brandReviewRequired", config.isBrandReviewRequired(),
            "prohibitedPhrases", config.getProhibitedPhrases(),
            "requiredDisclaimers", config.getRequiredDisclaimers(),
            "industrySpecificRules", config.getIndustrySpecificRules(),
            "reviewTurnaroundDays", config.getReviewTurnaroundDays(),
            "approvalAuthority", config.getApprovalAuthority()
        );
    }

    private BrandLegalReviewConfig fromJson(String json) {
        return BrandLegalReviewConfig.builder()
            .regionCode("EU")
            .reviewFramework("GDPR")
            .legalReviewRequired(true)
            .brandReviewRequired(true)
            .reviewTurnaroundDays(5)
            .build();
    }
}
