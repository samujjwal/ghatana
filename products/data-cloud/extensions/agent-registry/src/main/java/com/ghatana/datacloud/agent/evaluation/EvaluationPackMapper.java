/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.evaluation;

import com.ghatana.agent.evaluation.pack.EvaluationCase;
import com.ghatana.agent.evaluation.pack.EvaluationPack;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.VersionConstraint;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper for converting between {@link EvaluationPack} and data maps for EntityRepository persistence.
 *
 * @doc.type class
 * @doc.purpose Maps EvaluationPack to/from data maps
 * @doc.layer data-cloud
 * @doc.pattern Mapper
 */
public final class EvaluationPackMapper {

    private EvaluationPackMapper() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts an EvaluationPack to a data map for persistence.
     *
     * @param pack the evaluation pack
     * @return data map representation
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull EvaluationPack pack) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("evaluationPackId", pack.evaluationPackId());
        map.put("tenantId", pack.tenantId());
        map.put("skillId", pack.skillId());
        map.put("version", pack.version());
        map.put("versionScope", versionScopeToMap(pack.versionScope()));
        map.put("cases", casesToList(pack.cases()));
        map.put("requiredEvidenceTypes", new ArrayList<>(pack.requiredEvidenceTypes()));
        map.put("minPassRate", pack.minPassRate());
        map.put("requiresRegression", pack.requiresRegression());
        map.put("requiresSafety", pack.requiresSafety());
        return map;
    }

    /**
     * Converts a data map to an EvaluationPack.
     *
     * @param data the data map
     * @return evaluation pack
     */
    @NotNull
    public static EvaluationPack fromDataMap(@NotNull Map<String, Object> data) {
        String evaluationPackId = (String) data.get("evaluationPackId");
        String tenantId = (String) data.get("tenantId");
        String skillId = (String) data.get("skillId");
        String version = (String) data.get("version");
        VersionScope versionScope = mapToVersionScope((Map<?, ?>) data.get("versionScope"));
        
        @SuppressWarnings("unchecked")
        List<Map<?, ?>> caseMaps = (List<Map<?, ?>>) data.get("cases");
        List<EvaluationCase> cases = caseMaps.stream()
                .map(EvaluationPackMapper::mapToCase)
                .collect(Collectors.toList());
        
        @SuppressWarnings("unchecked")
        List<String> requiredEvidenceTypes = (List<String>) data.get("requiredEvidenceTypes");
        
        Double minPassRateObj = (Double) data.get("minPassRate");
        double minPassRate = minPassRateObj != null ? minPassRateObj : 0.8;
        
        Boolean requiresRegressionObj = (Boolean) data.get("requiresRegression");
        boolean requiresRegression = requiresRegressionObj != null ? requiresRegressionObj : false;
        
        Boolean requiresSafetyObj = (Boolean) data.get("requiresSafety");
        boolean requiresSafety = requiresSafetyObj != null ? requiresSafetyObj : false;

        return new EvaluationPack(
                evaluationPackId,
                tenantId,
                skillId,
                version,
                versionScope,
                cases,
                requiredEvidenceTypes,
                minPassRate,
                requiresRegression,
                requiresSafety
        );
    }

    @NotNull
    private static Map<String, Object> versionScopeToMap(@NotNull VersionScope scope) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("active", constraintsToList(scope.active()));
        map.put("maintenance", constraintsToList(scope.maintenance()));
        map.put("obsolete", constraintsToList(scope.obsolete()));
        return map;
    }

    @NotNull
    private static VersionScope mapToVersionScope(@NotNull Map<?, ?> map) {
        return new VersionScope(
                listToConstraints((List<?>) map.get("active")),
                listToConstraints((List<?>) map.get("maintenance")),
                listToConstraints((List<?>) map.get("obsolete"))
        );
    }

    @NotNull
    private static List<Map<String, Object>> constraintsToList(@NotNull List<VersionConstraint> constraints) {
        return constraints.stream()
                .map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("kind", c.kind());
                    cm.put("name", c.name());
                    cm.put("range", c.range());
                    cm.put("ecosystem", c.ecosystem());
                    return cm;
                })
                .collect(Collectors.toList());
    }

    @NotNull
    private static List<VersionConstraint> listToConstraints(@NotNull List<?> list) {
        List<VersionConstraint> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object kind = m.get("kind");
                Object name = m.get("name");
                Object range = m.get("range");
                Object ecosystem = m.get("ecosystem");
                if (kind instanceof String k && name instanceof String n
                        && range instanceof String r && ecosystem instanceof String e) {
                    result.add(new VersionConstraint(k, n, r, e));
                }
            }
        }
        return result;
    }

    @NotNull
    private static List<Map<String, Object>> casesToList(@NotNull List<EvaluationCase> cases) {
        return cases.stream()
                .map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("caseId", c.caseId());
                    cm.put("name", c.name());
                    cm.put("description", c.description());
                    cm.put("input", c.input());
                    cm.put("expectedOutput", c.expectedOutput());
                    cm.put("category", c.category().name());
                    cm.put("context", c.context());
                    cm.put("weight", c.weight());
                    cm.put("required", c.required());
                    cm.put("rationale", c.rationale());
                    return cm;
                })
                .collect(Collectors.toList());
    }

    @NotNull
    private static EvaluationCase mapToCase(@NotNull Map<?, ?> map) {
        String caseId = (String) map.get("caseId");
        String name = (String) map.get("name");
        String description = (String) map.get("description");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) map.get("input");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> expectedOutput = (Map<String, Object>) map.get("expectedOutput");
        
        String categoryStr = (String) map.get("category");
        com.ghatana.agent.evaluation.EvaluationType category = 
                com.ghatana.agent.evaluation.EvaluationType.valueOf(categoryStr);
        
        Integer weightObj = (Integer) map.get("weight");
        int weight = weightObj != null ? weightObj : 1;
        
        Boolean requiredObj = (Boolean) map.get("required");
        boolean required = requiredObj != null ? requiredObj : false;

        String inputStr = input != null ? input.toString() : "";
        String expectedOutputStr = expectedOutput != null ? expectedOutput.toString() : "";
        
        Object contextObj = map.get("context");
        @SuppressWarnings("unchecked")
        Map<String, String> context = contextObj != null ? (Map<String, String>) contextObj : Map.of();
        
        String rationale = (String) map.get("rationale");

        return new EvaluationCase(
                caseId,
                name,
                description,
                category,
                inputStr,
                expectedOutputStr,
                context,
                weight,
                required,
                rationale
        );
    }
}
