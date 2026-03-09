/**
 * Chemistry Parameter Widgets
 * 
 * Specialized widgets for chemistry simulation parameters.
 * Includes molecule selectors, reaction conditions, and bond editors.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for chemistry simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import { useState, useCallback, useMemo } from "react";
import { Button, Slider, Tooltip } from "@ghatana/ui";

// =============================================================================
// Types
// =============================================================================

export interface ReactionConditions {
  temperature: number;
  temperatureUnit: "K" | "C" | "F";
  pressure: number;
  pressureUnit: "atm" | "Pa" | "bar" | "mmHg";
  solvent?: string;
  catalyst?: string;
  ph?: number;
}

export interface BondConfig {
  bondOrder: 1 | 2 | 3 | 1.5;
  bondType: "covalent" | "ionic" | "hydrogen" | "metallic";
  stereochemistry?: "up" | "down" | "none";
}

export interface AtomConfig {
  element: string;
  charge?: number;
  isotope?: number;
  hybridization?: "sp" | "sp2" | "sp3" | "sp3d" | "sp3d2";
}

// =============================================================================
// Constants
// =============================================================================

const COMMON_SOLVENTS = [
  { value: "water", label: "Water (H₂O)", polar: true },
  { value: "ethanol", label: "Ethanol (C₂H₅OH)", polar: true },
  { value: "methanol", label: "Methanol (CH₃OH)", polar: true },
  { value: "acetone", label: "Acetone ((CH₃)₂CO)", polar: true },
  { value: "dmso", label: "DMSO ((CH₃)₂SO)", polar: true },
  { value: "thf", label: "THF (C₄H₈O)", polar: false },
  { value: "dcm", label: "DCM (CH₂Cl₂)", polar: false },
  { value: "hexane", label: "Hexane (C₆H₁₄)", polar: false },
  { value: "benzene", label: "Benzene (C₆H₆)", polar: false },
  { value: "ether", label: "Diethyl Ether ((C₂H₅)₂O)", polar: false },
];

const COMMON_CATALYSTS = [
  { value: "h2so4", label: "H₂SO₄ (Sulfuric Acid)" },
  { value: "hcl", label: "HCl (Hydrochloric Acid)" },
  { value: "naoh", label: "NaOH (Sodium Hydroxide)" },
  { value: "pd_c", label: "Pd/C (Palladium on Carbon)" },
  { value: "pt", label: "Pt (Platinum)" },
  { value: "ni", label: "Ni (Nickel)" },
  { value: "alcl3", label: "AlCl₃ (Aluminum Chloride)" },
  { value: "fecl3", label: "FeCl₃ (Iron(III) Chloride)" },
  { value: "bf3", label: "BF₃ (Boron Trifluoride)" },
  { value: "enzyme", label: "Enzyme" },
];

const PERIODIC_TABLE_COMMON = [
  { symbol: "H", name: "Hydrogen", number: 1, group: "nonmetal" },
  { symbol: "C", name: "Carbon", number: 6, group: "nonmetal" },
  { symbol: "N", name: "Nitrogen", number: 7, group: "nonmetal" },
  { symbol: "O", name: "Oxygen", number: 8, group: "nonmetal" },
  { symbol: "F", name: "Fluorine", number: 9, group: "halogen" },
  { symbol: "Cl", name: "Chlorine", number: 17, group: "halogen" },
  { symbol: "Br", name: "Bromine", number: 35, group: "halogen" },
  { symbol: "I", name: "Iodine", number: 53, group: "halogen" },
  { symbol: "S", name: "Sulfur", number: 16, group: "nonmetal" },
  { symbol: "P", name: "Phosphorus", number: 15, group: "nonmetal" },
  { symbol: "Na", name: "Sodium", number: 11, group: "alkali" },
  { symbol: "K", name: "Potassium", number: 19, group: "alkali" },
  { symbol: "Mg", name: "Magnesium", number: 12, group: "alkaline" },
  { symbol: "Ca", name: "Calcium", number: 20, group: "alkaline" },
  { symbol: "Fe", name: "Iron", number: 26, group: "transition" },
  { symbol: "Cu", name: "Copper", number: 29, group: "transition" },
  { symbol: "Zn", name: "Zinc", number: 30, group: "transition" },
];

// =============================================================================
// Temperature Conversion Utilities
// =============================================================================

function celsiusToKelvin(c: number): number {
  return c + 273.15;
}

function kelvinToCelsius(k: number): number {
  return k - 273.15;
}

function celsiusToFahrenheit(c: number): number {
  return (c * 9) / 5 + 32;
}

function fahrenheitToCelsius(f: number): number {
  return ((f - 32) * 5) / 9;
}

function convertTemperature(
  value: number,
  from: "K" | "C" | "F",
  to: "K" | "C" | "F"
): number {
  if (from === to) return value;

  // First convert to Celsius
  let celsius: number;
  switch (from) {
    case "K":
      celsius = kelvinToCelsius(value);
      break;
    case "F":
      celsius = fahrenheitToCelsius(value);
      break;
    default:
      celsius = value;
  }

  // Then convert to target
  switch (to) {
    case "K":
      return celsiusToKelvin(celsius);
    case "F":
      return celsiusToFahrenheit(celsius);
    default:
      return celsius;
  }
}

// =============================================================================
// Reaction Conditions Widget
// =============================================================================

export interface ReactionConditionsWidgetProps {
  value: ReactionConditions;
  onChange: (value: ReactionConditions) => void;
  disabled?: boolean;
}

export const ReactionConditionsWidget = ({
  value,
  onChange,
  disabled = false,
}: ReactionConditionsWidgetProps) => {
  const handleTemperatureChange = useCallback(
    (temp: number) => {
      onChange({ ...value, temperature: temp });
    },
    [value, onChange]
  );

  const handleTemperatureUnitChange = useCallback(
    (newUnit: "K" | "C" | "F") => {
      const convertedTemp = convertTemperature(
        value.temperature,
        value.temperatureUnit,
        newUnit
      );
      onChange({
        ...value,
        temperature: Math.round(convertedTemp * 10) / 10,
        temperatureUnit: newUnit,
      });
    },
    [value, onChange]
  );

  const conditionsSummary = useMemo(() => {
    const parts: string[] = [];
    parts.push(`${value.temperature}${value.temperatureUnit === "K" ? " K" : `°${value.temperatureUnit}`}`);
    parts.push(`${value.pressure} ${value.pressureUnit}`);
    if (value.solvent) parts.push(value.solvent);
    if (value.catalyst) parts.push(`cat: ${value.catalyst}`);
    return parts.join(", ");
  }, [value]);

  return (
    <div className="space-y-4 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Reaction Conditions
        </span>
      </div>

      {/* Temperature */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Temperature</label>
        </div>
        <div className="flex gap-2">
          <input
            type="number"
            value={value.temperature}
            onChange={(e) => handleTemperatureChange(Number(e.target.value))}
            disabled={disabled}
            className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          />
          <select
            value={value.temperatureUnit}
            onChange={(e) => handleTemperatureUnitChange(e.target.value as "K" | "C" | "F")}
            disabled={disabled}
            className="px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          >
            <option value="K">K</option>
            <option value="C">°C</option>
            <option value="F">°F</option>
          </select>
        </div>
        <Slider
          min={value.temperatureUnit === "K" ? 0 : value.temperatureUnit === "C" ? -273 : -460}
          max={value.temperatureUnit === "K" ? 1000 : value.temperatureUnit === "C" ? 727 : 1340}
          value={value.temperature}
          onChange={(e) => handleTemperatureChange(Number((e.target as HTMLInputElement).value))}
          disabled={disabled}
        />
      </div>

      {/* Pressure */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Pressure</label>
        </div>
        <div className="flex gap-2">
          <input
            type="number"
            value={value.pressure}
            onChange={(e) => onChange({ ...value, pressure: Number(e.target.value) })}
            disabled={disabled}
            min={0}
            step={0.1}
            className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          />
          <select
            value={value.pressureUnit}
            onChange={(e) =>
              onChange({ ...value, pressureUnit: e.target.value as ReactionConditions["pressureUnit"] })
            }
            disabled={disabled}
            className="px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          >
            <option value="atm">atm</option>
            <option value="Pa">Pa</option>
            <option value="bar">bar</option>
            <option value="mmHg">mmHg</option>
          </select>
        </div>
      </div>

      {/* Solvent */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Solvent</label>
        <select
          value={value.solvent || ""}
          onChange={(e) => onChange({ ...value, solvent: e.target.value || undefined })}
          disabled={disabled}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
        >
          <option value="">None / Neat</option>
          <optgroup label="Polar Solvents">
            {COMMON_SOLVENTS.filter((s) => s.polar).map((solvent) => (
              <option key={solvent.value} value={solvent.value}>
                {solvent.label}
              </option>
            ))}
          </optgroup>
          <optgroup label="Non-Polar Solvents">
            {COMMON_SOLVENTS.filter((s) => !s.polar).map((solvent) => (
              <option key={solvent.value} value={solvent.value}>
                {solvent.label}
              </option>
            ))}
          </optgroup>
        </select>
      </div>

      {/* Catalyst */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Catalyst</label>
        <select
          value={value.catalyst || ""}
          onChange={(e) => onChange({ ...value, catalyst: e.target.value || undefined })}
          disabled={disabled}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
        >
          <option value="">None</option>
          {COMMON_CATALYSTS.map((catalyst) => (
            <option key={catalyst.value} value={catalyst.value}>
              {catalyst.label}
            </option>
          ))}
        </select>
      </div>

      {/* pH */}
      {value.solvent && (
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">pH</label>
            <span className="text-xs font-mono">
              {value.ph?.toFixed(1) ?? "7.0"}
            </span>
          </div>
          <Slider
            min={0}
            max={14}
            value={value.ph ?? 7}
            onChange={(e) => onChange({ ...value, ph: Number((e.target as HTMLInputElement).value) })}
            disabled={disabled}
          />
          <div className="flex justify-between text-xs text-gray-400 mt-1">
            <span>Acidic</span>
            <span>Neutral</span>
            <span>Basic</span>
          </div>
        </div>
      )}

      {/* Summary */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700">
        <span className="font-medium">Conditions: </span>
        {conditionsSummary}
      </div>
    </div>
  );
};

// =============================================================================
// Element Picker Widget
// =============================================================================

export interface ElementPickerProps {
  value: string;
  onChange: (element: string) => void;
  disabled?: boolean;
}

export const ElementPicker = ({
  value,
  onChange,
  disabled = false,
}: ElementPickerProps) => {
  const [showAll, setShowAll] = useState(false);

  const groupColors: Record<string, string> = {
    nonmetal: "bg-green-100 text-green-800 border-green-300",
    halogen: "bg-yellow-100 text-yellow-800 border-yellow-300",
    alkali: "bg-red-100 text-red-800 border-red-300",
    alkaline: "bg-orange-100 text-orange-800 border-orange-300",
    transition: "bg-blue-100 text-blue-800 border-blue-300",
  };

  return (
    <div className="space-y-2 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Element
        </span>
        <Button size="sm" variant="ghost" onClick={() => setShowAll(!showAll)}>
          {showAll ? "Common" : "All"}
        </Button>
      </div>

      <div className="flex flex-wrap gap-1">
        {PERIODIC_TABLE_COMMON.map((element) => (
          <Tooltip
            key={element.symbol}
            content={`${element.name} (${element.number})`}
          >
            <button
              onClick={() => onChange(element.symbol)}
              disabled={disabled}
              className={`
                w-10 h-10 text-sm font-bold border rounded transition-colors
                ${
                  value === element.symbol
                    ? "ring-2 ring-blue-500"
                    : ""
                }
                ${groupColors[element.group] || "bg-gray-100 text-gray-800 border-gray-300"}
              `}
            >
              {element.symbol}
            </button>
          </Tooltip>
        ))}
      </div>

      {/* Custom Input */}
      <div className="flex gap-2 pt-2 border-t border-gray-200 dark:border-gray-700">
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value.toUpperCase())}
          disabled={disabled}
          maxLength={2}
          placeholder="Custom"
          className="w-16 px-2 py-1 text-sm text-center border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
        />
        <span className="text-xs text-gray-500 self-center">
          Enter element symbol
        </span>
      </div>
    </div>
  );
};

// =============================================================================
// Bond Editor Widget
// =============================================================================

export interface BondEditorProps {
  value: BondConfig;
  onChange: (config: BondConfig) => void;
  disabled?: boolean;
}

export const BondEditor = ({
  value,
  onChange,
  disabled = false,
}: BondEditorProps) => {
  const bondOrderDisplay: Record<number, string> = {
    1: "Single (—)",
    1.5: "Aromatic (⋯)",
    2: "Double (=)",
    3: "Triple (≡)",
  };

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Bond Configuration
      </span>

      {/* Bond Order */}
      <div>
        <label className="block text-xs text-gray-500 mb-2">Bond Order</label>
        <div className="flex gap-1">
          {([1, 1.5, 2, 3] as const).map((order) => (
            <button
              key={order}
              onClick={() => onChange({ ...value, bondOrder: order })}
              disabled={disabled}
              className={`
                flex-1 px-2 py-2 text-xs font-medium border rounded transition-colors
                ${
                  value.bondOrder === order
                    ? "bg-blue-500 text-white border-blue-500"
                    : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-blue-400"
                }
              `}
            >
              {bondOrderDisplay[order]}
            </button>
          ))}
        </div>
      </div>

      {/* Bond Type */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Bond Type</label>
        <select
          value={value.bondType}
          onChange={(e) =>
            onChange({ ...value, bondType: e.target.value as BondConfig["bondType"] })
          }
          disabled={disabled}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
        >
          <option value="covalent">Covalent</option>
          <option value="ionic">Ionic</option>
          <option value="hydrogen">Hydrogen</option>
          <option value="metallic">Metallic</option>
        </select>
      </div>

      {/* Stereochemistry (for single bonds) */}
      {value.bondOrder === 1 && (
        <div>
          <label className="block text-xs text-gray-500 mb-2">
            Stereochemistry
          </label>
          <div className="flex gap-1">
            {(["none", "up", "down"] as const).map((stereo) => (
              <button
                key={stereo}
                onClick={() => onChange({ ...value, stereochemistry: stereo })}
                disabled={disabled}
                className={`
                  flex-1 px-2 py-1 text-xs border rounded transition-colors
                  ${
                    value.stereochemistry === stereo
                      ? "bg-blue-500 text-white border-blue-500"
                      : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-blue-400"
                  }
                `}
              >
                {stereo === "none" ? "Flat" : stereo === "up" ? "Wedge ▲" : "Dash ▽"}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Visual Preview */}
      <div className="h-12 flex items-center justify-center border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <span className="text-2xl font-mono">
          {value.bondOrder === 1 && "A — B"}
          {value.bondOrder === 1.5 && "A ⋯ B"}
          {value.bondOrder === 2 && "A = B"}
          {value.bondOrder === 3 && "A ≡ B"}
        </span>
      </div>
    </div>
  );
};

// =============================================================================
// Energy Profile Widget
// =============================================================================

export interface EnergyProfilePoint {
  x: number;
  y: number;
  label?: string;
}

export interface EnergyProfileWidgetProps {
  points: EnergyProfilePoint[];
  onChange: (points: EnergyProfilePoint[]) => void;
  activationEnergy?: number;
  deltaH?: number;
  disabled?: boolean;
}

export const EnergyProfileWidget = ({
  points,
  onChange,
  activationEnergy,
  deltaH,
  disabled = false,
}: EnergyProfileWidgetProps) => {
  const handleAddPoint = useCallback(() => {
    const lastPoint = points[points.length - 1];
    const newPoint: EnergyProfilePoint = {
      x: (lastPoint?.x ?? 0) + 1,
      y: lastPoint?.y ?? 0,
    };
    onChange([...points, newPoint]);
  }, [points, onChange]);

  const handleRemovePoint = useCallback(
    (index: number) => {
      const newPoints = points.filter((_, i) => i !== index);
      onChange(newPoints);
    },
    [points, onChange]
  );

  const handlePointChange = useCallback(
    (index: number, updates: Partial<EnergyProfilePoint>) => {
      const newPoints = points.map((p, i) =>
        i === index ? { ...p, ...updates } : p
      );
      onChange(newPoints);
    },
    [points, onChange]
  );

  // Calculate SVG path
  const pathD = useMemo(() => {
    if (points.length < 2) return "";

    const minX = Math.min(...points.map((p) => p.x));
    const maxX = Math.max(...points.map((p) => p.x));
    const minY = Math.min(...points.map((p) => p.y));
    const maxY = Math.max(...points.map((p) => p.y));

    const xScale = (x: number) => ((x - minX) / (maxX - minX || 1)) * 180 + 10;
    const yScale = (y: number) => 90 - ((y - minY) / (maxY - minY || 1)) * 80;

    return points
      .map((p, i) => `${i === 0 ? "M" : "L"} ${xScale(p.x)} ${yScale(p.y)}`)
      .join(" ");
  }, [points]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Energy Profile
        </span>
        {!disabled && (
          <Button size="sm" variant="ghost" onClick={handleAddPoint}>
            + Point
          </Button>
        )}
      </div>

      {/* SVG Preview */}
      <div className="h-24 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="0 0 200 100" className="w-full h-full">
          {/* Axes */}
          <line x1="10" y1="90" x2="190" y2="90" stroke="#9ca3af" strokeWidth="1" />
          <line x1="10" y1="10" x2="10" y2="90" stroke="#9ca3af" strokeWidth="1" />

          {/* Path */}
          <path
            d={pathD}
            fill="none"
            stroke="#3b82f6"
            strokeWidth="2"
            strokeLinejoin="round"
          />

          {/* Points */}
          {points.map((point, i) => {
            const minX = Math.min(...points.map((p) => p.x));
            const maxX = Math.max(...points.map((p) => p.x));
            const minY = Math.min(...points.map((p) => p.y));
            const maxY = Math.max(...points.map((p) => p.y));

            const cx = ((point.x - minX) / (maxX - minX || 1)) * 180 + 10;
            const cy = 90 - ((point.y - minY) / (maxY - minY || 1)) * 80;

            return (
              <circle
                key={i}
                cx={cx}
                cy={cy}
                r="4"
                fill="#3b82f6"
                className="cursor-pointer hover:fill-blue-700"
              />
            );
          })}

          {/* Labels */}
          <text x="100" y="98" fontSize="8" textAnchor="middle" fill="#6b7280">
            Reaction Progress
          </text>
          <text x="5" y="50" fontSize="8" textAnchor="middle" fill="#6b7280" transform="rotate(-90, 5, 50)">
            Energy
          </text>
        </svg>
      </div>

      {/* Points List */}
      <div className="space-y-1 max-h-32 overflow-y-auto">
        {points.map((point, i) => (
          <div key={i} className="flex items-center gap-2 text-xs">
            <input
              type="text"
              value={point.label || `Point ${i + 1}`}
              onChange={(e) => handlePointChange(i, { label: e.target.value })}
              disabled={disabled}
              className="w-20 px-1 py-0.5 border border-gray-300 rounded"
              placeholder="Label"
            />
            <input
              type="number"
              value={point.y}
              onChange={(e) => handlePointChange(i, { y: Number(e.target.value) })}
              disabled={disabled}
              className="w-16 px-1 py-0.5 border border-gray-300 rounded"
              placeholder="E"
            />
            <span className="text-gray-400">kJ/mol</span>
            {!disabled && (
              <button
                onClick={() => handleRemovePoint(i)}
                className="text-red-500 hover:text-red-700"
              >
                ✕
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Derived Values */}
      {(activationEnergy !== undefined || deltaH !== undefined) && (
        <div className="text-xs text-gray-500 grid grid-cols-2 gap-2 pt-2 border-t border-gray-200 dark:border-gray-700">
          {activationEnergy !== undefined && (
            <div>Ea = {activationEnergy.toFixed(1)} kJ/mol</div>
          )}
          {deltaH !== undefined && (
            <div>ΔH = {deltaH.toFixed(1)} kJ/mol</div>
          )}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const ChemistryParameterWidgets = {
  ReactionConditionsWidget,
  ElementPicker,
  BondEditor,
  EnergyProfileWidget,
};

export default ChemistryParameterWidgets;
