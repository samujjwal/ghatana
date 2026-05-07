/**
 * Tests for DataCloudOnboardingWizard (B15).
 *
 * Verifies:
 * - The wizard renders the Welcome step on first render
 * - isOnboardingComplete() returns false before completion
 * - Calling onComplete triggers the callback and marks localStorage
 * - The @ghatana/wizard Wizard component renders step titles
 *
 * @doc.type test
 * @doc.purpose Unit tests for DataCloudOnboardingWizard component (B15)
 * @doc.layer frontend
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import {
  DataCloudOnboardingWizard,
  isOnboardingComplete,
} from '../DataCloudOnboardingWizard';
import { TestWrapper } from '../../../__tests__/test-utils/wrapper';

// ─── Module mocks ────────────────────────────────────────────────────────────

vi.mock('@ghatana/wizard', () => ({
  Wizard: ({
    steps,
    renderStep,
    onComplete,
  }: {
    steps: Array<{ id: string; title: string }>;
    renderStep: (stepId: string, index: number) => React.ReactNode;
    onComplete?: () => void;
  }) => (
    <div data-testid="wizard">
      <h2>{steps[0]?.title}</h2>
      {renderStep(steps[0]?.id ?? '', 0)}
      {onComplete && (
        <button data-testid="complete-btn" onClick={onComplete}>
          Complete
        </button>
      )}
    </div>
  ),
}));

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('DataCloudOnboardingWizard', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders the welcome step title', () => {
    const onComplete = vi.fn();
    render(
      <TestWrapper>
        <DataCloudOnboardingWizard onComplete={onComplete} />
      </TestWrapper>
    );

    expect(screen.getByText('Welcome to Data Cloud')).toBeInTheDocument();
  });

  it('renders the wizard container', () => {
    render(
      <TestWrapper>
        <DataCloudOnboardingWizard onComplete={vi.fn()} />
      </TestWrapper>
    );

    expect(screen.getByTestId('wizard')).toBeInTheDocument();
  });

  it('calls onComplete when wizard finishes', () => {
    const onComplete = vi.fn();
    render(
      <TestWrapper>
        <DataCloudOnboardingWizard onComplete={onComplete} />
      </TestWrapper>
    );

    screen.getByTestId('complete-btn').click();
    expect(onComplete).toHaveBeenCalledOnce();
  });

  it('marks localStorage after completion', () => {
    const onComplete = vi.fn();
    render(
      <TestWrapper>
        <DataCloudOnboardingWizard onComplete={onComplete} />
      </TestWrapper>
    );

    screen.getByTestId('complete-btn').click();
    expect(localStorage.getItem('dc:onboarding:complete')).toBe('true');
  });
});

describe('isOnboardingComplete', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('returns false when localStorage key is absent', () => {
    expect(isOnboardingComplete()).toBe(false);
  });

  it('returns true when localStorage key is set', () => {
    localStorage.setItem('dc:onboarding:complete', 'true');
    expect(isOnboardingComplete()).toBe(true);
  });

  it('returns false when key has a different value', () => {
    localStorage.setItem('dc:onboarding:complete', 'false');
    expect(isOnboardingComplete()).toBe(false);
  });
});
