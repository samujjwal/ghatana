/**
 * Math & Engineering Parameter Widgets
 * 
 * Specialized widgets for mathematics and engineering simulation parameters.
 * Includes geometry tools, function plotters, and circuit/mechanical diagrams.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for math/engineering simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import React, { useState, useCallback, useMemo } from "react";
import { Button, Badge, Slider, Tooltip } from "@ghatana/ui";

// =============================================================================
// Types
// =============================================================================

export interface Point2D {
  x: number;
  y: number;
}

export interface Point3D {
  x: number;
  y: number;
  z: number;
}

export interface GeometricShape {
  type: "circle" | "rectangle" | "triangle" | "polygon" | "line" | "ellipse";
  center: Point2D;
  // Shape-specific parameters
  radius?: number; // circle
  width?: number; // rectangle, ellipse (a)
  height?: number; // rectangle, ellipse (b)
  vertices?: Point2D[]; // polygon, triangle
  start?: Point2D; // line
  end?: Point2D; // line
  rotation?: number; // degrees
  style?: {
    fill?: string;
    stroke?: string;
    strokeWidth?: number;
    opacity?: number;
  };
}

export interface FunctionDefinition {
  expression: string; // e.g., "x^2 + 2*x + 1" or "sin(x)"
  domain: { min: number; max: number };
  color: string;
  lineWidth: number;
  label?: string;
}

export interface MatrixValue {
  rows: number;
  cols: number;
  data: number[][];
}

export interface ComplexNumber {
  real: number;
  imaginary: number;
}

export interface CircuitComponent {
  type: "resistor" | "capacitor" | "inductor" | "voltage-source" | "current-source" | "diode" | "ground";
  value: number;
  unit: string;
  position: Point2D;
  rotation: 0 | 90 | 180 | 270;
  connections: [string, string]; // [node1, node2]
}

export interface MechanicalElement {
  type: "mass" | "spring" | "damper" | "rigid-link" | "pivot" | "slider";
  value: number;
  position: Point2D;
  angle: number;
  connections: string[];
}

// =============================================================================
// Geometry Shape Editor
// =============================================================================

export interface GeometryShapeEditorProps {
  shapes: GeometricShape[];
  onShapesChange: (shapes: GeometricShape[]) => void;
  selectedIndex: number | null;
  onSelectionChange: (index: number | null) => void;
  disabled?: boolean;
}

export const GeometryShapeEditor = ({
  shapes,
  onShapesChange,
  selectedIndex,
  onSelectionChange,
  disabled = false,
}: GeometryShapeEditorProps) => {
  const selectedShape = selectedIndex !== null ? shapes[selectedIndex] : null;

  const updateShape = useCallback((index: number, updates: Partial<GeometricShape>) => {
    const newShapes = [...shapes];
    newShapes[index] = { ...newShapes[index], ...updates };
    onShapesChange(newShapes);
  }, [shapes, onShapesChange]);

  const addShape = useCallback((type: GeometricShape["type"]) => {
    const newShape: GeometricShape = {
      type,
      center: { x: 50, y: 50 },
      radius: type === "circle" ? 20 : undefined,
      width: type === "rectangle" || type === "ellipse" ? 40 : undefined,
      height: type === "rectangle" || type === "ellipse" ? 30 : undefined,
      vertices: type === "triangle" ? [
        { x: 50, y: 30 },
        { x: 30, y: 70 },
        { x: 70, y: 70 },
      ] : undefined,
      start: type === "line" ? { x: 20, y: 20 } : undefined,
      end: type === "line" ? { x: 80, y: 80 } : undefined,
      rotation: 0,
      style: { fill: "#3b82f6", stroke: "#1e40af", strokeWidth: 2, opacity: 0.7 },
    };
    onShapesChange([...shapes, newShape]);
    onSelectionChange(shapes.length);
  }, [shapes, onShapesChange, onSelectionChange]);

  const removeShape = useCallback((index: number) => {
    const newShapes = shapes.filter((_, i) => i !== index);
    onShapesChange(newShapes);
    onSelectionChange(null);
  }, [shapes, onShapesChange, onSelectionChange]);

  // Render shape to SVG
  const renderShape = (shape: GeometricShape, index: number): React.JSX.Element | null => {
    const isSelected = index === selectedIndex;
    const commonProps = {
      fill: shape.style?.fill || "#3b82f6",
      stroke: isSelected ? "#f59e0b" : (shape.style?.stroke || "#1e40af"),
      strokeWidth: isSelected ? 3 : (shape.style?.strokeWidth || 2),
      opacity: shape.style?.opacity || 0.7,
      onClick: () => onSelectionChange(index),
      style: { cursor: "pointer" },
    };

    switch (shape.type) {
      case "circle":
        return (
          <circle
            key={index}
            cx={shape.center.x}
            cy={shape.center.y}
            r={shape.radius || 20}
            {...commonProps}
          />
        );
      case "rectangle":
        return (
          <rect
            key={index}
            x={shape.center.x - (shape.width || 40) / 2}
            y={shape.center.y - (shape.height || 30) / 2}
            width={shape.width || 40}
            height={shape.height || 30}
            transform={`rotate(${shape.rotation || 0}, ${shape.center.x}, ${shape.center.y})`}
            {...commonProps}
          />
        );
      case "ellipse":
        return (
          <ellipse
            key={index}
            cx={shape.center.x}
            cy={shape.center.y}
            rx={(shape.width || 40) / 2}
            ry={(shape.height || 30) / 2}
            transform={`rotate(${shape.rotation || 0}, ${shape.center.x}, ${shape.center.y})`}
            {...commonProps}
          />
        );
      case "triangle":
        if (!shape.vertices || shape.vertices.length < 3) return null;
        return (
          <polygon
            key={index}
            points={shape.vertices.map(v => `${v.x},${v.y}`).join(" ")}
            {...commonProps}
          />
        );
      case "line":
        return (
          <line
            key={index}
            x1={shape.start?.x || 20}
            y1={shape.start?.y || 20}
            x2={shape.end?.x || 80}
            y2={shape.end?.y || 80}
            stroke={isSelected ? "#f59e0b" : (shape.style?.stroke || "#1e40af")}
            strokeWidth={isSelected ? 4 : (shape.style?.strokeWidth || 2)}
            onClick={() => onSelectionChange(index)}
            style={{ cursor: "pointer" }}
          />
        );
      default:
        return null;
    }
  };

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Geometry Editor
      </span>

      {/* Canvas */}
      <div className="h-48 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="0 0 100 100" className="w-full h-full">
          {/* Grid */}
          <defs>
            <pattern id="grid" width="10" height="10" patternUnits="userSpaceOnUse">
              <path d="M 10 0 L 0 0 0 10" fill="none" stroke="#e5e7eb" strokeWidth="0.3" />
            </pattern>
          </defs>
          <rect width="100" height="100" fill="url(#grid)" />
          
          {/* Shapes */}
          {shapes.map((shape, index) => renderShape(shape, index))}
        </svg>
      </div>

      {/* Shape Toolbar */}
      <div className="flex flex-wrap gap-1">
        {(["circle", "rectangle", "triangle", "ellipse", "line"] as const).map((type) => (
          <Tooltip key={type} content={`Add ${type}`}>
            <Button
              size="sm"
              variant="outline"
              onClick={() => addShape(type)}
              disabled={disabled}
            >
              {type === "circle" && "○"}
              {type === "rectangle" && "□"}
              {type === "triangle" && "△"}
              {type === "ellipse" && "⬭"}
              {type === "line" && "⟋"}
            </Button>
          </Tooltip>
        ))}
      </div>

      {/* Selected Shape Properties */}
      {selectedShape && (
        <div className="space-y-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium capitalize">{selectedShape.type}</span>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => removeShape(selectedIndex!)}
              disabled={disabled}
            >
              Delete
            </Button>
          </div>

          {/* Position */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block text-xs text-gray-500 mb-1">X</label>
              <input
                type="number"
                value={selectedShape.center.x}
                onChange={(e) => updateShape(selectedIndex!, { 
                  center: { ...selectedShape.center, x: Number(e.target.value) }
                })}
                disabled={disabled}
                className="w-full px-2 py-1 text-xs border border-gray-300 rounded"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Y</label>
              <input
                type="number"
                value={selectedShape.center.y}
                onChange={(e) => updateShape(selectedIndex!, { 
                  center: { ...selectedShape.center, y: Number(e.target.value) }
                })}
                disabled={disabled}
                className="w-full px-2 py-1 text-xs border border-gray-300 rounded"
              />
            </div>
          </div>

          {/* Size parameters based on type */}
          {selectedShape.type === "circle" && (
            <div>
              <label className="block text-xs text-gray-500 mb-1">Radius</label>
              <Slider
                min={5}
                max={45}
                value={selectedShape.radius || 20}
                onChange={(e) => updateShape(selectedIndex!, { radius: Number(e.target.value) })}
                disabled={disabled}
              />
            </div>
          )}

          {(selectedShape.type === "rectangle" || selectedShape.type === "ellipse") && (
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block text-xs text-gray-500 mb-1">Width</label>
                <Slider
                  min={5}
                  max={80}
                  value={selectedShape.width || 40}
                  onChange={(e) => updateShape(selectedIndex!, { width: Number(e.target.value) })}
                  disabled={disabled}
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">Height</label>
                <Slider
                  min={5}
                  max={80}
                  value={selectedShape.height || 30}
                  onChange={(e) => updateShape(selectedIndex!, { height: Number(e.target.value) })}
                  disabled={disabled}
                />
              </div>
            </div>
          )}

          {/* Rotation */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs text-gray-500">Rotation</label>
              <span className="text-xs font-mono">{selectedShape.rotation || 0}°</span>
            </div>
            <Slider
              min={0}
              max={360}
              value={selectedShape.rotation || 0}
              onChange={(e) => updateShape(selectedIndex!, { rotation: Number(e.target.value) })}
              disabled={disabled}
            />
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Function Plotter Widget
// =============================================================================

export interface FunctionPlotterProps {
  functions: FunctionDefinition[];
  onFunctionsChange: (functions: FunctionDefinition[]) => void;
  xRange: { min: number; max: number };
  yRange: { min: number; max: number };
  onRangeChange: (xRange: { min: number; max: number }, yRange: { min: number; max: number }) => void;
  disabled?: boolean;
}

export const FunctionPlotter = ({
  functions,
  onFunctionsChange,
  xRange,
  yRange,
  onRangeChange,
  disabled = false,
}: FunctionPlotterProps) => {
  const [newExpression, setNewExpression] = useState("");

  // Simple expression parser (basic operations)
  const evaluateExpression = useCallback((expr: string, x: number): number | null => {
    try {
      // Replace common math functions and x variable
      let safeExpr = expr
        .replace(/\^/g, "**")
        .replace(/sin/g, "Math.sin")
        .replace(/cos/g, "Math.cos")
        .replace(/tan/g, "Math.tan")
        .replace(/sqrt/g, "Math.sqrt")
        .replace(/log/g, "Math.log")
        .replace(/exp/g, "Math.exp")
        .replace(/abs/g, "Math.abs")
        .replace(/pi/gi, "Math.PI")
        .replace(/e(?![x])/gi, "Math.E")
        .replace(/x/g, `(${x})`);
      
       
      const result = new Function(`return ${safeExpr}`)();
      return typeof result === "number" && isFinite(result) ? result : null;
    } catch {
      return null;
    }
  }, []);

  // Generate path for function
  const generateFunctionPath = useCallback((func: FunctionDefinition): string => {
    const points: string[] = [];
    const steps = 100;
    const xStep = (xRange.max - xRange.min) / steps;

    for (let i = 0; i <= steps; i++) {
      const x = xRange.min + i * xStep;
      const y = evaluateExpression(func.expression, x);
      
      if (y !== null && y >= yRange.min && y <= yRange.max) {
        // Map to SVG coordinates
        const svgX = ((x - xRange.min) / (xRange.max - xRange.min)) * 80 + 10;
        const svgY = 90 - ((y - yRange.min) / (yRange.max - yRange.min)) * 80;
        
        points.push(`${points.length === 0 ? "M" : "L"} ${svgX} ${svgY}`);
      }
    }

    return points.join(" ");
  }, [xRange, yRange, evaluateExpression]);

  const addFunction = useCallback(() => {
    if (!newExpression.trim()) return;
    
    const colors = ["#ef4444", "#22c55e", "#3b82f6", "#f59e0b", "#8b5cf6"];
    const newFunc: FunctionDefinition = {
      expression: newExpression,
      domain: { min: xRange.min, max: xRange.max },
      color: colors[functions.length % colors.length],
      lineWidth: 2,
      label: `f${functions.length + 1}(x)`,
    };
    
    onFunctionsChange([...functions, newFunc]);
    setNewExpression("");
  }, [newExpression, functions, xRange, onFunctionsChange]);

  const removeFunction = useCallback((index: number) => {
    const newFunctions = functions.filter((_, i) => i !== index);
    onFunctionsChange(newFunctions);
  }, [functions, onFunctionsChange]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Function Plotter
      </span>

      {/* Graph Canvas */}
      <div className="h-48 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="0 0 100 100" className="w-full h-full">
          {/* Grid */}
          <defs>
            <pattern id="fnGrid" width="10" height="10" patternUnits="userSpaceOnUse">
              <path d="M 10 0 L 0 0 0 10" fill="none" stroke="#e5e7eb" strokeWidth="0.2" />
            </pattern>
          </defs>
          <rect width="100" height="100" fill="url(#fnGrid)" />

          {/* Axes */}
          {/* X-axis */}
          {yRange.min <= 0 && yRange.max >= 0 && (
            <line
              x1="10"
              y1={90 - ((0 - yRange.min) / (yRange.max - yRange.min)) * 80}
              x2="90"
              y2={90 - ((0 - yRange.min) / (yRange.max - yRange.min)) * 80}
              stroke="#374151"
              strokeWidth="0.5"
            />
          )}
          {/* Y-axis */}
          {xRange.min <= 0 && xRange.max >= 0 && (
            <line
              x1={((0 - xRange.min) / (xRange.max - xRange.min)) * 80 + 10}
              y1="10"
              x2={((0 - xRange.min) / (xRange.max - xRange.min)) * 80 + 10}
              y2="90"
              stroke="#374151"
              strokeWidth="0.5"
            />
          )}

          {/* Functions */}
          {functions.map((func, index) => (
            <path
              key={index}
              d={generateFunctionPath(func)}
              fill="none"
              stroke={func.color}
              strokeWidth={func.lineWidth}
            />
          ))}

          {/* Labels */}
          <text x="50" y="98" fontSize="5" textAnchor="middle" fill="#6b7280">x</text>
          <text x="4" y="50" fontSize="5" textAnchor="middle" fill="#6b7280" transform="rotate(-90, 4, 50)">y</text>
        </svg>
      </div>

      {/* Add Function */}
      <div className="flex gap-2">
        <input
          type="text"
          value={newExpression}
          onChange={(e) => setNewExpression(e.target.value)}
          placeholder="e.g., x^2, sin(x), 2*x+1"
          disabled={disabled}
          className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded"
          onKeyDown={(e) => e.key === "Enter" && addFunction()}
        />
        <Button size="sm" onClick={addFunction} disabled={disabled}>
          Add
        </Button>
      </div>

      {/* Function List */}
      {functions.length > 0 && (
        <div className="space-y-1">
          {functions.map((func, index) => (
            <div
              key={index}
              className="flex items-center justify-between text-xs p-1 bg-white dark:bg-gray-800 rounded"
            >
              <div className="flex items-center gap-2">
                <div
                  className="w-3 h-3 rounded"
                  style={{ backgroundColor: func.color }}
                />
                <span className="font-mono">{func.label}: {func.expression}</span>
              </div>
              <button
                onClick={() => removeFunction(index)}
                disabled={disabled}
                className="text-gray-400 hover:text-red-500"
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Range Controls */}
      <div className="grid grid-cols-2 gap-2 text-xs">
        <div>
          <label className="block text-gray-500 mb-1">X Range</label>
          <div className="flex gap-1">
            <input
              type="number"
              value={xRange.min}
              onChange={(e) => onRangeChange({ ...xRange, min: Number(e.target.value) }, yRange)}
              disabled={disabled}
              className="w-full px-1 py-0.5 border border-gray-300 rounded"
            />
            <span>to</span>
            <input
              type="number"
              value={xRange.max}
              onChange={(e) => onRangeChange({ ...xRange, max: Number(e.target.value) }, yRange)}
              disabled={disabled}
              className="w-full px-1 py-0.5 border border-gray-300 rounded"
            />
          </div>
        </div>
        <div>
          <label className="block text-gray-500 mb-1">Y Range</label>
          <div className="flex gap-1">
            <input
              type="number"
              value={yRange.min}
              onChange={(e) => onRangeChange(xRange, { ...yRange, min: Number(e.target.value) })}
              disabled={disabled}
              className="w-full px-1 py-0.5 border border-gray-300 rounded"
            />
            <span>to</span>
            <input
              type="number"
              value={yRange.max}
              onChange={(e) => onRangeChange(xRange, { ...yRange, max: Number(e.target.value) })}
              disabled={disabled}
              className="w-full px-1 py-0.5 border border-gray-300 rounded"
            />
          </div>
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Matrix Editor Widget
// =============================================================================

export interface MatrixEditorProps {
  value: MatrixValue;
  onChange: (value: MatrixValue) => void;
  label?: string;
  maxSize?: number;
  disabled?: boolean;
}

export const MatrixEditor = ({
  value,
  onChange,
  label = "Matrix",
  maxSize = 5,
  disabled = false,
}: MatrixEditorProps) => {
  // Calculate determinant for square matrices
  const determinant = useMemo(() => {
    if (value.rows !== value.cols || value.rows > 3) return null;
    
    const m = value.data;
    if (value.rows === 1) return m[0][0];
    if (value.rows === 2) return m[0][0] * m[1][1] - m[0][1] * m[1][0];
    if (value.rows === 3) {
      return (
        m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1]) -
        m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0]) +
        m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0])
      );
    }
    return null;
  }, [value]);

  const updateCell = useCallback((row: number, col: number, val: number) => {
    const newData = value.data.map((r, ri) =>
      r.map((c, ci) => (ri === row && ci === col ? val : c))
    );
    onChange({ ...value, data: newData });
  }, [value, onChange]);

  const resizeMatrix = useCallback((rows: number, cols: number) => {
    const newData: number[][] = [];
    for (let r = 0; r < rows; r++) {
      newData[r] = [];
      for (let c = 0; c < cols; c++) {
        newData[r][c] = value.data[r]?.[c] ?? 0;
      }
    }
    onChange({ rows, cols, data: newData });
  }, [value.data, onChange]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">{label}</span>
        {determinant !== null && (
          <Badge tone="secondary">
            det = {determinant.toFixed(2)}
          </Badge>
        )}
      </div>

      {/* Size Controls */}
      <div className="flex items-center gap-4 text-xs">
        <div className="flex items-center gap-2">
          <label className="text-gray-500">Rows:</label>
          <select
            value={value.rows}
            onChange={(e) => resizeMatrix(Number(e.target.value), value.cols)}
            disabled={disabled}
            className="px-2 py-1 border border-gray-300 rounded text-xs"
          >
            {Array.from({ length: maxSize }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </div>
        <span className="text-gray-400">×</span>
        <div className="flex items-center gap-2">
          <label className="text-gray-500">Cols:</label>
          <select
            value={value.cols}
            onChange={(e) => resizeMatrix(value.rows, Number(e.target.value))}
            disabled={disabled}
            className="px-2 py-1 border border-gray-300 rounded text-xs"
          >
            {Array.from({ length: maxSize }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Matrix Grid */}
      <div className="flex items-center gap-1">
        {/* Left bracket */}
        <div className="text-2xl text-gray-400">[</div>
        
        {/* Matrix cells */}
        <div
          className="grid gap-1"
          style={{ gridTemplateColumns: `repeat(${value.cols}, 1fr)` }}
        >
          {value.data.map((row, ri) =>
            row.map((cell, ci) => (
              <input
                key={`${ri}-${ci}`}
                type="number"
                value={cell}
                onChange={(e) => updateCell(ri, ci, Number(e.target.value))}
                disabled={disabled}
                className="w-12 h-8 text-center text-sm border border-gray-300 rounded"
              />
            ))
          )}
        </div>

        {/* Right bracket */}
        <div className="text-2xl text-gray-400">]</div>
      </div>
    </div>
  );
};

// =============================================================================
// Complex Number Widget
// =============================================================================

export interface ComplexNumberWidgetProps {
  value: ComplexNumber;
  onChange: (value: ComplexNumber) => void;
  showPolar?: boolean;
  disabled?: boolean;
}

export const ComplexNumberWidget = ({
  value,
  onChange,
  showPolar = true,
  disabled = false,
}: ComplexNumberWidgetProps) => {
  // Calculate polar form
  const polar = useMemo(() => {
    const r = Math.sqrt(value.real ** 2 + value.imaginary ** 2);
    const theta = Math.atan2(value.imaginary, value.real);
    return { r, theta, thetaDegrees: (theta * 180) / Math.PI };
  }, [value]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Complex Number
      </span>

      {/* Visual representation */}
      <div className="h-32 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="-60 -60 120 120" className="w-full h-full">
          {/* Axes */}
          <line x1="-50" y1="0" x2="50" y2="0" stroke="#9ca3af" strokeWidth="0.5" />
          <line x1="0" y1="-50" x2="0" y2="50" stroke="#9ca3af" strokeWidth="0.5" />
          
          {/* Labels */}
          <text x="52" y="3" fontSize="6" fill="#6b7280">Re</text>
          <text x="2" y="-52" fontSize="6" fill="#6b7280">Im</text>

          {/* Unit circle (dashed) */}
          <circle cx="0" cy="0" r="30" fill="none" stroke="#e5e7eb" strokeWidth="0.5" strokeDasharray="2,2" />

          {/* Vector to complex number */}
          <line
            x1="0"
            y1="0"
            x2={value.real * 5}
            y2={-value.imaginary * 5}
            stroke="#3b82f6"
            strokeWidth="1.5"
            markerEnd="url(#arrowhead)"
          />
          
          {/* Point */}
          <circle cx={value.real * 5} cy={-value.imaginary * 5} r="3" fill="#3b82f6" />

          {/* Arrowhead marker */}
          <defs>
            <marker id="arrowhead" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
              <path d="M0,0 L6,3 L0,6 Z" fill="#3b82f6" />
            </marker>
          </defs>
        </svg>
      </div>

      {/* Rectangular Form Inputs */}
      <div className="grid grid-cols-2 gap-2">
        <div>
          <label className="block text-xs text-gray-500 mb-1">Real (a)</label>
          <input
            type="number"
            value={value.real}
            onChange={(e) => onChange({ ...value, real: Number(e.target.value) })}
            disabled={disabled}
            step={0.1}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Imaginary (b)</label>
          <input
            type="number"
            value={value.imaginary}
            onChange={(e) => onChange({ ...value, imaginary: Number(e.target.value) })}
            disabled={disabled}
            step={0.1}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
          />
        </div>
      </div>

      {/* Display Forms */}
      <div className="text-xs space-y-1 bg-white dark:bg-gray-800 p-2 rounded">
        <div className="flex justify-between">
          <span className="text-gray-500">Rectangular:</span>
          <span className="font-mono">
            {value.real} {value.imaginary >= 0 ? "+" : "-"} {Math.abs(value.imaginary)}i
          </span>
        </div>
        {showPolar && (
          <>
            <div className="flex justify-between">
              <span className="text-gray-500">Polar (r∠θ):</span>
              <span className="font-mono">
                {polar.r.toFixed(2)}∠{polar.thetaDegrees.toFixed(1)}°
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Exponential:</span>
              <span className="font-mono">
                {polar.r.toFixed(2)}e^({polar.theta.toFixed(2)}i)
              </span>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Circuit Component Editor
// =============================================================================

const CIRCUIT_COMPONENT_TYPES: Array<{ type: CircuitComponent["type"]; label: string; symbol: string; unit: string }> = [
  { type: "resistor", label: "Resistor", symbol: "R", unit: "Ω" },
  { type: "capacitor", label: "Capacitor", symbol: "C", unit: "F" },
  { type: "inductor", label: "Inductor", symbol: "L", unit: "H" },
  { type: "voltage-source", label: "Voltage", symbol: "V", unit: "V" },
  { type: "current-source", label: "Current", symbol: "I", unit: "A" },
  { type: "diode", label: "Diode", symbol: "D", unit: "" },
  { type: "ground", label: "Ground", symbol: "⏚", unit: "" },
];

export interface CircuitEditorProps {
  components: CircuitComponent[];
  onComponentsChange: (components: CircuitComponent[]) => void;
  selectedIndex: number | null;
  onSelectionChange: (index: number | null) => void;
  disabled?: boolean;
}

export const CircuitEditor = ({
  components,
  onComponentsChange,
  selectedIndex,
  onSelectionChange,
  disabled = false,
}: CircuitEditorProps) => {
  const addComponent = useCallback((type: CircuitComponent["type"]) => {
    const typeInfo = CIRCUIT_COMPONENT_TYPES.find(t => t.type === type);
    const newComponent: CircuitComponent = {
      type,
      value: type === "resistor" ? 1000 : type === "capacitor" ? 0.000001 : type === "inductor" ? 0.001 : 5,
      unit: typeInfo?.unit || "",
      position: { x: 50, y: 50 },
      rotation: 0,
      connections: ["n1", "n2"],
    };
    onComponentsChange([...components, newComponent]);
    onSelectionChange(components.length);
  }, [components, onComponentsChange, onSelectionChange]);

  const updateComponent = useCallback((index: number, updates: Partial<CircuitComponent>) => {
    const newComponents = [...components];
    newComponents[index] = { ...newComponents[index], ...updates };
    onComponentsChange(newComponents);
  }, [components, onComponentsChange]);

  const selectedComponent = selectedIndex !== null ? components[selectedIndex] : null;
  const selectedTypeInfo = selectedComponent 
    ? CIRCUIT_COMPONENT_TYPES.find(t => t.type === selectedComponent.type)
    : null;

  // Format value with engineering notation
  const formatValue = useCallback((val: number, unit: string): string => {
    if (val >= 1e6) return `${(val / 1e6).toFixed(1)}M${unit}`;
    if (val >= 1e3) return `${(val / 1e3).toFixed(1)}k${unit}`;
    if (val >= 1) return `${val.toFixed(1)}${unit}`;
    if (val >= 1e-3) return `${(val * 1e3).toFixed(1)}m${unit}`;
    if (val >= 1e-6) return `${(val * 1e6).toFixed(1)}μ${unit}`;
    if (val >= 1e-9) return `${(val * 1e9).toFixed(1)}n${unit}`;
    return `${(val * 1e12).toFixed(1)}p${unit}`;
  }, []);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Circuit Editor
      </span>

      {/* Component Palette */}
      <div className="flex flex-wrap gap-1">
        {CIRCUIT_COMPONENT_TYPES.map(({ type, label, symbol }) => (
          <Tooltip key={type} content={label}>
            <Button
              size="sm"
              variant="outline"
              onClick={() => addComponent(type)}
              disabled={disabled}
            >
              {symbol}
            </Button>
          </Tooltip>
        ))}
      </div>

      {/* Component List */}
      <div className="max-h-32 overflow-auto space-y-1">
        {components.map((comp, index) => {
          const typeInfo = CIRCUIT_COMPONENT_TYPES.find(t => t.type === comp.type);
          return (
            <div
              key={index}
              onClick={() => onSelectionChange(index)}
              className={`
                flex items-center justify-between p-2 rounded cursor-pointer
                ${selectedIndex === index 
                  ? "bg-blue-100 dark:bg-blue-900/30 border border-blue-500" 
                  : "bg-white dark:bg-gray-800 hover:bg-gray-100 dark:hover:bg-gray-700"
                }
              `}
            >
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm">{typeInfo?.symbol}</span>
                <span className="text-xs text-gray-500">{typeInfo?.label}</span>
              </div>
              <span className="text-xs font-mono">
                {comp.type !== "ground" && comp.type !== "diode" && formatValue(comp.value, comp.unit)}
              </span>
            </div>
          );
        })}
      </div>

      {/* Selected Component Properties */}
      {selectedComponent && selectedTypeInfo && (
        <div className="space-y-2 p-2 bg-blue-50 dark:bg-blue-900/20 rounded">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium">{selectedTypeInfo.label}</span>
            <button
              onClick={() => {
                const newComponents = components.filter((_, i) => i !== selectedIndex);
                onComponentsChange(newComponents);
                onSelectionChange(null);
              }}
              disabled={disabled}
              className="text-xs text-red-500 hover:underline"
            >
              Delete
            </button>
          </div>

          {/* Value Input */}
          {selectedComponent.type !== "ground" && selectedComponent.type !== "diode" && (
            <div>
              <label className="block text-xs text-gray-500 mb-1">
                Value ({selectedTypeInfo.unit})
              </label>
              <input
                type="number"
                value={selectedComponent.value}
                onChange={(e) => updateComponent(selectedIndex!, { value: Number(e.target.value) })}
                disabled={disabled}
                step="any"
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
              />
              <span className="text-xs text-gray-400">
                = {formatValue(selectedComponent.value, selectedTypeInfo.unit)}
              </span>
            </div>
          )}

          {/* Rotation */}
          <div>
            <label className="block text-xs text-gray-500 mb-1">Rotation</label>
            <div className="flex gap-1">
              {([0, 90, 180, 270] as const).map((rot) => (
                <button
                  key={rot}
                  onClick={() => updateComponent(selectedIndex!, { rotation: rot })}
                  disabled={disabled}
                  className={`
                    flex-1 px-2 py-1 text-xs border rounded
                    ${selectedComponent.rotation === rot
                      ? "bg-blue-500 text-white border-blue-500"
                      : "bg-white border-gray-300"
                    }
                  `}
                >
                  {rot}°
                </button>
              ))}
            </div>
          </div>

          {/* Node Connections */}
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Node 1</label>
              <input
                type="text"
                value={selectedComponent.connections[0]}
                onChange={(e) => updateComponent(selectedIndex!, { 
                  connections: [e.target.value, selectedComponent.connections[1]]
                })}
                disabled={disabled}
                className="w-full px-2 py-1 text-xs border border-gray-300 rounded"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Node 2</label>
              <input
                type="text"
                value={selectedComponent.connections[1]}
                onChange={(e) => updateComponent(selectedIndex!, { 
                  connections: [selectedComponent.connections[0], e.target.value]
                })}
                disabled={disabled}
                className="w-full px-2 py-1 text-xs border border-gray-300 rounded"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const MathEngineeringWidgets = {
  GeometryShapeEditor,
  FunctionPlotter,
  MatrixEditor,
  ComplexNumberWidget,
  CircuitEditor,
};

export default MathEngineeringWidgets;
