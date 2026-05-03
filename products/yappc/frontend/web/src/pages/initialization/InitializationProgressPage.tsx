/**
 * InitializationProgressPage
 *
 * @description Real-time progress tracking page for initialization process.
 * Shows step-by-step progress, live logs, and created resources.
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import {
  LiveProgressViewer,
  ResourcesList,
  StepProgress,
  type LogEntry,
  type ProgressStep,
  type Resource,
  type WizardStep,
} from 'yappc-initialization-ui';

interface InitializationState {
  id: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  startedAt: Date;
  completedAt?: Date;
  currentStepIndex: number;
  steps: ProgressStep[];
  logs: LogEntry[];
  resources: Resource[];
}

const INITIALIZATION_STEPS: ProgressStep[] = [
  {
    id: 'repository',
    name: 'Create Repository',
    description: 'Setting up your code repository',
    status: 'pending',
  },
  {
    id: 'frontend-deploy',
    name: 'Deploy Frontend',
    description: 'Configuring frontend hosting',
    status: 'pending',
  },
  {
    id: 'backend-deploy',
    name: 'Deploy Backend',
    description: 'Configuring backend infrastructure',
    status: 'pending',
  },
  {
    id: 'database',
    name: 'Provision Database',
    description: 'Setting up database instance',
    status: 'pending',
  },
  {
    id: 'cicd',
    name: 'Configure CI/CD',
    description: 'Setting up automated pipelines',
    status: 'pending',
  },
  {
    id: 'monitoring',
    name: 'Setup Monitoring',
    description: 'Configuring error tracking and alerts',
    status: 'pending',
  },
];

const createLogEntry = (
  stepId: string,
  message: string,
  level: LogEntry['level'] = 'info',
): LogEntry => ({
  id: `log-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
  timestamp: new Date(),
  level,
  message,
  stepId,
});

function buildResource(stepId: string): Resource | null {
  const resources: Record<string, Resource> = {
    repository: {
      id: 'res-repo-1',
      name: 'bakery-app',
      type: 'repository',
      provider: 'github',
      status: 'active',
      url: 'https://github.com/user/bakery-app',
    },
    'frontend-deploy': {
      id: 'res-frontend-1',
      name: 'bakery-app-frontend',
      type: 'compute',
      provider: 'vercel',
      status: 'active',
      url: 'https://bakery-app.vercel.app',
      cost: 0,
      costUnit: '/mo',
    },
    'backend-deploy': {
      id: 'res-backend-1',
      name: 'bakery-app-backend',
      type: 'compute',
      provider: 'render',
      status: 'active',
      cost: 20,
      costUnit: '/mo',
    },
    database: {
      id: 'res-db-1',
      name: 'bakery-app-db',
      type: 'database',
      provider: 'aws',
      status: 'active',
      cost: 15,
      costUnit: '/mo',
    },
    cicd: {
      id: 'res-cicd-1',
      name: 'bakery-app-workflows',
      type: 'cicd',
      provider: 'github',
      status: 'active',
    },
    monitoring: {
      id: 'res-monitoring-1',
      name: 'bakery-app-monitoring',
      type: 'monitoring',
      provider: 'render',
      status: 'active',
      cost: 5,
      costUnit: '/mo',
    },
  };

  return resources[stepId] ?? null;
}

export const InitializationProgressPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const initializationId = searchParams.get('id') ?? 'init-1';

  const [initState, setInitState] = useState<InitializationState>({
    id: initializationId,
    status: 'pending',
    startedAt: new Date(),
    currentStepIndex: 0,
    steps: INITIALIZATION_STEPS.map((step) => ({ ...step })),
    logs: [],
    resources: [],
  });
  const [showLogs, setShowLogs] = useState(true);
  const activeTimeoutsRef = useRef<number[]>([]);

  useEffect(() => {
    if (initState.status !== 'pending') {
      return;
    }

    const runStep = (stepIndex: number): void => {
      if (stepIndex >= INITIALIZATION_STEPS.length) {
        setInitState((prev) => ({
          ...prev,
          status: 'completed',
          completedAt: new Date(),
          logs: [
            ...prev.logs,
            createLogEntry('system', 'Initialization completed successfully.', 'info'),
          ],
        }));
        return;
      }

      const step = INITIALIZATION_STEPS[stepIndex];
      setInitState((prev) => ({
        ...prev,
        status: 'running',
        currentStepIndex: stepIndex,
        steps: prev.steps.map((existing, index) =>
          index === stepIndex ? { ...existing, status: 'running' } : existing,
        ),
        logs: [
          ...prev.logs,
          createLogEntry(step.id, `Starting ${step.name}...`, 'info'),
        ],
      }));

      const timeoutId = window.setTimeout(() => {
        const resource = buildResource(step.id);
        setInitState((prev) => ({
          ...prev,
          steps: prev.steps.map((existing, index) =>
            index === stepIndex
              ? { ...existing, status: 'completed', duration: 2000 }
              : existing,
          ),
          logs: [
            ...prev.logs,
            createLogEntry(step.id, `Completed ${step.name}.`, 'info'),
          ],
          resources: resource ? [...prev.resources, resource] : prev.resources,
        }));
        runStep(stepIndex + 1);
      }, 1500 + stepIndex * 250);

      activeTimeoutsRef.current.push(timeoutId);
    };

    setInitState((prev) => ({
      ...prev,
      status: 'running',
      logs: [...prev.logs, createLogEntry('system', 'Starting project initialization...', 'info')],
    }));

    const startTimeout = window.setTimeout(() => runStep(0), 600);
    activeTimeoutsRef.current.push(startTimeout);

    return () => {
      activeTimeoutsRef.current.forEach((id) => window.clearTimeout(id));
      activeTimeoutsRef.current = [];
    };
  }, [initState.status]);

  const handleCancel = useCallback(() => {
    activeTimeoutsRef.current.forEach((id) => window.clearTimeout(id));
    activeTimeoutsRef.current = [];

    setInitState((prev) => ({
      ...prev,
      status: 'failed',
      steps: prev.steps.map((step) =>
        step.status === 'running'
          ? { ...step, status: 'failed', error: 'Cancelled by user' }
          : step,
      ),
      logs: [
        ...prev.logs,
        createLogEntry('system', 'Initialization cancelled by user.', 'warn'),
      ],
    }));
  }, []);

  const handleRetry = useCallback(() => {
    navigate(`/projects/${projectId}/initialize/rollback`);
  }, [navigate, projectId]);

  const handleContinue = useCallback(() => {
    if (initState.status === 'completed') {
      navigate(`/projects/${projectId}`);
      return;
    }

    if (initState.status === 'failed') {
      navigate(`/projects/${projectId}/initialize/rollback`);
    }
  }, [initState.status, navigate, projectId]);

  const wizardSteps = useMemo<WizardStep[]>(
    () =>
      initState.steps.map((step, index) => ({
        id: step.id,
        label: step.name,
        completed: step.status === 'completed',
        active:
          step.status === 'running' ||
          (step.status === 'pending' && index === initState.currentStepIndex),
      })),
    [initState.currentStepIndex, initState.steps],
  );

  return (
    <div className="initialization-progress-page">
      <header className="progress-header">
        <div className="header-content">
          <h1 className="header-title">
            {initState.status === 'completed'
              ? 'Initialization Complete'
              : initState.status === 'failed'
                ? 'Initialization Failed'
                : 'Initializing Your Project'}
          </h1>
          <p className="header-subtitle">
            {initState.status === 'running'
              ? 'Please wait while we provision your project foundations.'
              : initState.status === 'completed'
                ? 'Your project is ready to use.'
                : 'Initialization stopped before completion.'}
          </p>
        </div>

        {(initState.status === 'completed' || initState.status === 'failed') && (
          <button type="button" className="btn btn-primary" onClick={handleContinue}>
            {initState.status === 'completed' ? 'Continue to Project' : 'View Recovery Options'}
          </button>
        )}
      </header>

      <div className="step-progress-container">
        <StepProgress steps={wizardSteps} variant="horizontal" showLabels />
      </div>

      <div className="progress-content">
        <div className="progress-viewer-section">
          <LiveProgressViewer
            steps={initState.steps}
            logs={initState.logs}
            title="Initialization Progress"
            isRunning={initState.status === 'running'}
            startTime={initState.startedAt}
            onCancel={initState.status === 'running' ? handleCancel : undefined}
            onRetry={initState.status === 'failed' ? handleRetry : undefined}
            showLogs={showLogs}
            onToggleLogs={setShowLogs}
          />
        </div>

        <aside className="resources-sidebar">
          <div className="sidebar-header">
            <h3>Created Resources</h3>
            <span className="resource-count">{initState.resources.length} resources</span>
          </div>

          <ResourcesList resources={initState.resources} showFilters={false} showCost compact />

          {initState.resources.length === 0 && (
            <div className="resources-empty">
              <p>Resources will appear here as they are created.</p>
            </div>
          )}
        </aside>
      </div>

      <style>{`
        .initialization-progress-page {
          display: flex;
          flex-direction: column;
          min-height: 100vh;
          padding: 2rem;
          background: #f3f4f6;
        }

        .progress-header,
        .step-progress-container,
        .resources-sidebar {
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .progress-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 2rem;
          margin-bottom: 1.5rem;
          padding: 1.5rem 2rem;
        }

        .header-title {
          margin: 0;
          font-size: 1.5rem;
          font-weight: 700;
          color: #111827;
        }

        .header-subtitle {
          margin: 0.25rem 0 0;
          color: #6b7280;
        }

        .step-progress-container {
          margin-bottom: 1.5rem;
          padding: 1rem 2rem;
        }

        .progress-content {
          display: flex;
          gap: 1.5rem;
          flex: 1;
        }

        .progress-viewer-section {
          flex: 1;
        }

        .resources-sidebar {
          width: 320px;
          padding: 1rem;
          height: fit-content;
        }

        .sidebar-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1rem;
        }

        .btn {
          border: none;
          border-radius: 8px;
          cursor: pointer;
          font-size: 0.875rem;
          font-weight: 600;
          padding: 0.75rem 1.25rem;
        }

        .btn-primary {
          background: #2563eb;
          color: #fff;
        }

        @media (max-width: 1024px) {
          .progress-content {
            flex-direction: column;
          }

          .resources-sidebar {
            width: 100%;
          }
        }
      `}</style>
    </div>
  );
};

export default InitializationProgressPage;
