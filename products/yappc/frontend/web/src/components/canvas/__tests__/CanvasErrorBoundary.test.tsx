import { createRef } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CanvasErrorBoundary } from '../CanvasErrorBoundary';

describe('CanvasErrorBoundary', () => {
    it('renders retry as a shared UI button and recovers after reset', () => {
        const boundaryRef = createRef<CanvasErrorBoundary>();

        render(
            <CanvasErrorBoundary label="Diagram" ref={boundaryRef}>
                <div>Canvas recovered</div>
            </CanvasErrorBoundary>
        );

        act(() => {
            boundaryRef.current?.setState({ hasError: true, error: new Error('Canvas failed') });
        });

        const retry = screen.getByRole('button', { name: /retry/i });

        expect(screen.getByRole('alert')).toHaveTextContent('Diagram encountered an error');
        expect(retry).toHaveClass('inline-flex');

        fireEvent.click(retry);

        expect(screen.getByText('Canvas recovered')).toBeInTheDocument();
    });

    it('hides the retry action when reset is disabled', () => {
        const boundaryRef = createRef<CanvasErrorBoundary>();

        render(
            <CanvasErrorBoundary label="Inspector" showReset={false} ref={boundaryRef}>
                <div>Inspector recovered</div>
            </CanvasErrorBoundary>
        );

        act(() => {
            boundaryRef.current?.setState({ hasError: true, error: new Error('Inspector failed') });
        });

        expect(screen.queryByRole('button', { name: /retry/i })).not.toBeInTheDocument();
    });
});
