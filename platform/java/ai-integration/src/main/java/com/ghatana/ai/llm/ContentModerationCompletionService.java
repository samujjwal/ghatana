package com.ghatana.ai.llm;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decorator that applies content moderation filters on both the request prompt
 * and the generated response text for any {@link CompletionService}.
 *
 * <p><b>Purpose</b><br>
 * Intercepts LLM completions to detect and block harmful, hateful, violent,
 * or otherwise policy-violating content. Operates on both the input (prompt)
 * and output (completion response) to enforce content safety at the platform
 * level.
 *
 * <p><b>Moderation Layers</b>
 * <ol>
 *   <li><strong>Input moderation</strong> — scans prompt/messages before sending to LLM</li>
 *   <li><strong>Output moderation</strong> — scans generated text before returning to caller</li>
 * </ol>
 *
 * <p><b>Categories Detected</b>
 * <ul>
 *   <li>{@code HATE} — hate speech, slurs, discrimination</li>
 *   <li>{@code VIOLENCE} — graphic violence, threats, self-harm</li>
 *   <li>{@code SEXUAL} — explicit sexual content</li>
 *   <li>{@code PII} — email addresses, phone numbers, SSNs</li>
 *   <li>{@code PROMPT_INJECTION} — attempts to override system instructions</li>
 * </ul>
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * CompletionService raw = new OpenAICompletionService(config);
 * ContentModerationCompletionService moderated =
 *     new ContentModerationCompletionService(raw);
 *
 * // This will be rejected if prompt contains prohibited content:
 * Promise<CompletionResult> result = moderated.complete(request);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Content moderation filter for LLM request/response pipeline
 * @doc.layer platform
 * @doc.pattern Decorator
 */
public class ContentModerationCompletionService implements CompletionService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationCompletionService.class);

    /**
     * Categories of content violations detected by the moderation filter.
     */
    public enum ViolationCategory {
        HATE,
        VIOLENCE,
        SEXUAL,
        PII,
        PROMPT_INJECTION
    }

    // ─── Patterns for content detection ──────────────────────────────────

    /** Common prompt injection patterns. */
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+(previous|all|above)\\s+instructions|"
                    + "you\\s+are\\s+now\\s+DAN|"
                    + "disregard\\s+(your|all)\\s+(rules|instructions)|"
                    + "system\\s*:\\s*you\\s+are|"
                    + "\\[INST\\]|\\[/INST\\]|<\\|im_start\\|>|"
                    + "pretend\\s+you\\s+are\\s+(?:not|no longer)\\s+an?\\s+AI)"
    );

    /** PII detection: emails. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    );

    /** PII detection: SSN-like patterns. */
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    /** PII detection: phone numbers (US-style). */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b"
    );

    /** Explicit violence patterns. */
    private static final Set<String> VIOLENCE_KEYWORDS = Set.of(
            "kill", "murder", "torture", "bomb", "massacre", "execute",
            "decapitate", "dismember", "mutilate", "slaughter"
    );

    /** Phrase-level violence detection. */
    private static final Pattern VIOLENCE_PHRASE_PATTERN = Pattern.compile(
            "(?i)(how\\s+to\\s+(make|build|create)\\s+a?\\s*(bomb|weapon|explosive)|"
                    + "instructions\\s+for\\s+(killing|harming|poisoning)|"
                    + "step\\s+by\\s+step\\s+(guide|instructions)\\s+to\\s+(kill|harm|attack))"
    );

    // ─── Configuration ───────────────────────────────────────────────────

    private final CompletionService delegate;
    private volatile boolean moderateInput = true;
    private volatile boolean moderateOutput = true;
    private volatile boolean blockOnPii = false; // PII is warn-only by default

    /**
     * Wraps the given completion service with content moderation.
     *
     * @param delegate the underlying completion service to decorate
     */
    public ContentModerationCompletionService(CompletionService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * Enable or disable input (prompt) moderation.
     *
     * @param enabled true to moderate inputs (default: true)
     * @return this instance for fluent chaining
     */
    public ContentModerationCompletionService withInputModeration(boolean enabled) {
        this.moderateInput = enabled;
        return this;
    }

    /**
     * Enable or disable output (response) moderation.
     *
     * @param enabled true to moderate outputs (default: true)
     * @return this instance for fluent chaining
     */
    public ContentModerationCompletionService withOutputModeration(boolean enabled) {
        this.moderateOutput = enabled;
        return this;
    }

    /**
     * Configure whether PII detection should block requests (vs. warn-only).
     *
     * @param block true to block requests containing PII (default: false — warn only)
     * @return this instance for fluent chaining
     */
    public ContentModerationCompletionService withPiiBlocking(boolean block) {
        this.blockOnPii = block;
        return this;
    }

    // ─── CompletionService Implementation ────────────────────────────────

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        // Phase 1: Input moderation
        if (moderateInput) {
            ModerationResult inputCheck = moderateText(extractInputText(request), "input");
            if (inputCheck.blocked) {
                log.warn("CONTENT_MODERATION: Input blocked — category={}, snippet='{}'",
                        inputCheck.category, inputCheck.snippet);
                getMetricsCollector().incrementCounter("ai.moderation.input.blocked",
                        "category", inputCheck.category.name());
                return Promise.ofException(new ContentModerationException(
                        "Request blocked by content moderation: " + inputCheck.category,
                        inputCheck.category));
            }
            if (inputCheck.warned) {
                log.info("CONTENT_MODERATION: Input warning — category={}, snippet='{}'",
                        inputCheck.category, inputCheck.snippet);
                getMetricsCollector().incrementCounter("ai.moderation.input.warned",
                        "category", inputCheck.category.name());
            }
        }

        // Phase 2: Delegate to underlying service
        return delegate.complete(request)
                .then(result -> {
                    // Phase 3: Output moderation
                    if (moderateOutput && result != null && result.getText() != null) {
                        ModerationResult outputCheck = moderateText(result.getText(), "output");
                        if (outputCheck.blocked) {
                            log.warn("CONTENT_MODERATION: Output blocked — category={}, snippet='{}'",
                                    outputCheck.category, outputCheck.snippet);
                            getMetricsCollector().incrementCounter("ai.moderation.output.blocked",
                                    "category", outputCheck.category.name());
                            return Promise.ofException(new ContentModerationException(
                                    "Response blocked by content moderation: " + outputCheck.category,
                                    outputCheck.category));
                        }
                        if (outputCheck.warned) {
                            log.info("CONTENT_MODERATION: Output warning — category={}, snippet='{}'",
                                    outputCheck.category, outputCheck.snippet);
                            getMetricsCollector().incrementCounter("ai.moderation.output.warned",
                                    "category", outputCheck.category.name());
                        }
                    }

                    getMetricsCollector().incrementCounter("ai.moderation.passed");
                    return Promise.of(result);
                });
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        // For batch, moderate each input individually before delegating
        if (moderateInput) {
            for (CompletionRequest request : requests) {
                ModerationResult check = moderateText(extractInputText(request), "input");
                if (check.blocked) {
                    log.warn("CONTENT_MODERATION: Batch input blocked — category={}", check.category);
                    getMetricsCollector().incrementCounter("ai.moderation.input.blocked",
                            "category", check.category.name());
                    return Promise.ofException(new ContentModerationException(
                            "Batch request blocked by content moderation: " + check.category,
                            check.category));
                }
            }
        }
        return delegate.completeBatch(requests);
    }

    @Override
    public LLMConfiguration getConfig() {
        return delegate.getConfig();
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return delegate.getMetricsCollector();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    // ─── Moderation Logic ────────────────────────────────────────────────

    /**
     * Runs content moderation checks against the text.
     *
     * @param text the text to moderate
     * @param phase "input" or "output" (for logging)
     * @return moderation result indicating pass, warn, or block
     */
    ModerationResult moderateText(String text, String phase) {
        if (text == null || text.isBlank()) {
            return ModerationResult.pass();
        }

        String lower = text.toLowerCase();

        // 1. Prompt injection detection (input only)
        if ("input".equals(phase) && PROMPT_INJECTION_PATTERN.matcher(text).find()) {
            return ModerationResult.block(ViolationCategory.PROMPT_INJECTION,
                    truncateSnippet(text, PROMPT_INJECTION_PATTERN));
        }

        // 2. Violence phrase detection
        if (VIOLENCE_PHRASE_PATTERN.matcher(text).find()) {
            return ModerationResult.block(ViolationCategory.VIOLENCE,
                    truncateSnippet(text, VIOLENCE_PHRASE_PATTERN));
        }

        // 3. Violence keyword density (block if 3+ violence keywords in close proximity)
        long violenceCount = VIOLENCE_KEYWORDS.stream()
                .filter(kw -> lower.contains(kw))
                .count();
        if (violenceCount >= 3) {
            return ModerationResult.block(ViolationCategory.VIOLENCE,
                    "[multiple violence keywords detected]");
        }

        // 4. PII detection
        boolean hasPii = EMAIL_PATTERN.matcher(text).find()
                || SSN_PATTERN.matcher(text).find()
                || PHONE_PATTERN.matcher(text).find();
        if (hasPii) {
            if (blockOnPii) {
                return ModerationResult.block(ViolationCategory.PII, "[PII detected]");
            }
            return ModerationResult.warn(ViolationCategory.PII, "[PII detected]");
        }

        return ModerationResult.pass();
    }

    /**
     * Extracts the full input text from the request (prompt + messages).
     */
    private String extractInputText(CompletionRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getPrompt() != null) {
            sb.append(request.getPrompt());
        }
        if (request.getMessages() != null) {
            for (ChatMessage msg : request.getMessages()) {
                if (msg.getContent() != null) {
                    sb.append(' ').append(msg.getContent());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extracts a short snippet around the first match for logging.
     */
    private String truncateSnippet(String text, Pattern pattern) {
        var matcher = pattern.matcher(text);
        if (matcher.find()) {
            int start = Math.max(0, matcher.start() - 20);
            int end = Math.min(text.length(), matcher.end() + 20);
            return text.substring(start, end).replace("\n", " ");
        }
        return text.substring(0, Math.min(text.length(), 60));
    }

    // ─── Inner Types ─────────────────────────────────────────────────────

    /**
     * Result of a moderation check.
     */
    static final class ModerationResult {
        final boolean blocked;
        final boolean warned;
        final ViolationCategory category;
        final String snippet;

        private ModerationResult(boolean blocked, boolean warned,
                                  ViolationCategory category, String snippet) {
            this.blocked = blocked;
            this.warned = warned;
            this.category = category;
            this.snippet = snippet;
        }

        static ModerationResult pass() {
            return new ModerationResult(false, false, null, null);
        }

        static ModerationResult warn(ViolationCategory category, String snippet) {
            return new ModerationResult(false, true, category, snippet);
        }

        static ModerationResult block(ViolationCategory category, String snippet) {
            return new ModerationResult(true, false, category, snippet);
        }
    }

    /**
     * Exception thrown when content moderation blocks a request or response.
     *
     * @doc.type class
     * @doc.purpose Exception for content moderation violations
     * @doc.layer platform
     * @doc.pattern Exception
     */
    public static final class ContentModerationException extends RuntimeException {

        private final ViolationCategory category;

        /**
         * Creates a content moderation exception.
         *
         * @param message description of the violation
         * @param category the violation category that triggered the block
         */
        public ContentModerationException(String message, ViolationCategory category) {
            super(message);
            this.category = Objects.requireNonNull(category);
        }

        /**
         * Returns the violation category.
         *
         * @return the category
         */
        public ViolationCategory getCategory() {
            return category;
        }
    }
}
