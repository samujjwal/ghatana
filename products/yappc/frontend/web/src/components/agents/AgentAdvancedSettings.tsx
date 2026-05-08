/**
 * Agent Advanced Settings
 *
 * Progressive-disclosure panel for agent model, tool, runtime, and memory
 * configuration. Basic settings are always visible; advanced settings are
 * collapsed behind an Accordion and gated by the AGENT_ORCHESTRATION feature
 * flag for experimental controls.
 *
 * @doc.type component
 * @doc.purpose Progressive disclosure of advanced agent configuration
 * @doc.layer product
 * @doc.pattern ProgressiveDisclosure
 */

import React, { useCallback, useState } from 'react';
import { Settings2 } from 'lucide-react';
import { Accordion, Box, Switch, Typography } from '@ghatana/design-system';
import {
  FeatureFlag,
  useFeatureFlag,
} from '../../providers/FeatureFlagProvider';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Textarea } from '../ui/Textarea';

// ============================================================================
// Types
// ============================================================================

export type AgentModel =
  | 'gpt-4o'
  | 'gpt-4o-mini'
  | 'claude-3-5-sonnet'
  | 'claude-3-haiku'
  | 'gemini-1-5-pro';

export type AgentMemoryMode = 'none' | 'session' | 'persistent';

export interface AgentBasicSettings {
  /** Display name for the agent */
  name: string;
  /** Short description of the agent's purpose */
  description: string;
}

export interface AgentAdvancedConfig {
  /** LLM model selection */
  model: AgentModel;
  /** Sampling temperature 0–2 */
  temperature: number;
  /** Maximum tokens per response */
  maxTokens: number;
  /** Request timeout in milliseconds */
  timeoutMs: number;
  /** Whether the agent may call external tools */
  toolsEnabled: boolean;
  /** Memory strategy for this agent */
  memoryMode: AgentMemoryMode;
  /** Whether to enable experimental multi-step planning (flag-gated) */
  planningEnabled: boolean;
}

export interface AgentAdvancedSettingsProps {
  /** Current basic field values */
  basic: AgentBasicSettings;
  /** Current advanced config values */
  advanced: AgentAdvancedConfig;
  /** Whether the form is read-only (e.g. during save) */
  readOnly?: boolean;
  /** Called when any basic field changes */
  onBasicChange: (patch: Partial<AgentBasicSettings>) => void;
  /** Called when any advanced config field changes */
  onAdvancedChange: (patch: Partial<AgentAdvancedConfig>) => void;
}

// ============================================================================
// Constants
// ============================================================================

const MODEL_OPTIONS: Array<{ value: AgentModel; label: string }> = [
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
  { value: 'claude-3-5-sonnet', label: 'Claude 3.5 Sonnet' },
  { value: 'claude-3-haiku', label: 'Claude 3 Haiku' },
  { value: 'gemini-1-5-pro', label: 'Gemini 1.5 Pro' },
];

const MEMORY_MODE_OPTIONS: Array<{ value: AgentMemoryMode; label: string; description: string }> = [
  { value: 'none', label: 'None', description: 'No memory between runs' },
  { value: 'session', label: 'Session', description: 'Remembers within a session' },
  { value: 'persistent', label: 'Persistent', description: 'Persisted across sessions' },
];

const fieldCls =
  'w-full rounded-lg border border-border bg-surface px-3 py-2 text-sm text-fg placeholder-fg-muted focus:outline-none focus:ring-2 focus:ring-brand disabled:opacity-50 disabled:cursor-not-allowed';
const labelCls = 'block text-xs font-medium text-fg-muted mb-1';

// ============================================================================
// Component
// ============================================================================

/**
 * AgentAdvancedSettings
 *
 * Renders a two-section form:
 * 1. **Basic settings** (always visible) — name, description.
 * 2. **Advanced settings** (collapsed accordion) — model, temperature, timeout,
 *    tool permissions, memory mode, and (flag-gated) planning mode.
 *
 * @example
 * ```tsx
 * <AgentAdvancedSettings
 *   basic={{ name: 'My Agent', description: 'Does stuff' }}
 *   advanced={defaultAdvancedConfig}
 *   onBasicChange={(patch) => setBasic(prev => ({ ...prev, ...patch }))}
 *   onAdvancedChange={(patch) => setAdvanced(prev => ({ ...prev, ...patch }))}
 * />
 * ```
 */
export const AgentAdvancedSettings: React.FC<AgentAdvancedSettingsProps> = ({
  basic,
  advanced,
  readOnly = false,
  onBasicChange,
  onAdvancedChange,
}) => {
  const { isFeatureEnabled } = useFeatureFlag();
  const planningAvailable = isFeatureEnabled(FeatureFlag.AGENT_ORCHESTRATION);

  const [temperatureInput, setTemperatureInput] = useState<string>(String(advanced.temperature));
  const [maxTokensInput, setMaxTokensInput] = useState<string>(String(advanced.maxTokens));
  const [timeoutInput, setTimeoutInput] = useState<string>(String(advanced.timeoutMs));

  const handleTemperatureBlur = useCallback(() => {
    const parsed = parseFloat(temperatureInput);
    if (!isNaN(parsed) && parsed >= 0 && parsed <= 2) {
      onAdvancedChange({ temperature: parsed });
    } else {
      setTemperatureInput(String(advanced.temperature));
    }
  }, [temperatureInput, advanced.temperature, onAdvancedChange]);

  const handleMaxTokensBlur = useCallback(() => {
    const parsed = parseInt(maxTokensInput, 10);
    if (!isNaN(parsed) && parsed > 0) {
      onAdvancedChange({ maxTokens: parsed });
    } else {
      setMaxTokensInput(String(advanced.maxTokens));
    }
  }, [maxTokensInput, advanced.maxTokens, onAdvancedChange]);

  const handleTimeoutBlur = useCallback(() => {
    const parsed = parseInt(timeoutInput, 10);
    if (!isNaN(parsed) && parsed > 0) {
      onAdvancedChange({ timeoutMs: parsed });
    } else {
      setTimeoutInput(String(advanced.timeoutMs));
    }
  }, [timeoutInput, advanced.timeoutMs, onAdvancedChange]);

  const advancedContent = (
    <Box className="space-y-4 py-2">
      {/* Model */}
      <div>
        <label htmlFor="agent-model" className={labelCls}>
          Model
        </label>
        <Select
          id="agent-model"
          className={fieldCls}
          value={advanced.model}
          disabled={readOnly}
          onChange={(e) => {
            onAdvancedChange({ model: e.target.value as AgentModel });
          }}
          data-testid="agent-model-select"
        >
          {MODEL_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </Select>
      </div>

      {/* Temperature */}
      <div>
        <label htmlFor="agent-temperature" className={labelCls}>
          Temperature (0 – 2)
        </label>
        <Input
          id="agent-temperature"
          type="number"
          min={0}
          max={2}
          step={0.1}
          className={fieldCls}
          value={temperatureInput}
          disabled={readOnly}
          onChange={(e) => setTemperatureInput(e.target.value)}
          onBlur={handleTemperatureBlur}
          data-testid="agent-temperature-input"
        />
      </div>

      {/* Max tokens */}
      <div>
        <label htmlFor="agent-max-tokens" className={labelCls}>
          Max tokens
        </label>
        <Input
          id="agent-max-tokens"
          type="number"
          min={1}
          className={fieldCls}
          value={maxTokensInput}
          disabled={readOnly}
          onChange={(e) => setMaxTokensInput(e.target.value)}
          onBlur={handleMaxTokensBlur}
          data-testid="agent-max-tokens-input"
        />
      </div>

      {/* Timeout */}
      <div>
        <label htmlFor="agent-timeout" className={labelCls}>
          Request timeout (ms)
        </label>
        <Input
          id="agent-timeout"
          type="number"
          min={1000}
          step={1000}
          className={fieldCls}
          value={timeoutInput}
          disabled={readOnly}
          onChange={(e) => setTimeoutInput(e.target.value)}
          onBlur={handleTimeoutBlur}
          data-testid="agent-timeout-input"
        />
      </div>

      {/* Tools */}
      <div className="flex items-center justify-between">
        <div>
          <Typography variant="body2" className="font-medium text-fg">
            Enable tools
          </Typography>
          <Typography variant="caption" className="text-fg-muted">
            Allow the agent to call external tools and APIs
          </Typography>
        </div>
        <Switch
          checked={advanced.toolsEnabled}
          disabled={readOnly}
          onChange={(_e, checked) => onAdvancedChange({ toolsEnabled: checked })}
          data-testid="agent-tools-switch"
        />
      </div>

      {/* Memory mode */}
      <div>
        <span className={labelCls}>Memory mode</span>
        <div className="mt-1 flex gap-2 flex-wrap" role="radiogroup" aria-label="Memory mode">
          {MEMORY_MODE_OPTIONS.map((opt) => (
            <Button
              key={opt.value}
              variant="ghost"
              size="sm"
              role="radio"
              aria-checked={advanced.memoryMode === opt.value}
              disabled={readOnly}
              onClick={() => onAdvancedChange({ memoryMode: opt.value })}
              title={opt.description}
              data-testid={`agent-memory-${opt.value}`}
              className={[
                'rounded-lg border px-3 py-1.5 text-sm transition-colors',
                advanced.memoryMode === opt.value
                  ? 'border-brand bg-brand/10 text-brand font-medium'
                  : 'border-border bg-surface text-fg hover:border-brand/40',
                readOnly ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
              ].join(' ')}
            >
              {opt.label}
            </Button>
          ))}
        </div>
      </div>

      {/* Planning mode — gated by feature flag */}
      {planningAvailable && (
        <div className="flex items-center justify-between" data-testid="agent-planning-section">
          <div>
            <Typography variant="body2" className="font-medium text-fg">
              Multi-step planning{' '}
              <span className="ml-1 rounded bg-brand/10 px-1.5 py-0.5 text-xs font-medium text-brand">
                Experimental
              </span>
            </Typography>
            <Typography variant="caption" className="text-fg-muted">
              Enable ReAct-style goal decomposition and step planning
            </Typography>
          </div>
          <Switch
            checked={advanced.planningEnabled}
            disabled={readOnly}
            onChange={(_e, checked) => onAdvancedChange({ planningEnabled: checked })}
            data-testid="agent-planning-switch"
          />
        </div>
      )}
    </Box>
  );

  return (
    <Box className="space-y-6">
      {/* Basic settings — always visible */}
      <section aria-label="Basic agent settings">
        <div className="space-y-4">
          <div>
            <label htmlFor="agent-name" className={labelCls}>
              Agent name
            </label>
            <Input
              id="agent-name"
              type="text"
              className={fieldCls}
              value={basic.name}
              disabled={readOnly}
              placeholder="My Agent"
              onChange={(e) => onBasicChange({ name: e.target.value })}
              data-testid="agent-name-input"
            />
          </div>
          <div>
            <label htmlFor="agent-description" className={labelCls}>
              Description
            </label>
            <Textarea
              id="agent-description"
              rows={3}
              resize="vertical"
              className={fieldCls}
              value={basic.description}
              disabled={readOnly}
              placeholder="Describe what this agent does…"
              onChange={(e) => onBasicChange({ description: e.target.value })}
              data-testid="agent-description-input"
            />
          </div>
        </div>
      </section>

      {/* Advanced settings — progressive disclosure via Accordion */}
      <section aria-label="Advanced agent settings">
        <Accordion
          items={[
            {
              id: 'advanced',
              title: 'Advanced settings',
              children: advancedContent,
            },
          ]}
          allowMultiple={false}
          data-testid="agent-advanced-accordion"
        />
        <Box className="mt-1 flex items-center gap-1">
          <Settings2 size={12} className="text-fg-muted" />
          <Typography variant="caption" className="text-fg-muted">
            Advanced settings are optional. Defaults work for most use cases.
          </Typography>
        </Box>
      </section>
    </Box>
  );
};

AgentAdvancedSettings.displayName = 'AgentAdvancedSettings';

// ============================================================================
// Default config factory
// ============================================================================

/**
 * Returns a sensible default advanced configuration for a new agent.
 */
export function defaultAgentAdvancedConfig(): AgentAdvancedConfig {
  return {
    model: 'gpt-4o-mini',
    temperature: 0.7,
    maxTokens: 4096,
    timeoutMs: 30000,
    toolsEnabled: false,
    memoryMode: 'session',
    planningEnabled: false,
  };
}
