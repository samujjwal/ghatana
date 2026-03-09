/**
 * BioMed Parameter Widgets
 * 
 * Specialized widgets for biology and medicine simulation parameters.
 * Includes compartment models, gene expression, PK/PD, and epidemiology.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for biology/medicine simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import { useCallback, useMemo } from "react";
import { Badge, Slider, Tooltip } from "@ghatana/ui";

// =============================================================================
// Types
// =============================================================================

// Biology Types
export interface CompartmentConfig {
  volume: number;
  volumeUnit: "L" | "mL" | "μL";
  concentration: Record<string, number>;
  permeability?: Record<string, number>;
}

export interface GeneExpressionState {
  promoterActive: boolean;
  expressionLevel: number; // 0-100
  transcriptionRate: number; // molecules/s
  translationRate: number; // proteins/mRNA/s
  mRnaHalfLife: number; // minutes
  proteinHalfLife: number; // hours
}

export interface TransportConfig {
  type: "passive" | "active" | "facilitated";
  rate: number;
  km?: number; // Michaelis constant for facilitated
  vmax?: number; // Max velocity for facilitated
  atpCost?: number; // for active transport
}

// Medicine/PK-PD Types
export interface PKCompartmentConfig {
  type: "central" | "peripheral" | "effect";
  volume: number; // L
  initialConcentration: number;
  ke?: number; // elimination rate constant (h⁻¹)
  k12?: number; // transfer rate to peripheral
  k21?: number; // transfer rate from peripheral
}

export interface DosingConfig {
  amount: number;
  unit: "mg" | "g" | "μg" | "mg/kg";
  route: "iv" | "oral" | "im" | "sc" | "topical";
  bioavailability: number; // 0-1
  absorptionRate?: number; // for non-IV routes (h⁻¹)
}

export interface SIRParameters {
  population: number;
  initialInfected: number;
  beta: number; // transmission rate
  gamma: number; // recovery rate
  mu?: number; // mortality rate
  sigma?: number; // latency rate (for SEIR)
}

// =============================================================================
// Compartment Volume Widget (Biology)
// =============================================================================

export interface CompartmentVolumeWidgetProps {
  value: CompartmentConfig;
  onChange: (value: CompartmentConfig) => void;
  compartmentType?: "cell" | "organelle" | "extracellular" | "custom";
  disabled?: boolean;
}

const COMPARTMENT_PRESETS: Record<string, { volume: number; unit: "L" | "mL" | "μL" }> = {
  "Red Blood Cell": { volume: 90, unit: "μL" },
  "E. coli": { volume: 1, unit: "μL" },
  "Hepatocyte": { volume: 3700, unit: "μL" },
  "Mitochondrion": { volume: 0.5, unit: "μL" },
  "Nucleus": { volume: 500, unit: "μL" },
  "Blood Plasma": { volume: 3, unit: "L" },
  "Interstitial Fluid": { volume: 12, unit: "L" },
};

export const CompartmentVolumeWidget = ({
  value,
  onChange,
  disabled = false,
}: CompartmentVolumeWidgetProps) => {
  const handlePresetClick = useCallback(
    (preset: { volume: number; unit: "L" | "mL" | "μL" }) => {
      onChange({ ...value, volume: preset.volume, volumeUnit: preset.unit });
    },
    [value, onChange]
  );

  const addMolecule = useCallback(
    (molecule: string) => {
      onChange({
        ...value,
        concentration: { ...value.concentration, [molecule]: 0 },
      });
    },
    [value, onChange]
  );

  const updateConcentration = useCallback(
    (molecule: string, conc: number) => {
      onChange({
        ...value,
        concentration: { ...value.concentration, [molecule]: conc },
      });
    },
    [value, onChange]
  );

  const removeMolecule = useCallback(
    (molecule: string) => {
      const { [molecule]: removed, ...rest } = value.concentration;
      void removed; // Intentionally unused - extracting to remove from object
      onChange({ ...value, concentration: rest });
    },
    [value, onChange]
  );

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Compartment
      </span>

      {/* Volume Input */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Volume</label>
        <div className="flex gap-2">
          <input
            type="number"
            value={value.volume}
            onChange={(e) => onChange({ ...value, volume: Number(e.target.value) })}
            disabled={disabled}
            min={0}
            step={0.1}
            className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          />
          <select
            value={value.volumeUnit}
            onChange={(e) => onChange({ ...value, volumeUnit: e.target.value as "L" | "mL" | "μL" })}
            disabled={disabled}
            className="px-2 py-1 text-sm border border-gray-300 rounded dark:bg-gray-800 dark:border-gray-600"
          >
            <option value="L">L</option>
            <option value="mL">mL</option>
            <option value="μL">μL</option>
          </select>
        </div>
      </div>

      {/* Presets */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Presets</label>
        <div className="flex flex-wrap gap-1">
          {Object.entries(COMPARTMENT_PRESETS).map(([name, preset]) => (
            <Tooltip key={name} content={`${preset.volume} ${preset.unit}`}>
              <button
                onClick={() => handlePresetClick(preset)}
                disabled={disabled}
                className="px-2 py-1 text-xs bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded hover:border-blue-400 transition-colors"
              >
                {name}
              </button>
            </Tooltip>
          ))}
        </div>
      </div>

      {/* Concentrations */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Concentrations (mM)</label>
          {!disabled && (
            <button
              onClick={() => addMolecule(`Molecule ${Object.keys(value.concentration).length + 1}`)}
              className="text-xs text-blue-500 hover:text-blue-700"
            >
              + Add
            </button>
          )}
        </div>
        <div className="space-y-1 max-h-32 overflow-y-auto">
          {Object.entries(value.concentration).map(([molecule, conc]) => (
            <div key={molecule} className="flex items-center gap-2">
              <input
                type="text"
                value={molecule}
                onChange={(e) => {
                  const { [molecule]: oldConc, ...rest } = value.concentration;
                  onChange({
                    ...value,
                    concentration: { ...rest, [e.target.value]: oldConc },
                  });
                }}
                disabled={disabled}
                className="w-24 px-1 py-0.5 text-xs border border-gray-300 rounded"
              />
              <input
                type="number"
                value={conc}
                onChange={(e) => updateConcentration(molecule, Number(e.target.value))}
                disabled={disabled}
                min={0}
                step={0.01}
                className="w-16 px-1 py-0.5 text-xs border border-gray-300 rounded"
              />
              {!disabled && (
                <button
                  onClick={() => removeMolecule(molecule)}
                  className="text-red-500 hover:text-red-700 text-xs"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
          {Object.keys(value.concentration).length === 0 && (
            <p className="text-xs text-gray-400 italic">No molecules defined</p>
          )}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Gene Expression Widget
// =============================================================================

export interface GeneExpressionWidgetProps {
  value: GeneExpressionState;
  onChange: (value: GeneExpressionState) => void;
  geneName?: string;
  disabled?: boolean;
}

export const GeneExpressionWidget = ({
  value,
  onChange,
  geneName = "Gene",
  disabled = false,
}: GeneExpressionWidgetProps) => {
  // Calculate steady-state levels
  const steadyStateMRNA = useMemo(() => {
    if (!value.promoterActive) return 0;
    const degradationRate = Math.log(2) / value.mRnaHalfLife;
    return value.transcriptionRate / degradationRate;
  }, [value.promoterActive, value.transcriptionRate, value.mRnaHalfLife]);

  const steadyStateProtein = useMemo(() => {
    const degradationRate = Math.log(2) / (value.proteinHalfLife * 60); // convert to minutes
    return (steadyStateMRNA * value.translationRate) / degradationRate;
  }, [steadyStateMRNA, value.translationRate, value.proteinHalfLife]);

  return (
    <div className="space-y-4 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {geneName} Expression
        </span>
        <Badge
          tone={value.promoterActive ? "success" : "secondary"}
        >
          {value.promoterActive ? "Active" : "Inactive"}
        </Badge>
      </div>

      {/* Promoter Toggle */}
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={value.promoterActive}
          onChange={(e) => onChange({ ...value, promoterActive: e.target.checked })}
          disabled={disabled}
          className="rounded border-gray-300"
        />
        <span className="text-sm">Promoter Active</span>
      </label>

      {/* Expression Level */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Expression Level</label>
          <span className="text-xs font-mono">{value.expressionLevel}%</span>
        </div>
        <Slider
          min={0}
          max={100}
          value={value.expressionLevel}
          onChange={(e) => onChange({ ...value, expressionLevel: Number(e.target.value) })}
          disabled={disabled || !value.promoterActive}
        />
      </div>

      {/* Transcription Rate */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Transcription Rate</label>
          <span className="text-xs font-mono">{value.transcriptionRate} mol/s</span>
        </div>
        <Slider
          min={0}
          max={100}
          value={value.transcriptionRate}
          onChange={(e) => onChange({ ...value, transcriptionRate: Number(e.target.value) })}
          disabled={disabled || !value.promoterActive}
        />
      </div>

      {/* Translation Rate */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Translation Rate</label>
          <span className="text-xs font-mono">{value.translationRate} prot/mRNA/s</span>
        </div>
        <Slider
          min={0}
          max={50}
          value={value.translationRate}
          onChange={(e) => onChange({ ...value, translationRate: Number(e.target.value) })}
          disabled={disabled || !value.promoterActive}
        />
      </div>

      {/* Half-lives */}
      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs text-gray-500 mb-1">mRNA t½ (min)</label>
          <input
            type="number"
            value={value.mRnaHalfLife}
            onChange={(e) => onChange({ ...value, mRnaHalfLife: Number(e.target.value) })}
            disabled={disabled}
            min={0.1}
            step={0.5}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
          />
        </div>
        <div>
          <label className="block text-xs text-gray-500 mb-1">Protein t½ (h)</label>
          <input
            type="number"
            value={value.proteinHalfLife}
            onChange={(e) => onChange({ ...value, proteinHalfLife: Number(e.target.value) })}
            disabled={disabled}
            min={0.1}
            step={0.5}
            className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
          />
        </div>
      </div>

      {/* Steady State Preview */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700">
        <div>Steady-state mRNA: ~{Math.round(steadyStateMRNA)} molecules</div>
        <div>Steady-state Protein: ~{Math.round(steadyStateProtein)} molecules</div>
      </div>
    </div>
  );
};

// =============================================================================
// PK Compartment Widget
// =============================================================================

export interface PKCompartmentWidgetProps {
  value: PKCompartmentConfig;
  onChange: (value: PKCompartmentConfig) => void;
  disabled?: boolean;
}

export const PKCompartmentWidget = ({
  value,
  onChange,
  disabled = false,
}: PKCompartmentWidgetProps) => {
  // Calculate half-life from ke
  const halfLife = useMemo(() => {
    if (!value.ke || value.ke === 0) return Infinity;
    return Math.log(2) / value.ke;
  }, [value.ke]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          PK Compartment
        </span>
        <Badge variant="outline">
          {value.type}
        </Badge>
      </div>

      {/* Compartment Type */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Type</label>
        <div className="flex gap-1">
          {(["central", "peripheral", "effect"] as const).map((type) => (
            <button
              key={type}
              onClick={() => onChange({ ...value, type })}
              disabled={disabled}
              className={`
                flex-1 px-2 py-1 text-xs border rounded transition-colors capitalize
                ${
                  value.type === type
                    ? "bg-blue-500 text-white border-blue-500"
                    : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-blue-400"
                }
              `}
            >
              {type}
            </button>
          ))}
        </div>
      </div>

      {/* Volume of Distribution */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">
          Volume of Distribution (L)
        </label>
        <input
          type="number"
          value={value.volume}
          onChange={(e) => onChange({ ...value, volume: Number(e.target.value) })}
          disabled={disabled}
          min={0}
          step={0.1}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        />
      </div>

      {/* Initial Concentration */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">
          Initial Concentration (mg/L)
        </label>
        <input
          type="number"
          value={value.initialConcentration}
          onChange={(e) => onChange({ ...value, initialConcentration: Number(e.target.value) })}
          disabled={disabled}
          min={0}
          step={0.01}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        />
      </div>

      {/* Elimination Rate (for central) */}
      {value.type === "central" && (
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Elimination Rate (ke)</label>
            <span className="text-xs font-mono">{value.ke?.toFixed(3)} h⁻¹</span>
          </div>
          <Slider
            min={0}
            max={2}
            value={value.ke || 0}
            onChange={(e) => onChange({ ...value, ke: Number(e.target.value) })}
            disabled={disabled}
          />
          <div className="text-xs text-gray-400 mt-1">
            t½ = {isFinite(halfLife) ? `${halfLife.toFixed(1)} h` : "∞"}
          </div>
        </div>
      )}

      {/* Transfer rates (for peripheral) */}
      {value.type === "peripheral" && (
        <>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs text-gray-500">k₁₂ (to peripheral)</label>
              <span className="text-xs font-mono">{value.k12?.toFixed(3)} h⁻¹</span>
            </div>
            <Slider
              min={0}
              max={1}
              value={value.k12 || 0}
              onChange={(e) => onChange({ ...value, k12: Number(e.target.value) })}
              disabled={disabled}
            />
          </div>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs text-gray-500">k₂₁ (from peripheral)</label>
              <span className="text-xs font-mono">{value.k21?.toFixed(3)} h⁻¹</span>
            </div>
            <Slider
              min={0}
              max={1}
              value={value.k21 || 0}
              onChange={(e) => onChange({ ...value, k21: Number(e.target.value) })}
              disabled={disabled}
            />
          </div>
        </>
      )}
    </div>
  );
};

// =============================================================================
// Dosing Widget
// =============================================================================

export interface DosingWidgetProps {
  value: DosingConfig;
  onChange: (value: DosingConfig) => void;
  disabled?: boolean;
}

export const DosingWidget = ({
  value,
  onChange,
  disabled = false,
}: DosingWidgetProps) => {
  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Dosing
      </span>

      {/* Dose Amount */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Dose</label>
        <div className="flex gap-2">
          <input
            type="number"
            value={value.amount}
            onChange={(e) => onChange({ ...value, amount: Number(e.target.value) })}
            disabled={disabled}
            min={0}
            className="flex-1 px-2 py-1 text-sm border border-gray-300 rounded"
          />
          <select
            value={value.unit}
            onChange={(e) => onChange({ ...value, unit: e.target.value as "mg" | "g" | "μg" | "mg/kg" })}
            disabled={disabled}
            className="px-2 py-1 text-sm border border-gray-300 rounded"
          >
            <option value="mg">mg</option>
            <option value="g">g</option>
            <option value="μg">μg</option>
            <option value="mg/kg">mg/kg</option>
          </select>
        </div>
      </div>

      {/* Route of Administration */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Route</label>
        <div className="flex flex-wrap gap-1">
          {(["iv", "oral", "im", "sc", "topical"] as const).map((route) => (
            <button
              key={route}
              onClick={() => onChange({ ...value, route })}
              disabled={disabled}
              className={`
                px-2 py-1 text-xs border rounded uppercase transition-colors
                ${
                  value.route === route
                    ? "bg-blue-500 text-white border-blue-500"
                    : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-blue-400"
                }
              `}
            >
              {route}
            </button>
          ))}
        </div>
      </div>

      {/* Bioavailability (for non-IV) */}
      {value.route !== "iv" && (
        <>
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs text-gray-500">Bioavailability (F)</label>
              <span className="text-xs font-mono">{(value.bioavailability * 100).toFixed(0)}%</span>
            </div>
            <Slider
              min={0}
              max={1}
              value={value.bioavailability}
              onChange={(e) => onChange({ ...value, bioavailability: Number(e.target.value) })}
              disabled={disabled}
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs text-gray-500">Absorption Rate (ka)</label>
              <span className="text-xs font-mono">{value.absorptionRate?.toFixed(2)} h⁻¹</span>
            </div>
            <Slider
              min={0}
              max={5}
              value={value.absorptionRate || 0}
              onChange={(e) => onChange({ ...value, absorptionRate: Number(e.target.value) })}
              disabled={disabled}
            />
          </div>
        </>
      )}

      {/* Effective Dose */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700">
        Effective dose: {(value.amount * value.bioavailability).toFixed(2)} {value.unit}
      </div>
    </div>
  );
};

// =============================================================================
// SIR Model Widget (Epidemiology)
// =============================================================================

export interface SIRModelWidgetProps {
  value: SIRParameters;
  onChange: (value: SIRParameters) => void;
  modelType?: "SIR" | "SEIR" | "SIS";
  disabled?: boolean;
}

export const SIRModelWidget = ({
  value,
  onChange,
  modelType = "SIR",
  disabled = false,
}: SIRModelWidgetProps) => {
  // Calculate R0 (basic reproduction number)
  const R0 = useMemo(() => {
    return value.beta / value.gamma;
  }, [value.beta, value.gamma]);

  // Calculate herd immunity threshold
  const herdImmunityThreshold = useMemo(() => {
    if (R0 <= 1) return 0;
    return 1 - 1 / R0;
  }, [R0]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {modelType} Model
        </span>
        <Badge
          tone={R0 > 1 ? "danger" : "success"}
        >
          R₀ = {R0.toFixed(2)}
        </Badge>
      </div>

      {/* Population */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Population (N)</label>
        <input
          type="number"
          value={value.population}
          onChange={(e) => onChange({ ...value, population: Number(e.target.value) })}
          disabled={disabled}
          min={1}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        />
      </div>

      {/* Initial Infected */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Initial Infected (I₀)</label>
          <span className="text-xs font-mono">{value.initialInfected}</span>
        </div>
        <Slider
          min={1}
          max={value.population / 10}
          value={value.initialInfected}
          onChange={(e) => onChange({ ...value, initialInfected: Number(e.target.value) })}
          disabled={disabled}
        />
      </div>

      {/* Transmission Rate */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Transmission Rate (β)</label>
          <span className="text-xs font-mono">{value.beta.toFixed(3)}</span>
        </div>
        <Slider
          min={0}
          max={1}
          value={value.beta}
          onChange={(e) => onChange({ ...value, beta: Number(e.target.value) })}
          disabled={disabled}
        />
      </div>

      {/* Recovery Rate */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Recovery Rate (γ)</label>
          <span className="text-xs font-mono">{value.gamma.toFixed(3)}</span>
        </div>
        <Slider
          min={0.01}
          max={1}
          value={value.gamma}
          onChange={(e) => onChange({ ...value, gamma: Number(e.target.value) })}
          disabled={disabled}
        />
        <div className="text-xs text-gray-400 mt-1">
          Avg. infectious period: {(1 / value.gamma).toFixed(1)} days
        </div>
      </div>

      {/* Latency Rate (SEIR only) */}
      {modelType === "SEIR" && (
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Latency Rate (σ)</label>
            <span className="text-xs font-mono">{value.sigma?.toFixed(3)}</span>
          </div>
          <Slider
            min={0}
            max={1}
            value={value.sigma || 0}
            onChange={(e) => onChange({ ...value, sigma: Number(e.target.value) })}
            disabled={disabled}
          />
          <div className="text-xs text-gray-400 mt-1">
            Avg. latent period: {value.sigma ? (1 / value.sigma).toFixed(1) : "∞"} days
          </div>
        </div>
      )}

      {/* Derived Values */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700 space-y-1">
        <div className="flex justify-between">
          <span>R₀ (Basic Reproduction Number):</span>
          <span className={R0 > 1 ? "text-red-500" : "text-green-500"}>{R0.toFixed(2)}</span>
        </div>
        <div className="flex justify-between">
          <span>Herd Immunity Threshold:</span>
          <span>{(herdImmunityThreshold * 100).toFixed(1)}%</span>
        </div>
        <div className="flex justify-between">
          <span>Outbreak likely:</span>
          <span>{R0 > 1 ? "Yes ⚠️" : "No ✓"}</span>
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const BioMedParameterWidgets = {
  CompartmentVolumeWidget,
  GeneExpressionWidget,
  PKCompartmentWidget,
  DosingWidget,
  SIRModelWidget,
};

export default BioMedParameterWidgets;
