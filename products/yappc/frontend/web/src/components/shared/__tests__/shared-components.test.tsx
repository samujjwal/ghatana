/**
 * Shared Component Tests
 * @description Tests for FilterPanel, ExportButton, ErrorBoundary, ArtifactDetailPanel
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { FilterPanel, type FilterConfig } from '../FilterPanel';
import { ExportButton } from '../ExportButton';
import { ErrorBoundary } from '../ErrorBoundary';

// ============================================================================
// FilterPanel
// ============================================================================

describe('FilterPanel', () => {
    const searchFilter: FilterConfig = {
        id: 'query',
        label: 'Search',
        type: 'search',
        placeholder: 'Search...',
    };
    const selectFilter: FilterConfig = {
        id: 'status',
        label: 'Status',
        type: 'select',
        options: [
            { label: 'Open', value: 'open' },
            { label: 'Closed', value: 'closed' },
        ],
    };

    it('renders Filters label', () => {
        render(<FilterPanel filters={[searchFilter]} values={{}} onChange={vi.fn()} onClear={vi.fn()} />);
        expect(screen.getByText('Filters')).toBeTruthy();
    });

    it('toggles expanded on header click', () => {
        render(<FilterPanel filters={[searchFilter]} values={{}} onChange={vi.fn()} onClear={vi.fn()} />);
        const header = screen.getByText('Filters').closest('button') as HTMLElement;
        fireEvent.click(header);
        // After expand, should show filter inputs
        expect(screen.getByPlaceholderText('Search...')).toBeTruthy();
    });

    it('shows active filter count badge when filters have values', () => {
        render(
            <FilterPanel
                filters={[searchFilter]}
                values={{ query: 'hello' }}
                onChange={vi.fn()}
                onClear={vi.fn()}
            />
        );
        expect(screen.getByText('1')).toBeTruthy();
    });

    it('calls onClear when Clear All is clicked', () => {
        const onClear = vi.fn();
        render(
            <FilterPanel
                filters={[searchFilter]}
                values={{ query: 'hello' }}
                onChange={vi.fn()}
                onClear={onClear}
            />
        );
        // Expand first
        const header = screen.getByText('Filters').closest('button') as HTMLElement;
        fireEvent.click(header);
        fireEvent.click(screen.getByText('Clear'));
        expect(onClear).toHaveBeenCalled();
    });

    it('renders select filter options when expanded', () => {
        render(
            <FilterPanel
                filters={[selectFilter]}
                values={{}}
                onChange={vi.fn()}
                onClear={vi.fn()}
            />
        );
        const header = screen.getByText('Filters').closest('button') as HTMLElement;
        fireEvent.click(header);
        expect(screen.getByText('Status')).toBeTruthy();
    });

    it('calls onChange on search input', () => {
        const onChange = vi.fn();
        render(
            <FilterPanel
                filters={[searchFilter]}
                values={{}}
                onChange={onChange}
                onClear={vi.fn()}
            />
        );
        const header = screen.getByText('Filters').closest('button') as HTMLElement;
        fireEvent.click(header);
        const input = screen.getByPlaceholderText('Search...') as HTMLInputElement;
        fireEvent.change(input, { target: { value: 'test' } });
        expect(onChange).toHaveBeenCalledWith('query', 'test');
    });
});

// ============================================================================
// ExportButton
// ============================================================================

describe('ExportButton', () => {
    it('renders export button', () => {
        render(<ExportButton onExport={vi.fn()} />);
        expect(screen.getByText('Export')).toBeTruthy();
    });

    it('shows format menu on click', () => {
        render(<ExportButton onExport={vi.fn()} />);
        fireEvent.click(screen.getByText('Export'));
        expect(screen.getByText('PDF Report')).toBeTruthy();
        expect(screen.getByText('Markdown')).toBeTruthy();
        expect(screen.getByText('JSON Data')).toBeTruthy();
    });

    it('calls onExport with format when format selected', () => {
        const onExport = vi.fn();
        render(<ExportButton onExport={onExport} />);
        fireEvent.click(screen.getByText('Export'));
        fireEvent.click(screen.getByText('Markdown'));
        expect(onExport).toHaveBeenCalledWith('markdown');
    });

    it('calls onExport with pdf format', () => {
        const onExport = vi.fn();
        render(<ExportButton onExport={onExport} />);
        fireEvent.click(screen.getByText('Export'));
        fireEvent.click(screen.getByText('PDF Report'));
        expect(onExport).toHaveBeenCalledWith('pdf');
    });

    it('is disabled when disabled=true', () => {
        render(<ExportButton onExport={vi.fn()} disabled={true} />);
        const btn = screen.getByText('Export').closest('button') as HTMLButtonElement;
        expect(btn.disabled).toBe(true);
    });

    it('renders compact mode without Export text', () => {
        render(<ExportButton onExport={vi.fn()} compact={true} />);
        expect(screen.queryByText('Export')).toBeNull();
    });
});

// ============================================================================
// ErrorBoundary
// ============================================================================

const ThrowError = ({ shouldThrow }: { shouldThrow: boolean }) => {
    if (shouldThrow) throw new Error('Test error');
    return <div>no error</div>;
};

describe('ErrorBoundary', () => {
    it('renders children when no error', () => {
        render(
            <ErrorBoundary>
                <ThrowError shouldThrow={false} />
            </ErrorBoundary>
        );
        expect(screen.getByText('no error')).toBeTruthy();
    });

    it('renders fallback when child throws', () => {
        // Suppress console.error for this test
        const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
        render(
            <ErrorBoundary>
                <ThrowError shouldThrow={true} />
            </ErrorBoundary>
        );
        // Should show error UI — look for common error boundary text
        expect(document.body.textContent).not.toContain('no error');
        spy.mockRestore();
    });

    it('renders custom fallback when child throws', () => {
        const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
        render(
            <ErrorBoundary fallback={<div>Custom Error UI</div>}>
                <ThrowError shouldThrow={true} />
            </ErrorBoundary>
        );
        expect(screen.getByText('Custom Error UI')).toBeTruthy();
        spy.mockRestore();
    });

    it('calls onError when child throws', () => {
        const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
        const onError = vi.fn();
        render(
            <ErrorBoundary onError={onError}>
                <ThrowError shouldThrow={true} />
            </ErrorBoundary>
        );
        expect(onError).toHaveBeenCalled();
        spy.mockRestore();
    });
});
