/**
 * Tests for the Governance page (TrustCenter).
 *
 * Supplements TrustCenter.test.tsx with governance-specific scenarios.
 *
 * @doc.type test
 * @doc.purpose RTL tests for TrustCenter governance scenarios
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const { mockGovernanceService } = vi.hoisted(() => ({
    mockGovernanceService: {
        getPolicies: vi.fn(),
        getAuditLogs: vi.fn(),
        getComplianceReport: vi.fn(),
        getLifecycleSurfaces: vi.fn(),
        generateComplianceReport: vi.fn(),
    },
}));

vi.mock('../../api/governance.service', () => ({
    governanceService: mockGovernanceService,
}));

import { TrustCenter } from '../../pages/TrustCenter';

describe('GovernancePage — TrustCenter', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockGovernanceService.getPolicies.mockResolvedValue([
            {
                id: 'policy-1',
                name: 'PII Registry Coverage',
                type: 'PRIVACY',
                scope: { datasets: ['orders', 'customers'] },
                rules: [],
                enabled: true,
                createdAt: '2026-04-01T00:00:00Z',
                updatedAt: '2026-04-14T00:00:00Z',
                metadata: { source: 'contract-test' },
            },
            {
                id: 'policy-2',
                name: 'Security Audit Posture',
                type: 'SECURITY',
                scope: { roles: ['admin'] },
                rules: [],
                enabled: true,
                createdAt: '2026-04-01T00:00:00Z',
                updatedAt: '2026-04-14T00:00:00Z',
                metadata: {},
            },
        ]);
        mockGovernanceService.getAuditLogs.mockResolvedValue([
            {
                id: 'audit-1',
                timestamp: '2026-04-14T10:00:00Z',
                userId: 'user-1',
                userName: 'Jane Doe',
                action: 'Applied GDPR policy',
                resourceType: 'policy',
                resourceId: 'policy-1',
                outcome: 'SUCCESS',
                details: {},
            },
        ]);
        mockGovernanceService.getComplianceReport.mockResolvedValue({
            id: 'report-1',
            period: '30d',
            generatedAt: '2026-04-14T10:05:00Z',
            summary: {
                totalPolicies: 2,
                activePolicies: 2,
                violations: 0,
                remediations: 1,
                complianceScore: 96,
            },
            details: {
                piiScans: {
                    totalDatasets: 12,
                    datasetsWithPII: 4,
                    violations: 0,
                    remediated: 1,
                },
                accessAudits: {
                    totalAccesses: 100,
                    unauthorizedAttempts: 0,
                    blockedAccesses: 0,
                },
                retentionCompliance: {
                    datasetsCompliant: 12,
                    datasetsViolating: 0,
                },
            },
        });
        mockGovernanceService.generateComplianceReport.mockResolvedValue({ reportId: 'report-1', status: 'ready' });
        mockGovernanceService.getLifecycleSurfaces.mockResolvedValue([
            {
                id: 'retention-operations',
                title: 'Retention Operations',
                status: 'live-action',
                summary: 'Retention classification is live and current collections already carry a reviewed retention posture.',
                evidence: ['12 collections already classified'],
                action: 'classify-retention',
                actionLabel: 'Classify retention',
            },
            {
                id: 'policy-lifecycle',
                title: 'Policy Lifecycle',
                status: 'unavailable',
                summary: 'General policy create, update, toggle, and delete flows are still outside the current launcher-backed governance contract.',
                evidence: ['Policy creation is not exposed by the current Data Cloud governance API.'],
            },
        ]);
    });

    it('shows an honest empty-state message when no policies match the governance query', async () => {
        mockGovernanceService.getPolicies.mockResolvedValue([]);

        render(<TrustCenter />, { wrapper: TestWrapper });

        expect(await screen.findByText(/No derived policy coverage is available/i)).toBeInTheDocument();
        expect(screen.getByText(/Overall Compliance Score/i)).toBeInTheDocument();
    });

    it('renders canonical governance payloads into compliance score, policies, and audit log', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });

        expect(await screen.findByText(/overall compliance score/i)).toBeInTheDocument();
        await waitFor(() => {
            expect(document.body.textContent).toContain('96%');
            expect(screen.getByText('Governance Lifecycle Truth')).toBeInTheDocument();
            expect(screen.getByText('PII Registry Coverage')).toBeInTheDocument();
            expect(screen.getByText('Security Audit Posture')).toBeInTheDocument();
            expect(screen.getAllByText('Applied GDPR policy').length).toBeGreaterThan(0);
            expect(screen.getAllByText(/policy:policy-1/i).length).toBeGreaterThan(0);
            expect(screen.getByText(/1 event/i)).toBeInTheDocument();
            expect(screen.getByText(/Last updated by Jane Doe/i)).toBeInTheDocument();
        });

        await waitFor(() => {
            expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(1);
            expect(mockGovernanceService.getAuditLogs).toHaveBeenCalledWith(undefined, undefined, 10);
            expect(mockGovernanceService.getComplianceReport).toHaveBeenCalledWith('30d');
            expect(mockGovernanceService.getLifecycleSurfaces).toHaveBeenCalledTimes(1);
        });
    });

    it('filters policies through the Trust Center search input', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        await screen.findByText('PII Registry Coverage');

        fireEvent.change(
            screen.getByPlaceholderText(/search live safeguards/i),
            { target: { value: 'security' } },
        );

        expect(screen.queryByText('PII Registry Coverage')).not.toBeInTheDocument();
        expect(screen.getByText('Security Audit Posture')).toBeInTheDocument();
    });

    it('refreshes policies from the canonical governance service', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        await screen.findByText('PII Registry Coverage');

        fireEvent.click(screen.getByTestId('trust-refresh-policies'));

        await waitFor(() => {
            expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(2);
        });
    });

    it('refreshes the compliance summary through the operator quick action', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        await screen.findByText('PII Registry Coverage');

        fireEvent.click(screen.getByTestId('trust-quick-action-refresh-compliance'));

        await waitFor(() => {
            expect(mockGovernanceService.generateComplianceReport).toHaveBeenCalledWith('30d');
            expect(screen.getByTestId('trust-action-summary')).toHaveTextContent(/Compliance summary refreshed/i);
        });
    });
});
