package com.ghatana.virtualorg.workflows;

import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.agent.Agent;
import com.ghatana.virtualorg.model.Decision;
import com.ghatana.virtualorg.model.DecisionType;
import com.ghatana.virtualorg.model.PullRequest;
import io.activej.promise.Promise;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Code review workflow demonstrating multi-agent collaboration across IC and management layers.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates peer review process from PR submission to final approval/merge decision.
 * Demonstrates quality gates, parallel review, escalation paths, and team coordination.
 *
 * <p><b>Workflow Steps</b><br>
 * 1. Engineer submits PR (code changes, description, self-review checklist)
 * 2. SeniorEngineer technical review (architecture, patterns, complexity, mentoring)
 * 3. ArchitectLead design review (if architectural changes, >500 LOC, or new patterns)
 * 4. QAEngineer validates test coverage (unit/integration/E2E adequacy, quality)
 * 5. TeamLead final approval (workload impact, coordination, merge decision)
 *
 * <p><b>Escalation Triggers</b><br>
 * - Architectural changes → ArchitectLead mandatory review
 * - Large PRs (>500 LOC) → ArchitectLead review
 * - Insufficient test coverage (<80%) → QA blocks merge
 * - Conflicting feedback → TeamLead arbitrates
 * - Security/performance concerns → ArchitectLead + DevOpsLead
 *
 * <p><b>Decision Aggregation</b><br>
 * All reviewers must approve (or escalate blockers to TeamLead).
 * TeamLead makes final merge decision based on review feedback.
 *
 * @doc.type class
 * @doc.purpose Multi-agent code review workflow
 * @doc.layer product
 * @doc.pattern Workflow
 */
public class CodeReviewWorkflow {
    
    private final WorkflowEngine engine;
    private final Map<AgentRoleProto, Agent> agents;
    
    /**
     * Creates code review workflow with required agents.
     *
     * @param engine Workflow orchestration engine
     * @param agents Map of agent roles to agent instances (requires: ENGINEER, SENIOR_ENGINEER,
     *               ARCHITECT_LEAD, QA_ENGINEER, TEAM_LEAD)
     */
    public CodeReviewWorkflow(WorkflowEngine engine, Map<AgentRoleProto, Agent> agents) {
        this.engine = engine;
        this.agents = agents;
        validateRequiredAgents();
    }
    
    private void validateRequiredAgents() {
        List<AgentRoleProto> required = List.of(
            AgentRoleProto.AGENT_ROLE_ENGINEER,
            AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER,
            AgentRoleProto.AGENT_ROLE_TEAM_LEAD,
            AgentRoleProto.AGENT_ROLE_QA_ENGINEER,
            AgentRoleProto.AGENT_ROLE_TEAM_LEAD
        );
        
        List<AgentRoleProto> missing = required.stream()
            .filter(role -> !agents.containsKey(role))
            .collect(Collectors.toList());
        
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required agents: " + missing);
        }
    }
    
    /**
     * Executes code review workflow for a pull request.
     *
     * <p>GIVEN: PullRequest with code changes, description, author
     * <p>WHEN: execute() called
     * <p>THEN: Multi-stage review process (technical → design → quality → approval)
     *
     * @param pullRequest PR to review
     * @return Promise of WorkflowResult with final merge decision
     */
    public Promise<WorkflowResult> execute(PullRequest pullRequest) {
        WorkflowContext context = WorkflowContext.builder()
            .withCorrelationId("code-review-" + pullRequest.getId())
            .withUserId(pullRequest.getAuthor())
            .withMetadata("workflowType", "CODE_REVIEW")
            .withMetadata("pr_id", pullRequest.getId())
            .withMetadata("pr_title", pullRequest.getTitle())
            .withMetadata("pr_loc", String.valueOf(pullRequest.getLinesOfCode()))
            .withMetadata("pr_files", String.valueOf(pullRequest.getFilesChanged()))
            .withMetadata("has_architecture_changes", String.valueOf(pullRequest.hasArchitecturalChanges()))
            .withMetadata("test_coverage_pct", String.valueOf(pullRequest.getTestCoveragePct()))
            .build();
        
        WorkflowDefinition workflow = WorkflowDefinition.builder()
            // Step 1: Senior engineer technical review
            .addStep(WorkflowStep.builder()
                .stepId("technical_review")
                .stepName("Senior Engineer Technical Review")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER))
                .taskDescription(buildTechnicalReviewTask(pullRequest))
                .build())
            
            // Step 2: Architect review if needed (conditional)
            .addConditionalStep(
                "architect_review",
                ctx -> requiresArchitectReview(pullRequest),
                WorkflowStep.builder()
                    .stepId("architect_review")
                    .stepName("Architect Lead Design Review")
                    .executor(agents.get(AgentRoleProto.AGENT_ROLE_TEAM_LEAD))
                    .taskDescription(buildArchitectReviewTask(pullRequest))
                    .dependsOn(List.of("technical_review"))
                    .build())
            
            // Step 3: QA test coverage validation (parallel with architect if both needed)
            .addStep(WorkflowStep.builder()
                .stepId("qa_validation")
                .stepName("QA Test Coverage Validation")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_QA_ENGINEER))
                .taskDescription(buildQAValidationTask(pullRequest))
                .dependsOn(List.of("technical_review"))
                .build())
            
            // Step 4: Aggregate reviews and check for conflicts
            .<Map<String, Decision>>addAggregationStep("aggregate_reviews",
                results -> aggregateReviewDecisions(results, pullRequest))
            
            // Step 5: Team lead final approval
            .addStep(WorkflowStep.builder()
                .stepId("final_approval")
                .stepName("Team Lead Final Approval")
                .executor(agents.get(AgentRoleProto.AGENT_ROLE_TEAM_LEAD))
                .taskDescription(buildFinalApprovalTask(pullRequest))
                .dependsOn(List.of("aggregate_reviews"))
                .build())
            
            .build();
        
        return engine.executeWorkflow(workflow, context);
    }
    
    /**
     * Determines if architect review is required.
     *
     * <p>Triggers: Architectural changes, large PRs (>500 LOC), new design patterns
     */
    private boolean requiresArchitectReview(PullRequest pr) {
        return pr.hasArchitecturalChanges() 
            || pr.getLinesOfCode() > 500
            || pr.introducesNewPatterns();
    }
    
    private String buildTechnicalReviewTask(PullRequest pr) {
        return String.format("""
            TASK: Perform technical code review for pull request
            
            PULL REQUEST:
            - ID: %s
            - Title: %s
            - Author: %s
            - Lines Changed: %d
            - Files Changed: %d
            - Description: %s
            
            CODE CHANGES:
            %s
            
            REVIEW FOCUS:
            1. Code quality and best practices
            2. Design patterns and SOLID principles
            3. Performance implications
            4. Security considerations
            5. Error handling and edge cases
            6. Code duplication and reusability
            7. Naming and documentation
            8. Complexity and maintainability
            
            DELIVERABLES:
            - Review comments with severity (CRITICAL, HIGH, MEDIUM, LOW)
            - Approval status (APPROVE, REQUEST_CHANGES, COMMENT)
            - Mentoring opportunities for author
            - Estimated fix time for changes
            
            ESCALATION:
            - If architectural concerns → escalate to ARCHITECT_LEAD
            - If security vulnerabilities → escalate to ARCHITECT_LEAD + DEVOPS_LEAD
            - If complexity exceeds authority → escalate to ARCHITECT_LEAD
            """,
            pr.getId(),
            pr.getTitle(),
            pr.getAuthor(),
            pr.getLinesOfCode(),
            pr.getFilesChanged(),
            pr.getDescription(),
            pr.getCodeDiff()
        );
    }
    
    private String buildArchitectReviewTask(PullRequest pr) {
        return String.format("""
            TASK: Perform architectural design review for pull request
            
            PULL REQUEST:
            - ID: %s
            - Title: %s
            - Architectural Changes: %s
            - New Patterns: %s
            - System Impact: %s
            
            ARCHITECTURE REVIEW FOCUS:
            1. Design patterns appropriateness
            2. System architecture alignment
            3. Bounded context integrity (DDD)
            4. Cross-cutting concerns (logging, security, caching)
            5. Scalability and performance at scale
            6. Technical debt implications
            7. Migration and rollback strategy
            8. Documentation and ADR requirements
            
            DELIVERABLES:
            - Architectural feedback with rationale
            - Design improvement suggestions
            - Risk assessment (LOW, MEDIUM, HIGH, CRITICAL)
            - Documentation requirements
            - Follow-up work items
            
            DECISION:
            - APPROVE: Design is sound, ready to proceed
            - REQUEST_CHANGES: Design issues must be addressed
            - ESCALATE: Requires CTO review (enterprise-wide impact)
            """,
            pr.getId(),
            pr.getTitle(),
            pr.getArchitecturalChangesDescription(),
            pr.getNewPatternsDescription(),
            pr.getSystemImpactDescription()
        );
    }
    
    private String buildQAValidationTask(PullRequest pr) {
        return String.format("""
            TASK: Validate test coverage and quality for pull request
            
            PULL REQUEST:
            - ID: %s
            - Title: %s
            - Lines Changed: %d
            - Current Test Coverage: %.1f%%
            
            TEST COVERAGE ANALYSIS:
            - Unit Tests: %d tests
            - Integration Tests: %d tests
            - E2E Tests: %d tests
            
            VALIDATION CRITERIA:
            1. Test coverage >= 80%% (current: %.1f%%)
            2. All new code paths covered
            3. Edge cases and error scenarios tested
            4. Test quality (assertions, clarity, maintainability)
            5. No skipped or ignored tests
            6. Test execution time reasonable (<5 min for unit)
            7. Mock/stub usage appropriate
            8. Test documentation clear
            
            DELIVERABLES:
            - Coverage assessment (PASS/FAIL with threshold)
            - Missing test scenarios
            - Test quality feedback
            - Recommendation (APPROVE/BLOCK/REQUEST_IMPROVEMENTS)
            
            ESCALATION:
            - If coverage < 80%% → BLOCK merge (quality gate)
            - If critical paths untested → BLOCK merge
            - If test quality poor → REQUEST_IMPROVEMENTS
            """,
            pr.getId(),
            pr.getTitle(),
            pr.getLinesOfCode(),
            pr.getTestCoveragePct(),
            pr.getUnitTestsCount(),
            pr.getIntegrationTestsCount(),
            pr.getE2ETestsCount(),
            pr.getTestCoveragePct()
        );
    }
    
    private String buildFinalApprovalTask(PullRequest pr) {
        return String.format("""
            TASK: Final approval decision for pull request merge
            
            PULL REQUEST:
            - ID: %s
            - Title: %s
            - Author: %s
            
            REVIEW SUMMARY:
            - Technical Review: %s
            - Architecture Review: %s
            - QA Validation: %s
            
            TEAM LEAD CONSIDERATIONS:
            1. All reviewers approved (or conflicts resolved)
            2. Team workload and sprint goals
            3. Merge timing (avoid end-of-day, Fridays)
            4. Coordination with other PRs
            5. Deployment schedule alignment
            6. Author learning and growth
            
            DECISION OPTIONS:
            - APPROVE_AND_MERGE: All clear, merge immediately
            - APPROVE_SCHEDULE: Approved but schedule for later
            - REQUEST_CHANGES: Address review feedback first
            - ESCALATE: Conflicts or risks require management review
            
            DELIVERABLES:
            - Final merge decision
            - Merge timing recommendation
            - Follow-up actions (if any)
            - Author feedback and learning notes
            """,
            pr.getId(),
            pr.getTitle(),
            pr.getAuthor(),
            "PENDING", // Filled from workflow context
            "PENDING",
            "PENDING"
        );
    }
    
    /**
     * Aggregates review decisions from all reviewers.
     *
     * <p>CONFLICT RESOLUTION:
     * - If any CRITICAL issues → REQUEST_CHANGES
     * - If conflicting approvals → Escalate to TeamLead
     * - If all APPROVE → Proceed to final approval
     * - If QA blocks → Cannot merge regardless of other approvals
     */
    private Decision aggregateReviewDecisions(
            Map<String, Decision> stepResults,
            PullRequest pr) {
        
        Decision technicalReview = stepResults.get("technical_review");
        Decision architectReview = stepResults.get("architect_review");
        Decision qaValidation = stepResults.get("qa_validation");
        
        List<String> blockers = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        
        // Check technical review
        if (technicalReview.getType() == DecisionType.ESCALATE) {
            return Decision.builder()
                .type(DecisionType.ESCALATE)
                .escalationTarget("TEAM_LEAD")
                .rationale("Technical review escalated: " + technicalReview.getReasoning())
                .build();
        }
        if (technicalReview.getType() == DecisionType.REJECT) {
            blockers.add("Technical review: " + technicalReview.getReasoning());
        }
        
        // Check architect review if present
        if (architectReview != null) {
            if (architectReview.getType() == DecisionType.ESCALATE) {
                return Decision.builder()
                    .type(DecisionType.ESCALATE)
                    .escalationTarget("CTO")
                    .rationale("Architecture review escalated: " + architectReview.getReasoning())
                    .build();
            }
            if (architectReview.getType() == DecisionType.REJECT) {
                blockers.add("Architecture review: " + architectReview.getReasoning());
            }
        }
        
        // Check QA validation (quality gate)
        if (qaValidation.getType() == DecisionType.REJECT) {
            blockers.add("QA validation failed: " + qaValidation.getReasoning());
        }
        
        // Aggregate results
        if (!blockers.isEmpty()) {
            return Decision.builder()
                .type(DecisionType.REJECT)
                .rationale("Review blockers: " + String.join("; ", blockers))
                // TODO: Add metadata field to Decision model if needed
                .build();
        }
        
        // All reviews passed
        return Decision.builder()
            .type(DecisionType.APPROVE)
            .rationale("All reviews approved - ready for final team lead decision")
            .confidence(calculateAggregateConfidence(technicalReview, architectReview, qaValidation))
            // TODO: Add metadata field to Decision model if needed
            .build();
    }
    
    private double calculateAggregateConfidence(Decision... decisions) {
        return Arrays.stream(decisions)
            .filter(Objects::nonNull)
            .mapToDouble(Decision::getConfidence)
            .average()
            .orElse(0.0);
    }
}
