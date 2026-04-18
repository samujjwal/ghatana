import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { GenerationGovernancePanel } from './GenerationGovernancePanel';

vi.mock('../../services/contentStudioApi', () => ({
    contentStudioApi: {
        detectRegenerationCandidates: vi.fn().mockResolvedValue({ created: 0 }),
        dismissRegenerationCandidate: vi.fn(),
        evaluateGenerationRequest: vi.fn(),
        getGenerationEvaluations: vi.fn(),
        getGenerationRequest: vi.fn(),
        getGenerationRequests: vi.fn(),
        getGenerationReviewDecisions: vi.fn(),
        getRegenerationCandidates: vi.fn(),
        publishGenerationRequest: vi.fn(),
        queueRegenerationCandidate: vi.fn(),
        submitGenerationReviewDecision: vi.fn(),
    },
}));

import { contentStudioApi } from '../../services/contentStudioApi';

describe('GenerationGovernancePanel', () => {
    beforeEach(() => {
        vi.clearAllMocks();

        vi.mocked(contentStudioApi.getGenerationRequests).mockResolvedValue({
            items: [
                {
                    id: 'req-1',
                    tenantId: 'tenant-1',
                    title: 'Energy Transfer Pack',
                    description: 'Generate examples and interactives for convection.',
                    domain: 'SCIENCE',
                    requestedBy: 'educator-1',
                    status: 'completed',
                    riskLevel: 'HIGH',
                    riskFactors: ['missing_evidence', 'novel_domain'],
                    reviewPath: 'human_review',
                    totalJobs: 4,
                    completedJobs: 4,
                    failedJobs: 0,
                    createdAt: '2026-04-18T00:00:00.000Z',
                    updatedAt: '2026-04-18T00:00:00.000Z',
                },
            ],
            total: 1,
            limit: 8,
            offset: 0,
        });

        vi.mocked(contentStudioApi.getGenerationRequest).mockResolvedValue({
            id: 'req-1',
            tenantId: 'tenant-1',
            title: 'Energy Transfer Pack',
            description: 'Generate examples and interactives for convection.',
            domain: 'SCIENCE',
            requestedBy: 'educator-1',
            status: 'completed',
            riskLevel: 'HIGH',
            riskFactors: ['missing_evidence', 'novel_domain'],
            reviewPath: 'human_review',
            totalJobs: 4,
            completedJobs: 4,
            failedJobs: 0,
            createdAt: '2026-04-18T00:00:00.000Z',
            updatedAt: '2026-04-18T00:00:00.000Z',
            jobs: [],
        });

        vi.mocked(contentStudioApi.getGenerationEvaluations).mockResolvedValue([
            {
                id: 'eval-1',
                tenantId: 'tenant-1',
                assetId: 'asset-1',
                generationRequestId: 'req-1',
                overallScore: 0.41,
                status: 'failed',
                recommendation: 'block',
                issues: [
                    {
                        dimension: 'evidence',
                        severity: 'error',
                        message: 'Generated content has no evidence-producing task or assessment coverage',
                    },
                ],
                createdAt: '2026-04-18T00:00:00.000Z',
                updatedAt: '2026-04-18T00:00:00.000Z',
            },
        ] as never);

        vi.mocked(contentStudioApi.getGenerationReviewDecisions).mockResolvedValue([]);
        vi.mocked(contentStudioApi.getRegenerationCandidates).mockResolvedValue([
            {
                id: 'cand-1',
                tenantId: 'tenant-1',
                assetId: 'asset-1',
                assetType: 'simulation',
                trigger: 'low_evaluation_score',
                severity: 'HIGH',
                reason: 'Simulation coverage is missing for claims that requested it.',
                priority: 90,
                status: 'open',
                createdAt: '2026-04-18T00:00:00.000Z',
                updatedAt: '2026-04-18T00:00:00.000Z',
            },
        ] as never);

        vi.mocked(contentStudioApi.evaluateGenerationRequest).mockResolvedValue({
            evaluationId: 'eval-req-1',
            generationRequestId: 'req-1',
            overallScore: 0.41,
            recommendation: 'block',
            dimensions: {
                coherence: 0.82,
                completeness: 0.3,
                safety: 1,
                accessibility: 0.9,
                manifestValidity: 0.88,
            },
            issues: [
                {
                    dimension: 'evidence',
                    severity: 'error',
                    message: 'Generated content has no evidence-producing task or assessment coverage',
                },
                {
                    dimension: 'coverage',
                    severity: 'warning',
                    message: 'Claims requiring simulations are missing interactive modality coverage',
                },
            ],
            blockedReasons: [
                'Generated content has no evidence-producing task or assessment coverage',
            ],
        });
    });

    it('surfaces reviewer risk explanations and next-step triage guidance', async () => {
        const user = userEvent.setup();

        render(<GenerationGovernancePanel />);

        await waitFor(() => {
            expect(screen.getByText('Energy Transfer Pack')).toBeInTheDocument();
        });

        const evaluateButton = await screen.findByRole('button', { name: 'Evaluate' });

        await user.click(evaluateButton);

        await waitFor(() => {
            expect(contentStudioApi.evaluateGenerationRequest).toHaveBeenCalledWith('req-1');
        });

        expect(screen.getByText('Why This Request Is Risky')).toBeInTheDocument();
        expect(screen.getByText('missing evidence')).toBeInTheDocument();
        expect(screen.getByText('novel domain')).toBeInTheDocument();
        expect(
            screen.getByText((content) =>
                content.includes('Generated content has no evidence-producing task or assessment coverage'),
            ),
        ).toBeInTheDocument();
        expect(screen.getAllByText('Simulation coverage is missing for claims that requested it.').length).toBeGreaterThan(0);

        expect(screen.getByText('Recommended Next Steps')).toBeInTheDocument();
        expect(document.querySelectorAll('.border-sky-100').length).toBeGreaterThan(0);
        expect(screen.getByText('41')).toBeInTheDocument();
    });
});
