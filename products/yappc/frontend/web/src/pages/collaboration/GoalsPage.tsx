/**
 * Goals Page
 *
 * @description Team and project goals with OKR-style tracking,
 * progress indicators, and alignment views.
 *
 * @doc.type page
 * @doc.purpose Goal tracking and OKRs
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Target, Plus, TrendingUp } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useTranslation } from '@ghatana/i18n';

const GoalsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const { t } = useTranslation('common');

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-emerald-500/10">
              <Target className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">{t('goals.title')}</h1>
              <p className="text-fg-muted">{t('goals.subtitle')}</p>
            </div>
          </div>
          <Button startIcon={<Plus className="w-4 h-4" />} className="rounded-lg bg-violet-600 hover:bg-violet-500">
            {t('goals.newGoal')}
          </Button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Target className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">{t('goals.noGoals')}</h3>
          <p className="text-fg-muted max-w-md">
            {t('goals.noGoalsDesc')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default GoalsPage;
