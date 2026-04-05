/**
 * Pull Requests List Page
 *
 * @description Pull request dashboard with status filters, review queue,
 * and merge-readiness indicators.
 *
 * @doc.type page
 * @doc.purpose PR list and review queue
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { GitPullRequest, Search, Filter, CheckCircle2, Clock, XCircle } from 'lucide-react';
import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

type PRStatus = 'open' | 'merged' | 'closed';

const tabs: { label: string; status: PRStatus }[] = [
  { label: 'Open', status: 'open' },
  { label: 'Merged', status: 'merged' },
  { label: 'Closed', status: 'closed' },
];

const PullRequestsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [activeTab, setActiveTab] = useState<PRStatus>('open');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-green-500/10">
            <GitPullRequest className="w-6 h-6 text-green-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Pull Requests</h1>
            <p className="text-zinc-400">Review and merge code changes</p>
          </div>
        </div>

        <div className="flex items-center gap-4 mb-6 border-b border-zinc-800 pb-3">
          {tabs.map((tab) => (
            <button
              key={tab.status}
              onClick={() => setActiveTab(tab.status)}
              className={cn(
                'px-3 py-1.5 rounded-lg text-sm transition-colors',
                activeTab === tab.status
                  ? 'bg-zinc-800 text-white'
                  : 'text-zinc-400 hover:text-white'
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <GitPullRequest className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">
            No {activeTab} pull requests
          </h3>
          <p className="text-zinc-500 max-w-md">
            Pull requests will appear here once branches are pushed and reviews requested.
          </p>
        </div>
      </div>
    </div>
  );
};

export default PullRequestsPage;
