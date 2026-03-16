package com.ghatana.appplatform.ems.service;

import io.activej.promise.Promise;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Manages Smart Order Router venue configuration and K-02 compliance constraints
 *              (D02-004). Provides the effective venue list, routing priority, and
 *              instrument-type defaults for algorithm selection.
 * @doc.layer   Domain — EMS SOR configuration
 * @doc.pattern Configuration/policy service; all routing rules sourced from DB (no hard-coding)
 */
public class SorConfigurationService {

    public enum RouteScheme { BEST_PRICE, SEQUENTIAL, SPLIT, DIRECT }

    public record VenueRule(
        String ruleId,
        String instrumentType,  // EQUITY, BOND, DERIVATIVE
        String venueId,
        int priority,           // lower = higher priority
        RouteScheme scheme,
        boolean k02Restricted,  // K-02 compliance flag: regulator-restricted circuit
        double maxParticipationRate
    ) {}

    public record SorConfig(
        String instrumentType,
        List<VenueRule> orderedVenues,
        RouteScheme defaultScheme,
        double defaultParticipationRate
    ) {}

    private final DataSource dataSource;
    private final Executor executor;

    public SorConfigurationService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    /** Load the effective SOR config for a given instrument type. */
    public Promise<SorConfig> getConfig(String instrumentType) {
        return Promise.ofBlocking(executor, () -> {
            List<VenueRule> rules = loadRules(instrumentType);
            // default participation rate from system config; fallback 20%
            double defaultParticipation = loadSystemSetting("sor.default_participation_rate", 0.20);
            RouteScheme defaultScheme = rules.isEmpty() ? RouteScheme.DIRECT
                : rules.get(0).scheme();
            return new SorConfig(instrumentType, rules, defaultScheme, defaultParticipation);
        });
    }

    /** Return K-02 restricted venues — algorithms must skip these. */
    public Promise<List<String>> getRestrictedVenues() {
        return Promise.ofBlocking(executor, () -> {
            List<String> restricted = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT venue_id FROM sor_venue_rules WHERE k02_restricted = TRUE")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) restricted.add(rs.getString("venue_id"));
                }
            }
            return restricted;
        });
    }

    private List<VenueRule> loadRules(String instrumentType) throws Exception {
        List<VenueRule> rules = new ArrayList<>();
        String sql = "SELECT id, instrument_type, venue_id, priority, route_scheme, " +
                     "k02_restricted, max_participation_rate " +
                     "FROM sor_venue_rules WHERE instrument_type = ? AND active = TRUE " +
                     "ORDER BY priority ASC";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, instrumentType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rules.add(new VenueRule(rs.getString("id"), rs.getString("instrument_type"),
                        rs.getString("venue_id"), rs.getInt("priority"),
                        RouteScheme.valueOf(rs.getString("route_scheme")),
                        rs.getBoolean("k02_restricted"),
                        rs.getDouble("max_participation_rate")));
                }
            }
        }
        return rules;
    }

    private double loadSystemSetting(String key, double fallback) {
        String sql = "SELECT value FROM system_settings WHERE key = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Double.parseDouble(rs.getString("value"));
            }
        } catch (Exception ignored) {}
        return fallback;
    }
}
