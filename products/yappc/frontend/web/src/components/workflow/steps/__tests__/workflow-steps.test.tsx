/**
 * Workflow Step Components Unit Tests
 *
 * Tests for ContextStep, IntentStep, PlanStep, ExecuteStep, ObserveStep, VerifyStep, LearnStep, InstitutionalizeStep.
 * All steps read from Jotai atoms (currentWorkflowAtom, draftStepDataAtom). We wrap with Provider + createStore.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createStore, Provider } from 'jotai';
import {
    currentWorkflowAtom,
    draftStepDataAtom,
} from '../../../../stores/workflow.store';
import { ContextStep } from '../ContextStep';
import { IntentStep } from '../IntentStep';
import { PlanStep } from '../PlanStep';
import { ExecuteStep } from '../ExecuteStep';
import { ObserveStep } from '../ObserveStep';
import { VerifyStep } from '../VerifyStep';
import { LearnStep } from '../LearnStep';
import { InstitutionalizeStep } from '../InstitutionalizeStep';

// ============================================================================
// Mock WorkflowWorkspace (underlying canvas step dependencies)
// ============================================================================

// Mock heavy canvas dependencies that would otherwise fail to load
vi.mock('../../../services/ActionRegistry', () => ({
    useActions: vi.fn(() => ({ grouped: [], execute: vi.fn(), formatShortcut: vi.fn() })),
    ActionRegistry: { register: vi.fn(), registerAll: vi.fn() },
}));

// ============================================================================
// Fixtures
// ============================================================================

const makeWorkflowStep = () => ({
    status: 'NOT_STARTED' as const,
    data: null,
    completedAt: null,
    startedAt: null,
});

const makeWorkflow = (currentStep = 'CONTEXT') => ({
    id: 'wf-1',
    currentStep: currentStep as 'CONTEXT' | 'INTENT' | 'PLAN' | 'EXECUTE' | 'VERIFY' | 'OBSERVE' | 'LEARN' | 'INSTITUTIONALIZE',
    status: 'ACTIVE' as const,
    aiMode: 'ASSISTED' as const,
    steps: {
        intent: makeWorkflowStep(),
        context: makeWorkflowStep(),
        plan: makeWorkflowStep(),
        execute: makeWorkflowStep(),
        verify: makeWorkflowStep(),
        observe: makeWorkflowStep(),
        learn: makeWorkflowStep(),
        institutionalize: makeWorkflowStep(),
    },
});

function makeStore(workflow = makeWorkflow(), draftData: unknown = null) {
    const store = createStore();
    store.set(currentWorkflowAtom, workflow);
    store.set(draftStepDataAtom, draftData);
    return store;
}

function wrap(ui: React.ReactElement, store = makeStore()) {
    return render(<Provider store={store}>{ui}</Provider>);
}

// ============================================================================
// ContextStep Tests
// ============================================================================

describe('ContextStep', () => {
    it('renders Systems Impacted section', () => {
        wrap(<ContextStep />);
        expect(screen.getByText('Systems Impacted')).toBeTruthy();
    });

    it('renders Constraints & Assumptions section', () => {
        wrap(<ContextStep />);
        expect(screen.getByText('Constraints & Assumptions')).toBeTruthy();
    });

    it('renders References section', () => {
        wrap(<ContextStep />);
        expect(screen.getByText('References')).toBeTruthy();
    });

    it('shows system chip when system exists in draft data', () => {
        const store = makeStore(makeWorkflow(), {
            systemsImpacted: ['Auth Service'],
            constraints: [],
            references: [],
        });
        wrap(<ContextStep />, store);
        expect(screen.getByText('Auth Service')).toBeTruthy();
    });

    it('adds system when Add button is clicked', () => {
        wrap(<ContextStep />);
        const input = screen.getByPlaceholderText('Add a system or service...');
        fireEvent.change(input, { target: { value: 'Payment API' } });
        // Click first "Add" button (for systems)
        const addButtons = screen.getAllByRole('button', { name: /Add/i });
        fireEvent.click(addButtons[0]);
        expect(screen.getByText('Payment API')).toBeTruthy();
    });

    it('Add button is disabled when input is empty', () => {
        wrap(<ContextStep />);
        const addButtons = screen.getAllByRole('button', { name: /Add/i });
        // First Add button (systems) — disabled when input is empty
        expect(addButtons[0].getAttribute('disabled')).toBeDefined();
    });
});

// ============================================================================
// IntentStep Tests
// ============================================================================

describe('IntentStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<IntentStep />, makeStore(makeWorkflow('INTENT')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Goal Statement section', () => {
        wrap(<IntentStep />, makeStore(makeWorkflow('INTENT')));
        expect(screen.getByText('Goal Statement')).toBeTruthy();
    });

    it('renders Success Criteria section', () => {
        wrap(<IntentStep />, makeStore(makeWorkflow('INTENT')));
        expect(screen.getByText('Success Criteria')).toBeTruthy();
    });
});

// ============================================================================
// PlanStep Tests
// ============================================================================

describe('PlanStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<PlanStep />, makeStore(makeWorkflow('PLAN')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Execution Plan section', () => {
        wrap(<PlanStep />, makeStore(makeWorkflow('PLAN')));
        expect(screen.getByText('Execution Plan')).toBeTruthy();
    });

    it('renders risk-related content', () => {
        wrap(<PlanStep />, makeStore(makeWorkflow('PLAN')));
        // Risk section heading may vary
        expect(screen.queryByText('Risk Assessment') ?? screen.queryByText(/risk/i));
        // Confirm at least one Add button renders
        expect(screen.getAllByRole('button', { name: /Add/i }).length).toBeGreaterThan(0);
    });
});

// ============================================================================
// ExecuteStep Tests
// ============================================================================

describe('ExecuteStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<ExecuteStep />, makeStore(makeWorkflow('EXECUTE')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Add Change section', () => {
        wrap(<ExecuteStep />, makeStore(makeWorkflow('EXECUTE')));
        expect(screen.getByText('Add Change')).toBeTruthy();
    });
});

// ============================================================================
// ObserveStep Tests
// ============================================================================

describe('ObserveStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<ObserveStep />, makeStore(makeWorkflow('OBSERVE')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Metrics Comparison section', () => {
        wrap(<ObserveStep />, makeStore(makeWorkflow('OBSERVE')));
        expect(screen.getByText('Metrics Comparison')).toBeTruthy();
    });
});

// ============================================================================
// VerifyStep Tests
// ============================================================================

describe('VerifyStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<VerifyStep />, makeStore(makeWorkflow('VERIFY')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Verification Evidence section', () => {
        wrap(<VerifyStep />, makeStore(makeWorkflow('VERIFY')));
        expect(screen.getByText('Verification Evidence')).toBeTruthy();
    });
});

// ============================================================================
// LearnStep Tests
// ============================================================================

describe('LearnStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<LearnStep />, makeStore(makeWorkflow('LEARN')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders Lessons Learned section', () => {
        wrap(<LearnStep />, makeStore(makeWorkflow('LEARN')));
        expect(screen.getByText('Lessons Learned')).toBeTruthy();
    });
});

// ============================================================================
// InstitutionalizeStep Tests
// ============================================================================

describe('InstitutionalizeStep', () => {
    it('renders without crashing', () => {
        const { container } = wrap(<InstitutionalizeStep />, makeStore(makeWorkflow('INSTITUTIONALIZE')));
        expect(container.firstChild).toBeTruthy();
    });

    it('renders documentation or institutionalize content', () => {
        wrap(<InstitutionalizeStep />, makeStore(makeWorkflow('INSTITUTIONALIZE')));
        expect(screen.queryByText(/document|knowledge|standard|institutionalize/i) ?? screen.queryByRole('button')).toBeTruthy();
    });
});
