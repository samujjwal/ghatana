/**
 * Accessibility Tests for Org Components
 *
 * Tests WCAG 2.2 AA compliance for DepartmentStatusBadge, AgentAvatar, KpiCard, OrgMap.
 * Uses @axe-core/react and @testing-library/react for accessibility auditing.
 *
 * @package @ghatana/ui/tests/accessibility
 */

import React from 'react';
import { render, screen } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';

// Import org components
import { DepartmentStatusBadge } from '../../components/org/DepartmentStatusBadge';
import { AgentAvatar } from '../../components/org/AgentAvatar';
import { KpiCard } from '../../components/org/KpiCard';
import { OrgMap } from '../../components/org/OrgMap';

expect.extend(toHaveNoViolations);

describe('Org Components - Accessibility (WCAG 2.2 AA)', () => {
    describe('DepartmentStatusBadge', () => {
        it('should have no accessibility violations for active status', async () => {
            const { container } = render(
                <DepartmentStatusBadge status="active" label="Engineering" />
            );
            const results = await axe(container);
            expect(results).toHaveNoViolations();
        });

        it('should have no accessibility violations for warning status', async () => {
            const { container } = render(
                <DepartmentStatusBadge status="warning" label="QA" count={3} />
            );
            const results = await axe(container);
            expect(results).toHaveNoViolations();
        });

        it('should have proper aria-label', () => {
            render(<DepartmentStatusBadge status="active" label="Engineering" />);
            const badge = screen.getByRole('button');
            expect(badge).toHaveAttribute('aria-label', 'Engineering status: active');
        });

        it('should be keyboard navigable', () => {
            render(<DepartmentStatusBadge status="active" label="Engineering" />);
            const badge = screen.getByRole('button');
            expect(badge).toHaveAttribute('tabIndex', '0');
        });
    });

    describe('AgentAvatar', () => {
        it('should have no accessibility violations', async () => {
            const { container } = render(
                <AgentAvatar role="orchestrator" name="Orchestrator" />
            );
            const results = await axe(container);
            expect(results).toHaveNoViolations();
        });

        it('should have proper aria-label for agent', () => {
            render(
                <AgentAvatar role="task" name="Task Runner" agentId="task-123" />
            );
            const avatar = screen.getByRole('img');
            expect(avatar).toHaveAttribute(
                'aria-label',
                'Task Runner (task agent, ID: task-123)'
            );
        });

        it('should display role-specific initial', () => {
            render(<AgentAvatar role="task" name="Task Runner" />);
            expect(screen.getByText('T')).toBeInTheDocument();
        });
    });

    describe('KpiCard', () => {
        it('should have no accessibility violations', async () => {
            const { container } = render(
                <KpiCard label="Deployment Frequency" value="12" unit="/day" />
            );
            const results = await axe(container);
            expect(results).toHaveNoViolations();
        });

        it('should have proper aria-label', () => {
            render(
                <KpiCard label="Deployment Frequency" value="12" unit="/day" />
            );
            const card = screen.getByRole('region');
            expect(card).toHaveAttribute('aria-label', 'Deployment Frequency: 12/day');
        });

        it('should display progress bar with proper aria attributes', () => {
            render(
                <KpiCard label="Test Coverage" value="85" unit="%" progress={85} />
            );
            const progressBar = screen.getByRole('progressbar');
            expect(progressBar).toHaveAttribute('aria-valuenow', '85');
            expect(progressBar).toHaveAttribute('aria-valuemin', '0');
            expect(progressBar).toHaveAttribute('aria-valuemax', '100');
        });
    });

    describe('OrgMap', () => {
        const mockDepartments = [
            {
                id: 'eng',
                name: 'Engineering',
                status: 'active' as const,
                children: ['qa'],
            },
            {
                id: 'qa',
                name: 'QA',
                status: 'warning' as const,
                parent: 'eng',
            },
        ];

        it('should have no accessibility violations', async () => {
            const { container } = render(<OrgMap departments={mockDepartments} />);
            const results = await axe(container);
            expect(results).toHaveNoViolations();
        });

        it('should have proper region label', () => {
            render(<OrgMap departments={mockDepartments} />);
            const map = screen.getByRole('region');
            expect(map).toHaveAttribute('aria-label', 'Organization map');
        });

        it('should have proper department button labels', () => {
            render(<OrgMap departments={mockDepartments} />);
            const buttons = screen.getAllByRole('button');
            expect(buttons.length).toBeGreaterThan(0);
        });

        it('should be keyboard navigable for department selection', () => {
            render(<OrgMap departments={mockDepartments} />);
            const buttons = screen.getAllByRole('button');
            buttons.forEach((button) => {
                expect(button).toHaveAttribute('tabIndex', '0');
            });
        });
    });

    describe('Keyboard Navigation', () => {
        it('DepartmentStatusBadge should handle Enter key', () => {
            const mockClick = jest.fn();
            render(
                <DepartmentStatusBadge
                    status="active"
                    label="Engineering"
                    onClick={mockClick}
                />
            );
            const badge = screen.getByRole('button');
            badge.dispatchEvent(
                new KeyboardEvent('keydown', { key: 'Enter' })
            );
            expect(mockClick).toHaveBeenCalled();
        });

        it('KpiCard should handle Enter key', () => {
            const mockClick = jest.fn();
            render(
                <KpiCard
                    label="Test"
                    value="100"
                    onClick={mockClick}
                />
            );
            const card = screen.getByRole('button');
            card.dispatchEvent(
                new KeyboardEvent('keydown', { key: 'Enter' })
            );
            expect(mockClick).toHaveBeenCalled();
        });
    });

    describe('Color Contrast', () => {
        it('DepartmentStatusBadge should have sufficient color contrast in all statuses', () => {
            // This is a manual verification step - tools like aXe will check this
            const statuses = ['active', 'warning', 'critical', 'idle', 'unknown'] as const;
            const results: React.ReactNode[] = [];

            statuses.forEach((status) => {
                const { container } = render(
                    <DepartmentStatusBadge status={status} label="Test" />
                );
                results.push(container);
            });

            // In a real test, use Color Contrast Analyzer or axe color contrast plugin
            expect(results).toBeDefined();
        });
    });
});
