/**
 * Standups Page
 *
 * @description Async standup board with yesterday/today/blockers format,
 * team timeline, and streak tracking.
 *
 * @doc.type page
 * @doc.purpose Async standup management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { MessageSquare, Plus, Users, Clock } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { useTranslation } from '@ghatana/i18n';

const StandupsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const { t } = useTranslation('common');

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-sky-500/10">
              <MessageSquare className="w-6 h-6 text-sky-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">{t('standups.title')}</h1>
              <p className="text-fg-muted">{t('standups.subtitle')}</p>
            </div>
          </div>
          <Button startIcon={<Plus className="w-4 h-4" />} className="rounded-lg bg-violet-600 hover:bg-violet-500">
            {t('standups.postUpdate')}
          </Button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <MessageSquare className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">{t('standups.noStandups')}</h3>
          <p className="text-fg-muted max-w-md">
            {t('standups.noStandupsDesc')}
          </p>
        </div>
      </div>
    </div>
  );
};

export default StandupsPage;
