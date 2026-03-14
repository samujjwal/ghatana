/**
 * CS Discrete Parameter Widgets
 * 
 * Specialized widgets for computer science discrete simulations.
 * Includes data structure visualization, algorithm parameters, and code snippets.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for CS discrete simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import { useState, useCallback } from "react";
import { Button, Badge, Slider, Tooltip } from "@ghatana/design-system";

// =============================================================================
// Types
// =============================================================================

export interface ArrayElement {
  value: number | string;
  highlighted: boolean;
  color?: string;
  label?: string;
}

export interface LinkedListNode {
  id: string;
  value: number | string;
  next: string | null;
  highlighted: boolean;
  color?: string;
}

export interface TreeNode {
  id: string;
  value: number | string;
  left: string | null;
  right: string | null;
  highlighted: boolean;
  color?: string;
  label?: string;
}

export interface GraphNode {
  id: string;
  label: string;
  x: number;
  y: number;
  highlighted: boolean;
  color?: string;
}

export interface GraphEdge {
  source: string;
  target: string;
  weight?: number;
  directed: boolean;
  highlighted: boolean;
  color?: string;
}

export interface PointerConfig {
  name: string;
  targetIndex: number;
  color: string;
  style: "arrow" | "bracket" | "underline";
}

export interface SortingVisualizationConfig {
  algorithm: "bubble" | "selection" | "insertion" | "merge" | "quick" | "heap";
  speed: number; // ms per step
  showComparisons: boolean;
  showSwaps: boolean;
  compareColor: string;
  swapColor: string;
}

export interface AlgorithmMetrics {
  comparisons: number;
  swaps: number;
  accesses: number;
  writes: number;
  recursionDepth: number;
}

// =============================================================================
// Array Visualization Widget
// =============================================================================

export interface ArrayVisualizationProps {
  elements: ArrayElement[];
  onElementsChange: (elements: ArrayElement[]) => void;
  pointers: PointerConfig[];
  onPointersChange: (pointers: PointerConfig[]) => void;
  showIndices?: boolean;
  disabled?: boolean;
}

export const ArrayVisualization = ({
  elements,
  onElementsChange,
  pointers,
  onPointersChange,
  showIndices = true,
  disabled = false,
}: ArrayVisualizationProps) => {
  const [newValue, setNewValue] = useState("");
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);

  const addElement = useCallback(() => {
    if (!newValue.trim()) return;
    const value = !isNaN(Number(newValue)) ? Number(newValue) : newValue;
    onElementsChange([...elements, { value, highlighted: false }]);
    setNewValue("");
  }, [newValue, elements, onElementsChange]);

  const removeElement = useCallback((index: number) => {
    const newElements = elements.filter((_, i) => i !== index);
    onElementsChange(newElements);
    // Update pointers
    const newPointers = pointers
      .map((p) => ({
        ...p,
        targetIndex: p.targetIndex > index ? p.targetIndex - 1 : p.targetIndex,
      }))
      .filter((p) => p.targetIndex < newElements.length);
    onPointersChange(newPointers);
  }, [elements, pointers, onElementsChange, onPointersChange]);

  const updateElement = useCallback((index: number, updates: Partial<ArrayElement>) => {
    const newElements = [...elements];
    newElements[index] = { ...newElements[index], ...updates };
    onElementsChange(newElements);
  }, [elements, onElementsChange]);

  const toggleHighlight = useCallback((index: number) => {
    updateElement(index, { highlighted: !elements[index].highlighted });
  }, [elements, updateElement]);

  const addPointer = useCallback(() => {
    if (selectedIndex === null) return;
    const colors = ["#ef4444", "#22c55e", "#3b82f6", "#f59e0b", "#8b5cf6"];
    const newPointer: PointerConfig = {
      name: `ptr${pointers.length + 1}`,
      targetIndex: selectedIndex,
      color: colors[pointers.length % colors.length],
      style: "arrow",
    };
    onPointersChange([...pointers, newPointer]);
  }, [selectedIndex, pointers, onPointersChange]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Array Visualization
      </span>

      {/* Array Display */}
      <div className="flex flex-wrap gap-1 p-2 bg-white dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700 min-h-[3rem]">
        {elements.map((elem, index) => (
          <div key={index} className="flex flex-col items-center">
            {/* Pointers above */}
            <div className="h-5 flex items-end justify-center">
              {pointers
                .filter((p) => p.targetIndex === index)
                .map((p, pi) => (
                  <Tooltip key={pi} content={p.name}>
                    <span
                      className="text-xs font-mono cursor-pointer"
                      style={{ color: p.color }}
                    >
                      {p.style === "arrow" && "↓"}
                      {p.style === "bracket" && "["}
                      {p.style === "underline" && "_"}
                    </span>
                  </Tooltip>
                ))}
            </div>

            {/* Element box */}
            <button
              onClick={() => setSelectedIndex(index)}
              onDoubleClick={() => toggleHighlight(index)}
              className={`
                w-10 h-10 flex items-center justify-center border-2 rounded text-sm font-mono
                transition-all cursor-pointer
                ${selectedIndex === index ? "ring-2 ring-blue-500" : ""}
                ${elem.highlighted 
                  ? "bg-yellow-200 dark:bg-yellow-800 border-yellow-500" 
                  : "bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600"
                }
              `}
              style={elem.color ? { backgroundColor: elem.color } : undefined}
            >
              {elem.value}
            </button>

            {/* Index below */}
            {showIndices && (
              <span className="text-xs text-gray-400 mt-1">{index}</span>
            )}
          </div>
        ))}

        {elements.length === 0 && (
          <span className="text-sm text-gray-400 italic p-2">Empty array</span>
        )}
      </div>

      {/* Add Element */}
      <div className="flex gap-2">
        <input
          type="text"
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder="Value"
          disabled={disabled}
          className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded"
          onKeyDown={(e) => e.key === "Enter" && addElement()}
        />
        <Button size="sm" onClick={addElement} disabled={disabled}>
          Push
        </Button>
        <Button 
          size="sm" 
          variant="outline" 
          onClick={() => elements.length > 0 && removeElement(elements.length - 1)}
          disabled={disabled || elements.length === 0}
        >
          Pop
        </Button>
      </div>

      {/* Selected Element Actions */}
      {selectedIndex !== null && selectedIndex < elements.length && (
        <div className="flex items-center gap-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded">
          <span className="text-xs">Index {selectedIndex}:</span>
          <Button size="sm" variant="ghost" onClick={addPointer} disabled={disabled}>
            Add Pointer
          </Button>
          <Button 
            size="sm" 
            variant="ghost" 
            onClick={() => toggleHighlight(selectedIndex)}
            disabled={disabled}
          >
            {elements[selectedIndex]?.highlighted ? "Unhighlight" : "Highlight"}
          </Button>
          <Button 
            size="sm" 
            variant="ghost" 
            onClick={() => removeElement(selectedIndex)}
            disabled={disabled}
          >
            Remove
          </Button>
        </div>
      )}

      {/* Pointers List */}
      {pointers.length > 0 && (
        <div className="space-y-1">
          <span className="text-xs text-gray-500">Pointers:</span>
          <div className="flex flex-wrap gap-2">
            {pointers.map((p, index) => (
              <Badge
                key={index}
                tone="secondary"
                style={{ borderColor: p.color, color: p.color }}
              >
                {p.name} → [{p.targetIndex}]
                <button
                  onClick={() => onPointersChange(pointers.filter((_, i) => i !== index))}
                  className="ml-1 hover:text-red-500"
                >
                  ×
                </button>
              </Badge>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Graph Node/Edge Editor
// =============================================================================

export interface GraphEditorProps {
  nodes: GraphNode[];
  edges: GraphEdge[];
  onNodesChange: (nodes: GraphNode[]) => void;
  onEdgesChange: (edges: GraphEdge[]) => void;
  directed?: boolean;
  weighted?: boolean;
  disabled?: boolean;
}

export const GraphEditor = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  directed = false,
  weighted = false,
  disabled = false,
}: GraphEditorProps) => {
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [edgeSource, setEdgeSource] = useState<string | null>(null);

  const addNode = useCallback(() => {
    const id = `n${nodes.length + 1}`;
    const newNode: GraphNode = {
      id,
      label: id,
      x: 20 + (nodes.length % 4) * 20,
      y: 20 + Math.floor(nodes.length / 4) * 20,
      highlighted: false,
    };
    onNodesChange([...nodes, newNode]);
  }, [nodes, onNodesChange]);

  const removeNode = useCallback((nodeId: string) => {
    onNodesChange(nodes.filter((n) => n.id !== nodeId));
    onEdgesChange(edges.filter((e) => e.source !== nodeId && e.target !== nodeId));
    setSelectedNodeId(null);
  }, [nodes, edges, onNodesChange, onEdgesChange]);

  const handleNodeClick = useCallback((nodeId: string) => {
    if (edgeSource) {
      if (edgeSource !== nodeId) {
        // Create edge
        const newEdge: GraphEdge = {
          source: edgeSource,
          target: nodeId,
          weight: weighted ? 1 : undefined,
          directed,
          highlighted: false,
        };
        // Check if edge already exists
        const exists = edges.some(
          (e) => e.source === edgeSource && e.target === nodeId
        );
        if (!exists) {
          onEdgesChange([...edges, newEdge]);
        }
      }
      setEdgeSource(null);
    } else {
      setSelectedNodeId(nodeId);
    }
  }, [edgeSource, directed, weighted, edges, onEdgesChange]);

  const updateNode = useCallback((nodeId: string, updates: Partial<GraphNode>) => {
    onNodesChange(nodes.map((n) => (n.id === nodeId ? { ...n, ...updates } : n)));
  }, [nodes, onNodesChange]);

  const updateEdge = useCallback((index: number, updates: Partial<GraphEdge>) => {
    const newEdges = [...edges];
    newEdges[index] = { ...newEdges[index], ...updates };
    onEdgesChange(newEdges);
  }, [edges, onEdgesChange]);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Graph Editor
        </span>
        <div className="flex gap-1">
                    <Badge tone={directed ? "primary" : "secondary"}>
            {directed ? "Directed" : "Undirected"}
          </Badge>
          {weighted && <Badge tone="secondary">Weighted</Badge>}
        </div>
      </div>

      {/* Graph Canvas */}
      <div className="h-48 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="0 0 100 100" className="w-full h-full">
          {/* Edges */}
          {edges.map((edge, index) => {
            const sourceNode = nodes.find((n) => n.id === edge.source);
            const targetNode = nodes.find((n) => n.id === edge.target);
            if (!sourceNode || !targetNode) return null;

            const midX = (sourceNode.x + targetNode.x) / 2;
            const midY = (sourceNode.y + targetNode.y) / 2;

            return (
              <g key={index}>
                <line
                  x1={sourceNode.x}
                  y1={sourceNode.y}
                  x2={targetNode.x}
                  y2={targetNode.y}
                  stroke={edge.highlighted ? "#f59e0b" : (edge.color || "#6b7280")}
                  strokeWidth={edge.highlighted ? 2 : 1}
                  markerEnd={directed ? "url(#edgeArrow)" : undefined}
                />
                {weighted && edge.weight !== undefined && (
                  <text
                    x={midX}
                    y={midY - 2}
                    fontSize="6"
                    textAnchor="middle"
                    fill="#374151"
                    className="select-none"
                  >
                    {edge.weight}
                  </text>
                )}
              </g>
            );
          })}

          {/* Arrow marker for directed edges */}
          <defs>
            <marker
              id="edgeArrow"
              markerWidth="6"
              markerHeight="6"
              refX="8"
              refY="3"
              orient="auto"
            >
              <path d="M0,0 L6,3 L0,6 Z" fill="#6b7280" />
            </marker>
          </defs>

          {/* Nodes */}
          {nodes.map((node) => (
            <g
              key={node.id}
              onClick={() => handleNodeClick(node.id)}
              style={{ cursor: "pointer" }}
            >
              <circle
                cx={node.x}
                cy={node.y}
                r="5"
                fill={
                  node.highlighted
                    ? "#fbbf24"
                    : edgeSource === node.id
                    ? "#22c55e"
                    : node.color || "#3b82f6"
                }
                stroke={selectedNodeId === node.id ? "#1e40af" : "#1e3a5f"}
                strokeWidth={selectedNodeId === node.id ? 2 : 1}
              />
              <text
                x={node.x}
                y={node.y + 10}
                fontSize="5"
                textAnchor="middle"
                fill="#374151"
                className="select-none"
              >
                {node.label}
              </text>
            </g>
          ))}
        </svg>
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap gap-2">
        <Button size="sm" onClick={addNode} disabled={disabled}>
          Add Node
        </Button>
        <Button
          size="sm"
          variant={edgeSource ? "solid" : "outline"}
          onClick={() => setEdgeSource(edgeSource ? null : selectedNodeId)}
          disabled={disabled || !selectedNodeId}
        >
          {edgeSource ? "Cancel Edge" : "Start Edge"}
        </Button>
      </div>

      {/* Selected Node Properties */}
      {selectedNode && (
        <div className="space-y-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium">Node: {selectedNode.id}</span>
            <button
              onClick={() => removeNode(selectedNode.id)}
              disabled={disabled}
              className="text-xs text-red-500 hover:underline"
            >
              Delete
            </button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Label</label>
              <input
                type="text"
                value={selectedNode.label}
                onChange={(e) => updateNode(selectedNode.id, { label: e.target.value })}
                disabled={disabled}
                className="w-full px-2 py-1 text-xs border border-gray-300 rounded"
              />
            </div>
            <div className="flex items-end">
              <Button
                size="sm"
                variant="ghost"
                onClick={() => updateNode(selectedNode.id, { highlighted: !selectedNode.highlighted })}
                disabled={disabled}
              >
                {selectedNode.highlighted ? "Unhighlight" : "Highlight"}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Edge List */}
      {edges.length > 0 && (
        <div className="space-y-1 max-h-24 overflow-auto">
          <span className="text-xs text-gray-500">Edges:</span>
          {edges.map((edge, index) => (
            <div key={index} className="flex items-center justify-between text-xs bg-white dark:bg-gray-800 p-1 rounded">
              <span className="font-mono">
                {edge.source} {directed ? "→" : "—"} {edge.target}
                {weighted && edge.weight !== undefined && ` (${edge.weight})`}
              </span>
              <div className="flex gap-1">
                {weighted && (
                  <input
                    type="number"
                    value={edge.weight ?? 1}
                    onChange={(e) => updateEdge(index, { weight: Number(e.target.value) })}
                    disabled={disabled}
                    className="w-12 px-1 text-xs border border-gray-300 rounded"
                  />
                )}
                <button
                  onClick={() => onEdgesChange(edges.filter((_, i) => i !== index))}
                  disabled={disabled}
                  className="text-gray-400 hover:text-red-500"
                >
                  ×
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Edge Mode Hint */}
      {edgeSource && (
        <p className="text-xs text-green-600 italic">
          Click another node to create edge from {edgeSource}
        </p>
      )}
    </div>
  );
};

// =============================================================================
// Algorithm Configuration Widget
// =============================================================================

export interface AlgorithmConfigProps {
  config: SortingVisualizationConfig;
  onChange: (config: SortingVisualizationConfig) => void;
  metrics?: AlgorithmMetrics;
  disabled?: boolean;
}

export const AlgorithmConfig = ({
  config,
  onChange,
  metrics,
  disabled = false,
}: AlgorithmConfigProps) => {
  const algorithms: Array<{ value: SortingVisualizationConfig["algorithm"]; label: string; complexity: string }> = [
    { value: "bubble", label: "Bubble Sort", complexity: "O(n²)" },
    { value: "selection", label: "Selection Sort", complexity: "O(n²)" },
    { value: "insertion", label: "Insertion Sort", complexity: "O(n²)" },
    { value: "merge", label: "Merge Sort", complexity: "O(n log n)" },
    { value: "quick", label: "Quick Sort", complexity: "O(n log n)" },
    { value: "heap", label: "Heap Sort", complexity: "O(n log n)" },
  ];

  const selectedAlgo = algorithms.find((a) => a.value === config.algorithm);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Algorithm Config
        </span>
        {selectedAlgo && (
          <Badge tone="secondary">
            {selectedAlgo.complexity}
          </Badge>
        )}
      </div>

      {/* Algorithm Selection */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Algorithm</label>
        <select
          value={config.algorithm}
          onChange={(e) => onChange({ 
            ...config, 
            algorithm: e.target.value as SortingVisualizationConfig["algorithm"] 
          })}
          disabled={disabled}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        >
          {algorithms.map((algo) => (
            <option key={algo.value} value={algo.value}>
              {algo.label}
            </option>
          ))}
        </select>
      </div>

      {/* Speed Control */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Animation Speed</label>
          <span className="text-xs font-mono">{config.speed}ms</span>
        </div>
        <Slider
          min={10}
          max={1000}
          value={config.speed}
          onChange={(e) => onChange({ ...config, speed: Number(e.target.value) })}
          disabled={disabled}
        />
        <div className="flex justify-between text-xs text-gray-400 mt-1">
          <span>Fast</span>
          <span>Slow</span>
        </div>
      </div>

      {/* Visualization Options */}
      <div className="space-y-2">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={config.showComparisons}
            onChange={(e) => onChange({ ...config, showComparisons: e.target.checked })}
            disabled={disabled}
            className="rounded border-gray-300"
          />
          <span className="text-xs">Show comparisons</span>
          <div
            className="w-3 h-3 rounded"
            style={{ backgroundColor: config.compareColor }}
          />
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={config.showSwaps}
            onChange={(e) => onChange({ ...config, showSwaps: e.target.checked })}
            disabled={disabled}
            className="rounded border-gray-300"
          />
          <span className="text-xs">Show swaps</span>
          <div
            className="w-3 h-3 rounded"
            style={{ backgroundColor: config.swapColor }}
          />
        </label>
      </div>

      {/* Color Pickers */}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className="block text-xs text-gray-500 mb-1">Compare Color</label>
          <input
            type="color"
            value={config.compareColor}
            onChange={(e) => onChange({ ...config, compareColor: e.target.value })}
            disabled={disabled}
            className="w-full h-8 border border-gray-300 rounded cursor-pointer"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Swap Color</label>
          <input
            type="color"
            value={config.swapColor}
            onChange={(e) => onChange({ ...config, swapColor: e.target.value })}
            disabled={disabled}
            className="w-full h-8 border border-gray-300 rounded cursor-pointer"
          />
        </div>
      </div>

      {/* Metrics Display */}
      {metrics && (
        <div className="grid grid-cols-2 gap-2 text-xs bg-white dark:bg-gray-800 p-2 rounded">
          <div className="flex justify-between">
            <span className="text-gray-500">Comparisons:</span>
            <span className="font-mono">{metrics.comparisons}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Swaps:</span>
            <span className="font-mono">{metrics.swaps}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Accesses:</span>
            <span className="font-mono">{metrics.accesses}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Depth:</span>
            <span className="font-mono">{metrics.recursionDepth}</span>
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Code Snippet Widget
// =============================================================================

export interface CodeSnippetWidgetProps {
  code: string;
  language: "python" | "javascript" | "java" | "cpp" | "pseudocode";
  highlightedLines: number[];
  onCodeChange: (code: string) => void;
  onHighlightChange: (lines: number[]) => void;
  readOnly?: boolean;
  disabled?: boolean;
}

export const CodeSnippetWidget = ({
  code,
  language,
  highlightedLines,
  onCodeChange,
  onHighlightChange,
  readOnly = false,
  disabled = false,
}: CodeSnippetWidgetProps) => {
  const lines = code.split("\n");

  const toggleLineHighlight = useCallback((lineNumber: number) => {
    if (highlightedLines.includes(lineNumber)) {
      onHighlightChange(highlightedLines.filter((l) => l !== lineNumber));
    } else {
      onHighlightChange([...highlightedLines, lineNumber]);
    }
  }, [highlightedLines, onHighlightChange]);

  const languageColors: Record<string, string> = {
    python: "#3776ab",
    javascript: "#f7df1e",
    java: "#b07219",
    cpp: "#00599c",
    pseudocode: "#6b7280",
  };

  return (
    <div className="space-y-2 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Code Snippet
        </span>
        <div className="flex items-center gap-2">
          <select
            value={language}
            disabled
            className="px-2 py-0.5 text-xs border border-gray-300 rounded"
            style={{ borderLeftColor: languageColors[language], borderLeftWidth: 3 }}
          >
            <option value="python">Python</option>
            <option value="javascript">JavaScript</option>
            <option value="java">Java</option>
            <option value="cpp">C++</option>
            <option value="pseudocode">Pseudocode</option>
          </select>
        </div>
      </div>

      {/* Code Display */}
      <div className="bg-gray-900 rounded overflow-auto max-h-64">
        <table className="w-full text-xs font-mono">
          <tbody>
            {lines.map((line, index) => {
              const lineNumber = index + 1;
              const isHighlighted = highlightedLines.includes(lineNumber);
              return (
                <tr
                  key={index}
                  className={`
                    ${isHighlighted ? "bg-yellow-500/30" : "hover:bg-gray-800"}
                    cursor-pointer
                  `}
                  onClick={() => toggleLineHighlight(lineNumber)}
                >
                  <td className="px-2 py-0.5 text-gray-500 select-none text-right w-8 border-r border-gray-700">
                    {lineNumber}
                  </td>
                  <td className="px-2 py-0.5 text-gray-200 whitespace-pre">
                    {line || " "}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Code Editor (if not read-only) */}
      {!readOnly && (
        <textarea
          value={code}
          onChange={(e) => onCodeChange(e.target.value)}
          disabled={disabled}
          rows={6}
          className="w-full px-2 py-1 text-xs font-mono bg-gray-900 text-gray-200 border border-gray-700 rounded resize-none"
          placeholder="Enter code..."
        />
      )}

      {/* Highlighted Lines */}
      {highlightedLines.length > 0 && (
        <div className="text-xs text-gray-500">
          Highlighted: {highlightedLines.sort((a, b) => a - b).join(", ")}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Stack/Queue Visualization
// =============================================================================

export interface StackQueueVisualizationProps {
  type: "stack" | "queue";
  elements: Array<{ value: string | number; highlighted?: boolean }>;
  onElementsChange: (elements: Array<{ value: string | number; highlighted?: boolean }>) => void;
  maxSize?: number;
  disabled?: boolean;
}

export const StackQueueVisualization = ({
  type,
  elements,
  onElementsChange,
  maxSize = 10,
  disabled = false,
}: StackQueueVisualizationProps) => {
  const [newValue, setNewValue] = useState("");

  const push = useCallback(() => {
    if (!newValue.trim() || elements.length >= maxSize) return;
    const value = !isNaN(Number(newValue)) ? Number(newValue) : newValue;
    onElementsChange([...elements, { value }]);
    setNewValue("");
  }, [newValue, elements, maxSize, onElementsChange]);

  const pop = useCallback(() => {
    if (elements.length === 0) return;
    if (type === "stack") {
      onElementsChange(elements.slice(0, -1));
    } else {
      onElementsChange(elements.slice(1));
    }
  }, [type, elements, onElementsChange]);

  const peek = useCallback((): { value: string | number } | undefined => {
    if (elements.length === 0) return undefined;
    return type === "stack" ? elements[elements.length - 1] : elements[0];
  }, [type, elements]);

  const peekValue = peek();

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {type === "stack" ? "Stack (LIFO)" : "Queue (FIFO)"}
        </span>
        <Badge tone="secondary">
          {elements.length}/{maxSize}
        </Badge>
      </div>

      {/* Visualization */}
      <div className="flex items-end justify-center gap-1 p-2 bg-white dark:bg-gray-800 rounded border border-gray-200 dark:border-gray-700 min-h-[8rem]">
        {type === "stack" ? (
          // Stack: vertical, bottom to top
          <div className="flex flex-col-reverse gap-1">
            {elements.map((elem, index) => (
              <div
                key={index}
                className={`
                  w-16 h-8 flex items-center justify-center border-2 rounded text-sm font-mono
                  ${index === elements.length - 1 
                    ? "bg-green-100 dark:bg-green-900 border-green-500" 
                    : "bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600"
                  }
                `}
              >
                {elem.value}
                {index === elements.length - 1 && (
                  <span className="ml-1 text-xs text-green-600">←top</span>
                )}
              </div>
            ))}
            {elements.length === 0 && (
              <span className="text-sm text-gray-400 italic">Empty</span>
            )}
          </div>
        ) : (
          // Queue: horizontal, left to right
          <div className="flex flex-row gap-1 items-center">
            <span className="text-xs text-blue-500 mr-1">front→</span>
            {elements.map((elem, index) => (
              <div
                key={index}
                className={`
                  w-10 h-10 flex items-center justify-center border-2 rounded text-sm font-mono
                  ${index === 0 
                    ? "bg-blue-100 dark:bg-blue-900 border-blue-500" 
                    : index === elements.length - 1
                    ? "bg-green-100 dark:bg-green-900 border-green-500"
                    : "bg-gray-100 dark:bg-gray-700 border-gray-300 dark:border-gray-600"
                  }
                `}
              >
                {elem.value}
              </div>
            ))}
            {elements.length > 0 && (
              <span className="text-xs text-green-500 ml-1">←rear</span>
            )}
            {elements.length === 0 && (
              <span className="text-sm text-gray-400 italic">Empty</span>
            )}
          </div>
        )}
      </div>

      {/* Controls */}
      <div className="flex gap-2">
        <input
          type="text"
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder="Value"
          disabled={disabled || elements.length >= maxSize}
          className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded"
          onKeyDown={(e) => e.key === "Enter" && push()}
        />
        <Button 
          size="sm" 
          onClick={push} 
          disabled={disabled || elements.length >= maxSize}
        >
          {type === "stack" ? "Push" : "Enqueue"}
        </Button>
        <Button 
          size="sm" 
          variant="outline"
          onClick={pop}
          disabled={disabled || elements.length === 0}
        >
          {type === "stack" ? "Pop" : "Dequeue"}
        </Button>
      </div>

      {/* Peek Display */}
      {peekValue !== undefined && (
        <div className="text-xs text-gray-500">
          {type === "stack" ? "Top" : "Front"}: <span className="font-mono">{peekValue.value}</span>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const CSDiscreteWidgets = {
  ArrayVisualization,
  GraphEditor,
  AlgorithmConfig,
  CodeSnippetWidget,
  StackQueueVisualization,
};

export default CSDiscreteWidgets;
