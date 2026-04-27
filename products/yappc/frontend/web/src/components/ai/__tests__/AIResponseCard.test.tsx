/**
 * AIResponseCard Unit Tests
 *
 * Tests for the AI response display card with confirm/customize/reject actions.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AIResponseCard, type AIResponseCardProps } from '../AIResponseCard';
import type { AIResponse } from '../../../hooks/useAICommand';

// ============================================================================
// Fixtures
// ============================================================================

function makeResponse(overrides: Partial<AIResponse> = {}): AIResponse {
    return {
        type: 'create',
        summary: 'Build a task management app',
        confidence: 0.85,
        details: {
            name: 'Task Manager',
            projectType: 'web_app',
            techStack: ['React', 'Node.js'],
            features: ['Authentication', 'Dashboard'],
            estimatedTime: '3 weeks',
            nextActions: ['Set up repo', 'Define schema'],
        },
        ...overrides,
    };
}

function renderCard(props: Partial<AIResponseCardProps> = {}) {
    const defaultProps: AIResponseCardProps = {
        response: makeResponse(),
        onConfirm: vi.fn(),
        onReject: vi.fn(),
    };
    return render(<AIResponseCard {...defaultProps} {...props} />);
}

// ============================================================================
// Tests
// ============================================================================

describe('AIResponseCard', () => {
    describe('rendering', () => {
        it('renders the summary text', () => {
            renderCard();
            expect(screen.getByText('Build a task management app')).toBeTruthy();
        });

        it('renders confidence percentage (≥80% high confidence)', () => {
            renderCard({ response: makeResponse({ confidence: 0.85 }) });
            expect(screen.getByText('85% confident')).toBeTruthy();
        });

        it('renders confidence percentage (60–80% medium confidence)', () => {
            renderCard({ response: makeResponse({ confidence: 0.72 }) });
            expect(screen.getByText('72% confident')).toBeTruthy();
        });

        it('renders confidence percentage (<60% low confidence)', () => {
            renderCard({ response: makeResponse({ confidence: 0.45 }) });
            expect(screen.getByText('45% confident')).toBeTruthy();
        });

        it('renders project type when present', () => {
            renderCard();
            expect(screen.getByText(/web app/i)).toBeTruthy();
        });

        it('renders features list', () => {
            renderCard();
            expect(screen.getByText('Authentication')).toBeTruthy();
            expect(screen.getByText('Dashboard')).toBeTruthy();
        });

        it('renders estimated time', () => {
            renderCard();
            expect(screen.getByText('3 weeks')).toBeTruthy();
        });

        it('renders tech stack', () => {
            renderCard();
            expect(screen.getByText('React')).toBeTruthy();
            expect(screen.getByText('Node.js')).toBeTruthy();
        });

        it('renders next actions', () => {
            renderCard();
            expect(screen.getByText('Set up repo')).toBeTruthy();
            expect(screen.getByText('Define schema')).toBeTruthy();
        });
    });

    describe('action buttons', () => {
        it('renders Create Project confirm button for create type', () => {
            renderCard();
            expect(screen.getByRole('button', { name: /Create Project/i })).toBeTruthy();
        });

        it('renders Confirm button for non-create type', () => {
            renderCard({ response: makeResponse({ type: 'modify' }) });
            expect(screen.getByRole('button', { name: /Confirm/i })).toBeTruthy();
        });

        it('renders Cancel button', () => {
            renderCard();
            expect(screen.getByRole('button', { name: /Cancel/i })).toBeTruthy();
        });

        it('calls onConfirm when confirm button is clicked', () => {
            const onConfirm = vi.fn();
            renderCard({ onConfirm });
            fireEvent.click(screen.getByRole('button', { name: /Create Project/i }));
            expect(onConfirm).toHaveBeenCalledOnce();
        });

        it('calls onReject when Cancel button is clicked', () => {
            const onReject = vi.fn();
            renderCard({ onReject });
            fireEvent.click(screen.getByRole('button', { name: /Cancel/i }));
            expect(onReject).toHaveBeenCalledOnce();
        });

        it('shows Customize button when onCustomize is provided for create type', () => {
            renderCard({ onCustomize: vi.fn() });
            expect(screen.getByRole('button', { name: /Customize/i })).toBeTruthy();
        });

        it('does not show Customize button for non-create type', () => {
            renderCard({ response: makeResponse({ type: 'modify' }), onCustomize: vi.fn() });
            expect(screen.queryByRole('button', { name: /Customize/i })).toBeNull();
        });

        it('calls onCustomize when Customize button is clicked', () => {
            const onCustomize = vi.fn();
            renderCard({ onCustomize });
            fireEvent.click(screen.getByRole('button', { name: /Customize/i }));
            expect(onCustomize).toHaveBeenCalledOnce();
        });
    });

    describe('confirming state', () => {
        it('shows Creating... text when isConfirming is true', () => {
            renderCard({ isConfirming: true });
            expect(screen.getByText(/Creating\.\.\./i)).toBeTruthy();
        });

        it('disables confirm button when isConfirming', () => {
            renderCard({ isConfirming: true });
            // The confirm button becomes disabled
            const buttons = screen.getAllByRole('button');
            const confirmButton = buttons.find(b => b.textContent?.includes('Creating'));
            expect(confirmButton).toBeTruthy();
            expect(confirmButton?.getAttribute('disabled')).toBeDefined();
        });
    });

    describe('editing mode', () => {
        it('shows edit button when onUpdate is provided and type is create', () => {
            renderCard({ onUpdate: vi.fn() });
            expect(screen.getByTitle('Edit details')).toBeTruthy();
        });

        it('enters edit mode when edit button is clicked', () => {
            renderCard({ onUpdate: vi.fn() });
            fireEvent.click(screen.getByTitle('Edit details'));
            expect(screen.getByRole('button', { name: /Save Changes/i })).toBeTruthy();
            expect(screen.getByRole('button', { name: /Cancel/i })).toBeTruthy();
        });

        it('calls onUpdate with edited name when save changes is clicked', () => {
            const onUpdate = vi.fn();
            renderCard({ onUpdate });
            // Enter edit mode
            fireEvent.click(screen.getByTitle('Edit details'));
            // Change the name input (the first text input in the header)
            const nameInput = screen.getAllByRole('textbox')[0];
            fireEvent.change(nameInput, { target: { value: 'New Project Name' } });
            // Save
            fireEvent.click(screen.getByRole('button', { name: /Save Changes/i }));
            expect(onUpdate).toHaveBeenCalledWith(expect.objectContaining({
                name: 'New Project Name',
            }));
        });

        it('exits edit mode on Cancel click without calling onUpdate', () => {
            const onUpdate = vi.fn();
            renderCard({ onUpdate });
            fireEvent.click(screen.getByTitle('Edit details'));
            // In edit mode, Cancel button cancels editing (not onReject)
            const buttons = screen.getAllByRole('button');
            const cancelBtn = buttons.find(b => b.textContent === 'Cancel');
            fireEvent.click(cancelBtn!);
            // Back to view mode — Create Project button is shown again
            expect(screen.getByRole('button', { name: /Create Project/i })).toBeTruthy();
            expect(onUpdate).not.toHaveBeenCalled();
        });
    });

    describe('optional fields absent', () => {
        it('does not render tech stack section when absent', () => {
            renderCard({
                response: makeResponse({ details: { name: 'App' } }),
            });
            expect(screen.queryByText('Tech Stack')).toBeNull();
        });

        it('does not render features section when absent', () => {
            renderCard({
                response: makeResponse({ details: { name: 'App' } }),
            });
            // "Features" label should not appear
            expect(screen.queryByText(/Features \(/)).toBeNull();
        });
    });
});
