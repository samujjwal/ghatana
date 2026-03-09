/**
 * Node Renderer
 *
 * Renders live React components from canvas node data.
 * Supports theme application, edit/preview modes, and error boundaries.
 *
 * @module canvas/renderer/NodeRenderer
 */

import React, { useMemo, useCallback } from 'react';

import { RendererComponentRegistry, useRegisteredComponent } from './ComponentRegistry';
import { ThemeApplicator, ThemeLayer } from './ThemeApplicator';

import type { ThemeContext} from './ThemeApplicator';
import type { ComponentNodeData } from '../types/CanvasNode';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface NodeRendererProps {
  /**
   * Component type to render
   */
  componentType: string;

  /**
   * Node data containing props, tokens, bindings, etc.
   */
  nodeData: ComponentNodeData;

  /**
   * Theme context for token resolution
   */
  themeContext: ThemeContext;

  /**
   * Rendering mode
   */
  mode?: 'edit' | 'preview';

  /**
   * Children components (for containers)
   */
  children?: React.ReactNode;

  /**
   * Callback for prop changes (edit mode)
   */
  onPropsChange?: (props: Record<string, unknown>) => void;

  /**
   * Callback for event handler execution
   */
  onEvent?: (eventName: string, payload: unknown) => void;

  /**
   * Error handler
   */
  onError?: (error: Error) => void;

  /**
   * Additional class name
   */
  className?: string;

  /**
   * Additional styles
   */
  style?: React.CSSProperties;
}

/**
 *
 */
export interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

// ============================================================================
// Error Boundary
// ============================================================================

/**
 *
 */
class NodeErrorBoundary extends React.Component<
  { children: React.ReactNode; onError?: (error: Error) => void },
  ErrorBoundaryState
> {
  /**
   *
   */
  constructor(props: { children: React.ReactNode; onError?: (error: Error) => void }) {
    super(props);
    this.state = { hasError: false };
  }

  /**
   *
   */
  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  /**
   *
   */
  componentDidCatch(error: Error, errorInfo: React.ErrorInfo): void {
    console.error('NodeRenderer error:', error, errorInfo);
    this.props.onError?.(error);
  }

  /**
   *
   */
  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            padding: '16px',
            backgroundColor: '#fee',
            border: '1px solid #fcc',
            borderRadius: '4px',
            color: '#c33',
          }}
        >
          <strong>Component Error</strong>
          <p>{this.state.error?.message || 'Unknown error'}</p>
        </div>
      );
    }

    return this.props.children;
  }
}

// ============================================================================
// Node Renderer Component
// ============================================================================

/**
 * Renders a canvas node as a live React component
 */
export const NodeRenderer: React.FC<NodeRendererProps> = ({
  componentType,
  nodeData,
  themeContext,
  mode = 'preview',
  children,
  onPropsChange,
  onEvent,
  onError,
  className,
  style,
}) => {
  // Get component from registry
  const Component = useRegisteredComponent(componentType);

  // Resolve props with theme
  const resolvedData = useMemo(() => {
    return ThemeApplicator.applyTheme(nodeData.props, nodeData.tokens, themeContext);
  }, [nodeData.props, nodeData.tokens, themeContext]);

  // Merge styles
  const mergedStyles = useMemo(() => {
    return {
      ...resolvedData.styles,
      ...style,
    };
  }, [resolvedData.styles, style]);

  // Create event handlers
  const eventHandlers = useMemo(() => {
    if (!nodeData.events) return {};

    const handlers: Record<string, unknown> = {};

    for (const [eventName, eventConfig] of Object.entries(nodeData.events)) {
      handlers[eventName] = (payload: unknown) => {
        onEvent?.(eventConfig.emit, {
          ...eventConfig.payload,
          ...payload,
        });
      };
    }

    return handlers;
  }, [nodeData.events, onEvent]);

  // Handle prop changes in edit mode
  const handlePropsChange = useCallback(
    (newProps: Record<string, unknown>) => {
      if (mode === 'edit' && onPropsChange) {
        onPropsChange(newProps);
      }
    },
    [mode, onPropsChange]
  );

  // Log unresolved tokens in development
  React.useEffect(() => {
    if (process.env.NODE_ENV === 'development' && resolvedData.unresolvedTokens.length > 0) {
      console.warn(
        `NodeRenderer: Unresolved tokens for ${componentType}:`,
        resolvedData.unresolvedTokens
      );
    }
  }, [componentType, resolvedData.unresolvedTokens]);

  // Component not found
  if (!Component) {
    return (
      <div
        className={className}
        style={{
          ...mergedStyles,
          padding: '16px',
          backgroundColor: '#fef3cd',
          border: '1px solid #ffd966',
          borderRadius: '4px',
        }}
      >
        <strong>Component not found:</strong> {componentType}
      </div>
    );
  }

  // Prepare final props
  const finalProps = {
    ...resolvedData.props,
    ...eventHandlers,
    className,
    style: mergedStyles,
    ...(mode === 'edit' && { 'data-edit-mode': true }),
  };

  // Render component with error boundary
  return (
    <NodeErrorBoundary onError={onError}>
      <Component {...finalProps}>{children}</Component>
    </NodeErrorBoundary>
  );
};

// ============================================================================
// Edit Mode Wrapper
// ============================================================================

/**
 *
 */
export interface EditModeWrapperProps {
  children: React.ReactNode;
  isSelected?: boolean;
  onSelect?: () => void;
  onDoubleClick?: () => void;
}

/**
 * Wraps rendered nodes with edit mode interaction handlers
 */
export const EditModeWrapper: React.FC<EditModeWrapperProps> = ({
  children,
  isSelected = false,
  onSelect,
  onDoubleClick,
}) => {
  return (
    <div
      onClick={(e) => {
        e.stopPropagation();
        onSelect?.();
      }}
      onDoubleClick={(e) => {
        e.stopPropagation();
        onDoubleClick?.();
      }}
      style={{
        position: 'relative',
        outline: isSelected ? '2px solid #1976d2' : 'none',
        outlineOffset: '2px',
        cursor: 'pointer',
      }}
      data-edit-mode="true"
    >
      {children}
      {isSelected && (
        <div
          style={{
            position: 'absolute',
            top: -24,
            left: 0,
            padding: '2px 8px',
            backgroundColor: '#1976d2',
            color: '#fff',
            fontSize: '12px',
            borderRadius: '4px 4px 0 0',
            pointerEvents: 'none',
          }}
        >
          Selected
        </div>
      )}
    </div>
  );
};

// ============================================================================
// Batch Renderer (for multiple nodes)
// ============================================================================

/**
 *
 */
export interface BatchNodeRendererProps {
  nodes: Array<{
    id: string;
    componentType: string;
    nodeData: ComponentNodeData;
  }>;
  themeContext: ThemeContext;
  mode?: 'edit' | 'preview';
  selectedId?: string;
  onSelect?: (id: string) => void;
  onPropsChange?: (id: string, props: Record<string, unknown>) => void;
  onEvent?: (id: string, eventName: string, payload: unknown) => void;
  onError?: (id: string, error: Error) => void;
}

/**
 * Renders multiple nodes efficiently
 */
export const BatchNodeRenderer: React.FC<BatchNodeRendererProps> = ({
  nodes,
  themeContext,
  mode = 'preview',
  selectedId,
  onSelect,
  onPropsChange,
  onEvent,
  onError,
}) => {
  return (
    <>
      {nodes.map((node) => {
        const renderer = (
          <NodeRenderer
            key={node.id}
            componentType={node.componentType}
            nodeData={node.nodeData}
            themeContext={themeContext}
            mode={mode}
            onPropsChange={(props) => onPropsChange?.(node.id, props)}
            onEvent={(eventName, payload) => onEvent?.(node.id, eventName, payload)}
            onError={(error) => onError?.(node.id, error)}
          />
        );

        if (mode === 'edit') {
          return (
            <EditModeWrapper
              key={node.id}
              isSelected={selectedId === node.id}
              onSelect={() => onSelect?.(node.id)}
            >
              {renderer}
            </EditModeWrapper>
          );
        }

        return renderer;
      })}
    </>
  );
};
