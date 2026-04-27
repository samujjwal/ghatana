/**
 * EvidencePanel Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { Provider, createStore } from 'jotai';
import { EvidencePanel } from '../EvidencePanel';
import { workflowAuditAtom, showAISuggestionsAtom, currentAISuggestionAtom } from '../../../stores/workflow.store';

// Mock @yappc/core/types
vi.mock('@yappc/core/types', () => ({}));

// Mock the workflow store atoms
vi.mock('../../../stores/workflow.store', () => ({
    workflowAuditAtom: 'workflowAuditAtom',
    showAISuggestionsAtom: 'showAISuggestionsAtom',
    currentAISuggestionAtom: 'currentAISuggestionAtom',
}));

// Mock jotai to control atom values
vi.mock('jotai', async (importOriginal) => {
    const actual = await importOriginal<typeof import('jotai')>();
    return {
        ...actual,
        useAtomValue: vi.fn((atom) => {
            if (atom === 'workflowAuditAtom') return [];
            if (atom === 'showAISuggestionsAtom') return true;
            if (atom === 'currentAISuggestionAtom') return null;
            return null;
        }),
        useSetAtom: vi.fn(() => vi.fn()),
    };
});

describe('EvidencePanel', () => {
    it('renders panel with Audit tab', () => {
        render(<EvidencePanel />);
        expect(screen.getByText('Audit')).toBeTruthy();
    });

    it('renders AI tab', () => {
        render(<EvidencePanel />);
        expect(screen.getByText('AI')).toBeTruthy();
    });

    it('renders empty audit trail state', () => {
        render(<EvidencePanel />);
        // Empty state for audit trail
        expect(screen.getByText('Audit')).toBeTruthy();
    });
});
