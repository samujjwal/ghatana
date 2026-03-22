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
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.development.backlog(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Backlog
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-blue-500/10">
            <BookOpen className="w-6 h-6 text-blue-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Story {storyId}</h1>
            <p className="text-zinc-400">User story details and progress</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-6">
          <div className="col-span-2 space-y-6">
            <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
              <h2 className="text-lg font-semibold mb-4">Description</h2>
              <p className="text-zinc-400">Story description will be loaded from the API.</p>
            </div>
            <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
              <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
                <CheckSquare className="w-5 h-5" /> Acceptance Criteria
              </h2>
              <p className="text-zinc-500 text-sm">No criteria defined yet.</p>
            </div>
          </div>
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-zinc-900 border border-zinc-800 text-sm space-y-3">
              <div className="flex justify-between"><span className="text-zinc-400">Status</span><span>To Do</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Priority</span><span>Medium</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Points</span><span>—</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Assignee</span><span>Unassigned</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StoryDetailPage;
