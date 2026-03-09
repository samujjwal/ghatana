/**
 * Compliance Automation Service
 *
 * <p><b>Purpose</b><br>
 * Automates compliance workflows including control assessment, evidence collection,
 * remediation tracking, and reporting. Provides intelligent recommendations based
 * on compliance frameworks and risk assessment.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new ComplianceAutomationService(controlRepo, auditRepo);
 * const recommendations = await service.generateRemediationPlan(assessmentId);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Compliance automation and workflow orchestration
 * @doc.layer product
 * @doc.pattern Service
 */

import { PrismaClient } from '@prisma/client';

/**
 * Interface for remediation step recommendations
 */
export interface RemediationStep {
  id: string;
  controlId: string;
  title: string;
  description: string;
  priority: 'critical' | 'high' | 'medium' | 'low';
  estimatedEffort: number; // hours
  owner: string;
  deadline: Date;
  status: 'open' | 'in_progress' | 'completed' | 'deferred';
  evidence: string[];
  dependencies: string[]; // IDs of dependent steps
}

/**
 * Interface for remediation plan
 */
export interface RemediationPlan {
  id: string;
  assessmentId: string;
  steps: RemediationStep[];
  totalEffort: number;
  completionTarget: Date;
  riskScore: number;
  status: 'draft' | 'approved' | 'executing' | 'completed';
}

/**
 * Interface for compliance recommendation
 */
export interface ComplianceRecommendation {
  id: string;
  type: 'control' | 'process' | 'technology' | 'training';
  title: string;
  description: string;
  framework: string;
  impactScore: number;
  implementationCost: number;
  expectedBenefit: string;
  precedingControls: string[];
}

/**
 * ComplianceAutomationService handles automated compliance workflows
 */
export class ComplianceAutomationService {
  /**
   * Creates a new ComplianceAutomationService instance.
   *
   * @param prisma - Prisma client for database access
   */
  constructor(private prisma: PrismaClient) { }

  /**
   * Generates a remediation plan for an assessment.
   *
   * <p><b>Purpose</b><br>
   * Analyzes assessment findings and creates prioritized, sequenced remediation steps
   * based on control dependencies, risk levels, and resource availability.
   *
   * @param assessmentId - The assessment ID
   * @param frameworkId - Optional framework ID for control mapping
   * @returns Promise<RemediationPlan> - Generated remediation plan
   * @throws Error if assessment not found
   */
  async generateRemediationPlan(
    assessmentId: string,
    frameworkId?: string
  ): Promise<RemediationPlan> {
    try {
      // Fetch assessment findings
      const assessment = await this.prisma.complianceAssessment.findUnique({
        where: { id: assessmentId },
      });

      if (!assessment) {
        throw new Error(`Assessment not found: ${assessmentId}`);
      }

      // Group findings by control and calculate priorities
      const controlFindings = this.groupFindingsByControl(assessment.findings as unknown[]);

      // Generate remediation steps with dependencies
      const steps = this.generateRemediationSteps(
        controlFindings,
        assessment.controls as unknown[],
        frameworkId
      );

      // Sort steps by priority and dependencies
      const sortedSteps = this.topologicalSort(steps);

      // Calculate total effort and completion target
      const totalEffort = sortedSteps.reduce(
        (sum, step) => sum + step.estimatedEffort,
        0
      );
      const completionTarget = this.calculateCompletionTarget(
        totalEffort,
        assessment.riskScore || 0
      );

      const planId = `plan-${Date.now()}`;
      const plan: RemediationPlan = {
        id: planId,
        assessmentId,
        steps: sortedSteps,
        totalEffort,
        completionTarget,
        riskScore: assessment.riskScore || 0,
        status: 'draft',
      };

      // Save remediation plan
      await this.prisma.remediationPlan.create({
        data: {
          id: planId,
          assessmentId,
          steps: sortedSteps as unknown,
          status: 'draft',
          totalEffort,
          completionTarget,
        },
      });

      return plan;
    } catch (error) {
      throw new Error(
        `Failed to generate remediation plan: ${error instanceof Error ? error.message : 'unknown error'
        }`
      );
    }
  }

  /**
   * Generates compliance recommendations based on assessment gaps.
   *
   * <p><b>Purpose</b><br>
   * Analyzes identified gaps and generates prioritized, phased recommendations
   * for implementing missing controls or improving existing ones.
   *
   * @param assessmentId - The assessment ID
   * @returns Promise<ComplianceRecommendation[]> - List of recommendations
   */
  async generateRecommendations(
    assessmentId: string
  ): Promise<ComplianceRecommendation[]> {
    const assessment = await this.prisma.complianceAssessment.findUnique({
      where: { id: assessmentId },
    });

    if (!assessment) {
      throw new Error(`Assessment not found: ${assessmentId}`);
    }

    const recommendations: ComplianceRecommendation[] = [];

    for (const gap of (assessment.gaps as unknown[]) || []) {
      const recommendation: ComplianceRecommendation = {
        id: `rec-${Date.now()}-${Math.random()}`,
        type: this.inferRecommendationType(gap),
        title: `Implement ${gap.controlId} control`,
        description: gap.description || '',
        framework: gap.frameworkId || 'unknown',
        impactScore: this.calculateImpactScore(gap),
        implementationCost: this.estimateImplementationCost(gap),
        expectedBenefit: this.generateBenefitStatement(gap),
        precedingControls: gap.dependencies || [],
      };

      recommendations.push(recommendation);
    }

    // Sort by impact score descending
    return recommendations.sort(
      (a, b) => b.impactScore - a.impactScore
    );
  }

  /**
   * Tracks remediation progress and updates step status.
   *
   * <p><b>Purpose</b><br>
   * Records progress on remediation steps, updates evidence, and automatically
   * triggers dependent steps when prerequisites are completed.
   *
   * @param stepId - The remediation step ID
   * @param status - New status for the step
   * @param evidence - Optional evidence file paths
   * @returns Promise<RemediationStep> - Updated step
   */
  async updateRemediationProgress(
    stepId: string,
    status: RemediationStep['status'],
    evidence?: string[]
  ): Promise<RemediationStep> {
    const step = await this.prisma.remediationStep.findUnique({
      where: { id: stepId },
      include: { dependents: true },
    });

    if (!step) {
      throw new Error(`Remediation step not found: ${stepId}`);
    }

    // Update step status and evidence
    const updatedStep = await this.prisma.remediationStep.update({
      where: { id: stepId },
      data: {
        status,
        evidence: evidence ? [...(step.evidence || []), ...evidence] : undefined,
        updatedAt: new Date(),
      },
    });

    // If step is completed, trigger dependent steps
    if (status === 'completed' && step.dependents) {
      for (const dependentStep of step.dependents) {
        // Check if all dependencies are satisfied
        const allDependenciesMet = await this.checkDependenciesMet(
          dependentStep.id
        );
        if (allDependenciesMet) {
          await this.prisma.remediationStep.update({
            where: { id: dependentStep.id },
            data: { status: 'open' }, // Ready to start
          });
        }
      }
    }

    return updatedStep as unknown as RemediationStep;
  }

  /**
   * Generates compliance report based on assessment and remediation progress.
   *
   * <p><b>Purpose</b><br>
   * Creates comprehensive compliance report including assessment findings,
   * remediation status, metrics, and audit trail.
   *
   * @param assessmentId - The assessment ID
   * @returns Promise<object> - Formatted compliance report
   */
  async generateReport(assessmentId: string): Promise<object> {
    const assessment = await this.prisma.complianceAssessment.findUnique({
      where: { id: assessmentId },
      include: {
        remediationPlans: {
          include: { steps: true },
          orderBy: { createdAt: 'desc' },
          take: 1,
        },
      },
    });

    if (!assessment) {
      throw new Error(`Assessment not found: ${assessmentId}`);
    }

    const plan = assessment.remediationPlans?.[0];
    const completedSteps = plan?.steps.filter(
      (s: unknown) => s.status === 'completed'
    ).length || 0;
    const totalSteps = plan?.steps.length || 0;
    const completionPercentage =
      totalSteps > 0 ? (completedSteps / totalSteps) * 100 : 0;

    return {
      id: `report-${Date.now()}`,
      assessmentId,
      generatedAt: new Date(),
      framework: assessment.framework,
      overallScore: assessment.riskScore,
      findingsCount: (assessment.findings as unknown[])?.length || 0,
      gapsCount: (assessment.gaps as unknown[])?.length || 0,
      remediationProgress: completionPercentage,
      completedSteps,
      totalSteps,
      controls: assessment.controls,
      auditTrail: assessment.auditTrail,
    };
  }

  /**
   * Groups findings by control ID
   *
   * @private
   */
  private groupFindingsByControl(findings: unknown[]): Map<string, unknown[]> {
    const grouped = new Map<string, unknown[]>();
    for (const finding of findings) {
      const controlId = finding.controlId;
      if (!grouped.has(controlId)) {
        grouped.set(controlId, []);
      }
      grouped.get(controlId)!.push(finding);
    }
    return grouped;
  }

  /**
   * Generates remediation steps from grouped findings
   *
   * @private
   */
  private generateRemediationSteps(
    controlFindings: Map<string, unknown[]>,
    controls: unknown[],
    frameworkId?: string
  ): RemediationStep[] {
    const steps: RemediationStep[] = [];

    for (const [controlId, findings] of controlFindings.entries()) {
      const control = controls.find((c) => c.id === controlId);
      if (!control) continue;

      const priority = this.calculatePriority(findings);
      const step: RemediationStep = {
        id: `step-${Date.now()}-${Math.random()}`,
        controlId,
        title: `Remediate ${control.name} control`,
        description: `Address ${findings.length} findings for control ${controlId}`,
        priority,
        estimatedEffort: this.estimateEffort(findings),
        owner: control.owner || 'unassigned',
        deadline: this.calculateDeadline(priority),
        status: 'open',
        evidence: [],
        dependencies: control.dependencies || [],
      };

      steps.push(step);
    }

    return steps;
  }

  /**
   * Topologically sorts remediation steps by dependencies
   *
   * @private
   */
  private topologicalSort(steps: RemediationStep[]): RemediationStep[] {
    const visited = new Set<string>();
    const result: RemediationStep[] = [];

    const visit = (stepId: string) => {
      if (visited.has(stepId)) return;
      visited.add(stepId);

      const step = steps.find((s) => s.id === stepId);
      if (!step) return;

      for (const depId of step.dependencies) {
        visit(depId);
      }

      result.push(step);
    };

    for (const step of steps) {
      visit(step.id);
    }

    return result;
  }

  /**
   * Calculates completion target date based on effort and risk
   *
   * @private
   */
  private calculateCompletionTarget(effort: number, risk: number): Date {
    const baseDate = new Date();
    // Assume 8 hours work per day, urgent if risk > 70
    const daysToAdd = risk > 70 ? Math.ceil(effort / 8) : Math.ceil(effort / 5);
    baseDate.setDate(baseDate.getDate() + daysToAdd);
    return baseDate;
  }

  /**
   * Calculates priority level from findings
   *
   * @private
   */
  private calculatePriority(
    findings: unknown[]
  ): RemediationStep['priority'] {
    const riskLevels = findings.map((f) => f.severity || 'medium');
    if (riskLevels.includes('critical')) return 'critical';
    if (riskLevels.includes('high')) return 'high';
    if (riskLevels.includes('medium')) return 'medium';
    return 'low';
  }

  /**
   * Estimates effort for findings
   *
   * @private
   */
  private estimateEffort(findings: unknown[]): number {
    const baseEffort = 4;
    return baseEffort + findings.length * 2;
  }

  /**
   * Calculates deadline based on priority
   *
   * @private
   */
  private calculateDeadline(priority: string): Date {
    const now = new Date();
    const daysMap = {
      critical: 3,
      high: 7,
      medium: 14,
      low: 30,
    };
    const days =
      daysMap[priority as keyof typeof daysMap] ||
      daysMap['low'];
    now.setDate(now.getDate() + days);
    return now;
  }

  /**
   * Infers recommendation type from gap
   *
   * @private
   */
  private inferRecommendationType(
    gap: unknown
  ): ComplianceRecommendation['type'] {
    const description = gap.description || '';
    if (description.includes('process'))
      return 'process';
    if (description.includes('technology'))
      return 'technology';
    if (description.includes('training'))
      return 'training';
    return 'control';
  }

  /**
   * Calculates impact score for a gap
   *
   * @private
   */
  private calculateImpactScore(gap: unknown): number {
    return Math.min(100, 40 + (gap.severity || 5) * 12);
  }

  /**
   * Estimates implementation cost
   *
   * @private
   */
  private estimateImplementationCost(gap: unknown): number {
    return 5000 + Math.random() * 15000;
  }

  /**
   * Generates benefit statement
   *
   * @private
   */
  private generateBenefitStatement(gap: unknown): string {
    return `Improve ${gap.framework || 'compliance'} posture and reduce risk exposure`;
  }

  /**
   * Checks if all dependencies are met for a step
   *
   * @private
   */
  private async checkDependenciesMet(stepId: string): Promise<boolean> {
    const step = await this.prisma.remediationStep.findUnique({
      where: { id: stepId },
      include: {
        dependencies: true,
      },
    });

    if (!step || !step.dependencies) return true;

    for (const dep of step.dependencies) {
      if (dep.status !== 'completed') {
        return false;
      }
    }

    return true;
  }
}

