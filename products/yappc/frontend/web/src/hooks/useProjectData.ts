/**
 * Project Data Hooks - Custom hooks for project phases and progress
 * 
 * Provides data fetching and state management for project phases,
 * progress tracking, and change detection.
 */

import { useQuery } from '@tanstack/react-query';

// Mock data for development
const mockPhases = [
    {
        id: 'plan',
        name: 'Planning',
        description: 'Define requirements and scope',
        status: 'completed',
        tasks: [
            { id: 'req-1', name: 'Requirements gathering', status: 'completed' },
            { id: 'req-2', name: 'User stories', status: 'completed' }
        ]
    },
    {
        id: 'design',
        name: 'Design',
        description: 'Create UI/UX designs',
        status: 'in-progress',
        tasks: [
            { id: 'design-1', name: 'Wireframes', status: 'completed' },
            { id: 'design-2', name: 'Visual design', status: 'in-progress' }
        ]
    },
    {
        id: 'implement',
        name: 'Implementation',
        description: 'Build the application',
        status: 'not-started',
        tasks: [
            { id: 'impl-1', name: 'Backend setup', status: 'not-started' },
            { id: 'impl-2', name: 'Frontend development', status: 'not-started' }
        ]
    },
    {
        id: 'test',
        name: 'Testing',
        description: 'Quality assurance',
        status: 'not-started',
        tasks: []
    },
    {
        id: 'deploy',
        name: 'Deployment',
        description: 'Deploy to production',
        status: 'not-started',
        tasks: []
    }
];

const mockProgress = {
    phaseProgress: {
        plan: { status: 'completed', completedTasks: 2, totalTasks: 2, percentage: 100 },
        design: { status: 'in-progress', completedTasks: 1, totalTasks: 2, percentage: 50 },
        implement: { status: 'not-started', completedTasks: 0, totalTasks: 2, percentage: 0 },
        test: { status: 'not-started', completedTasks: 0, totalTasks: 0, percentage: 0 },
        deploy: { status: 'not-started', completedTasks: 0, totalTasks: 0, percentage: 0 }
    },
    overallProgress: 30,
    hasChanges: true
};

export function useProjectPhases(projectId: string) {
    return useQuery({
        queryKey: ['project-phases', projectId],
        queryFn: async () => {
            // Simulate API call
            await new Promise(resolve => setTimeout(resolve, 500));
            return mockPhases;
        },
        enabled: !!projectId
    });
}

export function useProjectProgress(projectId: string) {
    return useQuery({
        queryKey: ['project-progress', projectId],
        queryFn: async () => {
            // Simulate API call
            await new Promise(resolve => setTimeout(resolve, 300));
            return mockProgress;
        },
        enabled: !!projectId
    });
}
