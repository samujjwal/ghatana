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
  * @doc.pattern Component
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

        // Apply mastery-aware ordering
        List<ScoredMemoryItem> orderedItems = orderByMasteryPriority(items);

        StringBuilder sb = new StringBuilder();

        if (config.isGroupByTier()) {
            Map<MemoryItemType, List<ScoredMemoryItem>> grouped = orderedItems.stream()
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
            for (ScoredMemoryItem scored : orderedItems) {
                appendItem(sb, scored, config);
            }
        }

        return truncateToTokens(sb.toString(), config.getMaxTokens());
    }

    /**
     * Orders memory items by mastery priority:
     * 1. Blocking warnings / negative knowledge
     * 2. Mastered procedures
     * 3. Competent procedures
     * 4. Semantic facts
     * 5. Episodes
     * 6. Tentative/exploratory context
     */
    @NotNull
    private List<ScoredMemoryItem> orderByMasteryPriority(@NotNull List<ScoredMemoryItem> items) {
        return items.stream()
                .sorted((a, b) -> {
                    int priorityA = getMasteryPriority(a.getItem());
                    int priorityB = getMasteryPriority(b.getItem());
                    if (priorityA != priorityB) {
                        return Integer.compare(priorityB, priorityA); // Higher priority first
                    }
                    // Secondary sort by score
                    return Double.compare(b.getScore(), a.getScore());
                })
                .toList();
    }

    /**
     * Returns mastery priority for ordering (higher = more important).
     */
    private int getMasteryPriority(@NotNull MemoryItem item) {
        String masteryState = item.getLabels().get("masteryState");
        String negativeKnowledge = item.getLabels().get("negativeKnowledge");
        String tentative = item.getLabels().get("tentative");

        // Negative knowledge gets highest priority (blocking warnings)
        if ("true".equalsIgnoreCase(negativeKnowledge)) {
            return 100;
        }

        // Mastery state priorities
        if (masteryState != null) {
            return switch (masteryState) {
                case "MASTERED" -> 90;
                case "COMPETENT" -> 80;
                case "PRACTICED" -> 70;
                case "OBSERVED" -> 60;
                case "MAINTENANCE_ONLY" -> 50;
                case "OBSOLETE", "RETIRED" -> 10;
                default -> 50;
            };
        }

        // Type-based priorities for non-mastery items
        return switch (item.getType()) {
            case PROCEDURE -> 70;
            case FACT -> 50;
            case EPISODE -> 30;
            default -> 20;
        };
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

        // Mastery state markers
        appendMasteryMarkers(sb, item);

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

    /**
     * Appends mastery state markers to the formatted output.
     */
    private void appendMasteryMarkers(StringBuilder sb, MemoryItem item) {
        String masteryState = item.getLabels().get("masteryState");
        String negativeKnowledge = item.getLabels().get("negativeKnowledge");
        String tentative = item.getLabels().get("tentative");
        String requiresVerification = item.getLabels().get("requiresVerification");
        String versionMismatch = item.getLabels().get("versionMismatch");

        // Negative knowledge marker (highest priority for safety)
        if ("true".equalsIgnoreCase(negativeKnowledge)) {
            sb.append("[NEGATIVE_KNOWLEDGE] ");
            return; // Negative knowledge doesn't need other markers
        }

        // Obsolete/retired marker
        if ("OBSOLETE".equals(masteryState) || "RETIRED".equals(masteryState)) {
            sb.append("[OBSOLETE - DO NOT USE] ");
            return; // Obsolete items are blocked
        }

        // Maintenance-only marker
        if ("MAINTENANCE_ONLY".equals(masteryState)) {
            sb.append("[MAINTENANCE_ONLY] ");
        }

        // Mastery state markers
        if (masteryState != null) {
            sb.append("[").append(masteryState).append("] ");
        }

        // Version mismatch marker
        if ("true".equalsIgnoreCase(versionMismatch)) {
            sb.append("[VERSION_MISMATCH] ");
        }

        // Verification marker
        if ("true".equalsIgnoreCase(requiresVerification)) {
            sb.append("[REQUIRES_VERIFICATION] ");
        }

        // Tentative marker
        if ("true".equalsIgnoreCase(tentative)) {
            sb.append("[TENTATIVE] ");
        }
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
