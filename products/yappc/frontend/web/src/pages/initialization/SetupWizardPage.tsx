import React, { useState } from 'react';

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
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="mx-auto max-w-3xl">
        <h1 className="mb-2 text-3xl font-bold text-gray-900">Setup Wizard</h1>
        <p className="mb-8 text-gray-600">Create a new YAPPC project step by step.</p>

        {/* Step Indicator */}
        <div className="mb-8 flex items-center justify-between">
          {WIZARD_STEPS.map((ws) => (
            <div key={ws.id} className="flex flex-col items-center">
              <div className={`flex h-10 w-10 items-center justify-center rounded-full text-sm font-bold ${
                ws.id < step ? 'bg-green-500 text-white'
                  : ws.id === step ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-500'
              }`}>{ws.id < step ? '✓' : ws.id}</div>
              <p className="mt-1 text-xs text-gray-500">{ws.title}</p>
            </div>
          ))}
        </div>

        {/* Step Content */}
        <div className="rounded-lg border bg-white p-6 shadow-sm">
          {step === 1 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Project Information</h2>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Project Name</label>
                <input value={form.name} onChange={(e: React.ChangeEvent<HTMLInputElement>) => update('name', e.target.value)}
                  placeholder="my-yappc-service" className="w-full rounded-lg border px-4 py-2 focus:border-blue-500 focus:outline-none" />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-gray-700">Description</label>
                <textarea value={form.description} onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => update('description', e.target.value)}
                  rows={3} placeholder="A brief description..." className="w-full rounded-lg border px-4 py-2 focus:border-blue-500 focus:outline-none" />
              </div>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Technology Stack</h2>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">Language</label>
                <div className="flex flex-wrap gap-2">
                  {LANGUAGES.map((l) => (
                    <button key={l} onClick={() => { update('language', l); update('framework', FRAMEWORKS[l]?.[0] ?? ''); }}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.language === l ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                      }`}>{l}</button>
                  ))}
                </div>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">Framework</label>
                <div className="flex flex-wrap gap-2">
                  {(FRAMEWORKS[form.language] ?? []).map((f) => (
                    <button key={f} onClick={() => update('framework', f)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.framework === f ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                      }`}>{f}</button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">DevOps Configuration</h2>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">Git Provider</label>
                <div className="flex gap-2">
                  {GIT_PROVIDERS.map((g) => (
                    <button key={g} onClick={() => update('gitProvider', g)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.gitProvider === g ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                      }`}>{g}</button>
                  ))}
                </div>
              </div>
              <div>
                <label className="mb-2 block text-sm font-medium text-gray-700">CI Platform</label>
                <div className="flex flex-wrap gap-2">
                  {CI_PLATFORMS.map((c) => (
                    <button key={c} onClick={() => update('ciPlatform', c)}
                      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
                        form.ciPlatform === c ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-700'
                      }`}>{c}</button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {step === 4 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Project Features</h2>
              <p className="text-sm text-gray-500">Select capabilities to include in your project.</p>
              <div className="grid grid-cols-3 gap-2">
                {FEATURE_OPTIONS.map((f) => (
                  <button key={f} onClick={() => toggleFeature(f)}
                    className={`rounded-lg border px-3 py-2 text-sm font-medium transition ${
                      form.features.includes(f)
                        ? 'border-blue-400 bg-blue-50 text-blue-700'
                        : 'text-gray-600 hover:bg-gray-50'
                    }`}>{form.features.includes(f) ? '✓ ' : ''}{f}</button>
                ))}
              </div>
            </div>
          )}

          {step === 5 && (
            <div className="space-y-4">
              <h2 className="text-lg font-semibold">Review & Create</h2>
              <div className="rounded-lg bg-gray-50 p-4">
                <dl className="space-y-2 text-sm">
                  <div className="flex justify-between"><dt className="text-gray-500">Name</dt><dd className="font-medium">{form.name || '—'}</dd></div>
                  <div className="flex justify-between"><dt className="text-gray-500">Stack</dt><dd className="font-medium">{form.language} / {form.framework}</dd></div>
                  <div className="flex justify-between"><dt className="text-gray-500">Git</dt><dd className="font-medium">{form.gitProvider}</dd></div>
                  <div className="flex justify-between"><dt className="text-gray-500">CI</dt><dd className="font-medium">{form.ciPlatform}</dd></div>
                  <div className="flex justify-between"><dt className="text-gray-500">Features</dt><dd className="font-medium">{form.features.join(', ')}</dd></div>
                </dl>
              </div>
            </div>
          )}
        </div>

        {/* Navigation */}
        <div className="mt-6 flex justify-between">
          <button onClick={() => setStep((s) => Math.max(1, s - 1))} disabled={step === 1}
            className="rounded-md border px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-30">
            ← Back
          </button>
          {step < 5 ? (
            <button onClick={() => setStep((s) => Math.min(5, s + 1))} disabled={!canNext()}
              className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
              Next →
            </button>
          ) : (
            <button className="rounded-md bg-green-600 px-6 py-2 text-sm font-medium text-white hover:bg-green-700">
              🚀 Create Project
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default SetupWizardPage;
