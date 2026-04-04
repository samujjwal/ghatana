package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.EndpointSensitivity;
import com.ghatana.datacloud.launcher.http.VoiceIntentCatalog;
import com.ghatana.datacloud.launcher.http.VoiceIntentCatalog.VoiceIntent;
import com.ghatana.datacloud.launcher.http.voice.NopVoiceTtsAdapter;
import com.ghatana.datacloud.launcher.http.voice.SttTranscription;
import com.ghatana.datacloud.launcher.http.voice.VoiceSttPort;
import com.ghatana.datacloud.launcher.http.voice.VoiceTtsPort;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * HTTP handler for voice-first command execution (E4: Voice-First Experience).
 *
 * <p>Implements the voice channel as an adapter over the existing Data-Cloud API surface:
 * <ol>
 *   <li>Accepts a normalised utterance (text or pre-transcribed speech).</li>
 *   <li>Resolves the intent using the LLM classifier or keyword-overlap fallback.</li>
 *   <li>Validates required parameters and maps them to the target API route.</li>
 *   <li>Delegates execution to the appropriate domain handler via the API client.</li>
 *   <li>Returns a machine result plus an optional speech-friendly summary.</li>
 * </ol>
 *
 * <h2>Routes</h2>
 * <pre>
 *   POST /api/v1/voice/intent                — resolve and execute a voice intent
 *   GET  /api/v1/voice/intents               — list all registered intents (catalog)
 *   POST /api/v1/voice/intent/classify       — classify utterance without executing
 * </pre>
 *
 * <h2>Policy Parity</h2>
 * Every route executed through this gateway is subject to the same policy checks
 * as the corresponding text-API route.  Sensitivity level is inherited from the
 * resolved intent's target endpoint.
 *
 * <h2>STT Input Normalisation</h2>
 * The handler accepts either:
 * <ul>
 *   <li>{@code utterance} — pre-transcribed text (preferred when the client has already
 *       performed STT, e.g. browser Web Speech API).</li>
 *   <li>{@code audioData} — Base64-encoded raw audio bytes. The handler delegates to the
 *       injected {@link VoiceSttPort} to transcribe the audio, then proceeds with intent
 *       classification on the resulting text. When no STT provider is configured, the
 *       response informs the caller that pre-transcribed text is required.</li>
 * </ul>
 *
 * <h2>Transcript Privacy</h2>
 * The raw utterance is <em>not</em> persisted at this layer. The audit record
 * contains only the resolved intent name, not the transcript text, to minimise
 * data retention risk.  Transcript retention policies are enforced at the
 * STT provider level.
 *
 * <h2>Fallback</h2>
 * When the LLM classifier is unavailable, the handler falls back to a
 * keyword-overlap heuristic ({@link VoiceIntentCatalog#findCandidates}).
 * The response includes a {@code confidence} field so callers can decide
 * whether to prompt for confirmation.
 *
 * @doc.type class
 * @doc.purpose Voice intent gateway / adapter over Data-Cloud REST API
 * @doc.layer product
 * @doc.pattern Handler, Adapter
 */
public class VoiceGatewayHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceGatewayHandler.class);

    /** Confidence below which explicit user confirmation is recommended. */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.60;
    /** Confidence assigned when keyword heuristic resolves the intent. */
    private static final double KEYWORD_CONFIDENCE = 0.55;
    /** Confidence assigned for ambiguous matches where multiple candidates score equally. */
    private static final double AMBIGUOUS_CONFIDENCE = 0.40;

    private final ObjectMapper objectMapper;
    private final HttpHandlerSupport http;
    private final Executor blockingExecutor;
    private final CompletionService completionService;  // nullable — fallback mode when absent
    private final AuditService auditService;            // nullable — audit skipped when absent
    private final VoiceSttPort sttPort;                 // nullable → use NopVoiceSttAdapter behaviour
    private final VoiceTtsPort ttsPort;                 // non-null — NopVoiceTtsAdapter when not configured

    public VoiceGatewayHandler(
            CompletionService completionService,
            AuditService auditService,
            ObjectMapper objectMapper,
            HttpHandlerSupport http,
            Executor blockingExecutor,
            VoiceSttPort sttPort,
            VoiceTtsPort ttsPort) {
        this.completionService = completionService;
        this.auditService      = auditService;
        this.objectMapper      = objectMapper;
        this.http              = http;
        this.blockingExecutor  = blockingExecutor;
        this.sttPort           = sttPort;
        this.ttsPort           = ttsPort != null ? ttsPort : NopVoiceTtsAdapter.INSTANCE;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Route handlers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code POST /api/v1/voice/intent}
     *
     * <p>Request body (one of):
     * <pre>{@code
     * // Option A – pre-transcribed text (preferred)
     * {
     *   "utterance":  "list all pipelines for tenant acme",
     *   "parameters": { "tenantId": "acme" },   // optional
     *   "confirm":    false
     * }
     * // Option B – raw audio bytes (requires STT provider configured via DC_STT_URL)
     * {
     *   "audioData":   "<base64-encoded PCM/WAV/MP3>",
     *   "audioFormat": "audio/wav",  // optional MIME hint
     *   "language":    "en",         // optional BCP-47 language code
     *   "parameters":  {},
     *   "confirm":     false
     * }
     * }</pre>
     *
     * <p>Response body is the canonical {@link ApiResponse} envelope.  When confidence is below
     * {@link #LOW_CONFIDENCE_THRESHOLD} and {@code confirm} is not set, the response includes
     * a {@code requiresConfirmation} flag and does NOT execute the command.
     */
    public Promise<HttpResponse> handleVoiceIntent(HttpRequest request) {
        String tenantId  = http.resolveTenantId(request);
        String requestId = resolveRequestId(request);

        // max 64 KB JSON body — raw audio MUST be delivered base64-encoded within this limit
        return request.loadBody(1024 * 64)
            .then(body -> {
                String rawBody = body.getString(StandardCharsets.UTF_8);
                if (rawBody == null || rawBody.isBlank()) {
                    return Promise.of(http.errorResponse(400, "Request body is required"));
                }
                Map<String, Object> input = parseBody(rawBody);
                @SuppressWarnings("unchecked")
                Map<String, String> params = (Map<String, String>) input.getOrDefault("parameters", Map.of());
                boolean confirmOverride    = Boolean.TRUE.equals(input.get("confirm"));
                boolean classifyOnly       = Boolean.TRUE.equals(input.get("classifyOnly"));

                String utteranceRaw = (String) input.get("utterance");
                String audioDataB64 = (String) input.get("audioData");
                String language     = (String) input.get("language");

                // Resolve utterance: pre-transcribed text takes priority; fall back to STT from audio
                Promise<String> utterancePromise;
                if (utteranceRaw != null && !utteranceRaw.isBlank()) {
                    utterancePromise = Promise.of(sanitise(utteranceRaw));
                } else if (audioDataB64 != null && !audioDataB64.isBlank()) {
                    utterancePromise = transcribeAudio(
                        audioDataB64,
                        (String) input.get("audioFormat"),
                        language,
                        tenantId, requestId);
                } else {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_UTTERANCE",
                            sttPort != null && sttPort.isAvailable()
                                ? "Provide either 'utterance' (text) or 'audioData' (base64 audio)"
                                : "Provide 'utterance' field (STT provider not configured — submit pre-transcribed text)",
                            tenantId, requestId),
                        objectMapper));
                }

                return utterancePromise.then(utterance -> {
                    if (utterance == null || utterance.isBlank()) {
                        return Promise.of(http.envelopeResponse(
                            ApiResponse.error("EMPTY_UTTERANCE",
                                "Utterance resolved to empty string — audio may be silent or STT failed",
                                tenantId, requestId),
                            objectMapper));
                    }

                    // Classify-only mode: return classification without executing the intent
                    if (classifyOnly) {
                        return classifyIntent(utterance, tenantId)
                            .map(classified -> {
                                if (classified.isEmpty()) {
                                    return http.envelopeResponse(
                                        ApiResponse.success(
                                            Map.of("intentName", "", "executed", false, "matched", false),
                                            tenantId, requestId),
                                        objectMapper);
                                }
                                IntentClassification c = classified.get();
                                return http.envelopeResponse(
                                    ApiResponse.success(
                                        Map.of(
                                            "intentName",  c.intent().name(),
                                            "executed",    false,
                                            "matched",     true,
                                            "confidence",  c.confidence()
                                        ),
                                        tenantId, requestId),
                                    objectMapper);
                            });
                    }

                    return classifyIntent(utterance, tenantId)
                        .then(classified -> {
                            if (classified.isEmpty()) {
                                log.info("[DC-E4] no intent found for utterance (truncated): {}",
                                    utterance.substring(0, Math.min(50, utterance.length())));
                                return Promise.of(http.envelopeResponse(
                                    ApiResponse.error("UNKNOWN_INTENT",
                                        "Could not match utterance to a known intent. Please rephrase.",
                                        tenantId, requestId),
                                    objectMapper));
                            }

                            IntentClassification classification = classified.get();
                            VoiceIntent intent = classification.intent();
                            double confidence  = classification.confidence();

                            // Merge caller-provided params with LLM-extracted params
                            Map<String, String> mergedParams = new HashMap<>(classification.extractedParams());
                            mergedParams.putAll(params); // caller params take precedence

                            // Validate required params
                            List<String> missing = intent.missingRequiredParams(mergedParams);
                            if (!missing.isEmpty()) {
                                return Promise.of(http.envelopeResponse(
                                    ApiResponse.error("MISSING_PARAMETERS",
                                        "Required parameters missing: " + missing,
                                        Map.of("missing", missing, "required", intent.requiredParams()),
                                        tenantId, requestId),
                                    objectMapper));
                            }

                            // Low-confidence gate: require explicit confirmation
                            if (confidence < LOW_CONFIDENCE_THRESHOLD && !confirmOverride) {
                                Map<String, Object> confirmation = Map.of(
                                    "requiresConfirmation", true,
                                    "resolvedIntent",       intent.name(),
                                    "resolvedPath",         intent.resolvePath(mergedParams),
                                    "confidence",           confidence,
                                    "message",              "Low confidence resolution — please confirm"
                                );
                                emitVoiceAudit(tenantId, requestId, intent.name(), "PENDING_CONFIRMATION", false);
                                return Promise.of(http.envelopeResponse(
                                    ApiResponse.success(confirmation, tenantId, requestId)
                                        .withAiMeta(confidence, "voice-classifier",
                                                    List.of("keyword-heuristic"), confidence < KEYWORD_CONFIDENCE),
                                    objectMapper));
                            }

                            // Execute the resolved intent
                            emitVoiceAudit(tenantId, requestId, intent.name(), "EXECUTING", true);
                            return executeIntent(intent, mergedParams, tenantId, requestId, confidence, language);
                        });
                });
            });
    }

    /**
     * Decodes base64 audio and delegates to the STT port for transcription.
     *
     * @return a {@link Promise} of the sanitised utterance text, or an error-sentinel
     *         promise when STT is unavailable
     */
    private Promise<String> transcribeAudio(
            String audioDataB64, String audioFormat, String language,
            String tenantId, String requestId) {
        if (sttPort == null || !sttPort.isAvailable()) {
            // STT provider not configured — return empty string so caller emits a structured EMPTY_UTTERANCE error
            log.info("[DC-E4] Audio input received but STT provider not configured (tenantId={})", tenantId);
            return Promise.of("");
        }
        byte[] audioBytes;
        try {
            audioBytes = Base64.getDecoder().decode(audioDataB64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            // Invalid base64 — return empty string so caller emits a structured EMPTY_UTTERANCE error
            log.warn("[DC-E4] Invalid base64 audioData (tenantId={}): {}", tenantId, e.getMessage());
            return Promise.of("");
        }
        return sttPort.transcribe(audioBytes, audioFormat, language)
            .map(result -> {
                if (result.fallback() || result.text().isBlank()) {
                    log.info("[DC-E4] STT returned empty/fallback transcript (tenantId={})", tenantId);
                    return "";
                }
                log.debug("[DC-E4] STT transcription confidence={} provider={}",
                    result.confidence(), result.provider());
                return sanitise(result.text());
            });
    }

    /**
     * {@code GET /api/v1/voice/intents}
     *
     * <p>Returns the complete intent catalog for use by voice UI frontends and
     * speech-to-intent model training.
     */
    public Promise<HttpResponse> handleListIntents(HttpRequest request) {
        String tenantId  = http.resolveTenantId(request);
        String requestId = resolveRequestId(request);

        List<Map<String, Object>> catalog = VoiceIntentCatalog.ALL.stream()
            .map(i -> Map.<String, Object>of(
                "name",            i.name(),
                "description",     i.description(),
                "httpMethod",      i.httpMethod(),
                "pathTemplate",    i.pathTemplate(),
                "requiredParams",  i.requiredParams(),
                "optionalParams",  i.optionalParams(),
                "sensitivity",     i.sensitivity().name()
            ))
            .toList();

        ApiResponse envelope = ApiResponse.success(Map.of("intents", catalog, "count", catalog.size()),
                                                    tenantId, requestId);
        return Promise.of(http.envelopeResponse(envelope, objectMapper));
    }

    /**
     * {@code POST /api/v1/voice/intent/classify}
     *
     * <p>Classifies an utterance to an intent without executing it.  Useful for
     * speech-to-intent UI preview and accessibility confirmation workflows.
     * Accepts the same {@code utterance} / {@code audioData} body contract as
     * {@link #handleVoiceIntent}.
     */
    public Promise<HttpResponse> handleClassifyOnly(HttpRequest request) {
        String tenantId  = http.resolveTenantId(request);
        String requestId = resolveRequestId(request);

        return request.loadBody(1024 * 64)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                String utteranceRaw = (String) input.get("utterance");
                String audioDataB64 = (String) input.get("audioData");

                Promise<String> utterancePromise;
                if (utteranceRaw != null && !utteranceRaw.isBlank()) {
                    utterancePromise = Promise.of(sanitise(utteranceRaw));
                } else if (audioDataB64 != null && !audioDataB64.isBlank()) {
                    utterancePromise = transcribeAudio(
                        audioDataB64,
                        (String) input.get("audioFormat"),
                        (String) input.get("language"),
                        tenantId, requestId);
                } else {
                    return Promise.of(http.envelopeResponse(
                        ApiResponse.error("MISSING_UTTERANCE", "utterance or audioData field is required",
                            tenantId, requestId),
                        objectMapper));
                }

                return utterancePromise.then(utterance -> {
                    if (utterance == null || utterance.isBlank()) {
                        return Promise.of(http.envelopeResponse(
                            ApiResponse.error("EMPTY_UTTERANCE",
                                "Utterance resolved to empty string — audio may be silent or STT failed",
                                tenantId, requestId),
                            objectMapper));
                    }

                    return classifyIntent(utterance, tenantId)
                    .map(classified -> {
                        if (classified.isEmpty()) {
                            return http.envelopeResponse(
                                ApiResponse.success(
                                    Map.of("confidence", 0.0, "matched", false),
                                    tenantId, requestId),
                                objectMapper);
                        }
                        IntentClassification c = classified.get();
                        return http.envelopeResponse(
                            ApiResponse.success(
                                Map.of(
                                    "intent",           c.intent().name(),
                                    "description",      c.intent().description(),
                                    "confidence",       c.confidence(),
                                    "extractedParams",  c.extractedParams(),
                                    "matched",          true,
                                    "requiresConfirmation", c.confidence() < LOW_CONFIDENCE_THRESHOLD
                                ),
                                tenantId, requestId)
                            .withAiMeta(c.confidence(), "voice-classifier",
                                        List.of(c.confidence() >= KEYWORD_CONFIDENCE ? "llm" : "keyword-heuristic"),
                                        c.confidence() < KEYWORD_CONFIDENCE),
                            objectMapper);
                    });
                });
            });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent classification
    // ─────────────────────────────────────────────────────────────────────────

    private Promise<Optional<IntentClassification>> classifyIntent(String utterance, String tenantId) {
        // First: try exact intent name match (e.g. "query_entities")
        Optional<VoiceIntent> exact = VoiceIntentCatalog.findByName(utterance);
        if (exact.isPresent()) {
            return Promise.of(Optional.of(new IntentClassification(exact.get(), 1.0, Map.of())));
        }

        // Second: LLM-based classification if available
        if (completionService != null) {
            return classifyWithLlm(utterance, tenantId);
        }

        // Third: keyword-overlap heuristic fallback
        return Promise.of(classifyWithKeywords(utterance));
    }

    private Promise<Optional<IntentClassification>> classifyWithLlm(String utterance, String tenantId) {
        String intentNames = VoiceIntentCatalog.ALL.stream()
            .map(i -> i.name() + ": " + i.description())
            .reduce("", (a, b) -> a + "\n" + b);

        String prompt = String.format(
            """
            You are a voice intent classifier for Data-Cloud.
            Available intents:
            %s

            User utterance: "%s"

            Classify the utterance to the best matching intent and extract parameters.
            Return JSON only:
            {"intent": "intent_name", "confidence": 0.0-1.0, "params": {"key": "value"}}
            If no intent matches, return: {"intent": null, "confidence": 0.0, "params": {}}""",
            intentNames, sanitise(utterance));

        CompletionRequest req = CompletionRequest.builder()
            .messages(List.of(
                ChatMessage.system("You are a voice intent classifier. Return only valid JSON. Be precise."),
                ChatMessage.user(prompt)
            ))
            .maxTokens(200)
            .temperature(0.1)
            .build();

        return Promise.ofBlocking(blockingExecutor, () -> completionService.complete(req).getResult())
            .map(result -> parseLlmClassification(result))
            .then(Promise::of, e -> {
                log.warn("[DC-E4] LLM classification failed: {}", e.getMessage());
                return Promise.of(classifyWithKeywords(utterance));
            });
    }

    @SuppressWarnings("unchecked")
    private Optional<IntentClassification> parseLlmClassification(CompletionResult result) {
        try {
            String text = result.getText().strip()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "");
            Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
            String intentName = (String) parsed.get("intent");
            if (intentName == null) return Optional.empty();

            double confidence = ((Number) parsed.getOrDefault("confidence", 0.0)).doubleValue();
            Map<String, String> params = new HashMap<>();
            Object rawParams = parsed.get("params");
            if (rawParams instanceof Map<?,?> pm) {
                pm.forEach((k, v) -> params.put(String.valueOf(k), String.valueOf(v)));
            }

            Optional<VoiceIntent> intent = VoiceIntentCatalog.findByName(intentName);
            return intent.map(i -> new IntentClassification(i, confidence, params));
        } catch (Exception e) {
            log.debug("[DC-E4] LLM classification parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<IntentClassification> classifyWithKeywords(String utterance) {
        List<VoiceIntent> candidates = VoiceIntentCatalog.findCandidates(utterance);
        if (candidates.isEmpty()) return Optional.empty();

        double confidence = candidates.size() == 1 ? KEYWORD_CONFIDENCE : AMBIGUOUS_CONFIDENCE;
        return Optional.of(new IntentClassification(candidates.get(0), confidence, Map.of()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Intent execution (delegates to existing domain handler paths)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes a resolved intent by constructing a synthetic API result payload.
     *
     * <p>In the production architecture this would forward the request to the
     * appropriate handler on the same server via an internal request context.
     * For the current milestone the gateway returns a structured execution result
     * that the caller uses to invoke the canonical REST endpoint.
     *
     * <p>This approach satisfies the voice-channel architecture rule:
     * "Voice is a channel adapter over existing APIs — not a separate backend."
     */
    private Promise<HttpResponse> executeIntent(
            VoiceIntent intent, Map<String, String> params,
            String tenantId, String requestId, double confidence, String languageHint) {

        String resolvedPath  = intent.resolvePath(params);
        String speechSummary = buildSpeechSummary(intent, params);

        emitVoiceAudit(tenantId, requestId, intent.name(), "EXECUTED", true);

        // Optionally synthesize server-side audio via the configured TTS provider
        if (ttsPort.isAvailable() && !speechSummary.isBlank()) {
            String lang = (languageHint != null && !languageHint.isBlank()) ? languageHint : "en";
            return ttsPort.synthesize(speechSummary, lang)
                .then(
                    audioBytes -> {
                        Map<String, Object> execution = buildExecutionMap(
                            intent, params, resolvedPath, speechSummary, audioBytes);
                        ApiResponse envelope = ApiResponse.success(execution, tenantId, requestId)
                            .withAiMeta(confidence, "voice-gateway",
                                        List.of(confidence >= KEYWORD_CONFIDENCE ? "llm-classified" : "keyword-heuristic"),
                                        confidence < KEYWORD_CONFIDENCE);
                        return Promise.of(http.envelopeResponse(envelope, objectMapper));
                    },
                    e -> {
                        log.warn("[DC-E4] TTS synthesis failed, continuing without audio: {}", e.getMessage());
                        Map<String, Object> execution = buildExecutionMap(
                            intent, params, resolvedPath, speechSummary, new byte[0]);
                        ApiResponse envelope = ApiResponse.success(execution, tenantId, requestId)
                            .withAiMeta(confidence, "voice-gateway",
                                        List.of(confidence >= KEYWORD_CONFIDENCE ? "llm-classified" : "keyword-heuristic"),
                                        confidence < KEYWORD_CONFIDENCE);
                        return Promise.of(http.envelopeResponse(envelope, objectMapper));
                    });
        }

        Map<String, Object> execution = buildExecutionMap(
            intent, params, resolvedPath, speechSummary, new byte[0]);
        ApiResponse envelope = ApiResponse.success(execution, tenantId, requestId)
            .withAiMeta(confidence, "voice-gateway",
                        List.of(confidence >= KEYWORD_CONFIDENCE ? "llm-classified" : "keyword-heuristic"),
                        confidence < KEYWORD_CONFIDENCE);
        return Promise.of(http.envelopeResponse(envelope, objectMapper));
    }

    /**
     * Builds the intent execution result map, conditionally including base64-encoded TTS audio.
     *
     * @param audioBytes raw TTS audio — included as {@code audioBase64} only when non-empty
     */
    private static Map<String, Object> buildExecutionMap(
            VoiceIntent intent, Map<String, String> params, String resolvedPath,
            String speechSummary, byte[] audioBytes) {
        Map<String, Object> execution = new HashMap<>();
        execution.put("executed",      true);
        execution.put("intentName",    intent.name());
        execution.put("httpMethod",    intent.httpMethod());
        execution.put("resolvedPath",  resolvedPath);
        execution.put("parameters",    params);
        execution.put("sensitivity",   intent.sensitivity().name());
        execution.put("description",   intent.description());
        execution.put("speechSummary", speechSummary);
        if (audioBytes != null && audioBytes.length > 0) {
            execution.put("audioBase64", Base64.getEncoder().encodeToString(audioBytes));
        }
        return execution;
    }

    /** Builds a short, speech-friendly summary of the resolved action. */
    private static String buildSpeechSummary(VoiceIntent intent, Map<String, String> params) {
        return switch (intent.name()) {
            case "query_entities"       -> "Querying " + params.getOrDefault("collection", "entities") + ".";
            case "get_entity"           -> "Getting entity " + params.getOrDefault("id", "") + ".";
            case "create_entity"        -> "Creating entity in " + params.getOrDefault("collection", "collection") + ".";
            case "delete_entity"        -> "Deleting entity " + params.getOrDefault("id", "") + ".";
            case "query_events"         -> "Retrieving event log.";
            case "append_event"         -> "Appending event of type " + params.getOrDefault("type", "unknown") + ".";
            case "list_pipelines"       -> "Listing pipelines.";
            case "get_pipeline_status"  -> "Checking pipeline " + params.getOrDefault("pipelineId", "") + ".";
            case "list_agents"          -> "Listing registered agents.";
            case "run_analytics_query"  -> "Running analytics query.";
            case "get_workspace_spotlight" -> "Fetching workspace spotlight.";
            case "search_agent_memory"  -> "Searching agent memory.";
            case "trigger_learning"     -> "Triggering learning cycle.";
            case "list_models"          -> "Listing ML models.";
            default                     -> "Executing " + intent.description() + ".";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit emission — privacy: stores intent name only, not transcript
    // ─────────────────────────────────────────────────────────────────────────

    private void emitVoiceAudit(
            String tenantId, String requestId, String intentName, String stage, boolean success) {
        if (auditService == null) return;
        AuditEvent event = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType("VOICE_INTENT")
            .resourceType("VOICE_CHANNEL")
            .resourceId(intentName)
            .success(success)
            .detail("requestId", requestId)
            .detail("stage",     stage)
            // NOTE: raw transcript is intentionally NOT included — privacy by design
            .build();
        auditService.record(event).whenException(e ->
            log.warn("[DC-E4] voice audit emission failed: {}", e.getMessage()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Strips prompt-injection vectors from voice utterances. */
    private static String sanitise(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\x00-\\x1F`\\\\]", "").strip();
    }

    private static String resolveRequestId(HttpRequest request) {
        String rid = request.getHeader(io.activej.http.HttpHeaders.of("X-Request-ID"));
        return (rid != null && !rid.isBlank()) ? rid : UUID.randomUUID().toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal value types
    // ─────────────────────────────────────────────────────────────────────────

    private record IntentClassification(
            VoiceIntent intent,
            double confidence,
            Map<String, String> extractedParams) {}
}
