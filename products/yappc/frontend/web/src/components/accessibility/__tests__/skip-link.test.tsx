/**
 * SkipLink Accessibility Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { SkipLink } from '../SkipLink';

describe('SkipLink', () => {
    it('renders a link to the target', () => {
        render(<SkipLink targetId="main-content" />);
        const link = screen.getByRole('link');
        expect(link).toBeTruthy();
        expect(link.getAttribute('href')).toBe('#main-content');
    });

    it('renders default text', () => {
        render(<SkipLink targetId="main-content" />);
        expect(screen.getByText('Skip to main content')).toBeTruthy();
    });

    it('renders custom text when children provided', () => {
        render(<SkipLink targetId="main-content">Skip navigation</SkipLink>);
        expect(screen.getByText('Skip navigation')).toBeTruthy();
    });

    it('focuses target element on click', () => {
        const { container } = render(
            <div>
                <SkipLink targetId="main-content" />
                <main id="main-content" tabIndex={-1}>Main content</main>
            </div>
        );
        const link = screen.getByRole('link');
        const main = container.querySelector('#main-content') as HTMLElement;
        const focusSpy = vi.spyOn(main, 'focus');
        fireEvent.click(link);
        expect(focusSpy).toHaveBeenCalled();
    });
});
