/**
 * Article New Page
 *
 * @description Create a new knowledge base article with template selection.
 *
 * @doc.type page
 * @doc.purpose New article creation
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { FilePlus, ArrowLeft, Save, FileText, BookOpen, Wrench } from 'lucide-react';
import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

const templates = [
  { id: 'blank', label: 'Blank', icon: FileText, description: 'Start from scratch' },
  { id: 'adr', label: 'ADR', icon: BookOpen, description: 'Architecture Decision Record' },
  { id: 'runbook', label: 'Runbook', icon: Wrench, description: 'Operational procedure' },
];

const ArticleNewPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [title, setTitle] = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState('blank');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.team.knowledge(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-white mb-8"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Knowledge Base
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-violet-500/10">
            <FilePlus className="w-6 h-6 text-violet-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">New Article</h1>
            <p className="text-zinc-400">Choose a template and start writing</p>
          </div>
        </div>

        <div className="space-y-6">
          <div>
            <label className="block text-sm text-zinc-400 mb-2">Title</label>
            <input
              type="text"
              placeholder="Article title..."
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-4 py-3 rounded-lg bg-zinc-900 border border-zinc-800 text-white placeholder-zinc-500 focus:border-violet-500 focus:outline-none"
            />
          </div>

          <div>
            <label className="block text-sm text-zinc-400 mb-3">Template</label>
            <div className="grid grid-cols-3 gap-3">
              {templates.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setSelectedTemplate(t.id)}
                  className={cn(
                    'p-4 rounded-xl border text-left transition-colors',
                    selectedTemplate === t.id
                      ? 'border-violet-500 bg-violet-500/10'
                      : 'border-zinc-800 bg-zinc-900 hover:border-zinc-700'
                  )}
                >
                  <t.icon className="w-5 h-5 mb-2 text-zinc-400" />
                  <div className="font-medium text-sm">{t.label}</div>
                  <div className="text-xs text-zinc-500 mt-1">{t.description}</div>
                </button>
              ))}
            </div>
          </div>

          <button className="flex items-center gap-2 px-6 py-3 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Save className="w-4 h-4" /> Create Article
          </button>
        </div>
      </div>
    </div>
  );
};

export default ArticleNewPage;
