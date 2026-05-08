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
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Textarea } from '../../components/ui/Textarea';

const ArticleEditPage: React.FC = () => {
  const { projectId, articleId } = useParams<{ projectId: string; articleId: string }>();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <NavLink
            to={projectId ? ROUTES.team.knowledge(projectId) : '/'}
            className="inline-flex items-center gap-2 text-sm text-fg-muted hover:text-white"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Knowledge Base
          </NavLink>
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="sm"
              className="rounded-lg bg-surface px-3 py-2 text-sm text-fg-muted hover:text-white"
              startIcon={<Eye className="w-4 h-4" />}
            >
              Preview
            </Button>
            <Button
              size="sm"
              className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500"
              startIcon={<Save className="w-4 h-4" />}
            >
              Save
            </Button>
          </div>
        </div>

        <div className="space-y-4">
          <Input
            type="text"
            placeholder="Article title..."
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            fullWidth
            className="px-0 py-2 text-3xl font-bold bg-transparent border-none text-white placeholder-zinc-600"
          />
          <Textarea
            placeholder="Start writing in Markdown..."
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={20}
            fullWidth
            resize="none"
            className="px-0 py-2 bg-transparent border-none text-fg-muted placeholder-zinc-600 font-mono text-sm leading-relaxed"
          />
        </div>
      </div>
    </div>
  );
};

export default ArticleEditPage;
