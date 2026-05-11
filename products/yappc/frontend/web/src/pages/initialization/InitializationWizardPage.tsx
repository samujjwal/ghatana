/**
 * InitializationWizardPage
 *
 * @description Multi-step configuration wizard for project initialization.
 */

import React, { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { useTranslation } from '@ghatana/i18n';
import {
  ConfigurationWizard,
  CostEstimator,
  InfrastructureForm,
  ProviderSelector,
  type CloudProvider,
  type CostEstimates,
  type InfrastructureValues,
  type Provider,
  type StepValidation,
  type WizardStepDefinition,
} from 'yappc-initialization-ui';

interface RepositoryConfig {
  provider: 'github' | 'gitlab' | 'bitbucket';
  name: string;
  visibility: 'public' | 'private';
  description: string;
}

interface HostingConfig {
  frontendProvider: CloudProvider;
  backendProvider: CloudProvider;
}

interface TeamConfig {
  inviteMembers: string[];
  accessControl: 'open' | 'invite-only' | 'private';
}

type RadioControlProps = Omit<
  React.InputHTMLAttributes<HTMLInputElement>,
  'type'
>;

function RadioControl(props: RadioControlProps): React.ReactElement {
  return React.createElement('input', {
    ...props,
    type: 'radio',
  });
}

const repositoryProviders: Provider[] = [
  {
    id: 'github',
    name: 'GitHub',
    description: 'The default repository host for modern product teams.',
    features: ['Actions', 'Pull requests', 'Code review'],
    recommended: true,
  },
  {
    id: 'gitlab',
    name: 'GitLab',
    description: 'Integrated source control and CI/CD.',
    features: ['Pipelines', 'Merge requests', 'Container registry'],
  },
  {
    id: 'bitbucket',
    name: 'Bitbucket',
    description: 'Source control with strong Atlassian integration.',
    features: ['Pipelines', 'Jira integration', 'Code insights'],
  },
];

const hostingProviders: Provider[] = [
  {
    id: 'vercel',
    name: 'Vercel',
    description: 'Great default for frontend hosting.',
    features: ['Preview deployments', 'Edge delivery'],
    recommended: true,
  },
  {
    id: 'render',
    name: 'Render',
    description: 'Simple managed hosting for apps and APIs.',
    features: ['Background workers', 'Managed services'],
  },
  {
    id: 'fly',
    name: 'Fly.io',
    description: 'Global app hosting with flexible runtime support.',
    features: ['Global edge', 'Machines API'],
  },
];

function toValidationErrors(errors: Record<string, string>): StepValidation {
  return {
    valid: Object.keys(errors).length === 0,
    errors: Object.entries(errors).map(([field, message]) => ({ field, message })),
  };
}

export const InitializationWizardPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation('common');
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [repository, setRepository] = useState<RepositoryConfig>({
    provider: 'github',
    name: '',
    visibility: 'private',
    description: '',
  });
  const [hosting, setHosting] = useState<HostingConfig>({
    frontendProvider: 'vercel',
    backendProvider: 'render',
  });
  const [infrastructure, setInfrastructure] = useState<InfrastructureValues>({
    database: {
      provider: 'aws',
      plan: 'starter',
      region: 'us-east-1',
    },
    cache: {
      provider: 'render',
      plan: 'starter',
    },
    storage: {
      provider: 'aws',
      plan: 'standard',
    },
  });
  const [team, setTeam] = useState<TeamConfig>({
    inviteMembers: [],
    accessControl: 'invite-only',
  });
  const [inviteEmail, setInviteEmail] = useState('');

  const costEstimates = useMemo<CostEstimates>(
    () => ({
      monthly: 45,
      annual: 540,
      currency: 'USD',
      breakdown: [
        { service: 'Database', cost: 15, unit: '/mo' },
        { service: 'Application hosting', cost: 20, unit: '/mo' },
        { service: 'Storage', cost: 5, unit: '/mo' },
        { service: 'Monitoring', cost: 5, unit: '/mo' },
      ],
    }),
    [],
  );

  const steps = useMemo<WizardStepDefinition[]>(
    () => [
      {
        id: 'repository',
        label: 'Repository',
        description: 'Choose where your source code lives.',
        validate: () =>
          toValidationErrors(
            !repository.name
              ? { name: 'Repository name is required.' }
              : !/^[a-z0-9-]+$/.test(repository.name)
                ? { name: 'Repository name can only include lowercase letters, numbers, and hyphens.' }
                : {},
          ),
      },
      {
        id: 'hosting',
        label: 'Hosting',
        description: 'Pick default frontend and backend platforms.',
      },
      {
        id: 'infrastructure',
        label: 'Infrastructure',
        description: 'Configure the initial service footprint.',
        validate: () =>
          toValidationErrors(
            !infrastructure.database.region
              ? { databaseRegion: 'Database region is required.' }
              : {},
          ),
      },
      {
        id: 'team',
        label: 'Team',
        description: 'Decide who should have access on day one.',
      },
    ],
    [infrastructure.database.region, repository.name],
  );

  const currentStep = steps[currentStepIndex];

  const renderStepContent = (): React.ReactNode => {
    switch (currentStep.id) {
      case 'repository':
        return (
          <div className="step-content">
            <ProviderSelector
              category="repository"
              providers={repositoryProviders}
              selectedProvider={repository.provider}
              onSelect={(id) =>
                setRepository((prev) => ({
                  ...prev,
                  provider: id as RepositoryConfig['provider'],
                }))
              }
              variant="cards"
            />
            <div className="form-section">
              <label>
                Repository Name
                <Input
                  type="text"
                  className="form-input"
                  value={repository.name}
                  onChange={(event) =>
                    setRepository((prev) => ({ ...prev, name: event.target.value }))
                  }
                  placeholder={t('initWizard.repoNamePlaceholder')}
                />
              </label>
              <label>
                Description
                <Textarea
                  className="form-textarea"
                  value={repository.description}
                  onChange={(event) =>
                    setRepository((prev) => ({ ...prev, description: event.target.value }))
                  }
                  rows={3}
                />
              </label>
              <div className="radio-row">
                <label>
                  <RadioControl
                    checked={repository.visibility === 'private'}
                    onChange={() =>
                      setRepository((prev) => ({ ...prev, visibility: 'private' }))
                    }
                  />
                  Private
                </label>
                <label>
                  <RadioControl
                    checked={repository.visibility === 'public'}
                    onChange={() =>
                      setRepository((prev) => ({ ...prev, visibility: 'public' }))
                    }
                  />
                  Public
                </label>
              </div>
            </div>
          </div>
        );
      case 'hosting':
        return (
          <div className="step-content">
            <ProviderSelector
              category="frontend-hosting"
              title="Frontend Provider"
              providers={hostingProviders}
              selectedProvider={hosting.frontendProvider}
              onSelect={(id) =>
                setHosting((prev) => ({ ...prev, frontendProvider: id }))
              }
              variant="cards"
            />
            <ProviderSelector
              category="backend-hosting"
              title="Backend Provider"
              providers={hostingProviders}
              selectedProvider={hosting.backendProvider}
              onSelect={(id) =>
                setHosting((prev) => ({ ...prev, backendProvider: id }))
              }
              variant="cards"
            />
          </div>
        );
      case 'infrastructure':
        return (
          <div className="step-content">
            <InfrastructureForm values={infrastructure} onChange={setInfrastructure} />
          </div>
        );
      case 'team':
        return (
          <div className="step-content">
            <div className="form-section">
              <label>
                Invite Team Members
                <div className="invite-row">
                  <Input
                    type="email"
                    className="form-input"
                    placeholder={t('initWizard.inviteMemberPlaceholder')}
                    value={inviteEmail}
                    onChange={(event) => setInviteEmail(event.target.value)}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="btn btn-secondary"
                    onClick={() => {
                      if (!inviteEmail || team.inviteMembers.includes(inviteEmail)) {
                        return;
                      }
                      setTeam((prev) => ({
                        ...prev,
                        inviteMembers: [...prev.inviteMembers, inviteEmail],
                      }));
                      setInviteEmail('');
                    }}
                  >
                    Add
                  </Button>
                </div>
              </label>
              {team.inviteMembers.length > 0 && (
                <ul className="invite-list">
                  {team.inviteMembers.map((email) => (
                    <li key={email}>
                      <span>{email}</span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() =>
                          setTeam((prev) => ({
                            ...prev,
                            inviteMembers: prev.inviteMembers.filter((item) => item !== email),
                          }))
                        }
                      >
                        Remove
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="radio-row">
                <label>
                  <RadioControl
                    checked={team.accessControl === 'invite-only'}
                    onChange={() =>
                      setTeam((prev) => ({ ...prev, accessControl: 'invite-only' }))
                    }
                  />
                  Invite only
                </label>
                <label>
                  <RadioControl
                    checked={team.accessControl === 'open'}
                    onChange={() => setTeam((prev) => ({ ...prev, accessControl: 'open' }))}
                  />
                  Open
                </label>
                <label>
                  <RadioControl
                    checked={team.accessControl === 'private'}
                    onChange={() =>
                      setTeam((prev) => ({ ...prev, accessControl: 'private' }))
                    }
                  />
                  Private
                </label>
              </div>
            </div>
          </div>
        );
      default:
        return null;
    }
  };

  const handleNext = useCallback(() => {
    const validation = currentStep.validate?.({});
    if (validation && !validation.valid) {
      return;
    }
    setCurrentStepIndex((prev) => Math.min(prev + 1, steps.length - 1));
  }, [currentStep, steps.length]);

  const handleBack = useCallback(() => {
    setCurrentStepIndex((prev) => Math.max(prev - 1, 0));
  }, []);

  const handleComplete = useCallback(() => {
    setIsSubmitting(true);
    window.setTimeout(() => {
      navigate(`/projects/${projectId}/initialize/progress`);
    }, 250);
  }, [navigate, projectId]);

  return (
    <div className="initialization-wizard-page">
      <div className="wizard-container">
        <ConfigurationWizard
          steps={steps}
          currentStepIndex={currentStepIndex}
          onNext={handleNext}
          onBack={handleBack}
          onComplete={handleComplete}
          isSubmitting={isSubmitting}
        >
          {renderStepContent()}
        </ConfigurationWizard>
      </div>

      <aside className="cost-sidebar">
        <CostEstimator
          infrastructure={infrastructure}
          estimates={costEstimates}
          isLoading={false}
        />
      </aside>

      <style>{`
        .initialization-wizard-page {
          display: flex;
          gap: 2rem;
          min-height: 100vh;
          padding: 2rem;
          background: #f3f4f6;
        }

        .wizard-container {
          flex: 1;
          max-width: 800px;
        }

        .cost-sidebar {
          width: 320px;
          flex-shrink: 0;
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
          background: #f9fafb;
          border-radius: 8px;
        }

        .form-input,
        .form-textarea {
          width: 100%;
          margin-top: 0.375rem;
          padding: 0.5rem 0.75rem;
          border: 1px solid #d1d5db;
          border-radius: 6px;
        }

        .form-textarea {
          resize: vertical;
        }

        .radio-row {
          display: flex;
          gap: 1rem;
          flex-wrap: wrap;
        }

        .invite-row {
          display: flex;
          gap: 0.75rem;
          margin-top: 0.375rem;
        }

        .invite-list {
          margin: 0;
          padding: 0;
          list-style: none;
        }

        .invite-list li {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 0.5rem 0.75rem;
          background: #fff;
          border-radius: 6px;
        }

        .btn {
          border: none;
          border-radius: 8px;
          cursor: pointer;
          font-size: 0.875rem;
          font-weight: 600;
          padding: 0.75rem 1rem;
        }

        .btn-secondary {
          background: #e5e7eb;
          color: #111827;
        }

        @media (max-width: 1024px) {
          .initialization-wizard-page {
            flex-direction: column;
          }

          .wizard-container,
          .cost-sidebar {
            max-width: 100%;
            width: 100%;
          }
        }
      `}</style>
    </div>
  );
};

export default InitializationWizardPage;
