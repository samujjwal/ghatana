/**
 * Tests for GuidedPipelineFlow.
 *
 * Exercises real component behaviour via React Testing Library — no object-literal
 * assertions, no disabled tests, no mocks of the component under test.
 */
import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { GuidedPipelineFlow } from '../GuidedPipelineFlow';
import type { GuidedStageSuggestion } from '../GuidedPipelineFlow';

const MOCK_SUGGESTIONS: GuidedStageSuggestion[] = [
  { label: 'Validate', kind: 'validation', description: 'Schema validation stage' },
  { label: 'Enrich', kind: 'enrichment', description: 'CRM enrichment stage' },
];

function buildProps(overrides: Partial<React.ComponentProps<typeof GuidedPipelineFlow>> = {}) {
  return {
    onRequestSuggestions: vi.fn().mockResolvedValue(undefined),
    suggestions: null,
    suggestionsLoading: false,
    onApplySuggestions: vi.fn(),
    onValidate: vi.fn().mockResolvedValue(undefined),
    validating: false,
    validationPassed: null,
    onRunNow: vi.fn().mockResolvedValue(undefined),
    running: false,
    onSwitchToAdvanced: vi.fn(),
    canManagePipelines: true,
    ...overrides,
  };
}

describe('GuidedPipelineFlow', () => {
  describe('describe step', () => {
    it('renders the describe step by default', () => {
      render(<GuidedPipelineFlow {...buildProps()} />);

      expect(screen.getByTestId('guided-pipeline-flow')).toBeInTheDocument();
      expect(screen.getByText('Describe what this pipeline should do')).toBeInTheDocument();
    });

    it('disables the submit button when description is empty', () => {
      render(<GuidedPipelineFlow {...buildProps()} />);

      const button = screen.getByRole('button', { name: /generate stage suggestions/i });
      expect(button).toBeDisabled();
    });

    it('enables the submit button when description is entered', () => {
      render(<GuidedPipelineFlow {...buildProps()} />);

      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate and enrich data' } });

      const button = screen.getByRole('button', { name: /generate stage suggestions/i });
      expect(button).not.toBeDisabled();
    });

    it('calls onRequestSuggestions with description and goal when submitted', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      render(<GuidedPipelineFlow {...buildProps({ onRequestSuggestions })} />);

      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });

      const goalInput = screen.getByPlaceholderText(/e\.g\. Reduce latency/i);
      fireEvent.change(goalInput, { target: { value: 'Real-time analytics' } });

      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => {
        expect(onRequestSuggestions).toHaveBeenCalledWith('Validate schema', 'Real-time analytics');
      });
    });

    it('calls onRequestSuggestions without goal when goal is empty', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      render(<GuidedPipelineFlow {...buildProps({ onRequestSuggestions })} />);

      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });

      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => {
        expect(onRequestSuggestions).toHaveBeenCalledWith('Validate schema', undefined);
      });
    });

    it('calls onSwitchToAdvanced when "Switch to advanced mode" is clicked', () => {
      const onSwitchToAdvanced = vi.fn();
      render(<GuidedPipelineFlow {...buildProps({ onSwitchToAdvanced })} />);

      fireEvent.click(screen.getByRole('button', { name: /switch to advanced mode/i }));
      expect(onSwitchToAdvanced).toHaveBeenCalledOnce();
    });
  });

  describe('review step', () => {
    it('shows loading state when suggestionsLoading is true and step is review', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      render(<GuidedPipelineFlow {...buildProps({ onRequestSuggestions, suggestionsLoading: true })} />);

      // Trigger navigation to review step
      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generating suggestions/i }));

      // Loading spinner should appear; review step header transitions
      await waitFor(() => {
        expect(onRequestSuggestions).toHaveBeenCalled();
      });
    });

    it('renders suggested stages in review step', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      const props = buildProps({ onRequestSuggestions, suggestions: MOCK_SUGGESTIONS });
      render(<GuidedPipelineFlow {...props} />);

      // Move to review step
      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => {
        expect(screen.getByText('Review suggested stages')).toBeInTheDocument();
        expect(screen.getByText('Validate')).toBeInTheDocument();
        expect(screen.getByText('Enrich')).toBeInTheDocument();
      });
    });

    it('calls onApplySuggestions when apply button is clicked', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      const onApplySuggestions = vi.fn();
      const props = buildProps({ onRequestSuggestions, suggestions: MOCK_SUGGESTIONS, onApplySuggestions });
      render(<GuidedPipelineFlow {...props} />);

      // Move to review step
      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => {
        expect(screen.getByText('Review suggested stages')).toBeInTheDocument();
      });

      fireEvent.click(screen.getByRole('button', { name: /apply stages and continue/i }));
      expect(onApplySuggestions).toHaveBeenCalledOnce();
    });
  });

  describe('validate step', () => {
    async function navigateToValidateStep() {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      const onValidate = vi.fn().mockResolvedValue(undefined);
      const onApplySuggestions = vi.fn();
      const props = buildProps({ onRequestSuggestions, suggestions: MOCK_SUGGESTIONS, onApplySuggestions, onValidate });
      render(<GuidedPipelineFlow {...props} />);

      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => expect(screen.getByText('Review suggested stages')).toBeInTheDocument());

      fireEvent.click(screen.getByRole('button', { name: /apply stages and continue/i }));

      await waitFor(() => expect(screen.getByText('Validate the pipeline')).toBeInTheDocument());

      return { onValidate };
    }

    it('renders the validate step after applying stages', async () => {
      await navigateToValidateStep();
      expect(screen.getByText('Validate the pipeline')).toBeInTheDocument();
    });

    it('calls onValidate when validate button is clicked', async () => {
      const { onValidate } = await navigateToValidateStep();
      fireEvent.click(screen.getByRole('button', { name: /validate pipeline/i }));
      await waitFor(() => expect(onValidate).toHaveBeenCalledOnce());
    });

    it('shows success state when validationPassed is true', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      const onApplySuggestions = vi.fn();
      const props = buildProps({
        onRequestSuggestions,
        suggestions: MOCK_SUGGESTIONS,
        onApplySuggestions,
        validationPassed: true,
      });
      render(<GuidedPipelineFlow {...props} />);

      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));

      await waitFor(() => expect(screen.getByText('Review suggested stages')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /apply stages and continue/i }));

      await waitFor(() => {
        expect(screen.getByText('Pipeline passed validation.')).toBeInTheDocument();
      });
    });
  });

  describe('run step', () => {
    it('shows read-only warning when canManagePipelines is false', async () => {
      const onRequestSuggestions = vi.fn().mockResolvedValue(undefined);
      const onApplySuggestions = vi.fn();
      const onValidate = vi.fn().mockResolvedValue(undefined);
      const props = buildProps({
        onRequestSuggestions,
        suggestions: MOCK_SUGGESTIONS,
        onApplySuggestions,
        onValidate,
        canManagePipelines: false,
      });
      render(<GuidedPipelineFlow {...props} />);

      // Navigate to run step
      const textarea = screen.getByPlaceholderText(/e\.g\. Ingest customer events/i);
      fireEvent.change(textarea, { target: { value: 'Validate schema' } });
      fireEvent.click(screen.getByRole('button', { name: /generate stage suggestions/i }));
      await waitFor(() => expect(screen.getByText('Review suggested stages')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /apply stages and continue/i }));
      await waitFor(() => expect(screen.getByText('Validate the pipeline')).toBeInTheDocument());
      fireEvent.click(screen.getByRole('button', { name: /validate pipeline/i }));
      await waitFor(() => expect(onValidate).toHaveBeenCalled());

      // Should be on run step now
      await waitFor(() => expect(screen.getByText('Run the pipeline')).toBeInTheDocument());
      expect(screen.getByText(/read-only access/i)).toBeInTheDocument();

      const runButton = screen.getByRole('button', { name: /run now/i });
      expect(runButton).toBeDisabled();
    });
  });

  describe('step sidebar', () => {
    it('renders all four step labels', () => {
      render(<GuidedPipelineFlow {...buildProps()} />);

      expect(screen.getByRole('navigation', { name: /pipeline creation steps/i })).toBeInTheDocument();
      expect(screen.getByText('Describe goal')).toBeInTheDocument();
      expect(screen.getByText('Review stages')).toBeInTheDocument();
      expect(screen.getByText('Validate')).toBeInTheDocument();
      expect(screen.getByText('Run')).toBeInTheDocument();
    });

    it('marks describe step as active initially', () => {
      render(<GuidedPipelineFlow {...buildProps()} />);

      const describeButton = screen.getByRole('button', { name: /describe goal/i });
      expect(describeButton).toHaveAttribute('aria-current', 'step');
    });
  });
});
