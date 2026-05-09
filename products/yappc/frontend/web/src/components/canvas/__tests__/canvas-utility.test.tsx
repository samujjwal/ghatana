/**
 * Unit tests for canvas empty state and utility components:
 * - CanvasEmptyState
 * - LoadingFallback
 * - SkipLink
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { CanvasEmptyState } from '../CanvasEmptyState';
import LoadingFallback from '../../LoadingFallback';
import { SkipLink } from '../../accessibility/SkipLink';

// ---------------------------------------------------------------------------
// CanvasEmptyState
// ---------------------------------------------------------------------------

describe('CanvasEmptyState', () => {
    it('renders primary message', () => {
        render(<CanvasEmptyState message="No nodes yet" />);
        expect(screen.getByText('No nodes yet')).toBeTruthy();
    });

    it('renders description when provided', () => {
        render(<CanvasEmptyState message="Empty" description="Start by adding a node" />);
        expect(screen.getByText('Start by adding a node')).toBeTruthy();
    });

    it('does not render description when omitted', () => {
        render(<CanvasEmptyState message="Empty" />);
        expect(screen.queryByText('Start by adding a node')).toBeNull();
    });

    it('renders primary action button with label', () => {
        const onClick = vi.fn();
        render(
            <CanvasEmptyState
                message="Empty"
                primaryAction={{ label: 'Add Node', onClick }}
            />,
        );
        const btn = screen.getByRole('button', { name: /add node/i });
        expect(btn).toBeTruthy();
    });

    it('calls primary action onClick', () => {
        const onClick = vi.fn();
        render(
            <CanvasEmptyState
                message="Empty"
                primaryAction={{ label: 'Add Node', onClick }}
            />,
        );
        fireEvent.click(screen.getByRole('button', { name: /add node/i }));
        expect(onClick).toHaveBeenCalledTimes(1);
    });

    it('renders secondary action button when provided', () => {
        const onClick = vi.fn();
        render(
            <CanvasEmptyState
                message="Empty"
                secondaryAction={{ label: 'Import', onClick }}
            />,
        );
        expect(screen.getByRole('button', { name: /import/i })).toBeTruthy();
    });

    it('calls secondary action onClick', () => {
        const onClick = vi.fn();
        render(
            <CanvasEmptyState
                message="Empty"
                secondaryAction={{ label: 'Import', onClick }}
            />,
        );
        fireEvent.click(screen.getByRole('button', { name: /import/i }));
        expect(onClick).toHaveBeenCalledTimes(1);
    });

    it('does not render action buttons when none provided', () => {
        render(<CanvasEmptyState message="Empty" />);
        expect(screen.queryByRole('button')).toBeNull();
    });

    it('renders AI suggestions list', () => {
        render(
            <CanvasEmptyState
                message="Empty"
                aiSuggestions={['Add a microservice', 'Design a database']}
            />,
        );
        expect(screen.getByText('Add a microservice')).toBeTruthy();
        expect(screen.getByText('Design a database')).toBeTruthy();
    });

    it('renders "Suggested Improvements" header when suggestions present', () => {
        render(
            <CanvasEmptyState message="Empty" aiSuggestions={['Do something']} />,
        );
        expect(screen.getByText('Suggested Improvements')).toBeTruthy();
    });

    it('does not render AI suggestions section when array is empty', () => {
        render(<CanvasEmptyState message="Empty" aiSuggestions={[]} />);
        expect(screen.queryByText('Suggested Improvements')).toBeNull();
    });

    it('renders custom icon when provided', () => {
        render(
            <CanvasEmptyState
                message="Empty"
                icon={<span data-testid="custom-icon">★</span>}
            />,
        );
        expect(screen.getByTestId('custom-icon')).toBeTruthy();
    });

    it('both primary and secondary buttons present simultaneously', () => {
        render(
            <CanvasEmptyState
                message="Empty"
                primaryAction={{ label: 'Create', onClick: vi.fn() }}
                secondaryAction={{ label: 'Import', onClick: vi.fn() }}
            />,
        );
        expect(screen.getByRole('button', { name: /create/i })).toBeTruthy();
        expect(screen.getByRole('button', { name: /import/i })).toBeTruthy();
    });
});

// ---------------------------------------------------------------------------
// LoadingFallback
// ---------------------------------------------------------------------------

describe('LoadingFallback', () => {
    it('renders "Loading…" text', () => {
        render(<LoadingFallback />);
        expect(screen.getByText('Loading…')).toBeTruthy();
    });

    it('renders the spinner SVG', () => {
        const { container } = render(<LoadingFallback />);
        expect(container.querySelector('svg')).toBeTruthy();
    });

    it('renders centred container', () => {
        const { container } = render(<LoadingFallback />);
        const wrapper = container.firstElementChild as HTMLElement;
        expect(wrapper.style.display).toBe('flex');
        expect(wrapper.style.justifyContent).toBe('center');
    });
});

// ---------------------------------------------------------------------------
// SkipLink
// ---------------------------------------------------------------------------

describe('SkipLink', () => {
    it('renders with default text', () => {
        render(<SkipLink targetId="main-content" />);
        expect(screen.getByText('Skip to main content')).toBeTruthy();
    });

    it('renders with custom children', () => {
        render(<SkipLink targetId="main">Jump to content</SkipLink>);
        expect(screen.getByText('Jump to content')).toBeTruthy();
    });

    it('has href pointing to targetId', () => {
        render(<SkipLink targetId="main-area" />);
        const link = screen.getByRole('link');
        expect(link.getAttribute('href')).toBe('#main-area');
    });

    it('renders as an anchor element', () => {
        render(<SkipLink targetId="content" />);
        expect(screen.getByRole('link')).toBeTruthy();
    });

    it('calls focus on target element when clicked', () => {
        // Set up a target element
        const target = document.createElement('div');
        target.id = 'skip-target';
        target.tabIndex = -1;
        // jsdom does not implement scrollIntoView — stub it
        target.scrollIntoView = vi.fn();
        document.body.appendChild(target);
        const focusSpy = vi.spyOn(target, 'focus');

        render(<SkipLink targetId="skip-target" />);
        fireEvent.click(screen.getByRole('link'));
        expect(focusSpy).toHaveBeenCalled();

        // Cleanup
        document.body.removeChild(target);
    });

    it('does not throw when target element does not exist', () => {
        render(<SkipLink targetId="non-existent" />);
        expect(() => {
            fireEvent.click(screen.getByRole('link'));
        }).not.toThrow();
    });
});
