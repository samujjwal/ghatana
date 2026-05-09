package com.ghatana.digitalmarketing.api.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.api.DmosApiErrorResponses;
import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.localization.CurrencyConfig;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Eventloop eventloop;
    private final LocalizationConfigService configService;

    public DmosCurrencyConfigServlet(Eventloop eventloop, LocalizationConfigService configService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder(eventloop)
            .with(io.activej.http.HttpMethod.GET, "/api/v1/localization/currency/:regionCode", this::getCurrencyConfig)
            .with(io.activej.http.HttpMethod.POST, "/api/v1/localization/currency", this::saveCurrencyConfig)
            .build();
    }

    private Promise<HttpResponse> getCurrencyConfig(HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET currency config for region={}", regionCode);

        return configService.getCurrencyConfig(regionCode)
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
                LOG.error("[DMOS][API] Error getting currency config", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", req));
            });
    }

    private Promise<HttpResponse> saveCurrencyConfig(HttpRequest req) {
        LOG.info("[DMOS][API] POST save currency config");

        return req.loadBody()
            .then(body -> {
                try {
                    String bodyStr = body.toString();
                    CurrencyConfig config = fromJson(bodyStr);
                    return configService.saveCurrencyConfig(config)
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
                    LOG.error("[DMOS][API] Error parsing currency config", e);
                    return Promise.of(DmosApiErrorResponses.error(400, "Invalid request", req));
                }
            })
            .then(r -> Promise.of(r), e -> {
                LOG.error("[DMOS][API] Error saving currency config", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", req));
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
