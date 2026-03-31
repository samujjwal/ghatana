package com.ghatana.tutorputor.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Internal HTTP client for learner personalization snapshots exposed by the platform.
 *
 * @doc.type class
 * @doc.purpose Fetch learner personalization data for adaptive content generation
 * @doc.layer product
 * @doc.pattern Client
 */
public final class LearnerProfileHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(LearnerProfileHttpClient.class);
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String serviceToken;
    private final String tenantId;

    public LearnerProfileHttpClient(HttpClient httpClient, String baseUrl, String serviceToken, String tenantId) {
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        this.tenantId = tenantId == null ? "" : tenantId;
    }

    public static LearnerProfileHttpClient createFromEnvironment() {
        return new LearnerProfileHttpClient(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build(),
            System.getenv().getOrDefault("TUTORPUTOR_PLATFORM_BASE_URL", "http://localhost:8080"),
            System.getenv().getOrDefault("TUTORPUTOR_PLATFORM_SERVICE_TOKEN", ""),
            System.getenv().getOrDefault("TUTORPUTOR_PLATFORM_TENANT_ID", "")
        );
    }

    public Optional<LearnerPersonalizationSnapshot> getPersonalization(String learnerId, String topic) {
        if (learnerId == null || learnerId.isBlank()) {
            return Optional.empty();
        }

        if (serviceToken.isBlank()) {
            LOG.debug("Skipping learner personalization fetch because TUTORPUTOR_PLATFORM_SERVICE_TOKEN is not configured");
            return Optional.empty();
        }

        String url = baseUrl
            + "/api/v1/learning/learners/"
            + URLEncoder.encode(learnerId, StandardCharsets.UTF_8)
            + "/personalization";

        if (topic != null && !topic.isBlank()) {
            url = url + "?topic=" + URLEncoder.encode(topic, StandardCharsets.UTF_8);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + serviceToken);

        if (!tenantId.isBlank()) {
            requestBuilder.header("x-tenant-id", tenantId);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                requestBuilder.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() != 200) {
                LOG.warn("Learner personalization fetch failed with status {} for learner {}", response.statusCode(), learnerId);
                return Optional.empty();
            }

            return Optional.ofNullable(MAPPER.readValue(response.body(), LearnerPersonalizationSnapshot.class));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warn("Failed to fetch learner personalization for learner {}", learnerId, e);
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class LearnerPersonalizationSnapshot {
        public String learnerId;
        public String preferredDifficulty;
        public String preferredModality;
        public String preferredPacing;
        public String adjustedDifficulty;
        public List<String> preferences = List.of();
        public List<String> knowledgeGaps = List.of();
    }
}
