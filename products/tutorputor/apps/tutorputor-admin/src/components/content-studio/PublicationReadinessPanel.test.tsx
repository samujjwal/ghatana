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

    it('guides authors to exact missing publish-readiness fixes', async () => {
        vi.mocked(contentStudioApi.validateExperience).mockResolvedValue({
            status: 'invalid',
            canPublish: false,
            score: 41,
            validatedAt: new Date('2026-05-06T00:00:00.000Z'),
            pillarScores: {
                Educational: 35,
                Experiential: 30,
                Safety: 100,
                Technical: 40,
                Accessibility: 20,
            },
            checks: [
                {
                    checkId: 'claims-present',
                    pillar: 'educational',
                    name: 'Learning claims present',
                    passed: false,
                    severity: 'error',
                    message: 'No learning claims are defined.',
                    suggestion: 'Add at least one learning claim.',
                },
                {
                    checkId: 'evidence-coverage',
                    pillar: 'educational',
                    name: 'Evidence coverage',
                    passed: false,
                    severity: 'error',
                    message: 'Claims are missing evidence links.',
                    suggestion: 'Map each claim to evidence.',
                },
                {
                    checkId: 'simulation-manifest',
                    pillar: 'experiential',
                    name: 'Simulation manifest configured',
                    passed: false,
                    severity: 'error',
                    message: 'Simulation seed and parameters are missing.',
                    suggestion: 'Configure the canonical simulation manifest.',
                },
                {
                    checkId: 'assessment-task-coverage',
                    pillar: 'assessment',
                    name: 'Assessment coverage',
                    passed: false,
                    severity: 'error',
                    message: 'Assessment tasks do not cover required evidence.',
                    suggestion: 'Create assessment items for each evidence target.',
                },
                {
                    checkId: 'accessibility-notes',
                    pillar: 'accessibility',
                    name: 'Accessibility metadata',
                    passed: false,
                    severity: 'error',
                    message: 'Captions, transcripts, and text alternatives are missing.',
                    suggestion: 'Add accessibility alternatives before QA.',
                },
                {
                    checkId: 'sme-review',
                    pillar: 'review',
                    name: 'SME review status',
                    passed: false,
                    severity: 'error',
                    message: 'SME review is incomplete.',
                    suggestion: 'Send the module to an SME reviewer.',
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
            expect(screen.getByText('Guided Publish Readiness')).toBeInTheDocument();
        });

        expect(screen.getByText('0/6 complete')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Open claim map' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Map evidence' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Configure simulation' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Edit assessment' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Add accessibility notes' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Send to reviewer' })).toBeInTheDocument();
    });

    it('shows publish-ready state when all guided readiness targets pass', async () => {
        vi.mocked(contentStudioApi.validateExperience).mockResolvedValue({
            status: 'valid',
            canPublish: true,
            score: 96,
            validatedAt: new Date('2026-05-06T00:00:00.000Z'),
            pillarScores: {
                Educational: 100,
                Experiential: 95,
                Safety: 100,
                Technical: 95,
                Accessibility: 90,
            },
            checks: [
                { checkId: 'claims-present', pillar: 'educational', name: 'Learning claims present', passed: true, severity: 'info', message: 'Claims present.' },
                { checkId: 'evidence-coverage', pillar: 'educational', name: 'Evidence coverage', passed: true, severity: 'info', message: 'Evidence mapped.' },
                { checkId: 'simulation-manifest', pillar: 'experiential', name: 'Simulation manifest configured', passed: true, severity: 'info', message: 'Simulation configured.' },
                { checkId: 'assessment-task-coverage', pillar: 'assessment', name: 'Assessment coverage', passed: true, severity: 'info', message: 'Assessment covered.' },
                { checkId: 'accessibility-notes', pillar: 'accessibility', name: 'Accessibility metadata', passed: true, severity: 'info', message: 'Accessibility ready.' },
                { checkId: 'sme-review', pillar: 'review', name: 'SME review status', passed: true, severity: 'info', message: 'Review complete.' },
            ],
        });

        render(
            <PublicationReadinessPanel
                experienceId="exp-1"
                experienceTitle="Energy Transfer"
            />,
        );

        await waitFor(() => {
            expect(screen.getByText('6/6 complete')).toBeInTheDocument();
        });

        expect(screen.getByText('Ready to publish')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled();
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
