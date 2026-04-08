/**
 * ResourceAllocationService Tests
 */

import { describe, it, expect } from 'vitest';
import {
  allocateResources,
  getResourceUtilization,
  type AllocationRequest,
  type AllocationResponse,
  type TeamMember,
  type TaskRequirement,
} from '../ResourceAllocationService';

describe('ResourceAllocationService', () => {
  describe('allocateResources', () => {
    it('should allocate tasks to team members', async () => {
      const request: AllocationRequest = {
        tasks: [
          {
            taskId: '1',
            title: 'Test task',
            requiredSkills: ['javascript'],
            estimatedHours: 4,
            priority: 'high',
          },
        ],
        teamMembers: [
          {
            id: 'm1',
            name: 'John',
            role: 'Developer',
            skills: ['javascript', 'react'],
            currentWorkload: 50,
            availability: 40,
            efficiency: 0.9,
          },
        ],
      };

      const response = await allocateResources(request);

      expect(response).toBeDefined();
      expect(response.allocations).toBeInstanceOf(Array);
      expect(response.allocations.length).toBe(1);
      expect(response.allocations[0].taskId).toBe('1');
    });

    it('should assign based on skill match', async () => {
      const request: AllocationRequest = {
        tasks: [
          {
            taskId: '1',
            title: 'React task',
            requiredSkills: ['react'],
            estimatedHours: 4,
            priority: 'high',
          },
        ],
        teamMembers: [
          {
            id: 'm1',
            name: 'React Dev',
            role: 'Developer',
            skills: ['react', 'javascript'],
            currentWorkload: 50,
            availability: 40,
            efficiency: 0.9,
          },
          {
            id: 'm2',
            name: 'Backend Dev',
            role: 'Developer',
            skills: ['python', 'django'],
            currentWorkload: 50,
            availability: 40,
            efficiency: 0.9,
          },
        ],
      };

      const response = await allocateResources(request);

      expect(response.allocations[0].assignedMemberId).toBe('m1');
    });

    it('should consider workload in allocation', async () => {
      const request: AllocationRequest = {
        tasks: [
          {
            taskId: '1',
            title: 'Test task',
            requiredSkills: ['javascript'],
            estimatedHours: 4,
            priority: 'high',
          },
        ],
        teamMembers: [
          {
            id: 'm1',
            name: 'Busy Dev',
            role: 'Developer',
            skills: ['javascript'],
            currentWorkload: 90,
            availability: 40,
            efficiency: 0.9,
          },
          {
            id: 'm2',
            name: 'Available Dev',
            role: 'Developer',
            skills: ['javascript'],
            currentWorkload: 30,
            availability: 40,
            efficiency: 0.9,
          },
        ],
      };

      const response = await allocateResources(request);

      expect(response.allocations[0].assignedMemberId).toBe('m2');
    });

    it('should include capacity plan in response', async () => {
      const request: AllocationRequest = {
        tasks: [
          {
            taskId: '1',
            title: 'Test task',
            requiredSkills: ['javascript'],
            estimatedHours: 4,
            priority: 'high',
          },
        ],
        teamMembers: [
          {
            id: 'm1',
            name: 'John',
            role: 'Developer',
            skills: ['javascript'],
            currentWorkload: 50,
            availability: 40,
            efficiency: 0.9,
          },
        ],
      };

      const response = await allocateResources(request);

      expect(response.capacityPlan).toBeDefined();
      expect(response.capacityPlan.totalCapacity).toBeGreaterThan(0);
      expect(response.capacityPlan.utilizationRate).toBeGreaterThanOrEqual(0);
      expect(response.capacityPlan.utilizationRate).toBeLessThanOrEqual(1);
    });

    it('should generate recommendations based on utilization', async () => {
      const request: AllocationRequest = {
        tasks: [],
        teamMembers: [
          {
            id: 'm1',
            name: 'John',
            role: 'Developer',
            skills: ['javascript'],
            currentWorkload: 95,
            availability: 40,
            efficiency: 0.9,
          },
        ],
      };

      const response = await allocateResources(request);

      expect(response.capacityPlan.recommendations).toBeInstanceOf(Array);
      expect(response.capacityPlan.recommendations.length).toBeGreaterThan(0);
    });
  });

  describe('getResourceUtilization', () => {
    it('should calculate average utilization', () => {
      const teamMembers: TeamMember[] = [
        {
          id: 'm1',
          name: 'John',
          role: 'Developer',
          skills: ['javascript'],
          currentWorkload: 50,
          availability: 40,
          efficiency: 0.9,
        },
        {
          id: 'm2',
          name: 'Jane',
          role: 'Developer',
          skills: ['python'],
          currentWorkload: 70,
          availability: 40,
          efficiency: 0.9,
        },
      ];

      const utilization = getResourceUtilization(teamMembers);

      expect(utilization.averageUtilization).toBe(60);
    });

    it('should identify overloaded members', () => {
      const teamMembers: TeamMember[] = [
        {
          id: 'm1',
          name: 'Overloaded',
          role: 'Developer',
          skills: ['javascript'],
          currentWorkload: 95,
          availability: 40,
          efficiency: 0.9,
        },
        {
          id: 'm2',
          name: 'Normal',
          role: 'Developer',
          skills: ['python'],
          currentWorkload: 50,
          availability: 40,
          efficiency: 0.9,
        },
      ];

      const utilization = getResourceUtilization(teamMembers);

      expect(utilization.overloadedMembers).toContain('m1');
      expect(utilization.overloadedMembers).not.toContain('m2');
    });

    it('should identify underutilized members', () => {
      const teamMembers: TeamMember[] = [
        {
          id: 'm1',
          name: 'Underutilized',
          role: 'Developer',
          skills: ['javascript'],
          currentWorkload: 30,
          availability: 40,
          efficiency: 0.9,
        },
        {
          id: 'm2',
          name: 'Normal',
          role: 'Developer',
          skills: ['python'],
          currentWorkload: 50,
          availability: 40,
          efficiency: 0.9,
        },
      ];

      const utilization = getResourceUtilization(teamMembers);

      expect(utilization.underutilizedMembers).toContain('m1');
      expect(utilization.underutilizedMembers).not.toContain('m2');
    });
  });
});
