/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.agent.spi.AgentLogicProvider;
import com.ghatana.kernel.ai.*;
import io.activej.inject.module.ModuleBuilder;
import io.activej.inject.module.Module;

import javax.sql.DataSource;

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
            .bind(ModelRepository.class).to(ModelRepository::new)
            .bind(AlertService.class).to(AlertService::new)
            .bind(ModelGovernanceService.class).to(
                FinanceModelGovernanceImpl::new,
                ModelApprovalRepository.class,
                ModelPerformanceRepository.class,
                ModelRepository.class,
                AlertService.class
            )
            .bind(AgentOrchestrator.class).to(FinanceAgentOrchestratorImpl::new)
            .bind(AutonomyManager.class).to(FinanceAutonomyManagerImpl::new)
            .bind(AIEvaluationFramework.class).to(FinanceAIEvaluationImpl::new)
            .bind(FraudFeatureExtractor.class).to(FraudFeatureExtractor::new)
            .bind(FraudModelInferenceService.class).to(
                DefaultFraudModelInferenceService::new,
                ModelRepository.class
            )
            .bind(FinanceFraudDetectionKernelAgent.class).to(
                FinanceFraudDetectionKernelAgent::new,
                ModelGovernanceService.class,
                FraudFeatureExtractor.class,
                FraudModelInferenceService.class
            )
            // Agent logic provider wired with real services
            .bind(AgentLogicProvider.class).to(
                FinanceAgentLogicProvider::new,
                AlertService.class,
                ModelRepository.class
            )
            .build();
    }

    public static Module create(DataSource dataSource) {
        return ModuleBuilder.create()
            .bind(ModelApprovalRepository.class).to(() -> new ModelApprovalRepository(dataSource))
            .bind(ModelPerformanceRepository.class).to(() -> new ModelPerformanceRepository(dataSource))
            .bind(ModelRepository.class).to(() -> new ModelRepository(dataSource))
            .bind(AlertService.class).to(AlertService::new)
            .bind(ModelGovernanceService.class).to(
                FinanceModelGovernanceImpl::new,
                ModelApprovalRepository.class,
                ModelPerformanceRepository.class,
                ModelRepository.class,
                AlertService.class
            )
            .bind(AgentOrchestrator.class).to(FinanceAgentOrchestratorImpl::new)
            .bind(AutonomyManager.class).to(FinanceAutonomyManagerImpl::new)
            .bind(AIEvaluationFramework.class).to(FinanceAIEvaluationImpl::new)
            .bind(FraudFeatureExtractor.class).to(FraudFeatureExtractor::new)
            .bind(FraudModelInferenceService.class).to(
                DefaultFraudModelInferenceService::new,
                ModelRepository.class
            )
            .bind(FinanceFraudDetectionKernelAgent.class).to(
                FinanceFraudDetectionKernelAgent::new,
                ModelGovernanceService.class,
                FraudFeatureExtractor.class,
                FraudModelInferenceService.class
            )
            .bind(AgentLogicProvider.class).to(
                FinanceAgentLogicProvider::new,
                AlertService.class,
                ModelRepository.class
            )
            .build();
    }
}
