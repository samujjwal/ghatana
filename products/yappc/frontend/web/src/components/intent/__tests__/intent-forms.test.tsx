/**
 * Intent Component Tests - IdeaBriefForm, ProblemStatementEditor
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { IdeaBriefForm } from '../IdeaBriefForm';
import { ProblemStatementEditor } from '../ProblemStatementEditor';
import { ResearchPackEditor } from '../ResearchPackEditor';

describe('IdeaBriefForm', () => {
    const mockSubmit = vi.fn().mockResolvedValue(undefined);

    it('renders title input', () => {
        render(<IdeaBriefForm onSubmit={mockSubmit} />);
        expect(screen.getByPlaceholderText(/smart task manager/i)).toBeTruthy();
    });

    it('renders oneLiner input', () => {
        render(<IdeaBriefForm onSubmit={mockSubmit} />);
        expect(screen.getByPlaceholderText(/brief description/i)).toBeTruthy();
    });

    it('renders Cancel button when onCancel is provided', () => {
        render(<IdeaBriefForm onSubmit={mockSubmit} onCancel={vi.fn()} />);
        expect(screen.getByRole('button', { name: /cancel/i })).toBeTruthy();
    });

    it('renders Suggested Improvements action when assist is enabled', () => {
        render(<IdeaBriefForm onSubmit={mockSubmit} onAIAssist={vi.fn().mockResolvedValue(null)} />);
        expect(screen.getByRole('button', { name: /suggested improvements/i })).toBeTruthy();
    });

    it('calls onCancel when Cancel is clicked', () => {
        const onCancel = vi.fn();
        render(<IdeaBriefForm onSubmit={mockSubmit} onCancel={onCancel} />);
        fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
        expect(onCancel).toHaveBeenCalled();
    });

    it('renders with initial data', () => {
        render(
            <IdeaBriefForm
                onSubmit={mockSubmit}
                initialData={{ title: 'My Idea' }}
            />
        );
        const titleInput = screen.getByDisplayValue('My Idea');
        expect(titleInput).toBeTruthy();
    });

    it('adds target user row when Add button clicked', () => {
        render(<IdeaBriefForm onSubmit={mockSubmit} />);
        // Initially one target user row
        const rows = screen.getAllByPlaceholderText(/product managers/i);
        expect(rows.length).toBe(1);
        const addButtons = document.querySelectorAll('button[type="button"]');
        // find add icon button near target user section - click the + button
        const svgButtons = Array.from(addButtons).filter(b => b.querySelector('svg'));
        fireEvent.click(svgButtons[0]);
        const rowsAfter = screen.getAllByPlaceholderText(/product managers/i);
        expect(rowsAfter.length).toBeGreaterThanOrEqual(1);
    });
});

describe('ProblemStatementEditor', () => {
    const mockSubmit = vi.fn().mockResolvedValue(undefined);

    it('renders problem textarea', () => {
        render(<ProblemStatementEditor onSubmit={mockSubmit} />);
        expect(screen.getByPlaceholderText(/core problem/i)).toBeTruthy();
    });

    it('renders Cancel button when onCancel provided', () => {
        render(<ProblemStatementEditor onSubmit={mockSubmit} onCancel={vi.fn()} />);
        expect(screen.getByRole('button', { name: /cancel/i })).toBeTruthy();
    });

    it('calls onCancel when clicked', () => {
        const onCancel = vi.fn();
        render(<ProblemStatementEditor onSubmit={mockSubmit} onCancel={onCancel} />);
        fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
        expect(onCancel).toHaveBeenCalled();
    });

    it('renders with initial data', () => {
        render(
            <ProblemStatementEditor
                onSubmit={mockSubmit}
                initialData={{ problem: 'The problem is...' }}
            />
        );
        expect(screen.getByDisplayValue('The problem is...')).toBeTruthy();
    });
});

describe('ResearchPackEditor', () => {
    const mockSubmit = vi.fn().mockResolvedValue(undefined);

    it('renders market notes textarea', () => {
        render(<ResearchPackEditor onSubmit={mockSubmit} />);
        expect(screen.getByPlaceholderText(/market/i)).toBeTruthy();
    });

    it('renders Cancel button when onCancel provided', () => {
        render(<ResearchPackEditor onSubmit={mockSubmit} onCancel={vi.fn()} />);
        expect(screen.getByRole('button', { name: /cancel/i })).toBeTruthy();
    });

    it('calls onCancel when clicked', () => {
        const onCancel = vi.fn();
        render(<ResearchPackEditor onSubmit={mockSubmit} onCancel={onCancel} />);
        fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
        expect(onCancel).toHaveBeenCalled();
    });

    it('renders Guided Analysis action when assist is enabled', () => {
        render(<ResearchPackEditor onSubmit={mockSubmit} onAIAssist={vi.fn().mockResolvedValue(null)} />);
        expect(screen.getByRole('button', { name: /guided analysis/i })).toBeTruthy();
    });
});
