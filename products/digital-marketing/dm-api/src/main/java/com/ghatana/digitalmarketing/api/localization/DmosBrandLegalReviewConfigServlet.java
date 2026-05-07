package com.ghatana.digitalmarketing.api.localization;

import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.BrandLegalReviewConfig;
import io.activej.http.RoutingServlet;
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

    private final LocalizationConfigService configService;

    public DmosBrandLegalReviewConfigServlet(LocalizationConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder()
            .map("/api/v1/localization/brand-legal/:regionCode", this::getBrandLegalReviewConfig)
            .map("/api/v1/localization/brand-legal", this::saveBrandLegalReviewConfig)
            .build();
    }

    private io.activej.http.AsyncServlet getBrandLegalReviewConfig(io.activej.http.HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET brand legal review config for region={}", regionCode);

        return configService.getBrandLegalReviewConfig(regionCode)
            .map(config -> io.activej.http.HttpResponse.ok200()
                .withJson(toJson(config)))
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error getting brand legal review config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
            });
    }

    private io.activej.http.AsyncServlet saveBrandLegalReviewConfig(io.activej.http.HttpRequest req) {
        LOG.info("[DMOS][API] POST save brand legal review config");

        return req.loadBody()
            .then(body -> {
                try {
                    BrandLegalReviewConfig config = fromJson(body);
                    return configService.saveBrandLegalReviewConfig(config)
                        .map(saved -> io.activej.http.HttpResponse.ok200()
                            .withJson(toJson(saved)));
                } catch (Exception e) {
                    LOG.error("[DMOS][API] Error parsing brand legal review config", e);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(400).withPlainText("Invalid request"));
                }
            })
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error saving brand legal review config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
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
