package com.ghatana.tutorputor.contentstudio.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.core.activej.promise.PromiseUtils;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Production-ready Khan Academy API client for supplementary educational content.
 * 
 * <p>Khan Academy provides free educational videos and exercises. This client:
 * <ul>
 *   <li>Searches for educational videos by topic</li>
 *   <li>Retrieves exercise recommendations</li>
 *   <li>Maps content to learning standards</li>
 *   <li>Provides supplementary learning paths</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Khan Academy API integration for supplementary content
 * @doc.layer product
 * @doc.pattern Client
 */
public class KhanAcademyApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(KhanAcademyApiClient.class);
    private static final String KHAN_API_URL = "https://www.khanacademy.org/api/internal";
    private static final String KHAN_GRAPHQL_URL = "https://www.khanacademy.org/api/internal/graphql";
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    
    private final HttpClient httpClient;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter searchRequestsCounter;
    private final Counter videoRequestsCounter;
    private final Counter exerciseRequestsCounter;
    private final Counter apiErrorsCounter;
    private final Timer apiLatencyTimer;

    /**
     * Creates a new KhanAcademyApiClient.
     *
     * @param httpClient the HTTP client for making requests
     * @param meterRegistry the metrics registry
     */
    public KhanAcademyApiClient(HttpClient httpClient, MeterRegistry meterRegistry) {
        this.httpClient = httpClient;
        this.meterRegistry = meterRegistry;
        
        this.searchRequestsCounter = Counter.builder("tutorputor.khan.search.requests")
            .description("Number of Khan Academy search requests")
            .register(meterRegistry);
        this.videoRequestsCounter = Counter.builder("tutorputor.khan.video.requests")
            .description("Number of Khan Academy video requests")
            .register(meterRegistry);
        this.exerciseRequestsCounter = Counter.builder("tutorputor.khan.exercise.requests")
            .description("Number of Khan Academy exercise requests")
            .register(meterRegistry);
        this.apiErrorsCounter = Counter.builder("tutorputor.khan.errors")
            .description("Number of Khan Academy API errors")
            .register(meterRegistry);
        this.apiLatencyTimer = Timer.builder("tutorputor.khan.latency")
            .description("Khan Academy API latency")
            .register(meterRegistry);
    }

    /**
     * Searches Khan Academy for content matching the topic.
     *
     * @param topic the topic to search for
     * @param contentType filter by content type (video, article, exercise)
     * @param limit maximum number of results
     * @return a promise containing search results
     */
    public Promise<List<KhanSearchResult>> search(String topic, ContentType contentType, int limit) {
        Instant start = Instant.now();
        searchRequestsCounter.increment();
        
        String typeFilter = contentType != null ? "&type=" + contentType.name().toLowerCase() : "";
        String url = String.format(
            "%s/search?query=%s%s&limit=%d",
            KHAN_API_URL,
            URLEncoder.encode(topic, StandardCharsets.UTF_8),
            typeFilter,
            Math.min(limit, 20)
        );
        
        LOG.debug("Searching Khan Academy for topic: {}, type: {}", topic, contentType);

        Promise<List<KhanSearchResult>> promise = httpClient.request(
                HttpRequest.get(url)
                    .withHeader(HttpHeaders.ACCEPT, "application/json")
                    .withHeader(HttpHeaders.USER_AGENT, "TutorPutor/1.0")
                    .build()
            )
            .then(this::extractResponseBody)
            .map(body -> parseSearchResults(body, start))
            .whenException(e -> {
                LOG.error("Khan Academy search failed for topic: {}", topic, e);
                apiErrorsCounter.increment();
            });

        return PromiseUtils.withFallback(promise, new ArrayList<>());
    }

    /**
     * Gets videos related to a topic.
     *
     * @param topic the topic
     * @param limit maximum number of results
     * @return a promise containing video results
     */
    public Promise<List<KhanVideo>> getVideos(String topic, int limit) {
        Instant start = Instant.now();
        videoRequestsCounter.increment();
        
        LOG.debug("Fetching Khan Academy videos for topic: {}", topic);
        
        return search(topic, ContentType.VIDEO, limit)
            .map(results -> results.stream()
                .map(r -> new KhanVideo(
                    r.id(),
                    r.title(),
                    r.description(),
                    r.url(),
                    r.thumbnailUrl(),
                    0, // Duration not available in search
                    r.domain()
                ))
                .toList());
    }

    /**
     * Gets exercises related to a topic.
     *
     * @param topic the topic
     * @param limit maximum number of results
     * @return a promise containing exercise results
     */
    public Promise<List<KhanExercise>> getExercises(String topic, int limit) {
        Instant start = Instant.now();
        exerciseRequestsCounter.increment();
        
        LOG.debug("Fetching Khan Academy exercises for topic: {}", topic);
        
        return search(topic, ContentType.EXERCISE, limit)
            .map(results -> results.stream()
                .map(r -> new KhanExercise(
                    r.id(),
                    r.title(),
                    r.description(),
                    r.url(),
                    r.domain(),
                    List.of() // Skills not available in search
                ))
                .toList());
    }

    /**
     * Finds supplementary content for a learning claim.
     *
     * @param claimText the learning claim text
     * @param preferredTypes preferred content types
     * @return a promise containing supplementary content
     */
    public Promise<SupplementaryContentResult> findSupplementaryContent(
            String claimText, List<ContentType> preferredTypes) {
        
        LOG.info("Finding Khan Academy supplementary content for claim");
        
        List<Promise<List<KhanSearchResult>>> searches = preferredTypes.stream()
            .map(type -> search(claimText, type, 3))
            .toList();

        return PromiseUtils.all(searches)
            .map(resultLists -> {
                List<KhanSearchResult> allResults = new ArrayList<>();
                for (List<KhanSearchResult> results : resultLists) {
                    allResults.addAll(results);
                }
                
                if (allResults.isEmpty()) {
                    return new SupplementaryContentResult(
                        false,
                        "No supplementary Khan Academy content found",
                        List.of(),
                        List.of(),
                        List.of()
                    );
                }
                
                List<String> videoLinks = new ArrayList<>();
                List<String> exerciseLinks = new ArrayList<>();
                List<String> articleLinks = new ArrayList<>();
                
                for (KhanSearchResult result : allResults) {
                    switch (result.contentType()) {
                        case VIDEO -> videoLinks.add(result.url());
                        case EXERCISE -> exerciseLinks.add(result.url());
                        case ARTICLE -> articleLinks.add(result.url());
                    }
                }
                
                return new SupplementaryContentResult(
                    true,
                    "Found " + allResults.size() + " supplementary resources",
                    videoLinks,
                    exerciseLinks,
                    articleLinks
                );
            });
    }

    /**
     * Gets a learning path for a topic.
     *
     * @param topic the topic
     * @param gradeLevel the grade level
     * @return a promise containing the learning path
     */
    public Promise<LearningPath> getLearningPath(String topic, String gradeLevel) {
        LOG.debug("Building learning path for topic: {} at grade: {}", topic, gradeLevel);
        
        // Get a mix of content types for a complete learning path
        return search(topic, null, 10)
            .map(results -> {
                List<LearningPathStep> steps = new ArrayList<>();
                int order = 1;
                
                // First, add introductory videos
                for (KhanSearchResult result : results) {
                    if (result.contentType() == ContentType.VIDEO && 
                        (result.title().toLowerCase().contains("intro") ||
                         result.title().toLowerCase().contains("overview"))) {
                        steps.add(new LearningPathStep(
                            order++,
                            "Watch: " + result.title(),
                            result.url(),
                            StepType.VIDEO,
                            5
                        ));
                        if (order > 2) break;
                    }
                }
                
                // Then add core content
                for (KhanSearchResult result : results) {
                    if (result.contentType() == ContentType.VIDEO && 
                        !result.title().toLowerCase().contains("intro")) {
                        steps.add(new LearningPathStep(
                            order++,
                            "Learn: " + result.title(),
                            result.url(),
                            StepType.VIDEO,
                            10
                        ));
                        if (order > 5) break;
                    }
                }
                
                // Add practice exercises
                for (KhanSearchResult result : results) {
                    if (result.contentType() == ContentType.EXERCISE) {
                        steps.add(new LearningPathStep(
                            order++,
                            "Practice: " + result.title(),
                            result.url(),
                            StepType.EXERCISE,
                            15
                        ));
                        if (order > 8) break;
                    }
                }
                
                return new LearningPath(
                    topic,
                    gradeLevel,
                    steps,
                    steps.stream().mapToInt(LearningPathStep::estimatedMinutes).sum()
                );
            });
    }

    private Promise<String> extractResponseBody(HttpResponse response) {
        return response.loadBody().map(buf -> buf.asString(StandardCharsets.UTF_8));
    }

    private List<KhanSearchResult> parseSearchResults(String json, Instant start) {
        List<KhanSearchResult> results = new ArrayList<>();
        
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode hits = root.isArray() ? root : root.path("results");
            
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    ContentType type = parseContentType(hit.path("kind").asText());
                    
                    results.add(new KhanSearchResult(
                        hit.path("id").asText(),
                        hit.path("title").asText(),
                        hit.path("description").asText(""),
                        buildUrl(hit),
                        hit.path("image_url").asText(""),
                        type,
                        hit.path("domain_slug").asText("")
                    ));
                }
            }
            
            apiLatencyTimer.record(Duration.between(start, Instant.now()));
            LOG.debug("Khan Academy search returned {} results", results.size());
            
        } catch (Exception e) {
            LOG.error("Failed to parse Khan Academy search results", e);
            apiErrorsCounter.increment();
        }
        
        return results;
    }

    private ContentType parseContentType(String kind) {
        if (kind == null) return ContentType.ARTICLE;
        
        return switch (kind.toLowerCase()) {
            case "video" -> ContentType.VIDEO;
            case "exercise" -> ContentType.EXERCISE;
            case "article" -> ContentType.ARTICLE;
            case "tutorial" -> ContentType.ARTICLE;
            default -> ContentType.ARTICLE;
        };
    }

    private String buildUrl(JsonNode node) {
        String slug = node.path("slug").asText("");
        String path = node.path("relative_url").asText("");
        
        if (!path.isEmpty()) {
            return "https://www.khanacademy.org" + path;
        } else if (!slug.isEmpty()) {
            return "https://www.khanacademy.org/" + slug;
        }
        return "";
    }

    // =========================================================================
    // Enums and Record Classes
    // =========================================================================

    /**
     * Types of content available on Khan Academy.
     */
    public enum ContentType {
        VIDEO,
        EXERCISE,
        ARTICLE
    }

    /**
     * Types of learning path steps.
     */
    public enum StepType {
        VIDEO,
        EXERCISE,
        READING,
        QUIZ
    }

    /**
     * Search result from Khan Academy.
     */
    public record KhanSearchResult(
        String id,
        String title,
        String description,
        String url,
        String thumbnailUrl,
        ContentType contentType,
        String domain
    ) {}

    /**
     * Khan Academy video metadata.
     */
    public record KhanVideo(
        String id,
        String title,
        String description,
        String url,
        String thumbnailUrl,
        int durationSeconds,
        String domain
    ) {}

    /**
     * Khan Academy exercise metadata.
     */
    public record KhanExercise(
        String id,
        String title,
        String description,
        String url,
        String domain,
        List<String> skills
    ) {}

    /**
     * Result of supplementary content search.
     */
    public record SupplementaryContentResult(
        boolean found,
        String summary,
        List<String> videoLinks,
        List<String> exerciseLinks,
        List<String> articleLinks
    ) {}

    /**
     * A structured learning path.
     */
    public record LearningPath(
        String topic,
        String gradeLevel,
        List<LearningPathStep> steps,
        int totalEstimatedMinutes
    ) {}

    /**
     * A single step in a learning path.
     */
    public record LearningPathStep(
        int order,
        String title,
        String url,
        StepType type,
        int estimatedMinutes
    ) {}
}
