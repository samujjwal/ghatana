/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.agent.spi.AgentLogicProvider;
import com.ghatana.kernel.ai.*;
import io.activej.inject.module.ModuleBuilder;
import io.activej.inject.module.Module;

/**
 * Component for FinanceAIModule
 *
 * @doc.type class
 * @doc.purpose Component for FinanceAIModule
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FinanceAIModule {

    private FinanceAIModule() {}

    public static Module create() {
        return ModuleBuilder.create()
            // Core AI services
            .bind(ModelApprovalRepository.class).to(ModelApprovalRepository::new)
            .bind(ModelPerformanceRepository.class).to(ModelPerformanceRepository::new)
            .bind(AlertService.class).to(AlertService::new)
            .bind(ModelGovernanceService.class).to(
                FinanceModelGovernanceImpl::new,
                ModelApprovalRepository.class,
                ModelPerformanceRepository.class,
                AlertService.class
            )
            .bind(AgentOrchestrator.class).to(FinanceAgentOrchestratorImpl::new)
            .bind(AutonomyManager.class).to(FinanceAutonomyManagerImpl::new)
            .bind(AIEvaluationFramework.class).to(FinanceAIEvaluationImpl::new)
            // Agent logic provider wired with real services
            .bind(AgentLogicProvider.class).to(
                FinanceAgentLogicProvider::new,
                AlertService.class
            )
            .build();
    }
}
