/**
 * InitializationProgressPage
 *
 * @description Real-time progress tracking page for initialization process.
 * Shows step-by-step progress, live logs, and created resources.
 *
 * @route /projects/:projectId/initialize/progress
 * @doc.phase 2
 * @doc.type page
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  LiveProgressViewer,
  ResourcesList,
  StepProgress,
  ProgressStep,
  LogEntry,
  Resource,
  WizardStep,
} from '@ghatana/yappc-ui';

// ============================================================================
// Types
// ============================================================================

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

// ============================================================================
// Simulated Data & Logic (Replace with actual API calls in production)
// ============================================================================

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

const generateLogEntry = (
  stepId: string,
  message: string,
  level: LogEntry['level'] = 'info'
): LogEntry => ({
  id: `log-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
  timestamp: new Date(),
  level,
  message,
  stepId,
});

// ============================================================================
// Main Page Component
// ============================================================================

export const InitializationProgressPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const initializationId = searchParams.get('id') || 'init-1';

  // State
  const [initState, setInitState] = useState<InitializationState>({
    id: initializationId,
    status: 'pending',
    startedAt: new Date(),
    currentStepIndex: 0,
    steps: INITIALIZATION_STEPS.map((s) => ({ ...s })),
    logs: [],
    resources: [],
  });

  const [showLogs, setShowLogs] = useState(true);
  const simulationRef = useRef<NodeJS.Timeout | null>(null);

  // Simulate initialization progress
  useEffect(() => {
    if (initState.status !== 'pending' && initState.status !== 'running') {
      return;
    }

    // Start the simulation
    setInitState((prev) => ({
      ...prev,
      status: 'running',
      logs: [
        ...prev.logs,
        generateLogEntry('system', 'Starting project initialization...', 'info'),
      ],
    }));

    const simulateStep = (stepIndex: number) => {
      if (stepIndex >= INITIALIZATION_STEPS.length) {
        // All steps completed
        setInitState((prev) => ({
          ...prev,
          status: 'completed',
          completedAt: new Date(),
          logs: [
            ...prev.logs,
            generateLogEntry('system', 'Initialization completed successfully!', 'success'),
          ],
        }));
        return;
      }

      const step = INITIALIZATION_STEPS[stepIndex];

      // Mark step as in-progress
      setInitState((prev) => ({
        ...prev,
        currentStepIndex: stepIndex,
        steps: prev.steps.map((s, i) =>
          i === stepIndex ? { ...s, status: 'in-progress', startedAt: new Date() } : s
        ),
        logs: [
          ...prev.logs,
          generateLogEntry(step.id, `Starting: ${step.name}...`, 'info'),
        ],
      }));

      // Simulate progress updates
      let progress = 0;
      const progressInterval = setInterval(() => {
        progress += Math.random() * 20 + 10;
        if (progress >= 100) {
          progress = 100;
          clearInterval(progressInterval);
        }

        setInitState((prev) => ({
          ...prev,
          steps: prev.steps.map((s, i) =>
            i === stepIndex ? { ...s, progress: Math.min(progress, 100) } : s
          ),
        }));
      }, 500);

      // Complete step after random duration
      const duration = 2000 + Math.random() * 3000;
      simulationRef.current = setTimeout(() => {
        clearInterval(progressInterval);

        // Add resource for this step
        const newResource = createResourceForStep(step.id, stepIndex);

        setInitState((prev) => ({
          ...prev,
          steps: prev.steps.map((s, i) =>
            i === stepIndex
              ? {
                  ...s,
                  status: 'completed',
                  progress: 100,
                  duration,
                  completedAt: new Date(),
                }
              : s
          ),
          logs: [
            ...prev.logs,
            generateLogEntry(step.id, `Completed: ${step.name}`, 'success'),
          ],
          resources: newResource ? [...prev.resources, newResource] : prev.resources,
        }));

        // Start next step
        simulateStep(stepIndex + 1);
      }, duration);
    };

    // Start simulation
    simulationRef.current = setTimeout(() => {
      simulateStep(0);
    }, 1000);

    return () => {
      if (simulationRef.current) {
        clearTimeout(simulationRef.current);
      }
    };
  }, []);

  // Create resource based on step
  const createResourceForStep = (stepId: string, index: number): Resource | null => {
    const resources: Record<string, Partial<Resource>> = {
      repository: {
        id: 'res-repo-1',
        name: 'bakery-app',
        type: 'repository',
        provider: 'github',
        status: 'running',
        url: 'https://github.com/user/bakery-app',
        region: 'global',
      },
      'frontend-deploy': {
        id: 'res-frontend-1',
        name: 'bakery-app-frontend',
        type: 'compute',
        provider: 'vercel',
        status: 'running',
        url: 'https://bakery-app.vercel.app',
        region: 'iad1',
      },
      'backend-deploy': {
        id: 'res-backend-1',
        name: 'bakery-app-backend',
        type: 'compute',
        provider: 'railway',
        status: 'running',
        region: 'us-west',
      },
      database: {
        id: 'res-db-1',
        name: 'bakery-app-db',
        type: 'database',
        provider: 'supabase',
        status: 'running',
        region: 'us-east-1',
        details: {
          engine: 'PostgreSQL 16',
          storage: '1 GB',
        },
      },
      cicd: {
        id: 'res-cicd-1',
        name: 'bakery-app-workflows',
        type: 'cicd',
        provider: 'github',
        status: 'running',
      },
      monitoring: {
        id: 'res-monitoring-1',
        name: 'bakery-app-monitoring',
        type: 'monitoring',
        provider: 'sentry',
        status: 'running',
        url: 'https://sentry.io/organizations/user/projects/bakery-app',
      },
    };

    const resourceData = resources[stepId];
    if (!resourceData) return null;

    return {
      ...resourceData,
      createdAt: new Date(),
      monthlyCost: Math.random() * 20,
    } as Resource;
  };

  // Handle cancellation
  const handleCancel = useCallback(() => {
    if (simulationRef.current) {
      clearTimeout(simulationRef.current);
    }

    setInitState((prev) => ({
      ...prev,
      status: 'failed',
      steps: prev.steps.map((s) =>
        s.status === 'in-progress'
          ? { ...s, status: 'failed', error: 'Cancelled by user' }
          : s
      ),
      logs: [
        ...prev.logs,
        generateLogEntry('system', 'Initialization cancelled by user', 'warning'),
      ],
    }));
  }, []);

  // Handle retry
  const handleRetry = useCallback((stepId: string) => {
    navigate(`/projects/${projectId}/initialize/rollback?failedStep=${stepId}`);
  }, [projectId, navigate]);

  // Navigate to completion or rollback
  const handleContinue = useCallback(() => {
    if (initState.status === 'completed') {
      navigate(`/projects/${projectId}/initialize/complete`);
    } else if (initState.status === 'failed') {
      navigate(`/projects/${projectId}/initialize/rollback`);
    }
  }, [initState.status, projectId, navigate]);

  // Wizard steps for top progress indicator
  const wizardSteps: WizardStep[] = initState.steps.map((step) => ({
    id: step.id,
    label: step.name,
    status:
      step.status === 'completed'
        ? 'completed'
        : step.status === 'in-progress'
        ? 'current'
        : step.status === 'failed'
        ? 'error'
        : 'upcoming',
    description: step.description,
  }));

  return (
    <div className="initialization-progress-page">
      {/* Header */}
      <header className="progress-header">
        <div className="header-content">
          <h1 className="header-title">
            {initState.status === 'completed'
              ? '✅ Initialization Complete!'
              : initState.status === 'failed'
              ? '❌ Initialization Failed'
              : 'Initializing Your Project...'}
          </h1>
          <p className="header-subtitle">
            {initState.status === 'running'
              ? 'Please wait while we set up your development environment'
              : initState.status === 'completed'
              ? 'Your project is ready to use'
              : 'An error occurred during initialization'}
          </p>
        </div>

        {(initState.status === 'completed' || initState.status === 'failed') && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleContinue}
          >
            {initState.status === 'completed' ? 'Continue to Project' : 'View Rollback Options'}
          </button>
        )}
      </header>

      {/* Step Progress */}
      <div className="step-progress-container">
        <StepProgress
          steps={wizardSteps}
          variant="horizontal"
          showLabels
          showDuration
        />
      </div>

      {/* Main Content */}
      <div className="progress-content">
        {/* Live Progress Viewer */}
        <div className="progress-viewer-section">
          <LiveProgressViewer
            steps={initState.steps}
            logs={initState.logs}
            title="Initialization Progress"
            isRunning={initState.status === 'running'}
            startTime={initState.startedAt}
            onCancel={initState.status === 'running' ? handleCancel : undefined}
            onRetry={handleRetry}
            showLogs={showLogs}
            showTimeEstimates
          />
        </div>

        {/* Resources Panel */}
        <aside className="resources-sidebar">
          <div className="sidebar-header">
            <h3>Created Resources</h3>
            <span className="resource-count">
              {initState.resources.length} resources
            </span>
          </div>

          <ResourcesList
            resources={initState.resources}
            showFilters={false}
            showCost
            compact
          />

          {initState.resources.length === 0 && (
            <div className="resources-empty">
              <p>Resources will appear here as they are created</p>
            </div>
          )}
        </aside>
      </div>

      {/* Action Bar */}
      <div className="action-bar">
        <div className="action-bar-left">
          <label className="toggle-logs">
            <input
              type="checkbox"
              checked={showLogs}
              onChange={(e) => setShowLogs(e.target.checked)}
            />
            <span>Show Logs</span>
          </label>
        </div>

        <div className="action-bar-right">
          {initState.status === 'running' && (
            <button
              type="button"
              className="btn btn-danger"
              onClick={handleCancel}
            >
              Cancel Initialization
            </button>
          )}
        </div>
      </div>

      {/* CSS-in-JS Styles */}
      <style>{`
        .initialization-progress-page {
          display: flex;
          flex-direction: column;
          min-height: 100vh;
          padding: 2rem;
          background: #F3F4F6;
        }

        .progress-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: 2rem;
          margin-bottom: 1.5rem;
          padding: 1.5rem 2rem;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .header-content {
          flex: 1;
        }

        .header-title {
          margin: 0;
          font-size: 1.5rem;
          font-weight: 700;
          color: #111827;
        }

        .header-subtitle {
          margin: 0.25rem 0 0;
          font-size: 0.875rem;
          color: #6B7280;
        }

        .step-progress-container {
          margin-bottom: 1.5rem;
          padding: 1rem 2rem;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
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
          flex-shrink: 0;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
          padding: 1rem;
          height: fit-content;
        }

        .sidebar-header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-bottom: 1rem;
        }

        .sidebar-header h3 {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .resource-count {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .resources-empty {
          text-align: center;
          padding: 2rem 1rem;
        }

        .resources-empty p {
          margin: 0;
          font-size: 0.875rem;
          color: #9CA3AF;
        }

        .action-bar {
          display: flex;
          align-items: center;
          justify-content: space-between;
          margin-top: 1.5rem;
          padding: 1rem 1.5rem;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
        }

        .toggle-logs {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.875rem;
          color: #374151;
          cursor: pointer;
        }

        .toggle-logs input {
          width: 16px;
          height: 16px;
          accent-color: #3B82F6;
        }

        .btn {
          padding: 0.625rem 1.25rem;
          font-size: 0.875rem;
          font-weight: 500;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .btn-primary {
          color: #fff;
          background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
          border: none;
        }

        .btn-primary:hover {
          transform: translateY(-1px);
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
        }

        .btn-danger {
          color: #fff;
          background: #EF4444;
          border: none;
        }

        .btn-danger:hover {
          background: #DC2626;
        }

        @media (max-width: 1024px) {
          .progress-content {
            flex-direction: column;
          }

          .resources-sidebar {
            width: 100%;
          }
        }

        @media (max-width: 768px) {
          .progress-header {
            flex-direction: column;
            text-align: center;
          }
        }
      `}</style>
    </div>
  );
};

InitializationProgressPage.displayName = 'InitializationProgressPage';

export default InitializationProgressPage;
