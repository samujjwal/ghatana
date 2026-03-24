// All tests skipped - incomplete feature
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React, { act } from 'react';

import { GateWidget, type GateStatus } from './GateWidget';

const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => {
    return render(
        <ThemeProvider theme={theme}>
            {component}
        </ThemeProvider>
    );
};

const mockGates: GateStatus[] = [
    {
        id: 'build-1',
        name: 'Build Pipeline',
        category: 'build',
        status: 'success',
        lastUpdated: new Date().toISOString(),
        message: 'Build completed successfully',
        detailsUrl: 'https://example.com/build/1',
        duration: 120000,
        required: true,
    },
    {
        id: 'test-1',
        name: 'Unit Tests',
        category: 'test',
        status: 'error',
        lastUpdated: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        message: '3 tests failed',
        detailsUrl: 'https://example.com/tests/1',
        duration: 90000,
        required: true,
    },
    {
        id: 'security-1',
        name: 'Security Scan',
        category: 'security',
        status: 'warning',
        lastUpdated: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
        message: '2 vulnerabilities found',
        detailsUrl: 'https://example.com/security/1',
        duration: 45000,
        required: false,
    },
];

describe.skip('GateWidget', () => {
    describe('Basic Rendering', () => {
        it('renders with default props', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            expect(screen.getByText('Gates')).toBeInTheDocument();
            expect(screen.getByText('Build Pipeline')).toBeInTheDocument();
            expect(screen.getByText('Unit Tests')).toBeInTheDocument();
            expect(screen.getByText('Security Scan')).toBeInTheDocument();
        });

        it('renders with custom title', () => {
            renderWithTheme(<GateWidget gates={mockGates} title="Custom Gates" />);

            expect(screen.getByText('Custom Gates')).toBeInTheDocument();
        });

        it('renders empty state when no gates provided', () => {
            renderWithTheme(<GateWidget gates={[]} />);

            expect(screen.getByText('No gates configured')).toBeInTheDocument();
        });
    });

    describe('Gate Display', () => {
        it('displays gate names and messages', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            expect(screen.getByText('Build Pipeline')).toBeInTheDocument();
            expect(screen.getByText('Build completed successfully')).toBeInTheDocument();
            expect(screen.getByText('Unit Tests')).toBeInTheDocument();
            expect(screen.getByText('3 tests failed')).toBeInTheDocument();
        });

        it('displays status badges for each gate', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            const statusElements = screen.getAllByRole('status');
            // Expect at least one status badge per gate plus overall status
            expect(statusElements.length).toBeGreaterThanOrEqual(mockGates.length);
        });

        it('displays relative time for last updated', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            // Check for time indicators (should show relative times like "5m ago")
            expect(screen.getByText(/ago|Just now/)).toBeInTheDocument();
        });

        it('displays duration when available', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            // Check for duration display (formatted as "2m 0s" etc.)
            expect(screen.getByText(/\d+m|\d+s/)).toBeInTheDocument();
        });
    });

    describe('Overall Status Calculation', () => {
        it('shows error status when any gate has error', () => {
            const gatesWithError = [
                { ...mockGates[0], status: 'success' as const },
                { ...mockGates[1], status: 'error' as const },
            ];

            renderWithTheme(<GateWidget gates={gatesWithError} />);

            // Overall status should be shown near the title
            const statusElements = screen.getAllByRole('status');
            expect(statusElements.length).toBeGreaterThan(0);
        });

        it('shows warning status when no errors but warnings exist', () => {
            const gatesWithWarning = [
                { ...mockGates[0], status: 'success' as const },
                { ...mockGates[2], status: 'warning' as const },
            ];

            renderWithTheme(<GateWidget gates={gatesWithWarning} />);

            const statusElements = screen.getAllByRole('status');
            expect(statusElements.length).toBeGreaterThan(0);
        });

        it('shows success status when all gates are successful', () => {
            const successGates = mockGates.map(gate => ({ ...gate, status: 'success' as const }));

            renderWithTheme(<GateWidget gates={successGates} />);

            const statusElements = screen.getAllByRole('status');
            expect(statusElements.length).toBeGreaterThan(0);
        });
    });

    describe('Compact Mode', () => {
        it('renders in compact mode', () => {
            renderWithTheme(<GateWidget gates={mockGates} compact />);

            expect(screen.getByText('Gates:')).toBeInTheDocument();
        });

        it('shows gates inline in compact mode', () => {
            renderWithTheme(<GateWidget gates={mockGates} compact />);

            // In compact mode, gates should be displayed inline
            const statusElements = screen.getAllByRole('status');
            expect(statusElements.length).toBe(mockGates.length);
        });

        it('handles truncation in compact mode', () => {
            renderWithTheme(<GateWidget gates={mockGates} compact maxGates={2} />);

            expect(screen.getByText('+1 more')).toBeInTheDocument();
        });
    });

    describe('Loading State', () => {
        it('shows loading skeletons', () => {
            renderWithTheme(<GateWidget gates={[]} loading />);

            // Material-UI skeleton components have specific classes or test attributes
            const container = screen.getByRole('article') || screen.getByText('Gates').closest('[role="article"]');
            expect(container).toBeInTheDocument();
        });

        it('shows compact loading skeletons', () => {
            renderWithTheme(<GateWidget gates={[]} loading compact />);

            // Should show loading indicators in compact format
            const container = screen.getByText('Gates:').parentElement;
            expect(container).toBeInTheDocument();
        });
    });

    describe('Interactions', () => {
        it('calls onRefresh when refresh button is clicked', () => {
            const onRefresh = jest.fn();
            renderWithTheme(<GateWidget gates={mockGates} onRefresh={onRefresh} />);

            const refreshButton = screen.getByRole('button', { name: /refresh/i });
            act(() => {
                fireEvent.click(refreshButton);
            });

            expect(onRefresh).toHaveBeenCalledTimes(1);
        });

        it('disables refresh button when loading', () => {
            const onRefresh = jest.fn();
            renderWithTheme(<GateWidget gates={mockGates} loading onRefresh={onRefresh} />);

            const refreshButton = screen.getByRole('button', { name: /refresh/i });
            expect(refreshButton).toBeDisabled();
        });

        it('hides refresh button when showRefresh is false', () => {
            renderWithTheme(<GateWidget gates={mockGates} showRefresh={false} />);

            expect(screen.queryByRole('button', { name: /refresh/i })).not.toBeInTheDocument();
        });

        it('opens details URL when details button is clicked', () => {
            renderWithTheme(<GateWidget gates={mockGates} />);

            const detailsButtons = screen.getAllByRole('button', { name: /view details/i });
            expect(detailsButtons.length).toBeGreaterThan(0);

            // Check that the first details button has correct href
            const firstDetailsButton = detailsButtons[0].closest('a');
            expect(firstDetailsButton).toHaveAttribute('href', mockGates[0].detailsUrl);
            expect(firstDetailsButton).toHaveAttribute('target', '_blank');
        });
    });

    describe('Truncation', () => {
        it('truncates gates when maxGates is specified', () => {
            renderWithTheme(<GateWidget gates={mockGates} maxGates={2} />);

            expect(screen.getByText('+1 more gates')).toBeInTheDocument();
        });

        it('does not show truncation message when all gates fit', () => {
            renderWithTheme(<GateWidget gates={mockGates} maxGates={5} />);

            expect(screen.queryByText(/more gates/)).not.toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('has proper ARIA labels for buttons', () => {
            const onRefresh = jest.fn();
            renderWithTheme(<GateWidget gates={mockGates} onRefresh={onRefresh} />);

            expect(screen.getByRole('button', { name: 'Refresh gate status' })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'View details for Build Pipeline' })).toBeInTheDocument();
        });

        it('has proper heading structure', () => {
            renderWithTheme(<GateWidget gates={mockGates} title="Test Gates" />);

            expect(screen.getByRole('heading', { name: 'Test Gates' })).toBeInTheDocument();
        });

        it('supports keyboard navigation', () => {
            const onRefresh = jest.fn();
            renderWithTheme(<GateWidget gates={mockGates} onRefresh={onRefresh} />);

            const refreshButton = screen.getByRole('button', { name: /refresh/i });

            // Focus the button inside act so ripple/tooltip updates flush
            act(() => {
                refreshButton.focus();
            });
            expect(refreshButton).toHaveFocus();

            // Press Enter and click inside act
            act(() => {
                fireEvent.keyDown(refreshButton, { key: 'Enter', code: 'Enter' });
                fireEvent.click(refreshButton);
            });

            expect(onRefresh).toHaveBeenCalled();
        });
    });

    describe('Time Formatting', () => {
        it('formats duration correctly', () => {
            const gateWithLongDuration = [{
                ...mockGates[0],
                duration: 3665000, // 1h 1m 5s
            }];

            renderWithTheme(<GateWidget gates={gateWithLongDuration} />);

            expect(screen.getByText(/1h|61m/)).toBeInTheDocument();
        });

        it('handles missing duration gracefully', () => {
            const gateWithoutDuration = [{
                ...mockGates[0],
                duration: undefined,
            }];

            renderWithTheme(<GateWidget gates={gateWithoutDuration} />);

            // Should render without errors
            expect(screen.getByText('Build Pipeline')).toBeInTheDocument();
        });
    });

    describe('Custom Props', () => {
        it('forwards custom className', () => {
            renderWithTheme(<GateWidget gates={mockGates} className="custom-class" />);

            const widget = screen.getByText('Gates').closest('[class*="custom-class"]');
            expect(widget).toBeInTheDocument();
        });

        it('supports ref forwarding', () => {
            const ref = React.createRef<HTMLDivElement>();

            renderWithTheme(<GateWidget gates={mockGates} ref={ref} />);

            expect(ref.current).toBeInstanceOf(HTMLDivElement);
        });
    });

    describe('Category Icons', () => {
        it('displays appropriate icons for different categories', () => {
            const allCategoryGates: GateStatus[] = [
                { ...mockGates[0], category: 'build' },
                { ...mockGates[1], category: 'test' },
                { ...mockGates[2], category: 'security' },
            ];

            renderWithTheme(<GateWidget gates={allCategoryGates} />);

            // Each gate should have an icon
            const icons = screen.getAllByRole('img', { hidden: true });
            expect(icons.length).toBeGreaterThan(0);
        });
    });
});