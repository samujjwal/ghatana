package com.ghatana.virtualorg.workflows;

import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.agent.Agent;
import com.ghatana.virtualorg.model.Release;
import io.activej.promise.Promise;

import java.util.*;

/**
 * Deployment pipeline workflow from feature completion to production.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates end-to-end deployment process with quality gates, approval hierarchy,
 * and automated/manual validation steps.
 *
 * <p><b>Workflow Steps</b><br>
 * 1. Engineer marks feature complete (code merged, tests passing, docs updated)
 * 2. QAEngineer executes regression test suite (smoke, E2E validation)
 * 3. QALead approves quality gate (coverage, defects, performance benchmarks)
 * 4. DevOpsEngineer deploys to staging (automated deployment, smoke tests)
 * 5. ProductManager validates business requirements (acceptance criteria, demo)
 * 6. DevOpsLead approves production deployment (change window, rollback plan)
 * 7. DevOpsEngineer deploys to production (canary rollout, health monitoring)
 *
 * <p><b>Quality Gates</b><br>
 * - QA gate: >80% coverage, no P0/P1 defects, performance benchmarks met
 * - Staging gate: Smoke tests pass, health checks green, basic functionality verified
 * - Production gate: DevOpsLead approval, change window compliance, rollback plan ready
 *
 * <p><b>Rollback Triggers</b><br>
 * - Health checks fail post-deployment
 * - Error rate >5% in first 10 minutes
 * - Critical business flows broken
 * - Manual rollback initiated by DevOpsLead
 *
 * @doc.type class
 * @doc.purpose End-to-end deployment pipeline workflow
 * @doc.layer product
 * @doc.pattern Workflow
 */
public class DeploymentPipelineWorkflow {
    
    private final WorkflowEngine engine;
    private final Map<AgentRoleProto, Agent> agents;
    
    /**
     * Creates deployment pipeline workflow with required agents.
     *
     * @param engine Workflow orchestration engine
     * @param agents Map of agent roles (requires: AGENT_ROLE_ENGINEER, AGENT_ROLE_QA_ENGINEER, AGENT_ROLE_QA_LEAD,
     *               AGENT_ROLE_DEVOPS_ENGINEER, AGENT_ROLE_PRODUCT_MANAGER, AGENT_ROLE_DEVOPS_LEAD)
     */
    public DeploymentPipelineWorkflow(WorkflowEngine engine, Map<AgentRoleProto, Agent> agents) {
        this.engine = engine;
        this.agents = agents;
        validateRequiredAgents();
    }
    
    private void validateRequiredAgents() {
        List<AgentRoleProto> required = List.of(
            AgentRoleProto.AGENT_ROLE_ENGINEER,
            AgentRoleProto.AGENT_ROLE_QA_ENGINEER,
            AgentRoleProto.AGENT_ROLE_QA_LEAD,
            AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER,
            AgentRoleProto.AGENT_ROLE_PRODUCT_MANAGER,
            AgentRoleProto.AGENT_ROLE_DEVOPS_LEAD
        );
        
        for (AgentRoleProto role : required) {
            if (!agents.containsKey(role)) {
                throw new IllegalStateException("Missing required agent: " + role);
            }
        }
    }
    
    /**
     * Executes deployment pipeline for a release.
     *
     * @param release Release to deploy
     * @return Promise of WorkflowResult with deployment outcome
     */
    public Promise<WorkflowResult> execute(Release release) {
        // Build release metadata
        Map<String, String> releaseMetadata = buildReleaseMetadata(release);
        
        // Build workflow context
        WorkflowContext.Builder contextBuilder = WorkflowContext.builder()
            .withCorrelationId("deployment-" + release.getVersion())
            .withUserId(release.getReleaseManager())
            .withMetadata("workflowType", "DEPLOYMENT_PIPELINE");
        
        // Add all release metadata
        releaseMetadata.forEach(contextBuilder::withMetadata);
        
        WorkflowContext context = contextBuilder.build();
        
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            // Step 1: Feature completion verification
            .addStep(WorkflowStep.builder()
                .stepId("feature_complete")
                .stepName("Verify Feature Completion")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_ENGINEER))
                .taskDescription(buildFeatureCompletionTask(release))
                .build())
            
            // Step 2: QA regression test suite
            .addStep(WorkflowStep.builder()
                .stepId("qa_regression")
                .stepName("Execute Regression Test Suite")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_QA_ENGINEER))
                .taskDescription(buildRegressionTestTask(release))
                .dependsOn(List.of("feature_complete"))
                .build())
            
            // Step 3: QA Lead quality gate approval
            .addStep(WorkflowStep.builder()
                .stepId("qa_gate")
                .stepName("Quality Gate Approval")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_QA_LEAD))
                .taskDescription(buildQualityGateTask(release))
                .dependsOn(List.of("qa_regression"))
                .build())
            
            // Step 4: Deploy to staging
            .addStep(WorkflowStep.builder()
                .stepId("deploy_staging")
                .stepName("Deploy to Staging Environment")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER))
                .taskDescription(buildStagingDeploymentTask(release))
                .dependsOn(List.of("qa_gate"))
                .build())
            
            // Step 5: Product Manager business validation (parallel with staging stabilization)
            .addStep(WorkflowStep.builder()
                .stepId("business_validation")
                .stepName("Business Requirements Validation")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_PRODUCT_MANAGER))
                .taskDescription(buildBusinessValidationTask(release))
                .dependsOn(List.of("deploy_staging"))
                .build())
            
            // Step 6: DevOps Lead production approval
            .addStep(WorkflowStep.builder()
                .stepId("production_approval")
                .stepName("Production Deployment Approval")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_DEVOPS_LEAD))
                .taskDescription(buildProductionApprovalTask(release))
                .dependsOn(List.of("business_validation"))
                .build())
            
            // Step 7: Production deployment
            .addStep(WorkflowStep.builder()
                .stepId("deploy_production")
                .stepName("Deploy to Production Environment")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER))
                .taskDescription(buildProductionDeploymentTask(release))
                .dependsOn(List.of("production_approval"))
                .build())
            
            // Step 8: Post-deployment monitoring
            .addStep(WorkflowStep.builder()
                .stepId("post_deploy_monitoring")
                .stepName("Post-Deployment Health Monitoring")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER))
                .taskDescription(buildMonitoringTask(release))
                .dependsOn(List.of("deploy_production"))
                .build())
            
            .build();
        
        return engine.executeWorkflow(workflow, context);
    }
    
    private Map<String, String> buildReleaseMetadata(Release release) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", release.getVersion());
        metadata.put("release_type", release.getType());
        metadata.put("features_count", String.valueOf(release.getFeatures().size()));
        metadata.put("bug_fixes_count", String.valueOf(release.getBugFixes().size()));
        metadata.put("deployment_date", release.getPlannedDeploymentDate().toString());
        metadata.put("change_window", release.getChangeWindow());
        metadata.put("risk_level", release.getRiskLevel());
        return metadata;
    }
    
    private String buildFeatureCompletionTask(Release release) {
        return String.format("""
            TASK: Verify feature completion readiness for deployment
            
            RELEASE: %s (%s)
            FEATURES: %d features, %d bug fixes
            
            COMPLETION CHECKLIST:
            ✓ All feature PRs merged to main branch
            ✓ All tests passing (unit, integration, E2E)
            ✓ Code review approvals obtained
            ✓ Documentation updated (API docs, user guides, changelog)
            ✓ Database migrations tested and reviewed
            ✓ Feature flags configured correctly
            ✓ Rollback plan documented
            ✓ No known P0/P1 defects
            
            VERIFICATION:
            - Confirm all feature tickets closed in Jira
            - Verify CI/CD pipeline green for release branch
            - Check no uncommitted code or pending reviews
            - Validate release notes complete
            
            DELIVERABLES:
            - Completion status (COMPLETE/INCOMPLETE)
            - Readiness assessment
            - Outstanding issues (if any)
            - Recommendation to proceed or delay
            """,
            release.getVersion(),
            release.getType(),
            release.getFeatures().size(),
            release.getBugFixes().size()
        );
    }
    
    private String buildRegressionTestTask(Release release) {
        return String.format("""
            TASK: Execute regression test suite for release
            
            RELEASE: %s
            SCOPE: Full regression (smoke + E2E + performance)
            
            TEST SUITES:
            1. Smoke Tests (~50 tests, 5 min)
               - Critical paths (login, payment, core features)
               - API health checks
               - Database connectivity
            
            2. Regression Tests (~500 tests, 30 min)
               - All existing functionality
               - Integration points
               - Edge cases and error scenarios
            
            3. E2E Tests (~100 tests, 45 min)
               - User workflows end-to-end
               - Cross-service integration
               - Browser compatibility (if web app)
            
            4. Performance Tests (15 min)
               - Load tests (baseline throughput)
               - Latency benchmarks (p95 < 300ms)
               - Resource usage (memory, CPU)
            
            PASS CRITERIA:
            - 100%% smoke tests pass
            - >98%% regression tests pass (no P0/P1 failures)
            - >95%% E2E tests pass
            - Performance within 10%% of baseline
            
            DELIVERABLES:
            - Test execution report
            - Failed tests analysis (root cause, severity)
            - Performance benchmark comparison
            - Recommendation (PASS/FAIL quality gate)
            """,
            release.getVersion()
        );
    }
    
    private String buildQualityGateTask(Release release) {
        return String.format("""
            TASK: Quality gate approval decision
            
            RELEASE: %s
            RISK LEVEL: %s
            
            QUALITY CRITERIA:
            1. Test Results:
               - Test coverage >= 80%%
               - No P0 defects
               - No P1 defects (or all mitigated)
               - Regression test pass rate >= 98%%
            
            2. Code Quality:
               - No critical code smells
               - Technical debt acceptable
               - Code review completion 100%%
            
            3. Performance:
               - Performance benchmarks met
               - No regressions >10%%
               - Resource usage within limits
            
            4. Documentation:
               - Release notes complete
               - API documentation updated
               - Runbook ready for operations
            
            5. Risk Assessment:
               - Rollback plan validated
               - Database migrations reversible
               - Feature flags configured
               - Monitoring alerts configured
            
            DECISION:
            - APPROVE: Quality gate passed, proceed to staging
            - BLOCK: Critical issues, must fix before deployment
            - CONDITIONAL_APPROVE: Minor issues, can proceed with monitoring
            
            DELIVERABLES:
            - Gate decision with rationale
            - Outstanding issues to monitor
            - Recommendation for deployment timing
            """,
            release.getVersion(),
            release.getRiskLevel()
        );
    }
    
    private String buildStagingDeploymentTask(Release release) {
        return String.format("""
            TASK: Deploy release to staging environment
            
            RELEASE: %s
            ENVIRONMENT: Staging
            DEPLOYMENT METHOD: Automated (CI/CD pipeline)
            
            PRE-DEPLOYMENT:
            1. Backup current staging database
            2. Verify staging environment health
            3. Clear cache and temporary data
            4. Notify team of deployment start
            
            DEPLOYMENT STEPS:
            1. Deploy database migrations
            2. Deploy application containers (rolling update)
            3. Update configuration and secrets
            4. Warm up cache
            5. Run smoke tests
            
            POST-DEPLOYMENT:
            1. Health checks (readiness, liveness)
            2. Smoke test execution
            3. Basic functionality verification
            4. Logs review (no errors)
            5. Metrics verification (latency, throughput)
            
            SMOKE TESTS:
            - API health endpoint returns 200
            - Database connectivity confirmed
            - Cache operations working
            - Critical user flows functional
            - External integrations responding
            
            ROLLBACK PLAN:
            - If smoke tests fail → automatic rollback
            - Rollback command: kubectl rollout undo deployment/api
            - ETA to rollback: 2 minutes
            
            DELIVERABLES:
            - Deployment status (SUCCESS/FAILURE)
            - Smoke test results
            - Health check status
            - Recommendation to proceed to production
            """,
            release.getVersion()
        );
    }
    
    private String buildBusinessValidationTask(Release release) {
        return String.format("""
            TASK: Validate business requirements in staging
            
            RELEASE: %s
            FEATURES: %d new features, %d bug fixes
            
            VALIDATION APPROACH:
            1. Acceptance Criteria Verification
               - Review each feature against AC
               - Validate user stories completed
               - Confirm edge cases handled
            
            2. Stakeholder Demo
               - Demo key features to stakeholders
               - Gather feedback and concerns
               - Document approval or change requests
            
            3. Business Metrics Validation
               - Feature flags working correctly
               - Analytics tracking configured
               - Business KPIs measurable
            
            4. User Experience Review
               - UX flows intuitive
               - Performance acceptable
               - No regressions in existing features
            
            ACCEPTANCE CRITERIA:
            - All feature ACs met
            - Stakeholders approve for production
            - No critical UX issues
            - Business metrics ready
            
            DECISION:
            - APPROVE: Business requirements met, ready for production
            - REQUEST_CHANGES: Issues found, need fixes before production
            - DEFER: Not ready, reschedule deployment
            
            DELIVERABLES:
            - Validation results per feature
            - Stakeholder feedback summary
            - Approval decision with rationale
            """,
            release.getVersion(),
            release.getFeatures().size(),
            release.getBugFixes().size()
        );
    }
    
    private String buildProductionApprovalTask(Release release) {
        return String.format("""
            TASK: Production deployment approval decision
            
            RELEASE: %s
            CHANGE WINDOW: %s
            RISK LEVEL: %s
            
            APPROVAL CRITERIA:
            1. Quality Gates Passed
               - QA gate approved
               - Staging deployment successful
               - Business validation approved
            
            2. Operational Readiness
               - Change request CR-2024-XXX approved
               - Deployment runbook reviewed
               - Rollback plan tested
               - On-call team notified
            
            3. Timing and Coordination
               - Change window compliance
               - No conflicting deployments
               - Team availability for support
               - Customer communication sent
            
            4. Risk Assessment
               - Risk level acceptable: %s
               - Mitigation strategies in place
               - Monitoring and alerts configured
               - Incident response team ready
            
            PRODUCTION DEPLOYMENT PLAN:
            - Deployment method: Canary rollout (10%% → 50%% → 100%%)
            - Duration: ~30 minutes
            - Monitoring period: 2 hours post-deployment
            - Rollback SLA: <5 minutes if critical issues
            
            DECISION:
            - APPROVE: Proceed with production deployment
            - DEFER: Delay to next change window (reason required)
            - ESCALATE: Requires CTO approval (high risk)
            
            DELIVERABLES:
            - Approval decision with rationale
            - Deployment timing recommendation
            - Special monitoring requirements
            - Escalation plan if issues arise
            """,
            release.getVersion(),
            release.getChangeWindow(),
            release.getRiskLevel(),
            release.getRiskLevel()
        );
    }
    
    private String buildProductionDeploymentTask(Release release) {
        return String.format("""
            TASK: Deploy release to production environment
            
            RELEASE: %s
            DEPLOYMENT: Canary rollout (progressive)
            CHANGE WINDOW: %s
            
            DEPLOYMENT PHASES:
            
            PHASE 1: Canary 10%% (10 minutes)
            - Deploy to 10%% of production pods
            - Monitor error rates, latency, throughput
            - Validate critical flows working
            - ABORT if error rate >1%% or p95 latency >500ms
            
            PHASE 2: Canary 50%% (10 minutes)
            - Expand to 50%% of pods
            - Continue monitoring all metrics
            - Validate load distribution
            - ABORT if any SLO breach
            
            PHASE 3: Full rollout 100%% (10 minutes)
            - Complete rollout to all pods
            - Final health checks
            - Verify all pods healthy
            - Enable full traffic
            
            HEALTH CHECKS (per phase):
            ✓ HTTP 200 from /health endpoint
            ✓ Database connectivity confirmed
            ✓ Cache hit rate >80%%
            ✓ Error rate <0.5%%
            ✓ API latency p95 <300ms
            ✓ No pod crashes or restarts
            
            MONITORING DASHBOARD:
            - Real-time error rates
            - Request latency (p50, p95, p99)
            - Throughput (requests/sec)
            - Resource usage (CPU, memory)
            - Database connection pool
            
            ROLLBACK TRIGGERS:
            - Error rate >5%% for 5 minutes
            - Critical business flow broken
            - Database errors spiking
            - Manual rollback by DevOps Lead
            
            ROLLBACK PROCEDURE:
            1. kubectl rollout undo deployment/api
            2. Verify rollback successful (<5 min)
            3. Notify team and stakeholders
            4. Create incident report
            
            DELIVERABLES:
            - Deployment status per phase
            - Health check results
            - Metrics comparison (pre/post)
            - Incident report (if rollback executed)
            """,
            release.getVersion(),
            release.getChangeWindow()
        );
    }
    
    private String buildMonitoringTask(Release release) {
        return String.format("""
            TASK: Post-deployment health monitoring
            
            RELEASE: %s
            MONITORING PERIOD: 2 hours (intensive), 24 hours (standard)
            
            INTENSIVE MONITORING (first 2 hours):
            - Every 5 minutes: Error rates, latency, throughput
            - Alert on any anomaly >10%% deviation
            - Manual validation of critical flows
            - On-call engineer standing by
            
            METRICS TO MONITOR:
            1. Error Rates
               - Overall error rate <0.5%%
               - 4xx errors (client) <2%%
               - 5xx errors (server) <0.1%%
            
            2. Latency
               - API p95 latency <300ms
               - Database query p95 <50ms
               - Cache lookup p95 <5ms
            
            3. Throughput
               - Requests/sec within ±20%% of baseline
               - Database queries/sec stable
               - No traffic drops
            
            4. Resources
               - CPU usage <70%%
               - Memory usage <80%%
               - Database connections <80%% pool
            
            5. Business Metrics
               - Critical flows (login, payment) working
               - Conversion rates within ±10%%
               - User sessions normal
            
            ALERT THRESHOLDS:
            - P0: Error rate >5%%, latency >1000ms, availability <99%%
            - P1: Error rate >2%%, latency >500ms, resource >90%%
            - P2: Error rate >1%%, latency >400ms, anomaly detected
            
            SUCCESS CRITERIA:
            - 2 hours with no P0/P1 alerts
            - All metrics within normal ranges
            - No customer complaints or incidents
            - Business flows validated
            
            DELIVERABLES:
            - Monitoring report (2 hour, 24 hour)
            - Metrics comparison (pre/post deployment)
            - Issues identified and resolved
            - Deployment success confirmation
            """,
            release.getVersion()
        );
    }
}
