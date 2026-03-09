import { Settings as SettingsIcon, Hammer as BuildIcon } from 'lucide-react';
import { render } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';
import React from 'react';
import { describe, it, expect, vi } from 'vitest';

import { ProjectShell } from './ProjectShell';
import ThemeProvider from '../../theme/ThemeProvider';

// Add jest-axe matcher
expect.extend(toHaveNoViolations);

const mockTabs = [
    {
        id: 'overview',
        label: 'Overview',
        path: '/project/overview',
        icon: <BuildIcon size={16} />,
    },
    {
        id: 'settings',
        label: 'Settings',
        path: '/project/settings',
        icon: <SettingsIcon size={16} />,
    },
];

const mockBreadcrumbs = [
    { label: 'Workspace', href: '/workspace' },
    { label: 'My Project', href: '/project' },
];

const mockActions = [
    {
        id: 'refresh',
        label: 'Refresh',
        icon: <BuildIcon />,
        onClick: vi.fn(),
    },
];

const defaultProps = {
    title: 'Test Project',
    tabs: mockTabs,
    activeTab: 'overview',
    onTabChange: vi.fn(),
    children: <div>Test Content</div>,
};

describe.skip('ProjectShell Accessibility', () => {
    it('should be accessible with default props', async () => {
        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell {...defaultProps} />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should be accessible with all features enabled', async () => {
        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    description="Accessible project description"
                    breadcrumbs={mockBreadcrumbs}
                    actions={mockActions}
                    showBackButton={true}
                    onBackClick={vi.fn()}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should be accessible in loading state', async () => {
        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    isLoading={true}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should be accessible with custom header content', async () => {
        const customHeader = (
            <div role="banner" aria-label="Project status">
                <p>Build Status: Success</p>
            </div>
        );

        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    headerContent={customHeader}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should be accessible with disabled tabs', async () => {
        const tabsWithDisabled = [
            ...mockTabs,
            {
                id: 'disabled',
                label: 'Disabled Tab',
                path: '/disabled',
                disabled: true,
            },
        ];

        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    tabs={tabsWithDisabled}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should be accessible with badges in tabs', async () => {
        const tabsWithBadges = [
            {
                ...mockTabs[0],
                badge: <span aria-label="5 items">5</span>,
            },
            mockTabs[1],
        ];

        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    tabs={tabsWithBadges}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });

    it('should maintain accessibility with complex nested content', async () => {
        const complexContent = (
            <div>
                <h2>Section Title</h2>
                <nav aria-label="Secondary navigation">
                    <ul>
                        <li><a href="/link1">Link 1</a></li>
                        <li><a href="/link2">Link 2</a></li>
                    </ul>
                </nav>
                <main>
                    <article>
                        <h3>Article Title</h3>
                        <p>Article content with proper semantic structure.</p>
                    </article>
                </main>
            </div>
        );

        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    children={complexContent}
                    breadcrumbs={mockBreadcrumbs}
                    actions={mockActions}
                />
            </ThemeProvider>
        );

        const results = await axe(container);
        expect(results).toHaveNoViolations();
    });
});