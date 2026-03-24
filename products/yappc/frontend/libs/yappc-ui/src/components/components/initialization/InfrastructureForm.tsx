/**
 * InfrastructureForm Component
 *
 * @description Comprehensive form for configuring infrastructure settings
 * including providers, regions, resources, and deployment options.
 *
 * @doc.type component
 * @doc.purpose infrastructure-configuration
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <InfrastructureForm
 *   initialValues={{
 *     projectName: 'my-project',
 *     provider: 'vercel',
 *     region: 'us-east-1',
 *   }}
 *   onSubmit={(values) => handleInfrastructure(values)}
 *   onValidate={(values) => validateConfig(values)}
 * />
 * ```
 */

import React, { useState, useMemo, useCallback, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Cloud provider options
 */
export type CloudProvider =
  | 'vercel'
  | 'railway'
  | 'aws'
  | 'gcp'
  | 'azure'
  | 'digitalocean'
  | 'render'
  | 'fly';

/**
 * Database type options
 */
export type DatabaseType =
  | 'postgres'
  | 'mysql'
  | 'mongodb'
  | 'redis'
  | 'supabase'
  | 'planetscale'
  | 'neon'
  | 'none';

/**
 * Deployment environment
 */
export type DeployEnvironment = 'development' | 'staging' | 'production';

/**
 * Region info
 */
export interface Region {
  /** Region code */
  code: string;
  /** Display name */
  name: string;
  /** Flag emoji */
  flag?: string;
  /** Latency indicator (ms from user's location) */
  latency?: number;
  /** Whether this region is recommended */
  recommended?: boolean;
}

/**
 * Resource scaling option
 */
export interface ScalingOption {
  /** Option ID */
  id: string;
  /** Display label */
  label: string;
  /** CPU cores */
  cpu: number;
  /** Memory in MB */
  memory: number;
  /** Estimated monthly cost */
  estimatedCost: number;
}

/**
 * Form field error
 */
export interface FieldError {
  /** Field name */
  field: string;
  /** Error message */
  message: string;
}

/**
 * Infrastructure form values
 */
export interface InfrastructureValues {
  /** Project name */
  projectName: string;
  /** Selected cloud provider */
  provider: CloudProvider;
  /** Deployment region */
  region: string;
  /** Database type */
  database: DatabaseType;
  /** Database region (can differ from compute) */
  databaseRegion?: string;
  /** Enable CDN */
  enableCDN: boolean;
  /** CDN regions */
  cdnRegions?: string[];
  /** Scaling configuration */
  scaling: string;
  /** Auto-scale enabled */
  autoScale: boolean;
  /** Min instances (if auto-scale) */
  minInstances?: number;
  /** Max instances (if auto-scale) */
  maxInstances?: number;
  /** Deploy environments */
  environments: DeployEnvironment[];
  /** Custom domain */
  customDomain?: string;
  /** Environment variables */
  envVariables: Record<string, string>;
  /** Enable monitoring */
  enableMonitoring: boolean;
  /** Enable logging */
  enableLogging: boolean;
}

/**
 * Props for the InfrastructureForm component
 */
export interface InfrastructureFormProps {
  /** Initial form values */
  initialValues?: Partial<InfrastructureValues>;
  /** Callback when form is submitted */
  onSubmit: (values: InfrastructureValues) => void;
  /** Custom validation function */
  onValidate?: (values: InfrastructureValues) => FieldError[];
  /** Callback on value change */
  onChange?: (values: InfrastructureValues) => void;
  /** Available regions by provider */
  regionsByProvider?: Record<CloudProvider, Region[]>;
  /** Available scaling options */
  scalingOptions?: ScalingOption[];
  /** Loading state */
  loading?: boolean;
  /** Whether form is in compact mode */
  compact?: boolean;
  /** Show estimated cost */
  showCostEstimate?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_REGIONS: Record<CloudProvider, Region[]> = {
  vercel: [
    { code: 'iad1', name: 'Washington, D.C.', flag: '🇺🇸', recommended: true },
    { code: 'sfo1', name: 'San Francisco', flag: '🇺🇸' },
    { code: 'dub1', name: 'Dublin', flag: '🇮🇪' },
    { code: 'sin1', name: 'Singapore', flag: '🇸🇬' },
    { code: 'hnd1', name: 'Tokyo', flag: '🇯🇵' },
  ],
  railway: [
    { code: 'us-west1', name: 'US West', flag: '🇺🇸', recommended: true },
    { code: 'us-east1', name: 'US East', flag: '🇺🇸' },
    { code: 'eu-west1', name: 'EU West', flag: '🇪🇺' },
  ],
  aws: [
    { code: 'us-east-1', name: 'N. Virginia', flag: '🇺🇸', recommended: true },
    { code: 'us-west-2', name: 'Oregon', flag: '🇺🇸' },
    { code: 'eu-west-1', name: 'Ireland', flag: '🇮🇪' },
    { code: 'ap-northeast-1', name: 'Tokyo', flag: '🇯🇵' },
  ],
  gcp: [
    { code: 'us-central1', name: 'Iowa', flag: '🇺🇸', recommended: true },
    { code: 'us-east1', name: 'South Carolina', flag: '🇺🇸' },
    { code: 'europe-west1', name: 'Belgium', flag: '🇧🇪' },
    { code: 'asia-east1', name: 'Taiwan', flag: '🇹🇼' },
  ],
  azure: [
    { code: 'eastus', name: 'East US', flag: '🇺🇸', recommended: true },
    { code: 'westus2', name: 'West US 2', flag: '🇺🇸' },
    { code: 'westeurope', name: 'West Europe', flag: '🇪🇺' },
    { code: 'japaneast', name: 'Japan East', flag: '🇯🇵' },
  ],
  digitalocean: [
    { code: 'nyc1', name: 'New York', flag: '🇺🇸', recommended: true },
    { code: 'sfo3', name: 'San Francisco', flag: '🇺🇸' },
    { code: 'ams3', name: 'Amsterdam', flag: '🇳🇱' },
    { code: 'sgp1', name: 'Singapore', flag: '🇸🇬' },
  ],
  render: [
    { code: 'oregon', name: 'Oregon', flag: '🇺🇸', recommended: true },
    { code: 'ohio', name: 'Ohio', flag: '🇺🇸' },
    { code: 'frankfurt', name: 'Frankfurt', flag: '🇩🇪' },
    { code: 'singapore', name: 'Singapore', flag: '🇸🇬' },
  ],
  fly: [
    { code: 'iad', name: 'Virginia', flag: '🇺🇸', recommended: true },
    { code: 'lax', name: 'Los Angeles', flag: '🇺🇸' },
    { code: 'lhr', name: 'London', flag: '🇬🇧' },
    { code: 'nrt', name: 'Tokyo', flag: '🇯🇵' },
  ],
};

const DEFAULT_SCALING_OPTIONS: ScalingOption[] = [
  { id: 'starter', label: 'Starter', cpu: 0.5, memory: 512, estimatedCost: 0 },
  { id: 'basic', label: 'Basic', cpu: 1, memory: 1024, estimatedCost: 10 },
  { id: 'standard', label: 'Standard', cpu: 2, memory: 2048, estimatedCost: 25 },
  { id: 'pro', label: 'Professional', cpu: 4, memory: 4096, estimatedCost: 50 },
  { id: 'enterprise', label: 'Enterprise', cpu: 8, memory: 8192, estimatedCost: 100 },
];

const PROVIDER_LABELS: Record<CloudProvider, string> = {
  vercel: 'Vercel',
  railway: 'Railway',
  aws: 'AWS',
  gcp: 'Google Cloud',
  azure: 'Azure',
  digitalocean: 'DigitalOcean',
  render: 'Render',
  fly: 'Fly.io',
};

const DATABASE_LABELS: Record<DatabaseType, string> = {
  postgres: 'PostgreSQL',
  mysql: 'MySQL',
  mongodb: 'MongoDB',
  redis: 'Redis',
  supabase: 'Supabase',
  planetscale: 'PlanetScale',
  neon: 'Neon',
  none: 'No Database',
};

const DEFAULT_VALUES: InfrastructureValues = {
  projectName: '',
  provider: 'vercel',
  region: 'iad1',
  database: 'none',
  enableCDN: false,
  scaling: 'starter',
  autoScale: false,
  environments: ['development'],
  envVariables: {},
  enableMonitoring: true,
  enableLogging: true,
};

// ============================================================================
// Sub-Components
// ============================================================================

interface FormFieldProps {
  label: string;
  htmlFor: string;
  required?: boolean;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}

const FormField: React.FC<FormFieldProps> = ({
  label,
  htmlFor,
  required,
  error,
  hint,
  children,
}) => (
  <div className={`form-field ${error ? 'form-field--error' : ''}`}>
    <label htmlFor={htmlFor} className="form-field-label">
      {label}
      {required && <span className="form-field-required">*</span>}
    </label>
    {children}
    {hint && !error && <p className="form-field-hint">{hint}</p>}
    {error && <p className="form-field-error">{error}</p>}
  </div>
);

interface EnvVariableRowProps {
  keyName: string;
  value: string;
  onKeyChange: (newKey: string) => void;
  onValueChange: (newValue: string) => void;
  onRemove: () => void;
}

const EnvVariableRow: React.FC<EnvVariableRowProps> = ({
  keyName,
  value,
  onKeyChange,
  onValueChange,
  onRemove,
}) => (
  <div className="env-variable-row">
    <input
      type="text"
      className="form-input env-key"
      placeholder="KEY"
      value={keyName}
      onChange={(e) => onKeyChange(e.target.value)}
    />
    <input
      type="text"
      className="form-input env-value"
      placeholder="Value"
      value={value}
      onChange={(e) => onValueChange(e.target.value)}
    />
    <button
      type="button"
      className="env-remove-btn"
      onClick={onRemove}
      aria-label="Remove variable"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <line x1="18" y1="6" x2="6" y2="18" />
        <line x1="6" y1="6" x2="18" y2="18" />
      </svg>
    </button>
  </div>
);

// ============================================================================
// Main Component
// ============================================================================

export const InfrastructureForm: React.FC<InfrastructureFormProps> = ({
  initialValues,
  onSubmit,
  onValidate,
  onChange,
  regionsByProvider = DEFAULT_REGIONS,
  scalingOptions = DEFAULT_SCALING_OPTIONS,
  loading = false,
  compact = false,
  showCostEstimate = true,
  className = '',
}) => {
  const [values, setValues] = useState<InfrastructureValues>({
    ...DEFAULT_VALUES,
    ...initialValues,
  });

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [envKeys, setEnvKeys] = useState<string[]>(
    Object.keys(initialValues?.envVariables || {})
  );

  // Available regions for selected provider
  const availableRegions = useMemo(() => {
    return regionsByProvider[values.provider] || [];
  }, [regionsByProvider, values.provider]);

  // Estimated monthly cost
  const estimatedCost = useMemo(() => {
    const scaling = scalingOptions.find((s) => s.id === values.scaling);
    let cost = scaling?.estimatedCost || 0;

    // Add costs based on options
    if (values.enableCDN) cost += 10;
    if (values.database !== 'none') cost += 15;
    if (values.enableMonitoring) cost += 5;
    if (values.environments.includes('staging')) cost *= 1.5;
    if (values.environments.includes('production')) cost *= 2;

    return Math.round(cost);
  }, [values, scalingOptions]);

  // Update values and notify parent
  const updateValue = useCallback(
    <K extends keyof InfrastructureValues>(
      field: K,
      value: InfrastructureValues[K]
    ) => {
      setValues((prev) => {
        const newValues = { ...prev, [field]: value };

        // When provider changes, reset region to first available
        if (field === 'provider') {
          const regions = regionsByProvider[value as CloudProvider];
          if (regions && regions.length > 0) {
            const recommended = regions.find((r) => r.recommended);
            newValues.region = recommended?.code || regions[0].code;
          }
        }

        onChange?.(newValues);
        return newValues;
      });
    },
    [onChange, regionsByProvider]
  );

  // Handle field blur (for touched state)
  const handleBlur = useCallback((field: string) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  }, []);

  // Validate form
  const validate = useCallback((): boolean => {
    const newErrors: Record<string, string> = {};

    // Required fields
    if (!values.projectName.trim()) {
      newErrors.projectName = 'Project name is required';
    } else if (!/^[a-z0-9-]+$/.test(values.projectName)) {
      newErrors.projectName =
        'Project name can only contain lowercase letters, numbers, and hyphens';
    }

    if (!values.region) {
      newErrors.region = 'Region is required';
    }

    if (values.environments.length === 0) {
      newErrors.environments = 'At least one environment is required';
    }

    if (values.autoScale) {
      if (
        values.minInstances === undefined ||
        values.minInstances < 0
      ) {
        newErrors.minInstances = 'Min instances must be 0 or greater';
      }
      if (
        values.maxInstances === undefined ||
        values.maxInstances < 1
      ) {
        newErrors.maxInstances = 'Max instances must be at least 1';
      }
      if (
        values.minInstances !== undefined &&
        values.maxInstances !== undefined &&
        values.minInstances > values.maxInstances
      ) {
        newErrors.maxInstances =
          'Max instances must be greater than min instances';
      }
    }

    if (values.customDomain) {
      const domainRegex = /^([a-z0-9]+(-[a-z0-9]+)*\.)+[a-z]{2,}$/i;
      if (!domainRegex.test(values.customDomain)) {
        newErrors.customDomain = 'Please enter a valid domain';
      }
    }

    // Custom validation
    if (onValidate) {
      const customErrors = onValidate(values);
      customErrors.forEach((err) => {
        newErrors[err.field] = err.message;
      });
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [values, onValidate]);

  // Handle form submit
  const handleSubmit = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();

      // Mark all fields as touched
      const allTouched: Record<string, boolean> = {};
      Object.keys(values).forEach((key) => {
        allTouched[key] = true;
      });
      setTouched(allTouched);

      if (validate()) {
        onSubmit(values);
      }
    },
    [values, validate, onSubmit]
  );

  // Environment variable management
  const addEnvVariable = useCallback(() => {
    const newKey = `VAR_${envKeys.length + 1}`;
    setEnvKeys((prev) => [...prev, newKey]);
    updateValue('envVariables', { ...values.envVariables, [newKey]: '' });
  }, [envKeys.length, values.envVariables, updateValue]);

  const updateEnvKey = useCallback(
    (oldKey: string, newKey: string) => {
      const newEnvVars = { ...values.envVariables };
      const value = newEnvVars[oldKey];
      delete newEnvVars[oldKey];
      newEnvVars[newKey] = value;
      updateValue('envVariables', newEnvVars);

      setEnvKeys((prev) => prev.map((k) => (k === oldKey ? newKey : k)));
    },
    [values.envVariables, updateValue]
  );

  const updateEnvValue = useCallback(
    (key: string, value: string) => {
      updateValue('envVariables', { ...values.envVariables, [key]: value });
    },
    [values.envVariables, updateValue]
  );

  const removeEnvVariable = useCallback(
    (key: string) => {
      const newEnvVars = { ...values.envVariables };
      delete newEnvVars[key];
      updateValue('envVariables', newEnvVars);
      setEnvKeys((prev) => prev.filter((k) => k !== key));
    },
    [values.envVariables, updateValue]
  );

  // Toggle environment
  const toggleEnvironment = useCallback(
    (env: DeployEnvironment) => {
      const newEnvs = values.environments.includes(env)
        ? values.environments.filter((e) => e !== env)
        : [...values.environments, env];
      updateValue('environments', newEnvs);
    },
    [values.environments, updateValue]
  );

  // Validate on values change
  useEffect(() => {
    if (Object.values(touched).some(Boolean)) {
      validate();
    }
  }, [values, touched, validate]);

  const containerClasses = [
    'infrastructure-form',
    compact && 'infrastructure-form--compact',
    loading && 'infrastructure-form--loading',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <form className={containerClasses} onSubmit={handleSubmit} noValidate>
      {/* Project Settings Section */}
      <section className="form-section">
        <h3 className="form-section-title">Project Settings</h3>

        <FormField
          label="Project Name"
          htmlFor="projectName"
          required
          error={touched.projectName ? errors.projectName : undefined}
          hint="Used for URLs, resources, and identification"
        >
          <input
            id="projectName"
            type="text"
            className="form-input"
            value={values.projectName}
            onChange={(e) => updateValue('projectName', e.target.value)}
            onBlur={() => handleBlur('projectName')}
            placeholder="my-awesome-project"
            disabled={loading}
          />
        </FormField>

        <FormField
          label="Custom Domain"
          htmlFor="customDomain"
          error={touched.customDomain ? errors.customDomain : undefined}
          hint="Optional: Add your own domain"
        >
          <input
            id="customDomain"
            type="text"
            className="form-input"
            value={values.customDomain || ''}
            onChange={(e) => updateValue('customDomain', e.target.value)}
            onBlur={() => handleBlur('customDomain')}
            placeholder="example.com"
            disabled={loading}
          />
        </FormField>
      </section>

      {/* Infrastructure Section */}
      <section className="form-section">
        <h3 className="form-section-title">Infrastructure</h3>

        <FormField label="Cloud Provider" htmlFor="provider" required>
          <select
            id="provider"
            className="form-select"
            value={values.provider}
            onChange={(e) => updateValue('provider', e.target.value as CloudProvider)}
            disabled={loading}
          >
            {Object.entries(PROVIDER_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </FormField>

        <FormField
          label="Region"
          htmlFor="region"
          required
          error={touched.region ? errors.region : undefined}
        >
          <select
            id="region"
            className="form-select"
            value={values.region}
            onChange={(e) => updateValue('region', e.target.value)}
            onBlur={() => handleBlur('region')}
            disabled={loading}
          >
            {availableRegions.map((region) => (
              <option key={region.code} value={region.code}>
                {region.flag} {region.name}{' '}
                {region.recommended ? '(Recommended)' : ''}
              </option>
            ))}
          </select>
        </FormField>

        <FormField label="Database" htmlFor="database">
          <select
            id="database"
            className="form-select"
            value={values.database}
            onChange={(e) => updateValue('database', e.target.value as DatabaseType)}
            disabled={loading}
          >
            {Object.entries(DATABASE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </FormField>
      </section>

      {/* Scaling Section */}
      <section className="form-section">
        <h3 className="form-section-title">Scaling</h3>

        <FormField label="Instance Size" htmlFor="scaling">
          <div className="scaling-options">
            {scalingOptions.map((option) => (
              <label
                key={option.id}
                className={`scaling-option ${
                  values.scaling === option.id ? 'scaling-option--selected' : ''
                }`}
              >
                <input
                  type="radio"
                  name="scaling"
                  value={option.id}
                  checked={values.scaling === option.id}
                  onChange={() => updateValue('scaling', option.id)}
                  disabled={loading}
                />
                <span className="scaling-option-content">
                  <span className="scaling-option-label">{option.label}</span>
                  <span className="scaling-option-specs">
                    {option.cpu} vCPU • {option.memory} MB RAM
                  </span>
                  <span className="scaling-option-cost">
                    {option.estimatedCost === 0
                      ? 'Free'
                      : `$${option.estimatedCost}/mo`}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </FormField>

        <div className="form-checkbox-group">
          <label className="form-checkbox">
            <input
              type="checkbox"
              checked={values.autoScale}
              onChange={(e) => updateValue('autoScale', e.target.checked)}
              disabled={loading}
            />
            <span>Enable auto-scaling</span>
          </label>
        </div>

        {values.autoScale && (
          <div className="form-row">
            <FormField
              label="Min Instances"
              htmlFor="minInstances"
              error={touched.minInstances ? errors.minInstances : undefined}
            >
              <input
                id="minInstances"
                type="number"
                className="form-input"
                min={0}
                value={values.minInstances ?? 1}
                onChange={(e) =>
                  updateValue('minInstances', parseInt(e.target.value, 10))
                }
                onBlur={() => handleBlur('minInstances')}
                disabled={loading}
              />
            </FormField>

            <FormField
              label="Max Instances"
              htmlFor="maxInstances"
              error={touched.maxInstances ? errors.maxInstances : undefined}
            >
              <input
                id="maxInstances"
                type="number"
                className="form-input"
                min={1}
                value={values.maxInstances ?? 5}
                onChange={(e) =>
                  updateValue('maxInstances', parseInt(e.target.value, 10))
                }
                onBlur={() => handleBlur('maxInstances')}
                disabled={loading}
              />
            </FormField>
          </div>
        )}
      </section>

      {/* Environments Section */}
      <section className="form-section">
        <h3 className="form-section-title">Environments</h3>

        <FormField
          label="Deploy to"
          htmlFor="environments"
          error={touched.environments ? errors.environments : undefined}
        >
          <div className="environment-toggles">
            {(['development', 'staging', 'production'] as DeployEnvironment[]).map(
              (env) => (
                <label
                  key={env}
                  className={`environment-toggle ${
                    values.environments.includes(env)
                      ? 'environment-toggle--active'
                      : ''
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={values.environments.includes(env)}
                    onChange={() => toggleEnvironment(env)}
                    disabled={loading}
                  />
                  <span>{env.charAt(0).toUpperCase() + env.slice(1)}</span>
                </label>
              )
            )}
          </div>
        </FormField>
      </section>

      {/* Additional Options Section */}
      <section className="form-section">
        <h3 className="form-section-title">Additional Options</h3>

        <div className="form-checkbox-group">
          <label className="form-checkbox">
            <input
              type="checkbox"
              checked={values.enableCDN}
              onChange={(e) => updateValue('enableCDN', e.target.checked)}
              disabled={loading}
            />
            <span>Enable CDN for static assets</span>
          </label>

          <label className="form-checkbox">
            <input
              type="checkbox"
              checked={values.enableMonitoring}
              onChange={(e) => updateValue('enableMonitoring', e.target.checked)}
              disabled={loading}
            />
            <span>Enable monitoring & alerts</span>
          </label>

          <label className="form-checkbox">
            <input
              type="checkbox"
              checked={values.enableLogging}
              onChange={(e) => updateValue('enableLogging', e.target.checked)}
              disabled={loading}
            />
            <span>Enable centralized logging</span>
          </label>
        </div>
      </section>

      {/* Environment Variables Section */}
      <section className="form-section">
        <h3 className="form-section-title">
          Environment Variables
          <button
            type="button"
            className="add-env-btn"
            onClick={addEnvVariable}
            disabled={loading}
          >
            + Add Variable
          </button>
        </h3>

        <div className="env-variables-list">
          {envKeys.length === 0 ? (
            <p className="env-empty">
              No environment variables defined. Click &quot;Add Variable&quot; to add
              one.
            </p>
          ) : (
            envKeys.map((key) => (
              <EnvVariableRow
                key={key}
                keyName={key}
                value={values.envVariables[key] || ''}
                onKeyChange={(newKey) => updateEnvKey(key, newKey)}
                onValueChange={(value) => updateEnvValue(key, value)}
                onRemove={() => removeEnvVariable(key)}
              />
            ))
          )}
        </div>
      </section>

      {/* Cost Estimate */}
      {showCostEstimate && (
        <div className="form-cost-estimate">
          <div className="cost-estimate-label">Estimated Monthly Cost</div>
          <div className="cost-estimate-value">${estimatedCost}/mo</div>
          <div className="cost-estimate-note">
            Actual costs may vary based on usage
          </div>
        </div>
      )}

      {/* Submit Button */}
      <div className="form-actions">
        <button
          type="submit"
          className="form-submit-btn"
          disabled={loading}
        >
          {loading ? 'Processing...' : 'Continue'}
        </button>
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .infrastructure-form {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
        }

        .infrastructure-form--loading {
          opacity: 0.7;
          pointer-events: none;
        }

        .infrastructure-form--compact .form-section {
          padding: 0.75rem;
        }

        .form-section {
          padding: 1rem;
          background: #F9FAFB;
          border-radius: 10px;
        }

        .form-section-title {
          margin: 0 0 1rem;
          font-size: 0.875rem;
          font-weight: 600;
          color: #374151;
          display: flex;
          align-items: center;
          justify-content: space-between;
        }

        .form-field {
          margin-bottom: 1rem;
        }

        .form-field:last-child {
          margin-bottom: 0;
        }

        .form-field-label {
          display: block;
          margin-bottom: 0.375rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #374151;
        }

        .form-field-required {
          color: #EF4444;
          margin-left: 0.25rem;
        }

        .form-field-hint {
          margin: 0.25rem 0 0;
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .form-field-error {
          margin: 0.25rem 0 0;
          font-size: 0.625rem;
          color: #EF4444;
        }

        .form-field--error .form-input,
        .form-field--error .form-select {
          border-color: #EF4444;
        }

        .form-input,
        .form-select {
          width: 100%;
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          background: #fff;
          border: 1px solid #D1D5DB;
          border-radius: 6px;
          transition: border-color 0.15s ease;
        }

        .form-input:focus,
        .form-select:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
        }

        .form-input:disabled,
        .form-select:disabled {
          background: #F3F4F6;
          cursor: not-allowed;
        }

        .form-row {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 0.75rem;
        }

        .scaling-options {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .scaling-option {
          display: flex;
          align-items: flex-start;
          padding: 0.75rem;
          background: #fff;
          border: 2px solid #E5E7EB;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .scaling-option:hover {
          border-color: #3B82F6;
        }

        .scaling-option--selected {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .scaling-option input {
          margin-right: 0.75rem;
          margin-top: 0.125rem;
        }

        .scaling-option-content {
          display: flex;
          flex-direction: column;
          flex: 1;
        }

        .scaling-option-label {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .scaling-option-specs {
          font-size: 0.75rem;
          color: #6B7280;
          margin-top: 0.125rem;
        }

        .scaling-option-cost {
          font-size: 0.75rem;
          font-weight: 500;
          color: #3B82F6;
          margin-top: 0.25rem;
        }

        .form-checkbox-group {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .form-checkbox {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.875rem;
          color: #374151;
          cursor: pointer;
        }

        .form-checkbox input {
          width: 16px;
          height: 16px;
          accent-color: #3B82F6;
        }

        .environment-toggles {
          display: flex;
          gap: 0.5rem;
        }

        .environment-toggle {
          flex: 1;
          display: flex;
          align-items: center;
          justify-content: center;
          padding: 0.75rem;
          background: #fff;
          border: 2px solid #E5E7EB;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .environment-toggle:hover {
          border-color: #3B82F6;
        }

        .environment-toggle--active {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .environment-toggle input {
          display: none;
        }

        .environment-toggle span {
          font-size: 0.75rem;
          font-weight: 500;
          color: #374151;
        }

        .add-env-btn {
          padding: 0.25rem 0.5rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #3B82F6;
          background: transparent;
          border: 1px solid #3B82F6;
          border-radius: 4px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .add-env-btn:hover {
          background: #EFF6FF;
        }

        .env-variables-list {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .env-empty {
          font-size: 0.75rem;
          color: #9CA3AF;
          text-align: center;
          padding: 1rem;
        }

        .env-variable-row {
          display: flex;
          gap: 0.5rem;
          align-items: center;
        }

        .env-key {
          flex: 1;
          font-family: monospace;
        }

        .env-value {
          flex: 2;
        }

        .env-remove-btn {
          width: 32px;
          height: 32px;
          display: flex;
          align-items: center;
          justify-content: center;
          background: transparent;
          border: none;
          color: #9CA3AF;
          cursor: pointer;
          transition: color 0.15s ease;
          flex-shrink: 0;
        }

        .env-remove-btn:hover {
          color: #EF4444;
        }

        .env-remove-btn svg {
          width: 16px;
          height: 16px;
        }

        .form-cost-estimate {
          padding: 1rem;
          background: linear-gradient(135deg, #EFF6FF 0%, #DBEAFE 100%);
          border-radius: 10px;
          text-align: center;
        }

        .cost-estimate-label {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .cost-estimate-value {
          font-size: 1.5rem;
          font-weight: 700;
          color: #1E40AF;
          margin: 0.25rem 0;
        }

        .cost-estimate-note {
          font-size: 0.625rem;
          color: #9CA3AF;
        }

        .form-actions {
          display: flex;
          justify-content: flex-end;
          padding-top: 0.5rem;
        }

        .form-submit-btn {
          padding: 0.75rem 1.5rem;
          font-size: 0.875rem;
          font-weight: 600;
          color: #fff;
          background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
          border: none;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.2s ease;
        }

        .form-submit-btn:hover:not(:disabled) {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
        }

        .form-submit-btn:disabled {
          opacity: 0.7;
          cursor: not-allowed;
        }
      `}</style>
    </form>
  );
};

InfrastructureForm.displayName = 'InfrastructureForm';

export default InfrastructureForm;
