import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { AnalyticsDashboard } from './AnalyticsDashboard';

vi.mock('../../services/contentStudioApi', () => ({
    contentStudioApi: {
        getExperienceAnalytics: vi.fn(),
    },
}));

import { contentStudioApi } from '../../services/contentStudioApi';

describe('AnalyticsDashboard', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders latest validation details and recent authoring activity', async () => {
        vi.mocked(contentStudioApi.getExperienceAnalytics).mockResolvedValue({
            experienceId: 'exp-1',
            viewCount: 12,
            completionCount: 4,
            completionRate: 0.5,
            avgTimeMinutes: 8,
            dropOffRate: 0.1,
            simulationStarts: 3,
            simulationAborts: 1,
            simulationErrors: 0,
            hasEngagementDrift: false,
            hasQualityIssues: false,
            driftSignals: [],
            recommendedActions: [],
            latestValidation: {
                status: 'WARN',
                validatedAt: '2024-02-01T00:00:00.000Z',
                accessibilityScore: 85,
                authorityScore: 75,
                accuracyScore: 80,
                usefulnessScore: 70,
                harmlessnessScore: 100,
                suggestions: ['Add one more artifact'],
            },
            recentEvents: [
                {
                    id: 'evt-1',
                    type: 'CONTENT_CHANGED',
                    actorId: 'author-1',
                    metadata: { action: 'task_added', taskRef: 'T1' },
                    createdAt: '2024-02-02T00:00:00.000Z',
                },
            ],
        });

        render(
            <AnalyticsDashboard
                experienceId="exp-1"
                experienceTitle="Intro to Physics"
            />,
        );

        await waitFor(() => {
            expect(screen.getByText('Latest Validation')).toBeInTheDocument();
        });

        expect(screen.getByText('Recent Authoring Activity')).toBeInTheDocument();
        expect(screen.getByText('CONTENT CHANGED')).toBeInTheDocument();
        expect(screen.getByText(/Add one more artifact/)).toBeInTheDocument();
        expect(screen.getByText(/task_added/)).toBeInTheDocument();
    });
});