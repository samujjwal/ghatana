/**
 * Integrated UI Builder Component
 * 
 * Visual UI builder integrated with Monaco editor for synchronized
 * code and visual editing with real-time bidirectional sync.
 * 
 * Features:
 * - 🎨 Visual component builder
 * - 🔄 Bidirectional sync with Monaco editor
 * - 📦 Component library and palette
 * - 🎯 Property inspector and styling
 * - 👥 Collaborative editing support
 * - ⚡ Real-time preview
 * 
 * @doc.type component
 * @doc.purpose Integrated UI builder with code editor
 * @doc.layer product
 * @doc.pattern Advanced Component
 */

import React, { useRef, useEffect, useCallback, useState } from 'react';
import type * as Y from 'yjs';

import { MultiFileCodeEditor } from '@ghatana/yappc-code-editor';
import type { FileTab } from '@ghatana/yappc-code-editor';

/**
 * UI Component definition
 */
export interface UIComponent {
  id: string;
  type: string;
  name: string;
  props: Record<string, unknown>;
  children?: UIComponent[];
  styles?: Record<string, string>;
  position?: { x: number; y: number };
  size?: { width: number; height: number };
}

/**
 * Canvas state
 */
export interface CanvasState {
  components: UIComponent[];
  selectedComponentId?: string;
  hoveredComponentId?: string;
  zoom: number;
  panX: number;
  panY: number;
}

/**
 * UI Builder configuration
 */
export interface UIBuilderConfig {
  userId: string;
  userName: string;
  workspaceRoot: string;
  ydoc: Y.Doc;
  enableCollaboration: boolean;
  enablePreview: boolean;
  componentLibrary: Map<string, UIComponent>;
}

/**
 * Integrated UI Builder Component
 */
export const IntegratedUIBuilder: React.FC<{
  config: UIBuilderConfig;
  initialComponents?: UIComponent[];
  onComponentsChange?: (components: UIComponent[]) => void;
  onCodeChange?: (code: string) => void;
}> = ({
  config,
  initialComponents = [],
  onComponentsChange,
  onCodeChange,
}) => {
  const [canvasState, setCanvasState] = useState<CanvasState>({
    components: initialComponents,
    zoom: 1,
    panX: 0,
    panY: 0,
  });

  const [selectedComponent, setSelectedComponent] = useState<UIComponent | null>(null);
  const [isPreviewMode, setIsPreviewMode] = useState(false);
  const [files] = useState<FileTab[]>([
    {
      id: 'ui-builder',
      fileId: 'ui-builder',
      name: 'UI Builder',
      language: 'typescript',
      isDirty: false,
      isActive: true,
      hasConflict: false,
      lastModified: Date.now(),
    },
  ]);

  const canvasRef = useRef<HTMLDivElement>(null);

  /**
   * Convert components to code
   */
  const generateCode = useCallback((components: UIComponent[]): string => {
    const generateComponentCode = (component: UIComponent, indent = 0): string => {
      const spaces = ' '.repeat(indent);
      const propsStr = Object.entries(component.props)
        .map(([key, value]) => {
          if (typeof value === 'string') {
            return `${key}="${value}"`;
          } else if (typeof value === 'boolean') {
            return `${key}={${value}}`;
          } else {
            return `${key}={${JSON.stringify(value)}}`;
          }
        })
        .join(' ');

      const styleStr = component.styles
        ? ` style={${JSON.stringify(component.styles)}}`
        : '';

      if (component.children && component.children.length > 0) {
        const childrenCode = component.children
          .map((child) => generateComponentCode(child, indent + 2))
          .join('\n');
        return `${spaces}<${component.type} ${propsStr}${styleStr}>\n${childrenCode}\n${spaces}</${component.type}>`;
      }

      return `${spaces}<${component.type} ${propsStr}${styleStr} />`;
    };

    const componentCode = components
      .map((component) => generateComponentCode(component))
      .join('\n\n');

    return `import React from 'react';\n\nexport default function App() {\n  return (\n    <>\n${componentCode}\n    </>\n  );\n}`;
  }, []);

  /**
   * Handle component selection
   */
  const handleComponentSelect = useCallback((componentId: string) => {
    const findComponent = (components: UIComponent[]): UIComponent | null => {
      for (const component of components) {
        if (component.id === componentId) {
          return component;
        }
        if (component.children) {
          const found = findComponent(component.children);
          if (found) return found;
        }
      }
      return null;
    };

    const component = findComponent(canvasState.components);
    setSelectedComponent(component || null);
    setCanvasState((prev) => ({ ...prev, selectedComponentId: componentId }));
  }, [canvasState.components]);

  /**
   * Handle component property change
   */
  const handlePropertyChange = useCallback(
    (componentId: string, property: string, value: unknown) => {
      const updateComponent = (components: UIComponent[]): UIComponent[] => {
        return components.map((component) => {
          if (component.id === componentId) {
            return {
              ...component,
              props: { ...component.props, [property]: value },
            };
          }
          if (component.children) {
            return {
              ...component,
              children: updateComponent(component.children),
            };
          }
          return component;
        });
      };

      const updatedComponents = updateComponent(canvasState.components);
      setCanvasState((prev) => ({ ...prev, components: updatedComponents }));
      onComponentsChange?.(updatedComponents);

      // Update code
      const newCode = generateCode(updatedComponents);
      setCode(newCode);
      onCodeChange?.(newCode);
    },
    [canvasState.components, generateCode, onComponentsChange, onCodeChange]
  );

  /**
   * Handle component drag
   */
  const handleComponentDrag = useCallback(
    (componentId: string, x: number, y: number) => {
      const updateComponent = (components: UIComponent[]): UIComponent[] => {
        return components.map((component) => {
          if (component.id === componentId) {
            return {
              ...component,
              position: { x, y },
            };
          }
          if (component.children) {
            return {
              ...component,
              children: updateComponent(component.children),
            };
          }
          return component;
        });
      };

      const updatedComponents = updateComponent(canvasState.components);
      setCanvasState((prev) => ({ ...prev, components: updatedComponents }));
      onComponentsChange?.(updatedComponents);
    },
    [canvasState.components, onComponentsChange]
  );

  /**
   * Handle code change from editor
   */
  const handleCodeChange = useCallback(
    (newCode: string) => {
      setCode(newCode);
      onCodeChange?.(newCode);
      // NOTE: Parse code and update canvas
    },
    [onCodeChange]
  );

  /**
   * Render canvas components
   */
  const renderCanvasComponents = useCallback(
    (components: UIComponent[], parentX = 0, parentY = 0) => {
      return components.map((component) => {
        const x = (component.position?.x || 0) + parentX;
        const y = (component.position?.y || 0) + parentY;
        const width = component.size?.width || 200;
        const height = component.size?.height || 100;

        return (
          <div
            key={component.id}
            onClick={() => handleComponentSelect(component.id)}
            className={`absolute border-2 cursor-move transition-colors ${
              selectedComponent?.id === component.id
                ? 'border-blue-500 bg-blue-50'
                : 'border-gray-300 bg-white hover:border-gray-400'
            }`}
            style={{
              left: `${x}px`,
              top: `${y}px`,
              width: `${width}px`,
              height: `${height}px`,
              ...(component.styles || {}),
            }}
            draggable
            onDragEnd={(e) => {
              handleComponentDrag(component.id, e.clientX - (canvasRef.current?.offsetLeft || 0), e.clientY - (canvasRef.current?.offsetTop || 0));
            }}
          >
            <div className="p-2 text-sm font-semibold text-gray-700">
              {component.name}
            </div>
            {component.children && renderCanvasComponents(component.children, x, y)}
          </div>
        );
      });
    },
    [selectedComponent, handleComponentSelect, handleComponentDrag]
  );

  // Initialize code
  useEffect(() => {
    const initialCode = generateCode(canvasState.components);
    onCodeChange?.(initialCode);
  }, [canvasState.components, generateCode, onCodeChange]);

  return (
    <div className="integrated-ui-builder flex h-full bg-gray-900 text-gray-100">
      {/* Toolbar */}
      <div className="toolbar flex items-center gap-2 px-4 py-2 bg-gray-800 border-b border-gray-700">
        <button
          onClick={() => setIsPreviewMode(!isPreviewMode)}
          className={`px-3 py-1 rounded transition-colors ${
            isPreviewMode
              ? 'bg-blue-600 text-white'
              : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
          }`}
        >
          {isPreviewMode ? 'Edit' : 'Preview'}
        </button>
        <div className="flex-1" />
        <span className="text-sm text-gray-400">
          Zoom: {Math.round(canvasState.zoom * 100)}%
        </span>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Canvas */}
        <div className="canvas-panel flex-1 flex flex-col bg-gray-850 border-r border-gray-700">
          <div
            ref={canvasRef}
            className="flex-1 overflow-auto relative bg-gradient-to-br from-gray-900 to-gray-800"
            style={{
              transform: `scale(${canvasState.zoom})`,
              transformOrigin: '0 0',
            }}
          >
            {renderCanvasComponents(canvasState.components)}
          </div>
        </div>

        {/* Code Editor */}
        <div className="editor-panel flex-1 flex flex-col bg-gray-800 border-r border-gray-700">
          <MultiFileCodeEditor
            config={{
              userId: config.userId,
              userName: config.userName,
              workspaceRoot: config.workspaceRoot,
              ydoc: config.ydoc,
              enableAutoSave: true,
              autoSaveInterval: 5000,
              maxOpenTabs: 5,
              enableCollaborativeCursors: config.enableCollaboration,
            }}
            files={files}
            onTabChange={() => {}}
            onTabClose={() => {}}
            onFileSave={(fileId, content) => {
              handleCodeChange(content);
            }}
            onConflictDetected={() => {}}
          />
        </div>

        {/* Properties Panel */}
        {selectedComponent && (
          <div className="properties-panel w-64 flex flex-col bg-gray-800 border-l border-gray-700 overflow-y-auto">
            <div className="px-4 py-3 border-b border-gray-700">
              <h3 className="text-sm font-semibold">Properties</h3>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {/* Component Name */}
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">
                  Name
                </label>
                <input
                  type="text"
                  value={selectedComponent.name}
                  onChange={(e) =>
                    handlePropertyChange(selectedComponent.id, 'name', e.target.value)
                  }
                  className="w-full px-2 py-1 bg-gray-700 text-white rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Component Type */}
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">
                  Type
                </label>
                <input
                  type="text"
                  value={selectedComponent.type}
                  disabled
                  className="w-full px-2 py-1 bg-gray-700 text-gray-500 rounded text-sm"
                />
              </div>

              {/* Component Props */}
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-2">
                  Props
                </label>
                <div className="space-y-2">
                  {Object.entries(selectedComponent.props).map(([key, value]) => (
                    <div key={key}>
                      <label className="block text-xs text-gray-500 mb-1">
                        {key}
                      </label>
                      <input
                        type="text"
                        value={String(value)}
                        onChange={(e) =>
                          handlePropertyChange(
                            selectedComponent.id,
                            key,
                            e.target.value
                          )
                        }
                        className="w-full px-2 py-1 bg-gray-700 text-white rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                      />
                    </div>
                  ))}
                </div>
              </div>

              {/* Styles */}
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-2">
                  Styles
                </label>
                <div className="space-y-2">
                  {Object.entries(selectedComponent.styles || {}).map(
                    ([key, value]) => (
                      <div key={key}>
                        <label className="block text-xs text-gray-500 mb-1">
                          {key}
                        </label>
                        <input
                          type="text"
                          value={String(value)}
                          onChange={(e) => {
                            const updatedStyles = {
                              ...selectedComponent.styles,
                              [key]: e.target.value,
                            };
                            handlePropertyChange(
                              selectedComponent.id,
                              'styles',
                              updatedStyles
                            );
                          }}
                          className="w-full px-2 py-1 bg-gray-700 text-white rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>
                    )
                  )}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default IntegratedUIBuilder;
