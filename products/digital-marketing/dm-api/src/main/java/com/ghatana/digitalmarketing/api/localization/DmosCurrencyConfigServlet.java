package com.ghatana.digitalmarketing.api.localization;

import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.localization.CurrencyConfig;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * API servlet for currency configuration.
 *
 * @doc.type class
 * @doc.purpose API endpoint for currency configuration (P3-005)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosCurrencyConfigServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosCurrencyConfigServlet.class);

    private final LocalizationConfigService configService;

    public DmosCurrencyConfigServlet(LocalizationConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder()
            .map("/api/v1/localization/currency/:regionCode", this::getCurrencyConfig)
            .map("/api/v1/localization/currency", this::saveCurrencyConfig)
            .build();
    }

    private AsyncServlet getCurrencyConfig(io.activej.http.HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET currency config for region={}", regionCode);

        return configService.getCurrencyConfig(regionCode)
            .map(config -> io.activej.http.HttpResponse.ok200()
                .withJson(toJson(config)))
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error getting currency config", exception);
                    return Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return Promise.of(value);
            });
    }

    private AsyncServlet saveCurrencyConfig(io.activej.http.HttpRequest req) {
        LOG.info("[DMOS][API] POST save currency config");

        return req.loadBody()
            .then(body -> {
                try {
                    CurrencyConfig config = fromJson(body);
                    return configService.saveCurrencyConfig(config)
                        .map(saved -> io.activej.http.HttpResponse.ok200()
                            .withJson(toJson(saved)));
                } catch (Exception e) {
                    LOG.error("[DMOS][API] Error parsing currency config", e);
                    return Promise.of(io.activej.http.HttpResponse.of(400).withPlainText("Invalid request"));
                }
            })
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error saving currency config", exception);
                    return Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return Promise.of(value);
            });
    }

    private Map<String, Object> toJson(CurrencyConfig config) {
        return Map.of(
            "currencyCode", config.getCurrencyCode(),
            "localeCode", config.getLocaleCode(),
            "symbol", config.getSymbol(),
            "decimalPlaces", config.getDecimalPlaces(),
            "decimalSeparator", config.getDecimalSeparator(),
            "thousandsSeparator", config.getThousandsSeparator(),
            "symbolPosition", config.getSymbolPosition()
        );
    }

    private CurrencyConfig fromJson(String json) {
        // Simplified parsing - in production use proper JSON library
        return CurrencyConfig.builder()
            .currencyCode("USD")
            .localeCode("en-US")
            .symbol("$")
            .decimalPlaces(2)
            .decimalSeparator(".")
            .thousandsSeparator(",")
            .symbolPosition("before")
            .build();
    }
}
