/**
 * config-editor unit tests
 *
 * Covers ConfigEditor, YamlEditor, and ConfigDiff components.
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import type { PageConfig } from '@yappc/config-schema';

import { ConfigEditor } from '../ConfigEditor';
import { YamlEditor } from '../YamlEditor';
import { ConfigDiff } from '../ConfigDiff';

// ─── fixtures ─────────────────────────────────────────────────────────────────

const sampleConfig: PageConfig = {
    id: 'page-1',
    title: 'Test Page',
    route: '/test',
    components: [],
};

const altConfig: PageConfig = {
    id: 'page-1',
    title: 'Updated Page',
    route: '/updated',
    components: [{ id: 'comp-1', type: 'button' }],
};

// ─── ConfigEditor ─────────────────────────────────────────────────────────────

describe('ConfigEditor', () => {
    it('renders Config Editor heading', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByText('Config Editor')).toBeInTheDocument();
    });

    it('renders with data-testid="config-editor"', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByTestId('config-editor')).toBeInTheDocument();
    });

    it('shows JSON mode toggle button', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        // ToggleButton renders with role="option"
        expect(screen.getByRole('option', { name: /json mode/i })).toBeInTheDocument();
    });

    it('shows YAML mode toggle button', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByRole('option', { name: /yaml mode/i })).toBeInTheDocument();
    });

    it('defaults to JSON editor', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByTestId('json-editor')).toBeInTheDocument();
    });

    it('switches to YAML editor when YAML mode is selected', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        fireEvent.click(screen.getByRole('option', { name: /yaml mode/i }));
        expect(screen.getByTestId('yaml-editor')).toBeInTheDocument();
    });

    it('switches back to JSON editor when JSON mode is selected after YAML', () => {
        render(
            <ConfigEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        fireEvent.click(screen.getByRole('option', { name: /yaml mode/i }));
        fireEvent.click(screen.getByRole('option', { name: /json mode/i }));
        expect(screen.getByTestId('json-editor')).toBeInTheDocument();
    });
});

// ─── YamlEditor ───────────────────────────────────────────────────────────────

describe('YamlEditor', () => {
    it('renders YAML Editor heading', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByText('YAML Editor')).toBeInTheDocument();
    });

    it('renders with data-testid="yaml-editor"', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByTestId('yaml-editor')).toBeInTheDocument();
    });

    it('renders the YAML textarea', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByTestId('yaml-textarea')).toBeInTheDocument();
    });

    it('shows character count', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByText(/characters/i)).toBeInTheDocument();
    });

    it('shows Valid YAML status when initial config is valid', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByText('Valid YAML')).toBeInTheDocument();
    });

    it('renders Format button in editable mode', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByRole('button', { name: /format/i })).toBeInTheDocument();
    });

    it('does not render Format button in readOnly mode', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
                readOnly
            />
        );
        expect(screen.queryByRole('button', { name: /format/i })).not.toBeInTheDocument();
    });

    it('disables input in readOnly mode', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
                readOnly
            />
        );
        // data-testid is on the <textarea> element itself (passed via ...rest)
        expect(screen.getByTestId('yaml-textarea')).toBeDisabled();
    });

    it('shows a placeholder note about simplified YAML parser', () => {
        render(
            <YamlEditor
                value={sampleConfig}
                onChange={vi.fn()}
            />
        );
        expect(screen.getByText(/simplified yaml parser/i)).toBeInTheDocument();
    });
});

// ─── ConfigDiff ───────────────────────────────────────────────────────────────

describe('ConfigDiff', () => {
    it('renders Config Diff heading', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={sampleConfig}
            />
        );
        expect(screen.getByText('Config Diff')).toBeInTheDocument();
    });

    it('renders with data-testid="config-diff"', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={sampleConfig}
            />
        );
        expect(screen.getByTestId('config-diff')).toBeInTheDocument();
    });

    it('shows "No changes detected" when configs are identical', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={sampleConfig}
            />
        );
        expect(screen.getByText(/no changes detected/i)).toBeInTheDocument();
    });

    it('shows change count when configs differ', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        const changes = screen.getByText(/changes \(\d+\)/i);
        expect(changes).toBeInTheDocument();
        expect(changes.textContent).not.toContain('(0)');
    });

    it('shows modified path for title change', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        expect(screen.getByText(/~title/i)).toBeInTheDocument();
    });

    it('shows Old and New values for modified fields', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        // Multiple fields changed (title, route) → multiple Old:/New: labels
        const oldLabels = screen.getAllByText(/^old:/i);
        const newLabels = screen.getAllByText(/^new:/i);
        expect(oldLabels.length).toBeGreaterThan(0);
        expect(newLabels.length).toBeGreaterThan(0);
    });

    it('shows added component entry when target has extra component', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        expect(screen.getByText(/\+components/i)).toBeInTheDocument();
    });

    it('renders Base Version and Target Version toggle buttons', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        expect(screen.getByRole('button', { name: /base version/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /target version/i })).toBeInTheDocument();
    });

    it('renders Apply button defaulting to Apply Right', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        expect(screen.getByRole('button', { name: /apply right/i })).toBeInTheDocument();
    });

    it('switches Apply button to Apply Left when Base Version is selected', () => {
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
            />
        );
        fireEvent.click(screen.getByRole('button', { name: /base version/i }));
        expect(screen.getByRole('button', { name: /apply left/i })).toBeInTheDocument();
    });

    it('calls onApplyChange with targetConfig when Apply Right is clicked', () => {
        const onApplyChange = vi.fn();
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
                onApplyChange={onApplyChange}
            />
        );
        fireEvent.click(screen.getByRole('button', { name: /apply right/i }));
        expect(onApplyChange).toHaveBeenCalledWith(altConfig);
    });

    it('calls onApplyChange with baseConfig when Base Version selected then Apply clicked', () => {
        const onApplyChange = vi.fn();
        render(
            <ConfigDiff
                baseConfig={sampleConfig}
                targetConfig={altConfig}
                onApplyChange={onApplyChange}
            />
        );
        fireEvent.click(screen.getByRole('button', { name: /base version/i }));
        fireEvent.click(screen.getByRole('button', { name: /apply left/i }));
        expect(onApplyChange).toHaveBeenCalledWith(sampleConfig);
    });
});
