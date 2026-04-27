/**
 * @doc.type test
 * @doc.purpose Unit tests for AgentAdvancedSettings progressive disclosure component
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import React from 'react';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  AgentAdvancedSettings,
  defaultAgentAdvancedConfig,
  type AgentBasicSettings,
  type AgentAdvancedConfig,
} from '../AgentAdvancedSettings';
import { FeatureFlag } from '../../../providers/FeatureFlagProvider';

// ============================================================================
// Mocks
// ============================================================================

vi.mock('../../../providers/FeatureFlagProvider', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../../providers/FeatureFlagProvider')>();
  return {
    ...actual,
    useFeatureFlag: vi.fn(() => ({
      isFeatureEnabled: vi.fn(() => false),
      getFeatureValue: vi.fn((_flag: FeatureFlag, defaultValue: unknown) => defaultValue),
      growthbook: {},
    })),
  };
});

import { useFeatureFlag } from '../../../providers/FeatureFlagProvider';
const mockUseFeatureFlag = vi.mocked(useFeatureFlag);

// ============================================================================
// Test helpers
// ============================================================================

function makeBasic(overrides: Partial<AgentBasicSettings> = {}): AgentBasicSettings {
  return { name: 'Test Agent', description: 'Does things', ...overrides };
}

function makeAdvanced(overrides: Partial<AgentAdvancedConfig> = {}): AgentAdvancedConfig {
  return { ...defaultAgentAdvancedConfig(), ...overrides };
}

function renderComponent(
  props: Partial<React.ComponentProps<typeof AgentAdvancedSettings>> = {},
): ReturnType<typeof render> {
  return render(
    <AgentAdvancedSettings
      basic={makeBasic()}
      advanced={makeAdvanced()}
      onBasicChange={vi.fn()}
      onAdvancedChange={vi.fn()}
      {...props}
    />,
  );
}

// ============================================================================
// Tests
// ============================================================================

describe('AgentAdvancedSettings', () => {
  beforeEach(() => {
    mockUseFeatureFlag.mockReturnValue({
      isFeatureEnabled: vi.fn(() => false),
      getFeatureValue: vi.fn((_flag: FeatureFlag, defaultValue: unknown) => defaultValue),
      growthbook: {} as ReturnType<typeof useFeatureFlag>['growthbook'],
    });
  });

  describe('basic settings', () => {
    it('renders agent name input with initial value', () => {
      renderComponent({ basic: makeBasic({ name: 'My Agent' }) });
      const input = screen.getByTestId('agent-name-input') as HTMLInputElement;
      expect(input.value).toBe('My Agent');
    });

    it('renders agent description textarea with initial value', () => {
      renderComponent({ basic: makeBasic({ description: 'Agent description' }) });
      const textarea = screen.getByTestId('agent-description-input') as HTMLTextAreaElement;
      expect(textarea.value).toBe('Agent description');
    });

    it('calls onBasicChange when name changes', () => {
      const onBasicChange = vi.fn();
      renderComponent({ onBasicChange });
      const input = screen.getByTestId('agent-name-input');
      fireEvent.change(input, { target: { value: 'New Name' } });
      expect(onBasicChange).toHaveBeenCalledWith({ name: 'New Name' });
    });

    it('calls onBasicChange when description changes', () => {
      const onBasicChange = vi.fn();
      renderComponent({ onBasicChange });
      const textarea = screen.getByTestId('agent-description-input');
      fireEvent.change(textarea, { target: { value: 'Updated description' } });
      expect(onBasicChange).toHaveBeenCalledWith({ description: 'Updated description' });
    });

    it('disables basic fields when readOnly=true', () => {
      renderComponent({ readOnly: true });
      expect(screen.getByTestId('agent-name-input')).toBeDisabled();
      expect(screen.getByTestId('agent-description-input')).toBeDisabled();
    });
  });

  describe('advanced settings accordion', () => {
    it('renders accordion toggle for advanced settings', () => {
      renderComponent();
      // Accordion header should be present
      expect(screen.getByText('Advanced settings')).toBeInTheDocument();
    });

    it('hides advanced fields before accordion is opened', () => {
      renderComponent();
      // Advanced fields should not be visible by default (accordion collapsed)
      expect(screen.queryByTestId('agent-model-select')).not.toBeInTheDocument();
      expect(screen.queryByTestId('agent-temperature-input')).not.toBeInTheDocument();
    });

    it('reveals advanced fields after clicking the accordion toggle', async () => {
      renderComponent();
      const toggle = screen.getByText('Advanced settings');
      await act(async () => {
        fireEvent.click(toggle);
      });
      expect(screen.getByTestId('agent-model-select')).toBeInTheDocument();
      expect(screen.getByTestId('agent-temperature-input')).toBeInTheDocument();
      expect(screen.getByTestId('agent-max-tokens-input')).toBeInTheDocument();
      expect(screen.getByTestId('agent-timeout-input')).toBeInTheDocument();
    });

    it('shows model selector with initial model value after open', async () => {
      renderComponent({ advanced: makeAdvanced({ model: 'claude-3-5-sonnet' }) });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      const select = screen.getByTestId('agent-model-select') as HTMLSelectElement;
      expect(select.value).toBe('claude-3-5-sonnet');
    });

    it('calls onAdvancedChange when model changes', async () => {
      const onAdvancedChange = vi.fn();
      renderComponent({ onAdvancedChange });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      const select = screen.getByTestId('agent-model-select');
      fireEvent.change(select, { target: { value: 'gpt-4o' } });
      expect(onAdvancedChange).toHaveBeenCalledWith({ model: 'gpt-4o' });
    });

    it('calls onAdvancedChange when temperature blurs with valid value', async () => {
      const onAdvancedChange = vi.fn();
      renderComponent({ onAdvancedChange });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      const input = screen.getByTestId('agent-temperature-input');
      fireEvent.change(input, { target: { value: '1.2' } });
      fireEvent.blur(input);
      expect(onAdvancedChange).toHaveBeenCalledWith({ temperature: 1.2 });
    });

    it('reverts temperature to original when blurred with invalid value', async () => {
      const onAdvancedChange = vi.fn();
      renderComponent({ advanced: makeAdvanced({ temperature: 0.7 }), onAdvancedChange });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      const input = screen.getByTestId('agent-temperature-input') as HTMLInputElement;
      fireEvent.change(input, { target: { value: 'bad' } });
      fireEvent.blur(input);
      expect(onAdvancedChange).not.toHaveBeenCalled();
      expect(input.value).toBe('0.7');
    });

    it('renders memory mode radio group after open', async () => {
      renderComponent({ advanced: makeAdvanced({ memoryMode: 'persistent' }) });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      expect(screen.getByTestId('agent-memory-none')).toBeInTheDocument();
      expect(screen.getByTestId('agent-memory-session')).toBeInTheDocument();
      const persistBtn = screen.getByTestId('agent-memory-persistent');
      expect(persistBtn).toHaveAttribute('aria-checked', 'true');
    });

    it('calls onAdvancedChange when memory mode button is clicked', async () => {
      const onAdvancedChange = vi.fn();
      renderComponent({ advanced: makeAdvanced({ memoryMode: 'session' }), onAdvancedChange });
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      fireEvent.click(screen.getByTestId('agent-memory-none'));
      expect(onAdvancedChange).toHaveBeenCalledWith({ memoryMode: 'none' });
    });
  });

  describe('feature flag gating', () => {
    it('hides planning section when AGENT_ORCHESTRATION flag is off', async () => {
      renderComponent();
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      expect(screen.queryByTestId('agent-planning-section')).not.toBeInTheDocument();
    });

    it('shows planning section when AGENT_ORCHESTRATION flag is on', async () => {
      mockUseFeatureFlag.mockReturnValue({
        isFeatureEnabled: vi.fn((flag: FeatureFlag) => flag === FeatureFlag.AGENT_ORCHESTRATION),
        getFeatureValue: vi.fn((_flag: FeatureFlag, defaultValue: unknown) => defaultValue),
        growthbook: {} as ReturnType<typeof useFeatureFlag>['growthbook'],
      });
      renderComponent();
      const toggle = screen.getByText('Advanced settings');
      await act(async () => { fireEvent.click(toggle); });
      expect(screen.getByTestId('agent-planning-section')).toBeInTheDocument();
    });
  });

  describe('defaultAgentAdvancedConfig', () => {
    it('returns expected defaults', () => {
      const cfg = defaultAgentAdvancedConfig();
      expect(cfg.model).toBe('gpt-4o-mini');
      expect(cfg.temperature).toBe(0.7);
      expect(cfg.maxTokens).toBe(4096);
      expect(cfg.timeoutMs).toBe(30000);
      expect(cfg.toolsEnabled).toBe(false);
      expect(cfg.memoryMode).toBe('session');
      expect(cfg.planningEnabled).toBe(false);
    });
  });
});
