/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.kernelvisibility;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Suggests next actions based on Kernel lifecycle state.
 *
 * <p>This service analyzes Kernel health snapshot data and provides actionable recommendations
 * for users to improve ProductUnit health, resolve gate failures, fix deployment issues,
 * and address agent governance concerns.
 *
 * <p><b>Initial Implementation (Rule-Based)</b></p>
 * <ul>
 *   <li>Simple rule-based recommendations from health snapshot data</li>
 *   <li>Gate failure explanations with suggested fixes</li>
 *   <li>Deployment health recommendations</li>
 * </ul>
 *
 * <p><b>Future Implementation (AI-Enhanced)</b></p>
 * <ul>
 *   <li>AI-powered recommendations based on historical patterns</li>
 *   <li>Context-aware explanations</li>
 *   <li>Predictive action suggestions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Suggests next actions based on Kernel lifecycle state
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KernelActionRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(KernelActionRecommendationService.class);

    private final KernelHealthSnapshotService healthService;

    /**
     * Constructs a new KernelActionRecommendationService with default health service.
     */
    public KernelActionRecommendationService() {
        this(new KernelHealthSnapshotService());
    }

    /**
     * Constructs a new KernelActionRecommendationService with custom health service.
     *
     * @param healthService the health service to use for reading Kernel data
     */
    public KernelActionRecommendationService(@NotNull KernelHealthSnapshotService healthService) {
        this.healthService = healthService;
    }

    /**
     * Recommends actions for a specific ProductUnit based on its current health state.
     *
     * @param productUnitId the ProductUnit ID to recommend actions for
     * @return promise resolving to a list of recommended actions
     */
    public Promise<List<ActionRecommendation>> recommendActions(@NotNull String productUnitId) {
        return healthService.getProductUnitHealth(productUnitId)
                .map(this::generateRecommendations);
    }

    /**
     * Explains a specific gate failure and suggests remediation steps.
     *
     * @param productUnitId the ProductUnit ID
     * @param gateId the gate ID to explain
     * @return promise resolving to the gate failure explanation
     */
    public Promise<GateFailureExplanation> explainGateFailure(@NotNull String productUnitId, @NotNull String gateId) {
        return healthService.getProductUnitHealth(productUnitId)
                .map(healthView -> generateGateExplanation(healthView, gateId));
    }

    private List<ActionRecommendation> generateRecommendations(KernelHealthSnapshotService.ProductUnitHealthView healthView) {
        List<ActionRecommendation> recommendations = new ArrayList<>();

        // Overall health recommendations
        switch (healthView.overallStatus()) {
            case "failed":
                recommendations.add(new ActionRecommendation(
                        "critical",
                        "Lifecycle execution failed",
                        "Review gate failures and fix blocking issues before retrying lifecycle",
                        "review_gates"
                ));
                break;
            case "degraded":
                recommendations.add(new ActionRecommendation(
                        "warning",
                        "ProductUnit health is degraded",
                        "Check deployment status and artifact health to identify degradation source",
                        "check_deployment"
                ));
                break;
            case "unknown":
                recommendations.add(new ActionRecommendation(
                        "info",
                        "No health data available",
                        "Ensure Kernel lifecycle has been executed at least once",
                        "run_lifecycle"
                ));
                break;
            default:
                // Healthy - no critical actions needed
                break;
        }

        // Gate failure recommendations
        if (healthView.gateFailureCount() > 0) {
            recommendations.add(new ActionRecommendation(
                    "critical",
                    String.format("%d gate(s) failed", healthView.gateFailureCount()),
                    "Review failed gates and address blocking criteria",
                    "review_gates"
            ));
        }

        // Deployment recommendations
        switch (healthView.deploymentStatus()) {
            case "failed":
                recommendations.add(new ActionRecommendation(
                        "critical",
                        "Deployment failed",
                        "Check deployment logs and fix configuration or environment issues",
                        "fix_deployment"
                ));
                break;
            case "not_deployed":
                recommendations.add(new ActionRecommendation(
                        "info",
                        "ProductUnit not deployed",
                        "Run lifecycle deploy phase to deploy to target environment",
                    "run_deploy"
                ));
                break;
            default:
                break;
        }

        return recommendations;
    }

    private GateFailureExplanation generateGateExplanation(KernelHealthSnapshotService.ProductUnitHealthView healthView, String gateId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> gates = (Map<String, Object>) healthView.lifecycleResult().get("gates");

        if (gates == null) {
            return new GateFailureExplanation(gateId, "Gate data not available", List.of(), "Check if lifecycle has been executed");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> gateList = (List<Map<String, Object>>) gates.get("gates");

        if (gateList == null) {
            return new GateFailureExplanation(gateId, "Gate list not available", List.of(), "Check if lifecycle has been executed");
        }

        for (Map<String, Object> gate : gateList) {
            if (gateId.equals(gate.get("id"))) {
                String status = (String) gate.get("status");
                String reason = (String) gate.get("reason");
                
                @SuppressWarnings("unchecked")
                List<String> criteria = (List<String>) gate.get("criteria");

                List<String> suggestedActions = new ArrayList<>();
                if ("failed".equals(status)) {
                    suggestedActions.add("Review gate criteria and ensure all requirements are met");
                    suggestedActions.add("Check if required artifacts are present");
                    suggestedActions.add("Verify configuration settings match gate requirements");
                }

                return new GateFailureExplanation(gateId, status, criteria, reason);
            }
        }

        return new GateFailureExplanation(gateId, "Gate not found", List.of(), "Verify gate ID is correct");
    }

    /**
     * Represents a recommended action for the user.
     */
    public static final class ActionRecommendation {
        private final String severity;
        private final String title;
        private final String description;
        private final String actionType;

        public ActionRecommendation(String severity, String title, String description, String actionType) {
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.actionType = actionType;
        }

        public String severity() { return severity; }
        public String title() { return title; }
        public String description() { return description; }
        public String actionType() { return actionType; }
    }

    /**
     * Explanation of a gate failure with suggested remediation.
     */
    public static final class GateFailureExplanation {
        private final String gateId;
        private final String status;
        private final List<String> criteria;
        private final String reason;
        private final List<String> suggestedActions;

        public GateFailureExplanation(String gateId, String status, List<String> criteria, String reason) {
            this.gateId = gateId;
            this.status = status;
            this.criteria = criteria;
            this.reason = reason;
            this.suggestedActions = generateSuggestedActions(status);
        }

        private static List<String> generateSuggestedActions(String status) {
            List<String> actions = new ArrayList<>();
            if ("failed".equals(status)) {
                actions.add("Review gate criteria and ensure all requirements are met");
                actions.add("Check if required artifacts are present");
                actions.add("Verify configuration settings match gate requirements");
            }
            return actions;
        }

        public String gateId()
        {
            return gateId;
        }

        public String status()
        {
            return status;
        }

        public List<String> criteria()
        {
            return criteria;
        }

        public String reason()
        {
            return reason;
        }

        public List<String> suggestedActions()
        {
            return suggestedActions;
        }
    }
}
