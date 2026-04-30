/**
 * PreviewHost Shell Tests
 * Tests the top-level PreviewHost container with sub-components mocked.
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import React from 'react';
import type { PageConfig } from 'yappc-config-schema';

vi.mock('yappc-config-schema', () => ({
    PageConfigSchema: { parse: vi.fn((x: unknown) => x) },
}));

// Fully stub design-system to avoid Toolbar molecule's `actions.map` error
vi.mock('@ghatana/design-system', () => ({
    Box: ({ children, ...p }: { children?: React.ReactNode; [key: string]: unknown }) => <div {...(p as object)}>{children}</div>,
    Stack: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    Typography: ({ children, variant }: { children?: React.ReactNode; variant?: string }) => <span data-variant={variant}>{children}</span>,
    Button: ({ children, onClick }: { children?: React.ReactNode; onClick?: () => void }) => <button onClick={onClick}>{children}</button>,
    Paper: ({ children }: { children?: React.ReactNode }) => <div>{children}</div>,
    Toolbar: ({ children }: { children?: React.ReactNode }) => <div role="toolbar">{children}</div>,
    Divider: () => <hr />,
}));

// Stub all sub-panels
vi.mock('../ConfigRenderer', () => ({
    ConfigRenderer: ({ config }: { config: { title: string } }) => (
        <div data-testid="config-renderer">{config.title}</div>
    ),
}));
vi.mock('../MockDataManager', () => ({
    MockDataManager: () => <div data-testid="mock-data-manager" />,
}));
vi.mock('../AccessibilityChecker', () => ({
    AccessibilityChecker: () => <div data-testid="accessibility-checker" />,
}));
vi.mock('../VisualRegression', () => ({
    VisualRegression: () => <div data-testid="visual-regression" />,
}));

import { PreviewHost } from '../PreviewHost';

const testConfig: PageConfig = {
    id: 'page-1',
    version: '1.0',
    requirementIds: [],
    title: 'Test Page',
    description: 'A test page',
    route: '/test',
    layout: 'grid',
    components: [],
} as unknown as PageConfig;

describe('PreviewHost', () => {
    it('renders the preview host container', () => {
        render(<PreviewHost config={testConfig} />);
        expect(screen.getByTestId('preview-host')).toBeInTheDocument();
    });

    it('shows the page title in the toolbar', () => {
        render(<PreviewHost config={testConfig} />);
        const titles = screen.getAllByText('Test Page');
        expect(titles.length).toBeGreaterThanOrEqual(1);
    });

    it('shows the page route in the toolbar', () => {
        render(<PreviewHost config={testConfig} />);
        expect(screen.getByText('/test')).toBeInTheDocument();
    });

    it('renders the Preview panel by default', () => {
        render(<PreviewHost config={testConfig} />);
        expect(screen.getByTestId('config-renderer')).toBeInTheDocument();
    });

    it('switches to Mock Data panel on button click', () => {
        render(<PreviewHost config={testConfig} />);
        fireEvent.click(screen.getByRole('button', { name: /mock data/i }));
        expect(screen.getByTestId('mock-data-manager')).toBeInTheDocument();
    });

    it('switches to A11y panel on button click', () => {
        render(<PreviewHost config={testConfig} />);
        fireEvent.click(screen.getByRole('button', { name: /a11y/i }));
        expect(screen.getByTestId('accessibility-checker')).toBeInTheDocument();
    });

    it('switches to Visual Regression panel on button click', () => {
        render(<PreviewHost config={testConfig} />);
        fireEvent.click(screen.getByRole('button', { name: /visual/i }));
        expect(screen.getByTestId('visual-regression')).toBeInTheDocument();
    });

    it('is read-only compatible (renders without errors when readOnly=true)', () => {
        render(<PreviewHost config={testConfig} readOnly />);
        expect(screen.getByTestId('preview-host')).toBeInTheDocument();
    });
});
