/**
 * Physics Parameter Widgets
 * 
 * Specialized widgets for physics simulation parameters.
 * Includes vector editors, unit converters, and force diagrams.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for physics simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import { useState, useCallback, useMemo } from "react";
import { Badge, Slider, Tooltip } from "@ghatana/design-system";

// =============================================================================
// Types
// =============================================================================

export interface Vector2D {
  x: number;
  y: number;
}

export interface Vector3D extends Vector2D {
  z: number;
}

export interface PhysicsUnits {
  length: "m" | "cm" | "mm" | "km";
  mass: "kg" | "g" | "mg";
  time: "s" | "ms";
  force: "N" | "kN" | "mN";
  velocity: "m/s" | "km/h" | "mph";
}

// =============================================================================
// Unit Conversion Utilities
// =============================================================================

const LENGTH_CONVERSIONS: Record<PhysicsUnits["length"], number> = {
  m: 1,
  cm: 0.01,
  mm: 0.001,
  km: 1000,
};

const MASS_CONVERSIONS: Record<PhysicsUnits["mass"], number> = {
  kg: 1,
  g: 0.001,
  mg: 0.000001,
};

const FORCE_CONVERSIONS: Record<PhysicsUnits["force"], number> = {
  N: 1,
  kN: 1000,
  mN: 0.001,
};

const VELOCITY_CONVERSIONS: Record<PhysicsUnits["velocity"], number> = {
  "m/s": 1,
  "km/h": 1 / 3.6,
  mph: 0.44704,
};

// Export unit conversions for external use
export const UNIT_CONVERSIONS = {
  length: LENGTH_CONVERSIONS,
  mass: MASS_CONVERSIONS,
  force: FORCE_CONVERSIONS,
  velocity: VELOCITY_CONVERSIONS,
};

function convertValue(
  value: number,
  fromUnit: string,
  toUnit: string,
  conversions: Record<string, number>
): number {
  const inBase = value * conversions[fromUnit];
  return inBase / conversions[toUnit];
}

// =============================================================================
// Vector Input Widget
// =============================================================================

export interface VectorInputProps {
  value: Vector2D | Vector3D;
  onChange: (value: Vector2D | Vector3D) => void;
  label?: string;
  unit?: string;
  is3D?: boolean;
  min?: number;
  max?: number;
  step?: number;
  showMagnitude?: boolean;
  showAngle?: boolean;
  disabled?: boolean;
}

export const VectorInput = ({
  value,
  onChange,
  label,
  unit = "m/s",
  is3D = false,
  min = -1000,
  max = 1000,
  step = 0.1,
  showMagnitude = true,
  showAngle = true,
  disabled = false,
}: VectorInputProps) => {
  const magnitude = useMemo(() => {
    const mag = Math.sqrt(
      value.x * value.x + 
      value.y * value.y + 
      ((value as Vector3D).z ?? 0) * ((value as Vector3D).z ?? 0)
    );
    return Math.round(mag * 100) / 100;
  }, [value]);

  const angle = useMemo(() => {
    const rad = Math.atan2(value.y, value.x);
    return Math.round((rad * 180) / Math.PI);
  }, [value.x, value.y]);

  const handleComponentChange = useCallback(
    (component: "x" | "y" | "z", newValue: number) => {
      onChange({ ...value, [component]: newValue });
    },
    [value, onChange]
  );

  const handleMagnitudeChange = useCallback(
    (newMagnitude: number) => {
      if (magnitude === 0) {
        onChange({ ...value, x: newMagnitude, y: 0 });
        return;
      }
      const scale = newMagnitude / magnitude;
      onChange({
        x: value.x * scale,
        y: value.y * scale,
        ...((value as Vector3D).z !== undefined && {
          z: (value as Vector3D).z * scale,
        }),
      });
    },
    [value, magnitude, onChange]
  );

  const handleAngleChange = useCallback(
    (newAngle: number) => {
      const rad = (newAngle * Math.PI) / 180;
      onChange({
        ...value,
        x: Math.round(magnitude * Math.cos(rad) * 100) / 100,
        y: Math.round(magnitude * Math.sin(rad) * 100) / 100,
      });
    },
    [value, magnitude, onChange]
  );

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      {label && (
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {label}
          </span>
          <Badge variant="soft">
            {unit}
          </Badge>
        </div>
      )}

      {/* Component Inputs */}
      <div className={`grid gap-2 ${is3D ? "grid-cols-3" : "grid-cols-2"}`}>
        <div>
          <label className="block text-xs text-gray-500 mb-1">X</label>
          <input
            type="number"
            value={value.x}
            onChange={(e) => handleComponentChange("x", Number(e.target.value))}
            disabled={disabled}
            min={min}
            max={max}
            step={step}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Y</label>
          <input
            type="number"
            value={value.y}
            onChange={(e) => handleComponentChange("y", Number(e.target.value))}
            disabled={disabled}
            min={min}
            max={max}
            step={step}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
          />
        </div>
        {is3D && (
          <div>
            <label className="block text-xs text-gray-500 mb-1">Z</label>
            <input
              type="number"
              value={(value as Vector3D).z ?? 0}
              onChange={(e) => handleComponentChange("z", Number(e.target.value))}
              disabled={disabled}
              min={min}
              max={max}
              step={step}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
            />
          </div>
        )}
      </div>

      {/* Magnitude & Angle */}
      {(showMagnitude || showAngle) && (
        <div className="grid grid-cols-2 gap-2 pt-2 border-t border-gray-200 dark:border-gray-700">
          {showMagnitude && (
            <div>
              <label className="block text-xs text-gray-500 mb-1">
                Magnitude
              </label>
              <input
                type="number"
                value={magnitude}
                onChange={(e) => handleMagnitudeChange(Number(e.target.value))}
                disabled={disabled}
                min={0}
                step={step}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
              />
            </div>
          )}
          {showAngle && !is3D && (
            <div>
              <label className="block text-xs text-gray-500 mb-1">
                Angle (°)
              </label>
              <input
                type="number"
                value={angle}
                onChange={(e) => handleAngleChange(Number(e.target.value))}
                disabled={disabled}
                min={-180}
                max={180}
                step={1}
                className="w-full px-2 py-1 text-sm border border-gray-300 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
              />
            </div>
          )}
        </div>
      )}

      {/* Visual Preview */}
      <div className="h-20 relative border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="-50 -50 100 100" className="w-full h-full">
          {/* Grid */}
          <line
            x1="-45"
            y1="0"
            x2="45"
            y2="0"
            stroke="#e5e7eb"
            strokeWidth="0.5"
          />
          <line
            x1="0"
            y1="-45"
            x2="0"
            y2="45"
            stroke="#e5e7eb"
            strokeWidth="0.5"
          />

          {/* Vector Arrow */}
          <line
            x1="0"
            y1="0"
            x2={Math.max(-40, Math.min(40, value.x * 2))}
            y2={Math.max(-40, Math.min(40, -value.y * 2))} // Flip Y for SVG coords
            stroke="#3b82f6"
            strokeWidth="2"
            markerEnd="url(#arrowhead)"
          />

          {/* Arrowhead marker */}
          <defs>
            <marker
              id="arrowhead"
              markerWidth="10"
              markerHeight="7"
              refX="9"
              refY="3.5"
              orient="auto"
            >
              <polygon points="0 0, 10 3.5, 0 7" fill="#3b82f6" />
            </marker>
          </defs>
        </svg>
      </div>
    </div>
  );
};

// =============================================================================
// Gravity Widget
// =============================================================================

export interface GravityWidgetProps {
  value: Vector2D;
  onChange: (value: Vector2D) => void;
  disabled?: boolean;
}

const GRAVITY_PRESETS = [
  { label: "Earth", value: { x: 0, y: -9.81 } },
  { label: "Moon", value: { x: 0, y: -1.62 } },
  { label: "Mars", value: { x: 0, y: -3.71 } },
  { label: "Jupiter", value: { x: 0, y: -24.79 } },
  { label: "Zero-G", value: { x: 0, y: 0 } },
  { label: "Custom", value: null },
];

export const GravityWidget = ({
  value,
  onChange,
  disabled = false,
}: GravityWidgetProps) => {
  const [customMode, setCustomMode] = useState(false);

  const currentPreset = useMemo(() => {
    const found = GRAVITY_PRESETS.find(
      (p) => p.value?.x === value.x && p.value?.y === value.y
    );
    return found?.label || "Custom";
  }, [value]);

  const handlePresetChange = useCallback(
    (presetLabel: string) => {
      const preset = GRAVITY_PRESETS.find((p) => p.label === presetLabel);
      if (preset?.value) {
        onChange(preset.value);
        setCustomMode(false);
      } else {
        setCustomMode(true);
      }
    },
    [onChange]
  );

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Gravity
        </span>
        <Badge variant="soft">
          m/s²
        </Badge>
      </div>

      {/* Preset Selector */}
      <div className="flex flex-wrap gap-1">
        {GRAVITY_PRESETS.map((preset) => (
          <button
            key={preset.label}
            onClick={() => handlePresetChange(preset.label)}
            disabled={disabled}
            className={`
              px-2 py-1 text-xs rounded border transition-colors
              ${
                currentPreset === preset.label
                  ? "bg-blue-500 text-white border-blue-500"
                  : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-blue-400"
              }
            `}
          >
            {preset.label}
          </button>
        ))}
      </div>

      {/* Custom Input */}
      {customMode && (
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="block text-xs text-gray-500 mb-1">gx</label>
            <input
              type="number"
              value={value.x}
              onChange={(e) => onChange({ ...value, x: Number(e.target.value) })}
              disabled={disabled}
              step={0.01}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">gy</label>
            <input
              type="number"
              value={value.y}
              onChange={(e) => onChange({ ...value, y: Number(e.target.value) })}
              disabled={disabled}
              step={0.01}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
        </div>
      )}

      {/* Current Value Display */}
      <div className="text-xs text-gray-500 text-center">
        g = ({value.x.toFixed(2)}, {value.y.toFixed(2)}) m/s²
      </div>
    </div>
  );
};

// =============================================================================
// Mass Input Widget
// =============================================================================

export interface MassInputProps {
  value: number;
  onChange: (value: number) => void;
  unit?: PhysicsUnits["mass"];
  onUnitChange?: (unit: PhysicsUnits["mass"]) => void;
  min?: number;
  max?: number;
  disabled?: boolean;
}

export const MassInput = ({
  value,
  onChange,
  unit = "kg",
  onUnitChange,
  min = 0,
  max = 1e6,
  disabled = false,
}: MassInputProps) => {
  const handleUnitChange = useCallback(
    (newUnit: PhysicsUnits["mass"]) => {
      if (onUnitChange) {
        // Convert value to new unit
        const convertedValue = convertValue(value, unit, newUnit, MASS_CONVERSIONS);
        onChange(Math.round(convertedValue * 1000) / 1000);
        onUnitChange(newUnit);
      }
    },
    [value, unit, onChange, onUnitChange]
  );

  return (
    <div className="space-y-2">
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300">
        Mass
      </label>
      <div className="flex gap-2">
        <input
          type="number"
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          disabled={disabled}
          min={min}
          max={max}
          step={0.001}
          className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-800 dark:border-gray-600"
        />
        {onUnitChange ? (
          <select
            value={unit}
            onChange={(e) => handleUnitChange(e.target.value as PhysicsUnits["mass"])}
            disabled={disabled}
            className="px-2 py-1 border border-gray-300 rounded-md text-sm dark:bg-gray-800 dark:border-gray-600"
          >
            <option value="kg">kg</option>
            <option value="g">g</option>
            <option value="mg">mg</option>
          </select>
        ) : (
          <span className="px-3 py-2 bg-gray-100 dark:bg-gray-700 border border-gray-300 dark:border-gray-600 rounded-md text-sm">
            {unit}
          </span>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Spring Constants Widget
// =============================================================================

export interface SpringConstantsProps {
  stiffness: number;
  damping: number;
  restLength: number;
  onStiffnessChange: (value: number) => void;
  onDampingChange: (value: number) => void;
  onRestLengthChange: (value: number) => void;
  disabled?: boolean;
}

export const SpringConstants = ({
  stiffness,
  damping,
  restLength,
  onStiffnessChange,
  onDampingChange,
  onRestLengthChange,
  disabled = false,
}: SpringConstantsProps) => {
  // Calculate natural frequency and damping ratio
  const naturalFrequency = useMemo(() => {
    // Assuming unit mass for display
    return Math.sqrt(stiffness);
  }, [stiffness]);

  const dampingRatio = useMemo(() => {
    // ζ = c / (2 * sqrt(k * m)), assuming m = 1
    return damping / (2 * naturalFrequency);
  }, [damping, naturalFrequency]);

  const dampingType = useMemo(() => {
    if (dampingRatio < 0.01) return "Undamped";
    if (dampingRatio < 1) return "Underdamped";
    if (dampingRatio === 1) return "Critically Damped";
    return "Overdamped";
  }, [dampingRatio]);

  return (
    <div className="space-y-4 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Spring Properties
        </span>
        <Badge
          variant="soft"
          tone={dampingRatio < 1 ? "info" : dampingRatio === 1 ? "success" : "warning"}
        >
          {dampingType}
        </Badge>
      </div>

      {/* Stiffness */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Stiffness (k)</label>
          <span className="text-xs text-gray-600">{stiffness.toFixed(1)} N/m</span>
        </div>
        <Slider
          min={0}
          max={1000}
          value={stiffness}
          onChange={(e) => onStiffnessChange(Number(e.target.value))}
          disabled={disabled}
        />
      </div>

      {/* Damping */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Damping (c)</label>
          <span className="text-xs text-gray-600">{damping.toFixed(2)} Ns/m</span>
        </div>
        <Slider
          min={0}
          max={100}
          value={damping}
          onChange={(e) => onDampingChange(Number(e.target.value))}
          disabled={disabled}
        />
      </div>

      {/* Rest Length */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Rest Length</label>
          <span className="text-xs text-gray-600">{restLength.toFixed(2)} m</span>
        </div>
        <Slider
          min={0}
          max={10}
          value={restLength}
          onChange={(e) => onRestLengthChange(Number(e.target.value))}
          disabled={disabled}
        />
      </div>

      {/* Derived Values */}
      <div className="text-xs text-gray-500 grid grid-cols-2 gap-2 pt-2 border-t border-gray-200 dark:border-gray-700">
        <div>ωn = {naturalFrequency.toFixed(2)} rad/s</div>
        <div>ζ = {dampingRatio.toFixed(3)}</div>
      </div>
    </div>
  );
};

// =============================================================================
// Friction Coefficient Widget
// =============================================================================

export interface FrictionCoefficientProps {
  staticFriction: number;
  kineticFriction: number;
  onStaticChange: (value: number) => void;
  onKineticChange: (value: number) => void;
  disabled?: boolean;
}

const MATERIAL_PRESETS = [
  { label: "Ice", static: 0.03, kinetic: 0.01 },
  { label: "Wood/Wood", static: 0.5, kinetic: 0.3 },
  { label: "Rubber/Concrete", static: 1.0, kinetic: 0.8 },
  { label: "Steel/Steel", static: 0.74, kinetic: 0.57 },
  { label: "Glass/Glass", static: 0.94, kinetic: 0.4 },
];

export const FrictionCoefficient = ({
  staticFriction,
  kineticFriction,
  onStaticChange,
  onKineticChange,
  disabled = false,
}: FrictionCoefficientProps) => {
  const applyPreset = useCallback(
    (preset: typeof MATERIAL_PRESETS[0]) => {
      onStaticChange(preset.static);
      onKineticChange(preset.kinetic);
    },
    [onStaticChange, onKineticChange]
  );

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Friction Coefficients
      </span>

      {/* Material Presets */}
      <div className="flex flex-wrap gap-1">
        {MATERIAL_PRESETS.map((preset) => (
          <Tooltip key={preset.label} content={`μs=${preset.static}, μk=${preset.kinetic}`}>
            <button
              onClick={() => applyPreset(preset)}
              disabled={disabled}
              className="px-2 py-1 text-xs bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded hover:border-blue-400 transition-colors"
            >
              {preset.label}
            </button>
          </Tooltip>
        ))}
      </div>

      {/* Coefficient Sliders */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Static (μs)</label>
            <span className="text-xs font-mono">{staticFriction.toFixed(2)}</span>
          </div>
          <Slider
            min={0}
            max={2}
            value={staticFriction}
            onChange={(e) => onStaticChange(Number(e.target.value))}
            disabled={disabled}
          />
        </div>
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Kinetic (μk)</label>
            <span className="text-xs font-mono">{kineticFriction.toFixed(2)}</span>
          </div>
          <Slider
            min={0}
            max={staticFriction}
            value={kineticFriction}
            onChange={(e) => onKineticChange(Number(e.target.value))}
            disabled={disabled}
          />
        </div>
      </div>

      {kineticFriction > staticFriction && (
        <p className="text-xs text-amber-600">
          ⚠️ Kinetic friction should not exceed static friction
        </p>
      )}
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const PhysicsParameterWidgets = {
  VectorInput,
  GravityWidget,
  MassInput,
  SpringConstants,
  FrictionCoefficient,
};

export default PhysicsParameterWidgets;
