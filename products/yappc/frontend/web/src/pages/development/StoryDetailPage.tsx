/**
 * Story Detail Page
 *
 * @description Individual user story view with acceptance criteria,
 * sub-tasks, linked PRs, and activity timeline.
 *
 * @doc.type page
 * @doc.purpose Story detail view
 * @doc.layer product
 */

import React from 'react';
import { useParams, NavLink } from 'react-router';
import { BookOpen, ArrowLeft, MessageSquare, GitBranch, CheckSquare, Clock } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const StoryDetailPage: React.FC = () => {
  const { projectId, storyId } = useParams<{ projectId: string; storyId: string }>();

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.development.backlog(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-fg-muted hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Backlog
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-info-bg/10">
            <BookOpen className="w-6 h-6 text-info-color" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Story {storyId}</h1>
            <p className="text-fg-muted">User story details and progress</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-6">
          <div className="col-span-2 space-y-6">
            <div className="p-6 rounded-xl bg-surface border border-border">
              <h2 className="text-lg font-semibold mb-4">Description</h2>
              <p className="text-fg-muted">Story description will be loaded from the API.</p>
            </div>
            <div className="p-6 rounded-xl bg-surface border border-border">
              <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                <CheckSquare className="w-5 h-5" /> Acceptance Criteria
              </h2>
              <p className="text-fg-muted text-sm">No criteria defined yet.</p>
            </div>
          </div>
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-surface border border-border text-sm space-y-3">
              <div className="flex justify-between"><span className="text-fg-muted">Status</span><span>To Do</span></div>
              <div className="flex justify-between"><span className="text-fg-muted">Priority</span><span>Medium</span></div>
              <div className="flex justify-between"><span className="text-fg-muted">Points</span><span>—</span></div>
              <div className="flex justify-between"><span className="text-fg-muted">Assignee</span><span>Unassigned</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StoryDetailPage;
