/**
 * Resource Prediction Hook
 *
 * React hook for AI-powered resource allocation and capacity planning.
 * Provides team assignment suggestions and workload predictions.
 *
 * @doc.type hook
 * @doc.purpose AI-powered resource allocation
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  allocateResources,
  getResourceUtilization,
  type AllocationRequest,
  type AllocationResponse,
  type TeamMember,
  type TaskRequirement,
  type ResourceAllocation,
  type CapacityPlan,
} from '../services/ai/ResourceAllocationService';

// ============================================================================
// Types
// ============================================================================

export interface UseResourcePredictionOptions {
  enabled?: boolean;
  context?: AllocationRequest['context'];
}

export interface UseResourcePredictionResult {
  allocations: ResourceAllocation[];
  capacityPlan: CapacityPlan | null;
  isLoading: boolean;
  error: Error | null;
  allocate: (request: AllocationRequest) => Promise<void>;
  refetch: () => void;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useResourcePrediction(
  tasks: TaskRequirement[],
  teamMembers: TeamMember[],
  options: UseResourcePredictionOptions = {}
): UseResourcePredictionResult {
  const { enabled = true, context } = options;
  const queryClient = useQueryClient();

  // Query for resource allocation
  const {
    data: allocationResponse,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['resource-allocation', tasks, teamMembers, context],
    queryFn: () =>
      allocateResources({
        tasks,
        teamMembers,
        context,
      } as AllocationRequest),
    enabled: enabled && tasks.length > 0 && teamMembers.length > 0,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  const allocations = allocationResponse?.allocations || [];
  const capacityPlan = allocationResponse?.capacityPlan || null;

  // Mutation for allocating resources
  const allocateMutation = useMutation({
    mutationFn: (request: AllocationRequest) => allocateResources(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['resource-allocation'] });
    },
  });

  const allocate = async (request: AllocationRequest) => {
    await allocateMutation.mutateAsync(request);
  };

  return {
    allocations,
    capacityPlan,
    isLoading,
    error,
    allocate,
    refetch,
  };
}

// ============================================================================
// Resource Utilization Hook
// ============================================================================

export interface UseResourceUtilizationResult {
  averageUtilization: number;
  overloadedMembers: string[];
  underutilizedMembers: string[];
}

export function useResourceUtilization(teamMembers: TeamMember[]): UseResourceUtilizationResult {
  const utilization = getResourceUtilization(teamMembers);

  return utilization;
}

// ============================================================================
// Team Assignment Hook
// ============================================================================

export interface UseTeamAssignmentOptions {
  taskId: string;
  teamMembers: TeamMember[];
  requiredSkills: string[];
  estimatedHours: number;
}

export interface UseTeamAssignmentResult {
  recommendedMember: TeamMember | null;
  confidence: number;
  reasoning: string;
  isLoading: boolean;
  assignMember: (memberId: string) => void;
}

export function useTeamAssignment(options: UseTeamAssignmentOptions): UseTeamAssignmentResult {
  const { taskId, teamMembers, requiredSkills, estimatedHours } = options;

  // Calculate best match locally
  const { recommendedMember, confidence, reasoning } = teamMembers.reduce<{
    recommendedMember: TeamMember | null;
    confidence: number;
    reasoning: string;
  }>(
    (best, member) => {
      const skillMatch = requiredSkills.filter(skill =>
        member.skills.some(memberSkill =>
          memberSkill.toLowerCase().includes(skill.toLowerCase()) ||
          skill.toLowerCase().includes(memberSkill.toLowerCase())
        )
      ).length / Math.max(requiredSkills.length, 1);

      const workloadFactor = 1 - (member.currentWorkload / 100);
      const availabilityFactor = member.availability >= estimatedHours ? 1 : member.availability / estimatedHours;

      const score = skillMatch * 0.4 + workloadFactor * 0.3 + availabilityFactor * 0.2 + member.efficiency * 0.1;

      if (score > best.confidence) {
        return {
          recommendedMember: member,
          confidence: score,
          reasoning: `Skill match: ${(skillMatch * 100).toFixed(0)}%, Workload: ${(workloadFactor * 100).toFixed(0)}%`,
        };
      }

      return best;
    },
    { recommendedMember: null, confidence: 0, reasoning: '' }
  );

  const assignMember = (memberId: string) => {
    // This would typically call an API to assign the member
    console.log(`Assigning member ${memberId} to task ${taskId}`);
  };

  return {
    recommendedMember,
    confidence,
    reasoning,
    isLoading: false,
    assignMember,
  };
}
