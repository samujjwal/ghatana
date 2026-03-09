import { Settings as SettingsIcon, Hammer as BuildIcon } from 'lucide-react';
import { render, screen, fireEvent, within } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi } from 'vitest';

import { ProjectShell } from './ProjectShell';
import ThemeProvider from '../../theme/ThemeProvider';

// Mock navigation items
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

describe.skip('ProjectShell Component', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders correctly with default props', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell {...defaultProps} />
            </ThemeProvider>
        );

        expect(screen.getByRole('heading', { name: 'Test Project' })).toBeInTheDocument();
        expect(screen.getByText('Test Content')).toBeInTheDocument();
        expect(screen.getByRole('navigation', { name: 'Tab navigation' })).toBeInTheDocument();
    });

    it('displays project title and description', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    description="Test project description"
                />
            </ThemeProvider>
        );

        expect(screen.getByRole('heading', { name: 'Test Project' })).toBeInTheDocument();
        expect(screen.getByText('Test project description')).toBeInTheDocument();
    });

    it('renders breadcrumb navigation when provided', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    breadcrumbs={mockBreadcrumbs}
                />
            </ThemeProvider>
        );

        const breadcrumbNav = screen.getByLabelText('project breadcrumb');
        expect(breadcrumbNav).toBeInTheDocument();
        expect(within(breadcrumbNav).getByText('Workspace')).toBeInTheDocument();
        expect(within(breadcrumbNav).getByText('My Project')).toBeInTheDocument();
    });

    it('renders tab navigation with correct active tab', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell {...defaultProps} />
            </ThemeProvider>
        );

        const overviewTab = screen.getByRole('tab', { name: 'Overview' });
        const settingsTab = screen.getByRole('tab', { name: 'Settings' });

        expect(overviewTab).toBeInTheDocument();
        expect(settingsTab).toBeInTheDocument();
        expect(overviewTab).toHaveAttribute('aria-selected', 'true');
        expect(settingsTab).toHaveAttribute('aria-selected', 'false');
    });

    it('calls onTabChange when tab is clicked', () => {
        const onTabChange = vi.fn();

        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    onTabChange={onTabChange}
                />
            </ThemeProvider>
        );

        const settingsTab = screen.getByRole('tab', { name: 'Settings' });
        fireEvent.click(settingsTab);

        expect(onTabChange).toHaveBeenCalledWith('settings');
    });

    it('renders action buttons when provided', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    actions={mockActions}
                />
            </ThemeProvider>
        );

        const refreshButton = screen.getByLabelText('Refresh');
        expect(refreshButton).toBeInTheDocument();
    });

    it('calls action onClick when action button is clicked', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    actions={mockActions}
                />
            </ThemeProvider>
        );

        const refreshButton = screen.getByLabelText('Refresh');
        fireEvent.click(refreshButton);

        expect(mockActions[0].onClick).toHaveBeenCalled();
    });

    it('shows back button when showBackButton is true', () => {
        const onBackClick = vi.fn();

        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    showBackButton={true}
                    onBackClick={onBackClick}
                />
            </ThemeProvider>
        );

        const backButton = screen.getByLabelText('Go back');
        expect(backButton).toBeInTheDocument();

        fireEvent.click(backButton);
        expect(onBackClick).toHaveBeenCalled();
    });

    it('shows loading state when isLoading is true', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    isLoading={true}
                />
            </ThemeProvider>
        );

        expect(screen.getByText('Loading...')).toBeInTheDocument();
        expect(screen.queryByText('Test Content')).not.toBeInTheDocument();
    });

    it('renders custom header content when provided', () => {
        const customHeader = <div>Custom Header Content</div>;

        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    headerContent={customHeader}
                />
            </ThemeProvider>
        );

        expect(screen.getByText('Custom Header Content')).toBeInTheDocument();
    });

    it('disables actions when isLoading is true', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    actions={mockActions}
                    isLoading={true}
                />
            </ThemeProvider>
        );

        const refreshButton = screen.getByLabelText('Refresh');
        expect(refreshButton).toBeDisabled();
    });

    it('handles disabled tabs correctly', () => {
        const tabsWithDisabled = [
            ...mockTabs,
            {
                id: 'disabled',
                label: 'Disabled Tab',
                path: '/disabled',
                disabled: true,
            },
        ];

        render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    tabs={tabsWithDisabled}
                />
            </ThemeProvider>
        );

        const disabledTab = screen.getByRole('tab', { name: 'Disabled Tab' });
        expect(disabledTab).toHaveAttribute('aria-disabled', 'true');
    });

    it('has proper accessibility attributes', () => {
        render(
            <ThemeProvider mode="light">
                <ProjectShell {...defaultProps} />
            </ThemeProvider>
        );

        // Check main navigation has proper role
        const navigation = screen.getByRole('navigation', { name: 'Tab navigation' });
        expect(navigation).toBeInTheDocument();

        // Check tabs have proper ARIA attributes
        const overviewTab = screen.getByRole('tab', { name: 'Overview' });
        expect(overviewTab).toHaveAttribute('id');
        expect(overviewTab).toHaveAttribute('aria-controls');
    });

    it('applies custom className when provided', () => {
        const { container } = render(
            <ThemeProvider mode="light">
                <ProjectShell
                    {...defaultProps}
                    className="custom-shell"
                />
            </ThemeProvider>
        );

        expect(container.firstChild).toHaveClass('custom-shell');
    });
});

// Integration tests for mobile behavior would require more complex setup
// These would test responsive behavior, mobile drawer functionality, etc.
describe.skip('ProjectShell Mobile Behavior', () => {
    // Mock window.matchMedia for responsive tests
    const mockMatchMedia = (matches: boolean) => {
        Object.defineProperty(window, 'matchMedia', {
            writable: true,
            value: vi.fn().mockImplementation((query: string) => ({
                matches,
                media: query,
                onchange: null,
                addListener: vi.fn(),
                removeListener: vi.fn(),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
                dispatchEvent: vi.fn(),
            })),
        });
    };

    it('should handle mobile drawer functionality', () => {
        // This would require more complex testing setup for useMediaQuery
        // For now, we'll just ensure the component doesn't crash
        mockMatchMedia(true);

        render(
            <ThemeProvider mode="light">
                <ProjectShell {...defaultProps} />
            </ThemeProvider>
        );

        expect(screen.getByRole('heading', { name: 'Test Project' })).toBeInTheDocument();
    });
});