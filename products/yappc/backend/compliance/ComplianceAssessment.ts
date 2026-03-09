/**
 * Compliance Assessment Utilities
 *
 * Tools for evaluating compliance posture against frameworks.
 * Provides assessment scoring, gap analysis, and remediation tracking.
 *
 * Assessment types:
 * - Technical controls
 * - Process controls
 * - Policy controls
 * - Evidence verification
 */

export interface Assessment {
  id: string;
  date: Date;
  framework: string;
  controlId: string;
  assessor: string;
  result: AssessmentResult;
  evidence: string[];
  notes: string;
  nextAssessmentDate: Date;
}

export enum AssessmentResult {
  COMPLIANT = 'COMPLIANT',
  NON_COMPLIANT = 'NON_COMPLIANT',
  PARTIALLY_COMPLIANT = 'PARTIALLY_COMPLIANT',
  NOT_APPLICABLE = 'NOT_APPLICABLE',
}

export interface ComplianceGap {
  controlId: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  description: string;
  currentState: string;
  requiredState: string;
  remediationSteps: string[];
  estimatedEffort: number; // hours
  priority: number; // 1-10, higher = more urgent
}

export interface RemediationPlan {
  id: string;
  createdDate: Date;
  gapId: string;
  assignee: string;
  targetDate: Date;
  status: 'OPEN' | 'IN_PROGRESS' | 'COMPLETED' | 'ON_HOLD';
  steps: RemediationStep[];
}

export interface RemediationStep {
  sequence: number;
  description: string;
  assignee: string;
  dueDate: Date;
  completed: boolean;
  completedDate?: Date;
  evidence?: string[];
}

/**
 * Compliance assessment manager
 */
export class ComplianceAssessmentManager {
  private assessments: Map<string, Assessment> = new Map();
  private gaps: Map<string, ComplianceGap> = new Map();
  private plans: Map<string, RemediationPlan> = new Map();

  /**
   * Record assessment result
   *
   * GIVEN: Control assessment details
   * WHEN: recordAssessment called
   * THEN: Assessment stored with ID and evidence
   */
  recordAssessment(
    framework: string,
    controlId: string,
    result: AssessmentResult,
    evidence: string[],
    assessor: string,
    notes: string = ''
  ): Assessment {
    const assessment: Assessment = {
      id: `assessment-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      date: new Date(),
      framework,
      controlId,
      assessor,
      result,
      evidence,
      notes,
      nextAssessmentDate: this.calculateNextAssessmentDate(new Date()),
    };

    this.assessments.set(assessment.id, assessment);
    return assessment;
  }

  /**
   * Get assessment history for control
   *
   * GIVEN: Framework and control ID
   * WHEN: getAssessmentHistory called
   * THEN: Chronologically ordered assessments returned
   */
  getAssessmentHistory(framework: string, controlId: string): Assessment[] {
    return Array.from(this.assessments.values())
      .filter((a) => a.framework === framework && a.controlId === controlId)
      .sort((a, b) => b.date.getTime() - a.date.getTime());
  }

  /**
   * Identify compliance gaps
   *
   * GIVEN: Assessment results
   * WHEN: identifyGaps called
   * THEN: Non-compliant controls listed with severity
   */
  identifyGaps(framework: string, assessments: Assessment[]): ComplianceGap[] {
    const gaps: ComplianceGap[] = [];

    for (const assessment of assessments) {
      if (assessment.result === AssessmentResult.NON_COMPLIANT ||
          assessment.result === AssessmentResult.PARTIALLY_COMPLIANT) {
        
        const gap: ComplianceGap = {
          controlId: assessment.controlId,
          severity: this.calculateSeverity(assessment.result),
          description: `Control ${assessment.controlId} is not compliant`,
          currentState: 'Non-compliant',
          requiredState: 'Compliant',
          remediationSteps: this.suggestRemediationSteps(assessment.controlId),
          estimatedEffort: this.estimateRemediationEffort(assessment.controlId),
          priority: this.calculatePriority(assessment.result, framework, assessment.controlId),
        };

        gaps.push(gap);
        this.gaps.set(gap.controlId, gap);
      }
    }

    return gaps.sort((a, b) => b.priority - a.priority);
  }

  /**
   * Create remediation plan for gap
   *
   * GIVEN: Compliance gap and assignee
   * WHEN: createRemediationPlan called
   * THEN: Structured remediation plan created
   */
  createRemediationPlan(gap: ComplianceGap, assignee: string, startDate: Date): RemediationPlan {
    const steps = gap.remediationSteps.map((step, index) => ({
      sequence: index + 1,
      description: step,
      assignee,
      dueDate: new Date(startDate.getTime() + (index + 1) * 7 * 24 * 60 * 60 * 1000), // Weekly milestones
      completed: false,
    }));

    const plan: RemediationPlan = {
      id: `plan-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      createdDate: new Date(),
      gapId: gap.controlId,
      assignee,
      targetDate: new Date(startDate.getTime() + steps.length * 7 * 24 * 60 * 60 * 1000),
      status: 'OPEN',
      steps,
    };

    this.plans.set(plan.id, plan);
    return plan;
  }

  /**
   * Update remediation plan step
   *
   * GIVEN: Plan ID, step sequence, completion info
   * WHEN: updateRemediationStep called
   * THEN: Step marked complete with evidence
   */
  updateRemediationStep(planId: string, stepSequence: number, evidence: string[]): boolean {
    const plan = this.plans.get(planId);
    if (!plan) return false;

    const step = plan.steps.find((s) => s.sequence === stepSequence);
    if (!step) return false;

    step.completed = true;
    step.completedDate = new Date();
    step.evidence = evidence;

    // Update plan status if all steps complete
    if (plan.steps.every((s) => s.completed)) {
      plan.status = 'COMPLETED';
    } else if (plan.status === 'OPEN') {
      plan.status = 'IN_PROGRESS';
    }

    return true;
  }

  /**
   * Calculate compliance score
   *
   * GIVEN: Assessment results for framework
   * WHEN: calculateComplianceScore called
   * THEN: Weighted compliance percentage returned
   *
   * Scoring:
   * - COMPLIANT: 100%
   * - PARTIALLY_COMPLIANT: 50%
   * - NON_COMPLIANT: 0%
   * - NOT_APPLICABLE: excluded from calculation
   */
  calculateComplianceScore(framework: string): number {
    const assessments = Array.from(this.assessments.values())
      .filter((a) => a.framework === framework)
      .filter((a) => a.result !== AssessmentResult.NOT_APPLICABLE);

    if (assessments.length === 0) return 0;

    const scoreMap = {
      [AssessmentResult.COMPLIANT]: 100,
      [AssessmentResult.PARTIALLY_COMPLIANT]: 50,
      [AssessmentResult.NON_COMPLIANT]: 0,
      [AssessmentResult.NOT_APPLICABLE]: 0,
    };

    const totalScore = assessments.reduce((sum, a) => sum + (scoreMap[a.result] || 0), 0);
    return Math.round(totalScore / assessments.length);
  }

  /**
   * Get assessment due soon
   *
   * GIVEN: Days threshold
   * WHEN: getAssessmentsDue called
   * THEN: Assessments with next date within threshold returned
   */
  getAssessmentsDue(daysThreshold: number = 30): Assessment[] {
    const cutoffDate = new Date(Date.now() + daysThreshold * 24 * 60 * 60 * 1000);
    const controlsSeen = new Set<string>();
    const assessmentsDue: Assessment[] = [];

    // Get most recent assessment per control
    for (const assessment of Array.from(this.assessments.values()).sort(
      (a, b) => b.date.getTime() - a.date.getTime()
    )) {
      const key = `${assessment.framework}-${assessment.controlId}`;
      if (!controlsSeen.has(key)) {
        controlsSeen.add(key);
        if (assessment.nextAssessmentDate <= cutoffDate) {
          assessmentsDue.push(assessment);
        }
      }
    }

    return assessmentsDue;
  }

  /**
   * Get open remediation plans
   */
  getOpenRemediationPlans(): RemediationPlan[] {
    return Array.from(this.plans.values())
      .filter((p) => p.status === 'OPEN' || p.status === 'IN_PROGRESS')
      .sort((a, b) => a.targetDate.getTime() - b.targetDate.getTime());
  }

  /**
   * Get remediation plan progress
   *
   * GIVEN: Plan ID
   * WHEN: getRemediationProgress called
   * THEN: Progress percentage and details returned
   */
  getRemediationProgress(planId: string) {
    const plan = this.plans.get(planId);
    if (!plan) return null;

    const completedSteps = plan.steps.filter((s) => s.completed).length;
    const progress = Math.round((completedSteps / plan.steps.length) * 100);

    return {
      planId,
      progress,
      completedSteps,
      totalSteps: plan.steps.length,
      targetDate: plan.targetDate,
      status: plan.status,
      daysRemaining: Math.ceil((plan.targetDate.getTime() - Date.now()) / (24 * 60 * 60 * 1000)),
    };
  }

  // Private helper methods

  private calculateNextAssessmentDate(lastAssessment: Date): Date {
    const nextDate = new Date(lastAssessment);
    nextDate.setDate(nextDate.getDate() + 90); // 90-day default
    return nextDate;
  }

  private calculateSeverity(result: AssessmentResult): 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' {
    return result === AssessmentResult.NON_COMPLIANT ? 'CRITICAL' : 'HIGH';
  }

  private suggestRemediationSteps(controlId: string): string[] {
    // Control-specific remediation guidance
    const steps: Record<string, string[]> = {
      default: [
        'Review current control implementation',
        'Document gaps and requirements',
        'Develop implementation plan',
        'Implement control improvements',
        'Test and validate control',
        'Document evidence and verify compliance',
      ],
    };

    return steps[controlId] || steps.default;
  }

  private estimateRemediationEffort(controlId: string): number {
    // Effort estimation based on control complexity
    const effortMap: Record<string, number> = {
      default: 40, // hours
    };

    return effortMap[controlId] || effortMap.default;
  }

  private calculatePriority(
    result: AssessmentResult,
    framework: string,
    controlId: string
  ): number {
    // Priority based on criticality and framework importance
    let priority = 5;

    if (result === AssessmentResult.NON_COMPLIANT) priority += 3;
    if (framework === 'SOC2') priority += 1;

    return Math.min(priority, 10);
  }
}

/**
 * Global assessment manager
 */
export const assessmentManager = new ComplianceAssessmentManager();
