package com.ghatana.appplatform.compliance.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   RAG-based LLM copilot for EDD officers. Indexes K-07 audit logs, EDD case
 *                history, sanctions screening results, and K-03 compliance rules into a
 *                vector store. Provides draft EDD reports, Q&A answers, and suggested
 *                verification steps. K-09 advisory tier — human sign-off required for all output.
 * @doc.layer     Application
 * @doc.pattern   K-09 advisory; Retrieval-Augmented Generation via inner ports; all interactions
 *                logged via audit port
 *
 * Story: D07-017
 */
public class EddCopilotService {

    private static final Logger log = LoggerFactory.getLogger(EddCopilotService.class);

    private final RagContextPort   ragContextPort;
    private final LlmInferencePort llmPort;
    private final AuditLogPort     auditLogPort;
    private final Consumer<Object> eventPublisher;
    private final Counter          draftsGenerated;
    private final Counter          queriesAnswered;

    public EddCopilotService(RagContextPort ragContextPort,
                              LlmInferencePort llmPort,
                              AuditLogPort auditLogPort,
                              Consumer<Object> eventPublisher,
                              MeterRegistry meterRegistry) {
        this.ragContextPort = ragContextPort;
        this.llmPort        = llmPort;
        this.auditLogPort   = auditLogPort;
        this.eventPublisher = eventPublisher;
        this.draftsGenerated = meterRegistry.counter("compliance.edd_copilot.drafts_generated");
        this.queriesAnswered = meterRegistry.counter("compliance.edd_copilot.queries_answered");
    }

    /**
     * Generates a draft EDD report for a case. Advisory only — requires officer review and sign-off.
     *
     * @param caseId      EDD case identifier
     * @param requesterId officer requesting the draft
     * @return draft report with RAG context used and model reasoning
     */
    public EddDraft generateEddDraft(String caseId, String requesterId) {
        RagContext context = ragContextPort.retrieveEddContext(caseId);
        String prompt = buildDraftPrompt(caseId, context);

        LlmResponse response = llmPort.generate(prompt, context.relevantChunks());
        draftsGenerated.increment();

        auditLogPort.log("EDD_COPILOT_DRAFT", requesterId,
                "Generated EDD draft for caseId=" + caseId + " chunks=" + context.relevantChunks().size());

        EddDraft draft = new EddDraft(caseId, response.text(), context.relevantChunks(),
                response.modelId(), Instant.now());

        eventPublisher.accept(new EddDraftGeneratedEvent(caseId, requesterId,
                context.relevantChunks().size(), response.modelId()));
        log.info("EDD draft generated caseId={} requester={} modelId={}", caseId, requesterId, response.modelId());
        return draft;
    }

    /**
     * Answers a natural language query from an EDD officer using RAG over compliance data.
     *
     * @param caseId      optional EDD case for scoped context (null = global search)
     * @param query       officer's natural language question
     * @param requesterId officer identifier
     * @return answer with cited source chunks
     */
    public EddAnswer answerQuery(String caseId, String query, String requesterId) {
        RagContext context = ragContextPort.retrieveQueryContext(caseId, query);
        String prompt = buildQueryPrompt(query, context);

        LlmResponse response = llmPort.generate(prompt, context.relevantChunks());
        queriesAnswered.increment();

        auditLogPort.log("EDD_COPILOT_QUERY", requesterId, "Q=" + truncate(query, 200));

        log.debug("EDD copilot query caseId={} requester={} answerLen={}", caseId, requesterId, response.text().length());
        return new EddAnswer(caseId, query, response.text(), context.relevantChunks(),
                response.modelId(), Instant.now());
    }

    /**
     * Suggests verification steps for an EDD case based on its risk profile and case history.
     *
     * @param caseId      EDD case
     * @param requesterId requesting officer
     * @return ordered list of verification steps
     */
    public List<String> suggestVerificationSteps(String caseId, String requesterId) {
        RagContext context = ragContextPort.retrieveEddContext(caseId);
        String prompt = "Based on the EDD case context, list the 5 most important verification steps "
                      + "the compliance officer should take. Return as a numbered list.\n\n"
                      + "Context:\n" + summariseChunks(context.relevantChunks());

        LlmResponse response = llmPort.generate(prompt, context.relevantChunks());
        auditLogPort.log("EDD_COPILOT_STEPS", requesterId, "Suggestions for caseId=" + caseId);

        List<String> steps = parseSteps(response.text());
        log.info("EDD verification steps generated caseId={} count={}", caseId, steps.size());
        return steps;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    private String buildDraftPrompt(String caseId, RagContext context) {
        return "You are a compliance specialist assistant. Generate a formal EDD report for case "
             + caseId + ". Include: (1) Summary of findings, (2) Risk indicators identified, "
             + "(3) Source references, (4) Recommended resolution. Use only the provided context. "
             + "Mark any uncertain areas for officer review.\n\nContext:\n"
             + summariseChunks(context.relevantChunks());
    }

    private String buildQueryPrompt(String query, RagContext context) {
        return "You are a compliance specialist assistant. Answer the following officer question "
             + "using only the provided compliance records. If the answer is not in the context, say so.\n"
             + "Question: " + query + "\n\nContext:\n" + summariseChunks(context.relevantChunks());
    }

    private String summariseChunks(List<String> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(truncate(chunks.get(i), 500)).append("\n");
        }
        return sb.toString();
    }

    private List<String> parseSteps(String text) {
        return java.util.Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(s -> s.matches("^\\d+\\..*"))
                .map(s -> s.replaceFirst("^\\d+\\.\\s*", ""))
                .toList();
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ─── Ports ────────────────────────────────────────────────────────────────

    public interface RagContextPort {
        RagContext retrieveEddContext(String caseId);
        RagContext retrieveQueryContext(String caseId, String query);
    }

    public interface LlmInferencePort {
        LlmResponse generate(String prompt, List<String> contextChunks);
    }

    public interface AuditLogPort {
        void log(String action, String userId, String details);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record RagContext(List<String> relevantChunks) {}
    public record LlmResponse(String text, String modelId) {}
    public record EddDraft(String caseId, String draftText, List<String> sourceChunks,
                            String modelId, Instant generatedAt) {
        /** Advisory annotation — always shown in the draft header. */
        public String advisoryNotice() {
            return "ADVISORY: This draft was generated by an AI model. It must be reviewed, "
                 + "verified, and signed off by a qualified compliance officer before use.";
        }
    }
    public record EddAnswer(String caseId, String question, String answer,
                             List<String> sourceChunks, String modelId, Instant answeredAt) {}

    // ─── Events ───────────────────────────────────────────────────────────────

    public record EddDraftGeneratedEvent(String caseId, String requesterId,
                                          int chunksUsed, String modelId) {}
}
