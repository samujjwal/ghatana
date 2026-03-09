/**
 * Dashboard Layout Component
 * 
 * Provides a flexible grid-based layout system with:
 * - Drag-and-drop widget positioning
 * - Responsive grid sizing
 * - Widget resize support
 * - Layout persistence
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, useTheme } from '@mui/material';
import type { DashboardLayout as DashboardLayoutType, Widget } from '../types';

export interface DashboardLayoutProps {
  layout: DashboardLayoutType;
  onLayoutChange?: (layout: DashboardLayoutType) => void;
  editable?: boolean;
  children?: React.ReactNode;
}

/**
 * Dashboard layout component with drag-and-drop support
 */
export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
  layout,
  onLayoutChange,
  editable = false,
  children,
}) => {
  const theme = useTheme();
  const [draggingWidget, setDraggingWidget] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });

  // Calculate grid dimensions
  const gridStyle = useMemo(() => ({
    display: 'grid',
    gridTemplateColumns: `repeat(${layout.columns}, 1fr)`,
    gap: `${layout.gap}px`,
    width: '100%',
    minHeight: '100vh',
    padding: theme.spacing(2),
    backgroundColor: theme.palette.background.default,
  }), [layout.columns, layout.gap, theme]);

  // Handle widget drag start
  const handleDragStart = useCallback(
    (widgetId: string, event: React.DragEvent) => {
      if (!editable) return;

      const widget = layout.widgets.find(w => w.id === widgetId);
      if (!widget) return;

      setDraggingWidget(widgetId);
      
      // Calculate offset from widget corner
      const rect = (event.target as HTMLElement).getBoundingClientRect();
      setDragOffset({
        x: event.clientX - rect.left,
        y: event.clientY - rect.top,
      });

      event.dataTransfer.effectAllowed = 'move';
    },
    [editable, layout.widgets]
  );

  // Handle widget drag over
  const handleDragOver = useCallback((event: React.DragEvent) => {
    if (!editable || !draggingWidget) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, [editable, draggingWidget]);

  // Handle widget drop
  const handleDrop = useCallback(
    (event: React.DragEvent) => {
      if (!editable || !draggingWidget || !onLayoutChange) return;

      event.preventDefault();

      const containerRect = (event.currentTarget as HTMLElement).getBoundingClientRect();
      const columnWidth = containerRect.width / layout.columns;

      // Calculate new grid position
      const x = Math.floor((event.clientX - containerRect.left - dragOffset.x) / columnWidth);
      const y = Math.floor((event.clientY - containerRect.top - dragOffset.y) / layout.rowHeight);

      // Update widget position
      const updatedWidgets = layout.widgets.map(widget =>
        widget.id === draggingWidget
          ? {
              ...widget,
              position: {
                x: Math.max(0, Math.min(x, layout.columns - widget.size.width)),
                y: Math.max(0, y),
              },
            }
          : widget
      );

      onLayoutChange({
        ...layout,
        widgets: updatedWidgets,
      });

      setDraggingWidget(null);
    },
    [editable, draggingWidget, onLayoutChange, layout, dragOffset]
  );

  // Handle drag end
  const handleDragEnd = useCallback(() => {
    setDraggingWidget(null);
  }, []);

  // Render widget
  const renderWidget = useCallback(
    (widget: Widget) => {
      const widgetStyle: React.CSSProperties = {
        gridColumn: `${widget.position.x + 1} / span ${widget.size.width}`,
        gridRow: `${widget.position.y + 1} / span ${widget.size.height}`,
        minWidth: widget.size.minWidth,
        minHeight: widget.size.minHeight,
        maxWidth: widget.size.maxWidth,
        maxHeight: widget.size.maxHeight,
        backgroundColor: theme.palette.background.paper,
        borderRadius: theme.shape.borderRadius,
        padding: theme.spacing(2),
        boxShadow: theme.shadows[1],
        cursor: editable ? 'move' : 'default',
        opacity: draggingWidget === widget.id ? 0.5 : 1,
        transition: 'opacity 0.2s',
      };

      return (
        <Box
          key={widget.id}
          style={widgetStyle}
          draggable={editable}
          onDragStart={(e: React.DragEvent<HTMLDivElement>) => handleDragStart(widget.id, e)}
          onDragEnd={handleDragEnd}
        >
          {children}
        </Box>
      );
    },
    [theme, editable, draggingWidget, handleDragStart, handleDragEnd, children]
  );

  return (
    <Box
      sx={gridStyle}
      onDragOver={handleDragOver}
      onDrop={handleDrop}
    >
      {layout.widgets.map(renderWidget)}
    </Box>
  );
};

DashboardLayout.displayName = 'DashboardLayout';

export default DashboardLayout;
