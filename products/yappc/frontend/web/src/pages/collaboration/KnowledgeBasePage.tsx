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
import { Input } from '../../components/ui/Input';
import { ROUTES } from '../../router/paths';
import { useI18n } from '../../i18n/I18nProvider';

const KnowledgeBasePage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [search, setSearch] = useState('');
  const { t } = useI18n();

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-info-bg/10">
              <BookOpen className="w-6 h-6 text-info-color" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">{t('kb.title')}</h1>
              <p className="text-fg-muted">{t('kb.subtitle')}</p>
            </div>
          </div>
          {projectId && (
            <NavLink
              to={ROUTES.team.articleNew(projectId)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium"
            >
              <Plus className="w-4 h-4" /> {t('kb.newArticle')}
            </NavLink>
          )}
        </div>

        <div className="relative max-w-md mb-6">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
          <Input
            type="text"
            placeholder={t('kb.searchPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            fullWidth
            size="sm"
            className="pl-10 pr-4 py-2 rounded-lg bg-surface border border-border text-sm text-white placeholder-zinc-500 focus:border-violet-500"
          />
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <BookOpen className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">{t('kb.noArticles')}</h3>
          <p className="text-fg-muted max-w-md">
            {t('kb.noArticlesDesc')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default KnowledgeBasePage;
