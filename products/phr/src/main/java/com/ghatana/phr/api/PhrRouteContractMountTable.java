/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kernel-contract-backed backend mount table for the PHR HTTP server.
 *
 * @doc.type class
 * @doc.purpose Builds stable backend route mounts from the canonical PHR route contract
 * @doc.layer product
 * @doc.pattern Contract Adapter
 */
final class PhrRouteContractMountTable {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Path ROUTE_CONTRACT_PATH = resolveRouteContractPath();

    private PhrRouteContractMountTable() {
    }

    static List<MountSpec> loadStableMounts() {
        return loadStableMounts(ROUTE_CONTRACT_PATH);
    }

    static List<MountSpec> loadStableMounts(Path routeContractPath) {
        if (!Files.exists(routeContractPath)) {
            throw new IllegalStateException("PHR route contract file not found: " + routeContractPath);
        }

        try {
            JsonNode routesNode = JSON.readTree(routeContractPath.toFile()).path("routes");
            if (!routesNode.isArray()) {
                throw new IllegalStateException("PHR route contract is missing routes array: " + routeContractPath);
            }

            Map<MountTarget, MountSpec> mountsByTarget = new LinkedHashMap<>();
            for (JsonNode routeNode : routesNode) {
                String stability = routeNode.path("stability").asText();
                String apiEndpoint = routeNode.path("apiEndpoint").asText(null);
                if (apiEndpoint == null || apiEndpoint.isBlank()) {
                    continue;
                }
                if (!"stable".equals(stability)) {
                    continue;
                }
                if (!hasBackendSurface(routeNode) && !"/api/v1/route-entitlements".equals(apiEndpoint)) {
                    continue;
                }

                MountSpec spec = mountSpecForEndpoint(apiEndpoint);
                mountsByTarget.putIfAbsent(spec.target(), spec);
            }

            List<MountSpec> mounts = new ArrayList<>(mountsByTarget.values());
            mounts.sort(Comparator.comparingInt(spec -> spec.target().order()));
            if (mounts.isEmpty()) {
                throw new IllegalStateException("PHR route contract produced no stable backend mounts");
            }
            return List.copyOf(mounts);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read PHR route contract: " + routeContractPath, e);
        }
    }

    private static MountSpec mountSpecForEndpoint(String apiEndpoint) {
        String endpoint = normalizeEndpoint(apiEndpoint);
        if (endpoint.equals("/api/v1/dashboard")) {
            return new MountSpec("/api/v1/dashboard", MountTarget.DASHBOARD);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/records/documents")) {
            return new MountSpec("/api/v1/records/documents/*", MountTarget.DOCUMENTS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/records/imaging")) {
            return new MountSpec("/api/v1/records/imaging/*", MountTarget.IMAGING);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/records")) {
            return new MountSpec("/api/v1/records/*", MountTarget.RECORDS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/consents")) {
            return new MountSpec("/api/v1/consents/*", MountTarget.CONSENTS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/clinical/conditions")) {
            return new MountSpec("/api/v1/clinical/conditions/*", MountTarget.CONDITIONS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/clinical")) {
            return new MountSpec("/api/v1/clinical/*", MountTarget.CLINICAL);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/emergency")) {
            return new MountSpec("/api/v1/emergency/*", MountTarget.EMERGENCY);
        }
        if (endpoint.equals("/api/v1/release-readiness")) {
            return new MountSpec("/api/v1/release-readiness", MountTarget.RELEASE_READINESS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/appointments")) {
            return new MountSpec("/api/v1/appointments/*", MountTarget.APPOINTMENTS);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/admin")) {
            return new MountSpec("/api/v1/admin/*", MountTarget.ADMIN);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/audit")) {
            return new MountSpec("/api/v1/audit/*", MountTarget.AUDIT);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/notifications")) {
            return new MountSpec("/api/v1/notifications/*", MountTarget.NOTIFICATIONS);
        }
        if (endpoint.equals("/api/v1/profile/settings")) {
            return new MountSpec("/api/v1/profile/settings", MountTarget.PROFILE_SETTINGS);
        }
        if (endpoint.equals("/api/v1/profile")) {
            return new MountSpec("/api/v1/profile", MountTarget.PROFILE);
        }
        if (startsWithRoutePrefix(endpoint, "/api/v1/hie")) {
            return new MountSpec("/api/v1/hie/*", MountTarget.HIE);
        }
        if (endpoint.equals("/api/v1/route-entitlements")) {
            return new MountSpec("/api/v1/route-entitlements", MountTarget.ROUTE_ENTITLEMENTS);
        }

        throw new IllegalStateException("PHR route contract endpoint has no backend mount target: " + apiEndpoint);
    }

    private static String normalizeEndpoint(String endpoint) {
        String normalized = endpoint.replaceAll(":\\w+", ":id");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean startsWithRoutePrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean hasBackendSurface(JsonNode routeNode) {
        JsonNode surfaceNode = routeNode.path("surface");
        if (!surfaceNode.isArray()) {
            return false;
        }
        for (JsonNode surface : surfaceNode) {
            if ("backend".equals(surface.asText())) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveRouteContractPath() {
        Path configuredPath = Path.of(System.getProperty(
            "phr.route.contract.path",
            "products/phr/config/phr-route-contract.json"
        ));
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(configuredPath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        return Path.of("").toAbsolutePath().resolve(configuredPath).normalize();
    }

    record MountSpec(String path, MountTarget target) {
        MountSpec {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path cannot be blank");
            }
            if (target == null) {
                throw new IllegalArgumentException("target cannot be null");
            }
        }
    }

    enum MountTarget {
        DASHBOARD(10),
        DOCUMENTS(20),
        IMAGING(30),
        RECORDS(40),
        CONSENTS(50),
        CONDITIONS(60),
        CLINICAL(70),
        EMERGENCY(80),
        RELEASE_READINESS(90),
        APPOINTMENTS(100),
        ADMIN(110),
        AUDIT(120),
        NOTIFICATIONS(130),
        PROFILE_SETTINGS(140),
        PROFILE(150),
        HIE(160),
        ROUTE_ENTITLEMENTS(170);

        private final int order;

        MountTarget(int order) {
            this.order = order;
        }

        int order() {
            return order;
        }
    }
}
