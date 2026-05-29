package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteSecurityRegistry invariants")
class RouteSecurityRegistryInvariantTest {

    private static final Pattern ROUTER_ROUTE_PATTERN =
            Pattern.compile("\\.with\\(HttpMethod\\.([A-Z]+),\\s*\"([^\"]+)\"");
    private static final Pattern PARAMETER_PATTERN =
            Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern CHECKSUM_PATTERN =
            Pattern.compile("GENERATED_ROUTER_CHECKSUM:\\s*([a-f0-9]{64})", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("critical routes always require policy and blocking audit")
    void criticalRoutesRequirePolicyAndBlockingAudit() {
        Map<String, RouteSecurityMetadata> routes = RouteSecurityRegistry.allRoutes();

        assertThat(routes)
                .isNotEmpty();

        assertThat(routes.entrySet())
                .filteredOn(entry -> entry.getValue().sensitivity() == EndpointSensitivity.CRITICAL)
                .isNotEmpty()
                .allSatisfy(entry -> {
                    RouteSecurityMetadata metadata = entry.getValue();
                    assertThat(metadata.requiresPolicy())
                            .as("requiresPolicy for %s", entry.getKey())
                            .isTrue();
                    assertThat(metadata.requiresBlockingAudit())
                            .as("requiresBlockingAudit for %s", entry.getKey())
                            .isTrue();
                });
    }

    @Test
    @DisplayName("plan-mandated sensitive mutations require policy")
    void planMandatedSensitiveMutationsRequirePolicy() {
        Map<String, RouteSecurityMetadata> routes = RouteSecurityRegistry.allRoutes();

        assertThat(routes)
                .isNotEmpty();

        List<String> policyBackedRoutes = List.of(
                "POST /api/v1/action/pipelines/{pipelineId}/execute",
                "POST /api/v1/collections/{id}/migrate",
                "POST /api/v1/entities/{collection}/export",
                "POST /mcp/v1/tools",
                "POST /api/v1/plugins/{id}/conformance",
                "POST /api/v1/plugins/{id}/disable",
                "POST /api/v1/plugins/{id}/enable",
                "POST /api/v1/plugins/{id}/upgrade",
                "POST /api/v1/plugins/{id}/validate"
        );

        assertThat(policyBackedRoutes)
                .allSatisfy(routeKey -> assertThat(routes.get(routeKey))
                        .as("route metadata for %s", routeKey)
                        .isNotNull()
                        .extracting(RouteSecurityMetadata::requiresPolicy)
                        .as("requiresPolicy for %s", routeKey)
                        .isEqualTo(true));
    }

    @Test
        @DisplayName("critical and policy-backed routes resolve access levels")
        void criticalAndPolicyBackedRoutesResolveAccessLevels() {
        Map<String, RouteSecurityMetadata> routes = RouteSecurityRegistry.allRoutes();

        assertThat(routes)
                .isNotEmpty();

        assertThat(routes.entrySet())
                .filteredOn(entry -> entry.getValue().sensitivity() == EndpointSensitivity.CRITICAL || entry.getValue().requiresPolicy())
                .allSatisfy(entry -> {
                    RouteSecurityMetadata metadata = entry.getValue();
                    DataCloudSecurityFilter.AccessLevel requiredAccess = RouteActionAccessRegistry.requiredAccess(
                            metadata.method(),
                            metadata.canonicalPath());

                    assertThat(requiredAccess)
                            .as("resolved access mapping for %s", entry.getKey())
                            .isNotNull();
                    assertThat(requiredAccess.ordinal())
                            .as("access level floor for %s", entry.getKey())
                            .isGreaterThanOrEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR.ordinal());
                });
    }

    @Test
    @DisplayName("resolved access levels stay in parity with route metadata")
    void resolvedAccessLevelsStayInParityWithRouteMetadata() {
        Map<String, RouteSecurityMetadata> routes = RouteSecurityRegistry.allRoutes();

        assertThat(routes)
                .isNotEmpty();

        assertThat(routes.entrySet())
                .allSatisfy(entry -> {
                    RouteSecurityMetadata metadata = entry.getValue();
                    DataCloudSecurityFilter.AccessLevel resolvedAccess = RouteActionAccessRegistry.requiredAccess(
                            metadata.method(),
                            metadata.canonicalPath());

                    assertThat(resolvedAccess)
                            .as("resolved access mapping for %s", entry.getKey())
                            .isEqualTo(metadata.requiredAccess());
                });
    }

    @Test
    @DisplayName("runtime lookup prefers specific static routes over parameterized matches")
    void runtimeLookupPrefersSpecificStaticRoutes() {
        RouteSecurityMetadata contextSnapshot = RouteSecurityRegistry.lookupRuntimePath("GET", "/api/v1/context/snapshot")
                .orElseThrow(() -> new AssertionError("Missing runtime metadata for /api/v1/context/snapshot"));
        RouteSecurityMetadata entitiesSimilar = RouteSecurityRegistry.lookupRuntimePath("GET", "/api/v1/entities/orders/similar")
                .orElseThrow(() -> new AssertionError("Missing runtime metadata for /api/v1/entities/{collection}/similar"));

        assertThat(contextSnapshot.canonicalPath()).isEqualTo("/api/v1/context/snapshot");
        assertThat(contextSnapshot.requiredAccess()).isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);

        assertThat(entitiesSimilar.canonicalPath()).isEqualTo("/api/v1/entities/{collection}/similar");
        assertThat(entitiesSimilar.requiredAccess()).isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);
    }

    @Test
    @DisplayName("route registry contains both action-plane and compatibility routes")
    void registryContainsActionAndCompatibilityRoutes() {
        Map<String, RouteSecurityMetadata> routes = RouteSecurityRegistry.allRoutes();

        assertThat(routes.entrySet())
                .anySatisfy(entry -> assertThat(entry.getValue().runtimeTruthSurface()).isEqualTo("action_plane"));

        assertThat(routes.entrySet())
                .anySatisfy(entry -> assertThat(entry.getValue().legacyStatus()).isEqualTo("compatibility-only"));
    }

    @Test
    @DisplayName("registry operation set exactly matches router operation set")
    void registryOperationSetExactlyMatchesRouterOperationSet() throws IOException {
        Path repoRoot = resolveRepoRoot();
        Path routerPath = repoRoot.resolve("products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");

        String routerSource = Files.readString(routerPath);
        Set<String> routerOperations = extractRouterOperations(routerSource);
        Set<String> registryOperations = new HashSet<>(RouteSecurityRegistry.allRoutes().keySet());

        assertThat(routerOperations)
                .as("router operations parsed from DataCloudRouterBuilder")
                .isNotEmpty();
        assertThat(registryOperations)
                .as("registry operations exposed by RouteSecurityRegistry")
                .isNotEmpty();

        assertThat(registryOperations)
                .as("RouteSecurityRegistry must contain every runtime router operation")
                .containsExactlyInAnyOrderElementsOf(routerOperations);
        assertThat(registryOperations.size())
                .as("router and registry operation counts must stay in parity")
                .isEqualTo(routerOperations.size());
    }

        @Test
        @DisplayName("registry checksum marker matches router routes")
        void registryChecksumMatchesRouterRoutes() throws IOException {
                Path repoRoot = resolveRepoRoot();
                Path routerPath = repoRoot.resolve("products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java");
                Path registryPath = repoRoot.resolve("products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/RouteSecurityRegistry.java");

                String routerSource = Files.readString(routerPath);
                String registrySource = Files.readString(registryPath);

                Matcher checksumMatcher = CHECKSUM_PATTERN.matcher(registrySource);
                assertThat(checksumMatcher.find())
                                .as("RouteSecurityRegistry checksum marker must be present")
                                .isTrue();

                String expectedChecksum = computeRouterChecksum(routerSource);
                String actualChecksum = checksumMatcher.group(1).toLowerCase();

                assertThat(actualChecksum)
                                .as("RouteSecurityRegistry checksum marker must match DataCloudRouterBuilder route set")
                                .isEqualTo(expectedChecksum);
        }

        private static String computeRouterChecksum(String routerSource) {
                TreeSet<String> routeKeys = new TreeSet<>(extractRouterOperations(routerSource));
                assertThat(routeKeys)
                                .as("DataCloudRouterBuilder should expose runtime route registrations")
                                .isNotEmpty();
                String canonical = String.join("\n", new ArrayList<>(routeKeys));
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
                        return HexFormat.of().formatHex(hash);
                } catch (NoSuchAlgorithmException exception) {
                        throw new IllegalStateException("SHA-256 digest unavailable", exception);
                }
        }

        private static Set<String> extractRouterOperations(String routerSource) {
                Matcher matcher = ROUTER_ROUTE_PATTERN.matcher(routerSource);
                Set<String> routeKeys = new HashSet<>();
                while (matcher.find()) {
                        String method = matcher.group(1);
                        String path = PARAMETER_PATTERN.matcher(matcher.group(2)).replaceAll("{$1}");
                        routeKeys.add(method + " " + path);
                }
                return routeKeys;
        }

        private static Path resolveRepoRoot() {
                Path current = Path.of("").toAbsolutePath();
                while (current != null) {
                        if (Files.exists(current.resolve("products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java"))) {
                                return current;
                        }
                        current = current.getParent();
                }
                throw new IllegalStateException("Unable to locate repository root for RouteSecurityRegistry invariants");
        }
}
