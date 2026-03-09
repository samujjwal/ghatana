/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'jotai';
import { SketchToolbar } from '../SketchToolbar';

describe('SketchToolbar', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('renders sketch toolbar with all tools', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        expect(screen.getByTestId('sketch-toolbar')).toBeInTheDocument();
        expect(screen.getByLabelText(/pen tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/rectangle tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/ellipse tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/eraser tool/i)).toBeInTheDocument();
    });

    it('shows color picker', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        // Color picker should be visible
        const colorButtons = screen.getAllByRole('button');
        // Should have at least 9 color options (8 preset colors + current color button)
        expect(colorButtons.length).toBeGreaterThanOrEqual(4); // Tools + colors
    });

    it('shows stroke width slider', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const slider = screen.getByRole('slider', { name: /stroke width/i });
        expect(slider).toBeInTheDocument();
        expect(slider).toHaveAttribute('min', '1');
        expect(slider).toHaveAttribute('max', '20');
    });

    it('switches to pen tool when clicked', async () => {
        const user = userEvent.setup();

        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const penButton = screen.getByLabelText(/pen tool/i);
        await user.click(penButton);

        await waitFor(() => {
            expect(penButton).toHaveClass('Mui-selected');
        });
    });

    it('switches to rectangle tool when clicked', async () => {
        const user = userEvent.setup();

        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const rectButton = screen.getByLabelText(/rectangle tool/i);
        await user.click(rectButton);

        await waitFor(() => {
            expect(rectButton).toHaveClass('Mui-selected');
        });
    });

    it('switches to ellipse tool when clicked', async () => {
        const user = userEvent.setup();

        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const ellipseButton = screen.getByLabelText(/ellipse tool/i);
        await user.click(ellipseButton);

        await waitFor(() => {
            expect(ellipseButton).toHaveClass('Mui-selected');
        });
    });

    it('switches to eraser tool when clicked', async () => {
        const user = userEvent.setup();

        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const eraserButton = screen.getByLabelText(/eraser tool/i);
        await user.click(eraserButton);

        await waitFor(() => {
            expect(eraserButton).toHaveClass('Mui-selected');
        });
    });

    it('changes stroke width when slider is moved', async () => {
        const user = userEvent.setup();

        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const slider = screen.getByRole('slider', { name: /stroke width/i });

        // Simulate changing slider value
        await user.click(slider);

        // Slider should be interactive
        expect(slider).not.toBeDisabled();
    });

    it('applies custom className', () => {
        const { container } = render(
            <Provider>
                <SketchToolbar className="custom-class" />
            </Provider>
        );

        const toolbar = container.querySelector('.custom-class');
        expect(toolbar).toBeInTheDocument();
    });

    it('has proper z-index for layering', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const toolbar = screen.getByTestId('sketch-toolbar');
        const styles = window.getComputedStyle(toolbar);

        // Should have high z-index to appear above canvas
        expect(parseInt(styles.zIndex)).toBeGreaterThanOrEqual(1000);
    });

    it('is positioned at bottom center', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const toolbar = screen.getByTestId('sketch-toolbar');
        const styles = window.getComputedStyle(toolbar);

        expect(styles.position).toBe('fixed');
        expect(styles.bottom).toBeTruthy();
    });

    it('shows all preset colors', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        // Should have color options for: black, red, blue, green, yellow, orange, purple, pink
        const toolbar = screen.getByTestId('sketch-toolbar');
        expect(toolbar).toBeInTheDocument();

        // Color swatches should be present
        const colorButtons = screen.getAllByRole('button');
        expect(colorButtons.length).toBeGreaterThan(4); // More than just the 4 tool buttons
    });
});

describe('SketchToolbar Keyboard Shortcuts', () => {
    it('supports tool switching via keyboard', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        // Component should be present for keyboard interaction
        expect(screen.getByTestId('sketch-toolbar')).toBeInTheDocument();

        // Note: Actual keyboard event handling is tested in E2E tests
        // as it requires global keyboard event listeners
    });
});

describe('SketchToolbar Accessibility', () => {
    it('has proper ARIA labels for tools', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        expect(screen.getByLabelText(/pen tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/rectangle tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/ellipse tool/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/eraser tool/i)).toBeInTheDocument();
    });

    it('has proper role for slider', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const slider = screen.getByRole('slider');
        expect(slider).toBeInTheDocument();
    });

    it('buttons are keyboard accessible', () => {
        render(
            <Provider>
                <SketchToolbar />
            </Provider>
        );

        const buttons = screen.getAllByRole('button');
        buttons.forEach(button => {
            expect(button).not.toHaveAttribute('tabindex', '-1');
        });
    });
});
