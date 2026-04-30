/**
 * Preview Host Sub-Component Tests
 * Tests for AccessibilityChecker, MockDataManager, ConfigRenderer
 * (Not mocked — using real implementations)
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import type { PageConfig } from 'yappc-config-schema';

vi.mock('yappc-config-schema', () => ({
    PageConfigSchema: { parse: vi.fn((x: unknown) => x) },
}));

import { AccessibilityChecker } from '../AccessibilityChecker';
import { MockDataManager } from '../MockDataManager';
import { ConfigRenderer } from '../ConfigRenderer';

const minimalConfig: PageConfig = {
    id: 'page-1',
    version: '1.0',
    requirementIds: [],
    title: 'Test Page',
    description: 'A test page',
    route: '/test',
    layout: 'grid',
    components: [],
} as unknown as PageConfig;

describe('AccessibilityChecker', () => {
    it('renders without crashing', () => {
        render(<AccessibilityChecker config={minimalConfig} />);
        expect(document.body.firstChild).toBeTruthy();
    });

    it('shows content for empty components config', () => {
        render(<AccessibilityChecker config={minimalConfig} />);
        const text = document.body.textContent ?? '';
        expect(text.length).toBeGreaterThan(0);
    });
});

describe('MockDataManager', () => {
    it('renders add data form', () => {
        render(<MockDataManager config={minimalConfig} />);
        const inputs = screen.getAllByRole('textbox');
        expect(inputs.length).toBeGreaterThanOrEqual(1);
    });

    it('calls onDataChange when data is added', () => {
        const onDataChange = vi.fn();
        render(<MockDataManager config={minimalConfig} onDataChange={onDataChange} />);
        const inputs = screen.getAllByRole('textbox');
        fireEvent.change(inputs[0], { target: { value: 'testKey' } });
        if (inputs[1]) {
            fireEvent.change(inputs[1], { target: { value: '"hello"' } });
        }
        const addBtn = screen.getByRole('button', { name: /add/i });
        fireEvent.click(addBtn);
        expect(onDataChange).toHaveBeenCalled();
    });
});

describe('ConfigRenderer', () => {
    it('renders without crashing', () => {
        render(<ConfigRenderer config={minimalConfig} />);
        expect(document.body.firstChild).toBeTruthy();
    });

    it('renders the config renderer container', () => {
        render(<ConfigRenderer config={minimalConfig} />);
        expect(screen.getByTestId('config-renderer')).toBeInTheDocument();
    });
});
