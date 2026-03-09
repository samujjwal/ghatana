/**
 * Interactive Dashboard Components for Flashit
 * Customizable widgets and real-time data updates
 *
 * @doc.type component
 * @doc.purpose Advanced dashboard widgets with customization
 * @doc.layer product
 * @doc.pattern ReactComponent
 */

import React, { useState, useEffect, useCallback } from 'react';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

// Widget types
export type WidgetType =
  | 'productivity_trend'
  | 'emotion_overview'
  | 'moment_stats'
  | 'sphere_activity'
  | 'search_analytics'
  | 'goal_progress'
  | 'recent_insights'
  | 'weekly_summary';

export interface Widget {
  id: string;
  type: WidgetType;
  title: string;
  size: 'small' | 'medium' | 'large';
  position: { x: number; y: number };
  settings: Record<string, any>;
  enabled: boolean;
}

export interface DashboardConfig {
  widgets: Widget[];
  layout: 'grid' | 'masonry';
  theme: 'light' | 'dark' | 'auto';
  autoRefresh: boolean;
  refreshInterval: number; // seconds
}

/**
 * Customizable Dashboard Layout Component
 */
export function CustomizableDashboard() {
  const [config, setConfig] = useState<DashboardConfig>({
    widgets: [],
    layout: 'grid',
    theme: 'light',
    autoRefresh: true,
    refreshInterval: 30,
  });
  const [isEditing, setIsEditing] = useState(false);
  const [data, setData] = useState<any>({});
  const [loading, setLoading] = useState(true);

  // Load dashboard configuration
  useEffect(() => {
    loadDashboardConfig();
  }, []);

  // Auto-refresh data
  useEffect(() => {
    if (config.autoRefresh) {
      const interval = setInterval(() => {
        refreshData();
      }, config.refreshInterval * 1000);

      return () => clearInterval(interval);
    }
  }, [config.autoRefresh, config.refreshInterval]);

  const loadDashboardConfig = async () => {
    try {
      const stored = localStorage.getItem('flashit_dashboard_config');
      if (stored) {
        setConfig(JSON.parse(stored));
      } else {
        // Set default configuration
        const defaultConfig: DashboardConfig = {
          widgets: [
            {
              id: 'productivity-1',
              type: 'productivity_trend',
              title: 'Productivity Trend',
              size: 'large',
              position: { x: 0, y: 0 },
              settings: { period: 'week' },
              enabled: true,
            },
            {
              id: 'moments-1',
              type: 'moment_stats',
              title: 'Moment Statistics',
              size: 'medium',
              position: { x: 1, y: 0 },
              settings: {},
              enabled: true,
            },
            {
              id: 'emotions-1',
              type: 'emotion_overview',
              title: 'Emotional Overview',
              size: 'medium',
              position: { x: 0, y: 1 },
              settings: {},
              enabled: true,
            },
            {
              id: 'spheres-1',
              type: 'sphere_activity',
              title: 'Sphere Activity',
              size: 'medium',
              position: { x: 1, y: 1 },
              settings: {},
              enabled: true,
            },
          ],
          layout: 'grid',
          theme: 'light',
          autoRefresh: true,
          refreshInterval: 30,
        };
        setConfig(defaultConfig);
        localStorage.setItem('flashit_dashboard_config', JSON.stringify(defaultConfig));
      }

      await refreshData();
    } catch (error) {
      console.error('Failed to load dashboard config:', error);
    } finally {
      setLoading(false);
    }
  };

  const refreshData = async () => {
    try {
      const response = await fetch('/api/analytics/dashboard', {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('flashit_token')}`,
        },
      });

      if (response.ok) {
        const dashboardData = await response.json();
        setData(dashboardData);
      }
    } catch (error) {
      console.error('Failed to refresh dashboard data:', error);
    }
  };

  const saveDashboardConfig = (newConfig: DashboardConfig) => {
    setConfig(newConfig);
    localStorage.setItem('flashit_dashboard_config', JSON.stringify(newConfig));
  };

  const addWidget = (type: WidgetType) => {
    const newWidget: Widget = {
      id: `${type}-${Date.now()}`,
      type,
      title: getWidgetTitle(type),
      size: 'medium',
      position: { x: 0, y: 0 },
      settings: {},
      enabled: true,
    };

    const newConfig = {
      ...config,
      widgets: [...config.widgets, newWidget],
    };

    saveDashboardConfig(newConfig);
  };

  const removeWidget = (widgetId: string) => {
    const newConfig = {
      ...config,
      widgets: config.widgets.filter(w => w.id !== widgetId),
    };

    saveDashboardConfig(newConfig);
  };

  const updateWidget = (widgetId: string, updates: Partial<Widget>) => {
    const newConfig = {
      ...config,
      widgets: config.widgets.map(w =>
        w.id === widgetId ? { ...w, ...updates } : w
      ),
    };

    saveDashboardConfig(newConfig);
  };

  const onDragEnd = (result: any) => {
    if (!result.destination) return;

    const sourceIndex = result.source.index;
    const destinationIndex = result.destination.index;

    const newWidgets = Array.from(config.widgets);
    const [reorderedWidget] = newWidgets.splice(sourceIndex, 1);
    newWidgets.splice(destinationIndex, 0, reorderedWidget);

    saveDashboardConfig({
      ...config,
      widgets: newWidgets,
    });
  };

  if (loading) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Dashboard Header */}
        <DashboardHeader
          config={config}
          isEditing={isEditing}
          onEditToggle={() => setIsEditing(!isEditing)}
          onConfigChange={saveDashboardConfig}
          onRefresh={refreshData}
        />

        {/* Edit Mode Controls */}
        {isEditing && (
          <EditControls
            config={config}
            onAddWidget={addWidget}
            onConfigChange={saveDashboardConfig}
          />
        )}

        {/* Widget Grid */}
        <DragDropContext onDragEnd={onDragEnd}>
          <Droppable droppableId="dashboard" isDropDisabled={!isEditing}>
            {(provided) => (
              <div
                ref={provided.innerRef}
                {...provided.droppableProps}
                className={`grid gap-6 ${
                  config.layout === 'grid'
                    ? 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3'
                    : 'columns-1 md:columns-2 lg:columns-3'
                }`}
              >
                {config.widgets.filter(w => w.enabled).map((widget, index) => (
                  <Draggable
                    key={widget.id}
                    draggableId={widget.id}
                    index={index}
                    isDragDisabled={!isEditing}
                  >
                    {(provided, snapshot) => (
                      <div
                        ref={provided.innerRef}
                        {...provided.draggableProps}
                        className={`${getWidgetSizeClass(widget.size)} ${
                          snapshot.isDragging ? 'rotate-3 shadow-xl' : ''
                        }`}
                      >
                        <WidgetContainer
                          widget={widget}
                          data={data}
                          isEditing={isEditing}
                          onUpdate={(updates) => updateWidget(widget.id, updates)}
                          onRemove={() => removeWidget(widget.id)}
                          dragHandleProps={provided.dragHandleProps}
                        />
                      </div>
                    )}
                  </Draggable>
                ))}
                {provided.placeholder}
              </div>
            )}
          </Droppable>
        </DragDropContext>

        {/* No Widgets State */}
        {config.widgets.filter(w => w.enabled).length === 0 && (
          <EmptyDashboard onAddWidget={addWidget} />
        )}
      </div>
    </div>
  );
}

/**
 * Dashboard Header Component
 */
function DashboardHeader({
  config,
  isEditing,
  onEditToggle,
  onConfigChange,
  onRefresh
}: {
  config: DashboardConfig;
  isEditing: boolean;
  onEditToggle: () => void;
  onConfigChange: (config: DashboardConfig) => void;
  onRefresh: () => void;
}) {
  return (
    <div className="mb-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Analytics Dashboard</h1>
          <p className="text-gray-600 mt-1">
            {isEditing ? 'Customize your dashboard layout' : 'Your personalized insights'}
          </p>
        </div>

        <div className="flex items-center space-x-2">
          {/* Auto-refresh toggle */}
          <label className="flex items-center space-x-2">
            <input
              type="checkbox"
              checked={config.autoRefresh}
              onChange={(e) => onConfigChange({
                ...config,
                autoRefresh: e.target.checked,
              })}
              className="rounded border-gray-300 text-blue-600 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-600">Auto-refresh</span>
          </label>

          {/* Manual refresh */}
          <button
            onClick={onRefresh}
            className="p-2 text-gray-400 hover:text-gray-600 rounded-md hover:bg-gray-100 transition-colors"
            title="Refresh data"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>

          {/* Edit mode toggle */}
          <button
            onClick={onEditToggle}
            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              isEditing
                ? 'bg-blue-500 text-white hover:bg-blue-600'
                : 'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50'
            }`}
          >
            {isEditing ? 'Done' : 'Customize'}
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Edit Controls Component
 */
function EditControls({
  config,
  onAddWidget,
  onConfigChange
}: {
  config: DashboardConfig;
  onAddWidget: (type: WidgetType) => void;
  onConfigChange: (config: DashboardConfig) => void;
}) {
  const availableWidgets: Array<{ type: WidgetType; title: string; description: string }> = [
    { type: 'productivity_trend', title: 'Productivity Trend', description: 'Track your productivity over time' },
    { type: 'emotion_overview', title: 'Emotion Overview', description: 'Visualize your emotional patterns' },
    { type: 'moment_stats', title: 'Moment Statistics', description: 'Daily and weekly moment counts' },
    { type: 'sphere_activity', title: 'Sphere Activity', description: 'Activity across your spheres' },
    { type: 'search_analytics', title: 'Search Analytics', description: 'Your search patterns and trends' },
    { type: 'goal_progress', title: 'Goal Progress', description: 'Track your goals and achievements' },
    { type: 'recent_insights', title: 'Recent Insights', description: 'Latest AI-generated insights' },
    { type: 'weekly_summary', title: 'Weekly Summary', description: 'Weekly productivity summary' },
  ];

  return (
    <div className="bg-blue-50 border border-blue-200 rounded-lg p-6 mb-8">
      <h3 className="text-lg font-semibold text-blue-900 mb-4">Add Widgets</h3>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {availableWidgets.map((widget) => (
          <button
            key={widget.type}
            onClick={() => onAddWidget(widget.type)}
            className="p-4 bg-white border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors text-left"
          >
            <h4 className="font-medium text-gray-900">{widget.title}</h4>
            <p className="text-sm text-gray-600 mt-1">{widget.description}</p>
          </button>
        ))}
      </div>

      <div className="flex items-center space-x-4">
        <label className="flex items-center space-x-2">
          <span className="text-sm font-medium text-gray-700">Layout:</span>
          <select
            value={config.layout}
            onChange={(e) => onConfigChange({
              ...config,
              layout: e.target.value as 'grid' | 'masonry',
            })}
            className="px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="grid">Grid</option>
            <option value="masonry">Masonry</option>
          </select>
        </label>

        <label className="flex items-center space-x-2">
          <span className="text-sm font-medium text-gray-700">Refresh Interval:</span>
          <select
            value={config.refreshInterval}
            onChange={(e) => onConfigChange({
              ...config,
              refreshInterval: parseInt(e.target.value),
            })}
            className="px-2 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value={10}>10 seconds</option>
            <option value={30}>30 seconds</option>
            <option value={60}>1 minute</option>
            <option value={300}>5 minutes</option>
          </select>
        </label>
      </div>
    </div>
  );
}

/**
 * Widget Container Component
 */
function WidgetContainer({
  widget,
  data,
  isEditing,
  onUpdate,
  onRemove,
  dragHandleProps
}: {
  widget: Widget;
  data: any;
  isEditing: boolean;
  onUpdate: (updates: Partial<Widget>) => void;
  onRemove: () => void;
  dragHandleProps: any;
}) {
  return (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 relative">
      {/* Widget Header */}
      <div className={`flex items-center justify-between p-4 border-b border-gray-100 ${
        isEditing ? 'bg-gray-50' : ''
      }`}>
        <div className="flex items-center space-x-2">
          {isEditing && (
            <div {...dragHandleProps} className="cursor-move text-gray-400 hover:text-gray-600">
              <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                <path d="M7 2a2 2 0 00-2 2v12a2 2 0 002 2h6a2 2 0 002-2V4a2 2 0 00-2-2H7zM6 6h8v2H6V6zm8 4H6v2h8v-2zm-8 4h8v2H6v-2z" />
              </svg>
            </div>
          )}
          <h3 className="font-semibold text-gray-900">{widget.title}</h3>
        </div>

        {isEditing && (
          <div className="flex items-center space-x-2">
            <WidgetSettings widget={widget} onUpdate={onUpdate} />
            <button
              onClick={onRemove}
              className="p-1 text-red-400 hover:text-red-600 rounded hover:bg-red-50 transition-colors"
              title="Remove widget"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        )}
      </div>

      {/* Widget Content */}
      <div className="p-4">
        <WidgetContent type={widget.type} data={data} settings={widget.settings} />
      </div>
    </div>
  );
}

/**
 * Widget Settings Component
 */
function WidgetSettings({
  widget,
  onUpdate
}: {
  widget: Widget;
  onUpdate: (updates: Partial<Widget>) => void;
}) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="p-1 text-gray-400 hover:text-gray-600 rounded hover:bg-gray-100 transition-colors"
        title="Widget settings"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute right-0 top-full mt-2 w-64 bg-white border border-gray-200 rounded-lg shadow-lg z-10">
          <div className="p-4">
            <h4 className="font-medium text-gray-900 mb-3">Widget Settings</h4>

            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Title
                </label>
                <input
                  type="text"
                  value={widget.title}
                  onChange={(e) => onUpdate({ title: e.target.value })}
                  className="w-full px-3 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Size
                </label>
                <select
                  value={widget.size}
                  onChange={(e) => onUpdate({ size: e.target.value as 'small' | 'medium' | 'large' })}
                  className="w-full px-3 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="small">Small</option>
                  <option value="medium">Medium</option>
                  <option value="large">Large</option>
                </select>
              </div>

              {/* Widget-specific settings */}
              {widget.type === 'productivity_trend' && (
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Period
                  </label>
                  <select
                    value={widget.settings.period || 'week'}
                    onChange={(e) => onUpdate({
                      settings: { ...widget.settings, period: e.target.value }
                    })}
                    className="w-full px-3 py-1 border border-gray-300 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="week">Week</option>
                    <option value="month">Month</option>
                    <option value="quarter">Quarter</option>
                  </select>
                </div>
              )}
            </div>

            <div className="mt-4 flex justify-end">
              <button
                onClick={() => setIsOpen(false)}
                className="px-3 py-1 bg-blue-500 text-white rounded text-sm hover:bg-blue-600 transition-colors"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * Widget Content Renderer
 */
function WidgetContent({
  type,
  data,
  settings
}: {
  type: WidgetType;
  data: any;
  settings: Record<string, any>;
}) {
  // This would render different widget types based on the type
  // For brevity, showing a simplified version

  const renderWidget = () => {
    switch (type) {
      case 'productivity_trend':
        return <ProductivityTrendWidget data={data} settings={settings} />;
      case 'emotion_overview':
        return <EmotionOverviewWidget data={data} settings={settings} />;
      case 'moment_stats':
        return <MomentStatsWidget data={data} settings={settings} />;
      case 'sphere_activity':
        return <SphereActivityWidget data={data} settings={settings} />;
      default:
        return (
          <div className="text-center py-8 text-gray-500">
            <p>Widget type "{type}" not implemented yet</p>
          </div>
        );
    }
  };

  return (
    <div className="min-h-[200px]">
      {renderWidget()}
    </div>
  );
}

// Utility functions
function getWidgetTitle(type: WidgetType): string {
  const titles = {
    productivity_trend: 'Productivity Trend',
    emotion_overview: 'Emotion Overview',
    moment_stats: 'Moment Statistics',
    sphere_activity: 'Sphere Activity',
    search_analytics: 'Search Analytics',
    goal_progress: 'Goal Progress',
    recent_insights: 'Recent Insights',
    weekly_summary: 'Weekly Summary',
  };
  return titles[type] || 'Widget';
}

function getWidgetSizeClass(size: 'small' | 'medium' | 'large'): string {
  const classes = {
    small: 'col-span-1',
    medium: 'col-span-1 md:col-span-1',
    large: 'col-span-1 md:col-span-2 lg:col-span-2',
  };
  return classes[size];
}

// Widget implementations (simplified)
function ProductivityTrendWidget({ data, settings }: any) {
  return <div>Productivity trend chart would go here</div>;
}

function EmotionOverviewWidget({ data, settings }: any) {
  return <div>Emotion overview chart would go here</div>;
}

function MomentStatsWidget({ data, settings }: any) {
  return <div>Moment statistics would go here</div>;
}

function SphereActivityWidget({ data, settings }: any) {
  return <div>Sphere activity chart would go here</div>;
}

function DashboardSkeleton() {
  return (
    <div className="min-h-screen bg-gray-50 animate-pulse">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="h-8 bg-gray-200 rounded w-1/3 mb-8"></div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 6 }, (_, i) => (
            <div key={i} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
              <div className="h-4 bg-gray-200 rounded w-1/2 mb-4"></div>
              <div className="h-32 bg-gray-200 rounded"></div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function EmptyDashboard({ onAddWidget }: { onAddWidget: (type: WidgetType) => void }) {
  return (
    <div className="text-center py-16">
      <div className="text-6xl mb-4">📊</div>
      <h3 className="text-xl font-semibold text-gray-900 mb-2">Your dashboard is empty</h3>
      <p className="text-gray-600 mb-6">Add some widgets to start tracking your insights</p>
      <button
        onClick={() => onAddWidget('productivity_trend')}
        className="bg-blue-500 text-white px-6 py-3 rounded-lg hover:bg-blue-600 transition-colors"
      >
        Add Your First Widget
      </button>
    </div>
  );
}

export default CustomizableDashboard;
