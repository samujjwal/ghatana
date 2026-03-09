package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Sentiment Agent - analyzes sentiment in feedback, comments, and communications.
 * <p>
 * Uses NLP models to detect sentiment, emotion, and team mood indicators.
 *
 * @doc.type class
 * @doc.purpose AI-powered sentiment analysis
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class SentimentAgent extends AbstractAIAgent<SentimentAgent.SentimentInput, SentimentAgent.SentimentOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(SentimentAgent.class);

        private static final String VERSION = "1.0.0";
        private static final String DESCRIPTION = "Sentiment and emotion analysis for feedback, comments, and team communications";
        private static final List<String> CAPABILITIES = List.of(
            "sentiment-analysis",
            "emotion-detection",
            "mood-tracking"
        );
        private static final List<String> SUPPORTED_MODELS = List.of(
            "local-nlp",
            "distilbert-sentiment"
        );

    private final NLPService nlpService;

    public SentimentAgent(
            @NotNull MetricsCollector metricsCollector,
            @NotNull NLPService nlpService
    ) {
        super(
                AgentName.SENTIMENT_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.nlpService = nlpService;
    }

    @Override
    public void validateInput(@NotNull SentimentInput input) {
        if ((input.text() == null || input.text().isBlank()) && 
            (input.texts() == null || input.texts().isEmpty())) {
            throw new IllegalArgumentException("text or texts is required");
        }
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return Promise.of(Map.of(
                "nlpService", AgentHealth.DependencyStatus.HEALTHY
        ));
    }

    @Override
    protected @NotNull Promise<ProcessResult<SentimentOutput>> processRequest(
            @NotNull SentimentInput input,
            @NotNull AIAgentContext context
    ) {
        long startTime = System.currentTimeMillis();

        // Single text or batch analysis
        List<String> textsToAnalyze = input.texts() != null && !input.texts().isEmpty()
                ? input.texts()
                : List.of(input.text());

        return nlpService.analyzeSentimentBatch(textsToAnalyze)
                .map(analyses -> {
                    List<SentimentAnalysis> results = new ArrayList<>();
                    double totalPositive = 0, totalNegative = 0, totalNeutral = 0;

                    for (int i = 0; i < analyses.size(); i++) {
                        NLPAnalysis analysis = analyses.get(i);
                        
                        results.add(new SentimentAnalysis(
                                textsToAnalyze.get(i),
                                analysis.sentiment(),
                                analysis.confidence(),
                                analysis.emotions(),
                                analysis.keywords(),
                                analysis.urgency(),
                                analysis.actionRequired()
                        ));

                        // Aggregate scores
                        switch (analysis.sentiment()) {
                            case POSITIVE -> totalPositive += analysis.confidence();
                            case NEGATIVE -> totalNegative += analysis.confidence();
                            case NEUTRAL -> totalNeutral += analysis.confidence();
                        }
                    }

                    int count = analyses.size();
                    AggregateSentiment aggregate = new AggregateSentiment(
                            totalPositive / count,
                            totalNegative / count,
                            totalNeutral / count,
                            determineDominantSentiment(totalPositive, totalNegative, totalNeutral),
                            calculateMoodTrend(results)
                    );

                    SentimentOutput output = new SentimentOutput(
                            results,
                            aggregate,
                            new SentimentMetadata(
                                    System.currentTimeMillis() - startTime,
                                    "distilbert-sentiment",
                                    count
                            )
                    );

                        return ProcessResult.of(output, null, output.metadata().modelUsed(), null);
                });
    }

    private Sentiment determineDominantSentiment(double positive, double negative, double neutral) {
        if (positive >= negative && positive >= neutral) {
            return Sentiment.POSITIVE;
        } else if (negative >= positive && negative >= neutral) {
            return Sentiment.NEGATIVE;
        } else {
            return Sentiment.NEUTRAL;
        }
    }

    private String calculateMoodTrend(List<SentimentAnalysis> results) {
        if (results.size() < 2) {
            return "stable";
        }

        // Compare first half to second half
        int mid = results.size() / 2;
        double firstHalfScore = 0, secondHalfScore = 0;

        for (int i = 0; i < mid; i++) {
            firstHalfScore += sentimentToScore(results.get(i).sentiment());
        }
        for (int i = mid; i < results.size(); i++) {
            secondHalfScore += sentimentToScore(results.get(i).sentiment());
        }

        firstHalfScore /= mid;
        secondHalfScore /= (results.size() - mid);

        double diff = secondHalfScore - firstHalfScore;
        if (diff > 0.2) return "improving";
        if (diff < -0.2) return "declining";
        return "stable";
    }

    private double sentimentToScore(Sentiment sentiment) {
        return switch (sentiment) {
            case POSITIVE -> 1.0;
            case NEUTRAL -> 0.5;
            case NEGATIVE -> 0.0;
            case MIXED -> 0.5;
        };
    }

    // Input/Output types

    public record SentimentInput(
            @Nullable String text,
            @Nullable List<String> texts,
            @Nullable String context,
            boolean includeEmotions,
            boolean includeKeywords
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String text;
            private List<String> texts;
            private String context;
            private boolean includeEmotions = true;
            private boolean includeKeywords = true;

            public Builder text(String text) {
                this.text = text;
                return this;
            }

            public Builder texts(List<String> texts) {
                this.texts = texts;
                return this;
            }

            public Builder context(String context) {
                this.context = context;
                return this;
            }

            public Builder includeEmotions(boolean includeEmotions) {
                this.includeEmotions = includeEmotions;
                return this;
            }

            public Builder includeKeywords(boolean includeKeywords) {
                this.includeKeywords = includeKeywords;
                return this;
            }

            public SentimentInput build() {
                return new SentimentInput(text, texts, context, includeEmotions, includeKeywords);
            }
        }
    }

    public record SentimentOutput(
            @NotNull List<SentimentAnalysis> analyses,
            @NotNull AggregateSentiment aggregate,
            @NotNull SentimentMetadata metadata
    ) {}

    public record SentimentAnalysis(
            @NotNull String text,
            @NotNull Sentiment sentiment,
            double confidence,
            @Nullable List<Emotion> emotions,
            @Nullable List<String> keywords,
            @NotNull UrgencyLevel urgency,
            boolean actionRequired
    ) {}

    public record AggregateSentiment(
            double positiveScore,
            double negativeScore,
            double neutralScore,
            @NotNull Sentiment dominant,
            @NotNull String trend
    ) {}

    public record SentimentMetadata(
            long processingTimeMs,
            @NotNull String modelUsed,
            int textsAnalyzed
    ) {}

    public enum Sentiment {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
        MIXED
    }

    public record Emotion(
            @NotNull String emotion,
            double score
    ) {}

    public enum UrgencyLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    // Service interface

    public interface NLPService {
        Promise<List<NLPAnalysis>> analyzeSentimentBatch(List<String> texts);
    }

    public record NLPAnalysis(
            Sentiment sentiment,
            double confidence,
            List<Emotion> emotions,
            List<String> keywords,
            UrgencyLevel urgency,
            boolean actionRequired
    ) {}
}
