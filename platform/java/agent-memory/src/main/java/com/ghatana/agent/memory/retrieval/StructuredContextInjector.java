package com.ghatana.agent.memory.retrieval;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.MemoryItemType;
import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import com.ghatana.agent.memory.store.ScoredMemoryItem;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Formats retrieved memory items into structured context blocks with
 * provenance display, recency/validity indicators, conflict markers,
 * and tier grouping.
 *
 * @doc.type class
 * @doc.purpose Structured context formatting for LLM injection
 * @doc.layer agent-memory
 */
public class StructuredContextInjector implements ContextInjector {

    @Override
    @NotNull
    public String formatForInjection(
            @NotNull List<ScoredMemoryItem> items,
            @NotNull InjectionConfig config) {

        if (items.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (config.isGroupByTier()) {
            Map<MemoryItemType, List<ScoredMemoryItem>> grouped = items.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getItem().getType(),
                            LinkedHashMap::new,
                            Collectors.toList()));

            for (Map.Entry<MemoryItemType, List<ScoredMemoryItem>> entry : grouped.entrySet()) {
                appendTierHeader(sb, entry.getKey(), config);
                for (ScoredMemoryItem scored : entry.getValue()) {
                    appendItem(sb, scored, config);
                }
                sb.append('\n');
            }
        } else {
            for (ScoredMemoryItem scored : items) {
                appendItem(sb, scored, config);
            }
        }

        return truncateToTokens(sb.toString(), config.getMaxTokens());
    }

    private void appendTierHeader(StringBuilder sb, MemoryItemType type, InjectionConfig config) {
        switch (config.getFormat()) {
            case MARKDOWN -> sb.append("### ").append(tierLabel(type)).append('\n');
            case XML -> sb.append("<tier name=\"").append(type.name().toLowerCase()).append("\">\n");
            case JSON -> sb.append("\"").append(type.name().toLowerCase()).append("\": [\n");
        }
    }

    private void appendItem(StringBuilder sb, ScoredMemoryItem scored, InjectionConfig config) {
        MemoryItem item = scored.getItem();
        switch (config.getFormat()) {
            case MARKDOWN -> appendMarkdownItem(sb, scored, config);
            case XML -> appendXmlItem(sb, scored, config);
            case JSON -> appendJsonItem(sb, scored, config);
        }
    }

    private void appendMarkdownItem(StringBuilder sb, ScoredMemoryItem scored, InjectionConfig config) {
        MemoryItem item = scored.getItem();
        sb.append("- ");

        if (config.isIncludeConflictMarkers()) {
            boolean hasContradiction = item.getLinks().stream()
                    .anyMatch(l -> l.getLinkType() == com.ghatana.agent.memory.model.LinkType.CONTRADICTS);
            if (hasContradiction) {
                sb.append("[CONFLICTING] ");
            }
        }

        sb.append(summarizeItem(item));

        if (config.isIncludeConfidence()) {
            double conf = item.getValidity().effectiveConfidence(Instant.now());
            sb.append(String.format(" (confidence: %.0f%%)", conf * 100));
        }

        if (config.isIncludeProvenance()) {
            sb.append(" [source: ").append(item.getProvenance().getSource()).append("]");
        }

        String recency = formatRecency(item.getCreatedAt());
        sb.append(" (").append(recency).append(")");
        sb.append('\n');
    }

    private void appendXmlItem(StringBuilder sb, ScoredMemoryItem scored, InjectionConfig config) {
        MemoryItem item = scored.getItem();
        sb.append("  <item id=\"").append(item.getId()).append("\" score=\"")
                .append(String.format("%.3f", scored.getScore())).append("\">");
        sb.append(summarizeItem(item));
        sb.append("</item>\n");
    }

    private void appendJsonItem(StringBuilder sb, ScoredMemoryItem scored, InjectionConfig config) {
        MemoryItem item = scored.getItem();
        sb.append("  {\"id\":\"").append(item.getId())
                .append("\",\"score\":").append(String.format("%.3f", scored.getScore()))
                .append(",\"content\":\"").append(escapeJson(summarizeItem(item)))
                .append("\"},\n");
    }

    private String summarizeItem(MemoryItem item) {
        return switch (item) {
            case EnhancedEpisode e -> String.format("[%s] %s → %s",
                    e.getTurnId(), truncate(e.getInput(), 100), truncate(e.getOutput(), 100));
            case EnhancedFact f -> String.format("%s %s %s",
                    f.getSubject(), f.getPredicate(), f.getObject());
            case EnhancedProcedure p -> String.format("Procedure: %s → %s (success: %.0f%%)",
                    p.getSituation(), p.getAction(), p.getSuccessRate() * 100);
            default -> item.getType().name() + ":" + item.getId();
        };
    }

    private String tierLabel(MemoryItemType type) {
        return switch (type) {
            case EPISODE -> "Recent Episodes";
            case FACT -> "Known Facts";
            case PROCEDURE -> "Available Procedures";
            case TASK_STATE -> "Active Tasks";
            case WORKING -> "Working Context";
            case PREFERENCE -> "Preferences";
            case ARTIFACT -> "Artifacts";
            case CUSTOM -> "Custom";
        };
    }

    private String formatRecency(Instant createdAt) {
        long hours = ChronoUnit.HOURS.between(createdAt, Instant.now());
        if (hours < 1) return "just now";
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return days / 7 + "w ago";
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String escapeJson(String s) {
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncateToTokens(String text, int maxTokens) {
        // Rough approximation: 1 token ≈ 4 chars
        int maxChars = maxTokens * 4;
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n[... truncated]";
    }
}
