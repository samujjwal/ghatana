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
 * Production-ready OpenStax API client for curriculum-aligned educational content.
 * 
 * <p>OpenStax provides free, peer-reviewed textbooks. This client:
 * <ul>
 *   <li>Searches for relevant textbook content</li>
 *   <li>Retrieves content by subject and topic</li>
 *   <li>Aligns claims with curriculum standards</li>
 *   <li>Provides source attribution for generated content</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose OpenStax API integration for curriculum alignment
 * @doc.layer product
 * @doc.pattern Client
 */
public class OpenStaxApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenStaxApiClient.class);
    private static final String OPENSTAX_API_URL = "https://openstax.org/api/v2";
    private static final String OPENSTAX_SEARCH_URL = "https://openstax.org/rex/api/search";
    private static final ObjectMapper MAPPER = JsonUtils.getDefaultMapper();
    
    private final HttpClient httpClient;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter searchRequestsCounter;
    private final Counter bookRequestsCounter;
    private final Counter apiErrorsCounter;
    private final Timer apiLatencyTimer;

    /**
     * Creates a new OpenStaxApiClient.
     *
     * @param httpClient the HTTP client for making requests
     * @param meterRegistry the metrics registry
     */
    public OpenStaxApiClient(HttpClient httpClient, MeterRegistry meterRegistry) {
        this.httpClient = httpClient;
        this.meterRegistry = meterRegistry;
        
        this.searchRequestsCounter = Counter.builder("tutorputor.openstax.search.requests")
            .description("Number of OpenStax search requests")
            .register(meterRegistry);
        this.bookRequestsCounter = Counter.builder("tutorputor.openstax.book.requests")
            .description("Number of OpenStax book requests")
            .register(meterRegistry);
        this.apiErrorsCounter = Counter.builder("tutorputor.openstax.errors")
            .description("Number of OpenStax API errors")
            .register(meterRegistry);
        this.apiLatencyTimer = Timer.builder("tutorputor.openstax.latency")
            .description("OpenStax API latency")
            .register(meterRegistry);
    }

    /**
     * Searches OpenStax for content matching the topic.
     *
     * @param topic the topic to search for
     * @param subject optional subject filter (e.g., "Physics", "Biology")
     * @param limit maximum number of results
     * @return a promise containing matching content results
     */
    public Promise<List<OpenStaxSearchResult>> search(String topic, String subject, int limit) {
        Instant start = Instant.now();
        searchRequestsCounter.increment();
        
        String url = String.format(
            "%s?q=%s&per_page=%d",
            OPENSTAX_SEARCH_URL,
            URLEncoder.encode(topic, StandardCharsets.UTF_8),
            Math.min(limit, 20)
        );
        
        LOG.debug("Searching OpenStax for topic: {}, subject: {}", topic, subject);

        Promise<List<OpenStaxSearchResult>> promise = httpClient.request(
                HttpRequest.get(url)
                    .withHeader(HttpHeaders.ACCEPT, "application/json")
                    .build()
            )
            .then(this::extractResponseBody)
            .map(body -> parseSearchResults(body, start))
            .whenException(e -> {
                LOG.error("OpenStax search failed for topic: {}", topic, e);
                apiErrorsCounter.increment();
            });

        return PromiseUtils.withFallback(promise, new ArrayList<>());
    }

    /**
     * Lists available OpenStax books/textbooks.
     *
     * @return a promise containing available books
     */
    public Promise<List<OpenStaxBook>> listBooks() {
        Instant start = Instant.now();
        bookRequestsCounter.increment();
        
        String url = OPENSTAX_API_URL + "/pages";
        
        LOG.debug("Fetching OpenStax book list");

        Promise<List<OpenStaxBook>> promise = httpClient.request(
                HttpRequest.get(url)
                    .withHeader(HttpHeaders.ACCEPT, "application/json")
                    .build()
            )
            .then(this::extractResponseBody)
            .map(body -> parseBookList(body, start))
            .whenException(e -> {
                LOG.error("OpenStax book list fetch failed", e);
                apiErrorsCounter.increment();
            });

        return PromiseUtils.withFallback(promise, new ArrayList<>());
    }

    /**
     * Finds curriculum-aligned content for a learning claim.
     *
     * @param claimText the learning claim text
     * @param domain the subject domain (e.g., "SCIENCE", "MATH")
     * @param gradeLevel the grade level
     * @return a promise containing curriculum alignment result
     */
    public Promise<CurriculumAlignmentResult> findAlignedContent(
            String claimText, String domain, String gradeLevel) {
        
        LOG.info("Finding OpenStax content aligned to claim in domain: {}", domain);
        
        String subject = mapDomainToSubject(domain);
        
        return search(claimText, subject, 5)
            .map(results -> {
                if (results.isEmpty()) {
                    return new CurriculumAlignmentResult(
                        false,
                        0.0,
                        "No aligned OpenStax content found",
                        List.of(),
                        null
                    );
                }
                
                // Score alignment based on result relevance
                double totalScore = 0;
                List<String> alignedTopics = new ArrayList<>();
                String primarySource = null;
                
                for (OpenStaxSearchResult result : results) {
                    totalScore += result.relevance();
                    alignedTopics.add(result.title());
                    if (primarySource == null) {
                        primarySource = result.url();
                    }
                }
                
                double avgScore = totalScore / results.size();
                
                return new CurriculumAlignmentResult(
                    avgScore > 0.3,
                    avgScore,
                    avgScore > 0.3 
                        ? "Claim aligns with OpenStax curriculum content"
                        : "Limited curriculum alignment found",
                    alignedTopics,
                    primarySource
                );
            });
    }

    /**
     * Gets recommended prerequisites for a topic.
     *
     * @param topic the topic
     * @param domain the subject domain
     * @return a promise containing prerequisite recommendations
     */
    public Promise<List<PrerequisiteRecommendation>> getPrerequisites(String topic, String domain) {
        LOG.debug("Fetching prerequisites for topic: {} in domain: {}", topic, domain);
        
        // Search for the topic and extract related concepts
        return search(topic + " introduction fundamentals", mapDomainToSubject(domain), 10)
            .map(results -> {
                List<PrerequisiteRecommendation> prerequisites = new ArrayList<>();
                
                for (OpenStaxSearchResult result : results) {
                    // Filter for introductory/foundational content
                    String titleLower = result.title().toLowerCase();
                    if (titleLower.contains("introduction") || 
                        titleLower.contains("fundamentals") ||
                        titleLower.contains("basic") ||
                        titleLower.contains("overview")) {
                        
                        prerequisites.add(new PrerequisiteRecommendation(
                            result.title(),
                            result.snippet(),
                            result.url(),
                            result.relevance()
                        ));
                    }
                }
                
                return prerequisites;
            });
    }

    private Promise<String> extractResponseBody(HttpResponse response) {
        return response.loadBody().map(buf -> buf.asString(StandardCharsets.UTF_8));
    }

    private List<OpenStaxSearchResult> parseSearchResults(String json, Instant start) {
        List<OpenStaxSearchResult> results = new ArrayList<>();
        
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode hits = root.path("hits");
            
            if (hits.isArray()) {
                for (JsonNode hit : hits) {
                    JsonNode source = hit.path("_source");
                    
                    results.add(new OpenStaxSearchResult(
                        source.path("title").asText(),
                        source.path("content").asText("").substring(0, 
                            Math.min(500, source.path("content").asText("").length())),
                        source.path("book_title").asText(""),
                        source.path("url").asText(""),
                        hit.path("_score").asDouble(0.5) / 10.0 // Normalize score to 0-1
                    ));
                }
            }
            
            apiLatencyTimer.record(Duration.between(start, Instant.now()));
            LOG.debug("OpenStax search returned {} results", results.size());
            
        } catch (Exception e) {
            LOG.error("Failed to parse OpenStax search results", e);
            apiErrorsCounter.increment();
        }
        
        return results;
    }

    private List<OpenStaxBook> parseBookList(String json, Instant start) {
        List<OpenStaxBook> books = new ArrayList<>();
        
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode items = root.path("items");
            
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if ("books".equals(item.path("type").asText())) {
                        books.add(new OpenStaxBook(
                            item.path("id").asText(),
                            item.path("title").asText(),
                            item.path("slug").asText(),
                            item.path("subjects").isArray() 
                                ? parseSubjects(item.path("subjects"))
                                : List.of()
                        ));
                    }
                }
            }
            
            apiLatencyTimer.record(Duration.between(start, Instant.now()));
            LOG.debug("OpenStax returned {} books", books.size());
            
        } catch (Exception e) {
            LOG.error("Failed to parse OpenStax book list", e);
            apiErrorsCounter.increment();
        }
        
        return books;
    }

    private List<String> parseSubjects(JsonNode subjectsNode) {
        List<String> subjects = new ArrayList<>();
        for (JsonNode subject : subjectsNode) {
            subjects.add(subject.path("name").asText());
        }
        return subjects;
    }

    private String mapDomainToSubject(String domain) {
        if (domain == null) return "";
        
        return switch (domain.toUpperCase()) {
            case "MATH" -> "Math";
            case "SCIENCE" -> "Science";
            case "PHYSICS" -> "Physics";
            case "CHEMISTRY" -> "Chemistry";
            case "BIOLOGY" -> "Biology";
            case "ECONOMICS" -> "Economics";
            case "PSYCHOLOGY" -> "Psychology";
            case "SOCIOLOGY" -> "Sociology";
            case "HISTORY" -> "History";
            default -> domain;
        };
    }

    // =========================================================================
    // Record Classes
    // =========================================================================

    /**
     * Search result from OpenStax.
     */
    public record OpenStaxSearchResult(
        String title,
        String snippet,
        String bookTitle,
        String url,
        double relevance
    ) {}

    /**
     * OpenStax textbook metadata.
     */
    public record OpenStaxBook(
        String id,
        String title,
        String slug,
        List<String> subjects
    ) {}

    /**
     * Result of curriculum alignment check.
     */
    public record CurriculumAlignmentResult(
        boolean aligned,
        double confidenceScore,
        String explanation,
        List<String> alignedTopics,
        String primarySourceUrl
    ) {}

    /**
     * Prerequisite content recommendation.
     */
    public record PrerequisiteRecommendation(
        String title,
        String description,
        String url,
        double relevance
    ) {}
}
