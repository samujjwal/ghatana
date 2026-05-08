/**
 * Runbooks List Page
 *
 * @description Operational runbook library with search, categories,
 * and automated execution triggers.
 *
 * @doc.type page
 * @doc.purpose Runbook management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { BookOpen, Plus, Search } from 'lucide-react';
import { Button } from '../../components/ui/Button';

const RunbooksPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-info-bg/10">
              <BookOpen className="w-6 h-6 text-info-color" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Runbooks</h1>
              <p className="text-fg-muted">Operational procedures and automated responses</p>
            </div>
          </div>
          <Button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Runbook
          </Button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <BookOpen className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">No runbooks yet</h3>
          <p className="text-fg-muted max-w-md">
            Create runbooks to document operational procedures and enable
            automated incident response.
          </p>
        </div>
      </div>
    </div>
  );
};

export default RunbooksPage;
