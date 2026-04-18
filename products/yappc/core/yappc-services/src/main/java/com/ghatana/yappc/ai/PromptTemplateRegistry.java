package com.ghatana.yappc.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Prompt template registry with versioning and deterministic A/B variant selection.
 *
 * @doc.type class
 * @doc.purpose Registers and resolves prompt templates by key/version with deterministic A/B selection
 * @doc.layer service
 * @doc.pattern Registry
 */
public final class PromptTemplateRegistry {

    private final ConcurrentMap<String, List<PromptTemplateVersion>> templatesByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeVersionByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, VariantScoreStats> scoreStatsByVariant = new ConcurrentHashMap<>();

    public void register(PromptTemplateVersion template) {
        Objects.requireNonNull(template, "template must not be null");

        templatesByKey.compute(template.key(), (key, existing) -> {
            List<PromptTemplateVersion> templates = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            templates.removeIf(item -> item.version().equalsIgnoreCase(template.version())
                    && item.variant().equalsIgnoreCase(template.variant()));
            templates.add(template);
            templates.sort(Comparator.comparing(PromptTemplateVersion::version));
            return List.copyOf(templates);
        });
    }

    public Optional<PromptTemplateVersion> latest(String key) {
        List<PromptTemplateVersion> templates = templatesByKey.get(key);
        if (templates == null || templates.isEmpty()) {
            return Optional.empty();
        }

        return templates.stream()
                .max(Comparator.comparing(PromptTemplateVersion::version));
    }

    public Optional<String> activeVersion(String key) {
        return Optional.ofNullable(activeVersionByKey.get(key));
    }

    public void setActiveVersion(String key, String version) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(version, "version must not be null");

        boolean exists = templatesByKey.getOrDefault(key, List.of()).stream()
                .anyMatch(item -> item.version().equalsIgnoreCase(version));
        if (!exists) {
            throw new IllegalArgumentException("No template exists for key='" + key + "' and version='" + version + "'");
        }

        activeVersionByKey.put(key, version);
    }

    public boolean rollbackToVersion(String key, String version) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(version, "version must not be null");

        boolean exists = templatesByKey.getOrDefault(key, List.of()).stream()
                .anyMatch(item -> item.version().equalsIgnoreCase(version));
        if (!exists) {
            return false;
        }

        activeVersionByKey.put(key, version);
        return true;
    }

    public Optional<PromptTemplateVersion> find(String key, String version, String variant) {
        List<PromptTemplateVersion> templates = templatesByKey.get(key);
        if (templates == null || templates.isEmpty()) {
            return Optional.empty();
        }

        return templates.stream()
                .filter(item -> item.version().equalsIgnoreCase(version)
                        && item.variant().equalsIgnoreCase(variant))
                .findFirst();
    }

    public Optional<PromptTemplateVersion> selectForExperiment(
            String key,
            String version,
            String subjectKey,
            String experimentKey
    ) {
        Objects.requireNonNull(subjectKey, "subjectKey must not be null");
        Objects.requireNonNull(experimentKey, "experimentKey must not be null");

        List<PromptTemplateVersion> candidates = templatesByKey.getOrDefault(key, List.of()).stream()
                .filter(item -> item.version().equalsIgnoreCase(version))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = candidates.stream().mapToInt(PromptTemplateVersion::weight).sum();
        if (totalWeight <= 0) {
            return Optional.of(candidates.get(0));
        }

        int bucket = Math.floorMod(Objects.hash(subjectKey, experimentKey), totalWeight);
        int runningWeight = 0;
        for (PromptTemplateVersion candidate : candidates) {
            runningWeight += candidate.weight();
            if (bucket < runningWeight) {
                return Optional.of(candidate);
            }
        }

        return Optional.of(candidates.get(candidates.size() - 1));
    }

    public Optional<PromptTemplateVersion> selectForActiveExperiment(
            String key,
            String subjectKey,
            String experimentKey
    ) {
        String resolvedVersion = activeVersionByKey.get(key);
        if (resolvedVersion == null) {
            resolvedVersion = latest(key).map(PromptTemplateVersion::version).orElse(null);
        }

        if (resolvedVersion == null) {
            return Optional.empty();
        }

        return selectForExperiment(key, resolvedVersion, subjectKey, experimentKey);
    }

    public void recordVariantScore(
            String key,
            String version,
            String variant,
            double score
    ) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(variant, "variant must not be null");
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }

        double clamped = Math.max(0.0, Math.min(1.0, score));
        String statsKey = statsKey(key, version, variant);
        scoreStatsByVariant.computeIfAbsent(statsKey, ignored -> new VariantScoreStats())
                .record(clamped);
    }

    public boolean rebalanceVariantWeights(String key, String version, int minimumSamplesPerVariant) {
        if (minimumSamplesPerVariant <= 0) {
            throw new IllegalArgumentException("minimumSamplesPerVariant must be > 0");
        }

        List<PromptTemplateVersion> candidates = templatesByKey.getOrDefault(key, List.of()).stream()
                .filter(item -> item.version().equalsIgnoreCase(version))
                .toList();

        if (candidates.isEmpty()) {
            return false;
        }

        List<VariantPerformance> scoredVariants = new ArrayList<>();
        for (PromptTemplateVersion candidate : candidates) {
            VariantScoreStats stats = scoreStatsByVariant.get(statsKey(key, version, candidate.variant()));
            if (stats == null || stats.sampleCount() < minimumSamplesPerVariant) {
                return false;
            }
            scoredVariants.add(new VariantPerformance(candidate.variant(), stats.average()));
        }

        double totalScore = scoredVariants.stream().mapToDouble(VariantPerformance::averageScore).sum();
        if (totalScore <= 0.0) {
            return false;
        }

        List<PromptTemplateVersion> reweighted = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < candidates.size(); i++) {
            PromptTemplateVersion candidate = candidates.get(i);
            double variantScore = scoredVariants.stream()
                    .filter(item -> item.variant().equalsIgnoreCase(candidate.variant()))
                    .mapToDouble(VariantPerformance::averageScore)
                    .findFirst()
                    .orElse(0.0);

            int newWeight;
            if (i == candidates.size() - 1) {
                newWeight = Math.max(1, 100 - allocated);
            } else {
                newWeight = Math.max(1, (int) Math.round((variantScore / totalScore) * 100.0));
                allocated += newWeight;
            }

            reweighted.add(PromptTemplateVersion.of(
                    candidate.key(),
                    candidate.version(),
                    candidate.variant(),
                    candidate.template(),
                    newWeight));
        }

        templatesByKey.compute(key, (mapKey, existing) -> {
            List<PromptTemplateVersion> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            updated.removeIf(item -> item.version().equalsIgnoreCase(version));
            updated.addAll(reweighted);
            updated.sort(Comparator.comparing(PromptTemplateVersion::version));
            return List.copyOf(updated);
        });

        return true;
    }

    public String render(PromptTemplateVersion template, Map<String, String> variables) {
        String rendered = template.template();
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered = rendered.replace("${" + variable.getKey() + "}", variable.getValue());
        }
        return rendered;
    }

    private String statsKey(String key, String version, String variant) {
        return key + "::" + version + "::" + variant;
    }

    private record VariantPerformance(String variant, double averageScore) {
    }

    private static final class VariantScoreStats {
        private final DoubleAdder scoreTotal = new DoubleAdder();
        private final AtomicInteger samples = new AtomicInteger(0);

        void record(double score) {
            scoreTotal.add(score);
            samples.incrementAndGet();
        }

        int sampleCount() {
            return samples.get();
        }

        double average() {
            int sampleCount = samples.get();
            if (sampleCount == 0) {
                return 0.0;
            }
            return scoreTotal.sum() / sampleCount;
        }
    }
}