package com.ghatana.tutorputor.contentstudio.knowledge;

import io.activej.http.HttpClient;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Unified Knowledge Base Service that orchestrates all external knowledge APIs.
 * 
 * <p>This service provides:
 * <ul>
 *   <li>Unified search across Wikipedia, OpenStax, and Khan Academy</li>
 *   <li>Fact verification using multiple sources</li>
 *   <li>Curriculum alignment checking</li>
 *   <li>Supplementary content discovery</li>
 *   <li>Caching for performance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unified knowledge base orchestration service
 * @doc.layer product
 * @doc.pattern Facade
 */
public class KnowledgeBaseService {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBaseService.class);
    
    private final WikipediaApiClient wikipediaClient;
    private final OpenStaxApiClient openStaxClient;
    private final KhanAcademyApiClient khanClient;
    private final MeterRegistry meterRegistry;
    
    // Simple cache for fact verification results (in production, use Redis/Caffeine)
    private final ConcurrentMap<String, FactVerificationResult> factCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * Creates a new KnowledgeBaseService with all API clients.
     *
     * @param httpClient the HTTP client for making requests
     * @param meterRegistry the metrics registry
     */
    public KnowledgeBaseService(HttpClient httpClient, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.wikipediaClient = new WikipediaApiClient(httpClient, meterRegistry);
        this.openStaxClient = new OpenStaxApiClient(httpClient, meterRegistry);
        this.khanClient = new KhanAcademyApiClient(httpClient, meterRegistry);
        
        LOG.info("KnowledgeBaseService initialized with Wikipedia, OpenStax, and Khan Academy clients");
    }

    /**
     * Verifies a learning claim against multiple knowledge sources.
     *
     * @param claimText the claim to verify
     * @param domain the subject domain
     * @param gradeLevel the grade level
     * @return a promise containing the comprehensive verification result
     */
    public Promise<FactVerificationResult> verifyLearningClaim(
            String claimText, String domain, String gradeLevel) {
        
        LOG.info("Verifying learning claim against knowledge bases: '{}'", 
            claimText.substring(0, Math.min(50, claimText.length())));
        
        // Check cache first
        String cacheKey = buildCacheKey(claimText, domain);
        FactVerificationResult cached = factCache.get(cacheKey);
        if (cached != null) {
            LOG.debug("Cache hit for claim verification");
            return Promise.of(cached);
        }

        String wikiTopic = (domain == null || domain.isBlank()) ? claimText : domain;
        
        // Verify against all sources in parallel
        Promise<WikipediaApiClient.FactVerificationResult> wikiVerification =
            wikipediaClient.verifyFact(claimText, wikiTopic);
        
        Promise<OpenStaxApiClient.CurriculumAlignmentResult> curriculumAlignment = 
            openStaxClient.findAlignedContent(claimText, domain, gradeLevel);
        
        // Combine results
        return wikiVerification.combine(curriculumAlignment, (wiki, curriculum) -> {
            List<VerificationSource> sources = new ArrayList<>();
            
            // Add Wikipedia verification
            if (wiki.verified()) {
                sources.add(new VerificationSource(
                    "Wikipedia",
                    wiki.sourceUrl(),
                    wiki.confidence(),
                    wiki.explanation()
                ));
            }
            
            // Add OpenStax verification
            if (curriculum.aligned()) {
                sources.add(new VerificationSource(
                    "OpenStax",
                    curriculum.primarySourceUrl(),
                    curriculum.confidenceScore(),
                    curriculum.explanation()
                ));
            }
            
            // Calculate overall confidence
            double overallConfidence = calculateOverallConfidence(wiki, curriculum);
            
            VerificationStatus status;
            if (overallConfidence >= 0.7) {
                status = VerificationStatus.VERIFIED;
            } else if (overallConfidence >= 0.4) {
                status = VerificationStatus.PARTIALLY_VERIFIED;
            } else if (sources.isEmpty()) {
                status = VerificationStatus.UNVERIFIED;
            } else {
                status = VerificationStatus.DISPUTED;
            }
            
            FactVerificationResult result = new FactVerificationResult(
                status,
                overallConfidence,
                sources,
                buildVerificationSummary(status, sources),
                curriculum.alignedTopics()
            );
            
            // Cache the result
            if (factCache.size() < MAX_CACHE_SIZE) {
                factCache.put(cacheKey, result);
            }
            
            return result;
        });
    }

    /**
     * Searches all knowledge sources for content related to a topic.
     *
     * @param topic the topic to search
     * @param domain the subject domain
     * @param limit maximum results per source
     * @return a promise containing unified search results
     */
    public Promise<UnifiedSearchResult> search(String topic, String domain, int limit) {
        LOG.debug("Unified search for topic: {}", topic);
        
        Promise<List<WikipediaApiClient.WikipediaArticleSummary>> wikiSearch =
            wikipediaClient.search(topic, limit);
        
        Promise<List<OpenStaxApiClient.OpenStaxSearchResult>> openstaxSearch = 
            openStaxClient.search(topic, domain, limit);
        
        Promise<List<KhanAcademyApiClient.KhanSearchResult>> khanSearch = 
            khanClient.search(topic, null, limit);
        
        return wikiSearch.combine(openstaxSearch, (wiki, openstax) -> 
            new PartialResults(wiki, openstax))
            .combine(khanSearch, (partial, khan) -> {
                List<SearchResultItem> allResults = new ArrayList<>();
                
                // Add Wikipedia results
                for (var result : partial.wiki) {
                    allResults.add(new SearchResultItem(
                        result.title(),
                        result.snippet(),
                        "https://en.wikipedia.org/?curid=" + result.pageId(),
                        "Wikipedia",
                        SourceType.ENCYCLOPEDIA,
                        0.8 // Wikipedia is highly authoritative
                    ));
                }
                
                // Add OpenStax results
                for (var result : partial.openstax) {
                    allResults.add(new SearchResultItem(
                        result.title(),
                        result.snippet(),
                        result.url(),
                        "OpenStax: " + result.bookTitle(),
                        SourceType.TEXTBOOK,
                        0.9 // Peer-reviewed textbooks
                    ));
                }
                
                // Add Khan Academy results
                for (var result : khan) {
                    allResults.add(new SearchResultItem(
                        result.title(),
                        result.description(),
                        result.url(),
                        "Khan Academy",
                        mapContentType(result.contentType()),
                        0.7
                    ));
                }
                
                return new UnifiedSearchResult(topic, allResults, allResults.size());
            });
    }

    /**
     * Gets supplementary learning resources for a topic.
     *
     * @param topic the topic
     * @param domain the subject domain
     * @param gradeLevel the grade level
     * @return a promise containing supplementary resources
     */
    public Promise<SupplementaryResources> getSupplementaryResources(
            String topic, String domain, String gradeLevel) {
        
        LOG.debug("Fetching supplementary resources for topic: {}", topic);
        
        Promise<List<OpenStaxApiClient.PrerequisiteRecommendation>> prerequisites = 
            openStaxClient.getPrerequisites(topic, domain);
        
        Promise<KhanAcademyApiClient.SupplementaryContentResult> khanContent = 
            khanClient.findSupplementaryContent(topic, List.of(
                KhanAcademyApiClient.ContentType.VIDEO,
                KhanAcademyApiClient.ContentType.EXERCISE
            ));
        
        Promise<KhanAcademyApiClient.LearningPath> learningPath = 
            khanClient.getLearningPath(topic, gradeLevel);
        
        return prerequisites.combine(khanContent, (prereqs, khan) -> 
            new PartialSupplementary(prereqs, khan))
            .combine(learningPath, (partial, path) -> {
                List<ResourceLink> videos = partial.khan.videoLinks().stream()
                    .map(url -> new ResourceLink("Video", url, "Khan Academy"))
                    .toList();
                
                List<ResourceLink> exercises = partial.khan.exerciseLinks().stream()
                    .map(url -> new ResourceLink("Exercise", url, "Khan Academy"))
                    .toList();
                
                List<ResourceLink> prereqLinks = partial.prereqs.stream()
                    .map(p -> new ResourceLink(p.title(), p.url(), "OpenStax"))
                    .toList();
                
                List<LearningPathStep> pathSteps = path.steps().stream()
                    .map(s -> new LearningPathStep(s.order(), s.title(), s.url(), s.estimatedMinutes()))
                    .toList();
                
                return new SupplementaryResources(
                    videos,
                    exercises,
                    prereqLinks,
                    pathSteps,
                    path.totalEstimatedMinutes()
                );
            });
    }

    /**
     * Checks if content aligns with curriculum standards.
     *
     * @param contentText the content to check
     * @param domain the subject domain
     * @param gradeLevel the grade level
     * @return a promise containing alignment result
     */
    public Promise<CurriculumAlignmentResult> checkCurriculumAlignment(
            String contentText, String domain, String gradeLevel) {
        
        return openStaxClient.findAlignedContent(contentText, domain, gradeLevel)
            .map(result -> new CurriculumAlignmentResult(
                result.aligned(),
                result.confidenceScore(),
                result.explanation(),
                result.alignedTopics(),
                result.primarySourceUrl()
            ));
    }

    private double calculateOverallConfidence(
            WikipediaApiClient.FactVerificationResult wiki,
            OpenStaxApiClient.CurriculumAlignmentResult curriculum) {
        
        double wikiWeight = 0.4;
        double curriculumWeight = 0.6;
        
        double wikiScore = wiki.verified() ? wiki.confidence() : 0.0;
        double curriculumScore = curriculum.aligned() ? curriculum.confidenceScore() : 0.0;
        
        return (wikiScore * wikiWeight) + (curriculumScore * curriculumWeight);
    }

    private String buildVerificationSummary(VerificationStatus status, List<VerificationSource> sources) {
        if (sources.isEmpty()) {
            return "Unable to verify claim against available knowledge sources.";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append(switch (status) {
            case VERIFIED -> "Claim verified by multiple authoritative sources. ";
            case PARTIALLY_VERIFIED -> "Claim partially verified. ";
            case UNVERIFIED -> "Claim could not be verified. ";
            case DISPUTED -> "Claim verification inconclusive. ";
        });
        
        summary.append("Sources consulted: ");
        summary.append(sources.stream()
            .map(VerificationSource::sourceName)
            .distinct()
            .reduce((a, b) -> a + ", " + b)
            .orElse("none"));
        
        return summary.toString();
    }

    private String buildCacheKey(String claim, String domain) {
        return domain + ":" + claim.toLowerCase().trim().hashCode();
    }

    private SourceType mapContentType(KhanAcademyApiClient.ContentType type) {
        return switch (type) {
            case VIDEO -> SourceType.VIDEO;
            case EXERCISE -> SourceType.EXERCISE;
            case ARTICLE -> SourceType.ARTICLE;
        };
    }

    // Helper records for combining promises
    private record PartialResults(
        List<WikipediaApiClient.WikipediaArticleSummary> wiki,
        List<OpenStaxApiClient.OpenStaxSearchResult> openstax
    ) {}
    
    private record PartialSupplementary(
        List<OpenStaxApiClient.PrerequisiteRecommendation> prereqs,
        KhanAcademyApiClient.SupplementaryContentResult khan
    ) {}

    // =========================================================================
    // Public Record Classes and Enums
    // =========================================================================

    /**
     * Status of fact verification.
     */
    public enum VerificationStatus {
        VERIFIED,
        PARTIALLY_VERIFIED,
        UNVERIFIED,
        DISPUTED
    }

    /**
     * Type of knowledge source.
     */
    public enum SourceType {
        ENCYCLOPEDIA,
        TEXTBOOK,
        VIDEO,
        EXERCISE,
        ARTICLE
    }

    /**
     * A verification source with its confidence.
     */
    public record VerificationSource(
        String sourceName,
        String sourceUrl,
        double confidenceScore,
        String details
    ) {}

    /**
     * Complete fact verification result.
     */
    public record FactVerificationResult(
        VerificationStatus status,
        double overallConfidence,
        List<VerificationSource> sources,
        String summary,
        List<String> relatedTopics
    ) {}

    /**
     * A single search result item.
     */
    public record SearchResultItem(
        String title,
        String snippet,
        String url,
        String source,
        SourceType sourceType,
        double authorityScore
    ) {}

    /**
     * Unified search results from all sources.
     */
    public record UnifiedSearchResult(
        String query,
        List<SearchResultItem> results,
        int totalResults
    ) {}

    /**
     * A resource link.
     */
    public record ResourceLink(
        String title,
        String url,
        String source
    ) {}

    /**
     * A step in a learning path.
     */
    public record LearningPathStep(
        int order,
        String title,
        String url,
        int estimatedMinutes
    ) {}

    /**
     * Supplementary learning resources.
     */
    public record SupplementaryResources(
        List<ResourceLink> videos,
        List<ResourceLink> exercises,
        List<ResourceLink> prerequisites,
        List<LearningPathStep> learningPath,
        int totalEstimatedMinutes
    ) {}

    /**
     * Curriculum alignment result.
     */
    public record CurriculumAlignmentResult(
        boolean aligned,
        double confidenceScore,
        String explanation,
        List<String> alignedTopics,
        String primarySourceUrl
    ) {}
}
