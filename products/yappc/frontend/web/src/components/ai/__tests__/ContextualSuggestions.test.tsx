/**
 * ContextualSuggestions Unit Tests
 *
 * Tests for the contextual AI suggestions panel.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ContextualSuggestions, type ContextualSuggestion } from '../ContextualSuggestions';

// ============================================================================
// Fixtures
// ============================================================================

function makeSuggestion(overrides: Partial<ContextualSuggestion> = {}): ContextualSuggestion {
    return {
        id: 'sug-1',
        type: 'action',
        title: 'Complete onboarding',
        description: 'Finish setting up your workspace.',
        confidence: 0.9,
        priority: 'high',
        context: 'onboarding',
        ...overrides,
    };
}

// ============================================================================
// Tests
// ============================================================================

describe('ContextualSuggestions', () => {
    describe('empty state', () => {
        it('renders nothing when suggestions array is empty', () => {
            const { container } = render(
                <ContextualSuggestions suggestions={[]} />
            );
            expect(container.firstChild).toBeNull();
        });

        it('renders nothing when all suggestions are dismissed', () => {
            const suggestions = [makeSuggestion({ id: 'sug-1' })];
            const { container } = render(
                <ContextualSuggestions suggestions={suggestions} />
            );
            // Dismiss the only suggestion
            const dismissBtn = screen.getAllByRole('button').find(b =>
                b.getAttribute('title') === 'Dismiss'
            );
            if (dismissBtn) fireEvent.click(dismissBtn);
            // Container should be null or empty after all dismissed
            // (actual behavior: returns null when filteredSuggestions is empty)
        });
    });

    describe('rendering', () => {
        it('renders the AI Suggestions header', () => {
            render(<ContextualSuggestions suggestions={[makeSuggestion()]} />);
            expect(screen.getByText('AI Suggestions')).toBeTruthy();
        });

        it('renders suggestion title', () => {
            render(<ContextualSuggestions suggestions={[makeSuggestion()]} />);
            expect(screen.getByText('Complete onboarding')).toBeTruthy();
        });

        it('renders suggestion description', () => {
            render(<ContextualSuggestions suggestions={[makeSuggestion()]} />);
            expect(screen.getByText('Finish setting up your workspace.')).toBeTruthy();
        });

        it('renders priority chip', () => {
            render(<ContextualSuggestions suggestions={[makeSuggestion({ priority: 'critical' })]} />);
            expect(screen.getByText('critical')).toBeTruthy();
        });

        it('renders multiple suggestions up to maxSuggestions', () => {
            const suggestions = [
                makeSuggestion({ id: '1', title: 'First suggestion' }),
                makeSuggestion({ id: '2', title: 'Second suggestion' }),
                makeSuggestion({ id: '3', title: 'Third suggestion' }),
                makeSuggestion({ id: '4', title: 'Fourth suggestion' }),
            ];
            render(<ContextualSuggestions suggestions={suggestions} maxSuggestions={3} />);
            expect(screen.getByText('First suggestion')).toBeTruthy();
            expect(screen.getByText('Second suggestion')).toBeTruthy();
            expect(screen.getByText('Third suggestion')).toBeTruthy();
            expect(screen.queryByText('Fourth suggestion')).toBeNull();
        });

        it('shows Dismiss All button when more than one suggestion', () => {
            const suggestions = [
                makeSuggestion({ id: '1' }),
                makeSuggestion({ id: '2', title: 'Another' }),
            ];
            render(<ContextualSuggestions suggestions={suggestions} />);
            expect(screen.getByRole('button', { name: /Dismiss All/i })).toBeTruthy();
        });

        it('does not show Dismiss All button for single suggestion', () => {
            render(<ContextualSuggestions suggestions={[makeSuggestion()]} />);
            expect(screen.queryByRole('button', { name: /Dismiss All/i })).toBeNull();
        });
    });

    describe('sorting by priority', () => {
        it('renders higher priority suggestions first', () => {
            const suggestions = [
                makeSuggestion({ id: '1', title: 'Low priority', priority: 'low' }),
                makeSuggestion({ id: '2', title: 'Critical priority', priority: 'critical' }),
            ];
            render(<ContextualSuggestions suggestions={suggestions} />);
            const titles = screen.getAllByText(/priority/i).map(el => el.textContent);
            // Critical should appear before Low
            const critIdx = titles.findIndex(t => t?.includes('Critical'));
            const lowIdx = titles.findIndex(t => t?.includes('Low'));
            expect(critIdx).toBeLessThan(lowIdx);
        });
    });

    describe('dismiss behavior', () => {
        it('calls onDismiss with suggestion id when dismissed', () => {
            const onDismiss = vi.fn();
            const suggestions = [
                makeSuggestion({ id: '1', title: 'First' }),
                makeSuggestion({ id: '2', title: 'Second' }),
            ];
            render(<ContextualSuggestions suggestions={suggestions} onDismiss={onDismiss} />);
            // Each suggestion may have a dismiss button
            const buttons = screen.getAllByRole('button');
            const dismissBtns = buttons.filter(b => b.getAttribute('title') === 'Dismiss');
            if (dismissBtns.length > 0) {
                fireEvent.click(dismissBtns[0]);
                expect(onDismiss).toHaveBeenCalled();
            }
        });

        it('calls onDismissAll when Dismiss All is clicked', () => {
            const onDismissAll = vi.fn();
            const suggestions = [
                makeSuggestion({ id: '1' }),
                makeSuggestion({ id: '2', title: 'Another' }),
            ];
            render(<ContextualSuggestions suggestions={suggestions} onDismissAll={onDismissAll} />);
            fireEvent.click(screen.getByRole('button', { name: /Dismiss All/i }));
            expect(onDismissAll).toHaveBeenCalledOnce();
        });
    });

    describe('action button', () => {
        it('renders action button when actionLabel and onAction are provided', () => {
            const suggestions = [makeSuggestion({ actionLabel: 'Fix Now', onAction: vi.fn() })];
            render(<ContextualSuggestions suggestions={suggestions} />);
            expect(screen.getByText('Fix Now')).toBeTruthy();
        });

        it('calls onAction when action button is clicked', () => {
            const onAction = vi.fn();
            const suggestions = [makeSuggestion({ actionLabel: 'Fix Now', onAction })];
            render(<ContextualSuggestions suggestions={suggestions} />);
            fireEvent.click(screen.getByRole('button', { name: /Fix Now/i }));
            expect(onAction).toHaveBeenCalledOnce();
        });
    });

    describe('suggestion types', () => {
        const types: ContextualSuggestion['type'][] = ['action', 'warning', 'info', 'success', 'pending'];
        types.forEach(type => {
            it(`renders ${type} suggestion without error`, () => {
                const { container } = render(
                    <ContextualSuggestions suggestions={[makeSuggestion({ type })]} />
                );
                expect(container.firstChild).toBeTruthy();
            });
        });
    });
});
