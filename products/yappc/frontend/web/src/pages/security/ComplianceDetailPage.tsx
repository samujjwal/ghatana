/**
 * Compliance Detail Page
 *
 * @description Individual compliance framework detail with control list,
 * evidence status, and remediation steps.
 *
 * @doc.type page
 * @doc.purpose Compliance framework detail
 * @doc.layer product
 */

import React from 'react';
import { useParams, NavLink } from 'react-router';
import { Shield, ArrowLeft } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const ComplianceDetailPage: React.FC = () => {
  const { projectId, frameworkId } = useParams<{ projectId: string; frameworkId: string }>();

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.security.compliance(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-fg-muted hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Compliance
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-info-bg/10">
            <Shield className="w-6 h-6 text-info-color" />
          </div>
          <div>
            <h1 className="text-2xl font-bold capitalize">{frameworkId ?? 'Framework'}</h1>
            <p className="text-fg-muted">Control status and evidence tracking</p>
          </div>
        </div>

        <div className="p-6 rounded-xl bg-surface border border-border">
          <p className="text-fg-muted text-sm text-center py-8">
            Control details will appear here once the framework is configured.
          </p>
        </div>
      </div>
    </div>
  );
};

export default ComplianceDetailPage;
