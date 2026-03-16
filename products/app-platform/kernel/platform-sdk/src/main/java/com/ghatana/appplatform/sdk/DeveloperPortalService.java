package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Assemble developer portal content: API Reference, Event Catalog, SDK Reference,
 *              Changelog, API explorer sandbox, full-text search index, and feedback mechanism.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter; Promise.ofBlocking
 *
 * STORY-K12-012: Developer portal
 */
public class DeveloperPortalService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface ContentSourcePort {
        /** Retrieve a named content section (e.g. "api-reference", "changelog"). Returns Markdown/HTML. */
        String getContent(String section) throws Exception;
        List<String> listSections() throws Exception;
    }

    public interface SearchIndexPort {
        void index(String docId, String title, String content) throws Exception;
        List<SearchResult> search(String query) throws Exception;
    }

    public interface FeedbackPort {
        void submitFeedback(PortalFeedback feedback) throws Exception;
        List<PortalFeedback> listFeedback(String pageId, int limit) throws Exception;
    }

    public interface ApiExplorerPort {
        /** Execute an API call on behalf of a sandbox user and return the response body. */
        ExplorerResponse execute(ExplorerRequest request) throws Exception;
    }

    public interface PortalStoragePort {
        String store(String path, byte[] content) throws Exception;
        Optional<byte[]> load(String path) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record PortalPage(String pageId, String title, String htmlContent, String lastUpdated) {}

    public record SearchResult(String docId, String title, double score, String excerpt) {}

    public record PortalFeedback(
        String feedbackId,
        String pageId,
        String userId,
        boolean helpful,
        String comment,
        String submittedAt
    ) {}

    public record ExplorerRequest(
        String method,
        String path,
        Map<String, String> headers,
        String body,
        String authToken
    ) {}

    public record ExplorerResponse(int statusCode, String body, Map<String, String> headers, long latencyMs) {}

    public record PortalBuildResult(
        boolean success,
        List<String> pagesBuilt,
        String searchIndexedAt,
        String error
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final ContentSourcePort contentSource;
    private final SearchIndexPort searchIndex;
    private final FeedbackPort feedback;
    private final ApiExplorerPort apiExplorer;
    private final PortalStoragePort storage;
    private final Executor executor;
    private final Counter portrailBuildsCounter;
    private final Counter explorerRequestsCounter;

    public DeveloperPortalService(
        ContentSourcePort contentSource,
        SearchIndexPort searchIndex,
        FeedbackPort feedback,
        ApiExplorerPort apiExplorer,
        PortalStoragePort storage,
        MeterRegistry registry,
        Executor executor
    ) {
        this.contentSource = contentSource;
        this.searchIndex   = searchIndex;
        this.feedback      = feedback;
        this.apiExplorer   = apiExplorer;
        this.storage       = storage;
        this.executor      = executor;
        this.portrailBuildsCounter  = Counter.builder("sdk.portal.builds").register(registry);
        this.explorerRequestsCounter = Counter.builder("sdk.portal.explorer.requests").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Rebuild all portal pages from source and re-index for search. */
    public Promise<PortalBuildResult> buildPortal() {
        return Promise.ofBlocking(executor, () -> {
            try {
                List<String> sections = contentSource.listSections();
                List<String> built = new ArrayList<>();

                for (String section : sections) {
                    try {
                        String content = contentSource.getContent(section);
                        byte[] html = wrapInPortalTemplate(section, content);
                        storage.store("portal/" + section + "/index.html", html);
                        searchIndex.index(section, sectionTitle(section), stripHtml(content));
                        built.add(section);
                    } catch (Exception e) {
                        // Log and continue
                    }
                }

                portrailBuildsCounter.increment();
                return new PortalBuildResult(true, built, now(), null);
            } catch (Exception e) {
                return new PortalBuildResult(false, List.of(), null, e.getMessage());
            }
        });
    }

    /** Get a specific portal page. */
    public Promise<Optional<PortalPage>> getPage(String section) {
        return Promise.ofBlocking(executor, () -> {
            Optional<byte[]> stored = storage.load("portal/" + section + "/index.html");
            if (stored.isEmpty()) return Optional.empty();
            String html = new String(stored.get(), java.nio.charset.StandardCharsets.UTF_8);
            return Optional.of(new PortalPage(section, sectionTitle(section), html, now()));
        });
    }

    /** Full-text search across portal content. */
    public Promise<List<SearchResult>> search(String query) {
        return Promise.ofBlocking(executor, () -> searchIndex.search(query));
    }

    /** Execute a sandboxed API call via the API Explorer. */
    public Promise<ExplorerResponse> explore(ExplorerRequest request) {
        return Promise.ofBlocking(executor, () -> {
            explorerRequestsCounter.increment();
            return apiExplorer.execute(request);
        });
    }

    /** Submit feedback for a portal page. */
    public Promise<Void> submitFeedback(String pageId, String userId, boolean helpful, String comment) {
        return Promise.ofBlocking(executor, () -> {
            PortalFeedback fb = new PortalFeedback(
                UUID.randomUUID().toString(), pageId, userId, helpful, comment, now()
            );
            feedback.submitFeedback(fb);
            return null;
        });
    }

    /** List feedback for a page (for admin review). */
    public Promise<List<PortalFeedback>> listFeedback(String pageId, int limit) {
        return Promise.ofBlocking(executor, () -> feedback.listFeedback(pageId, limit));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private byte[] wrapInPortalTemplate(String section, String content) {
        String html = "<!DOCTYPE html><html><head><title>" + sectionTitle(section) +
            " — Developer Portal</title></head><body>" + content + "</body></html>";
        return html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String sectionTitle(String section) {
        return switch (section) {
            case "api-reference"  -> "API Reference";
            case "event-catalog"  -> "Event Catalog";
            case "sdk-reference"  -> "SDK Reference";
            case "changelog"      -> "Changelog";
            case "quickstart"     -> "Quick Start";
            default               -> section;
        };
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String now() { return java.time.Instant.now().toString(); }
}
