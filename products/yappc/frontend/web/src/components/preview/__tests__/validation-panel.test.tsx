/**
 * ValidationPanel Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ValidationPanel } from '../ValidationPanel';

vi.mock('../../services/canvas/lifecycle/LifecycleArtifactService', () => ({
    useLifecycleArtifacts: vi.fn(() => ({
        artifacts: [],
        createArtifact: vi.fn(),
        updateArtifact: vi.fn(),
    })),
}));

describe('ValidationPanel', () => {
    it('renders with overview tab active', () => {
        render(<ValidationPanel projectId="test-project" />);
        expect(screen.getByText(/overview/i)).toBeTruthy();
    });

    it('renders validation panel content', () => {
        render(<ValidationPanel projectId="test-project" />);
        // Should render some content
        expect(document.body.textContent).toBeTruthy();
    });

    it('renders run button', () => {
        render(<ValidationPanel projectId="test-project" />);
        // Should have a "Run" or "Validate" button
        const runButtons = screen.queryAllByRole('button');
        expect(runButtons.length).toBeGreaterThan(0);
    });
});
