/**
 * Viva Intervention Workflow Service
 *
 * Connects VivaEngine overconfidence detection to intervention workflows.
 * Triggers oral assessments when learners show concerning patterns.
 *
 * @doc.type class
 * @doc.purpose Connect Viva detection to intervention workflows
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
  VivaCandidate,
  VivaConditionType,
} from "@tutorputor/contracts/v1/learning-unit";
import { VivaEngine, type PredictionRecord, type SimulationRecord, type ExplanationRecord, type CohortStats } from "@tutorputor/core/kernel/engine/analytics/VivaEngine";

/**
 * Intervention action types
 */
export type InterventionAction =
  | "schedule_viva"
  | "flag_instructor"
  | "require_remediation"
  | "block_progression"
  | "notify_learner";

/**
 * Intervention severity levels
 */
export type InterventionSeverity = "critical" | "high" | "medium" | "low";

/**
 * Intervention trigger mapping
 */
interface InterventionRule {
  condition: VivaConditionType;
  action: InterventionAction;
  severity: InterventionSeverity;
  autoExecute: boolean;
  description: string;
}

/**
 * Intervention event
 */
export interface InterventionEvent {
  id: string;
  learnerId: string;
  claimId: string;
  triggerReason: VivaConditionType;
  action: InterventionAction;
  severity: InterventionSeverity;
  timestamp: Date;
  status: "pending" | "executed" | "dismissed" | "escalated";
  metadata: {
    priority: number;
    calibrationIndex?: number;
    consecutiveWrong?: number;
    speedPercentile?: number;
    notes?: string;
  };
}

/**
 * Intervention result
 */
export interface InterventionResult {
  eventId: string;
  executed: boolean;
  action: InterventionAction;
  learnerNotified: boolean;
  instructorNotified: boolean;
  scheduledVivaId?: string;
  error?: string;
}

/**
 * Viva queue entry
 */
export interface VivaQueueEntry {
  id: string;
  learnerId: string;
  claimId: string;
  priority: number;
  reason: VivaConditionType;
  scheduledAt: Date;
  slot: {
    startsAt: Date;
    durationMinutes: number;
  };
  rubric: Array<{
    criterionId: string;
    description: string;
    maxScore: number;
  }>;
  state: "scheduled" | "completed" | "remediation_required" | "reviva_scheduled";
  recordingUrl?: string;
  remediationTask?: {
    taskId: string;
    claimId: string;
    status: "assigned" | "completed";
  };
  reVivaForId?: string;
  conductedAt?: Date;
  result?: "pass" | "fail" | "inconclusive";
  notes?: string;
}

/**
 * Workflow configuration
 */
export interface WorkflowConfig {
  autoScheduleViva: boolean;
  notifyInstructor: boolean;
  notifyLearner: boolean;
  blockOnCritical: boolean;
  instructorThreshold: InterventionSeverity;
  randomSamplingRate: number;
}

/**
 * Viva Intervention Workflow Service
 */
export class VivaInterventionWorkflow {
  private vivaEngine: VivaEngine;
  private config: WorkflowConfig;
  private interventionRules: InterventionRule[];
  private interventionLog: InterventionEvent[] = [];
  private vivaQueue: VivaQueueEntry[] = [];
  private listeners: Map<string, ((event: InterventionEvent) => void)[]> = new Map();

  constructor(
    cohortStats: CohortStats,
    config: Partial<WorkflowConfig> = {},
  ) {
    this.vivaEngine = new VivaEngine(cohortStats);
    this.config = {
      autoScheduleViva: true,
      notifyInstructor: true,
      notifyLearner: true,
      blockOnCritical: true,
      instructorThreshold: "high",
      randomSamplingRate: 0.1,
      ...config,
    };
    this.interventionRules = this.initializeRules();
  }

  /**
   * Initialize default intervention rules
   */
  private initializeRules(): InterventionRule[] {
    return [
      {
        condition: "overconfident_wrong",
        action: "schedule_viva",
        severity: "critical",
        autoExecute: true,
        description: "Learner is confidently wrong - schedule oral assessment",
      },
      {
        condition: "random_sampling",
        action: "schedule_viva",
        severity: "low",
        autoExecute: true,
        description: "Random quality-control viva sample",
      },
      {
        condition: "speed_anomaly",
        action: "flag_instructor",
        severity: "high",
        autoExecute: false,
        description: "Suspiciously fast completion - instructor review needed",
      },
      {
        condition: "pattern_mismatch",
        action: "require_remediation",
        severity: "medium",
        autoExecute: true,
        description: "Pattern suggests guessing - require remediation",
      },
      {
        condition: "explanation_avoidance",
        action: "require_remediation",
        severity: "high",
        autoExecute: true,
        description: "Avoiding explanation tasks - require remediation",
      },
      {
        condition: "gaming_detection",
        action: "block_progression",
        severity: "critical",
        autoExecute: true,
        description: "Possible gaming behavior - block progression",
      },
      {
        condition: "sim_evidence_contradiction",
        action: "schedule_viva",
        severity: "high",
        autoExecute: true,
        description: "Simulation and prediction mismatch - schedule viva",
      },
    ];
  }

  /**
   * Process learner evidence and trigger interventions
   */
  async processEvidence(
    learnerId: string,
    predictions: PredictionRecord[],
    simulations: SimulationRecord[],
    explanations: ExplanationRecord[] = [],
  ): Promise<InterventionResult[]> {
    // Identify viva candidates
    const candidates = this.vivaEngine.identifyVivaCandidatesWithSampling(
      predictions,
      simulations,
      explanations,
      this.config.randomSamplingRate,
    );

    // Find candidates for this learner, or process cohort-wide samples when requested.
    const learnerCandidates =
      learnerId === "*"
        ? candidates
        : candidates.filter((c) => c.learnerId === learnerId);

    // Execute interventions
    const results: InterventionResult[] = [];
    for (const candidate of learnerCandidates) {
      const result = await this.executeIntervention(candidate, {
        predictions,
        simulations,
        explanations,
      });
      results.push(result);
    }

    return results;
  }

  /**
   * Execute intervention for a viva candidate
   */
  private async executeIntervention(
    candidate: VivaCandidate,
    context: {
      predictions: PredictionRecord[];
      simulations: SimulationRecord[];
      explanations: ExplanationRecord[];
    },
  ): Promise<InterventionResult> {
    const rule = this.interventionRules.find(
      (r) => r.condition === candidate.reason,
    );

    if (!rule) {
      return {
        eventId: "",
        executed: false,
        action: "notify_learner",
        learnerNotified: false,
        instructorNotified: false,
        error: `No rule found for condition: ${candidate.reason}`,
      };
    }

    // Create intervention event
    const event: InterventionEvent = {
      id: `int-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      learnerId: candidate.learnerId,
      claimId: candidate.claimId,
      triggerReason: candidate.reason,
      action: rule.action,
      severity: rule.severity,
      timestamp: new Date(),
      status: "pending",
      metadata: {
        priority: candidate.priority,
        notes: rule.description,
      },
    };

    this.interventionLog.push(event);
    this.emit("intervention:created", event);

    // Execute based on action type
    const result = await this.executeAction(event, rule, context);
    
    if (result.executed) {
      event.status = "executed";
    }

    this.emit("intervention:completed", event);
    return result;
  }

  /**
   * Execute the specific intervention action
   */
  private async executeAction(
    event: InterventionEvent,
    rule: InterventionRule,
    context: {
      predictions: PredictionRecord[];
      simulations: SimulationRecord[];
      explanations: ExplanationRecord[];
    },
  ): Promise<InterventionResult> {
    const result: InterventionResult = {
      eventId: event.id,
      executed: false,
      action: rule.action,
      learnerNotified: false,
      instructorNotified: false,
    };

    switch (rule.action) {
      case "schedule_viva":
        if (this.config.autoScheduleViva || rule.autoExecute) {
          const vivaId = await this.scheduleViva(event);
          result.scheduledVivaId = vivaId;
          result.executed = true;
        }
        break;

      case "flag_instructor":
        if (this.config.notifyInstructor && this.shouldNotifyInstructor(rule.severity)) {
          await this.notifyInstructor(event, context);
          result.instructorNotified = true;
          result.executed = true;
        }
        break;

      case "require_remediation":
        await this.requireRemediation(event);
        result.executed = true;
        break;

      case "block_progression":
        if (this.config.blockOnCritical) {
          await this.blockProgression(event);
          result.executed = true;
        }
        break;

      case "notify_learner":
        if (this.config.notifyLearner) {
          await this.notifyLearner(event);
          result.learnerNotified = true;
          result.executed = true;
        }
        break;
    }

    return result;
  }

  /**
   * Schedule a viva (oral assessment)
   */
  private async scheduleViva(event: InterventionEvent): Promise<string> {
    const vivaId = `viva-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    
    const entry: VivaQueueEntry = {
      id: vivaId,
      learnerId: event.learnerId,
      claimId: event.claimId,
      priority: event.metadata.priority,
      reason: event.triggerReason,
      scheduledAt: new Date(),
      slot: {
        startsAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
        durationMinutes: 15,
      },
      rubric: [
        {
          criterionId: "conceptual-explanation",
          description: "Learner explains the claim in their own words",
          maxScore: 4,
        },
        {
          criterionId: "simulation-evidence",
          description: "Learner connects simulation behavior to evidence",
          maxScore: 4,
        },
        {
          criterionId: "confidence-calibration",
          description: "Learner reflects accurately on confidence",
          maxScore: 2,
        },
      ],
      state: "scheduled",
    };

    this.vivaQueue.push(entry);
    this.emit("viva:scheduled", entry);

    return vivaId;
  }

  /**
   * Notify instructor about learner
   */
  private async notifyInstructor(
    event: InterventionEvent,
    _context: {
      predictions: PredictionRecord[];
      simulations: SimulationRecord[];
      explanations: ExplanationRecord[];
    },
  ): Promise<void> {
    // In production, this would send email/notification
    this.emit("instructor:notified", {
      instructorId: "system",
      learnerId: event.learnerId,
      claimId: event.claimId,
      reason: event.triggerReason,
      severity: event.severity,
      timestamp: new Date(),
    });
  }

  /**
   * Require learner to complete remediation
   */
  private async requireRemediation(event: InterventionEvent): Promise<void> {
    this.emit("remediation:required", {
      learnerId: event.learnerId,
      claimId: event.claimId,
      reason: event.triggerReason,
      requiredModules: ["remediation-basics", `remediation-${event.claimId}`],
      timestamp: new Date(),
    });
  }

  /**
   * Block learner from progressing
   */
  private async blockProgression(event: InterventionEvent): Promise<void> {
    this.emit("progression:blocked", {
      learnerId: event.learnerId,
      claimId: event.claimId,
      reason: event.triggerReason,
      until: "viva_completed",
      timestamp: new Date(),
    });
  }

  /**
   * Notify learner about intervention
   */
  private async notifyLearner(event: InterventionEvent): Promise<void> {
    const messages: Record<VivaConditionType, string> = {
      overconfident_wrong: "Your confidence seems high but answers suggest gaps. An oral assessment is scheduled.",
      speed_anomaly: "Your completion speed was unusual. Please take your time to understand the material.",
      pattern_mismatch: "Your approach suggests some guessing. Let's review the fundamentals.",
      explanation_avoidance: "You skipped explanation tasks. These help solidify understanding.",
      gaming_detection: "Progression has been paused. Please contact your instructor.",
      sim_evidence_contradiction: "There's a mismatch between your predictions and simulations. Let's discuss this.",
      random_sampling: "You have been selected for a short verification viva sample.",
    };

    this.emit("learner:notified", {
      learnerId: event.learnerId,
      message: messages[event.triggerReason],
      severity: event.severity,
      timestamp: new Date(),
    });
  }

  /**
   * Check if instructor should be notified based on severity
   */
  private shouldNotifyInstructor(severity: InterventionSeverity): boolean {
    const severityOrder: Record<InterventionSeverity, number> = {
      low: 1,
      medium: 2,
      high: 3,
      critical: 4,
    };

    const thresholdOrder = severityOrder[this.config.instructorThreshold];
    const currentOrder = severityOrder[severity];

    return currentOrder >= thresholdOrder;
  }

  /**
   * Get pending interventions
   */
  getPendingInterventions(): InterventionEvent[] {
    return this.interventionLog.filter((e) => e.status === "pending");
  }

  /**
   * Get viva queue
   */
  getVivaQueue(): VivaQueueEntry[] {
    return [...this.vivaQueue].sort((a, b) => a.priority - b.priority);
  }

  /**
   * Record viva result
   */
  recordVivaResult(
    vivaId: string,
    result: "pass" | "fail" | "inconclusive",
    notes?: string,
    recordingUrl?: string,
  ): void {
    const entry = this.vivaQueue.find((v) => v.id === vivaId);
    if (entry) {
      entry.result = result;
      entry.conductedAt = new Date();
      entry.state = result === "pass" ? "completed" : "remediation_required";
      if (notes !== undefined) {
        entry.notes = notes;
      }
      if (recordingUrl !== undefined) {
        entry.recordingUrl = recordingUrl;
      }
      if (result === "fail") {
        entry.remediationTask = {
          taskId: `remediate-${entry.claimId}`,
          claimId: entry.claimId,
          status: "assigned",
        };
        this.scheduleReViva(entry);
      }
      this.emit("viva:completed", entry);
    }
  }

  completeRemediation(vivaId: string): boolean {
    const entry = this.vivaQueue.find((v) => v.id === vivaId);
    if (!entry?.remediationTask) {
      return false;
    }
    entry.remediationTask.status = "completed";
    return true;
  }

  private scheduleReViva(original: VivaQueueEntry): void {
    const vivaId = `reviva-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const reViva: VivaQueueEntry = {
      ...original,
      id: vivaId,
      scheduledAt: new Date(),
      slot: {
        startsAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
        durationMinutes: 15,
      },
      state: "reviva_scheduled",
      result: undefined,
      conductedAt: undefined,
      notes: undefined,
      recordingUrl: undefined,
      remediationTask: undefined,
      reVivaForId: original.id,
    };
    original.state = "reviva_scheduled";
    this.vivaQueue.push(reViva);
    this.emit("viva:reviva_scheduled", reViva);
  }

  /**
   * Dismiss an intervention
   */
  dismissIntervention(eventId: string, reason: string): boolean {
    const event = this.interventionLog.find((e) => e.id === eventId);
    if (event) {
      event.status = "dismissed";
      this.emit("intervention:dismissed", { event, reason });
      return true;
    }
    return false;
  }

  /**
   * Subscribe to events
   */
  on(event: string, callback: (data: unknown) => void): void {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event)!.push(callback as (event: InterventionEvent) => void);
  }

  /**
   * Emit event
   */
  private emit(event: string, data: unknown): void {
    const callbacks = this.listeners.get(event);
    if (callbacks) {
      callbacks.forEach((cb) => cb(data as InterventionEvent));
    }
  }

  /**
   * Get intervention statistics
   */
  getStatistics(): {
    totalInterventions: number;
    bySeverity: Record<InterventionSeverity, number>;
    byAction: Record<InterventionAction, number>;
    byCondition: Record<VivaConditionType, number>;
    pendingCount: number;
    executedCount: number;
    dismissedCount: number;
    vivaQueueLength: number;
  } {
    const bySeverity: Record<InterventionSeverity, number> = {
      critical: 0,
      high: 0,
      medium: 0,
      low: 0,
    };

    const byAction: Record<InterventionAction, number> = {
      schedule_viva: 0,
      flag_instructor: 0,
      require_remediation: 0,
      block_progression: 0,
      notify_learner: 0,
    };

    const byCondition: Record<VivaConditionType, number> = {
      overconfident_wrong: 0,
      speed_anomaly: 0,
      pattern_mismatch: 0,
      explanation_avoidance: 0,
      gaming_detection: 0,
      sim_evidence_contradiction: 0,
      random_sampling: 0,
    };

    for (const event of this.interventionLog) {
      bySeverity[event.severity]++;
      byAction[event.action]++;
      byCondition[event.triggerReason]++;
    }

    return {
      totalInterventions: this.interventionLog.length,
      bySeverity,
      byAction,
      byCondition,
      pendingCount: this.interventionLog.filter((e) => e.status === "pending").length,
      executedCount: this.interventionLog.filter((e) => e.status === "executed").length,
      dismissedCount: this.interventionLog.filter((e) => e.status === "dismissed").length,
      vivaQueueLength: this.vivaQueue.length,
    };
  }
}

/**
 * Factory function
 */
export function createVivaInterventionWorkflow(
  cohortStats: CohortStats,
  config?: Partial<WorkflowConfig>,
): VivaInterventionWorkflow {
  return new VivaInterventionWorkflow(cohortStats, config);
}
