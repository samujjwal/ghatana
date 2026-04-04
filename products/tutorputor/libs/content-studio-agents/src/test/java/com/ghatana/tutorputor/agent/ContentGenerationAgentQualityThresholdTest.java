package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContentGenerationAgent – Quality-Threshold Boundary Tests
 *
 * <p>The existing {@code ContentGenerationAgentTest} covers the "happy path" where
 * quality is well above all thresholds. This class specifically targets the
 * <em>boundary</em> conditions that gate storeFact (CAPTURE) and storePolicy
 * (REFLECT), which the audit report identified as missing coverage.
 *
 * <h2>Gaps addressed</h2>
 * <ul>
 *   <li>CAPTURE: {@code qualityScore < 0.8} → only {@code storeEpisode} called,
 *       {@code storeFact} NOT called.</li>
 *   <li>CAPTURE: {@code qualityScore >= 0.8} → both {@code storeEpisode} and
 *       {@code storeFact} called.</li>
 *   <li>REFLECT: {@code analyzedQuality < HIGH_CONFIDENCE_THRESHOLD (0.85)} →
 *       {@code storePolicy} (extractAndStoreStrategy) NOT called.</li>
 *   <li>REFLECT: {@code analyzedQuality >= 0.85} → {@code storePolicy}
 *       IS called.</li>
 *   <li>REFLECT: anonymous request ({@code learnerId == null}) → neither
 *       {@code storePolicy} nor {@code updateLearnerModel} (storeFact) is called
 *       in the immediate reflection path.</li>
 *   <li>REFLECT: {@code domainEpisodes.size() < MIN_EPISODES_FOR_POLICY_EXTRACTION (3)}
 *       → aggregate {@code storePolicy} NOT called from
 *       {@code reflectOnRecentEpisodes}.</li>
 *   <li>REFLECT: exactly 3 domain episodes with avgQuality >= 0.85 →
 *       aggregate {@code storePolicy} IS called.</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Quality-threshold boundary coverage for ContentGenerationAgent
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentGenerationAgent – Quality Threshold Tests")
class ContentGenerationAgentQualityThresholdTest extends EventloopTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationAgentQualityThresholdTest.class);

    @Mock
    private OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> mockGenerator;

    @Mock
    private KnowledgeBaseService mockKnowledgeBaseService;

    @Mock
    private AgentContext mockContext;

    @Mock
    private MemoryStore mockMemoryStore;

    @Mock
    private LearnerProfileHttpClient mockLearnerProfileClient;

    private TestAgent agent;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ContentQualityValidator qualityValidator = new ContentQualityValidator(registry);

        agent = new TestAgent(
            mockGenerator,
            mockKnowledgeBaseService,
            qualityValidator,
            mockLearnerProfileClient
        );

        lenient().when(mockContext.getLogger()).thenReturn(LOG);
        lenient().when(mockContext.getTurnId()).thenReturn("test-turn-1");
        lenient().when(mockContext.getMemoryStore()).thenReturn(mockMemoryStore);
        lenient().when(mockLearnerProfileClient.getPersonalization(anyString(), anyString()))
            .thenReturn(Optional.empty());
    }

    // -----------------------------------------------------------------------
    // CAPTURE: storeFact gated at qualityScore >= 0.8
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CAPTURE phase – storeFact threshold (qualityScore >= 0.8)")
    class CaptureQualityThresholdTests {

        @Test
        @DisplayName("Does NOT call storeFact when qualityScore is below 0.8")
        void captureDoesNotStoreFactWhenQualityBelowThreshold() {
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Photosynthesis", "SCIENCE", "6", ContentGenerationRequest.ContentType.CLAIM
            );

            // qualityScore = 0.79 – just below the 0.8 threshold
            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Short content.")
                .domain("SCIENCE")
                .gradeLevel("6")
                .contentType(ContentGenerationRequest.ContentType.CLAIM)
                .qualityScore(0.79)
                .generationTimeMs(300)
                .tokenCount(30)
                .build();

            when(mockMemoryStore.storeEpisode(any()))
                .thenReturn(Promise.of(Episode.builder()
                    .agentId("test")
                    .turnId("t1")
                    .timestamp(Instant.now())
                    .input("i")
                    .output("o")
                    .build()));

            runPromise(() -> agent.runCapture(request, response, mockContext));

            // Episode is always stored
            verify(mockMemoryStore).storeEpisode(any());
            // Fact MUST NOT be stored below the 0.8 threshold
            verify(mockMemoryStore, never()).storeFact(any());
        }

        @Test
        @DisplayName("Calls storeFact when qualityScore is exactly 0.8 (boundary inclusive)")
        void captureStoresFactWhenQualityIsExactlyThreshold() {
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Cell Division", "BIOLOGY", "8", ContentGenerationRequest.ContentType.CLAIM
            );

            // qualityScore = 0.80 – exactly at the threshold
            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Cell division is the process of splitting a cell into two.")
                .domain("BIOLOGY")
                .gradeLevel("8")
                .contentType(ContentGenerationRequest.ContentType.CLAIM)
                .qualityScore(0.80)
                .generationTimeMs(350)
                .tokenCount(55)
                .build();

            when(mockMemoryStore.storeEpisode(any()))
                .thenReturn(Promise.of(Episode.builder()
                    .agentId("test")
                    .turnId("t1")
                    .timestamp(Instant.now())
                    .input("i")
                    .output("o")
                    .build()));

            when(mockMemoryStore.storeFact(any()))
                .thenReturn(Promise.of(Fact.builder()
                    .agentId("test")
                    .subject("Cell Division")
                    .predicate("has_quality_content")
                    .object("CLAIM")
                    .build()));

            runPromise(() -> agent.runCapture(request, response, mockContext));

            verify(mockMemoryStore).storeEpisode(any());
            verify(mockMemoryStore).storeFact(any());
        }

        @Test
        @DisplayName("Calls storeFact when qualityScore is well above 0.8")
        void captureStoresFactWhenQualityWellAboveThreshold() {
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Newton's Laws", "PHYSICS", "10", ContentGenerationRequest.ContentType.LESSON
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Newton's three laws of motion govern classical mechanics.")
                .domain("PHYSICS")
                .gradeLevel("10")
                .contentType(ContentGenerationRequest.ContentType.LESSON)
                .qualityScore(0.95)
                .generationTimeMs(420)
                .tokenCount(80)
                .build();

            when(mockMemoryStore.storeEpisode(any()))
                .thenReturn(Promise.of(Episode.builder()
                    .agentId("test")
                    .turnId("t1")
                    .timestamp(Instant.now())
                    .input("i")
                    .output("o")
                    .build()));

            when(mockMemoryStore.storeFact(any()))
                .thenReturn(Promise.of(Fact.builder()
                    .agentId("test")
                    .subject("Newton's Laws")
                    .predicate("has_quality_content")
                    .object("LESSON")
                    .build()));

            runPromise(() -> agent.runCapture(request, response, mockContext));

            verify(mockMemoryStore).storeEpisode(any());
            verify(mockMemoryStore).storeFact(any());
        }
    }

    // -----------------------------------------------------------------------
    // REFLECT: extractAndStoreStrategy gated at analyzedQuality >= 0.85
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("REFLECT phase – extractAndStoreStrategy threshold (analyzedQuality >= 0.85)")
    class ReflectQualityThresholdTests {

        /**
         * analyzeGenerationQuality formula:
         *   analyzedQuality = min(1.0, rawQuality * 0.8 + alignBoost + validBoost + tokenBoost)
         *   where alignBoost = curriculumAligned ? 0.1 : 0.0
         *         validBoost = (validationIssues == null || empty) ? 0.05 : 0.0
         *         tokenBoost = tokenCount >= 150 ? 0.05 : 0.0
         *
         * For no boosts: analyzedQuality = rawQuality * 0.8
         * To stay below 0.85 with no boosts: rawQuality < 1.0625 (always true for any valid score)
         * For this test: rawQuality = 0.90, no boosts → 0.90 * 0.8 = 0.72 < 0.85 ✓
         */
        @Test
        @DisplayName("Does NOT call storePolicy (extractAndStoreStrategy) when analyzedQuality < 0.85")
        void reflectDoesNotStorePolicyWhenAnalyzedQualityBelowThreshold() {
            // learnerId is present, so immediate reflection path runs
            ContentGenerationRequest request = ContentGenerationRequest.forLearner(
                "Algebra", "MATH", "8", ContentGenerationRequest.ContentType.EXAMPLE, "learner-1"
            );

            // rawQuality=0.90, NO boosts (not aligned, has validation issues, low token count)
            // analyzedQuality = 0.90 * 0.8 = 0.72 < 0.85 → no extractAndStoreStrategy
            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("An example of algebra.")
                .domain("MATH")
                .gradeLevel("8")
                .contentType(ContentGenerationRequest.ContentType.EXAMPLE)
                .qualityScore(0.90)
                .curriculumAligned(false)
                .validationIssues(List.of("minor formatting issue"))
                .generationTimeMs(300)
                .tokenCount(40) // below 150, no tokenBoost
                .build();

            // updateLearnerModel IS called (learnerId != null), so storeFact needs a stub.
            when(mockMemoryStore.storeFact(any()))
                .thenReturn(Promise.of(Fact.builder()
                    .agentId("test")
                    .subject("learner-1")
                    .predicate("responds_well_to")
                    .object("example")
                    .build()));

            // reflectOnRecentEpisodes is always called at end of reflect
            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of()));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // storePolicy MUST NOT be called (analyzedQuality is below 0.85)
            verify(mockMemoryStore, never()).storePolicy(any());
            // storeFact IS called via updateLearnerModel (learnerId != null)
            verify(mockMemoryStore).storeFact(any());
        }

        /**
         * With all boosts: analyzedQuality = rawQuality * 0.8 + 0.2
         * rawQuality = 0.83 → 0.83 * 0.8 + 0.2 = 0.664 + 0.2 = 0.864 >= 0.85 → storePolicy ✓
         */
        @Test
        @DisplayName("Calls storePolicy (extractAndStoreStrategy) when analyzedQuality >= 0.85")
        void reflectStoresPolicyWhenAnalyzedQualityMeetsThreshold() {
            ContentGenerationRequest request = ContentGenerationRequest.forLearner(
                "Quadratic Equations", "MATH", "9",
                ContentGenerationRequest.ContentType.EXAMPLE, "learner-2"
            );

            // rawQuality=0.83, WITH all boosts (aligned=true, no issues, tokenCount=200)
            // analyzedQuality = 0.83 * 0.8 + 0.1 + 0.05 + 0.05 = 0.864 ≥ 0.85
            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Step-by-step worked example of quadratic equation solution.")
                .domain("MATH")
                .gradeLevel("9")
                .contentType(ContentGenerationRequest.ContentType.EXAMPLE)
                .qualityScore(0.83)
                .curriculumAligned(true)
                .generationTimeMs(380)
                .tokenCount(200)
                .build();

            when(mockMemoryStore.storePolicy(any()))
                .thenReturn(Promise.of(Policy.builder()
                    .agentId("test")
                    .situation("s")
                    .action("a")
                    .confidence(0.864)
                    .build()));

            when(mockMemoryStore.storeFact(any()))
                .thenReturn(Promise.of(Fact.builder()
                    .agentId("test")
                    .subject("learner-2")
                    .predicate("responds_well_to")
                    .object("example")
                    .build()));

            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of()));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            verify(mockMemoryStore).storePolicy(any());
        }

        @Test
        @DisplayName("Does NOT call storePolicy or updateLearnerModel (storeFact) for anonymous request (learnerId = null)")
        void reflectSkipsImmediateReflectionForAnonymousRequests() {
            // Anonymous request: learnerId = null → immediate reflection path is skipped entirely
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Fractions", "MATH", "5", ContentGenerationRequest.ContentType.CLAIM
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Fractions represent parts of a whole.")
                .domain("MATH")
                .gradeLevel("5")
                .contentType(ContentGenerationRequest.ContentType.CLAIM)
                .qualityScore(0.99) // quality is irrelevant when learnerId == null
                .curriculumAligned(true)
                .generationTimeMs(250)
                .tokenCount(300)
                .build();

            // reflectOnRecentEpisodes is still called even for anonymous requests
            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of()));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // No storePolicy (no extractAndStoreStrategy)
            verify(mockMemoryStore, never()).storePolicy(any());
            // No storeFact (updateLearnerModel skipped because learnerId == null)
            verify(mockMemoryStore, never()).storeFact(any());
        }
    }

    // -----------------------------------------------------------------------
    // REFLECT: reflectOnRecentEpisodes – aggregate pattern extraction threshold
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("REFLECT phase – aggregate pattern extraction (MIN_EPISODES_FOR_POLICY_EXTRACTION = 3)")
    class ReflectEpisodeThresholdTests {

        @Test
        @DisplayName("Does NOT extract aggregate policy when fewer than 3 domain episodes exist")
        void doesNotExtractAggregatePatternWithFewerThanThreeEpisodes() {
            // Anonymous request so that the immediate reflection path is skipped.
            // This isolates the reflectOnRecentEpisodes behaviour.
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Osmosis", "BIOLOGY", "7", ContentGenerationRequest.ContentType.LESSON
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Osmosis is the movement of water across a membrane.")
                .domain("BIOLOGY")
                .gradeLevel("7")
                .contentType(ContentGenerationRequest.ContentType.LESSON)
                .qualityScore(0.85)
                .generationTimeMs(400)
                .tokenCount(100)
                .build();

            // Only 2 matching domain episodes (< MIN_EPISODES_FOR_POLICY_EXTRACTION = 3)
            // avgReward = 0.90 which is >= 0.85, but episode count gate blocks extraction
            Episode episode1 = buildEpisode("BIOLOGY", 0.92);
            Episode episode2 = buildEpisode("BIOLOGY", 0.88);

            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of(episode1, episode2)));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // storePolicy must NOT be called (< 3 episodes)
            verify(mockMemoryStore, never()).storePolicy(any());
        }

        @Test
        @DisplayName("Extracts aggregate policy when exactly 3 domain episodes exist with high avgQuality")
        void extractsAggregatePolicyWithExactlyThreeEpisodes() {
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Evolution", "BIOLOGY", "9", ContentGenerationRequest.ContentType.LESSON
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Evolution explains species diversity over time.")
                .domain("BIOLOGY")
                .gradeLevel("9")
                .contentType(ContentGenerationRequest.ContentType.LESSON)
                .qualityScore(0.80)
                .generationTimeMs(500)
                .tokenCount(160)
                .build();

            // Exactly 3 domain episodes with avgQuality = 0.90 >= 0.85
            Episode ep1 = buildEpisode("BIOLOGY", 0.93);
            Episode ep2 = buildEpisode("BIOLOGY", 0.88);
            Episode ep3 = buildEpisode("BIOLOGY", 0.89);

            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of(ep1, ep2, ep3)));

            when(mockMemoryStore.storePolicy(any()))
                .thenReturn(Promise.of(Policy.builder()
                    .agentId("test")
                    .situation("s")
                    .action("a")
                    .confidence(0.90)
                    .build()));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // Aggregate storePolicy MUST be called (extractSuccessfulPatterns)
            verify(mockMemoryStore).storePolicy(any());
        }

        @Test
        @DisplayName("Does NOT extract aggregate policy when episodes exist but avgQuality is below threshold")
        void doesNotExtractAggregatePatternWhenAvgQualityIsBelowThreshold() {
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Geometry", "MATH", "6", ContentGenerationRequest.ContentType.EXAMPLE
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Geometry example.")
                .domain("MATH")
                .gradeLevel("6")
                .contentType(ContentGenerationRequest.ContentType.EXAMPLE)
                .qualityScore(0.80)
                .generationTimeMs(300)
                .tokenCount(50)
                .build();

            // 3 episodes but avgReward = 0.55 < 0.85 → extractSuccessfulPatterns NOT called
            // avgReward = 0.55 > 0.60? No: 0.55 < 0.60 (LOW_QUALITY_THRESHOLD) → identifyFailurePatterns
            // identifyFailurePatterns only logs (no storePolicy), so storePolicy is still never called
            Episode ep1 = buildEpisode("MATH", 0.55);
            Episode ep2 = buildEpisode("MATH", 0.52);
            Episode ep3 = buildEpisode("MATH", 0.58);

            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of(ep1, ep2, ep3)));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // storePolicy must NOT be called (avgQuality < 0.85)
            verify(mockMemoryStore, never()).storePolicy(any());
        }

        @Test
        @DisplayName("Ignores episodes from a different domain when computing pattern eligibility")
        void ignoresCrossDomainEpisodesForPatternExtraction() {
            // Request is for PHYSICS; stored episodes are for CHEMISTRY.
            // After filtering, domainEpisodes will be empty → size < MIN_EPISODES_FOR_PATTERN (1).
            // The code returns Promise.complete() without calling storePolicy.
            ContentGenerationRequest request = ContentGenerationRequest.basic(
                "Waves", "PHYSICS", "11", ContentGenerationRequest.ContentType.LESSON
            );

            ContentGenerationResponse response = ContentGenerationResponse.builder()
                .content("Waves are oscillations that transfer energy.")
                .domain("PHYSICS")
                .gradeLevel("11")
                .contentType(ContentGenerationRequest.ContentType.LESSON)
                .qualityScore(0.85)
                .generationTimeMs(450)
                .tokenCount(200)
                .build();

            // 3 episodes but all from a different domain
            Episode ep1 = buildEpisode("CHEMISTRY", 0.90);
            Episode ep2 = buildEpisode("CHEMISTRY", 0.91);
            Episode ep3 = buildEpisode("CHEMISTRY", 0.92);

            when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
                .thenReturn(Promise.of(List.of(ep1, ep2, ep3)));

            runPromise(() -> agent.runReflect(request, response, mockContext));

            // After domain filtering, zero matching episodes → no storePolicy
            verify(mockMemoryStore, never()).storePolicy(any());
        }
    }

    // -----------------------------------------------------------------------
    // Inner helper class: exposes protected lifecycle methods for direct testing
    // -----------------------------------------------------------------------

    private static final class TestAgent extends ContentGenerationAgent {

        private TestAgent(
            OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> generator,
            KnowledgeBaseService knowledgeBaseService,
            ContentQualityValidator qualityValidator,
            LearnerProfileHttpClient learnerProfileClient
        ) {
            super(generator, knowledgeBaseService, qualityValidator, learnerProfileClient);
        }

        Promise<Void> runCapture(
            ContentGenerationRequest request,
            ContentGenerationResponse response,
            AgentContext context
        ) {
            return super.capture(request, response, context);
        }

        Promise<Void> runReflect(
            ContentGenerationRequest request,
            ContentGenerationResponse response,
            AgentContext context
        ) {
            return super.reflect(request, response, context);
        }
    }

    // -----------------------------------------------------------------------
    // Utility builders
    // -----------------------------------------------------------------------

    private static Episode buildEpisode(String domain, double reward) {
        return Episode.builder()
            .agentId("tutorputor-content-agent")
            .turnId("turn-" + System.nanoTime())
            .timestamp(Instant.now())
            .input("topic: test domain: " + domain)
            .output("Quality: 0.90, Tokens: 200, Aligned: true")
            .context(java.util.Map.of("domain", domain))
            .reward(reward)
            .build();
    }
}
