import React, { useEffect, useState } from 'react';

// ============================================================================
// Types
// ============================================================================

type StepStatus = 'pending' | 'running' | 'completed' | 'failed';

interface SetupStep {
  id: string;
  label: string;
  description: string;
  status: StepStatus;
  progress: number;
  duration?: string;
  logs: string[];
}

// ============================================================================
// Constants
// ============================================================================

const INITIAL_STEPS: SetupStep[] = [
  { id: 'deps', label: 'Install Dependencies', description: 'Installing Java, Node.js, and build tools', status: 'completed', progress: 100, duration: '45s', logs: ['pnpm install ✔', 'gradle wrapper ✔', 'Dependencies locked'] },
  { id: 'scaffold', label: 'Scaffold Project', description: 'Generating project structure from template', status: 'completed', progress: 100, duration: '12s', logs: ['Created src/main/java/', 'Created build.gradle.kts', 'Created Dockerfile'] },
  { id: 'git', label: 'Initialize Git', description: 'Setting up repository and branch protection', status: 'completed', progress: 100, duration: '3s', logs: ['git init ✔', 'Created .gitignore', 'Initial commit created'] },
  { id: 'ci', label: 'Configure CI/CD', description: 'Setting up GitHub Actions workflows', status: 'running', progress: 65, logs: ['Created ci.yml', 'Created release.yml', 'Configuring secrets...'] },
  { id: 'infra', label: 'Provision Infrastructure', description: 'Creating Kubernetes namespace and secrets', status: 'pending', progress: 0, logs: [] },
  { id: 'verify', label: 'Verification', description: 'Running build and smoke tests', status: 'pending', progress: 0, logs: [] },
];

const STATUS_STYLES: Record<StepStatus, { icon: string; color: string }> = {
  pending: { icon: '⏳', color: 'text-gray-400' },
  running: { icon: '🔄', color: 'text-blue-600' },
  completed: { icon: '✅', color: 'text-green-600' },
  failed: { icon: '❌', color: 'text-red-600' },
};

// ============================================================================
// Component
// ============================================================================

const SetupProgressPage: React.FC = () => {
  const [steps, setSteps] = useState(INITIAL_STEPS);
  const [expandedId, setExpandedId] = useState<string | null>('ci');

  // Simulate progress on the running step
  useEffect(() => {
    const interval = setInterval(() => {
      setSteps((prev) =>
        prev.map((s) => {
          if (s.status !== 'running') return s;
          const next = Math.min(s.progress + 5, 100);
          if (next >= 100) {
            return { ...s, progress: 100, status: 'completed', duration: '28s', logs: [...s.logs, 'CI/CD configured ✔'] };
          }
          return { ...s, progress: next };
        }),
      );
    }, 800);
    return () => clearInterval(interval);
  }, []);

  // Auto-advance to next step when current completes
  useEffect(() => {
    const allCompleted = steps.every((s) => s.status === 'completed' || s.status === 'failed');
    const hasRunning = steps.some((s) => s.status === 'running');
    if (!hasRunning && !allCompleted) {
      const timer = setTimeout(() => {
        setSteps((prev) => {
          const idx = prev.findIndex((s) => s.status === 'pending');
          if (idx < 0) return prev;
          const updated = [...prev];
          updated[idx] = { ...updated[idx], status: 'running', progress: 10, logs: ['Starting...'] };
          setExpandedId(updated[idx].id);
          return updated;
        });
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [steps]);

  const completedCount = steps.filter((s) => s.status === 'completed').length;
  const overallProgress = Math.round((completedCount / steps.length) * 100);

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="mx-auto max-w-3xl">
        <h1 className="mb-2 text-3xl font-bold text-gray-900">Setup Progress</h1>
        <p className="mb-6 text-gray-600">Your project is being configured.</p>

        {/* Overall Progress */}
        <div className="mb-8 rounded-lg border bg-white p-5 shadow-sm">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-medium text-gray-700">
              {completedCount}/{steps.length} steps completed
            </span>
            <span className="text-sm font-bold text-blue-600">{overallProgress}%</span>
          </div>
          <div className="h-3 overflow-hidden rounded-full bg-gray-200">
            <div
              className="h-full rounded-full bg-blue-600 transition-all duration-500"
              style={{ width: `${overallProgress}%` }}
            />
          </div>
        </div>

        {/* Steps */}
        <div className="space-y-3">
          {steps.map((step, idx) => {
            const style = STATUS_STYLES[step.status];
            const isExpanded = expandedId === step.id;
            return (
              <div key={step.id} className="rounded-lg border bg-white shadow-sm">
                <button
                  onClick={() => setExpandedId(isExpanded ? null : step.id)}
                  className="flex w-full items-center gap-4 p-4 text-left"
                >
                  {/* Timeline connector */}
                  <div className="flex flex-col items-center">
                    <span className="text-xl">{style.icon}</span>
                    {idx < steps.length - 1 && (
                      <div className={`mt-1 h-4 w-0.5 ${
                        step.status === 'completed' ? 'bg-green-300' : 'bg-gray-200'
                      }`} />
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className={`font-semibold ${style.color}`}>{step.label}</p>
                    <p className="text-sm text-gray-500">{step.description}</p>
                  </div>
                  {step.duration && (
                    <span className="text-xs text-gray-400">{step.duration}</span>
                  )}
                  {step.status === 'running' && (
                    <span className="text-xs font-medium text-blue-600">{step.progress}%</span>
                  )}
                </button>

                {/* Progress bar for running step */}
                {step.status === 'running' && (
                  <div className="mx-4 mb-2 h-1.5 overflow-hidden rounded-full bg-gray-200">
                    <div
                      className="h-full rounded-full bg-blue-500 transition-all duration-300"
                      style={{ width: `${step.progress}%` }}
                    />
                  </div>
                )}

                {/* Expandable log */}
                {isExpanded && step.logs.length > 0 && (
                  <div className="mx-4 mb-4 rounded bg-gray-900 p-3 font-mono text-xs text-green-400">
                    {step.logs.map((log, li) => (
                      <div key={li}>$ {log}</div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default SetupProgressPage;
