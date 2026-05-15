/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.skill;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of canonical GAA skill manifests.
 *
 * <p>Provides authoritative skill definitions for common GAA capabilities.
 *
 * @doc.type class
 * @doc.purpose Registry of canonical skill manifests
 * @doc.layer agent-core
 * @doc.pattern Registry
 */
public final class CanonicalSkillManifests {

    private CanonicalSkillManifests() {}

    /**
     * Code generation skill manifest.
     */
    @NotNull
    public static SkillManifest codeGeneration() {
        return new SkillManifest(
                "gaa.code-generation",
                "Code Generation",
                "Ability to generate code from natural language descriptions",
                "development",
                "code",
                "1.0.0",
                List.of(
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-codegen-basic",
                                "Basic Code Generation Evaluation",
                                true,
                                "L2"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-codegen-advanced",
                                "Advanced Code Generation Evaluation",
                                true,
                                "L3"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-codegen-regression",
                                "Code Generation Regression Tests",
                                true,
                                "L4"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-codegen-safety",
                                "Code Generation Safety Tests",
                                true,
                                "L4"
                        )
                ),
                Map.of(
                        "L1", new SkillManifest.PromotionCriteria(
                                "L1",
                                3,
                                0.6,
                                Set.of("eval-pack-codegen-basic"),
                                false,
                                5
                        ),
                        "L2", new SkillManifest.PromotionCriteria(
                                "L2",
                                10,
                                0.7,
                                Set.of("eval-pack-codegen-basic"),
                                false,
                                20
                        ),
                        "L3", new SkillManifest.PromotionCriteria(
                                "L3",
                                25,
                                0.8,
                                Set.of("eval-pack-codegen-basic", "eval-pack-codegen-advanced"),
                                true,
                                50
                        ),
                        "L4", new SkillManifest.PromotionCriteria(
                                "L4",
                                50,
                                0.9,
                                Set.of("eval-pack-codegen-basic", "eval-pack-codegen-advanced",
                                        "eval-pack-codegen-regression", "eval-pack-codegen-safety"),
                                true,
                                100
                        )
                ),
                new SkillManifest.EvidenceRequirements(
                        1,
                        5,
                        15,
                        30,
                        Set.of("trace", "episode", "evaluation")
                ),
                new SkillManifest.VersionScopeConstraints(
                        "language",
                        "programming-language",
                        ">=1.0.0",
                        true
                ),
                Map.of(
                        "language", "java,python,typescript,javascript",
                        "frameworks", "spring,express,nextjs",
                        "tags", "code,generation,development"
                )
        );
    }

    /**
     * Debugging skill manifest.
     */
    @NotNull
    public static SkillManifest debugging() {
        return new SkillManifest(
                "gaa.debugging",
                "Debugging",
                "Ability to identify and fix bugs in code",
                "development",
                "debugging",
                "1.0.0",
                List.of(
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-debug-basic",
                                "Basic Debugging Evaluation",
                                true,
                                "L2"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-debug-advanced",
                                "Advanced Debugging Evaluation",
                                true,
                                "L3"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-debug-regression",
                                "Debugging Regression Tests",
                                true,
                                "L4"
                        )
                ),
                Map.of(
                        "L1", new SkillManifest.PromotionCriteria(
                                "L1",
                                2,
                                0.5,
                                Set.of("eval-pack-debug-basic"),
                                false,
                                3
                        ),
                        "L2", new SkillManifest.PromotionCriteria(
                                "L2",
                                8,
                                0.65,
                                Set.of("eval-pack-debug-basic"),
                                false,
                                15
                        ),
                        "L3", new SkillManifest.PromotionCriteria(
                                "L3",
                                20,
                                0.75,
                                Set.of("eval-pack-debug-basic", "eval-pack-debug-advanced"),
                                true,
                                40
                        ),
                        "L4", new SkillManifest.PromotionCriteria(
                                "L4",
                                40,
                                0.85,
                                Set.of("eval-pack-debug-basic", "eval-pack-debug-advanced",
                                        "eval-pack-debug-regression"),
                                true,
                                80
                        )
                ),
                new SkillManifest.EvidenceRequirements(
                        1,
                        3,
                        10,
                        25,
                        Set.of("trace", "episode", "evaluation")
                ),
                new SkillManifest.VersionScopeConstraints(
                        "language",
                        "programming-language",
                        ">=1.0.0",
                        true
                ),
                Map.of(
                        "language", "java,python,typescript",
                        "tags", "debug,bugfix,troubleshooting"
                )
        );
    }

    /**
     * Testing skill manifest.
     */
    @NotNull
    public static SkillManifest testing() {
        return new SkillManifest(
                "gaa.testing",
                "Testing",
                "Ability to write and execute tests",
                "development",
                "testing",
                "1.0.0",
                List.of(
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-test-basic",
                                "Basic Testing Evaluation",
                                true,
                                "L2"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-test-advanced",
                                "Advanced Testing Evaluation",
                                true,
                                "L3"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-test-regression",
                                "Testing Regression Tests",
                                true,
                                "L4"
                        )
                ),
                Map.of(
                        "L1", new SkillManifest.PromotionCriteria(
                                "L1",
                                2,
                                0.5,
                                Set.of("eval-pack-test-basic"),
                                false,
                                3
                        ),
                        "L2", new SkillManifest.PromotionCriteria(
                                "L2",
                                8,
                                0.65,
                                Set.of("eval-pack-test-basic"),
                                false,
                                15
                        ),
                        "L3", new SkillManifest.PromotionCriteria(
                                "L3",
                                20,
                                0.75,
                                Set.of("eval-pack-test-basic", "eval-pack-test-advanced"),
                                true,
                                40
                        ),
                        "L4", new SkillManifest.PromotionCriteria(
                                "L4",
                                40,
                                0.85,
                                Set.of("eval-pack-test-basic", "eval-pack-test-advanced",
                                        "eval-pack-test-regression"),
                                true,
                                80
                        )
                ),
                new SkillManifest.EvidenceRequirements(
                        1,
                        3,
                        10,
                        25,
                        Set.of("trace", "episode", "evaluation")
                ),
                new SkillManifest.VersionScopeConstraints(
                        "language",
                        "programming-language",
                        ">=1.0.0",
                        true
                ),
                Map.of(
                        "language", "java,python,typescript",
                        "frameworks", "junit,pytest,jest",
                        "tags", "test,qa,quality"
                )
        );
    }

    /**
     * Documentation skill manifest.
     */
    @NotNull
    public static SkillManifest documentation() {
        return new SkillManifest(
                "gaa.documentation",
                "Documentation",
                "Ability to generate and maintain documentation",
                "development",
                "documentation",
                "1.0.0",
                List.of(
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-doc-basic",
                                "Basic Documentation Evaluation",
                                true,
                                "L2"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-doc-advanced",
                                "Advanced Documentation Evaluation",
                                true,
                                "L3"
                        )
                ),
                Map.of(
                        "L1", new SkillManifest.PromotionCriteria(
                                "L1",
                                2,
                                0.5,
                                Set.of("eval-pack-doc-basic"),
                                false,
                                3
                        ),
                        "L2", new SkillManifest.PromotionCriteria(
                                "L2",
                                6,
                                0.65,
                                Set.of("eval-pack-doc-basic"),
                                false,
                                15
                        ),
                        "L3", new SkillManifest.PromotionCriteria(
                                "L3",
                                15,
                                0.75,
                                Set.of("eval-pack-doc-basic", "eval-pack-doc-advanced"),
                                true,
                                30
                        ),
                        "L4", new SkillManifest.PromotionCriteria(
                                "L4",
                                30,
                                0.85,
                                Set.of("eval-pack-doc-basic", "eval-pack-doc-advanced"),
                                true,
                                60
                        )
                ),
                new SkillManifest.EvidenceRequirements(
                        1,
                        3,
                        8,
                        20,
                        Set.of("trace", "episode", "evaluation")
                ),
                new SkillManifest.VersionScopeConstraints(
                        "language",
                        "documentation",
                        ">=1.0.0",
                        true
                ),
                Map.of(
                        "formats", "markdown,restructuredtext,asciidoc",
                        "tags", "docs,documentation,writing"
                )
        );
    }

    /**
     * Refactoring skill manifest.
     */
    @NotNull
    public static SkillManifest refactoring() {
        return new SkillManifest(
                "gaa.refactoring",
                "Refactoring",
                "Ability to refactor code while preserving behavior",
                "development",
                "refactoring",
                "1.0.0",
                List.of(
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-refactor-basic",
                                "Basic Refactoring Evaluation",
                                true,
                                "L2"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-refactor-advanced",
                                "Advanced Refactoring Evaluation",
                                true,
                                "L3"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-refactor-regression",
                                "Refactoring Regression Tests",
                                true,
                                "L4"
                        ),
                        new SkillManifest.EvaluationPackRequirement(
                                "eval-pack-refactor-safety",
                                "Refactoring Safety Tests",
                                true,
                                "L4"
                        )
                ),
                Map.of(
                        "L1", new SkillManifest.PromotionCriteria(
                                "L1",
                                2,
                                0.5,
                                Set.of("eval-pack-refactor-basic"),
                                false,
                                3
                        ),
                        "L2", new SkillManifest.PromotionCriteria(
                                "L2",
                                8,
                                0.65,
                                Set.of("eval-pack-refactor-basic"),
                                false,
                                15
                        ),
                        "L3", new SkillManifest.PromotionCriteria(
                                "L3",
                                20,
                                0.75,
                                Set.of("eval-pack-refactor-basic", "eval-pack-refactor-advanced"),
                                true,
                                40
                        ),
                        "L4", new SkillManifest.PromotionCriteria(
                                "L4",
                                40,
                                0.85,
                                Set.of("eval-pack-refactor-basic", "eval-pack-refactor-advanced",
                                        "eval-pack-refactor-regression", "eval-pack-refactor-safety"),
                                true,
                                80
                        )
                ),
                new SkillManifest.EvidenceRequirements(
                        1,
                        3,
                        10,
                        25,
                        Set.of("trace", "episode", "evaluation")
                ),
                new SkillManifest.VersionScopeConstraints(
                        "language",
                        "programming-language",
                        ">=1.0.0",
                        true
                ),
                Map.of(
                        "language", "java,python,typescript",
                        "tags", "refactor,cleanup,optimization"
                )
        );
    }

    /**
     * Returns all canonical skill manifests.
     *
     * @return list of canonical skill manifests
     */
    @NotNull
    public static List<SkillManifest> all() {
        return List.of(
                codeGeneration(),
                debugging(),
                testing(),
                documentation(),
                refactoring()
        );
    }

    /**
     * Finds a canonical skill manifest by ID.
     *
     * @param skillId skill identifier
     * @return Optional containing the manifest if found
     */
    @NotNull
    public static java.util.Optional<SkillManifest> findById(@NotNull String skillId) {
        return all().stream()
                .filter(manifest -> manifest.skillId().equals(skillId))
                .findFirst();
    }
}
