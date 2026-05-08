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
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
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
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.team.knowledge(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-fg-muted hover:text-white mb-8"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Knowledge Base
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-violet-500/10">
            <FilePlus className="w-6 h-6 text-violet-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">New Article</h1>
            <p className="text-fg-muted">Choose a template and start writing</p>
          </div>
        </div>

        <div className="space-y-6">
          <div>
            <label className="block text-sm text-fg-muted mb-2">Title</label>
            <Input
              type="text"
              placeholder="Article title..."
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              fullWidth
              className="px-4 py-3 rounded-lg bg-surface border border-border text-white placeholder-zinc-500 focus:border-violet-500"
            />
          </div>

          <div>
            <label className="block text-sm text-fg-muted mb-3">Template</label>
            <div className="grid grid-cols-3 gap-3">
              {templates.map((t) => (
                <Button
                  key={t.id}
                  variant="ghost"
                  fullWidth
                  onClick={() => setSelectedTemplate(t.id)}
                  aria-pressed={selectedTemplate === t.id}
                  className={cn(
                    'h-full justify-start p-4 rounded-xl border text-left transition-colors [&>span]:block',
                    selectedTemplate === t.id
                      ? 'border-violet-500 bg-violet-500/10'
                      : 'border-border bg-surface hover:border-border'
                  )}
                >
                  <t.icon className="w-5 h-5 mb-2 text-fg-muted" />
                  <span className="block font-medium text-sm">{t.label}</span>
                  <span className="block text-xs text-fg-muted mt-1">{t.description}</span>
                </Button>
              ))}
            </div>
          </div>

          <Button startIcon={<Save className="w-4 h-4" />} className="px-6 py-3 rounded-lg bg-violet-600 hover:bg-violet-500">
            Create Article
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ArticleNewPage;
