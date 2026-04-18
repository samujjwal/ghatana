import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PublicationReadinessPanel } from './PublicationReadinessPanel';

vi.mock('../../services/contentStudioApi', () => ({
    contentStudioApi: {
        publishExperience: vi.fn(),
        validateExperience: vi.fn(),
    },
}));

import { contentStudioApi } from '../../services/contentStudioApi';

describe('PublicationReadinessPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('surfaces blocking publication reasons from validation checks', async () => {
        vi.mocked(contentStudioApi.validateExperience).mockResolvedValue({
            status: 'invalid',
            canPublish: false,
            score: 52,
            validatedAt: new Date('2026-04-18T00:00:00.000Z'),
            pillarScores: {
                Educational: 60,
                Experiential: 25,
                Safety: 100,
                Technical: 50,
                Accessibility: 75,
            },
            checks: [
                {
                    checkId: 'claim-artifacts',
                    pillar: 'experiential',
                    name: 'Concrete Learning Artifacts',
                    passed: false,
                    severity: 'error',
                    message: '2 claims lack examples, simulations, or animations.',
                    suggestion: 'Run the content generation pipeline.',
                },
                {
                    checkId: 'claim-bloom',
                    pillar: 'educational',
                    name: 'Bloom Coverage',
                    passed: false,
                    severity: 'warning',
                    message: '1 claim is missing Bloom classification.',
                    suggestion: 'Assign Bloom levels.',
                },
            ],
        });

        render(
            <PublicationReadinessPanel
                experienceId="exp-1"
                experienceTitle="Energy Transfer"
            />,
        );

        await waitFor(() => {
            expect(screen.getByText('Publication Block Reasons')).toBeInTheDocument();
        });

        expect(screen.getByText('2 claims lack examples, simulations, or animations.')).toBeInTheDocument();
        expect(screen.getByText(/Assign Bloom levels/)).toBeInTheDocument();
        expect(screen.getByText('HIGH')).toBeInTheDocument();
    });

    it('publishes when validation passes', async () => {
        const user = userEvent.setup();
        const onPublished = vi.fn();

        vi.mocked(contentStudioApi.validateExperience).mockResolvedValue({
            status: 'valid',
            canPublish: true,
            score: 92,
            validatedAt: new Date('2026-04-18T00:00:00.000Z'),
            pillarScores: {
                Educational: 100,
                Experiential: 90,
                Safety: 100,
                Technical: 90,
                Accessibility: 80,
            },
            checks: [],
        });
        vi.mocked(contentStudioApi.publishExperience).mockResolvedValue({
            id: 'exp-1',
            title: 'Energy Transfer',
            description: 'Test experience',
            status: 'published',
            gradeLevel: 'grade_6_8',
            subject: 'SCIENCE',
            claims: [],
            createdAt: '2026-04-18T00:00:00.000Z',
            updatedAt: '2026-04-18T00:00:00.000Z',
        });

        render(
            <PublicationReadinessPanel
                experienceId="exp-1"
                experienceTitle="Energy Transfer"
                onPublished={onPublished}
            />,
        );

        await waitFor(() => {
            expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled();
        });

        await user.click(screen.getByRole('button', { name: 'Publish' }));

        await waitFor(() => {
            expect(contentStudioApi.publishExperience).toHaveBeenCalledWith('exp-1');
        });
        expect(onPublished).toHaveBeenCalledTimes(1);
    });
});