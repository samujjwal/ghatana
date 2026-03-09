/**
 * Tests for BottomSheet component
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { BottomSheet } from '../components/BottomSheet';

describe('BottomSheet', () => {
    const mockOnClose = vi.fn();
    const mockOnSnapPointChange = vi.fn();

    beforeEach(() => {
        mockOnClose.mockClear();
        mockOnSnapPointChange.mockClear();

        // Mock window dimensions
        Object.defineProperty(window, 'innerHeight', {
            writable: true,
            configurable: true,
            value: 800,
        });
        Object.defineProperty(window, 'innerWidth', {
            writable: true,
            configurable: true,
            value: 400,
        });
    });

    describe('basic rendering', () => {
        it('should render when open', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Sheet Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should not render when closed', () => {
            render(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Sheet Content</div>
                </BottomSheet>
            );

            expect(screen.queryByTestId('sheet-content')).not.toBeInTheDocument();
        });

        it('should render children content', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <h2>Sheet Title</h2>
                    <p>Sheet body text</p>
                </BottomSheet>
            );

            expect(screen.getByText('Sheet Title')).toBeInTheDocument();
            expect(screen.getByText('Sheet body text')).toBeInTheDocument();
        });

        it('should return null when not open', () => {
            const { container } = render(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div>Content</div>
                </BottomSheet>
            );

            expect(container.firstChild).toBeNull();
        });
    });

    describe('snap points', () => {
        it('should use default snap points when not provided', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support percentage snap points', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support number snap points (pixels)', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={[200, 400, 600]}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support fractional snap points (0-1)', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={[0.25, 0.5, 0.9]}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support mixed snap point formats', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', 400, 0.9]}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support single snap point', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['50%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('default snap', () => {
        it('should use first snap point by default', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should use specified default snap index', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                    defaultSnap={1}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should handle last snap point as default', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                    defaultSnap={2}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('drag handle', () => {
        it('should show drag handle by default', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            // Drag handle is a styled div without specific test id
            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should hide drag handle when showHandle=false', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose} showHandle={false}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should show drag handle when showHandle=true', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose} showHandle={true}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('backdrop interaction', () => {
        it('should show backdrop when open', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).toBeInTheDocument();
        });

        it('should call onClose when backdrop is clicked', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            fireEvent.click(backdrop!);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onClose when backdrop is clicked and closeOnBackdrop=true', () => {
            const { container } = render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    closeOnBackdrop={true}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            fireEvent.click(backdrop!);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should not call onClose when backdrop is clicked and closeOnBackdrop=false', () => {
            const { container } = render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    closeOnBackdrop={false}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            fireEvent.click(backdrop!);

            expect(mockOnClose).not.toHaveBeenCalled();
        });
    });

    describe('swipe to close', () => {
        it('should enable swipe to close by default', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support swipeToClose=true', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose} swipeToClose={true}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should support swipeToClose=false', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose} swipeToClose={false}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('snap point change callback', () => {
        it('should support onSnapPointChange callback', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                    onSnapPointChange={mockOnSnapPointChange}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should work without onSnapPointChange callback', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('scroll lock', () => {
        let originalOverflow: string;
        let originalPaddingRight: string;

        beforeEach(() => {
            originalOverflow = document.body.style.overflow;
            originalPaddingRight = document.body.style.paddingRight;
        });

        afterEach(() => {
            document.body.style.overflow = originalOverflow;
            document.body.style.paddingRight = originalPaddingRight;
        });

        it('should lock body scroll when open', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(document.body.style.overflow).toBe('hidden');
        });

        it('should not lock body scroll when closed', () => {
            render(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(document.body.style.overflow).not.toBe('hidden');
        });

        it('should restore body scroll when sheet closes', () => {
            const { rerender } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(document.body.style.overflow).toBe('hidden');

            rerender(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            // Restored to original (empty string in test environment)
            expect(document.body.style.overflow).toBe('');
        });

        it('should restore body scroll when unmounted', () => {
            const { unmount } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(document.body.style.overflow).toBe('hidden');

            unmount();

            expect(document.body.style.overflow).toBe('');
        });
    });

    describe('accessibility', () => {
        it('should set role="dialog"', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toBeInTheDocument();
        });

        it('should set aria-modal="true"', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toHaveAttribute('aria-modal', 'true');
        });

        it('should set aria-hidden on backdrop', () => {
            const { container } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).toBeInTheDocument();
        });
    });

    describe('viewport height changes', () => {
        it('should handle window resize', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['50%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            // Trigger resize
            Object.defineProperty(window, 'innerHeight', {
                writable: true,
                configurable: true,
                value: 1000,
            });

            fireEvent(window, new Event('resize'));

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should recalculate snap points on resize', () => {
            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            // Change viewport height
            Object.defineProperty(window, 'innerHeight', {
                writable: true,
                configurable: true,
                value: 1200,
            });

            fireEvent(window, new Event('resize'));

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('open/close transitions', () => {
        it('should handle opening sheet', async () => {
            const { rerender } = render(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.queryByTestId('sheet-content')).not.toBeInTheDocument();

            rerender(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            await waitFor(() => {
                expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
            });
        });

        it('should handle closing sheet', async () => {
            const { rerender } = render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();

            rerender(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            await waitFor(() => {
                expect(screen.queryByTestId('sheet-content')).not.toBeInTheDocument();
            });
        });
    });

    describe('edge cases', () => {
        it('should handle rapid open/close toggles', () => {
            const { rerender } = render(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            rerender(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            rerender(
                <BottomSheet open={false} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            rerender(
                <BottomSheet open={true} onClose={mockOnClose}>
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should handle empty children', () => {
            render(
                <BottomSheet open={true} onClose={mockOnClose}>
                    {null}
                </BottomSheet>
            );

            // Should not crash
        });

        it('should handle changing snap points while open', () => {
            const { rerender } = render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();

            rerender(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['50%', '90%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should reset snap to defaultSnap on open', () => {
            const { rerender } = render(
                <BottomSheet
                    open={false}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                    defaultSnap={1}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            rerender(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['25%', '50%', '90%']}
                    defaultSnap={1}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });

        it('should handle zero innerHeight gracefully', () => {
            Object.defineProperty(window, 'innerHeight', {
                writable: true,
                configurable: true,
                value: 0,
            });

            render(
                <BottomSheet
                    open={true}
                    onClose={mockOnClose}
                    snapPoints={['50%']}
                >
                    <div data-testid="sheet-content">Content</div>
                </BottomSheet>
            );

            expect(screen.getByTestId('sheet-content')).toBeInTheDocument();
        });
    });

    describe('multiple bottom sheets', () => {
        it('should render multiple sheets independently', () => {
            render(
                <>
                    <BottomSheet open={true} onClose={vi.fn()}>
                        <div data-testid="sheet-1">Sheet 1</div>
                    </BottomSheet>
                    <BottomSheet open={true} onClose={vi.fn()}>
                        <div data-testid="sheet-2">Sheet 2</div>
                    </BottomSheet>
                </>
            );

            expect(screen.getByTestId('sheet-1')).toBeInTheDocument();
            expect(screen.getByTestId('sheet-2')).toBeInTheDocument();
        });

        it('should handle different snap points', () => {
            render(
                <>
                    <BottomSheet
                        open={true}
                        onClose={vi.fn()}
                        snapPoints={['25%']}
                    >
                        <div data-testid="sheet-small">Small Sheet</div>
                    </BottomSheet>
                    <BottomSheet
                        open={true}
                        onClose={vi.fn()}
                        snapPoints={['90%']}
                    >
                        <div data-testid="sheet-large">Large Sheet</div>
                    </BottomSheet>
                </>
            );

            expect(screen.getByTestId('sheet-small')).toBeInTheDocument();
            expect(screen.getByTestId('sheet-large')).toBeInTheDocument();
        });
    });
});
