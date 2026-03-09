/**
 * DevInspector - Real-time canvas state inspection component
 *
 * @module canvas/devtools
 */

import { useAtomValue } from 'jotai';
import { useState, useCallback } from 'react';

import {
  canvasDocumentAtom,
  canvasSelectionAtom,
  canvasViewportAtom,
  canvasHistoryAtom,
  canvasUIStateAtom,
  canvasPerformanceAtom,
} from '../state';

import type { CanvasDocument, CanvasSelection, CanvasViewport, CanvasHistoryEntry, CanvasUIState, CanvasPerformanceMetrics } from '../types/canvas-document';

/**
 *
 */
export interface DevInspectorProps {
  /**
   * Show the inspector panel
   * @default false
   */
  visible?: boolean;

  /**
   * Position of the inspector
   * @default 'bottom-right'
   */
  position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

  /**
   * Initial width of the panel in pixels
   * @default 400
   */
  width?: number;

  /**
   * Initial height of the panel in pixels
   * @default 600
   */
  height?: number;

  /**
   * Callback when visibility changes
   */
  onVisibilityChange?: (visible: boolean) => void;
}

/**
 *
 */
type InspectorTab = 'document' | 'selection' | 'viewport' | 'history' | 'ui' | 'performance';

/**
 * DevInspector component for debugging canvas state
 *
 * Displays real-time canvas state including document, selection, viewport,
 * history, UI state, and performance metrics. Only use in development.
 *
 * @example
 * ```tsx
 * import { DevInspector } from '@ghatana/yappc-canvas/devtools';
 *
 * function App() {
 *   return (
 *     <>
 *       <Canvas />
 *       {process.env.NODE_ENV === 'development' && (
 *         <DevInspector visible={true} position="bottom-right" />
 *       )}
 *     </>
 *   );
 * }
 * ```
 */
export function DevInspector({
  visible = false,
  position = 'bottom-right',
  width = 400,
  height = 600,
  onVisibilityChange,
}: DevInspectorProps) {
  const [activeTab, setActiveTab] = useState<InspectorTab>('document');
  const [isCollapsed, setIsCollapsed] = useState(false);

  // Subscribe to all canvas atoms
  const document = useAtomValue(canvasDocumentAtom);
  const selection = useAtomValue(canvasSelectionAtom);
  const viewport = useAtomValue(canvasViewportAtom);
  const history = useAtomValue(canvasHistoryAtom);
  const uiState = useAtomValue(canvasUIStateAtom);
  const performance = useAtomValue(canvasPerformanceAtom);

  const handleClose = useCallback(() => {
    onVisibilityChange?.(false);
  }, [onVisibilityChange]);

  const handleToggleCollapse = useCallback(() => {
    setIsCollapsed((prev) => !prev);
  }, []);

  if (!visible) {
    return null;
  }

  const positionStyles: React.CSSProperties = {
    position: 'fixed',
    zIndex: 99999,
    backgroundColor: '#1e1e1e',
    color: '#d4d4d4',
    border: '1px solid #3e3e3e',
    borderRadius: '8px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.4)',
    fontFamily: 'monospace',
    fontSize: '12px',
    ...(position.includes('top') ? { top: '20px' } : { bottom: '20px' }),
    ...(position.includes('left') ? { left: '20px' } : { right: '20px' }),
    width: isCollapsed ? '200px' : `${width}px`,
    height: isCollapsed ? 'auto' : `${height}px`,
    display: 'flex',
    flexDirection: 'column',
  };

  const tabs: { id: InspectorTab; label: string; count?: number }[] = [
    { id: 'document', label: 'Document', count: Object.keys(document?.elements || {}).length },
    { id: 'selection', label: 'Selection', count: selection?.selectedIds.length || 0 },
    { id: 'viewport', label: 'Viewport' },
    { id: 'history', label: 'History', count: history?.entries.length || 0 },
    { id: 'ui', label: 'UI' },
    { id: 'performance', label: 'Performance' },
  ];

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
        <span style={{ fontWeight: 'bold', fontSize: '13px' }}>🔍 Canvas DevTools</span>
        <div style={{ display: 'flex', gap: '8px' }}>
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
        <div style={{ padding: '8px 12px', fontSize: '11px', color: '#888' }}>
          Click ⬆ to expand
        </div>
      )}

      {/* Tabs */}
      {!isCollapsed && (
        <>
          <div
            style={{
              display: 'flex',
              borderBottom: '1px solid #3e3e3e',
              backgroundColor: '#2d2d30',
              overflowX: 'auto',
            }}
          >
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  background: activeTab === tab.id ? '#1e1e1e' : 'transparent',
                  border: 'none',
                  color: activeTab === tab.id ? '#fff' : '#888',
                  cursor: 'pointer',
                  padding: '8px 12px',
                  fontSize: '11px',
                  whiteSpace: 'nowrap',
                  borderBottom: activeTab === tab.id ? '2px solid #007acc' : 'none',
                }}
              >
                {tab.label}
                {tab.count !== undefined && <span style={{ marginLeft: '4px', opacity: 0.6 }}>({tab.count})</span>}
              </button>
            ))}
          </div>

          {/* Content */}
          <div
            style={{
              flex: 1,
              overflow: 'auto',
              padding: '12px',
            }}
          >
            {activeTab === 'document' && <DocumentPanel document={document} />}
            {activeTab === 'selection' && <SelectionPanel selection={selection} document={document} />}
            {activeTab === 'viewport' && <ViewportPanel viewport={viewport} />}
            {activeTab === 'history' && <HistoryPanel history={history} />}
            {activeTab === 'ui' && <UIPanel uiState={uiState} />}
            {activeTab === 'performance' && <PerformancePanel performance={performance} />}
          </div>
        </>
      )}
    </div>
  );
}

// Panel Components

/**
 *
 */
function DocumentPanel({ document }: { document: CanvasDocument | null }) {
  if (!document) {
    return <div style={{ color: '#888' }}>No document loaded</div>;
  }

  const elements = Object.values(document.elements);
  const nodes = elements.filter((el) => el.type === 'node');
  const edges = elements.filter((el) => el.type === 'edge');
  const groups = elements.filter((el) => el.type === 'group');

  return (
    <div>
      <Section title="Overview">
        <KeyValue label="ID" value={document.id} />
        <KeyValue label="Title" value={document.title || '(untitled)'} />
        <KeyValue label="Version" value={document.version} />
        <KeyValue label="Elements" value={`${elements.length} (${nodes.length}N, ${edges.length}E, ${groups.length}G)`} />
        <KeyValue label="Modified" value={new Date(document.updatedAt).toLocaleString()} />
      </Section>

      <Section title="Elements">
        <div style={{ maxHeight: '300px', overflow: 'auto' }}>
          {elements.map((el) => (
            <div
              key={el.id}
              style={{
                padding: '6px',
                marginBottom: '4px',
                backgroundColor: '#2d2d30',
                borderRadius: '4px',
                fontSize: '10px',
              }}
            >
              <div style={{ fontWeight: 'bold', color: '#4ec9b0' }}>{el.type.toUpperCase()}</div>
              <div style={{ color: '#9cdcfe' }}>{el.id}</div>
              {el.type === 'node' && (el as unknown).data?.label && <div style={{ color: '#ce9178' }}>"{(el as unknown).data.label}"</div>}
            </div>
          ))}
        </div>
      </Section>
    </div>
  );
}

/**
 *
 */
function SelectionPanel({ selection, document }: { selection: CanvasSelection | null; document: CanvasDocument | null }) {
  if (!selection || selection.selectedIds.length === 0) {
    return <div style={{ color: '#888' }}>No selection</div>;
  }

  const selectedElements = selection.selectedIds
    .map((id) => document?.elements[id])
    .filter(Boolean);

  return (
    <div>
      <Section title="Selection">
        <KeyValue label="Count" value={selection.selectedIds.length} />
        <KeyValue label="Focused" value={selection.focusedId || '(none)'} />
        <KeyValue label="Hovered" value={selection.hoveredId || '(none)'} />
      </Section>

      <Section title="Selected Elements">
        {selectedElements.map((el) => (
          <div
            key={el!.id}
            style={{
              padding: '6px',
              marginBottom: '4px',
              backgroundColor: '#2d2d30',
              borderRadius: '4px',
              fontSize: '10px',
            }}
          >
            <div style={{ fontWeight: 'bold', color: '#4ec9b0' }}>{el!.type.toUpperCase()}</div>
            <div style={{ color: '#9cdcfe' }}>{el!.id}</div>
            <CodeBlock data={el} />
          </div>
        ))}
      </Section>
    </div>
  );
}

/**
 *
 */
function ViewportPanel({ viewport }: { viewport: CanvasViewport | null }) {
  if (!viewport) {
    return <div style={{ color: '#888' }}>No viewport state</div>;
  }

  return (
    <div>
      <Section title="Center">
        <KeyValue label="X" value={viewport.center.x.toFixed(2)} />
        <KeyValue label="Y" value={viewport.center.y.toFixed(2)} />
      </Section>

      <Section title="Zoom">
        <KeyValue label="Zoom" value={`${(viewport.zoom * 100).toFixed(0)}%`} />
      </Section>

      <Section title="Bounds">
        <KeyValue label="X" value={viewport.bounds.x.toFixed(2)} />
        <KeyValue label="Y" value={viewport.bounds.y.toFixed(2)} />
        <KeyValue label="Width" value={viewport.bounds.width} />
        <KeyValue label="Height" value={viewport.bounds.height} />
      </Section>
    </div>
  );
}

/**
 *
 */
function HistoryPanel({ history }: { history: { entries: CanvasHistoryEntry[]; currentIndex: number } | null }) {
  if (!history || history.entries.length === 0) {
    return <div style={{ color: '#888' }}>No history</div>;
  }

  return (
    <div>
      <Section title="History">
        <KeyValue label="Entries" value={history.entries.length} />
        <KeyValue label="Current" value={history.currentIndex} />
        <KeyValue label="Can Undo" value={history.currentIndex > 0 ? 'Yes' : 'No'} />
        <KeyValue label="Can Redo" value={history.currentIndex < history.entries.length - 1 ? 'Yes' : 'No'} />
      </Section>

      <Section title="Entries">
        <div style={{ maxHeight: '400px', overflow: 'auto' }}>
          {history.entries.map((entry, index) => (
            <div
              key={entry.id}
              style={{
                padding: '6px',
                marginBottom: '4px',
                backgroundColor: index === history.currentIndex ? '#264f78' : '#2d2d30',
                borderRadius: '4px',
                fontSize: '10px',
                borderLeft: index === history.currentIndex ? '3px solid #007acc' : 'none',
              }}
            >
              <div style={{ fontWeight: 'bold', color: '#dcdcaa' }}>{entry.action}</div>
              <div style={{ color: '#888', fontSize: '9px' }}>
                {new Date(entry.timestamp).toLocaleTimeString()}
              </div>
              <div style={{ color: '#569cd6', fontSize: '9px' }}>
                {entry.elementIds.length} element{entry.elementIds.length !== 1 ? 's' : ''}
              </div>
            </div>
          ))}
        </div>
      </Section>
    </div>
  );
}

/**
 *
 */
function UIPanel({ uiState }: { uiState: CanvasUIState | null }) {
  if (!uiState) {
    return <div style={{ color: '#888' }}>No UI state</div>;
  }

  return (
    <div>
      <Section title="Mode">
        <KeyValue label="Current Mode" value={uiState.mode} />
      </Section>

      <Section title="Interaction">
        <KeyValue label="Dragging" value={uiState.isDragging ? 'Yes' : 'No'} />
        <KeyValue label="Selecting" value={uiState.isSelecting ? 'Yes' : 'No'} />
        <KeyValue label="Panning" value={uiState.isPanning ? 'Yes' : 'No'} />
        <KeyValue label="Loading" value={uiState.isLoading ? 'Yes' : 'No'} />
      </Section>

      {uiState.error && (
        <Section title="Error">
          <div style={{ color: '#f48771', padding: '8px', backgroundColor: '#3c1f1e', borderRadius: '4px', fontSize: '10px' }}>
            {uiState.error}
          </div>
        </Section>
      )}
    </div>
  );
}

/**
 *
 */
function PerformancePanel({ performance }: { performance: CanvasPerformanceMetrics | null }) {
  if (!performance) {
    return <div style={{ color: '#888' }}>No performance data</div>;
  }

  return (
    <div>
      <Section title="Render Performance">
        <KeyValue label="Render Time" value={`${performance.renderTime.toFixed(2)}ms`} />
        <KeyValue label="FPS" value={performance.fps.toFixed(0)} />
      </Section>

      <Section title="Last Update">
        <KeyValue label="Timestamp" value={new Date(performance.lastUpdate).toLocaleString()} />
        <KeyValue label="Time Ago" value={`${Math.round((Date.now() - new Date(performance.lastUpdate).getTime()) / 1000)}s ago`} />
      </Section>
    </div>
  );
}

// Helper Components

/**
 *
 */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: '16px' }}>
      <div style={{ fontWeight: 'bold', marginBottom: '8px', color: '#4ec9b0', fontSize: '11px', textTransform: 'uppercase' }}>
        {title}
      </div>
      <div style={{ paddingLeft: '8px' }}>{children}</div>
    </div>
  );
}

/**
 *
 */
function KeyValue({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', marginBottom: '4px', fontSize: '11px' }}>
      <span style={{ color: '#9cdcfe', minWidth: '120px' }}>{label}:</span>
      <span style={{ color: '#ce9178', wordBreak: 'break-all' }}>{value}</span>
    </div>
  );
}

/**
 *
 */
function CodeBlock({ data }: { data: unknown }) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div style={{ marginTop: '4px' }}>
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        style={{
          background: 'none',
          border: 'none',
          color: '#569cd6',
          cursor: 'pointer',
          padding: 0,
          fontSize: '10px',
          textDecoration: 'underline',
        }}
      >
        {isExpanded ? 'Hide' : 'Show'} JSON
      </button>
      {isExpanded && (
        <pre
          style={{
            marginTop: '4px',
            padding: '8px',
            backgroundColor: '#1e1e1e',
            borderRadius: '4px',
            overflow: 'auto',
            maxHeight: '200px',
            fontSize: '9px',
            color: '#d4d4d4',
          }}
        >
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
}
