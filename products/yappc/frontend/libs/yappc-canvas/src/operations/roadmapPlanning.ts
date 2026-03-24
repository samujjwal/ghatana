/**
 * Roadmap Planning Module
 *
 * Provides strategic roadmap planning capabilities including:
 * - Strategic initiatives and goals
 * - Milestone tracking with dependencies
 * - Timeline visualization and Gantt charts
 * - Resource allocation to initiatives
 * - Progress tracking and reporting
 * - Risk assessment and mitigation
 * - Stakeholder management
 */

// ============================================================================
// Types and Interfaces
// ============================================================================

/**
 *
 */
export type InitiativeStatus =
  | 'planned'
  | 'in-progress'
  | 'on-hold'
  | 'completed'
  | 'cancelled';

/**
 *
 */
export type InitiativePriority = 'critical' | 'high' | 'medium' | 'low';

/**
 *
 */
export type MilestoneStatus =
  | 'not-started'
  | 'in-progress'
  | 'at-risk'
  | 'completed'
  | 'missed';

/**
 *
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 *
 */
export type TimeframeType = 'quarter' | 'half' | 'year' | 'custom';

/**
 *
 */
export interface StrategicGoal {
  id: string;
  title: string;
  description: string;
  targetDate: Date;
  metrics: GoalMetric[];
  owner: string;
  status: 'active' | 'achieved' | 'abandoned';
  achievedDate?: Date;
  createdAt: Date;
}

/**
 *
 */
export interface GoalMetric {
  name: string;
  target: number;
  current: number;
  unit: string;
}

/**
 *
 */
export interface Initiative {
  id: string;
  title: string;
  description: string;
  goalIds: string[];
  priority: InitiativePriority;
  status: InitiativeStatus;
  startDate: Date;
  targetDate: Date;
  completedDate?: Date;
  owner: string;
  team: string[];
  budget?: number;
  estimatedEffort: number; // person-days
  actualEffort?: number;
  tags: string[];
  createdAt: Date;
  updatedAt: Date;
}

/**
 *
 */
export interface Milestone {
  id: string;
  initiativeId: string;
  title: string;
  description: string;
  targetDate: Date;
  completedDate?: Date;
  status: MilestoneStatus;
  dependencies: string[]; // milestone IDs
  deliverables: string[];
  successCriteria: string[];
  owner: string;
  progress: number; // 0-100
  createdAt: Date;
}

/**
 *
 */
export interface Dependency {
  id: string;
  sourceId: string; // milestone or initiative ID
  targetId: string; // milestone or initiative ID
  type: 'blocks' | 'depends-on' | 'related';
  description?: string;
  createdAt: Date;
}

/**
 *
 */
export interface Timeframe {
  id: string;
  name: string;
  type: TimeframeType;
  startDate: Date;
  endDate: Date;
  goals: string[];
  initiativeIds: string[];
  description?: string;
}

/**
 *
 */
export interface RiskAssessment {
  id: string;
  initiativeId: string;
  title: string;
  description: string;
  level: RiskLevel;
  probability: number; // 0-100
  impact: number; // 0-100
  mitigation: string;
  owner: string;
  status: 'identified' | 'mitigating' | 'mitigated' | 'accepted';
  identifiedDate: Date;
  mitigatedDate?: Date;
}

/**
 *
 */
export interface Stakeholder {
  id: string;
  name: string;
  role: string;
  email: string;
  influence: 'low' | 'medium' | 'high';
  interest: 'low' | 'medium' | 'high';
  initiativeIds: string[];
  communicationPreference: 'email' | 'slack' | 'meeting';
  lastContactDate?: Date;
}

/**
 *
 */
export interface RoadmapReport {
  timeframe: string;
  totalInitiatives: number;
  initiativesByStatus: Record<InitiativeStatus, number>;
  initiativesByPriority: Record<InitiativePriority, number>;
  completedMilestones: number;
  upcomingMilestones: number;
  atRiskMilestones: number;
  totalRisks: number;
  risksByLevel: Record<RiskLevel, number>;
  averageProgress: number;
  onTrackInitiatives: number;
  delayedInitiatives: number;
  budgetUtilization?: number;
}

/**
 *
 */
export interface TimelineItem {
  id: string;
  type: 'initiative' | 'milestone' | 'goal';
  title: string;
  startDate: Date;
  endDate: Date;
  status: string;
  progress: number;
  dependencies: string[];
}

/**
 *
 */
export interface RoadmapPlanningConfig {
  defaultTimeframeDays: number;
  riskScoreThreshold: number;
  atRiskDaysThreshold: number;
  retentionDays: number;
}

// ============================================================================
// Roadmap Planning Manager
// ============================================================================

/**
 *
 */
export class RoadmapPlanningManager {
  private goals = new Map<string, StrategicGoal>();
  private initiatives = new Map<string, Initiative>();
  private milestones = new Map<string, Milestone>();
  private dependencies = new Map<string, Dependency>();
  private timeframes = new Map<string, Timeframe>();
  private risks = new Map<string, RiskAssessment>();
  private stakeholders = new Map<string, Stakeholder>();

  private goalCounter = 0;
  private initiativeCounter = 0;
  private milestoneCounter = 0;
  private dependencyCounter = 0;
  private timeframeCounter = 0;
  private riskCounter = 0;
  private stakeholderCounter = 0;

  private config: RoadmapPlanningConfig;

  /**
   *
   */
  constructor(config?: Partial<RoadmapPlanningConfig>) {
    this.config = {
      defaultTimeframeDays: 90,
      riskScoreThreshold: 60,
      atRiskDaysThreshold: 7,
      retentionDays: 730,
      ...config,
    };
  }

  // ============================================================================
  // Strategic Goals
  // ============================================================================

  /**
   *
   */
  createStrategicGoal(
    data: Omit<StrategicGoal, 'id' | 'createdAt' | 'status'>,
  ): StrategicGoal {
    const goal: StrategicGoal = {
      ...data,
      id: `goal-${Date.now()}-${++this.goalCounter}`,
      status: 'active',
      createdAt: new Date(),
    };

    this.goals.set(goal.id, goal);
    return goal;
  }

  /**
   *
   */
  getStrategicGoal(id: string): StrategicGoal | undefined {
    return this.goals.get(id);
  }

  /**
   *
   */
  getAllStrategicGoals(): StrategicGoal[] {
    return Array.from(this.goals.values());
  }

  /**
   *
   */
  getActiveGoals(): StrategicGoal[] {
    return Array.from(this.goals.values()).filter((g) => g.status === 'active');
  }

  /**
   *
   */
  updateGoalMetric(goalId: string, metricName: string, currentValue: number): void {
    const goal = this.goals.get(goalId);
    if (!goal) return;

    const metric = goal.metrics.find((m) => m.name === metricName);
    if (metric) {
      metric.current = currentValue;
    }
  }

  /**
   *
   */
  achieveGoal(goalId: string): void {
    const goal = this.goals.get(goalId);
    if (goal) {
      goal.status = 'achieved';
      goal.achievedDate = new Date();
    }
  }

  // ============================================================================
  // Initiatives
  // ============================================================================

  /**
   *
   */
  createInitiative(data: Omit<Initiative, 'id' | 'createdAt' | 'updatedAt'>): Initiative {
    const initiative: Initiative = {
      ...data,
      id: `init-${Date.now()}-${++this.initiativeCounter}`,
      createdAt: new Date(),
      updatedAt: new Date(),
    };

    this.initiatives.set(initiative.id, initiative);
    return initiative;
  }

  /**
   *
   */
  getInitiative(id: string): Initiative | undefined {
    return this.initiatives.get(id);
  }

  /**
   *
   */
  getAllInitiatives(): Initiative[] {
    return Array.from(this.initiatives.values());
  }

  /**
   *
   */
  getInitiativesByGoal(goalId: string): Initiative[] {
    return Array.from(this.initiatives.values()).filter((i) =>
      i.goalIds.includes(goalId),
    );
  }

  /**
   *
   */
  getInitiativesByStatus(status: InitiativeStatus): Initiative[] {
    return Array.from(this.initiatives.values()).filter((i) => i.status === status);
  }

  /**
   *
   */
  getInitiativesByPriority(priority: InitiativePriority): Initiative[] {
    return Array.from(this.initiatives.values()).filter((i) => i.priority === priority);
  }

  /**
   *
   */
  updateInitiativeStatus(id: string, status: InitiativeStatus): void {
    const initiative = this.initiatives.get(id);
    if (initiative) {
      initiative.status = status;
      initiative.updatedAt = new Date();
      if (status === 'completed') {
        initiative.completedDate = new Date();
      }
    }
  }

  /**
   *
   */
  updateInitiativeProgress(id: string, actualEffort: number): void {
    const initiative = this.initiatives.get(id);
    if (initiative) {
      initiative.actualEffort = actualEffort;
      initiative.updatedAt = new Date();
    }
  }

  // ============================================================================
  // Milestones
  // ============================================================================

  /**
   *
   */
  createMilestone(data: Omit<Milestone, 'id' | 'createdAt'>): Milestone {
    const milestone: Milestone = {
      ...data,
      id: `milestone-${Date.now()}-${++this.milestoneCounter}`,
      createdAt: new Date(),
    };

    this.milestones.set(milestone.id, milestone);
    return milestone;
  }

  /**
   *
   */
  getMilestone(id: string): Milestone | undefined {
    return this.milestones.get(id);
  }

  /**
   *
   */
  getMilestonesByInitiative(initiativeId: string): Milestone[] {
    return Array.from(this.milestones.values()).filter(
      (m) => m.initiativeId === initiativeId,
    );
  }

  /**
   *
   */
  getUpcomingMilestones(daysAhead: number = 30): Milestone[] {
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + daysAhead);

    return Array.from(this.milestones.values())
      .filter(
        (m) =>
          m.status !== 'completed' &&
          m.status !== 'missed' &&
          m.targetDate <= futureDate &&
          m.targetDate >= new Date(),
      )
      .sort((a, b) => a.targetDate.getTime() - b.targetDate.getTime());
  }

  /**
   *
   */
  getAtRiskMilestones(): Milestone[] {
    return Array.from(this.milestones.values()).filter((m) => m.status === 'at-risk');
  }

  /**
   *
   */
  updateMilestoneProgress(id: string, progress: number): void {
    const milestone = this.milestones.get(id);
    if (!milestone) return;

    milestone.progress = Math.max(0, Math.min(100, progress));

    // Auto-update status based on progress and dates
    const now = new Date();
    const daysUntilTarget =
      (milestone.targetDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);

    if (milestone.progress === 100) {
      milestone.status = 'completed';
      milestone.completedDate = new Date();
    } else if (milestone.targetDate < now) {
      milestone.status = 'missed';
    } else if (
      daysUntilTarget < this.config.atRiskDaysThreshold &&
      milestone.progress < 80
    ) {
      milestone.status = 'at-risk';
    } else if (milestone.status === 'not-started' && milestone.progress > 0) {
      milestone.status = 'in-progress';
    }
  }

  /**
   *
   */
  completeMilestone(id: string): void {
    const milestone = this.milestones.get(id);
    if (milestone) {
      milestone.status = 'completed';
      milestone.completedDate = new Date();
      milestone.progress = 100;
    }
  }

  // ============================================================================
  // Dependencies
  // ============================================================================

  /**
   *
   */
  createDependency(
    data: Omit<Dependency, 'id' | 'createdAt'>,
  ): Dependency {
    const dependency: Dependency = {
      ...data,
      id: `dep-${Date.now()}-${++this.dependencyCounter}`,
      createdAt: new Date(),
    };

    this.dependencies.set(dependency.id, dependency);
    return dependency;
  }

  /**
   *
   */
  getDependency(id: string): Dependency | undefined {
    return this.dependencies.get(id);
  }

  /**
   *
   */
  getDependenciesFor(itemId: string): Dependency[] {
    return Array.from(this.dependencies.values()).filter(
      (d) => d.sourceId === itemId || d.targetId === itemId,
    );
  }

  /**
   *
   */
  getBlockedBy(itemId: string): Dependency[] {
    return Array.from(this.dependencies.values()).filter(
      (d) => d.targetId === itemId && d.type === 'blocks',
    );
  }

  /**
   *
   */
  removeDependency(id: string): void {
    this.dependencies.delete(id);
  }

  // ============================================================================
  // Timeframes
  // ============================================================================

  /**
   *
   */
  createTimeframe(data: Omit<Timeframe, 'id'>): Timeframe {
    const timeframe: Timeframe = {
      ...data,
      id: `timeframe-${Date.now()}-${++this.timeframeCounter}`,
    };

    this.timeframes.set(timeframe.id, timeframe);
    return timeframe;
  }

  /**
   *
   */
  getTimeframe(id: string): Timeframe | undefined {
    return this.timeframes.get(id);
  }

  /**
   *
   */
  getAllTimeframes(): Timeframe[] {
    return Array.from(this.timeframes.values()).sort(
      (a, b) => a.startDate.getTime() - b.startDate.getTime(),
    );
  }

  /**
   *
   */
  getCurrentTimeframe(): Timeframe | undefined {
    const now = new Date();
    return Array.from(this.timeframes.values()).find(
      (t) => t.startDate <= now && t.endDate >= now,
    );
  }

  /**
   *
   */
  addInitiativeToTimeframe(timeframeId: string, initiativeId: string): void {
    const timeframe = this.timeframes.get(timeframeId);
    if (timeframe && !timeframe.initiativeIds.includes(initiativeId)) {
      timeframe.initiativeIds.push(initiativeId);
    }
  }

  // ============================================================================
  // Risk Assessment
  // ============================================================================

  /**
   *
   */
  createRiskAssessment(
    data: Omit<RiskAssessment, 'id' | 'identifiedDate'>,
  ): RiskAssessment {
    const risk: RiskAssessment = {
      ...data,
      id: `risk-${Date.now()}-${++this.riskCounter}`,
      identifiedDate: new Date(),
    };

    this.risks.set(risk.id, risk);
    return risk;
  }

  /**
   *
   */
  getRiskAssessment(id: string): RiskAssessment | undefined {
    return this.risks.get(id);
  }

  /**
   *
   */
  getRisksByInitiative(initiativeId: string): RiskAssessment[] {
    return Array.from(this.risks.values()).filter(
      (r) => r.initiativeId === initiativeId,
    );
  }

  /**
   *
   */
  getHighRisks(): RiskAssessment[] {
    return Array.from(this.risks.values()).filter(
      (r) => (r.level === 'high' || r.level === 'critical') && r.status !== 'mitigated',
    );
  }

  /**
   *
   */
  updateRiskStatus(
    id: string,
    status: RiskAssessment['status'],
  ): void {
    const risk = this.risks.get(id);
    if (risk) {
      risk.status = status;
      if (status === 'mitigated') {
        risk.mitigatedDate = new Date();
      }
    }
  }

  /**
   *
   */
  calculateRiskScore(risk: RiskAssessment): number {
    return (risk.probability * risk.impact) / 100;
  }

  // ============================================================================
  // Stakeholders
  // ============================================================================

  /**
   *
   */
  createStakeholder(data: Omit<Stakeholder, 'id'>): Stakeholder {
    const stakeholder: Stakeholder = {
      ...data,
      id: `stakeholder-${Date.now()}-${++this.stakeholderCounter}`,
    };

    this.stakeholders.set(stakeholder.id, stakeholder);
    return stakeholder;
  }

  /**
   *
   */
  getStakeholder(id: string): Stakeholder | undefined {
    return this.stakeholders.get(id);
  }

  /**
   *
   */
  getStakeholdersByInitiative(initiativeId: string): Stakeholder[] {
    return Array.from(this.stakeholders.values()).filter((s) =>
      s.initiativeIds.includes(initiativeId),
    );
  }

  /**
   *
   */
  getHighInfluenceStakeholders(): Stakeholder[] {
    return Array.from(this.stakeholders.values()).filter((s) => s.influence === 'high');
  }

  /**
   *
   */
  updateStakeholderContact(id: string): void {
    const stakeholder = this.stakeholders.get(id);
    if (stakeholder) {
      stakeholder.lastContactDate = new Date();
    }
  }

  // ============================================================================
  // Timeline and Visualization
  // ============================================================================

  /**
   *
   */
  generateTimeline(): TimelineItem[] {
    const items: TimelineItem[] = [];

    // Add goals
    for (const goal of this.goals.values()) {
      if (goal.status === 'active') {
        const progress = this.calculateGoalProgress(goal);
        items.push({
          id: goal.id,
          type: 'goal',
          title: goal.title,
          startDate: goal.createdAt,
          endDate: goal.targetDate,
          status: goal.status,
          progress,
          dependencies: [],
        });
      }
    }

    // Add initiatives
    for (const initiative of this.initiatives.values()) {
      if (initiative.status !== 'cancelled') {
        const progress = this.calculateInitiativeProgress(initiative.id);
        items.push({
          id: initiative.id,
          type: 'initiative',
          title: initiative.title,
          startDate: initiative.startDate,
          endDate: initiative.completedDate || initiative.targetDate,
          status: initiative.status,
          progress,
          dependencies: initiative.goalIds,
        });
      }
    }

    // Add milestones
    for (const milestone of this.milestones.values()) {
      if (milestone.status !== 'missed') {
        items.push({
          id: milestone.id,
          type: 'milestone',
          title: milestone.title,
          startDate: milestone.createdAt,
          endDate: milestone.completedDate || milestone.targetDate,
          status: milestone.status,
          progress: milestone.progress,
          dependencies: [milestone.initiativeId, ...milestone.dependencies],
        });
      }
    }

    return items.sort((a, b) => a.startDate.getTime() - b.startDate.getTime());
  }

  /**
   *
   */
  generateGanttData(timeframeId?: string): TimelineItem[] {
    let items = this.generateTimeline();

    if (timeframeId) {
      const timeframe = this.timeframes.get(timeframeId);
      if (timeframe) {
        items = items.filter(
          (item) =>
            item.startDate <= timeframe.endDate && item.endDate >= timeframe.startDate,
        );
      }
    }

    return items;
  }

  // ============================================================================
  // Reporting
  // ============================================================================

  /**
   *
   */
  generateRoadmapReport(timeframeId?: string): RoadmapReport {
    let initiatives = Array.from(this.initiatives.values());
    let milestones = Array.from(this.milestones.values());
    let risks = Array.from(this.risks.values());

    let timeframeName = 'All Time';

    if (timeframeId) {
      const timeframe = this.timeframes.get(timeframeId);
      if (timeframe) {
        timeframeName = timeframe.name;
        initiatives = initiatives.filter((i) =>
          timeframe.initiativeIds.includes(i.id),
        );
        const initiativeIds = new Set(initiatives.map((i) => i.id));
        milestones = milestones.filter((m) => initiativeIds.has(m.initiativeId));
        risks = risks.filter((r) => initiativeIds.has(r.initiativeId));
      }
    }

    const initiativesByStatus: Record<InitiativeStatus, number> = {
      planned: 0,
      'in-progress': 0,
      'on-hold': 0,
      completed: 0,
      cancelled: 0,
    };

    const initiativesByPriority: Record<InitiativePriority, number> = {
      critical: 0,
      high: 0,
      medium: 0,
      low: 0,
    };

    const risksByLevel: Record<RiskLevel, number> = {
      low: 0,
      medium: 0,
      high: 0,
      critical: 0,
    };

    for (const initiative of initiatives) {
      initiativesByStatus[initiative.status]++;
      initiativesByPriority[initiative.priority]++;
    }

    for (const risk of risks) {
      risksByLevel[risk.level]++;
    }

    const completedMilestones = milestones.filter((m) => m.status === 'completed').length;
    const upcomingMilestones = milestones.filter(
      (m) =>
        m.status !== 'completed' &&
        m.status !== 'missed' &&
        m.targetDate > new Date(),
    ).length;
    const atRiskMilestones = milestones.filter((m) => m.status === 'at-risk').length;

    const totalProgress = initiatives.reduce(
      (sum, init) => sum + this.calculateInitiativeProgress(init.id),
      0,
    );
    const averageProgress =
      initiatives.length > 0 ? totalProgress / initiatives.length : 0;

    const now = new Date();
    const onTrackInitiatives = initiatives.filter((init) => {
      if (init.status === 'completed') return true;
      if (init.status === 'cancelled' || init.status === 'on-hold') return false;
      const progress = this.calculateInitiativeProgress(init.id);
      const timeElapsed = now.getTime() - init.startDate.getTime();
      const totalTime = init.targetDate.getTime() - init.startDate.getTime();
      const expectedProgress = (timeElapsed / totalTime) * 100;
      return progress >= expectedProgress - 10; // 10% tolerance
    }).length;

    const delayedInitiatives = initiatives.filter((init) => {
      if (init.status !== 'in-progress') return false;
      const progress = this.calculateInitiativeProgress(init.id);
      const timeElapsed = now.getTime() - init.startDate.getTime();
      const totalTime = init.targetDate.getTime() - init.startDate.getTime();
      const expectedProgress = (timeElapsed / totalTime) * 100;
      return progress < expectedProgress - 10;
    }).length;

    const totalBudget = initiatives.reduce((sum, init) => sum + (init.budget || 0), 0);
    const spentBudget = initiatives.reduce((sum, init) => {
      if (!init.budget || !init.actualEffort) return sum;
      const estimatedCost = (init.actualEffort / init.estimatedEffort) * init.budget;
      return sum + estimatedCost;
    }, 0);

    const budgetUtilization = totalBudget > 0 ? (spentBudget / totalBudget) * 100 : undefined;

    return {
      timeframe: timeframeName,
      totalInitiatives: initiatives.length,
      initiativesByStatus,
      initiativesByPriority,
      completedMilestones,
      upcomingMilestones,
      atRiskMilestones,
      totalRisks: risks.length,
      risksByLevel,
      averageProgress,
      onTrackInitiatives,
      delayedInitiatives,
      budgetUtilization,
    };
  }

  // ============================================================================
  // Progress Calculations
  // ============================================================================

  /**
   *
   */
  calculateInitiativeProgress(initiativeId: string): number {
    const milestones = this.getMilestonesByInitiative(initiativeId);
    if (milestones.length === 0) {
      const initiative = this.initiatives.get(initiativeId);
      if (!initiative) return 0;
      if (initiative.status === 'completed') return 100;
      if (initiative.actualEffort && initiative.estimatedEffort) {
        return Math.min(
          100,
          (initiative.actualEffort / initiative.estimatedEffort) * 100,
        );
      }
      return 0;
    }

    const totalProgress = milestones.reduce((sum, m) => sum + m.progress, 0);
    return totalProgress / milestones.length;
  }

  /**
   *
   */
  calculateGoalProgress(goal: StrategicGoal): number {
    if (goal.metrics.length === 0) return 0;

    const totalProgress = goal.metrics.reduce((sum, metric) => {
      const progress = metric.target > 0 ? (metric.current / metric.target) * 100 : 0;
      return sum + Math.min(100, progress);
    }, 0);

    return totalProgress / goal.metrics.length;
  }

  // ============================================================================
  // Cleanup and Reset
  // ============================================================================

  /**
   *
   */
  cleanupOldData(): void {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - this.config.retentionDays);

    // Remove old completed initiatives
    for (const [id, initiative] of this.initiatives.entries()) {
      if (
        initiative.status === 'completed' &&
        initiative.completedDate &&
        initiative.completedDate < cutoffDate
      ) {
        this.initiatives.delete(id);

        // Remove associated milestones
        for (const [mId, milestone] of this.milestones.entries()) {
          if (milestone.initiativeId === id) {
            this.milestones.delete(mId);
          }
        }

        // Remove associated risks
        for (const [rId, risk] of this.risks.entries()) {
          if (risk.initiativeId === id) {
            this.risks.delete(rId);
          }
        }
      }
    }

    // Remove achieved goals older than retention period
    for (const [id, goal] of this.goals.entries()) {
      if (
        goal.status === 'achieved' &&
        goal.achievedDate &&
        goal.achievedDate < cutoffDate
      ) {
        this.goals.delete(id);
      }
    }
  }

  /**
   *
   */
  reset(): void {
    this.goals.clear();
    this.initiatives.clear();
    this.milestones.clear();
    this.dependencies.clear();
    this.timeframes.clear();
    this.risks.clear();
    this.stakeholders.clear();

    this.goalCounter = 0;
    this.initiativeCounter = 0;
    this.milestoneCounter = 0;
    this.dependencyCounter = 0;
    this.timeframeCounter = 0;
    this.riskCounter = 0;
    this.stakeholderCounter = 0;
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

/**
 *
 */
export function calculateTimeToTarget(targetDate: Date): number {
  const now = new Date();
  const diff = targetDate.getTime() - now.getTime();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

/**
 *
 */
export function isOverdue(targetDate: Date): boolean {
  return targetDate < new Date();
}

/**
 *
 */
export function calculateBurnRate(
  estimatedEffort: number,
  actualEffort: number,
  daysElapsed: number,
): number {
  if (daysElapsed === 0) return 0;
  return actualEffort / daysElapsed;
}

/**
 *
 */
export function estimateCompletionDate(
  startDate: Date,
  estimatedEffort: number,
  actualEffort: number,
  burnRate: number,
): Date | null {
  if (burnRate === 0) return null;
  const remainingEffort = estimatedEffort - actualEffort;
  const daysRemaining = remainingEffort / burnRate;
  const completionDate = new Date(startDate);
  completionDate.setDate(completionDate.getDate() + daysRemaining);
  return completionDate;
}

/**
 *
 */
export function formatTimeframe(timeframe: Timeframe): string {
  const start = timeframe.startDate.toLocaleDateString();
  const end = timeframe.endDate.toLocaleDateString();
  return `${timeframe.name} (${start} - ${end})`;
}

/**
 *
 */
export function calculateCriticalPath(
  milestones: Milestone[],
  dependencies: Dependency[],
): string[] {
  // Simple critical path calculation (can be enhanced with more sophisticated algorithm)
  const milestoneMap = new Map(milestones.map((m) => [m.id, m]));
  const dependencyMap = new Map<string, string[]>();

  // Build dependency graph
  for (const dep of dependencies) {
    if (dep.type === 'blocks' || dep.type === 'depends-on') {
      if (!dependencyMap.has(dep.targetId)) {
        dependencyMap.set(dep.targetId, []);
      }
      dependencyMap.get(dep.targetId)!.push(dep.sourceId);
    }
  }

  // Find longest path (simplified)
  const criticalPath: string[] = [];
  let currentMilestone: Milestone | undefined;

  // Start with the milestone with the latest target date
  const sorted = [...milestones].sort(
    (a, b) => b.targetDate.getTime() - a.targetDate.getTime(),
  );

  if (sorted.length > 0) {
    currentMilestone = sorted[0];
    criticalPath.push(currentMilestone!.id);

    // Walk back through dependencies
    while (currentMilestone) {
      const deps = dependencyMap.get(currentMilestone.id);
      if (!deps || deps.length === 0) break;

      // Pick the dependency with the longest chain
      const depMilestones = deps
        .map((id) => milestoneMap.get(id))
        .filter((m): m is Milestone => m !== undefined);

      if (depMilestones.length === 0) break;

      currentMilestone = depMilestones.sort(
        (a, b) => b.targetDate.getTime() - a.targetDate.getTime(),
      )[0];

      if (currentMilestone) {
        criticalPath.unshift(currentMilestone.id);
      }
    }
  }

  return criticalPath;
}

/**
 *
 */
export function prioritizeInitiatives(
  initiatives: Initiative[],
): Initiative[] {
  const priorityScore: Record<InitiativePriority, number> = {
    critical: 4,
    high: 3,
    medium: 2,
    low: 1,
  };

  return [...initiatives].sort((a, b) => {
    // First by priority
    const priorityDiff = priorityScore[b.priority] - priorityScore[a.priority];
    if (priorityDiff !== 0) return priorityDiff;

    // Then by target date (earlier first)
    return a.targetDate.getTime() - b.targetDate.getTime();
  });
}

/**
 *
 */
export function identifyBottlenecks(
  milestones: Milestone[],
  dependencies: Dependency[],
): string[] {
  // Find milestones that are blocking multiple others
  const blockingCount = new Map<string, number>();

  for (const dep of dependencies) {
    if (dep.type === 'blocks') {
      blockingCount.set(dep.sourceId, (blockingCount.get(dep.sourceId) || 0) + 1);
    }
  }

  const bottlenecks: string[] = [];
  for (const [milestoneId, count] of blockingCount.entries()) {
    if (count >= 2) {
      // Blocking 2 or more milestones
      const milestone = milestones.find((m) => m.id === milestoneId);
      if (milestone && milestone.status !== 'completed') {
        bottlenecks.push(milestoneId);
      }
    }
  }

  return bottlenecks;
}
