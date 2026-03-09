/**
 * InitializationCompletePage
 *
 * @description Success page shown after initialization completes successfully.
 * Shows a summary of what was created and provides next steps.
 *
 * @route /projects/:projectId/initialize/complete
 * @doc.phase 2
 * @doc.type page
 */

import React, { useState, useMemo, useCallback } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { ResourcesList, Resource } from '@ghatana/yappc-ui';

// ============================================================================
// Types
// ============================================================================

interface NextStep {
  id: string;
  icon: string;
  title: string;
  description: string;
  action: string;
  href?: string;
  primary?: boolean;
}

interface QuickLink {
  id: string;
  label: string;
  url: string;
  provider: string;
  icon: string;
}

interface InitializationStats {
  totalDuration: number;
  resourcesCreated: number;
  estimatedMonthlyCost: number;
  environmentsConfigured: number;
  pipelinesCreated: number;
}

// ============================================================================
// Static Data
// ============================================================================

const CREATED_RESOURCES: Resource[] = [
  {
    id: 'res-repo-1',
    name: 'bakery-app',
    type: 'repository',
    provider: 'github',
    status: 'running',
    url: 'https://github.com/user/bakery-app',
    region: 'global',
    createdAt: new Date(Date.now() - 600000),
    monthlyCost: 0,
  },
  {
    id: 'res-frontend-1',
    name: 'bakery-app-frontend',
    type: 'compute',
    provider: 'vercel',
    status: 'running',
    url: 'https://bakery-app.vercel.app',
    region: 'iad1',
    createdAt: new Date(Date.now() - 540000),
    monthlyCost: 0,
  },
  {
    id: 'res-backend-1',
    name: 'bakery-app-backend',
    type: 'compute',
    provider: 'railway',
    status: 'running',
    region: 'us-west',
    createdAt: new Date(Date.now() - 480000),
    monthlyCost: 5,
  },
  {
    id: 'res-db-1',
    name: 'bakery-app-db',
    type: 'database',
    provider: 'supabase',
    status: 'running',
    url: 'https://supabase.com/dashboard/project/bakery-app',
    region: 'us-east-1',
    createdAt: new Date(Date.now() - 420000),
    monthlyCost: 0,
  },
  {
    id: 'res-storage-1',
    name: 'bakery-app-storage',
    type: 'storage',
    provider: 'cloudflare',
    status: 'running',
    region: 'global',
    createdAt: new Date(Date.now() - 360000),
    monthlyCost: 5,
  },
  {
    id: 'res-cicd-1',
    name: 'bakery-app-pipeline',
    type: 'ci-cd',
    provider: 'github',
    status: 'running',
    url: 'https://github.com/user/bakery-app/actions',
    region: 'global',
    createdAt: new Date(Date.now() - 300000),
    monthlyCost: 0,
  },
  {
    id: 'res-monitoring-1',
    name: 'bakery-app-monitoring',
    type: 'monitoring',
    provider: 'grafana',
    status: 'running',
    url: 'https://grafana.com/dashboards/bakery-app',
    region: 'us-central',
    createdAt: new Date(Date.now() - 240000),
    monthlyCost: 0,
  },
];

const QUICK_LINKS: QuickLink[] = [
  {
    id: 'link-live',
    label: 'Live App',
    url: 'https://bakery-app.vercel.app',
    provider: 'vercel',
    icon: '🌐',
  },
  {
    id: 'link-repo',
    label: 'Repository',
    url: 'https://github.com/user/bakery-app',
    provider: 'github',
    icon: '📁',
  },
  {
    id: 'link-db',
    label: 'Database',
    url: 'https://supabase.com/dashboard/project/bakery-app',
    provider: 'supabase',
    icon: '🗄️',
  },
  {
    id: 'link-cicd',
    label: 'CI/CD',
    url: 'https://github.com/user/bakery-app/actions',
    provider: 'github',
    icon: '🔄',
  },
  {
    id: 'link-monitoring',
    label: 'Monitoring',
    url: 'https://grafana.com/dashboards/bakery-app',
    provider: 'grafana',
    icon: '📊',
  },
];

const NEXT_STEPS: NextStep[] = [
  {
    id: 'step-customize',
    icon: '🎨',
    title: 'Customize Your App',
    description: 'Open the repository and start building your features',
    action: 'Open in Editor',
    href: 'vscode://file/path/to/bakery-app',
    primary: true,
  },
  {
    id: 'step-invite',
    icon: '👥',
    title: 'Invite Team Members',
    description: 'Add collaborators to your repository and deployment platforms',
    action: 'Invite Team',
    href: '/projects/:projectId/settings/team',
  },
  {
    id: 'step-domain',
    icon: '🔗',
    title: 'Connect Custom Domain',
    description: 'Add your own domain name to make your app look professional',
    action: 'Configure Domain',
    href: '/projects/:projectId/settings/domains',
  },
  {
    id: 'step-secrets',
    icon: '🔐',
    title: 'Configure Secrets',
    description: 'Add API keys, credentials, and environment variables',
    action: 'Manage Secrets',
    href: '/projects/:projectId/settings/secrets',
  },
  {
    id: 'step-docs',
    icon: '📚',
    title: 'Read Documentation',
    description: 'Learn about best practices and advanced features',
    action: 'View Docs',
    href: 'https://docs.yappc.dev',
  },
];

const STATS: InitializationStats = {
  totalDuration: 342,
  resourcesCreated: CREATED_RESOURCES.length,
  estimatedMonthlyCost: CREATED_RESOURCES.reduce((sum, r) => sum + r.monthlyCost, 0),
  environmentsConfigured: 3,
  pipelinesCreated: 2,
};

// ============================================================================
// Sub Components
// ============================================================================

interface SuccessHeaderProps {
  projectName: string;
  stats: InitializationStats;
}

const SuccessHeader: React.FC<SuccessHeaderProps> = ({ projectName, stats }) => {
  const formatDuration = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}m ${secs}s`;
  };

  return (
    <header className="success-header">
      <div className="success-confetti">
        <span className="confetti-piece">🎉</span>
        <span className="confetti-piece">✨</span>
        <span className="confetti-piece">🚀</span>
      </div>

      <div className="success-icon">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
          <polyline points="22,4 12,14.01 9,11.01" />
        </svg>
      </div>

      <h1 className="success-title">Your Project is Ready!</h1>
      <p className="success-subtitle">
        <strong>{projectName}</strong> has been successfully initialized and deployed
      </p>

      <div className="stats-row">
        <div className="stat-item">
          <span className="stat-value">{formatDuration(stats.totalDuration)}</span>
          <span className="stat-label">Setup Time</span>
        </div>
        <div className="stat-item">
          <span className="stat-value">{stats.resourcesCreated}</span>
          <span className="stat-label">Resources Created</span>
        </div>
        <div className="stat-item">
          <span className="stat-value">{stats.environmentsConfigured}</span>
          <span className="stat-label">Environments</span>
        </div>
        <div className="stat-item">
          <span className="stat-value">
            {stats.estimatedMonthlyCost === 0
              ? 'Free'
              : `$${stats.estimatedMonthlyCost}/mo`}
          </span>
          <span className="stat-label">Est. Cost</span>
        </div>
      </div>

      <style>{`
        .success-header {
          text-align: center;
          padding: 3rem 2rem;
          background: linear-gradient(135deg, #ECFDF5 0%, #D1FAE5 100%);
          border: 1px solid #10B981;
          border-radius: 16px;
          margin-bottom: 2rem;
          position: relative;
          overflow: hidden;
        }

        .success-confetti {
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          display: flex;
          justify-content: space-around;
          padding-top: 1rem;
        }

        .confetti-piece {
          font-size: 1.5rem;
          animation: confetti-fall 3s ease-in-out infinite;
        }

        .confetti-piece:nth-child(2) {
          animation-delay: 0.5s;
        }

        .confetti-piece:nth-child(3) {
          animation-delay: 1s;
        }

        @keyframes confetti-fall {
          0%, 100% { transform: translateY(0) rotate(0deg); opacity: 1; }
          50% { transform: translateY(10px) rotate(180deg); opacity: 0.7; }
        }

        .success-icon {
          width: 72px;
          height: 72px;
          margin: 0 auto 1rem;
          padding: 1rem;
          background: #10B981;
          border-radius: 50%;
          color: #fff;
        }

        .success-icon svg {
          width: 100%;
          height: 100%;
        }

        .success-title {
          margin: 0;
          font-size: 1.75rem;
          font-weight: 700;
          color: #065F46;
        }

        .success-subtitle {
          margin: 0.5rem 0 2rem;
          font-size: 1rem;
          color: #047857;
        }

        .stats-row {
          display: flex;
          justify-content: center;
          gap: 3rem;
          flex-wrap: wrap;
        }

        .stat-item {
          display: flex;
          flex-direction: column;
          align-items: center;
        }

        .stat-value {
          font-size: 1.25rem;
          font-weight: 700;
          color: #065F46;
        }

        .stat-label {
          font-size: 0.75rem;
          color: #047857;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }
      `}</style>
    </header>
  );
};

interface QuickLinksBarProps {
  links: QuickLink[];
}

const QuickLinksBar: React.FC<QuickLinksBarProps> = ({ links }) => {
  return (
    <div className="quick-links-bar">
      <h3 className="quick-links-title">Quick Links</h3>
      <div className="quick-links-list">
        {links.map((link) => (
          <a
            key={link.id}
            href={link.url}
            target="_blank"
            rel="noopener noreferrer"
            className="quick-link"
          >
            <span className="quick-link-icon">{link.icon}</span>
            <span className="quick-link-label">{link.label}</span>
            <span className="quick-link-provider">{link.provider}</span>
          </a>
        ))}
      </div>

      <style>{`
        .quick-links-bar {
          padding: 1rem 1.5rem;
          background: #fff;
          border-radius: 12px;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
          margin-bottom: 2rem;
        }

        .quick-links-title {
          margin: 0 0 0.75rem;
          font-size: 0.75rem;
          font-weight: 600;
          color: #6B7280;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .quick-links-list {
          display: flex;
          gap: 0.5rem;
          flex-wrap: wrap;
        }

        .quick-link {
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.5rem 1rem;
          background: #F3F4F6;
          border-radius: 8px;
          text-decoration: none;
          color: #374151;
          font-size: 0.875rem;
          transition: all 0.15s ease;
        }

        .quick-link:hover {
          background: #E5E7EB;
          transform: translateY(-1px);
        }

        .quick-link-icon {
          font-size: 1rem;
        }

        .quick-link-label {
          font-weight: 500;
        }

        .quick-link-provider {
          font-size: 0.625rem;
          color: #9CA3AF;
          text-transform: uppercase;
        }
      `}</style>
    </div>
  );
};

interface NextStepsListProps {
  steps: NextStep[];
  projectId: string;
}

const NextStepsList: React.FC<NextStepsListProps> = ({ steps, projectId }) => {
  const navigate = useNavigate();

  const handleStepClick = (step: NextStep) => {
    if (step.href) {
      if (step.href.startsWith('http') || step.href.startsWith('vscode://')) {
        window.open(step.href, '_blank');
      } else {
        const resolvedHref = step.href.replace(':projectId', projectId);
        navigate(resolvedHref);
      }
    }
  };

  return (
    <section className="next-steps-section">
      <h2 className="section-title">Next Steps</h2>

      <div className="next-steps-grid">
        {steps.map((step) => (
          <div
            key={step.id}
            className={`next-step-card ${step.primary ? 'next-step-card--primary' : ''}`}
          >
            <span className="step-icon">{step.icon}</span>
            <div className="step-content">
              <h3 className="step-title">{step.title}</h3>
              <p className="step-description">{step.description}</p>
            </div>
            <button
              type="button"
              className={`step-action ${step.primary ? 'step-action--primary' : ''}`}
              onClick={() => handleStepClick(step)}
            >
              {step.action}
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <polyline points="9,18 15,12 9,6" />
              </svg>
            </button>
          </div>
        ))}
      </div>

      <style>{`
        .next-steps-section {
          margin-bottom: 2rem;
        }

        .section-title {
          margin: 0 0 1rem;
          font-size: 1rem;
          font-weight: 600;
          color: #111827;
        }

        .next-steps-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
          gap: 1rem;
        }

        .next-step-card {
          display: flex;
          align-items: flex-start;
          gap: 1rem;
          padding: 1.25rem;
          background: #fff;
          border: 1px solid #E5E7EB;
          border-radius: 12px;
          transition: all 0.2s ease;
        }

        .next-step-card:hover {
          border-color: #3B82F6;
          box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
        }

        .next-step-card--primary {
          border-color: #3B82F6;
          background: linear-gradient(135deg, #EFF6FF 0%, #DBEAFE 100%);
        }

        .step-icon {
          font-size: 1.5rem;
          flex-shrink: 0;
        }

        .step-content {
          flex: 1;
          min-width: 0;
        }

        .step-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .step-description {
          margin: 0.25rem 0 0;
          font-size: 0.75rem;
          color: #6B7280;
          line-height: 1.4;
        }

        .step-action {
          display: inline-flex;
          align-items: center;
          gap: 0.25rem;
          padding: 0.5rem 0.75rem;
          font-size: 0.75rem;
          font-weight: 500;
          color: #3B82F6;
          background: transparent;
          border: 1px solid #3B82F6;
          border-radius: 6px;
          cursor: pointer;
          flex-shrink: 0;
          transition: all 0.15s ease;
        }

        .step-action svg {
          width: 12px;
          height: 12px;
        }

        .step-action:hover {
          background: #3B82F6;
          color: #fff;
        }

        .step-action--primary {
          background: #3B82F6;
          color: #fff;
        }

        .step-action--primary:hover {
          background: #2563EB;
        }
      `}</style>
    </section>
  );
};

interface CredentialsCardProps {
  projectId: string;
}

const CredentialsCard: React.FC<CredentialsCardProps> = ({ projectId }) => {
  const [showSecrets, setShowSecrets] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  const credentials = useMemo(() => [
    { key: 'DATABASE_URL', value: 'postgresql://user:****@db.supabase.co:5432/bakery' },
    { key: 'NEXT_PUBLIC_API_URL', value: 'https://bakery-app-backend.railway.app' },
    { key: 'JWT_SECRET', value: 'sk_live_*****************************' },
    { key: 'STORAGE_BUCKET', value: 'bakery-app-storage' },
  ], []);

  const handleCopy = useCallback((key: string, value: string) => {
    navigator.clipboard.writeText(value);
    setCopied(key);
    setTimeout(() => setCopied(null), 2000);
  }, []);

  return (
    <div className="credentials-card">
      <div className="credentials-header">
        <h3 className="credentials-title">Generated Credentials</h3>
        <button
          type="button"
          className="toggle-visibility"
          onClick={() => setShowSecrets(!showSecrets)}
        >
          {showSecrets ? 'Hide' : 'Show'}
        </button>
      </div>

      <div className="credentials-list">
        {credentials.map((cred) => (
          <div key={cred.key} className="credential-item">
            <span className="credential-key">{cred.key}</span>
            <code className="credential-value">
              {showSecrets ? cred.value : '••••••••••••••••••••'}
            </code>
            <button
              type="button"
              className="copy-btn"
              onClick={() => handleCopy(cred.key, cred.value)}
              title="Copy to clipboard"
            >
              {copied === cred.key ? '✓' : '📋'}
            </button>
          </div>
        ))}
      </div>

      <p className="credentials-note">
        These credentials are also stored in your project's environment configuration.
      </p>

      <style>{`
        .credentials-card {
          padding: 1.25rem;
          background: #1F2937;
          border-radius: 12px;
          margin-bottom: 2rem;
        }

        .credentials-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 1rem;
        }

        .credentials-title {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #F9FAFB;
        }

        .toggle-visibility {
          padding: 0.375rem 0.75rem;
          font-size: 0.75rem;
          color: #9CA3AF;
          background: transparent;
          border: 1px solid #4B5563;
          border-radius: 4px;
          cursor: pointer;
        }

        .toggle-visibility:hover {
          color: #F9FAFB;
          border-color: #6B7280;
        }

        .credentials-list {
          display: flex;
          flex-direction: column;
          gap: 0.5rem;
        }

        .credential-item {
          display: flex;
          align-items: center;
          gap: 0.75rem;
          padding: 0.5rem 0.75rem;
          background: #374151;
          border-radius: 6px;
        }

        .credential-key {
          font-size: 0.75rem;
          font-weight: 500;
          color: #10B981;
          min-width: 160px;
          font-family: 'Monaco', 'Menlo', monospace;
        }

        .credential-value {
          flex: 1;
          font-size: 0.75rem;
          color: #D1D5DB;
          font-family: 'Monaco', 'Menlo', monospace;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .copy-btn {
          padding: 0.25rem;
          background: transparent;
          border: none;
          cursor: pointer;
          font-size: 0.875rem;
          opacity: 0.7;
        }

        .copy-btn:hover {
          opacity: 1;
        }

        .credentials-note {
          margin: 0.75rem 0 0;
          font-size: 0.625rem;
          color: #6B7280;
        }
      `}</style>
    </div>
  );
};

// ============================================================================
// Main Page Component
// ============================================================================

export const InitializationCompletePage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const projectName = searchParams.get('projectName') || 'bakery-app';

  // Handle view dashboard
  const handleViewDashboard = useCallback(() => {
    navigate(`/projects/${projectId}`);
  }, [projectId, navigate]);

  return (
    <div className="initialization-complete-page">
      {/* Success Header */}
      <SuccessHeader projectName={projectName} stats={STATS} />

      {/* Quick Links */}
      <QuickLinksBar links={QUICK_LINKS} />

      {/* Main Content Grid */}
      <div className="complete-content">
        <main className="main-content">
          {/* Next Steps */}
          <NextStepsList steps={NEXT_STEPS} projectId={projectId || ''} />

          {/* Credentials */}
          <CredentialsCard projectId={projectId || ''} />
        </main>

        {/* Resources Sidebar */}
        <aside className="resources-sidebar">
          <h3 className="sidebar-title">Created Resources</h3>
          <ResourcesList
            resources={CREATED_RESOURCES}
            showFilters={false}
            showCost
            compact
          />
        </aside>
      </div>

      {/* Footer Actions */}
      <footer className="complete-footer">
        <div className="footer-tip">
          <span className="tip-icon">💡</span>
          <span className="tip-text">
            Need help? Check out our{' '}
            <a href="https://docs.yappc.dev" target="_blank" rel="noopener noreferrer">
              documentation
            </a>{' '}
            or join our{' '}
            <a href="https://discord.gg/yappc" target="_blank" rel="noopener noreferrer">
              Discord community
            </a>
          </span>
        </div>

        <button
          type="button"
          className="btn btn-primary"
          onClick={handleViewDashboard}
        >
          View Project Dashboard
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <polyline points="9,18 15,12 9,6" />
          </svg>
        </button>
      </footer>

      {/* CSS-in-JS Styles */}
      <style>{`
        .initialization-complete-page {
          min-height: 100vh;
          padding: 2rem;
          background: #F3F4F6;
        }

        .complete-content {
          display: grid;
          grid-template-columns: 1fr 340px;
          gap: 2rem;
        }

        .main-content {
          min-width: 0;
        }

        .resources-sidebar {
          background: #fff;
          border-radius: 12px;
          padding: 1.25rem;
          box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
          height: fit-content;
          position: sticky;
          top: 2rem;
        }

        .sidebar-title {
          margin: 0 0 1rem;
          font-size: 0.875rem;
          font-weight: 600;
          color: #111827;
        }

        .complete-footer {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-top: 2rem;
          padding-top: 1.5rem;
          border-top: 1px solid #E5E7EB;
        }

        .footer-tip {
          display: flex;
          align-items: center;
          gap: 0.5rem;
        }

        .tip-icon {
          font-size: 1.25rem;
        }

        .tip-text {
          font-size: 0.875rem;
          color: #6B7280;
        }

        .tip-text a {
          color: #3B82F6;
          text-decoration: none;
        }

        .tip-text a:hover {
          text-decoration: underline;
        }

        .btn {
          display: inline-flex;
          align-items: center;
          gap: 0.5rem;
          padding: 0.75rem 1.5rem;
          font-size: 0.875rem;
          font-weight: 500;
          border-radius: 8px;
          cursor: pointer;
          transition: all 0.15s ease;
        }

        .btn svg {
          width: 16px;
          height: 16px;
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

        @media (max-width: 1024px) {
          .complete-content {
            grid-template-columns: 1fr;
          }

          .resources-sidebar {
            position: static;
            order: -1;
          }
        }

        @media (max-width: 640px) {
          .complete-footer {
            flex-direction: column;
            gap: 1rem;
            text-align: center;
          }
        }
      `}</style>
    </div>
  );
};

InitializationCompletePage.displayName = 'InitializationCompletePage';

export default InitializationCompletePage;
