/**
 * Audit Page
 *
 * @description Security audit log with user actions, access events,
 * and compliance-relevant activity tracking.
 *
 * @doc.type page
 * @doc.purpose Security audit trail
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams } from 'react-router';
import { ScrollText, Search, Filter, Download } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { useTranslation } from '@ghatana/i18n';

const AuditPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [search, setSearch] = useState('');
  const { t } = useTranslation('common');

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-surface-muted/10">
              <ScrollText className="w-6 h-6 text-fg-muted" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Audit Log</h1>
              <p className="text-fg-muted">Security-relevant events and access history</p>
            </div>
          </div>
          <Button variant="ghost" className="flex items-center gap-2 px-4 py-2 rounded-lg bg-surface hover:bg-surface-muted transition-colors text-sm text-fg-muted">
            <Download className="w-4 h-4" /> Export
          </Button>
        </div>

        <div className="flex items-center gap-3 mb-6">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
            <Input
              type="text"
              placeholder={t('audit.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 rounded-lg bg-surface border border-border text-sm text-white placeholder-zinc-500 focus:border-violet-500 focus:outline-none"
            />
          </div>
          <Button variant="outline" className="flex items-center gap-2 px-3 py-2 rounded-lg bg-surface border border-border text-sm text-fg-muted hover:text-white">
            <Filter className="w-4 h-4" /> Filters
          </Button>
        </div>

        <div className="p-6 rounded-xl bg-surface border border-border text-center">
          <ScrollText className="w-12 h-12 text-fg-muted mx-auto mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">No audit events</h3>
          <p className="text-fg-muted max-w-md mx-auto">
            Audit events will be recorded as users interact with the system.
            All authentication, authorization, and data access events are logged.
          </p>
        </div>
      </div>
    </div>
  );
};

export default AuditPage;
