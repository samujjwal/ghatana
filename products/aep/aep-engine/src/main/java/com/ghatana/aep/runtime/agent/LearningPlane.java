package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Learning Plane is the SPI for agent self-improvement.
 * It provides grading, skill promotion, rollback, and evaluation
 * capabilities that sit above the Memory Plane.
 *
 * <p>Key operations:
 * <ul>
 *   <li><b>gradeTrace</b>: Scores a completed execution trace</li>
 *   <li><b>promoteSkill</b>: Promotes a procedure to a higher confidence tier</li>
 *   <li><b>rollback</b>: Reverts a failed promotion</li>
 *   <li><b>evaluateUpdate</b>: Runs evaluation gates before a skill change</li>
 *   <li><b>listSkillVersions</b>: Retrieves version history of a skill</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Learning plane SPI for agent self-improvement
 * @doc.layer agent-learning
 */
public interface LearningPlane {

    /**
     * Grades a completed execution trace, scoring quality and extracting lessons.
     *
     * @param traceId The unique trace identifier
     * @param agentId The agent that produced the trace
     * @return Grade result with score, feedback, and extracted lessons
     */
    @NotNull Promise<TraceGrade> gradeTrace(@NotNull String traceId, @NotNull String agentId);

    /**
     * Promotes a skill (procedure) to a higher confidence tier after
     * passing evaluation gates.
     *
     * @param skillId The procedure/skill to promote
     * @param targetVersion Version to promote to
     * @return Promotion result with success/failure and evaluation details
     */
    @NotNull Promise<PromotionResult> promoteSkill(@NotNull String skillId, @NotNull String targetVersion);

    /**
     * Rolls back a previously promoted skill to its prior version.
     *
     * @param skillId The skill to rollback
     * @param reason Reason for rollback
     * @return Rollback result
     */
    @NotNull Promise<RollbackResult> rollback(@NotNull String skillId, @NotNull String reason);

    /**
     * Evaluates a proposed skill update against evaluation gates
     * (regression, safety, performance).
     *
     * @param candidate The proposed update
     * @return Evaluation result with pass/fail per gate
     */
    @NotNull Promise<EvaluationGateResult> evaluateUpdate(@NotNull UpdateCandidate candidate);

    /**
     * Lists all versions of a skill with their evaluation history.
     *
     * @param skillId The skill to query
     * @return Version list, most recent first
     */
    @NotNull Promise<List<SkillVersion>> listSkillVersions(@NotNull String skillId);
}
