/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * MaxMind GeoIP2 adapter that resolves IP addresses to geographic coordinates
 * using an embedded {@code .mmdb} database file.
 *
 * <p>Gracefully returns {@code null} when lookup fails (unknown IP, local address,
 * corrupted DB, etc.) — the caller ({@link LoginAnomalyDetector}) already handles
 * null coordinates safely.
 *
 * @doc.type class
 * @doc.purpose MaxMind GeoIP2 adapter for IP-to-coordinates resolution (K01-023)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class MaxMindGeoIpResolver implements GeoIpResolver, Closeable {

    private static final Logger log = LoggerFactory.getLogger(MaxMindGeoIpResolver.class);

    private final DatabaseReader reader;

    /**
     * @param dbPath path to MaxMind GeoLite2-City or GeoIP2-City {@code .mmdb} file
     * @throws IOException if the database file cannot be opened
     */
    public MaxMindGeoIpResolver(Path dbPath) throws IOException {
        Objects.requireNonNull(dbPath, "dbPath");
        this.reader = new DatabaseReader.Builder(dbPath.toFile()).build();
        log.info("geoip.maxmind.loaded db={}", dbPath);
    }

    @Override
    public Coordinates resolve(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(ipAddress);
            CityResponse response = reader.city(addr);
            Location loc = response.getLocation();
            if (loc == null || loc.getLatitude() == null || loc.getLongitude() == null) {
                return null;
            }
            return new Coordinates(loc.getLatitude(), loc.getLongitude());
        } catch (Exception e) {
            log.debug("geoip.maxmind.lookup.failed ip={} reason={}", ipAddress, e.getMessage());
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
