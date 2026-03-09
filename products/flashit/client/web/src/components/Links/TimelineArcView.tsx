/**
 * TimelineArcView - Visualize temporal arcs and moment connections over time
 * Phase 1 Week 11: Linking & Temporal Arcs (Day 54)
 */

import { useMemo, useState } from 'react';
import { useLinkTimeline, useLinkGraph, useLinkStats } from '../../hooks/use-api';
import { format, parseISO, differenceInDays, startOfMonth, endOfMonth, eachDayOfInterval, subMonths } from 'date-fns';
import { Calendar, GitBranch, TrendingUp, Loader2, ChevronLeft, ChevronRight, ZoomIn, ZoomOut } from 'lucide-react';

export interface TimelineArcViewProps {
  sphereId: string;
  onMomentClick?: (momentId: string) => void;
}

interface TimelineNode {
  id: string;
  date: Date;
  content: string;
  linkCount: number;
}

interface TimelineArc {
  id: string;
  sourceId: string;
  targetId: string;
  linkType: string;
}

// Link type colors for arcs
const ARC_COLORS: Record<string, string> = {
  related: '#6B7280',
  follows: '#3B82F6',
  precedes: '#8B5CF6',
  references: '#10B981',
  causes: '#EF4444',
  similar: '#F59E0B',
  contradicts: '#F97316',
  elaborates: '#6366F1',
  summarizes: '#14B8A6',
};

export default function TimelineArcView({ sphereId, onMomentClick }: TimelineArcViewProps) {
  const [viewMonth, setViewMonth] = useState(new Date());
  const [zoomLevel, setZoomLevel] = useState<'month' | 'week'>('month');
  const [selectedLinkTypes, setSelectedLinkTypes] = useState<string[]>([]);

  const startDate = startOfMonth(viewMonth).toISOString();
  const endDate = endOfMonth(viewMonth).toISOString();

  // Fetch timeline data (used for potential future enhancements)
  const { isLoading: isLoadingTimeline } = useLinkTimeline(sphereId, {
    startDate,
    endDate,
    groupBy: zoomLevel === 'month' ? 'day' : 'day',
  });

  // Fetch graph data for arc visualization
  const { data: graphData, isLoading: isLoadingGraph } = useLinkGraph(sphereId, {
    depth: 2,
    limit: 100,
  });

  // Fetch stats for overview
  const { data: statsData } = useLinkStats(sphereId);

  // Process data for visualization
  const { nodes, arcs, days } = useMemo(() => {
    if (!graphData) {
      return { nodes: [], arcs: [], days: [] };
    }

    const nodeMap = new Map<string, TimelineNode>();
    const arcList: TimelineArc[] = [];

    // Process nodes from graph data
    graphData.nodes?.forEach((node: { id: string; contentText: string; capturedAt: string; linkCount: number }) => {
      nodeMap.set(node.id, {
        id: node.id,
        date: parseISO(node.capturedAt),
        content: node.contentText,
        linkCount: node.linkCount || 0,
      });
    });

    // Process edges/arcs
    graphData.edges?.forEach((edge: { id: string; source: string; target: string; linkType: string }) => {
      if (selectedLinkTypes.length === 0 || selectedLinkTypes.includes(edge.linkType)) {
        arcList.push({
          id: edge.id,
          sourceId: edge.source,
          targetId: edge.target,
          linkType: edge.linkType,
        });
      }
    });

    // Generate days in the current view
    const daysInView = eachDayOfInterval({
      start: startOfMonth(viewMonth),
      end: endOfMonth(viewMonth),
    });

    return {
      nodes: Array.from(nodeMap.values()),
      arcs: arcList,
      days: daysInView,
    };
  }, [graphData, viewMonth, selectedLinkTypes]);

  // Group nodes by date
  const nodesByDate = useMemo(() => {
    const grouped = new Map<string, TimelineNode[]>();
    nodes.forEach((node) => {
      const dateKey = format(node.date, 'yyyy-MM-dd');
      if (!grouped.has(dateKey)) {
        grouped.set(dateKey, []);
      }
      grouped.get(dateKey)!.push(node);
    });
    return grouped;
  }, [nodes]);

  const isLoading = isLoadingTimeline || isLoadingGraph;

  const handlePrevMonth = () => setViewMonth((prev) => subMonths(prev, 1));
  const handleNextMonth = () => setViewMonth((prev) => {
    const next = new Date(prev);
    next.setMonth(next.getMonth() + 1);
    return next;
  });

  const linkTypes = [...new Set(graphData?.edges?.map((e: { linkType: string }) => e.linkType) || [])] as string[];

  const toggleLinkType = (type: string) => {
    setSelectedLinkTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    );
  };

  return (
    <div className="rounded-lg border border-gray-200 bg-white">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-gray-200 px-4 py-3">
        <div className="flex items-center gap-3">
          <GitBranch className="h-5 w-5 text-primary-600" />
          <h3 className="font-semibold text-gray-900">Temporal Arc Timeline</h3>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handlePrevMonth}
            className="rounded-lg p-2 text-gray-500 hover:bg-gray-100"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span className="min-w-[120px] text-center font-medium text-gray-700">
            {format(viewMonth, 'MMMM yyyy')}
          </span>
          <button
            onClick={handleNextMonth}
            className="rounded-lg p-2 text-gray-500 hover:bg-gray-100"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
          <div className="ml-4 flex items-center gap-1 rounded-lg border border-gray-200 p-1">
            <button
              onClick={() => setZoomLevel('week')}
              className={`rounded px-2 py-1 text-xs ${
                zoomLevel === 'week' ? 'bg-gray-100 text-gray-800' : 'text-gray-500'
              }`}
            >
              <ZoomIn className="h-3 w-3" />
            </button>
            <button
              onClick={() => setZoomLevel('month')}
              className={`rounded px-2 py-1 text-xs ${
                zoomLevel === 'month' ? 'bg-gray-100 text-gray-800' : 'text-gray-500'
              }`}
            >
              <ZoomOut className="h-3 w-3" />
            </button>
          </div>
        </div>
      </div>

      {/* Stats Bar */}
      {statsData && (
        <div className="flex items-center gap-6 border-b border-gray-200 bg-gray-50 px-4 py-2">
          <div className="flex items-center gap-2">
            <Calendar className="h-4 w-4 text-gray-400" />
            <span className="text-sm text-gray-600">
              <strong>{statsData.totalLinks || 0}</strong> total links
            </span>
          </div>
          <div className="flex items-center gap-2">
            <GitBranch className="h-4 w-4 text-gray-400" />
            <span className="text-sm text-gray-600">
              <strong>{statsData.linkedMoments || 0}</strong> linked moments
            </span>
          </div>
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-gray-400" />
            <span className="text-sm text-gray-600">
              <strong>{statsData.averageLinksPerMoment?.toFixed(1) || 0}</strong> avg links/moment
            </span>
          </div>
        </div>
      )}

      {/* Link Type Filters */}
      {linkTypes.length > 0 && (
        <div className="flex items-center gap-2 border-b border-gray-200 px-4 py-2">
          <span className="text-xs text-gray-500">Filter by type:</span>
          {linkTypes.map((type) => (
            <button
              key={type}
              onClick={() => toggleLinkType(type)}
              className={`rounded-full px-2 py-0.5 text-xs font-medium transition-colors ${
                selectedLinkTypes.length === 0 || selectedLinkTypes.includes(type)
                  ? 'opacity-100'
                  : 'opacity-40'
              }`}
              style={{
                backgroundColor: `${ARC_COLORS[type] || '#6B7280'}20`,
                color: ARC_COLORS[type] || '#6B7280',
              }}
            >
              {type}
            </button>
          ))}
          {selectedLinkTypes.length > 0 && (
            <button
              onClick={() => setSelectedLinkTypes([])}
              className="text-xs text-gray-400 hover:text-gray-600"
            >
              Clear
            </button>
          )}
        </div>
      )}

      {/* Timeline Content */}
      <div className="p-4">
        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : nodes.length === 0 ? (
          <div className="py-12 text-center">
            <GitBranch className="mx-auto h-12 w-12 text-gray-300" />
            <p className="mt-3 text-gray-500">No linked moments in this period</p>
            <p className="mt-1 text-sm text-gray-400">
              Create links between moments to see them on the timeline
            </p>
          </div>
        ) : (
          <div className="relative">
            {/* Timeline Grid */}
            <div className="grid grid-cols-7 gap-1">
              {/* Day Headers */}
              {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map((day) => (
                <div
                  key={day}
                  className="py-1 text-center text-xs font-medium text-gray-500"
                >
                  {day}
                </div>
              ))}

              {/* Calendar Days */}
              {days.map((day) => {
                const dateKey = format(day, 'yyyy-MM-dd');
                const dayNodes = nodesByDate.get(dateKey) || [];
                const isToday = format(new Date(), 'yyyy-MM-dd') === dateKey;
                const dayOfWeek = day.getDay();

                // Add empty cells for first row alignment
                const emptyCells = day.getDate() === 1 ? Array(dayOfWeek).fill(null) : [];

                return (
                  <>
                    {emptyCells.map((_, i) => (
                      <div key={`empty-${i}`} className="h-20" />
                    ))}
                    <div
                      key={dateKey}
                      className={`relative h-20 rounded-lg border p-1 ${
                        isToday
                          ? 'border-primary-300 bg-primary-50'
                          : dayNodes.length > 0
                          ? 'border-gray-300 bg-gray-50'
                          : 'border-gray-100'
                      }`}
                    >
                      <div className="text-right text-xs text-gray-400">
                        {format(day, 'd')}
                      </div>
                      {dayNodes.length > 0 && (
                        <div className="mt-1 space-y-0.5">
                          {dayNodes.slice(0, 2).map((node) => (
                            <button
                              key={node.id}
                              onClick={() => onMomentClick?.(node.id)}
                              className="block w-full truncate rounded bg-primary-100 px-1 py-0.5 text-left text-xs text-primary-700 hover:bg-primary-200"
                              title={node.content}
                            >
                              {node.content.substring(0, 20)}...
                            </button>
                          ))}
                          {dayNodes.length > 2 && (
                            <div className="text-center text-xs text-gray-400">
                              +{dayNodes.length - 2} more
                            </div>
                          )}
                        </div>
                      )}
                      {/* Link indicator */}
                      {dayNodes.some((n) => n.linkCount > 0) && (
                        <div className="absolute bottom-1 right-1">
                          <div className="flex items-center gap-0.5">
                            <GitBranch className="h-3 w-3 text-primary-500" />
                            <span className="text-xs text-primary-600">
                              {dayNodes.reduce((sum, n) => sum + n.linkCount, 0)}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                  </>
                );
              })}
            </div>

            {/* Arc SVG Overlay - Simplified visualization */}
            {arcs.length > 0 && (
              <div className="mt-6 rounded-lg border border-gray-200 bg-gray-50 p-4">
                <h4 className="mb-3 text-sm font-medium text-gray-700">
                  Active Arcs This Month ({arcs.length})
                </h4>
                <div className="flex flex-wrap gap-2">
                  {arcs.slice(0, 10).map((arc) => {
                    const sourceNode = nodes.find((n) => n.id === arc.sourceId);
                    const targetNode = nodes.find((n) => n.id === arc.targetId);

                    if (!sourceNode || !targetNode) return null;

                    const dayDiff = Math.abs(differenceInDays(sourceNode.date, targetNode.date));

                    return (
                      <div
                        key={arc.id}
                        className="flex items-center gap-2 rounded-lg border bg-white px-3 py-2"
                        style={{ borderColor: ARC_COLORS[arc.linkType] || '#6B7280' }}
                      >
                        <button
                          onClick={() => onMomentClick?.(sourceNode.id)}
                          className="text-xs text-gray-700 hover:text-primary-600"
                        >
                          {format(sourceNode.date, 'MMM d')}
                        </button>
                        <span
                          className="rounded-full px-2 py-0.5 text-xs"
                          style={{
                            backgroundColor: `${ARC_COLORS[arc.linkType] || '#6B7280'}20`,
                            color: ARC_COLORS[arc.linkType] || '#6B7280',
                          }}
                        >
                          {arc.linkType}
                        </span>
                        <button
                          onClick={() => onMomentClick?.(targetNode.id)}
                          className="text-xs text-gray-700 hover:text-primary-600"
                        >
                          {format(targetNode.date, 'MMM d')}
                        </button>
                        <span className="text-xs text-gray-400">
                          ({dayDiff}d span)
                        </span>
                      </div>
                    );
                  })}
                  {arcs.length > 10 && (
                    <div className="flex items-center rounded-lg border border-dashed border-gray-300 px-3 py-2 text-xs text-gray-500">
                      +{arcs.length - 10} more arcs
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
