/**
 * @yappc/initialization-ui
 *
 * YAPPC initialization wizard and progress UI components.
 * Provides typed domain primitives and React components for the
 * project-initialization onboarding flow.
 *
 * @doc.type module
 * @doc.purpose Domain UI library for YAPPC project initialization flows
 * @doc.layer product
 * @doc.pattern ComponentLibrary
 */

import React from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// Domain type aliases
// ─────────────────────────────────────────────────────────────────────────────

/** Cloud deployment provider identifier. */
export type CloudProvider =
  | 'vercel'
  | 'netlify'
  | 'cloudflare'
  | 'aws'
  | 'gcp'
  | 'azure'
  | 'fly'
  | 'render'
  | string;

/** Git / CI-CD / hosting provider descriptor. */
export interface Provider {
  id: string;
  name: string;
  description: string;
  features: string[];
  recommended?: boolean;
  pricing?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress domain types
// ─────────────────────────────────────────────────────────────────────────────

/** Status of a single progress step. */
export type StepStatus = 'pending' | 'running' | 'completed' | 'failed' | 'skipped';

/** A single step in an initialization progress sequence. */
export interface ProgressStep {
  id: string;
  name: string;
  description: string;
  status: StepStatus;
  duration?: number; // milliseconds
  error?: string;
}

/** A structured log entry produced during initialization. */
export interface LogEntry {
  id: string;
  timestamp: Date;
  level: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  stepId?: string;
}

/** A cloud resource created during initialization. */
export interface Resource {
  id: string;
  name: string;
  type: string;
  provider: CloudProvider;
  status: 'creating' | 'active' | 'failed';
  url?: string;
  cost?: number;
  costUnit?: string;
}

/** A wizard step definition for wizard navigation. */
export interface WizardStep {
  id: string;
  label: string;
  completed: boolean;
  active?: boolean;
}

// ─────────────────────────────────────────────────────────────────────────────
// Wizard domain types
// ─────────────────────────────────────────────────────────────────────────────

/** Infrastructure sizing values. */
export interface InfrastructureValues {
  database: {
    provider: CloudProvider;
    plan: string;
    region: string;
  };
  cache?: {
    provider: CloudProvider;
    plan: string;
  };
  storage?: {
    provider: CloudProvider;
    plan: string;
  };
}

/** Cost estimates for a given infrastructure configuration. */
export interface CostEstimates {
  monthly: number;
  annual: number;
  breakdown: Array<{
    service: string;
    cost: number;
    unit: string;
  }>;
  currency: string;
}

/** Individual environment configuration. */
export interface Environment {
  id: string;
  name: string;
  type: 'development' | 'staging' | 'production';
  variables: Record<string, string>;
  autoDeployBranch?: string;
}

/** Validation result for a wizard step. */
export interface StepValidation {
  valid: boolean;
  errors: Array<{ field: string; message: string }>;
  warnings?: Array<{ field: string; message: string }>;
}

/** A step definition used by the ConfigurationWizard. */
export interface WizardStepDefinition {
  id: string;
  label: string;
  description?: string;
  validate?: (values: unknown) => StepValidation;
}

// ─────────────────────────────────────────────────────────────────────────────
// Components — LiveProgressViewer
// ─────────────────────────────────────────────────────────────────────────────

export interface LiveProgressViewerProps {
  steps: ProgressStep[];
  logs: LogEntry[];
  title?: string;
  isRunning: boolean;
  startTime: Date;
  onCancel?: () => void;
  onRetry?: () => void;
  showLogs?: boolean;
  onToggleLogs?: (show: boolean) => void;
}

/**
 * Displays real-time initialization progress with step tracking and log output.
 *
 * @doc.type component
 * @doc.purpose Real-time progress viewer for initialization flows
 * @doc.layer product
 * @doc.pattern Display
 */
export const LiveProgressViewer: React.FC<LiveProgressViewerProps> = ({
  steps,
  logs,
  title = 'Progress',
  isRunning,
  startTime,
  onCancel,
  onRetry,
  showLogs = true,
  onToggleLogs,
}) => {
  const completedCount = steps.filter((s) => s.status === 'completed').length;
  const failedStep = steps.find((s) => s.status === 'failed');
  const elapsed = Date.now() - startTime.getTime();
  const elapsedSecs = Math.floor(elapsed / 1000);

  return (
    <div className="live-progress-viewer" aria-label={title} aria-live="polite">
      <div className="lpv-header">
        <span className="lpv-title">{title}</span>
        <span className="lpv-count">
          {completedCount}/{steps.length} steps
        </span>
        {isRunning && (
          <span className="lpv-elapsed" aria-label="Elapsed time">
            {elapsedSecs}s
          </span>
        )}
      </div>

      <ol className="lpv-steps" aria-label="Initialization steps">
        {steps.map((step) => (
          <li
            key={step.id}
            className={`lpv-step lpv-step--${step.status}`}
            aria-label={`${step.name}: ${step.status}`}
          >
            <span className="lpv-step-name">{step.name}</span>
            <span className="lpv-step-status">{step.status}</span>
            {step.error != null && (
              <span className="lpv-step-error" role="alert">
                {step.error}
              </span>
            )}
          </li>
        ))}
      </ol>

      {showLogs && (
        <div className="lpv-logs" aria-label="Log output">
          {onToggleLogs != null && (
            <button type="button" onClick={() => onToggleLogs(false)}>
              Hide logs
            </button>
          )}
          <ul>
            {logs.map((entry) => (
              <li key={entry.id} className={`lpv-log lpv-log--${entry.level}`}>
                <time dateTime={entry.timestamp.toISOString()}>
                  {entry.timestamp.toLocaleTimeString()}
                </time>
                <span>{entry.message}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="lpv-actions">
        {isRunning && onCancel != null && (
          <button type="button" onClick={onCancel}>
            Cancel
          </button>
        )}
        {failedStep != null && onRetry != null && (
          <button type="button" onClick={onRetry}>
            Retry
          </button>
        )}
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Components — ResourcesList
// ─────────────────────────────────────────────────────────────────────────────

export interface ResourcesListProps {
  resources: Resource[];
  showFilters?: boolean;
  showCost?: boolean;
  compact?: boolean;
}

/**
 * Displays a list of cloud resources created during initialization.
 *
 * @doc.type component
 * @doc.purpose Show created resources with status and optional cost
 * @doc.layer product
 * @doc.pattern Display
 */
export const ResourcesList: React.FC<ResourcesListProps> = ({
  resources,
  showCost = false,
  compact = false,
}) => {
  if (resources.length === 0) return null;

  return (
    <ul
      className={`resources-list${compact ? ' resources-list--compact' : ''}`}
      aria-label="Created resources"
    >
      {resources.map((resource) => (
        <li
          key={resource.id}
          className={`resource-item resource-item--${resource.status}`}
        >
          <span className="resource-name">{resource.name}</span>
          <span className="resource-type">{resource.type}</span>
          <span className="resource-status">{resource.status}</span>
          {resource.url != null && (
            <a
              href={resource.url}
              target="_blank"
              rel="noopener noreferrer"
              className="resource-url"
            >
              Open
            </a>
          )}
          {showCost && resource.cost != null && (
            <span className="resource-cost">
              {resource.cost} {resource.costUnit ?? '/mo'}
            </span>
          )}
        </li>
      ))}
    </ul>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Components — StepProgress
// ─────────────────────────────────────────────────────────────────────────────

export interface StepProgressProps {
  steps: WizardStep[];
  variant?: 'horizontal' | 'vertical';
  showLabels?: boolean;
  showDuration?: boolean;
  onStepClick?: (stepId: string) => void;
}

/**
 * Visual progress indicator for a multi-step wizard.
 *
 * @doc.type component
 * @doc.purpose Show progress through a sequence of wizard steps
 * @doc.layer product
 * @doc.pattern Navigation
 */
export const StepProgress: React.FC<StepProgressProps> = ({
  steps,
  variant = 'horizontal',
  showLabels = true,
  onStepClick,
}) => (
  <nav
    className={`step-progress step-progress--${variant}`}
    aria-label="Wizard progress"
  >
    <ol>
      {steps.map((step, index) => (
        <li
          key={step.id}
          className={[
            'step-progress-item',
            step.completed ? 'step-progress-item--completed' : '',
            step.active === true ? 'step-progress-item--active' : '',
          ]
            .filter(Boolean)
            .join(' ')}
          aria-current={step.active === true ? 'step' : undefined}
        >
          {onStepClick != null ? (
            <button type="button" onClick={() => onStepClick(step.id)}>
              <span className="step-number">{index + 1}</span>
              {showLabels && <span className="step-label">{step.label}</span>}
            </button>
          ) : (
            <>
              <span className="step-number">{index + 1}</span>
              {showLabels && <span className="step-label">{step.label}</span>}
            </>
          )}
        </li>
      ))}
    </ol>
  </nav>
);

// ─────────────────────────────────────────────────────────────────────────────
// Components — ProviderSelector
// ─────────────────────────────────────────────────────────────────────────────

export interface ProviderSelectorProps {
  category: string;
  title?: string;
  providers: Provider[];
  selectedProvider: string;
  onSelect: (id: string) => void;
  showPricing?: boolean;
  variant?: 'cards' | 'list';
}

/**
 * Provider selection widget used in wizard steps.
 *
 * @doc.type component
 * @doc.purpose Select a cloud/service provider from a list of options
 * @doc.layer product
 * @doc.pattern Form
 */
export const ProviderSelector: React.FC<ProviderSelectorProps> = ({
  category,
  title,
  providers,
  selectedProvider,
  onSelect,
  showPricing = false,
  variant = 'cards',
}) => (
  <div
    className={`provider-selector provider-selector--${variant}`}
    role="radiogroup"
    aria-label={title ?? `Select ${category} provider`}
  >
    {title != null && <h4 className="provider-selector-title">{title}</h4>}
    <ul>
      {providers.map((provider) => (
        <li key={provider.id}>
          <label
            className={[
              'provider-option',
              selectedProvider === provider.id ? 'provider-option--selected' : '',
              provider.recommended === true ? 'provider-option--recommended' : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <input
              type="radio"
              name={`provider-${category}`}
              value={provider.id}
              checked={selectedProvider === provider.id}
              onChange={() => onSelect(provider.id)}
            />
            <span className="provider-name">{provider.name}</span>
            <span className="provider-description">{provider.description}</span>
            {provider.features.length > 0 && (
              <ul className="provider-features" aria-label="Features">
                {provider.features.map((f) => (
                  <li key={f}>{f}</li>
                ))}
              </ul>
            )}
            {showPricing && provider.pricing != null && (
              <span className="provider-pricing">{provider.pricing}</span>
            )}
            {provider.recommended === true && (
              <span className="provider-badge">Recommended</span>
            )}
          </label>
        </li>
      ))}
    </ul>
  </div>
);

// ─────────────────────────────────────────────────────────────────────────────
// Components — InfrastructureForm
// ─────────────────────────────────────────────────────────────────────────────

export interface InfrastructureFormProps {
  values: InfrastructureValues;
  onChange: (values: InfrastructureValues) => void;
}

/**
 * Form for selecting infrastructure (database, cache, storage) configuration.
 *
 * @doc.type component
 * @doc.purpose Configure database/cache/storage providers and plans
 * @doc.layer product
 * @doc.pattern Form
 */
export const InfrastructureForm: React.FC<InfrastructureFormProps> = ({
  values,
  onChange,
}) => {
  const dbProviders: Array<{ id: CloudProvider; label: string }> = [
    { id: 'aws', label: 'AWS RDS' },
    { id: 'gcp', label: 'Cloud SQL' },
    { id: 'azure', label: 'Azure Database' },
    { id: 'render', label: 'Render Postgres' },
  ];

  return (
    <div className="infrastructure-form">
      <fieldset>
        <legend>Database</legend>
        <div className="form-field">
          <label htmlFor="db-provider">Provider</label>
          <select
            id="db-provider"
            value={values.database.provider}
            onChange={(e) =>
              onChange({
                ...values,
                database: { ...values.database, provider: e.target.value as CloudProvider },
              })
            }
          >
            {dbProviders.map(({ id, label }) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="db-region">Region</label>
          <input
            id="db-region"
            type="text"
            value={values.database.region}
            onChange={(e) =>
              onChange({
                ...values,
                database: { ...values.database, region: e.target.value },
              })
            }
          />
        </div>
      </fieldset>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Components — CostEstimator
// ─────────────────────────────────────────────────────────────────────────────

export interface CostEstimatorProps {
  infrastructure: InfrastructureValues;
  estimates: CostEstimates | null;
  isLoading?: boolean;
}

/**
 * Displays estimated monthly cost for the selected infrastructure.
 *
 * @doc.type component
 * @doc.purpose Show cost breakdown for infrastructure choices
 * @doc.layer product
 * @doc.pattern Display
 */
export const CostEstimator: React.FC<CostEstimatorProps> = ({
  estimates,
  isLoading = false,
}) => {
  if (isLoading) {
    return <div className="cost-estimator cost-estimator--loading">Estimating cost…</div>;
  }

  if (estimates == null) {
    return <div className="cost-estimator cost-estimator--empty">No estimate available</div>;
  }

  return (
    <div className="cost-estimator">
      <div className="cost-summary">
        <span className="cost-monthly">
          {estimates.currency} {estimates.monthly.toFixed(2)}/mo
        </span>
        <span className="cost-annual">
          {estimates.currency} {estimates.annual.toFixed(2)}/yr
        </span>
      </div>
      <ul className="cost-breakdown" aria-label="Cost breakdown">
        {estimates.breakdown.map((item) => (
          <li key={item.service}>
            <span>{item.service}</span>
            <span>
              {estimates.currency} {item.cost.toFixed(2)} {item.unit}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Components — EnvironmentTabs
// ─────────────────────────────────────────────────────────────────────────────

export interface EnvironmentTabsProps {
  environments: Environment[];
  activeEnvironmentId: string;
  onEnvironmentChange: (id: string) => void;
  onVariableChange: (environmentId: string, key: string, value: string) => void;
}

/**
 * Tab-based UI for configuring per-environment variables.
 *
 * @doc.type component
 * @doc.purpose Configure environment variables per deployment environment
 * @doc.layer product
 * @doc.pattern Form
 */
export const EnvironmentTabs: React.FC<EnvironmentTabsProps> = ({
  environments,
  activeEnvironmentId,
  onEnvironmentChange,
  onVariableChange,
}) => {
  const activeEnv = environments.find((e) => e.id === activeEnvironmentId);

  return (
    <div className="environment-tabs">
      <div className="env-tab-list" role="tablist" aria-label="Environments">
        {environments.map((env) => (
          <button
            key={env.id}
            type="button"
            role="tab"
            aria-selected={env.id === activeEnvironmentId}
            aria-controls={`env-panel-${env.id}`}
            className={[
              'env-tab',
              env.id === activeEnvironmentId ? 'env-tab--active' : '',
            ]
              .filter(Boolean)
              .join(' ')}
            onClick={() => onEnvironmentChange(env.id)}
          >
            {env.name}
          </button>
        ))}
      </div>

      {activeEnv != null && (
        <div
          id={`env-panel-${activeEnv.id}`}
          role="tabpanel"
          aria-label={`${activeEnv.name} variables`}
          className="env-panel"
        >
          <table className="env-vars-table">
            <thead>
              <tr>
                <th scope="col">Variable</th>
                <th scope="col">Value</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(activeEnv.variables).map(([key, val]) => (
                <tr key={key}>
                  <td>
                    <code>{key}</code>
                  </td>
                  <td>
                    <input
                      type="text"
                      value={val}
                      aria-label={key}
                      onChange={(e) =>
                        onVariableChange(activeEnv.id, key, e.target.value)
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Components — ConfigurationWizard
// ─────────────────────────────────────────────────────────────────────────────

export interface ConfigurationWizardProps {
  steps: WizardStepDefinition[];
  currentStepIndex: number;
  onNext: () => void;
  onBack: () => void;
  onComplete: () => void;
  isSubmitting?: boolean;
  children: React.ReactNode;
}

/**
 * Multi-step configuration wizard shell.
 *
 * @doc.type component
 * @doc.purpose Scaffold multi-step form flows with navigation and validation
 * @doc.layer product
 * @doc.pattern Wizard
 */
export const ConfigurationWizard: React.FC<ConfigurationWizardProps> = ({
  steps,
  currentStepIndex,
  onNext,
  onBack,
  onComplete,
  isSubmitting = false,
  children,
}) => {
  const isFirst = currentStepIndex === 0;
  const isLast = currentStepIndex === steps.length - 1;
  const currentStep = steps[currentStepIndex];

  return (
    <div className="configuration-wizard">
      <header className="wizard-header">
        <h2>{currentStep?.label}</h2>
        {currentStep?.description != null && (
          <p>{currentStep.description}</p>
        )}
        <StepProgress
          steps={steps.map((s, i) => ({
            id: s.id,
            label: s.label,
            completed: i < currentStepIndex,
            active: i === currentStepIndex,
          }))}
          variant="horizontal"
          showLabels
        />
      </header>

      <div className="wizard-body">{children}</div>

      <footer className="wizard-footer">
        <button type="button" onClick={onBack} disabled={isFirst || isSubmitting}>
          Back
        </button>
        {isLast ? (
          <button type="button" onClick={onComplete} disabled={isSubmitting}>
            {isSubmitting ? 'Submitting…' : 'Complete Setup'}
          </button>
        ) : (
          <button type="button" onClick={onNext} disabled={isSubmitting}>
            Next
          </button>
        )}
      </footer>
    </div>
  );
};
