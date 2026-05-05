import React, { useState } from 'react';

// ============================================================================
// Types
// ============================================================================

interface EnvVar {
  key: string;
  value: string;
  source: 'manual' | 'vault' | 'inherited';
  sensitive: boolean;
}

type Environment = 'development' | 'staging' | 'production';

interface ToolCheck {
  name: string;
  version: string;
  status: 'installed' | 'missing' | 'outdated';
  required: boolean;
}

// ============================================================================
// Mock data
// ============================================================================

const TOOL_CHECKS: ToolCheck[] = [
  { name: 'Java', version: '21.0.2', status: 'installed', required: true },
  { name: 'Gradle', version: '8.10', status: 'installed', required: true },
  { name: 'Node.js', version: '22.12.0', status: 'installed', required: true },
  { name: 'Docker', version: '27.4.0', status: 'installed', required: true },
  { name: 'kubectl', version: '1.31.4', status: 'installed', required: false },
  { name: 'Helm', version: '—', status: 'missing', required: false },
  { name: 'pnpm', version: '9.15.0', status: 'installed', required: true },
  { name: 'GraalVM', version: '—', status: 'missing', required: false },
];

const INITIAL_VARS: EnvVar[] = [
  { key: 'DATABASE_URL', value: 'postgresql://localhost:5432/yappc', source: 'manual', sensitive: false },
  { key: 'REDIS_URL', value: 'redis://localhost:6379', source: 'manual', sensitive: false },
  { key: 'JWT_SECRET', value: '••••••••', source: 'vault', sensitive: true },
  { key: 'API_KEY', value: '••••••••', source: 'vault', sensitive: true },
  { key: 'LOG_LEVEL', value: 'INFO', source: 'inherited', sensitive: false },
];

// ============================================================================
// Component
// ============================================================================

const EnvironmentSetupPage: React.FC = () => {
  const [activeEnv, setActiveEnv] = useState<Environment>('development');
  const [envVars, setEnvVars] = useState<EnvVar[]>(INITIAL_VARS);
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');

  const addVar = (): void => {
    if (!newKey.trim()) return;
    setEnvVars((prev) => [
      ...prev,
      { key: newKey.toUpperCase(), value: newValue, source: 'manual', sensitive: false },
    ]);
    setNewKey('');
    setNewValue('');
  };

  const removeVar = (key: string): void => {
    setEnvVars((prev) => prev.filter((v) => v.key !== key));
  };

  const statusIcon = (s: ToolCheck['status']): string =>
    s === 'installed' ? '✅' : s === 'outdated' ? '⚠️' : '❌';

  return (
    <div className="min-h-screen bg-surface-muted p-6">
      <div className="mx-auto max-w-5xl">
        <h1 className="mb-2 text-3xl font-bold text-fg">Environment Setup</h1>
        <p className="mb-8 text-fg-muted">Configure toolchain and environment variables.</p>

        {/* Environment Selector */}
        <div className="mb-6 flex gap-2">
          {(['development', 'staging', 'production'] as Environment[]).map((env) => (
            <button
              key={env}
              onClick={() => setActiveEnv(env)}
              className={`rounded-full px-4 py-1.5 text-sm font-medium capitalize transition ${
                activeEnv === env
                  ? 'bg-primary text-white'
                  : 'bg-white text-fg hover:bg-surface-muted'
              }`}
            >
              {env}
            </button>
          ))}
        </div>

        {/* Toolchain */}
        <div className="mb-8 rounded-lg border bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-fg">Toolchain Checks</h2>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {TOOL_CHECKS.map((tool) => (
              <div key={tool.name} className="flex items-center gap-3 rounded-lg border p-3">
                <span className="text-lg">{statusIcon(tool.status)}</span>
                <div className="min-w-0">
                  <p className="font-medium text-fg">{tool.name}</p>
                  <p className="text-xs text-fg-muted">
                    {tool.version}{tool.required ? '' : ' (optional)'}
                  </p>
                </div>
              </div>
            ))}
          </div>
          <p className="mt-3 text-sm text-fg-muted">
            {TOOL_CHECKS.filter((t) => t.status === 'installed').length}/{TOOL_CHECKS.length} tools ready
          </p>
        </div>

        {/* Environment Variables */}
        <div className="rounded-lg border bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-fg">
              Environment Variables — <span className="capitalize">{activeEnv}</span>
            </h2>
            <span className="text-sm text-fg-muted">{envVars.length} variables</span>
          </div>

          <div className="mb-4 space-y-2">
            {envVars.map((v) => (
              <div key={v.key} className="flex items-center gap-3 rounded border px-3 py-2">
                <span className="w-40 truncate font-mono text-sm font-medium text-fg">{v.key}</span>
                <span className="flex-1 truncate font-mono text-sm text-fg-muted">
                  {v.sensitive ? '••••••••' : v.value}
                </span>
                <span className={`rounded px-2 py-0.5 text-xs ${
                  v.source === 'vault' ? 'bg-info-bg text-info-color'
                    : v.source === 'inherited' ? 'bg-success-bg text-success-color'
                    : 'bg-surface-muted text-fg-muted'
                }`}>{v.source}</span>
                <button onClick={() => removeVar(v.key)} className="text-fg-muted hover:text-destructive">✕</button>
              </div>
            ))}
          </div>

          {/* Add new */}
          <div className="flex gap-2">
            <input
              placeholder="KEY"
              value={newKey}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewKey(e.target.value)}
              className="w-40 rounded border px-3 py-1.5 font-mono text-sm focus:border-info-border focus:outline-none"
            />
            <input
              placeholder="value"
              value={newValue}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setNewValue(e.target.value)}
              className="flex-1 rounded border px-3 py-1.5 font-mono text-sm focus:border-info-border focus:outline-none"
            />
            <button
              onClick={addVar}
              className="rounded-md bg-primary px-4 py-1.5 text-sm font-medium text-white hover:bg-info-bg"
            >
              Add
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default EnvironmentSetupPage;
