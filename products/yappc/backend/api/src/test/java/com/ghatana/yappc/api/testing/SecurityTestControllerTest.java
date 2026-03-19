/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.testing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.api.middleware.RateLimitFilter;
import com.ghatana.yappc.api.testing.dto.DependencyScanRequest;
import com.ghatana.yappc.api.testing.dto.SASTFindings;
import com.ghatana.yappc.api.testing.dto.SASTRequest;
import com.ghatana.yappc.api.testing.dto.SASTStatistics;
import com.ghatana.yappc.api.testing.dto.SecurityFinding;
import com.ghatana.yappc.api.testing.dto.SecurityScanRequest;
import com.ghatana.yappc.api.testing.dto.SecurityScanResult;
import com.ghatana.yappc.api.testing.dto.SecurityScore;
import com.ghatana.yappc.api.testing.dto.Vulnerability;
import com.ghatana.yappc.api.testing.dto.VulnerabilityReport;
import com.ghatana.yappc.api.testing.dto.VulnerabilitySummary;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link SecurityTestController} route wiring and request parsing.
 *
 * @doc.type class
 * @doc.purpose Exercise security testing endpoints through the ActiveJ servlet
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("SecurityTestController Tests")
class SecurityTestControllerTest extends EventloopTestBase {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockSecurityTestService securityTestService;
  private RoutingServlet servlet;

  @BeforeEach
  void setUp() {
    securityTestService = new MockSecurityTestService();
    servlet = new SecurityTestController(securityTestService).securityTestingServlet(eventloop());
  }

  @Test
  @DisplayName("should route base scan endpoint and deserialize request body")
  void shouldRouteBaseScanEndpoint() throws Exception {
    HttpRequest request =
        createJsonRequest(
            "/api/testing/security/scan",
            """
            {
              "projectPath": "/workspace/demo",
              "scanTypes": ["dependencies", "sast"]
            }
            """);

    HttpResponse response = runPromise(() -> servlet.serve(request));
    Map<String, Object> payload = parseJson(response);

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(securityTestService.lastSecurityScanRequest.projectPath())
        .isEqualTo("/workspace/demo");
    assertThat(securityTestService.lastSecurityScanRequest.scanTypes())
        .containsExactly("dependencies", "sast");
    assertThat(payload).containsKey("findings");
    assertThat(payload).containsKey("score");
  }

  @Test
  @DisplayName("should route v1 dependency scan alias")
  void shouldRouteDependencyAliasEndpoint() throws Exception {
    HttpRequest request =
        createJsonRequest(
            "/api/v1/testing/security/dependencies",
            """
            {
              "projectPath": "/workspace/service",
              "includeDevDependencies": true
            }
            """);

    HttpResponse response = runPromise(() -> servlet.serve(request));
    Map<String, Object> payload = parseJson(response);

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(securityTestService.lastDependencyScanRequest.projectPath())
        .isEqualTo("/workspace/service");
    assertThat(securityTestService.lastDependencyScanRequest.includeDevDependencies()).isTrue();
    assertThat(payload).containsKey("vulnerabilities");
    assertThat(payload).containsKey("summary");
  }

  @Test
  @DisplayName("should route SAST endpoint and preserve requested ruleset")
  void shouldRouteSastEndpoint() throws Exception {
    HttpRequest request =
        createJsonRequest(
            "/api/testing/security/sast",
            """
            {
              "projectPath": "/workspace/app",
              "ruleSet": "strict"
            }
            """);

    HttpResponse response = runPromise(() -> servlet.serve(request));
    Map<String, Object> payload = parseJson(response);

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(securityTestService.lastSastRequest.projectPath()).isEqualTo("/workspace/app");
    assertThat(securityTestService.lastSastRequest.ruleSet()).isEqualTo("strict");
    assertThat(payload).containsKey("findings");
    assertThat(payload).containsKey("statistics");
  }

  // ---------------------------------------------------------------------------
  // Rate-limiting tests — verify that RateLimitFilter wrapping the security
  // servlet enforces HTTP-layer rate limits on the security-test endpoints.
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("should allow requests when within rate limit on security scan endpoint")
  void shouldAllowSecurityScanWithinRateLimit() throws Exception {
    // Wrap the security controller servlet with a generous limit
    io.activej.http.AsyncServlet rateLimited = new RateLimitFilter(5, 60_000L, servlet);
    HttpRequest request =
        createJsonRequest(
            "/api/testing/security/scan",
            """
            { "projectPath": "/ws", "scanTypes": [] }
            """);

    HttpResponse response = runPromise(() -> rateLimited.serve(request));

    assertThat(response.getCode()).isEqualTo(200);
    assertThat(response.getHeader(RateLimitFilter.HEADER_LIMIT)).isEqualTo("5");
    assertThat(Integer.parseInt(response.getHeader(RateLimitFilter.HEADER_REMAINING)))
        .isLessThan(5);
  }

  @Test
  @DisplayName("should return 429 after rate limit exhausted on dependency scan endpoint")
  void shouldReturn429WhenRateLimitExhaustedOnDependencyScan() throws Exception {
    // Allow only 2 requests per window
    io.activej.http.AsyncServlet rateLimited = new RateLimitFilter(2, 60_000L, servlet);
    // Must create a fresh HttpRequest for each serve() call: RoutingServlet advances
    // the URL-position pointer (pollUrlPart) on each match and never resets it.
    Supplier<HttpRequest> req =
        () ->
            createJsonRequest(
                "/api/testing/security/dependencies",
                """
                { "projectPath": "/ws", "includeDevDependencies": false }
                """);

    // Consume both allowed slots
    runPromise(() -> rateLimited.serve(req.get()));
    runPromise(() -> rateLimited.serve(req.get()));
    // Third must be rejected
    HttpResponse rejected = runPromise(() -> rateLimited.serve(req.get()));

    assertThat(rejected.getCode()).isEqualTo(429);
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("0");
    assertThat(rejected.getHeader(RateLimitFilter.HEADER_RETRY_AFTER)).isNotNull();
  }

  @Test
  @DisplayName("should isolate rate-limit buckets per client on SAST endpoint")
  void shouldIsolateBucketsPerClientOnSast() throws Exception {
    io.activej.http.AsyncServlet rateLimited = new RateLimitFilter(1, 60_000L, servlet);

    HttpRequest clientA =
        HttpRequest.builder(HttpMethod.POST, "http://localhost/api/testing/security/sast")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.X_FORWARDED_FOR, "10.1.2.3")
            .withBody(
                """
                { "projectPath": "/ws", "ruleSet": "default" }
                """
                    .getBytes(StandardCharsets.UTF_8))
            .build();
    HttpRequest clientB =
        HttpRequest.builder(HttpMethod.POST, "http://localhost/api/testing/security/sast")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.X_FORWARDED_FOR, "10.9.8.7")
            .withBody(
                """
                { "projectPath": "/ws", "ruleSet": "default" }
                """
                    .getBytes(StandardCharsets.UTF_8))
            .build();

    // Exhaust client A's slot
    runPromise(() -> rateLimited.serve(clientA));
    HttpResponse aRejected = runPromise(() -> rateLimited.serve(clientA));
    assertThat(aRejected.getCode()).isEqualTo(429);

    // Client B's first request must still be allowed
    HttpResponse bAllowed = runPromise(() -> rateLimited.serve(clientB));
    assertThat(bAllowed.getCode()).isEqualTo(200);
  }

  private HttpRequest createJsonRequest(String path, String body) {
    return HttpRequest.builder(HttpMethod.POST, "http://localhost" + path)
        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .withBody(body.getBytes(StandardCharsets.UTF_8))
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJson(HttpResponse response) throws Exception {
    return objectMapper.readValue(response.getBody().asArray(), Map.class);
  }

  private static final class MockSecurityTestService extends SecurityTestService {
    private SecurityScanRequest lastSecurityScanRequest;
    private DependencyScanRequest lastDependencyScanRequest;
    private SASTRequest lastSastRequest;

    @Override
    public Promise<SecurityScanResult> runSecurityScan(SecurityScanRequest request) {
      lastSecurityScanRequest = request;
      return Promise.of(
          new SecurityScanResult(
              List.of(
                  new SecurityFinding(
                      "scan-1",
                      "LOW",
                      "Example finding",
                      "Example finding description",
                      request.projectPath(),
                      12,
                      "Review configuration")),
              new SecurityScore(91, "A", 1),
              new Date()));
    }

    @Override
    public Promise<VulnerabilityReport> scanDependencies(DependencyScanRequest request) {
      lastDependencyScanRequest = request;
      return Promise.of(
          new VulnerabilityReport(
              List.of(new Vulnerability("CVE-2026-0001", "HIGH", "Example CVE", "Upgrade package")),
              new VulnerabilitySummary(0, 1, 0, 0),
              new Date()));
    }

    @Override
    public Promise<SASTFindings> runSAST(SASTRequest request) {
      lastSastRequest = request;
      return Promise.of(
          new SASTFindings(
              List.of(
                  new SecurityFinding(
                      "sast-1",
                      "MEDIUM",
                      "Potential injection",
                      "Potential injection description",
                      request.projectPath() + "/src/App.java",
                      27,
                      "Use parameterized queries")),
              new SASTStatistics(1, 0, 0, 7),
              new Date()));
    }
  }
}
