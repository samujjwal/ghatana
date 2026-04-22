package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactGraphMergeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Three-way semantic merge engine for Artifact Product Models with field-level conflict detection
 *              and configurable resolution strategies.
 * @doc.layer service
 * @doc.pattern Service
 */
public class SemanticMergeEngine {

    private static final Logger log = LoggerFactory.getLogger(SemanticMergeEngine.class);

    private final String resolutionStrategy;

    public SemanticMergeEngine(String resolutionStrategy) {
        this.resolutionStrategy = resolutionStrategy != null ? resolutionStrategy : "auto-resolve";
    }

    /**
     * Perform a three-way merge between base, left (current), and right (incoming) models.
     * Returns the merged model, conflict list, and provenance map.
     */
    public MergeResult merge(ArtifactGraphMergeRequest request) {
        Map<String, Object> base = request.baseModel() != null ? request.baseModel() : Map.of();
        Map<String, Object> left = request.leftModel() != null ? request.leftModel() : Map.of();
        Map<String, Object> right = request.rightModel() != null ? request.rightModel() : Map.of();

        Map<String, Object> merged = new HashMap<>();
        List<MergeConflict> conflicts = new ArrayList<>();
        Map<String, String> provenance = new HashMap<>();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(base.keySet());
        allKeys.addAll(left.keySet());
        allKeys.addAll(right.keySet());

        for (String key : allKeys) {
            Object baseValue = base.get(key);
            Object leftValue = left.get(key);
            Object rightValue = right.get(key);

            MergeFieldResult result = mergeField(key, baseValue, leftValue, rightValue);
            merged.put(key, result.value());
            provenance.put(key, result.provenance());

            if (result.conflict() != null) {
                conflicts.add(result.conflict());
            }
        }

        log.info("Three-way merge completed: {} fields, {} conflicts, strategy={}",
                allKeys.size(), conflicts.size(), resolutionStrategy);

        return new MergeResult(merged, conflicts, provenance);
    }

    private MergeFieldResult mergeField(String key, Object base, Object left, Object right) {
        // All three equal or base is null and both sides agree
        if (Objects.equals(left, right)) {
            return new MergeFieldResult(left, "both", null);
        }

        // Only left changed
        if (Objects.equals(base, right)) {
            return new MergeFieldResult(left, "left", null);
        }

        // Only right changed
        if (Objects.equals(base, left)) {
            return new MergeFieldResult(right, "right", null);
        }

        // Conflict: base, left, and right all differ
        return resolveConflict(key, base, left, right);
    }

    private MergeFieldResult resolveConflict(String key, Object base, Object left, Object right) {
        MergeConflict conflict = new MergeConflict(key, "field-level",
                formatValue(base), formatValue(left), formatValue(right));

        return switch (resolutionStrategy.toLowerCase()) {
            case "left-wins", "last-write-wins" -> {
                log.debug("Conflict on {} resolved by strategy {}: left wins", key, resolutionStrategy);
                yield new MergeFieldResult(left, "left(strategy=" + resolutionStrategy + ")", conflict);
            }
            case "right-wins" -> {
                log.debug("Conflict on {} resolved by strategy {}: right wins", key, resolutionStrategy);
                yield new MergeFieldResult(right, "right(strategy=" + resolutionStrategy + ")", conflict);
            }
            case "union", "auto-resolve", "merge" -> {
                if (left instanceof List && right instanceof List) {
                    List<?> mergedList = unionLists((List<?>) left, (List<?>) right);
                    yield new MergeFieldResult(mergedList, "union", conflict);
                }
                if (left instanceof Map && right instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mergedMap = unionMaps((Map<String, Object>) left, (Map<String, Object>) right);
                    yield new MergeFieldResult(mergedMap, "union", conflict);
                }
                // Default to deeper inspection for primitives
                log.debug("Conflict on {} resolved by strategy {}: right wins (default for primitives)", key, resolutionStrategy);
                yield new MergeFieldResult(right, "right(strategy=" + resolutionStrategy + ")", conflict);
            }
            case "manual-review", "review" -> {
                log.warn("Conflict on {} requires manual review (strategy={})", key, resolutionStrategy);
                yield new MergeFieldResult(base, "manual-review-required", conflict);
            }
            case "longest" -> {
                String leftStr = left != null ? left.toString() : "";
                String rightStr = right != null ? right.toString() : "";
                Object chosen = leftStr.length() >= rightStr.length() ? left : right;
                yield new MergeFieldResult(chosen, "longest", conflict);
            }
            default -> {
                log.warn("Unknown merge strategy '{}', defaulting to right wins for key {}", resolutionStrategy, key);
                yield new MergeFieldResult(right, "right(default)", conflict);
            }
        };
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<?> unionLists(List<?> left, List<?> right) {
        Set<Object> seen = new HashSet<>();
        List<Object> result = new ArrayList<>();
        for (Object item : left) {
            if (seen.add(item)) {
                result.add(item);
            }
        }
        for (Object item : right) {
            if (seen.add(item)) {
                result.add(item);
            }
        }
        // Attempt to sort if elements are comparable
        if (!result.isEmpty() && result.get(0) instanceof Comparable) {
            try {
                result.sort((Comparator) Comparator.naturalOrder());
            } catch (ClassCastException ignored) {
                // Leave unsorted if types are mixed
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unionMaps(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new HashMap<>(left);
        right.forEach((key, value) -> {
            Object existing = result.get(key);
            if (existing instanceof Map && value instanceof Map) {
                result.put(key, unionMaps((Map<String, Object>) existing, (Map<String, Object>) value));
            } else if (existing instanceof List && value instanceof List) {
                result.put(key, unionLists((List<?>) existing, (List<?>) value));
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";
        String str = value.toString();
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }

    public record MergeFieldResult(
        Object value,
        String provenance,
        MergeConflict conflict
    ) {
    }

    public record MergeConflict(
        String fieldPath,
        String conflictType,
        String baseValue,
        String leftValue,
        String rightValue
    ) {
    }

    public record MergeResult(
        Map<String, Object> mergedModel,
        List<MergeConflict> conflicts,
        Map<String, String> fieldProvenance
    ) {
    }
}
