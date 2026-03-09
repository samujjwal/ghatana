/**
 * EventLog - Real-time event logging for debugging
 *
 * @module canvas/devtools
 */

import { useState, useCallback, useEffect, useRef } from 'react';

import type { CanvasEvent } from '../types/canvas-document';

/**
 *
 */
export interface EventLogProps {
  /**
   * Show the event log panel
   * @default false
   */
  visible?: boolean;

  /**
   * Position of the event log
   * @default 'bottom-left'
   */
  position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

  /**
   * Maximum number of events to keep
   * @default 100
   */
  maxEvents?: number;

  /**
   * Initial width of the panel in pixels
   * @default 400
   */
  width?: number;

  /**
   * Initial height of the panel in pixels
   * @default 300
   */
  height?: number;

  /**
   * Callback when visibility changes
   */
  onVisibilityChange?: (visible: boolean) => void;

  /**
   * Event listener to subscribe to canvas events
   */
  onSubscribe?: (callback: (event: CanvasEvent) => void) => () => void;
}

/**
 *
 */
interface LogEntry extends CanvasEvent {
  id: string;
}

/**
 * EventLog component for debugging canvas events
 *
 * Displays real-time stream of canvas events with filtering, search,
 * and export capabilities. Only use in development.
 *
 * @example
 * ```tsx
 * import { EventLog } from '@ghatana/yappc-canvas/devtools';
 *
 * function App() {
 *   const handleSubscribe = (callback) => {
 *     // Subscribe to canvas events
 *     return canvasAPI.on('*', callback);
 *   };
 *
 *   return (
 *     <>
 *       <Canvas />
 *       {process.env.NODE_ENV === 'development' && (
 *         <EventLog
 *           visible={true}
 *           position="bottom-left"
 *           onSubscribe={handleSubscribe}
 *         />
 *       )}
 *     </>
 *   );
 * }
 * ```
 */
export function EventLog({
  visible = false,
  position = 'bottom-left',
  maxEvents = 100,
  width = 400,
  height = 300,
  onVisibilityChange,
  onSubscribe,
}: EventLogProps) {
  const [events, setEvents] = useState<LogEntry[]>([]);
  const [filter, setFilter] = useState('');
  const [isPaused, setIsPaused] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);
  const autoScrollRef = useRef(true);

  // Subscribe to canvas events
  useEffect(() => {
    if (!onSubscribe || !visible) return;

    const handleEvent = (event: CanvasEvent) => {
      if (isPaused) return;

      setEvents((prev) => {
        const newEvent: LogEntry = {
          ...event,
          id: `${event.type}-${Date.now()}-${Math.random()}`,
        };

        const updated = [newEvent, ...prev];
        return updated.slice(0, maxEvents);
      });
    };

    const unsubscribe = onSubscribe(handleEvent);
    return unsubscribe;
  }, [onSubscribe, visible, isPaused, maxEvents]);

  // Auto-scroll to latest event
  useEffect(() => {
    if (autoScrollRef.current && logRef.current) {
      logRef.current.scrollTop = 0;
    }
  }, [events]);

  const handleClose = useCallback(() => {
    onVisibilityChange?.(false);
  }, [onVisibilityChange]);

  const handleToggleCollapse = useCallback(() => {
    setIsCollapsed((prev) => !prev);
  }, []);

  const handleClear = useCallback(() => {
    setEvents([]);
  }, []);

  const handleTogglePause = useCallback(() => {
    setIsPaused((prev) => !prev);
  }, []);

  const handleExport = useCallback(() => {
    const data = JSON.stringify(events, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `canvas-events-${new Date().toISOString()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }, [events]);

  if (!visible) {
    return null;
  }

  const filteredEvents = events.filter((event) => {
    if (!filter) return true;
    const searchText = filter.toLowerCase();
    return (
      event.type.toLowerCase().includes(searchText) ||
      event.elementId?.toLowerCase().includes(searchText) ||
      JSON.stringify(event.data).toLowerCase().includes(searchText)
    );
  });

  const positionStyles: React.CSSProperties = {
    position: 'fixed',
    zIndex: 99999,
    backgroundColor: '#1e1e1e',
    color: '#d4d4d4',
    border: '1px solid #3e3e3e',
    borderRadius: '8px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.4)',
    fontFamily: 'monospace',
    fontSize: '11px',
    ...(position.includes('top') ? { top: '20px' } : { bottom: '20px' }),
    ...(position.includes('left') ? { left: '20px' } : { right: '20px' }),
    width: isCollapsed ? '200px' : `${width}px`,
    height: isCollapsed ? 'auto' : `${height}px`,
    display: 'flex',
    flexDirection: 'column',
  };

  return (
    <div style={positionStyles}>
      {/* Header */}
      <div
        style={{
          padding: '8px 12px',
          borderBottom: isCollapsed ? 'none' : '1px solid #3e3e3e',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#252526',
          borderTopLeftRadius: '8px',
          borderTopRightRadius: '8px',
        }}
      >
        <span style={{ fontWeight: 'bold', fontSize: '12px' }}>
          📋 Event Log
          {!isCollapsed && <span style={{ marginLeft: '8px', opacity: 0.6 }}>({filteredEvents.length})</span>}
        </span>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            onClick={handleTogglePause}
            style={{
              background: isPaused ? '#264f78' : 'none',
              border: '1px solid',
              borderColor: isPaused ? '#007acc' : 'transparent',
              color: '#d4d4d4',
              cursor: 'pointer',
              padding: '2px 8px',
              fontSize: '10px',
              borderRadius: '4px',
            }}
            title={isPaused ? 'Resume' : 'Pause'}
          >
            {isPaused ? '▶' : '⏸'}
          </button>
          <button
            onClick={handleToggleCollapse}
            style={{
              background: 'none',
              border: 'none',
              color: '#d4d4d4',
              cursor: 'pointer',
              padding: '2px 4px',
              fontSize: '14px',
            }}
            title={isCollapsed ? 'Expand' : 'Collapse'}
          >
            {isCollapsed ? '⬆' : '⬇'}
          </button>
          <button
            onClick={handleClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#d4d4d4',
              cursor: 'pointer',
              padding: '2px 4px',
              fontSize: '14px',
            }}
            title="Close"
          >
            ✕
          </button>
        </div>
      </div>

      {/* Collapsed state */}
      {isCollapsed && (
        <div style={{ padding: '8px 12px', fontSize: '10px', color: '#888' }}>
          Click ⬆ to expand
        </div>
      )}

      {/* Controls */}
      {!isCollapsed && (
        <>
          <div
            style={{
              padding: '8px 12px',
              borderBottom: '1px solid #3e3e3e',
              backgroundColor: '#2d2d30',
              display: 'flex',
              gap: '8px',
              alignItems: 'center',
            }}
          >
            <input
              type="text"
              placeholder="Filter events..."
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              style={{
                flex: 1,
                background: '#1e1e1e',
                border: '1px solid #3e3e3e',
                borderRadius: '4px',
                color: '#d4d4d4',
                padding: '4px 8px',
                fontSize: '10px',
                outline: 'none',
              }}
            />
            <button
              onClick={handleClear}
              style={{
                background: 'none',
                border: '1px solid #3e3e3e',
                borderRadius: '4px',
                color: '#d4d4d4',
                cursor: 'pointer',
                padding: '4px 8px',
                fontSize: '10px',
              }}
              title="Clear all events"
            >
              Clear
            </button>
            <button
              onClick={handleExport}
              style={{
                background: 'none',
                border: '1px solid #3e3e3e',
                borderRadius: '4px',
                color: '#d4d4d4',
                cursor: 'pointer',
                padding: '4px 8px',
                fontSize: '10px',
              }}
              title="Export as JSON"
            >
              Export
            </button>
          </div>

          {/* Event List */}
          <div
            ref={logRef}
            style={{
              flex: 1,
              overflow: 'auto',
              padding: '8px',
            }}
          >
            {filteredEvents.length === 0 ? (
              <div style={{ color: '#888', textAlign: 'center', padding: '20px' }}>
                {events.length === 0 ? 'No events logged yet' : 'No events match filter'}
              </div>
            ) : (
              filteredEvents.map((event) => (
                <EventItem key={event.id} event={event} />
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}

/**
 *
 */
function EventItem({ event }: { event: LogEntry }) {
  const [isExpanded, setIsExpanded] = useState(false);

  const getEventColor = (type: string): string => {
    if (type.includes('created') || type.includes('added')) return '#4ec9b0';
    if (type.includes('updated') || type.includes('changed')) return '#dcdcaa';
    if (type.includes('deleted') || type.includes('removed')) return '#f48771';
    if (type.includes('selected')) return '#569cd6';
    if (type.includes('error')) return '#f14c4c';
    return '#9cdcfe';
  };

  const eventColor = getEventColor(event.type);
  const timeStr = new Date(event.timestamp).toLocaleTimeString();

  return (
    <div
      style={{
        marginBottom: '6px',
        padding: '8px',
        backgroundColor: '#2d2d30',
        borderRadius: '4px',
        borderLeft: `3px solid ${eventColor}`,
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
        <span style={{ fontWeight: 'bold', color: eventColor }}>{event.type}</span>
        <span style={{ fontSize: '9px', color: '#888' }}>{timeStr}</span>
      </div>

      {event.elementId && (
        <div style={{ fontSize: '9px', color: '#9cdcfe', marginBottom: '4px' }}>
          Element: {event.elementId}
        </div>
      )}

      {event.userId && (
        <div style={{ fontSize: '9px', color: '#c586c0', marginBottom: '4px' }}>
          User: {event.userId}
        </div>
      )}

      <button
        onClick={() => setIsExpanded(!isExpanded)}
        style={{
          background: 'none',
          border: 'none',
          color: '#569cd6',
          cursor: 'pointer',
          padding: 0,
          fontSize: '9px',
          textDecoration: 'underline',
          marginTop: '4px',
        }}
      >
        {isExpanded ? 'Hide' : 'Show'} Data
      </button>

      {isExpanded && (
        <pre
          style={{
            marginTop: '6px',
            padding: '6px',
            backgroundColor: '#1e1e1e',
            borderRadius: '4px',
            overflow: 'auto',
            maxHeight: '150px',
            fontSize: '9px',
            color: '#ce9178',
          }}
        >
          {JSON.stringify(event.data, null, 2)}
        </pre>
      )}
    </div>
  );
}
