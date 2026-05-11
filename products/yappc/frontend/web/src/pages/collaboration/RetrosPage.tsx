/**
 * Retros Page
 *
 * @description Sprint retrospective board with columns for went-well,
 * improve, and action items. Supports voting and anonymity.
 *
 * @doc.type page
 * @doc.purpose Sprint retrospective facilitation
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { RotateCcw, Plus, ThumbsUp, ThumbsDown, Lightbulb } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useTranslation } from '@ghatana/i18n';

const RetrosPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const { t } = useTranslation('common');

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-info-bg/10">
              <RotateCcw className="w-6 h-6 text-info-color" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">{t('retros.title')}</h1>
              <p className="text-fg-muted">{t('retros.subtitle')}</p>
            </div>
          </div>
          <Button
            className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500"
            startIcon={<Plus className="w-4 h-4" />}
          >
            {t('retros.newRetro')}
          </Button>
        </div>

        <div className="grid grid-cols-3 gap-4 mb-8">
          {[
            { label: 'Went Well', icon: ThumbsUp, color: 'emerald' },
            { label: 'To Improve', icon: ThumbsDown, color: 'amber' },
            { label: 'Action Items', icon: Lightbulb, color: 'blue' },
          ].map(({ label, icon: Icon, color }) => (
            <div key={label} className="p-4 rounded-xl bg-surface border border-border">
              <div className="flex items-center gap-2 mb-3">
                <Icon className={`w-4 h-4 text-${color}-400`} />
                <span className="text-sm font-medium">{label}</span>
              </div>
              <p className="text-xs text-fg-muted">{t('retros.noItems')}</p>
            </div>
          ))}
        </div>

        <div className="flex flex-col items-center justify-center py-12 text-center">
          <RotateCcw className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">{t('retros.noRetros')}</h3>
          <p className="text-fg-muted max-w-md">
            {t('retros.emptyDesc')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default RetrosPage;
