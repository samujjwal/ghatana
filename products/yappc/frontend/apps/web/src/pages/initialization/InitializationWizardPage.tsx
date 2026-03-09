/**
 * InitializationWizardPage
 *
 * @description Multi-step configuration wizard for project initialization.
 * Guides users through setting up repository, hosting, infrastructure,
 * CI/CD, monitoring, and team configuration.
 *
 * @route /projects/:projectId/initialize
 * @doc.phase 2
 * @doc.type page
 */

import React, { useState, useCallback, useMemo, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAtom, useSetAtom } from 'jotai';
import {
  ConfigurationWizard,
  StepProgress,
  InfrastructureForm,
  ProviderSelector,
  CostEstimator,
  EnvironmentTabs,
  WizardStepDefinition,
  StepValidation,
  InfrastructureValues,
  CloudProvider,
  Provider,
  CostEstimates,
  Environment,
} from '@ghatana/yappc-ui';

// ============================================================================
// Types
// ============================================================================

interface RepositoryConfig {
  provider: 'github' | 'gitlab' | 'bitbucket';
  name: string;
  visibility: 'public' | 'private';
  description: string;
  template?: string;
  initializeWithReadme: boolean;
  licenseType: string;
}

interface HostingConfig {
  frontend: {
    provider: CloudProvider;
    region: string;
    customDomain?: string;
  };
  backend: {
    provider: CloudProvider;
    region: string;
    scaling: string;
  };
}

interface CICDConfig {
  enabled: boolean;
  provider: 'github-actions' | 'gitlab-ci' | 'jenkins' | 'circleci';
  deployBranches: string[];
  runTests: boolean;
  runLinting: boolean;
  runTypeCheck: boolean;
}

interface MonitoringConfig {
  enabled: boolean;
  errorTracking: boolean;
  performanceMonitoring: boolean;
  logging: boolean;
  alerting: boolean;
  provider: 'sentry' | 'datadog' | 'newrelic' | 'grafana';
}

interface TeamConfig {
  inviteMembers: string[];
  accessControl: 'open' | 'invite-only' | 'private';
  defaultRole: 'viewer' | 'editor' | 'admin';
}

interface InitializationConfig {
  repository: RepositoryConfig;
  hosting: HostingConfig;
  infrastructure: InfrastructureValues;
  cicd: CICDConfig;
  monitoring: MonitoringConfig;
  team: TeamConfig;
}

// ============================================================================
// Form Step Components
// ============================================================================

const RepositoryStep: React.FC<{
  config: RepositoryConfig;
  onChange: (config: RepositoryConfig) => void;
}> = ({ config, onChange }) => {
  const providers: Provider[] = [
    {
      id: 'github',
      name: 'GitHub',
      description: 'The world\'s leading software development platform',
      features: ['Actions', 'Issues', 'Pull Requests', 'Code Review'],
      recommended: true,
    },
    {
      id: 'gitlab',
      name: 'GitLab',
      description: 'Complete DevOps platform with built-in CI/CD',
      features: ['CI/CD', 'Issues', 'Merge Requests', 'Container Registry'],
    },
    {
      id: 'bitbucket',
      name: 'Bitbucket',
      description: 'Git solution for professional teams',
      features: ['Pipelines', 'Code Insights', 'Jira Integration'],
    },
  ];

  return (
    <div className="step-content">
      <ProviderSelector
        category="repository"
        providers={providers}
        selectedProvider={config.provider}
        onSelect={(id) => onChange({ ...config, provider: id as RepositoryConfig['provider'] })}
        showPricing={false}
        variant="cards"
      />

      <div className="form-section">
        <div className="form-field">
          <label htmlFor="repo-name">Repository Name</label>
          <input
            id="repo-name"
            type="text"
            className="form-input"
            value={config.name}
            onChange={(e) => onChange({ ...config, name: e.target.value })}
            placeholder="my-awesome-project"
          />
        </div>

        <div className="form-field">
          <label htmlFor="repo-description">Description</label>
          <textarea
            id="repo-description"
            className="form-textarea"
            value={config.description}
            onChange={(e) => onChange({ ...config, description: e.target.value })}
            placeholder="A brief description of your project"
            rows={3}
          />
        </div>

        <div className="form-field">
          <label>Visibility</label>
          <div className="radio-group">
            <label className="radio-option">
              <input
                type="radio"
                checked={config.visibility === 'private'}
                onChange={() => onChange({ ...config, visibility: 'private' })}
              />
              <span>Private</span>
            </label>
            <label className="radio-option">
              <input
                type="radio"
                checked={config.visibility === 'public'}
                onChange={() => onChange({ ...config, visibility: 'public' })}
              />
              <span>Public</span>
            </label>
          </div>
        </div>

        <div className="form-field">
          <label className="checkbox-option">
            <input
              type="checkbox"
              checked={config.initializeWithReadme}
              onChange={(e) =>
                onChange({ ...config, initializeWithReadme: e.target.checked })
              }
            />
            <span>Initialize with README</span>
          </label>
        </div>
      </div>
    </div>
  );
};

const HostingStep: React.FC<{
  config: HostingConfig;
  onChange: (config: HostingConfig) => void;
}> = ({ config, onChange }) => {
  const frontendProviders: Provider[] = [
    {
      id: 'vercel',
      name: 'Vercel',
      description: 'The platform for frontend developers',
      features: ['Edge Functions', 'Preview Deployments', 'Analytics'],
      recommended: true,
    },
    {
      id: 'railway',
      name: 'Railway',
      description: 'Infrastructure, Subtracted',
      features: ['Auto-deploy', 'Metrics', 'Easy Scaling'],
    },
    {
      id: 'render',
      name: 'Render',
      description: 'Cloud for developers and teams',
      features: ['Free SSL', 'Auto-deploys', 'DDoS Protection'],
    },
  ];

  const backendProviders: Provider[] = [
    {
      id: 'railway',
      name: 'Railway',
      description: 'Deploy anything with ease',
      features: ['Containers', 'Databases', 'Redis'],
      recommended: true,
    },
    {
      id: 'fly',
      name: 'Fly.io',
      description: 'Run your full stack apps globally',
      features: ['Global Edge', 'Machines API', 'Persistent Volumes'],
    },
    {
      id: 'render',
      name: 'Render',
      description: 'Everything you need for production',
      features: ['Private Networks', 'Background Workers', 'Cron Jobs'],
    },
  ];

  return (
    <div className="step-content">
      <div className="hosting-section">
        <h4>Frontend Hosting</h4>
        <ProviderSelector
          category="hosting"
          title="Frontend Provider"
          providers={frontendProviders}
          selectedProvider={config.frontend.provider}
          onSelect={(id) =>
            onChange({
              ...config,
              frontend: { ...config.frontend, provider: id as CloudProvider },
            })
          }
          showPricing
          variant="cards"
        />
      </div>

      <div className="hosting-section">
        <h4>Backend Hosting</h4>
        <ProviderSelector
          category="hosting"
          title="Backend Provider"
          providers={backendProviders}
          selectedProvider={config.backend.provider}
          onSelect={(id) =>
            onChange({
              ...config,
              backend: { ...config.backend, provider: id as CloudProvider },
            })
          }
          showPricing
          variant="cards"
        />
      </div>
    </div>
  );
};

const CICDStep: React.FC<{
  config: CICDConfig;
  onChange: (config: CICDConfig) => void;
}> = ({ config, onChange }) => {
  const cicdProviders: Provider[] = [
    {
      id: 'github-actions',
      name: 'GitHub Actions',
      description: 'Automate your workflow from idea to production',
      features: ['Matrix builds', 'Secrets management', 'Artifacts'],
      recommended: true,
    },
    {
      id: 'gitlab-ci',
      name: 'GitLab CI',
      description: 'Built-in CI/CD with GitLab',
      features: ['Pipeline editor', 'Auto DevOps', 'Merge trains'],
    },
    {
      id: 'circleci',
      name: 'CircleCI',
      description: 'Fastest CI/CD platform',
      features: ['Parallelism', 'Resource classes', 'Insights'],
    },
  ];

  return (
    <div className="step-content">
      <div className="form-field">
        <label className="checkbox-option large">
          <input
            type="checkbox"
            checked={config.enabled}
            onChange={(e) => onChange({ ...config, enabled: e.target.checked })}
          />
          <div>
            <span className="checkbox-label">Enable CI/CD</span>
            <span className="checkbox-hint">
              Automatically build, test, and deploy your code
            </span>
          </div>
        </label>
      </div>

      {config.enabled && (
        <>
          <ProviderSelector
            category="cicd"
            providers={cicdProviders}
            selectedProvider={config.provider}
            onSelect={(id) =>
              onChange({ ...config, provider: id as CICDConfig['provider'] })
            }
            showPricing={false}
            variant="cards"
          />

          <div className="form-section">
            <h4>Pipeline Settings</h4>

            <div className="checkbox-group">
              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.runTests}
                  onChange={(e) =>
                    onChange({ ...config, runTests: e.target.checked })
                  }
                />
                <span>Run tests on every push</span>
              </label>

              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.runLinting}
                  onChange={(e) =>
                    onChange({ ...config, runLinting: e.target.checked })
                  }
                />
                <span>Run linting</span>
              </label>

              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.runTypeCheck}
                  onChange={(e) =>
                    onChange({ ...config, runTypeCheck: e.target.checked })
                  }
                />
                <span>Run type checking</span>
              </label>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

const MonitoringStep: React.FC<{
  config: MonitoringConfig;
  onChange: (config: MonitoringConfig) => void;
}> = ({ config, onChange }) => {
  const monitoringProviders: Provider[] = [
    {
      id: 'sentry',
      name: 'Sentry',
      description: 'Application monitoring and error tracking',
      features: ['Error tracking', 'Performance', 'Release tracking'],
      recommended: true,
    },
    {
      id: 'datadog',
      name: 'Datadog',
      description: 'Cloud-scale monitoring and security',
      features: ['APM', 'Infrastructure', 'Logs'],
    },
    {
      id: 'grafana',
      name: 'Grafana',
      description: 'Operational dashboards for your data',
      features: ['Dashboards', 'Alerting', 'Logs'],
    },
  ];

  return (
    <div className="step-content">
      <div className="form-field">
        <label className="checkbox-option large">
          <input
            type="checkbox"
            checked={config.enabled}
            onChange={(e) => onChange({ ...config, enabled: e.target.checked })}
          />
          <div>
            <span className="checkbox-label">Enable Monitoring</span>
            <span className="checkbox-hint">
              Track errors, performance, and application health
            </span>
          </div>
        </label>
      </div>

      {config.enabled && (
        <>
          <ProviderSelector
            category="monitoring"
            providers={monitoringProviders}
            selectedProvider={config.provider}
            onSelect={(id) =>
              onChange({ ...config, provider: id as MonitoringConfig['provider'] })
            }
            showPricing
            variant="cards"
          />

          <div className="form-section">
            <h4>Monitoring Features</h4>

            <div className="checkbox-group">
              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.errorTracking}
                  onChange={(e) =>
                    onChange({ ...config, errorTracking: e.target.checked })
                  }
                />
                <span>Error tracking</span>
              </label>

              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.performanceMonitoring}
                  onChange={(e) =>
                    onChange({
                      ...config,
                      performanceMonitoring: e.target.checked,
                    })
                  }
                />
                <span>Performance monitoring</span>
              </label>

              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.logging}
                  onChange={(e) =>
                    onChange({ ...config, logging: e.target.checked })
                  }
                />
                <span>Centralized logging</span>
              </label>

              <label className="checkbox-option">
                <input
                  type="checkbox"
                  checked={config.alerting}
                  onChange={(e) =>
                    onChange({ ...config, alerting: e.target.checked })
                  }
                />
                <span>Alerting & notifications</span>
              </label>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

const TeamStep: React.FC<{
  config: TeamConfig;
  onChange: (config: TeamConfig) => void;
}> = ({ config, onChange }) => {
  const [emailInput, setEmailInput] = useState('');

  const addMember = () => {
    if (emailInput && !config.inviteMembers.includes(emailInput)) {
      onChange({
        ...config,
        inviteMembers: [...config.inviteMembers, emailInput],
      });
      setEmailInput('');
    }
  };

  const removeMember = (email: string) => {
    onChange({
      ...config,
      inviteMembers: config.inviteMembers.filter((e) => e !== email),
    });
  };

  return (
    <div className="step-content">
      <div className="form-section">
        <h4>Invite Team Members</h4>

        <div className="invite-input-row">
          <input
            type="email"
            className="form-input"
            placeholder="colleague@company.com"
            value={emailInput}
            onChange={(e) => setEmailInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addMember()}
          />
          <button
            type="button"
            className="btn btn-secondary"
            onClick={addMember}
          >
            Add
          </button>
        </div>

        {config.inviteMembers.length > 0 && (
          <div className="invited-list">
            {config.inviteMembers.map((email) => (
              <div key={email} className="invited-member">
                <span>{email}</span>
                <button
                  type="button"
                  className="remove-btn"
                  onClick={() => removeMember(email)}
                  aria-label={`Remove ${email}`}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="form-section">
        <h4>Access Control</h4>

        <div className="radio-group vertical">
          <label className="radio-option">
            <input
              type="radio"
              checked={config.accessControl === 'invite-only'}
              onChange={() =>
                onChange({ ...config, accessControl: 'invite-only' })
              }
            />
            <div>
              <span className="radio-label">Invite Only</span>
              <span className="radio-hint">
                Only invited members can access
              </span>
            </div>
          </label>

          <label className="radio-option">
            <input
              type="radio"
              checked={config.accessControl === 'open'}
              onChange={() => onChange({ ...config, accessControl: 'open' })}
            />
            <div>
              <span className="radio-label">Open to Organization</span>
              <span className="radio-hint">
                Anyone in your organization can access
              </span>
            </div>
          </label>

          <label className="radio-option">
            <input
              type="radio"
              checked={config.accessControl === 'private'}
              onChange={() =>
                onChange({ ...config, accessControl: 'private' })
              }
            />
            <div>
              <span className="radio-label">Private</span>
              <span className="radio-hint">Only you can access</span>
            </div>
          </label>
        </div>
      </div>

      <div className="form-section">
        <h4>Default Member Role</h4>

        <div className="radio-group">
          <label className="radio-option">
            <input
              type="radio"
              checked={config.defaultRole === 'viewer'}
              onChange={() => onChange({ ...config, defaultRole: 'viewer' })}
            />
            <span>Viewer</span>
          </label>

          <label className="radio-option">
            <input
              type="radio"
              checked={config.defaultRole === 'editor'}
              onChange={() => onChange({ ...config, defaultRole: 'editor' })}
            />
            <span>Editor</span>
          </label>

          <label className="radio-option">
            <input
              type="radio"
              checked={config.defaultRole === 'admin'}
              onChange={() => onChange({ ...config, defaultRole: 'admin' })}
            />
            <span>Admin</span>
          </label>
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// Main Page Component
// ============================================================================

export const InitializationWizardPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();

  // Configuration state
  const [config, setConfig] = useState<InitializationConfig>({
    repository: {
      provider: 'github',
      name: '',
      visibility: 'private',
      description: '',
      initializeWithReadme: true,
      licenseType: 'MIT',
    },
    hosting: {
      frontend: { provider: 'vercel', region: 'iad1' },
      backend: { provider: 'railway', region: 'us-west1', scaling: 'basic' },
    },
    infrastructure: {
      projectName: '',
      provider: 'vercel',
      region: 'iad1',
      database: 'postgres',
      enableCDN: true,
      scaling: 'basic',
      autoScale: false,
      environments: ['development', 'production'],
      envVariables: {},
      enableMonitoring: true,
      enableLogging: true,
    },
    cicd: {
      enabled: true,
      provider: 'github-actions',
      deployBranches: ['main'],
      runTests: true,
      runLinting: true,
      runTypeCheck: true,
    },
    monitoring: {
      enabled: true,
      errorTracking: true,
      performanceMonitoring: true,
      logging: true,
      alerting: false,
      provider: 'sentry',
    },
    team: {
      inviteMembers: [],
      accessControl: 'invite-only',
      defaultRole: 'editor',
    },
  });

  // Cost estimates (would be fetched from API in production)
  const costEstimates: CostEstimates = useMemo(
    () => ({
      total: 45,
      currency: 'USD',
      period: 'monthly',
      breakdown: [
        { category: 'Compute', amount: 20, percentage: 44 },
        { category: 'Database', amount: 15, percentage: 33 },
        { category: 'Storage', amount: 5, percentage: 11 },
        { category: 'Monitoring', amount: 5, percentage: 11 },
      ],
      byProvider: [
        { provider: 'Vercel', amount: 0, percentage: 0 },
        { provider: 'Railway', amount: 20, percentage: 44 },
        { provider: 'Supabase', amount: 15, percentage: 33 },
        { provider: 'Sentry', amount: 5, percentage: 11 },
      ],
      byEnvironment: [
        { environment: 'Development', amount: 10, percentage: 22 },
        { environment: 'Production', amount: 35, percentage: 78 },
      ],
      optimizations: [
        {
          id: 'use-free-tier',
          title: 'Use Free Tier Database',
          description: 'Supabase free tier includes 500MB storage',
          potentialSavings: 15,
          impact: 'low',
        },
      ],
    }),
    []
  );

  // Wizard step definitions
  const steps: WizardStepDefinition[] = useMemo(
    () => [
      {
        id: 'repository',
        title: 'Repository',
        description: 'Set up your code repository',
        content: (
          <RepositoryStep
            config={config.repository}
            onChange={(repo) => setConfig((prev) => ({ ...prev, repository: repo }))}
          />
        ),
        validate: async (): Promise<StepValidation> => {
          const errors: Record<string, string> = {};
          if (!config.repository.name) {
            errors.name = 'Repository name is required';
          } else if (!/^[a-z0-9-]+$/.test(config.repository.name)) {
            errors.name =
              'Repository name can only contain lowercase letters, numbers, and hyphens';
          }
          return { valid: Object.keys(errors).length === 0, errors };
        },
      },
      {
        id: 'hosting',
        title: 'Hosting',
        description: 'Configure deployment targets',
        content: (
          <HostingStep
            config={config.hosting}
            onChange={(hosting) =>
              setConfig((prev) => ({ ...prev, hosting }))
            }
          />
        ),
      },
      {
        id: 'infrastructure',
        title: 'Infrastructure',
        description: 'Set up database, cache, and storage',
        content: (
          <InfrastructureForm
            initialValues={config.infrastructure}
            onSubmit={(values) =>
              setConfig((prev) => ({ ...prev, infrastructure: values }))
            }
            onChange={(values) =>
              setConfig((prev) => ({ ...prev, infrastructure: values }))
            }
            showCostEstimate={false}
          />
        ),
        validate: async (): Promise<StepValidation> => {
          const errors: Record<string, string> = {};
          if (!config.infrastructure.projectName) {
            errors.projectName = 'Project name is required';
          }
          return { valid: Object.keys(errors).length === 0, errors };
        },
      },
      {
        id: 'cicd',
        title: 'CI/CD',
        description: 'Configure continuous integration and deployment',
        content: (
          <CICDStep
            config={config.cicd}
            onChange={(cicd) => setConfig((prev) => ({ ...prev, cicd }))}
          />
        ),
        optional: true,
      },
      {
        id: 'monitoring',
        title: 'Monitoring',
        description: 'Set up error tracking and performance monitoring',
        content: (
          <MonitoringStep
            config={config.monitoring}
            onChange={(monitoring) =>
              setConfig((prev) => ({ ...prev, monitoring }))
            }
          />
        ),
        optional: true,
      },
      {
        id: 'team',
        title: 'Team',
        description: 'Invite team members and configure access',
        content: (
          <TeamStep
            config={config.team}
            onChange={(team) => setConfig((prev) => ({ ...prev, team }))}
          />
        ),
        optional: true,
      },
    ],
    [config]
  );

  // Handle wizard completion
  const handleComplete = useCallback(
    async (data: Record<string, Record<string, unknown>>) => {
      // Start initialization process
      // In production, this would call the API to start initialization
      console.log('Starting initialization with config:', config);

      // Navigate to progress page
      navigate(`/projects/${projectId}/initialize/progress`);
    },
    [config, projectId, navigate]
  );

  // Handle cancel
  const handleCancel = useCallback(() => {
    navigate(`/projects/${projectId}`);
  }, [projectId, navigate]);

  return (
    <div className="initialization-wizard-page">
      <div className="wizard-container">
        <ConfigurationWizard
          steps={steps}
          title="Initialize Your Project"
          subtitle="Configure your development environment and infrastructure"
          onComplete={handleComplete}
          onCancel={handleCancel}
          showStepNumbers
          allowStepNavigation
          persistData
          storageKey={`yappc-init-${projectId}`}
          completeButtonText="Start Initialization"
        />
      </div>

      <aside className="cost-sidebar">
        <CostEstimator
          estimates={costEstimates}
          showProviders
          showEnvironments
          variant="detailed"
        />
      </aside>

      <style>{`
        .initialization-wizard-page {
          display: flex;
          gap: 2rem;
          min-height: 100vh;
          padding: 2rem;
          background: #F3F4F6;
        }

        .wizard-container {
          flex: 1;
          max-width: 800px;
        }

        .cost-sidebar {
          width: 320px;
          flex-shrink: 0;
        }

        @media (max-width: 1024px) {
          .initialization-wizard-page {
            flex-direction: column;
          }

          .wizard-container {
            max-width: 100%;
          }

          .cost-sidebar {
            width: 100%;
          }
        }

        .step-content {
          display: flex;
          flex-direction: column;
          gap: 1.5rem;
        }

        .form-section {
          display: flex;
          flex-direction: column;
          gap: 1rem;
          padding: 1rem;
          background: #F9FAFB;
          border-radius: 8px;
        }

        .form-section h4 {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #374151;
        }

        .form-field {
          display: flex;
          flex-direction: column;
          gap: 0.375rem;
        }

        .form-field label {
          font-size: 0.75rem;
          font-weight: 500;
          color: #374151;
        }

        .form-input,
        .form-textarea {
          padding: 0.5rem 0.75rem;
          font-size: 0.875rem;
          border: 1px solid #D1D5DB;
          border-radius: 6px;
          transition: border-color 0.15s ease;
        }

        .form-input:focus,
        .form-textarea:focus {
          outline: none;
          border-color: #3B82F6;
          box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
        }

        .form-textarea {
          resize: vertical;
          min-height: 80px;
        }

        .radio-group {
          display: flex;
          gap: 1rem;
        }

        .radio-group.vertical {
          flex-direction: column;
          gap: 0.75rem;
        }

        .radio-option {
          display: flex;
          align-items: flex-start;
          gap: 0.5rem;
          cursor: pointer;
        }

        .radio-option input {
          margin-top: 0.25rem;
        }

        .radio-label {
          font-size: 0.875rem;
          font-weight: 500;
          color: #111827;
        }

        .radio-hint {
          display: block;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .checkbox-option {
          display: flex;
          align-items: flex-start;
          gap: 0.5rem;
          cursor: pointer;
          font-size: 0.875rem;
          color: #374151;
        }

        .checkbox-option.large {
          padding: 1rem;
          background: #fff;
          border: 2px solid #E5E7EB;
          border-radius: 8px;
        }

        .checkbox-option.large:has(input:checked) {
          border-color: #3B82F6;
          background: #EFF6FF;
        }

        .checkbox-label {
          display: block;
          font-weight: 500;
          color: #111827;
        }

        .checkbox-hint {
          display: block;
          font-size: 0.75rem;
          color: #6B7280;
        }

        .checkbox-group {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .hosting-section {
          margin-bottom: 1.5rem;
        }

        .hosting-section h4 {
          margin: 0 0 0.75rem;
          font-size: 0.875rem;
          font-weight: 600;
          color: #374151;
        }

        .invite-input-row {
          display: flex;
          gap: 0.5rem;
        }

        .invite-input-row .form-input {
          flex: 1;
        }

        .btn {
          padding: 0.5rem 1rem;
          font-size: 0.875rem;
          font-weight: 500;
          border-radius: 6px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .btn-secondary {
          color: #374151;
          background: #fff;
          border: 1px solid #D1D5DB;
        }

        .btn-secondary:hover {
          background: #F9FAFB;
        }

        .invited-list {
          display: flex;
          flex-wrap: wrap;
          gap: 0.5rem;
          margin-top: 0.5rem;
        }

        .invited-member {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.25rem 0.5rem;
          background: #EFF6FF;
          border-radius: 9999px;
          font-size: 0.75rem;
          color: #1E40AF;
        }

        .remove-btn {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 16px;
          height: 16px;
          font-size: 1rem;
          color: #6B7280;
          background: transparent;
          border: none;
          cursor: pointer;
        }

        .remove-btn:hover {
          color: #EF4444;
        }
      `}</style>
    </div>
  );
};

InitializationWizardPage.displayName = 'InitializationWizardPage';

export default InitializationWizardPage;
