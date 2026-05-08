/**
 * Epics Page
 *
 * @description Epic tracking with progress bars, story roll-ups,
 * and timeline visualization.
 *
 * @doc.type page
 * @doc.purpose Epic management and progress tracking
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Layers, Plus, BarChart3 } from 'lucide-react';
import { Button } from '../../components/ui/Button';

const EpicsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-info-bg/10">
              <Layers className="w-6 h-6 text-info-color" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Epics</h1>
              <p className="text-fg-muted">Track large features and initiatives</p>
            </div>
          </div>
          <Button
            className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500"
            startIcon={<Plus className="w-4 h-4" />}
          >
            New Epic
          </Button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <BarChart3 className="w-12 h-12 text-fg-muted mb-4" />
          <h3 className="text-lg font-semibold text-fg-muted mb-2">No epics yet</h3>
          <p className="text-fg-muted max-w-md">
            Create epics to group related stories and track progress across
            larger initiatives.
          </p>
        </div>
      </div>
    </div>
  );
};

export default EpicsPage;
