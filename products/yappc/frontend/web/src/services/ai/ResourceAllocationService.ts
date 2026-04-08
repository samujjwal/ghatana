/**
 * Resource Allocation Service
 *
 * Provides AI-powered resource allocation and capacity planning.
 * Includes team assignment suggestions and workload prediction.
 *
 * @doc.type service
 * @doc.purpose AI-powered resource allocation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface TeamMember {
  id: string;
  name: string;
  role: string;
  skills: string[];
  currentWorkload: number; // 0-100
  availability: number; // hours per week
  efficiency: number; // 0-1
}

export interface TaskRequirement {
  taskId: string;
  title: string;
  requiredSkills: string[];
  estimatedHours: number;
  priority: 'high' | 'medium' | 'low';
  deadline?: string;
  dependencies?: string[];
}

export interface ResourceAllocation {
  taskId: string;
  assignedMemberId: string;
  confidence: number;
  reasoning: string;
  estimatedCompletion: string;
}

export interface CapacityPlan {
  teamId: string;
  period: string;
  totalCapacity: number;
  allocated: number;
  available: number;
  utilizationRate: number;
  overloadRisk: 'low' | 'medium' | 'high';
  recommendations: string[];
}

export interface AllocationRequest {
  tasks: TaskRequirement[];
  teamMembers: TeamMember[];
  context?: {
    projectId?: string;
    timeframe?: string;
    preferences?: Record<string, unknown>;
  };
}

export interface AllocationResponse {
  allocations: ResourceAllocation[];
  capacityPlan: CapacityPlan;
  metadata: {
    timestamp: string;
    algorithm: 'ai-hybrid';
  };
}

// ============================================================================
// Allocation Algorithms
// ============================================================================

/**
 * Calculate skill match score between task and team member
 */
function calculateSkillMatch(taskSkills: string[], memberSkills: string[]): number {
  if (taskSkills.length === 0) return 0.5; // Neutral if no skills required

  const matches = taskSkills.filter(skill => 
    memberSkills.some(memberSkill => 
      memberSkill.toLowerCase().includes(skill.toLowerCase()) ||
      skill.toLowerCase().includes(memberSkill.toLowerCase())
    )
  );

  return matches.length / taskSkills.length;
}

/**
 * Calculate workload factor (prefer members with lower workload)
 */
function calculateWorkloadFactor(currentWorkload: number): number {
  // Lower workload = higher factor
  return 1 - (currentWorkload / 100);
}

/**
 * Calculate availability factor
 */
function calculateAvailabilityFactor(availability: number, requiredHours: number): number {
  if (availability >= requiredHours) return 1.0;
  return availability / requiredHours;
}

/**
 * Calculate deadline urgency
 */
function calculateDeadlineUrgency(deadline?: string): number {
  if (!deadline) return 0.5;

  const now = new Date();
  const due = new Date(deadline);
  const daysUntilDue = (due.getTime() - now.getTime()) / (1000 * 60 * 60 * 24);

  if (daysUntilDue < 0) return 1.0;
  if (daysUntilDue <= 1) return 0.9;
  if (daysUntilDue <= 3) return 0.7;
  if (daysUntilDue <= 7) return 0.5;
  if (daysUntilDue <= 14) return 0.3;
  return 0.1;
}

/**
 * Calculate priority weight
 */
function calculatePriorityWeight(priority: TaskRequirement['priority']): number {
  switch (priority) {
    case 'high':
      return 1.0;
    case 'medium':
      return 0.7;
    case 'low':
      return 0.4;
  }
}

/**
 * Generate allocation reasoning
 */
function generateAllocationReasoning(
  task: TaskRequirement,
  member: TeamMember,
  skillMatch: number,
  workloadFactor: number,
  availabilityFactor: number
): string {
  const reasons: string[] = [];

  if (skillMatch > 0.7) {
    reasons.push('strong skill match');
  } else if (skillMatch > 0.4) {
    reasons.push('moderate skill match');
  }

  if (workloadFactor > 0.7) {
    reasons.push('available capacity');
  }

  if (availabilityFactor > 0.8) {
    reasons.push('sufficient availability');
  }

  if (member.efficiency > 0.8) {
    reasons.push('high efficiency');
  }

  if (reasons.length === 0) {
    return 'Best available match based on overall fit';
  }

  return `Recommended due to: ${reasons.join(', ')}`;
}

// ============================================================================
// Capacity Planning
// ============================================================================

/**
 * Calculate capacity utilization for a team
 */
function calculateCapacityUtilization(teamMembers: TeamMember[], taskAllocations: ResourceAllocation[]): number {
  const totalCapacity = teamMembers.reduce((sum, member) => sum + member.availability, 0);
  const totalAllocated = taskAllocations.reduce((sum, allocation) => {
    const task = taskAllocations.find(a => a.taskId === allocation.taskId);
    // This is simplified - in reality we'd look up the task hours
    return sum + 8; // Assume 8 hours per task
  }, 0);

  if (totalCapacity === 0) return 0;
  return totalAllocated / totalCapacity;
}

/**
 * Assess overload risk
 */
function assessOverloadRisk(utilizationRate: number): 'low' | 'medium' | 'high' {
  if (utilizationRate < 0.7) return 'low';
  if (utilizationRate < 0.9) return 'medium';
  return 'high';
}

/**
 * Generate capacity recommendations
 */
function generateCapacityRecommendations(
  utilizationRate: number,
  overloadRisk: 'low' | 'medium' | 'high'
): string[] {
  const recommendations: string[] = [];

  if (overloadRisk === 'high') {
    recommendations.push('Consider redistributing tasks to reduce overload');
    recommendations.push('Evaluate extending timeline or adding resources');
  } else if (overloadRisk === 'medium') {
    recommendations.push('Monitor workload closely');
    recommendations.push('Prepare contingency plans for potential delays');
  }

  if (utilizationRate < 0.5) {
    recommendations.push('Consider taking on additional work');
    recommendations.push('Optimize team utilization');
  }

  if (recommendations.length === 0) {
    recommendations.push('Current allocation is well-balanced');
  }

  return recommendations;
}

// ============================================================================
// Service Implementation
// ============================================================================

/**
 * Allocate tasks to team members using AI-hybrid algorithm
 */
export async function allocateResources(request: AllocationRequest): Promise<AllocationResponse> {
  const { tasks, teamMembers } = request;

  // Calculate scores for each task-member combination
  const allocations: ResourceAllocation[] = [];

  for (const task of tasks) {
    let bestMatch: { memberId: string; score: number } | null = null;

    for (const member of teamMembers) {
      const skillMatch = calculateSkillMatch(task.requiredSkills, member.skills);
      const workloadFactor = calculateWorkloadFactor(member.currentWorkload);
      const availabilityFactor = calculateAvailabilityFactor(member.availability, task.estimatedHours);
      const deadlineUrgency = calculateDeadlineUrgency(task.deadline);
      const priorityWeight = calculatePriorityWeight(task.priority);

      // Calculate weighted score
      const score =
        skillMatch * 0.35 +
        workloadFactor * 0.25 +
        availabilityFactor * 0.20 +
        member.efficiency * 0.15 +
        deadlineUrgency * 0.05 * priorityWeight;

      if (!bestMatch || score > bestMatch.score) {
        bestMatch = { memberId: member.id, score };
      }
    }

    if (bestMatch) {
      const member = teamMembers.find(m => m.id === bestMatch!.memberId);
      if (member) {
        const skillMatch = calculateSkillMatch(task.requiredSkills, member.skills);
        const workloadFactor = calculateWorkloadFactor(member.currentWorkload);
        const availabilityFactor = calculateAvailabilityFactor(member.availability, task.estimatedHours);

        // Estimate completion time
        const estimatedHours = task.estimatedHours / member.efficiency;
        const daysToComplete = Math.ceil(estimatedHours / member.availability * 7);
        const completionDate = new Date();
        completionDate.setDate(completionDate.getDate() + daysToComplete);

        allocations.push({
          taskId: task.taskId,
          assignedMemberId: bestMatch.memberId,
          confidence: bestMatch.score,
          reasoning: generateAllocationReasoning(task, member, skillMatch, workloadFactor, availabilityFactor),
          estimatedCompletion: completionDate.toISOString(),
        });
      }
    }
  }

  // Generate capacity plan
  const utilizationRate = calculateCapacityUtilization(teamMembers, allocations);
  const overloadRisk = assessOverloadRisk(utilizationRate);
  const recommendations = generateCapacityRecommendations(utilizationRate, overloadRisk);

  const totalCapacity = teamMembers.reduce((sum, member) => sum + member.availability, 0);
  const allocated = allocations.length * 8; // Simplified calculation

  const capacityPlan: CapacityPlan = {
    teamId: request.context?.projectId as string || 'default',
    period: request.context?.timeframe || 'current-sprint',
    totalCapacity,
    allocated,
    available: totalCapacity - allocated,
    utilizationRate,
    overloadRisk,
    recommendations,
  };

  return {
    allocations,
    capacityPlan,
    metadata: {
      timestamp: new Date().toISOString(),
      algorithm: 'ai-hybrid',
    },
  };
}

/**
 * Get resource utilization metrics
 */
export function getResourceUtilization(teamMembers: TeamMember[]): {
  averageUtilization: number;
  overloadedMembers: string[];
  underutilizedMembers: string[];
} {
  const utilizationRates = teamMembers.map(m => m.currentWorkload);
  const averageUtilization = utilizationRates.reduce((sum, rate) => sum + rate, 0) / utilizationRates.length;

  const overloadedMembers = teamMembers
    .filter(m => m.currentWorkload > 90)
    .map(m => m.id);

  const underutilizedMembers = teamMembers
    .filter(m => m.currentWorkload < 50)
    .map(m => m.id);

  return {
    averageUtilization,
    overloadedMembers,
    underutilizedMembers,
  };
}
