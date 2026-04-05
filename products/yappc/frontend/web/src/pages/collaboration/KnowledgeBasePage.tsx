/**
 * Knowledge Base Page
 *
 * @description Team knowledge base with article search, categories,
 * and recently-updated content.
 *
 * @doc.type page
 * @doc.purpose Knowledge base listing
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { BookOpen, Plus, Search, Tag } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const KnowledgeBasePage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [search, setSearch] = useState('');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10">
              <BookOpen className="w-6 h-6 text-blue-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Knowledge Base</h1>
              <p className="text-zinc-400">Team documentation and shared knowledge</p>
            </div>
          </div>
          {projectId && (
            <NavLink
              to={ROUTES.team.articleNew(projectId)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium"
            >
              <Plus className="w-4 h-4" /> New Article
            </NavLink>
          )}
        </div>

        <div className="relative max-w-md mb-6">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
          <input
            type="text"
            placeholder="Search articles..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-white placeholder-zinc-500 focus:border-violet-500 focus:outline-none"
          />
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <BookOpen className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No articles yet</h3>
          <p className="text-zinc-500 max-w-md">
            Start building your team's knowledge base by creating articles for
            onboarding, architecture decisions, and runbooks.
          </p>
        </div>
      </div>
    </div>
  );
};

export default KnowledgeBasePage;
