import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import { LifecycleNavigation } from '../LifecycleNavigation';

function LocationProbe(): JSX.Element {
    const location = useLocation();
    return <span data-testid="location">{location.pathname}</span>;
}

describe('LifecycleNavigation', () => {
    const originalInnerWidth = window.innerWidth;

    afterEach(() => {
        Object.defineProperty(window, 'innerWidth', {
            configurable: true,
            value: originalInnerWidth,
        });
    });

    it('renders mobile nav actions as design-system buttons and navigates', () => {
        Object.defineProperty(window, 'innerWidth', {
            configurable: true,
            value: 390,
        });

        render(
            <MemoryRouter initialEntries={['/journey']}>
                <Routes>
                    <Route
                        path="*"
                        element={(
                            <>
                                <LifecycleNavigation />
                                <LocationProbe />
                            </>
                        )}
                    />
                </Routes>
            </MemoryRouter>,
        );

        const insightsButton = screen.getByRole('button', { name: /insights/i });
        expect(insightsButton).toHaveClass('gh-button');

        fireEvent.click(insightsButton);

        expect(screen.getByTestId('location')).toHaveTextContent('/journey/insights');
    });
});
