package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   NLP adverse media screening using a BERT-class classifier. Analyses news
 *                articles from external feeds and classifies each article into one of five
 *                categories. HIGH signals auto-trigger EDD escalation. K-09 advisory tier —
 *                human compliance officer must review before any action.
 * @doc.layer     Application
 * @doc.pattern   K-09 advisory; pluggable ML model via inner port; SHAP token contributions
 *
 * Story: D07-015
 */
public class NlpAdverseMediaService {

    private static final Logger log = LoggerFactory.getLogger(NlpAdverseMediaService.class);

    /** Minimum article character length to run inference (avoids noise from stubs). */
    private static final int MIN_ARTICLE_LENGTH = 100;

    private final NewsSourcePort          newsSourcePort;
    private final ClassificationModelPort modelPort;
    private final Consumer<Object>        eventPublisher;
    private final Counter articlesAnalysed;
    private final Counter highSignalsDetected;

    public NlpAdverseMediaService(NewsSourcePort newsSourcePort,
                                   ClassificationModelPort modelPort,
                                   Consumer<Object> eventPublisher,
                                   MeterRegistry meterRegistry) {
        this.newsSourcePort     = newsSourcePort;
        this.modelPort          = modelPort;
        this.eventPublisher     = eventPublisher;
        this.articlesAnalysed   = meterRegistry.counter("compliance.adverse_media.articles_analysed");
        this.highSignalsDetected = meterRegistry.counter("compliance.adverse_media.high_signals");
    }

    /**
     * Screens an entity (client or company) against recent news articles.
     *
     * @param entityId    client or company identifier
     * @param entityName  name for news search query
     * @return screening result with per-article classifications
     */
    public ScreeningResult screenEntity(String entityId, String entityName) {
        List<NewsArticle> articles = newsSourcePort.fetchRecent(entityName, 30);
        log.debug("AdverseMedia: screening entityId={} articles={}", entityId, articles.size());

        AdverseCategory worstCategory = AdverseCategory.NONE;
        double maxScore = 0.0;
        java.util.List<ArticleClassification> classifications = new java.util.ArrayList<>();

        for (NewsArticle article : articles) {
            if (article.body().length() < MIN_ARTICLE_LENGTH) continue;

            ClassificationResult result = modelPort.classify(article.body());
            articlesAnalysed.increment();

            if (result.score() > maxScore) {
                maxScore      = result.score();
                worstCategory = result.category();
            }

            classifications.add(new ArticleClassification(article.articleId(), article.title(),
                    result.category(), result.score(), result.shapContributions()));

            if (result.category() != AdverseCategory.NONE && result.score() >= 0.7) {
                highSignalsDetected.increment();
                log.warn("AdverseMedia HIGH signal: entityId={} article={} category={} score={}",
                        entityId, article.articleId(), result.category(), result.score());
                eventPublisher.accept(new AdverseMediaHighSignalEvent(
                        entityId, article.articleId(), result.category(), result.score(),
                        result.shapContributions()));
            }
        }

        return new ScreeningResult(entityId, entityName, worstCategory, maxScore,
                classifications, Instant.now());
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface NewsSourcePort {
        /** Fetches articles mentioning the entity name published in the last {@code days} days. */
        List<NewsArticle> fetchRecent(String entityName, int days);
    }

    public interface ClassificationModelPort {
        /**
         * Classifies a single article body. Returns category, confidence score, and SHAP
         * token-level contributions.
         */
        ClassificationResult classify(String articleText);
    }

    // ─── Enums / domain records ───────────────────────────────────────────────

    public enum AdverseCategory {
        ADVERSE_FINANCIAL_CRIME,
        REGULATORY,
        POLITICAL_EXPOSURE,
        LITIGATION,
        NONE
    }

    public record NewsArticle(String articleId, String title, String body, Instant publishedAt) {}

    public record ClassificationResult(AdverseCategory category, double score,
                                        Map<String, Double> shapContributions) {}

    public record ArticleClassification(String articleId, String title, AdverseCategory category,
                                         double score, Map<String, Double> shapContributions) {}

    public record ScreeningResult(String entityId, String entityName,
                                   AdverseCategory overallCategory, double maxScore,
                                   List<ArticleClassification> articles, Instant screenedAt) {
        public boolean hasHighSignal() { return maxScore >= 0.7 && overallCategory != AdverseCategory.NONE; }
    }

    // ─── Events ───────────────────────────────────────────────────────────────

    public record AdverseMediaHighSignalEvent(String entityId, String articleId,
                                              AdverseCategory category, double score,
                                              Map<String, Double> shapContributions) {}
}
