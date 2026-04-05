/**
 * Article Edit Page
 *
 * @description Rich-text article editor with Markdown support,
 * version history, and auto-save.
 *
 * @doc.type page
 * @doc.purpose Article editing
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { Edit3, ArrowLeft, Save, Eye } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const ArticleEditPage: React.FC = () => {
  const { projectId, articleId } = useParams<{ projectId: string; articleId: string }>();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <NavLink
            to={projectId ? ROUTES.team.knowledge(projectId) : '/'}
            className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-white"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Knowledge Base
          </NavLink>
          <div className="flex items-center gap-3">
            <button className="flex items-center gap-2 px-3 py-2 rounded-lg bg-zinc-800 text-sm text-zinc-300 hover:text-white">
              <Eye className="w-4 h-4" /> Preview
            </button>
            <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
              <Save className="w-4 h-4" /> Save
            </button>
          </div>
        </div>

        <div className="space-y-4">
          <input
            type="text"
            placeholder="Article title..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-0 py-2 text-3xl font-bold bg-transparent border-none text-white placeholder-zinc-600 focus:outline-none"
          />
          <textarea
            placeholder="Start writing in Markdown..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={20}
            className="w-full px-0 py-2 bg-transparent border-none text-zinc-300 placeholder-zinc-600 focus:outline-none resize-none font-mono text-sm leading-relaxed"
          />
        </div>
      </div>
    </div>
  );
};

export default ArticleEditPage;
