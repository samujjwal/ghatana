/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import com.ghatana.agent.mastery.VersionScope;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of mode selection policy.
 *
 * <p>Maps mastery states to execution modes based on the following logic:
 * <ul>
 *   <li><b>UNKNOWN</b>: BLOCKED - skill not known, cannot execute</li>
 *   <li><b>OBSERVED</b>: VERIFICATION - requires verification proof before execution</li>
 *   <li><b>PRACTICED</b>: APPROVAL - requires human approval before execution</li>
 *   <li><b>COMPETENT</b>: VERIFICATION - requires verification proof for each execution</li>
 *   <li><b>MASTERED</b>: AUTONOMOUS - can execute deterministically without checks</li>
 *   <li><b>MAINTENANCE_ONLY</b>: APPROVAL - requires approval for legacy work only</li>
 *   <li><b>OBSOLETE</b>: BLOCKED - skill is obsolete, cannot execute</li>
 *   <li><b>RETIRED</b>: BLOCKED - skill is retired, cannot execute</li>
 *   <li><b>QUARANTINED</b>: BLOCKED - skill is quarantined for safety, cannot execute</li>
 * </ul>
 *
 * <p>Version compatibility is also considered:
 * <ul>
 *   <li><b>ACTIVE</b>: Normal mode selection applies</li>
 *   <li><b>MAINTENANCE</b>: Downgrade to APPROVAL if not already BLOCKED</li>
 *   <li><b>OBSOLETE</b>: BLOCK regardless of mastery state</li>
 *   <li><b>UNKNOWN</b>: Proceed with mastery state mapping</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default mode selection policy with mastery state mapping
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public class DefaultModeSelectionPolicy implements ModeSelectionPolicy {

    @Override
    @NotNull
    public Promise<ModeSelectionResult> selectMode(
            @NotNull MasteryDecision masteryDecision,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext) {
        
        MasteryState state = masteryDecision.state();
        
        // Check version compatibility first
        VersionApplicability versionApplicability = checkVersionCompatibility(masteryDecision, versionContext);
        if (versionApplicability == VersionApplicability.OBSOLETE) {
            return Promise.of(ModeSelectionResult.of(
                    ExecutionMode.BLOCKED,
                    "Version is obsolete for this skill"
            ));
        }
        
        // Map mastery state to execution mode
        return Promise.of(mapStateToMode(state, taskClassification, versionApplicability));
    }
    
    /**
     * Checks version compatibility using mastery version scope.
     *
     * @param masteryDecision mastery decision with version scope
     * @param versionContext current version context
     * @return version applicability
     */
    private VersionApplicability checkVersionCompatibility(
            @NotNull MasteryDecision masteryDecision,
            @NotNull VersionContext versionContext) {
        
        if (masteryDecision.versionScope() != null && !masteryDecision.versionScope().equals(VersionScope.empty())) {
            return masteryDecision.versionScope().classify(versionContext);
        }
        
        return VersionApplicability.UNKNOWN;
    }
    
    /**
     * Maps mastery state to execution mode with governance requirements.
     *
     * @param state mastery state
     * @param taskClassification task classification
     * @param versionApplicability version applicability
     * @return mode selection result
     */
    @NotNull
    private ModeSelectionResult mapStateToMode(
            @NotNull MasteryState state,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionApplicability versionApplicability) {
        
        // Terminal states are always BLOCKED
        if (state.isTerminal()) {
            String reason = switch (state) {
                case OBSOLETE -> "Skill is obsolete and cannot be used for execution";
                case RETIRED -> "Skill is retired and has no active use cases";
                case QUARANTINED -> "Skill is quarantined due to safety violations";
                default -> "Skill is in a terminal state and cannot execute";
            };
            return ModeSelectionResult.of(ExecutionMode.BLOCKED, reason);
        }
        
        // UNKNOWN state is BLOCKED
        if (state == MasteryState.UNKNOWN) {
            return ModeSelectionResult.of(
                    ExecutionMode.BLOCKED,
                    "Skill is unknown - no evidence of mastery exists"
            );
        }
        
        // Apply version compatibility adjustments
        if (versionApplicability == VersionApplicability.MAINTENANCE) {
            if (state == MasteryState.MASTERED || state == MasteryState.COMPETENT) {
                return ModeSelectionResult.requiringApproval(
                        ExecutionMode.SUPERVISED,
                        "Skill is mastered/competent but version is in maintenance - requires approval"
                );
            }
        }
        
        // Map non-terminal states
        return switch (state) {
            case OBSERVED -> ModeSelectionResult.requiringVerification(
                    ExecutionMode.SUPERVISED,
                    "Skill has been observed but not practiced - requires verification proof"
            );
            
            case PRACTICED -> ModeSelectionResult.requiringApproval(
                    ExecutionMode.SUPERVISED,
                    "Skill has been practiced but not evaluated - requires approval"
            );
            
            case COMPETENT -> ModeSelectionResult.requiringVerification(
                    ExecutionMode.AUTONOMOUS,
                    "Skill is competent but not fully mastered - requires verification proof"
            );
            
            case MASTERED -> ModeSelectionResult.of(
                    ExecutionMode.AUTONOMOUS,
                    "Skill is mastered with full regression, safety, and recovery test coverage"
            );
            
            case MAINTENANCE_ONLY -> ModeSelectionResult.requiringApproval(
                    ExecutionMode.SUPERVISED,
                    "Skill is in maintenance-only mode for legacy versions - requires approval"
            );
            
            default -> ModeSelectionResult.of(
                    ExecutionMode.BLOCKED,
                    "Unknown mastery state - cannot determine execution mode"
            );
        };
    }
}
