package com.ghatana.digitalmarketing.api.localization;

import com.ghatana.digitalmarketing.application.localization.LocalizationConfigService;
import com.ghatana.digitalmarketing.domain.localization.TimeZoneConfig;
import io.activej.http.RoutingServlet;
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

    private final LocalizationConfigService configService;

    public DmosTimeZoneConfigServlet(LocalizationConfigService configService) {
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
    }

    public RoutingServlet routing() {
        return RoutingServlet.builder()
            .map("/api/v1/localization/timezone/:regionCode", this::getTimeZoneConfig)
            .map("/api/v1/localization/timezone", this::saveTimeZoneConfig)
            .build();
    }

    private io.activej.http.AsyncServlet getTimeZoneConfig(io.activej.http.HttpRequest req) {
        String regionCode = req.getPathParameter("regionCode");
        LOG.info("[DMOS][API] GET timezone config for region={}", regionCode);

        return configService.getTimeZoneConfig(regionCode)
            .map(config -> io.activej.http.HttpResponse.ok200()
                .withJson(toJson(config)))
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error getting timezone config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
            });
    }

    private io.activej.http.AsyncServlet saveTimeZoneConfig(io.activej.http.HttpRequest req) {
        LOG.info("[DMOS][API] POST save timezone config");

        return req.loadBody()
            .then(body -> {
                try {
                    TimeZoneConfig config = fromJson(body);
                    return configService.saveTimeZoneConfig(config)
                        .map(saved -> io.activej.http.HttpResponse.ok200()
                            .withJson(toJson(saved)));
                } catch (Exception e) {
                    LOG.error("[DMOS][API] Error parsing timezone config", e);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(400).withPlainText("Invalid request"));
                }
            })
            .thenEx((exception, value) -> {
                if (exception != null) {
                    LOG.error("[DMOS][API] Error saving timezone config", exception);
                    return io.activej.promise.Promise.of(io.activej.http.HttpResponse.of(500).withPlainText("Internal server error"));
                }
                return io.activej.promise.Promise.of(value);
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
