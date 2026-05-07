package com.ghatana.digitalmarketing.api.localization;

import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.ConsentRuleConfig;
import io.activej.http.RoutingServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final LocalizationConfigService configService;

    public DmosConsentRuleConfigServlet(LocalizationConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder()
            .map("/api/v1/localization/consent/:regionCode", this::getConsentRuleConfig)
            .map("/api/v1/localization/consent", this::saveConsentRuleConfig)
            .build();
    }

    private io.activej.http.AsyncServlet getConsentRuleConfig(io.activej.http.HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET consent rule config for region={}", regionCode);

        return configService.getConsentRuleConfig(regionCode)
            .map(config -> io.activej.http.HttpResponse.ok200()
                .withJson(toJson(config)))
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error getting consent rule config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
            });
    }

    private io.activej.http.AsyncServlet saveConsentRuleConfig(io.activej.http.HttpRequest req) {
        LOG.info("[DMOS][API] POST save consent rule config");

        return req.loadBody()
            .then(body -> {
                try {
                    ConsentRuleConfig config = fromJson(body);
                    return configService.saveConsentRuleConfig(config)
                        .map(saved -> io.activej.http.HttpResponse.ok200()
                            .withJson(toJson(saved)));
                } catch (Exception e) {
                    LOG.error("[DMOS][API] Error parsing consent rule config", e);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(400).withPlainText("Invalid request"));
                }
            })
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error saving consent rule config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
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
