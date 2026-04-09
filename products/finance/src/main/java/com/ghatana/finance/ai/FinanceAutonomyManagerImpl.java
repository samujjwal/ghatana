/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AutonomyManager;
import com.ghatana.kernel.ai.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Component for FinanceAutonomyManagerImpl
 *
 * @doc.type class
 * @doc.purpose Component for FinanceAutonomyManagerImpl
 * @doc.layer product
 * @doc.pattern Manager
 */
public class FinanceAutonomyManagerImpl implements AutonomyManager {

    private static final Logger logger = LoggerFactory.getLogger(FinanceAutonomyManagerImpl.class);
    private final Map<String, AutonomyLevel> autonomyLevels = new ConcurrentHashMap<>();
    private final Map<String, List<AutonomousDecision>> decisions = new ConcurrentHashMap<>();

    @Override
    public void configureAutonomyLevel(String agentId, AutonomyLevel level) {
        autonomyLevels.put(agentId, level);
    }

    @Override
    public boolean requiresHumanReview(AgentOrchestrator.AgentRequest request, AgentOrchestrator.KernelAgent agent) {
        AutonomyLevel level = autonomyLevels.getOrDefault(agent.getAgentId(), AutonomyLevel.MEDIUM);

        // None and Low autonomy always require review
        if (level == AutonomyLevel.NONE || level == AutonomyLevel.LOW) {
            return true;
        }

        // For high-value transactions, always require review
        if (request.getContext() != null && request.getContext().containsKey("amount")) {
            double amount = ((Number) request.getContext().get("amount")).doubleValue();
            if (amount > 100000) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void recordAutonomousDecision(AutonomousDecision decision) {
        logger.info("Recording autonomous decision: agent={}, requiresReview={}",
            decision.getAgentId(), decision.requiresReview());
        decisions.computeIfAbsent(decision.getAgentId(), k -> new ArrayList<>()).add(decision);
    }

    @Override
    public List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window) {
        List<AutonomousDecision> allDecisions = decisions.getOrDefault(agentId, new ArrayList<>());
        return allDecisions.stream()
            .filter(d -> d.getTimestamp() >= window.getStartTime() && d.getTimestamp() <= window.getEndTime())
            .toList();
    }

    @Override
    public AutonomyLevel getAutonomyLevel(String agentId) {
        return autonomyLevels.getOrDefault(agentId, AutonomyLevel.MEDIUM);
    }

    @Override
    public void approveDecision(String decisionId, String approver) {
        decisions.values().stream()
            .flatMap(List::stream)
            .filter(d -> d.getDecisionId().equals(decisionId))
            .forEach(d -> d.setStatus(DecisionStatus.APPROVED));
    }

    @Override
    public void rejectDecision(String decisionId, String rejector, String reason) {
        decisions.values().stream()
            .flatMap(List::stream)
            .filter(d -> d.getDecisionId().equals(decisionId))
            .forEach(d -> {
                d.setStatus(DecisionStatus.REJECTED);
                d.getMetadata().put("rejection_reason", reason);
            });
    }
}
