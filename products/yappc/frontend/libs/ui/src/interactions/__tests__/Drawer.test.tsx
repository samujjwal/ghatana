/**
 * Tests for Drawer component
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { Drawer } from '../components/Drawer';

import type { DrawerAnchor, DrawerVariant } from '../types';

describe('Drawer', () => {
    const mockOnClose = vi.fn();

    beforeEach(() => {
        mockOnClose.mockClear();
    });

    describe('basic rendering', () => {
        it('should render when open', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Drawer Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should not render when closed', () => {
            render(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Drawer Content</div>
                </Drawer>
            );

            expect(screen.queryByTestId('drawer-content')).not.toBeInTheDocument();
        });

        it('should render children content', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <h2>Drawer Title</h2>
                    <p>Drawer body text</p>
                </Drawer>
            );

            expect(screen.getByText('Drawer Title')).toBeInTheDocument();
            expect(screen.getByText('Drawer body text')).toBeInTheDocument();
        });
    });

    describe('anchors', () => {
        const anchors: DrawerAnchor[] = ['left', 'right', 'top', 'bottom'];

        anchors.forEach((anchor) => {
            it(`should render with ${anchor} anchor`, () => {
                const { container } = render(
                    <Drawer open={true} onClose={mockOnClose} anchor={anchor}>
                        <div data-testid="drawer-content">Content</div>
                    </Drawer>
                );

                expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
                expect(container.querySelector('[role="dialog"]')).toBeInTheDocument();
            });
        });

        it('should default to left anchor', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });
    });

    describe('variants', () => {
        const variants: DrawerVariant[] = ['temporary', 'persistent', 'permanent'];

        variants.forEach((variant) => {
            it(`should render with ${variant} variant`, () => {
                render(
                    <Drawer open={true} onClose={mockOnClose} variant={variant}>
                        <div data-testid="drawer-content">Content</div>
                    </Drawer>
                );

                expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
            });
        });

        it('should default to temporary variant', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should show backdrop for temporary variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).toBeInTheDocument();
        });

        it('should not show backdrop for persistent variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).not.toBeInTheDocument();
        });

        it('should not show backdrop for permanent variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="permanent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).not.toBeInTheDocument();
        });

        it('should set aria-modal for temporary variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toHaveAttribute('aria-modal', 'true');
        });

        it('should not set aria-modal for persistent variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toHaveAttribute('aria-modal', 'false');
        });

        it('should render permanent variant without role=dialog', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="permanent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).not.toBeInTheDocument();
        });
    });

    describe('dimensions', () => {
        it('should support custom width for left anchor', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} anchor="left" width={400}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should support custom width for right anchor', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} anchor="right" width="50%">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should support custom height for top anchor', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} anchor="top" height={200}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should support custom height for bottom anchor', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} anchor="bottom" height="30vh">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });
    });

    describe('backdrop interaction', () => {
        it('should call onClose when backdrop is clicked (temporary)', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).toBeInTheDocument();

            fireEvent.click(backdrop!);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onClose when backdrop is clicked and closeOnBackdrop=true', () => {
            const { container } = render(
                <Drawer
                    open={true}
                    onClose={mockOnClose}
                    variant="temporary"
                    closeOnBackdrop={true}
                >
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            fireEvent.click(backdrop!);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should not call onClose when backdrop is clicked and closeOnBackdrop=false', () => {
            const { container } = render(
                <Drawer
                    open={true}
                    onClose={mockOnClose}
                    variant="temporary"
                    closeOnBackdrop={false}
                >
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            fireEvent.click(backdrop!);

            expect(mockOnClose).not.toHaveBeenCalled();
        });

        it('should not show backdrop for persistent variant by default', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).not.toBeInTheDocument();
        });
    });

    describe('escape key handling', () => {
        it('should call onClose when Escape key is pressed', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Escape' });

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onClose when Escape key is pressed and closeOnEscape=true', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} closeOnEscape={true}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Escape' });

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should not call onClose when Escape key is pressed and closeOnEscape=false', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} closeOnEscape={false}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Escape' });

            expect(mockOnClose).not.toHaveBeenCalled();
        });

        it('should not call onClose on other keys', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Enter' });
            fireEvent.keyDown(document, { key: 'Tab' });
            fireEvent.keyDown(document, { key: 'Space' });

            expect(mockOnClose).not.toHaveBeenCalled();
        });

        it('should not register escape listener when closed', () => {
            const { rerender } = render(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Escape' });
            expect(mockOnClose).not.toHaveBeenCalled();

            rerender(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            fireEvent.keyDown(document, { key: 'Escape' });
            expect(mockOnClose).toHaveBeenCalledTimes(1);
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

        it('should lock body scroll when open (temporary variant)', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).toBe('hidden');
        });

        it('should not lock body scroll when closed', () => {
            render(
                <Drawer open={false} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).not.toBe('hidden');
        });

        it('should not lock body scroll for persistent variant', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).not.toBe('hidden');
        });

        it('should not lock body scroll for permanent variant', () => {
            render(
                <Drawer open={true} onClose={mockOnClose} variant="permanent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).not.toBe('hidden');
        });

        it('should restore body scroll when drawer closes', () => {
            const { rerender } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).toBe('hidden');

            rerender(
                <Drawer open={false} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            // Restored to original (empty string in test environment)
            expect(document.body.style.overflow).toBe('');
        });

        it('should restore body scroll when unmounted', () => {
            const { unmount } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(document.body.style.overflow).toBe('hidden');

            unmount();

            expect(document.body.style.overflow).toBe('');
        });
    });

    describe('accessibility', () => {
        it('should set role="dialog" for temporary variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toBeInTheDocument();
        });

        it('should set role="dialog" for persistent variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).toBeInTheDocument();
        });

        it('should not set role="dialog" for permanent variant', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="permanent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const dialog = container.querySelector('[role="dialog"]');
            expect(dialog).not.toBeInTheDocument();
        });

        it('should set aria-hidden on backdrop', () => {
            const { container } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            const backdrop = container.querySelector('[aria-hidden="true"]');
            expect(backdrop).toBeInTheDocument();
        });
    });

    describe('open/close transitions', () => {
        it('should handle opening drawer', async () => {
            const { rerender } = render(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.queryByTestId('drawer-content')).not.toBeInTheDocument();

            rerender(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            await waitFor(() => {
                expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
            });
        });

        it('should handle closing drawer', async () => {
            const { rerender } = render(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();

            rerender(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            await waitFor(() => {
                expect(screen.queryByTestId('drawer-content')).not.toBeInTheDocument();
            });
        });
    });

    describe('multiple drawers', () => {
        it('should render multiple drawers independently', () => {
            render(
                <>
                    <Drawer open={true} onClose={vi.fn()} anchor="left">
                        <div data-testid="drawer-left">Left Drawer</div>
                    </Drawer>
                    <Drawer open={true} onClose={vi.fn()} anchor="right">
                        <div data-testid="drawer-right">Right Drawer</div>
                    </Drawer>
                </>
            );

            expect(screen.getByTestId('drawer-left')).toBeInTheDocument();
            expect(screen.getByTestId('drawer-right')).toBeInTheDocument();
        });

        it('should render drawers with different variants', () => {
            render(
                <>
                    <Drawer open={true} onClose={vi.fn()} variant="temporary">
                        <div data-testid="drawer-temporary">Temporary</div>
                    </Drawer>
                    <Drawer open={true} onClose={vi.fn()} variant="permanent">
                        <div data-testid="drawer-permanent">Permanent</div>
                    </Drawer>
                </>
            );

            expect(screen.getByTestId('drawer-temporary')).toBeInTheDocument();
            expect(screen.getByTestId('drawer-permanent')).toBeInTheDocument();
        });
    });

    describe('edge cases', () => {
        it('should handle rapid open/close toggles', () => {
            const { rerender } = render(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            rerender(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            rerender(
                <Drawer open={false} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            rerender(
                <Drawer open={true} onClose={mockOnClose}>
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should handle empty children', () => {
            render(
                <Drawer open={true} onClose={mockOnClose}>
                    {null}
                </Drawer>
            );

            // Should not crash
        });

        it('should handle changing anchor while open', () => {
            const { rerender } = render(
                <Drawer open={true} onClose={mockOnClose} anchor="left">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();

            rerender(
                <Drawer open={true} onClose={mockOnClose} anchor="right">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });

        it('should handle changing variant while open', () => {
            const { rerender } = render(
                <Drawer open={true} onClose={mockOnClose} variant="temporary">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();

            rerender(
                <Drawer open={true} onClose={mockOnClose} variant="persistent">
                    <div data-testid="drawer-content">Content</div>
                </Drawer>
            );

            expect(screen.getByTestId('drawer-content')).toBeInTheDocument();
        });
    });
});
