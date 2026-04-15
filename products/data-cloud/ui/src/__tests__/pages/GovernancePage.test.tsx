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
    });

    it('renders without crashing', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays governance or compliance content', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/govern|trust|compli|policy|securit|audit|privacy/i);
    });

    it('renders with meaningful structure', () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('does not throw on render', () => {
        expect(() =>
            render(<TrustCenter />, { wrapper: TestWrapper })
        ).not.toThrow();
    });

    it('renders canonical governance payloads into compliance score, policies, and audit log', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });

        expect(await screen.findByText(/overall compliance score/i)).toBeInTheDocument();
        await waitFor(() => {
            expect(document.body.textContent).toContain('96%');
            expect(screen.getByText('PII Registry Coverage')).toBeInTheDocument();
            expect(screen.getByText('Security Audit Posture')).toBeInTheDocument();
            expect(screen.getByText('Applied GDPR policy')).toBeInTheDocument();
            expect(screen.getByText(/policy:policy-1/i)).toBeInTheDocument();
        });

        await waitFor(() => {
            expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(1);
            expect(mockGovernanceService.getAuditLogs).toHaveBeenCalledWith(undefined, undefined, 10);
            expect(mockGovernanceService.getComplianceReport).toHaveBeenCalledWith('30d');
        });
    });

    it('filters policies through the Trust Center search input', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        await screen.findByText('PII Registry Coverage');

        fireEvent.change(
            screen.getByPlaceholderText(/search policies or try/i),
            { target: { value: 'security' } },
        );

        expect(screen.queryByText('PII Registry Coverage')).not.toBeInTheDocument();
        expect(screen.getByText('Security Audit Posture')).toBeInTheDocument();
    });

    it('refreshes policies from the canonical governance service', async () => {
        render(<TrustCenter />, { wrapper: TestWrapper });
        await screen.findByText('PII Registry Coverage');

        fireEvent.click(screen.getAllByLabelText('Refresh')[0]);

        await waitFor(() => {
            expect(mockGovernanceService.getPolicies).toHaveBeenCalledTimes(2);
        });
    });
});
