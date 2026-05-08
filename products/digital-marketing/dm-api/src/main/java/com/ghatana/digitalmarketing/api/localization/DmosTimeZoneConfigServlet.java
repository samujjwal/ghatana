package com.ghatana.digitalmarketing.api.localization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.TimeZoneConfig;
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
 * API servlet for time zone configuration.
 *
 * @doc.type class
 * @doc.purpose API endpoint for time zone configuration (P3-005)
 * @doc.layer product
 * @doc.pattern Servlet
 */
public final class DmosTimeZoneConfigServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosTimeZoneConfigServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Eventloop eventloop;
    private final LocalizationConfigService configService;

    public DmosTimeZoneConfigServlet(Eventloop eventloop, LocalizationConfigService configService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder(eventloop)
            .with(io.activej.http.HttpMethod.GET, "/api/v1/localization/timezone/:regionCode", this::getTimeZoneConfig)
            .with(io.activej.http.HttpMethod.POST, "/api/v1/localization/timezone", this::saveTimeZoneConfig)
            .build();
    }

    private Promise<HttpResponse> getTimeZoneConfig(HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET timezone config for region={}", regionCode);

        return configService.getTimeZoneConfig(regionCode)
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
                LOG.error("[DMOS][API] Error getting timezone config", e);
                return Promise.of(HttpResponse.ofCode(500).withPlainText("Internal server error").build());
            });
    }

    private Promise<HttpResponse> saveTimeZoneConfig(HttpRequest req) {
        LOG.info("[DMOS][API] POST save timezone config");

        return req.loadBody()
            .then(body -> {
                try {
                    String bodyStr = body.toString();
                    TimeZoneConfig config = fromJson(bodyStr);
                    return configService.saveTimeZoneConfig(config)
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
                    LOG.error("[DMOS][API] Error parsing timezone config", e);
                    return Promise.of(HttpResponse.ofCode(400).withPlainText("Invalid request").build());
                }
            })
            .then(r -> Promise.of(r), e -> {
                LOG.error("[DMOS][API] Error saving timezone config", e);
                return Promise.of(HttpResponse.ofCode(500).withPlainText("Internal server error").build());
            });
    }

    private Map<String, Object> toJson(TimeZoneConfig config) {
        return Map.of(
            "regionCode", config.getRegionCode(),
            "timeZoneId", config.getTimeZoneId(),
            "displayName", config.getDisplayName(),
            "utcOffset", config.getUtcOffset(),
            "observesDST", config.observesDST()
        );
    }

    private TimeZoneConfig fromJson(String json) {
        return TimeZoneConfig.builder()
            .regionCode("US")
            .timeZoneId("America/New_York")
            .displayName("Eastern Time")
            .utcOffset("-05:00")
            .observesDST(true)
            .build();
    }
}
