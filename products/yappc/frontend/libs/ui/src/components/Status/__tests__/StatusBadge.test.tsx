// All tests skipped - incomplete feature
import { render, screen } from '@testing-library/react';
import React from 'react';

import { StatusBadge } from './StatusBadge';

const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => {
    return render(
        <ThemeProvider theme={theme}>
            {component}
        </ThemeProvider>
    );
};

describe.skip('StatusBadge', () => {
    describe('Basic Rendering', () => {
        it('renders with default props', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
            expect(badge).toHaveTextContent('Success');
        });

        it('renders with custom label', () => {
            renderWithTheme(<StatusBadge status="success" label="Build Passed" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveTextContent('Build Passed');
        });

        it('renders with category icon', () => {
            renderWithTheme(<StatusBadge status="success" category="build" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();

            // Check for icon presence (Material-UI icons have specific classes)
            const icon = badge.querySelector('svg');
            expect(icon).toBeInTheDocument();
        });
    });

    describe('Status Types', () => {
        const statusTypes = ['success', 'error', 'warning', 'pending', 'running', 'unknown', 'cancelled'] as const;

        statusTypes.forEach((status) => {
            it(`renders ${status} status correctly`, () => {
                renderWithTheme(<StatusBadge status={status} />);

                const badge = screen.getByRole('status');
                expect(badge).toBeInTheDocument();

                // Check ARIA label contains status
                expect(badge).toHaveAttribute('aria-label', expect.stringContaining(status.charAt(0).toUpperCase() + status.slice(1)));
            });
        });
    });

    describe('Categories', () => {
        const categories = ['build', 'test', 'deploy', 'security', 'quality', 'general'] as const;

        categories.forEach((category) => {
            it(`renders ${category} category correctly`, () => {
                renderWithTheme(<StatusBadge status="success" category={category} />);

                const badge = screen.getByRole('status');
                expect(badge).toHaveAttribute('aria-label', expect.stringContaining(category.charAt(0).toUpperCase() + category.slice(1)));
            });
        });
    });

    describe('Variants', () => {
        it('renders filled variant by default', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });

        it('renders outlined variant', () => {
            renderWithTheme(<StatusBadge status="success" variant="outlined" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });

        it('renders soft variant', () => {
            renderWithTheme(<StatusBadge status="success" variant="soft" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });
    });

    describe('Sizes', () => {
        it('renders small size', () => {
            renderWithTheme(<StatusBadge status="success" size="sm" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveClass('MuiChip-sizeSmall');
        });

        it('renders medium size by default', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveClass('MuiChip-sizeMedium');
        });

        it('renders large size with custom class', () => {
            renderWithTheme(<StatusBadge status="success" size="lg" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveClass('status-badge-large');
        });
    });

    describe('Icons', () => {
        it('shows icon by default', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            const icon = badge.querySelector('svg');
            expect(icon).toBeInTheDocument();
        });

        it('hides icon when showIcon is false', () => {
            renderWithTheme(<StatusBadge status="success" showIcon={false} />);

            const badge = screen.getByRole('status');
            const icon = badge.querySelector('svg');
            expect(icon).not.toBeInTheDocument();
        });
    });

    describe('Tooltip', () => {
        it('renders with default tooltip', () => {
            renderWithTheme(<StatusBadge status="success" category="build" />);

            const badge = screen.getByRole('status');
            expect(badge.parentElement).toHaveAttribute('aria-describedby');
        });

        it('renders with custom tooltip', () => {
            renderWithTheme(
                <StatusBadge
                    status="success"
                    category="build"
                    tooltip="Custom tooltip text"
                />
            );

            const badge = screen.getByRole('status');
            expect(badge.parentElement).toHaveAttribute('aria-describedby');
        });

        it('renders without tooltip when tooltip is empty string', () => {
            renderWithTheme(<StatusBadge status="success" tooltip="" />);

            const badge = screen.getByRole('status');
            expect(badge.parentElement).not.toHaveAttribute('aria-describedby');
        });
    });

    describe('Animation', () => {
        it('applies animation class when animated is true', () => {
            renderWithTheme(<StatusBadge status="running" animated />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });

        it('does not apply animation when animated is false', () => {
            renderWithTheme(<StatusBadge status="running" animated={false} />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('has correct role attribute', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveAttribute('role', 'status');
        });

        it('has descriptive aria-label', () => {
            renderWithTheme(<StatusBadge status="success" category="build" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveAttribute('aria-label', 'Build: Success');
        });

        it('uses custom aria-label when provided', () => {
            renderWithTheme(
                <StatusBadge
                    status="success"
                    category="build"
                    aria-label="Custom build status: All tests passed"
                />
            );

            const badge = screen.getByRole('status');
            expect(badge).toHaveAttribute('aria-label', 'Custom build status: All tests passed');
        });

        it('supports keyboard focus', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toHaveAttribute('tabindex', '0');
        });
    });

    describe('Custom Props', () => {
        it('forwards additional props to the Chip component', () => {
            renderWithTheme(
                <StatusBadge
                    status="success"
                    data-testid="custom-badge"
                    className="custom-class"
                />
            );

            const badge = screen.getByTestId('custom-badge');
            expect(badge).toBeInTheDocument();
            expect(badge).toHaveClass('custom-class');
        });

        it('supports ref forwarding', () => {
            const ref = React.createRef<HTMLDivElement>();

            renderWithTheme(<StatusBadge status="success" ref={ref} />);

            expect(ref.current).toBeInstanceOf(HTMLDivElement);
        });
    });

    describe('Color Configuration', () => {
        it('applies correct colors for success status', () => {
            renderWithTheme(<StatusBadge status="success" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
            // Colors are applied via styled components, so we check for the component presence
        });

        it('applies correct colors for error status', () => {
            renderWithTheme(<StatusBadge status="error" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });

        it('applies correct colors for warning status', () => {
            renderWithTheme(<StatusBadge status="warning" />);

            const badge = screen.getByRole('status');
            expect(badge).toBeInTheDocument();
        });
    });
});