import React, { useState } from 'react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';
import { useTranslation } from '@ghatana/i18n';

// ============================================================================
// Types
// ============================================================================

interface WizardStep {
  id: number;
  title: string;
  description: string;
}

interface ProjectForm {
  name: string;
  description: string;
  language: string;
  framework: string;
  gitProvider: string;
  ciPlatform: string;
  features: string[];
}

// ============================================================================
// Constants
// ============================================================================

const WIZARD_STEPS: WizardStep[] = [
  { id: 1, title: 'Project Info', description: 'Name and description' },
  { id: 2, title: 'Tech Stack', description: 'Language and framework' },
  { id: 3, title: 'DevOps', description: 'Git and CI/CD' },
  { id: 4, title: 'Features', description: 'Capabilities to include' },
  { id: 5, title: 'Review', description: 'Confirm and create' },
];

const LANGUAGES = ['Java 21', 'TypeScript', 'Python 3.12', 'Go 1.22', 'Rust'];
const FRAMEWORKS: Record<string, string[]> = {
  'Java 21': ['ActiveJ', 'Spring Boot', 'Quarkus', 'Micronaut'],
  TypeScript: ['React 19', 'Next.js', 'Fastify', 'NestJS'],
  'Python 3.12': ['FastAPI', 'Django', 'Flask'],
  'Go 1.22': ['Gin', 'Echo', 'Fiber'],
  Rust: ['Axum', 'Actix', 'Rocket'],
};
const GIT_PROVIDERS = ['GitHub', 'GitLab', 'Bitbucket'];
const CI_PLATFORMS = ['GitHub Actions', 'GitLab CI', 'Jenkins', 'CircleCI'];
const FEATURE_OPTIONS = [
  'REST API', 'gRPC', 'GraphQL', 'WebSocket',
  'Event Sourcing', 'CQRS', 'Observability', 'Docker',
  'Kubernetes', 'Database Migration', 'Authentication', 'Rate Limiting',
];

// ============================================================================
// Component
// ============================================================================

const SetupWizardPage: React.FC = () => {
  const { t } = useTranslation('common');
  const [step, setStep] = useState(1);
  const [form, setForm] = useState<ProjectForm>({
    name: '', description: '', language: 'Java 21', framework: 'ActiveJ',
    gitProvider: 'GitHub', ciPlatform: 'GitHub Actions', features: ['REST API', 'Docker', 'Observability'],
  });

  const update = <K extends keyof ProjectForm>(key: K, value: ProjectForm[K]): void => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const toggleFeature = (f: string): void => {
    setForm((prev) => ({
      ...prev,
      features: prev.features.includes(f)
        ? prev.features.filter((x) => x !== f)
        : [...prev.features, f],
    }));
  };

  const canNext = (): boolean => {
    if (step === 1) return form.name.trim().length > 0;
    return true;
  };

  return (
    <div className="min-h-screen bg-surface-muted p-6">
      <div className="mx-auto max-w-3xl">
        <h1 className="mb-2 text-3xl font-bold text-fg">Setup Wizard</h1>
        <p className="mb-8 text-fg-muted">Create a new YAPPC project step by step.</p>

        {/* Step Indicator */}
        <div className="mb-8 flex items-center justify-between">
          {WIZARD_STEPS.map((ws) => (
            <div key={ws.id} className="flex flex-col items-center">
              <div className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-bold ${
                ws.id < step ? 'bg-success-bg0 text-white'
                  : ws.id === step ? 'bg-primary text-white'
                  : 'bg-muted text-fg-muted'
              }`}>{ws.id < step ? '✓' : ws.id}</div>
              <p className="mt-1 text-xs text-fg-muted">{ws.title}</p>
            </div>
          ))}
        </div>

        {/* Step Content */}
        <div className="rounded-lg border bg-white p-6 shadow-sm">
          {step === 1 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Project Information</h2>
              <div>
                <label className="mb-1 block text-sm font-medium text-fg">Project Name</label>
                <Input value={form.name} onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('name', e.target.value)}
                  placeholder={t('setupWizard.projectNamePlaceholder')} className="w-full rounded-lg border px-4 py-2 focus:border-info-border focus:outline-none" />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-fg">Description</label>
                <Textarea value={form.description} onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => update('description', e.target.value)}
                  rows={3} placeholder={t('setupWizard.projectDescriptionPlaceholder')} className="w-full rounded-lg border px-4 py-2 focus:border-info-border focus:outline-none" />
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Technology Stack</h2>
              <div>
                <label className="mb-2 block text-sm font-medium text-fg">Language</label>
                <div className="flex flex-wrap gap-2">
                  {LANGUAGES.map((l) => (
                    <Button key={l} onClick={() => { update('language', l); update('framework', FRAMEWORKS[l]?.[0] ?? ''); }}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.language === l ? 'bg-primary text-white' : 'bg-surface-muted text-fg hover:bg-muted'
                      }`}>{l}</Button>
                  ))}
                </div>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-fg">Framework</label>
                <div className="flex flex-wrap gap-2">
                  {(FRAMEWORKS[form.language] ?? []).map((f) => (
                    <Button key={f} onClick={() => update('framework', f)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.framework === f ? 'bg-primary text-white' : 'bg-surface-muted text-fg hover:bg-muted'
                      }`}>{f}</Button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">DevOps Configuration</h2>
              <div>
                <label className="mb-2 block text-sm font-medium text-fg">Git Provider</label>
                <div className="flex gap-2">
                  {GIT_PROVIDERS.map((g) => (
                    <Button key={g} onClick={() => update('gitProvider', g)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.gitProvider === g ? 'bg-primary text-white' : 'bg-surface-muted text-fg'
                      }`}>{g}</Button>
                  ))}
                </div>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-fg">CI Platform</label>
                <div className="flex flex-wrap gap-2">
                  {CI_PLATFORMS.map((c) => (
                    <Button key={c} onClick={() => update('ciPlatform', c)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.ciPlatform === c ? 'bg-primary text-white' : 'bg-surface-muted text-fg'
                      }`}>{c}</Button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Project Features</h2>
              <p className="text-sm text-fg-muted">Select capabilities to include in your project.</p>
              <div className="grid grid-cols-3 gap-2">
                {FEATURE_OPTIONS.map((f) => (
                  <Button key={f} onClick={() => toggleFeature(f)}
                    className={`rounded-lg border px-3 py-2 text-sm font-medium transition ${
                      form.features.includes(f)
                        ? 'border-info-border bg-info-bg text-info-color'
                        : 'text-fg-muted hover:bg-surface-muted'
                    }`}>{form.features.includes(f) ? '✓ ' : ''}{f}</Button>
                ))}
              </div>
            </div>
          )}

          {step === 5 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Review & Create</h2>
              <div className="rounded-lg bg-surface-muted p-4">
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between"><dt className="text-fg-muted">Name</dt><dd className="font-medium">{form.name || '—'}</dd></div>
                  <div className="flex justify-between"><dt className="text-fg-muted">Stack</dt><dd className="font-medium">{form.language} / {form.framework}</dd></div>
                  <div className="flex justify-between"><dt className="text-fg-muted">Git</dt><dd className="font-medium">{form.gitProvider}</dd></div>
                  <div className="flex justify-between"><dt className="text-fg-muted">CI</dt><dd className="font-medium">{form.ciPlatform}</dd></div>
                  <div className="flex justify-between"><dt className="text-fg-muted">Features</dt><dd className="font-medium">{form.features.join(', ')}</dd></div>
                </dl>
              </div>
            </div>
          )}
        </div>

        {/* Navigation */}
        <div className="mt-6 flex justify-between">
          <Button onClick={() => setStep((s) => Math.max(1, s - 1))} disabled={step === 1}
            className="rounded-md border px-4 py-2 text-sm font-medium text-fg hover:bg-surface-muted disabled:opacity-30">
            ← Back
          </Button>
          {step < 5 ? (
            <Button onClick={() => setStep((s) => Math.min(5, s + 1))} disabled={!canNext()}
              className="rounded-md bg-primary px-6 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50">
              Next →
            </Button>
          ) : (
            <Button className="rounded-md bg-success-color px-6 py-2 text-sm font-medium text-white hover:opacity-90">
              🚀 Create Project
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

export default SetupWizardPage;
