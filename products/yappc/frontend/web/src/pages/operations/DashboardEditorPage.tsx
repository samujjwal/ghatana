import React, { useState, useCallback } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

type WidgetType = 'chart' | 'metric' | 'table' | 'log' | 'status';

interface DashboardWidget {
  id: string;
  type: WidgetType;
  title: string;
  col: number;
  row: number;
  width: number;
  height: number;
  config: Record<string, string | number | boolean>;
}

interface DashboardData {
  id: string;
  name: string;
  description: string;
  widgets: DashboardWidget[];
  updatedAt: string;
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
  'Content-Type': 'application/json',
});

const WIDGET_ICONS: Record<WidgetType, string> = {
  chart: '📊',
  metric: '🔢',
  table: '📋',
  log: '📝',
  status: '🟢',
};

const GRID_COLS = 12;

/**
 * DashboardEditorPage — Dashboard editor with widget grid and save/discard.
 *
 * @doc.type component
 * @doc.purpose Dashboard layout editor for customizable operations dashboards
 * @doc.layer product
 */
const DashboardEditorPage: React.FC = () => {
  const { dashboardId } = useParams<{ dashboardId: string }>();
  const queryClient = useQueryClient();

  const { data: savedDashboard, isLoading, error } = useQuery<DashboardData>({
    queryKey: ['dashboard-editor', dashboardId],
    queryFn: async () => {
      const res = await fetch(`/api/dashboards/${dashboardId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load dashboard');
      return res.json() as Promise<DashboardData>;
    },
    enabled: !!dashboardId,
  });

  const [name, setName] = useState('');
  const [widgets, setWidgets] = useState<DashboardWidget[]>([]);
  const [hasChanges, setHasChanges] = useState(false);
  const [draggingId, setDraggingId] = useState<string | null>(null);

  // Sync from server data on first load
  React.useEffect(() => {
    if (savedDashboard) {
      setName(savedDashboard.name);
      setWidgets(savedDashboard.widgets);
      setHasChanges(false);
    }
  }, [savedDashboard]);

  const saveMutation = useMutation<void, Error>({
    mutationFn: async () => {
      const res = await fetch(`/api/dashboards/${dashboardId}`, {
        method: 'PUT',
        headers: authHeaders(),
        body: JSON.stringify({ name, widgets }),
      });
      if (!res.ok) throw new Error('Failed to save dashboard');
    },
    onSuccess: () => {
      setHasChanges(false);
      queryClient.invalidateQueries({ queryKey: ['dashboard-editor', dashboardId] });
    },
  });

  const addWidget = useCallback(() => {
    const maxRow = widgets.reduce((max, w) => Math.max(max, w.row + w.height), 0);
    const newWidget: DashboardWidget = {
      id: `widget-${Date.now()}`,
      type: 'metric',
      title: 'New Widget',
      col: 0,
      row: maxRow,
      width: 4,
      height: 2,
      config: {},
    };
    setWidgets((prev) => [...prev, newWidget]);
    setHasChanges(true);
  }, [widgets]);

  const removeWidget = useCallback((widgetId: string) => {
    setWidgets((prev) => prev.filter((w) => w.id !== widgetId));
    setHasChanges(true);
  }, []);

  const updateWidgetTitle = useCallback((widgetId: string, title: string) => {
    setWidgets((prev) =>
      prev.map((w) => (w.id === widgetId ? { ...w, title } : w)),
    );
    setHasChanges(true);
  }, []);

  const updateWidgetType = useCallback((widgetId: string, type: WidgetType) => {
    setWidgets((prev) =>
      prev.map((w) => (w.id === widgetId ? { ...w, type } : w)),
    );
    setHasChanges(true);
  }, []);

  const handleDiscard = () => {
    if (savedDashboard) {
      setName(savedDashboard.name);
      setWidgets(savedDashboard.widgets);
    }
    setHasChanges(false);
  };

  const handleDragStart = (id: string) => {
    setDraggingId(id);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>, targetId: string) => {
    e.preventDefault();
    if (!draggingId || draggingId === targetId) return;
  };

  const handleDrop = (targetId: string) => {
    if (!draggingId || draggingId === targetId) {
      setDraggingId(null);
      return;
    }
    setWidgets((prev) => {
      const fromIdx = prev.findIndex((w) => w.id === draggingId);
      const toIdx = prev.findIndex((w) => w.id === targetId);
      if (fromIdx === -1 || toIdx === -1) return prev;
      const updated = [...prev];
      const [moved] = updated.splice(fromIdx, 1);
      updated.splice(toIdx, 0, moved);
      return updated;
    });
    setDraggingId(null);
    setHasChanges(true);
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load dashboard'}
        </div>
      </div>
    );
  }

  const widgetTypes: WidgetType[] = ['chart', 'metric', 'table', 'log', 'status'];

  return (
    <div className="p-6 space-y-6">
      {/* Header bar */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex-1 min-w-0 max-w-md">
          <label htmlFor="dashboard-name" className="block text-xs font-medium text-zinc-500 mb-1">Dashboard Name</label>
          <input
            id="dashboard-name"
            type="text"
            value={name}
            onChange={(e) => { setName(e.target.value); setHasChanges(true); }}
            className="w-full bg-zinc-900 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-zinc-200 placeholder-zinc-500 outline-none focus:border-blue-500 transition-colors"
            placeholder="Dashboard name…"
          />
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            onClick={addWidget}
            className="px-4 py-2 bg-zinc-800 border border-zinc-700 text-zinc-300 text-sm font-medium rounded-lg hover:bg-zinc-700 transition-colors flex items-center gap-1.5"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Add Widget
          </button>
          <button
            onClick={handleDiscard}
            disabled={!hasChanges}
            className="px-4 py-2 border border-zinc-700 text-zinc-300 text-sm font-medium rounded-lg hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            Discard
          </button>
          <button
            onClick={() => saveMutation.mutate()}
            disabled={!hasChanges || saveMutation.isPending}
            className="px-4 py-2 bg-blue-600 text-white text-sm font-semibold rounded-lg hover:bg-blue-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
          >
            {saveMutation.isPending ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>

      {hasChanges && (
        <div className="flex items-center gap-2 text-xs text-yellow-400 bg-yellow-500/10 border border-yellow-500/20 rounded-lg px-3 py-2">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          You have unsaved changes.
        </div>
      )}

      {/* Widget grid */}
      {widgets.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 border-2 border-dashed border-zinc-800 rounded-lg">
          <svg className="w-12 h-12 text-zinc-700 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M4 5a1 1 0 011-1h4a1 1 0 011 1v5a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v2a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 16a1 1 0 011-1h4a1 1 0 011 1v3a1 1 0 01-1 1H5a1 1 0 01-1-1v-3zM14 13a1 1 0 011-1h4a1 1 0 011 1v6a1 1 0 01-1 1h-4a1 1 0 01-1-1v-6z" />
          </svg>
          <p className="text-sm text-zinc-500 mb-3">No widgets yet</p>
          <button
            onClick={addWidget}
            className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-500 transition-colors"
          >
            Add Your First Widget
          </button>
        </div>
      ) : (
        <div
          className="grid gap-4"
          style={{ gridTemplateColumns: `repeat(${GRID_COLS}, minmax(0, 1fr))` }}
        >
          {widgets.map((widget) => (
            <div
              key={widget.id}
              draggable
              onDragStart={() => handleDragStart(widget.id)}
              onDragOver={(e) => handleDragOver(e, widget.id)}
              onDrop={() => handleDrop(widget.id)}
              className={`bg-zinc-900 border rounded-lg p-4 transition-all ${
                draggingId === widget.id
                  ? 'border-blue-500 opacity-50'
                  : 'border-zinc-800 hover:border-zinc-700'
              }`}
              style={{
                gridColumn: `span ${Math.min(widget.width, GRID_COLS)}`,
              }}
            >
              {/* Drag handle & actions */}
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2 cursor-grab active:cursor-grabbing">
                  <svg className="w-4 h-4 text-zinc-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8h16M4 16h16" />
                  </svg>
                  <span className="text-lg" role="img" aria-label={widget.type}>{WIDGET_ICONS[widget.type]}</span>
                </div>
                <button
                  onClick={() => removeWidget(widget.id)}
                  className="text-zinc-600 hover:text-red-400 transition-colors"
                  aria-label="Remove widget"
                >
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>

              {/* Widget title */}
              <input
                type="text"
                value={widget.title}
                onChange={(e) => updateWidgetTitle(widget.id, e.target.value)}
                className="w-full bg-transparent text-sm font-medium text-zinc-200 outline-none border-b border-transparent focus:border-zinc-600 mb-2 pb-1"
              />

              {/* Widget type selector */}
              <select
                value={widget.type}
                onChange={(e) => updateWidgetType(widget.id, e.target.value as WidgetType)}
                className="w-full bg-zinc-800 border border-zinc-700 rounded px-2 py-1 text-xs text-zinc-400 outline-none"
              >
                {widgetTypes.map((t) => (
                  <option key={t} value={t}>{t.charAt(0).toUpperCase() + t.slice(1)}</option>
                ))}
              </select>

              {/* Placeholder content */}
              <div className="mt-3 h-24 bg-zinc-950 rounded border border-zinc-800 flex items-center justify-center">
                <span className="text-xs text-zinc-600">Widget preview</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default DashboardEditorPage;
