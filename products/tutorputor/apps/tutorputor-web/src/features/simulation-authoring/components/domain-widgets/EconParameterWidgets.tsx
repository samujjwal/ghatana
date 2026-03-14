/**
 * Economics Parameter Widgets
 * 
 * Specialized widgets for economics simulation parameters.
 * Includes supply/demand, market dynamics, and policy interventions.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific parameter widgets for economics simulations
 * @doc.layer product
 * @doc.pattern Widget
 */

import { useMemo } from "react";
import { Badge, Slider, Tooltip } from "@ghatana/design-system";

// =============================================================================
// Types
// =============================================================================

export interface SupplyDemandCurve {
  intercept: number; // price at quantity 0
  slope: number; // change in price per unit quantity (negative for demand)
  elasticity?: number; // price elasticity
}

export interface MarketEquilibrium {
  equilibriumPrice: number;
  equilibriumQuantity: number;
  consumerSurplus: number;
  producerSurplus: number;
  totalSurplus: number;
}

export interface TaxSubsidyConfig {
  type: "tax" | "subsidy";
  amount: number;
  amountType: "specific" | "ad-valorem"; // specific = per unit, ad-valorem = percentage
  incidence: "buyer" | "seller" | "split";
}

export interface PriceControlConfig {
  type: "floor" | "ceiling";
  price: number;
  enabled: boolean;
}

export interface TimeDelayConfig {
  adjustmentSpeed: number; // 0-1, how fast market adjusts
  informationLag: number; // periods of delay
  expectationsType: "naive" | "adaptive" | "rational";
}

export interface MacroParameters {
  gdp: number;
  inflationRate: number;
  unemploymentRate: number;
  interestRate: number;
  exchangeRate: number;
  moneySupply: number;
}

// =============================================================================
// Supply/Demand Elasticity Widget
// =============================================================================

export interface SupplyDemandWidgetProps {
  supply: SupplyDemandCurve;
  demand: SupplyDemandCurve;
  onSupplyChange: (supply: SupplyDemandCurve) => void;
  onDemandChange: (demand: SupplyDemandCurve) => void;
  disabled?: boolean;
}

export const SupplyDemandWidget = ({
  supply,
  demand,
  onSupplyChange,
  onDemandChange,
  disabled = false,
}: SupplyDemandWidgetProps) => {
  // Calculate equilibrium
  const equilibrium = useMemo((): MarketEquilibrium | null => {
    // At equilibrium: supply.intercept + supply.slope * Q = demand.intercept + demand.slope * Q
    // Q* = (demand.intercept - supply.intercept) / (supply.slope - demand.slope)
    const denominator = supply.slope - demand.slope;
    if (Math.abs(denominator) < 0.001) return null; // Parallel lines

    const equilibriumQuantity = (demand.intercept - supply.intercept) / denominator;
    const equilibriumPrice = supply.intercept + supply.slope * equilibriumQuantity;

    if (equilibriumQuantity < 0 || equilibriumPrice < 0) return null;

    // Consumer surplus = 0.5 * (demand.intercept - P*) * Q*
    const consumerSurplus = 0.5 * (demand.intercept - equilibriumPrice) * equilibriumQuantity;
    
    // Producer surplus = 0.5 * (P* - supply.intercept) * Q*
    const producerSurplus = 0.5 * (equilibriumPrice - supply.intercept) * equilibriumQuantity;

    return {
      equilibriumPrice: Math.round(equilibriumPrice * 100) / 100,
      equilibriumQuantity: Math.round(equilibriumQuantity * 100) / 100,
      consumerSurplus: Math.round(consumerSurplus * 100) / 100,
      producerSurplus: Math.round(producerSurplus * 100) / 100,
      totalSurplus: Math.round((consumerSurplus + producerSurplus) * 100) / 100,
    };
  }, [supply, demand]);

  // Generate SVG path for curves
  const generateCurvePath = (curve: SupplyDemandCurve, maxQ: number): string => {
    const points: string[] = [];
    const steps = 20;

    for (let i = 0; i <= steps; i++) {
      const q = (i / steps) * maxQ;
      const p = curve.intercept + curve.slope * q;
      
      // Scale to SVG coordinates (0-100 for both axes)
      const x = (q / maxQ) * 80 + 10;
      const y = 90 - (p / 200) * 80; // Assuming max price of 200
      
      points.push(`${i === 0 ? "M" : "L"} ${x} ${Math.max(10, Math.min(90, y))}`);
    }

    return points.join(" ");
  };

  const maxQ = equilibrium ? equilibrium.equilibriumQuantity * 2 : 100;

  return (
    <div className="space-y-4 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Supply & Demand
      </span>

      {/* Graph Preview */}
      <div className="h-40 border border-gray-200 dark:border-gray-700 rounded bg-white dark:bg-gray-800">
        <svg viewBox="0 0 100 100" className="w-full h-full">
          {/* Axes */}
          <line x1="10" y1="90" x2="90" y2="90" stroke="#9ca3af" strokeWidth="0.5" />
          <line x1="10" y1="10" x2="10" y2="90" stroke="#9ca3af" strokeWidth="0.5" />
          
          {/* Labels */}
          <text x="50" y="98" fontSize="6" textAnchor="middle" fill="#6b7280">Quantity</text>
          <text x="5" y="50" fontSize="6" textAnchor="middle" fill="#6b7280" transform="rotate(-90, 5, 50)">Price</text>

          {/* Supply curve */}
          <path
            d={generateCurvePath(supply, maxQ)}
            fill="none"
            stroke="#22c55e"
            strokeWidth="1.5"
          />
          <text x="85" y="30" fontSize="5" fill="#22c55e">S</text>

          {/* Demand curve */}
          <path
            d={generateCurvePath(demand, maxQ)}
            fill="none"
            stroke="#ef4444"
            strokeWidth="1.5"
          />
          <text x="85" y="75" fontSize="5" fill="#ef4444">D</text>

          {/* Equilibrium point */}
          {equilibrium && (
            <>
              <circle
                cx={(equilibrium.equilibriumQuantity / maxQ) * 80 + 10}
                cy={90 - (equilibrium.equilibriumPrice / 200) * 80}
                r="3"
                fill="#3b82f6"
              />
              {/* Dashed lines to axes */}
              <line
                x1={(equilibrium.equilibriumQuantity / maxQ) * 80 + 10}
                y1={90 - (equilibrium.equilibriumPrice / 200) * 80}
                x2={(equilibrium.equilibriumQuantity / maxQ) * 80 + 10}
                y2="90"
                stroke="#3b82f6"
                strokeWidth="0.5"
                strokeDasharray="2,2"
              />
              <line
                x1="10"
                y1={90 - (equilibrium.equilibriumPrice / 200) * 80}
                x2={(equilibrium.equilibriumQuantity / maxQ) * 80 + 10}
                y2={90 - (equilibrium.equilibriumPrice / 200) * 80}
                stroke="#3b82f6"
                strokeWidth="0.5"
                strokeDasharray="2,2"
              />
            </>
          )}
        </svg>
      </div>

      {/* Supply Curve Controls */}
      <div className="space-y-2">
        <label className="text-xs font-medium text-green-600">Supply Curve</label>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Intercept</label>
            <input
              type="number"
              value={supply.intercept}
              onChange={(e) => onSupplyChange({ ...supply, intercept: Number(e.target.value) })}
              disabled={disabled}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Slope</label>
            <input
              type="number"
              value={supply.slope}
              onChange={(e) => onSupplyChange({ ...supply, slope: Number(e.target.value) })}
              disabled={disabled}
              step={0.1}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
        </div>
      </div>

      {/* Demand Curve Controls */}
      <div className="space-y-2">
        <label className="text-xs font-medium text-red-600">Demand Curve</label>
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="block text-xs text-gray-500 mb-1">Intercept</label>
            <input
              type="number"
              value={demand.intercept}
              onChange={(e) => onDemandChange({ ...demand, intercept: Number(e.target.value) })}
              disabled={disabled}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-500 mb-1">Slope</label>
            <input
              type="number"
              value={demand.slope}
              onChange={(e) => onDemandChange({ ...demand, slope: Number(e.target.value) })}
              disabled={disabled}
              step={0.1}
              className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
            />
          </div>
        </div>
      </div>

      {/* Equilibrium Results */}
      {equilibrium && (
        <div className="text-xs bg-blue-50 dark:bg-blue-900/20 p-2 rounded space-y-1">
          <div className="font-medium text-blue-700 dark:text-blue-400">Equilibrium</div>
          <div className="grid grid-cols-2 gap-x-4">
            <span>Price: ${equilibrium.equilibriumPrice}</span>
            <span>Quantity: {equilibrium.equilibriumQuantity}</span>
            <span>Consumer Surplus: ${equilibrium.consumerSurplus}</span>
            <span>Producer Surplus: ${equilibrium.producerSurplus}</span>
          </div>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Tax/Subsidy Widget
// =============================================================================

export interface TaxSubsidyWidgetProps {
  value: TaxSubsidyConfig;
  onChange: (value: TaxSubsidyConfig) => void;
  disabled?: boolean;
}

export const TaxSubsidyWidget = ({
  value,
  onChange,
  disabled = false,
}: TaxSubsidyWidgetProps) => {
  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Tax / Subsidy
        </span>
        <Badge
          tone={value.type === "tax" ? "danger" : "success"}
        >
          {value.type === "tax" ? "Tax" : "Subsidy"}
        </Badge>
      </div>

      {/* Type Toggle */}
      <div className="flex gap-1">
        <button
          onClick={() => onChange({ ...value, type: "tax" })}
          disabled={disabled}
          className={`
            flex-1 px-3 py-2 text-sm border rounded transition-colors
            ${
              value.type === "tax"
                ? "bg-red-500 text-white border-red-500"
                : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-red-400"
            }
          `}
        >
          Tax
        </button>
        <button
          onClick={() => onChange({ ...value, type: "subsidy" })}
          disabled={disabled}
          className={`
            flex-1 px-3 py-2 text-sm border rounded transition-colors
            ${
              value.type === "subsidy"
                ? "bg-green-500 text-white border-green-500"
                : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600 hover:border-green-400"
            }
          `}
        >
          Subsidy
        </button>
      </div>

      {/* Amount Type */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Type</label>
        <div className="flex gap-1">
          <button
            onClick={() => onChange({ ...value, amountType: "specific" })}
            disabled={disabled}
            className={`
              flex-1 px-2 py-1 text-xs border rounded transition-colors
              ${
                value.amountType === "specific"
                  ? "bg-blue-500 text-white border-blue-500"
                  : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
              }
            `}
          >
            Per Unit ($)
          </button>
          <button
            onClick={() => onChange({ ...value, amountType: "ad-valorem" })}
            disabled={disabled}
            className={`
              flex-1 px-2 py-1 text-xs border rounded transition-colors
              ${
                value.amountType === "ad-valorem"
                  ? "bg-blue-500 text-white border-blue-500"
                  : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
              }
            `}
          >
            Percentage (%)
          </button>
        </div>
      </div>

      {/* Amount */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Amount</label>
          <span className="text-xs font-mono">
            {value.amountType === "specific" ? `$${value.amount}` : `${value.amount}%`}
          </span>
        </div>
        <Slider
          min={0}
          max={value.amountType === "specific" ? 50 : 100}
          value={value.amount}
          onChange={(e) => onChange({ ...value, amount: Number(e.target.value) })}
          disabled={disabled}
        />
      </div>

      {/* Incidence */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Statutory Incidence</label>
        <div className="flex gap-1">
          {(["buyer", "seller", "split"] as const).map((incidence) => (
            <button
              key={incidence}
              onClick={() => onChange({ ...value, incidence })}
              disabled={disabled}
              className={`
                flex-1 px-2 py-1 text-xs border rounded capitalize transition-colors
                ${
                  value.incidence === incidence
                    ? "bg-blue-500 text-white border-blue-500"
                    : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                }
              `}
            >
              {incidence}
            </button>
          ))}
        </div>
      </div>

      {/* Info */}
      <p className="text-xs text-gray-400 italic">
        Note: Economic incidence depends on relative elasticities
      </p>
    </div>
  );
};

// =============================================================================
// Price Control Widget
// =============================================================================

export interface PriceControlWidgetProps {
  value: PriceControlConfig;
  onChange: (value: PriceControlConfig) => void;
  equilibriumPrice?: number;
  disabled?: boolean;
}

export const PriceControlWidget = ({
  value,
  onChange,
  equilibriumPrice,
  disabled = false,
}: PriceControlWidgetProps) => {
  const isBinding = useMemo(() => {
    if (!equilibriumPrice) return false;
    if (value.type === "floor") return value.price > equilibriumPrice;
    return value.price < equilibriumPrice;
  }, [value.type, value.price, equilibriumPrice]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Price Control
        </span>
        <div className="flex items-center gap-2">
          {value.enabled && (
            <Badge
              tone={isBinding ? "warning" : "secondary"}
            >
              {isBinding ? "Binding" : "Non-Binding"}
            </Badge>
          )}
          <label className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={value.enabled}
              onChange={(e) => onChange({ ...value, enabled: e.target.checked })}
              disabled={disabled}
              className="rounded border-gray-300"
            />
            <span className="text-xs">Enabled</span>
          </label>
        </div>
      </div>

      {/* Type Toggle */}
      <div className="flex gap-1">
        <button
          onClick={() => onChange({ ...value, type: "floor" })}
          disabled={disabled || !value.enabled}
          className={`
            flex-1 px-3 py-2 text-sm border rounded transition-colors
            ${
              value.type === "floor"
                ? "bg-amber-500 text-white border-amber-500"
                : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
            }
          `}
        >
          Price Floor
        </button>
        <button
          onClick={() => onChange({ ...value, type: "ceiling" })}
          disabled={disabled || !value.enabled}
          className={`
            flex-1 px-3 py-2 text-sm border rounded transition-colors
            ${
              value.type === "ceiling"
                ? "bg-purple-500 text-white border-purple-500"
                : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
            }
          `}
        >
          Price Ceiling
        </button>
      </div>

      {/* Price Input */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">
            {value.type === "floor" ? "Minimum" : "Maximum"} Price
          </label>
          <span className="text-xs font-mono">${value.price.toFixed(2)}</span>
        </div>
        <Slider
          min={0}
          max={200}
          value={value.price}
          onChange={(e) => onChange({ ...value, price: Number(e.target.value) })}
          disabled={disabled || !value.enabled}
        />
        {equilibriumPrice && (
          <div className="text-xs text-gray-400 mt-1">
            Equilibrium: ${equilibriumPrice.toFixed(2)}
          </div>
        )}
      </div>

      {/* Effects Info */}
      {value.enabled && isBinding && (
        <div className="text-xs bg-amber-50 dark:bg-amber-900/20 p-2 rounded">
          {value.type === "floor" ? (
            <span>⚠️ Price floor creates surplus (Qs {'>'} Qd)</span>
          ) : (
            <span>⚠️ Price ceiling creates shortage (Qd {'>'} Qs)</span>
          )}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Time Delay / Market Dynamics Widget
// =============================================================================

export interface TimeDelayWidgetProps {
  value: TimeDelayConfig;
  onChange: (value: TimeDelayConfig) => void;
  disabled?: boolean;
}

export const TimeDelayWidget = ({
  value,
  onChange,
  disabled = false,
}: TimeDelayWidgetProps) => {
  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
        Market Dynamics
      </span>

      {/* Adjustment Speed */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Adjustment Speed</label>
          <span className="text-xs font-mono">{(value.adjustmentSpeed * 100).toFixed(0)}%</span>
        </div>
        <Slider
          min={0}
          max={1}
          value={value.adjustmentSpeed}
          onChange={(e) => onChange({ ...value, adjustmentSpeed: Number(e.target.value) })}
          disabled={disabled}
        />
        <div className="flex justify-between text-xs text-gray-400 mt-1">
          <span>Slow</span>
          <span>Instant</span>
        </div>
      </div>

      {/* Information Lag */}
      <div>
        <div className="flex items-center justify-between mb-1">
          <label className="text-xs text-gray-500">Information Lag</label>
          <span className="text-xs font-mono">{value.informationLag} periods</span>
        </div>
        <Slider
          min={0}
          max={10}
          value={value.informationLag}
          onChange={(e) => onChange({ ...value, informationLag: Number(e.target.value) })}
          disabled={disabled}
        />
      </div>

      {/* Expectations Type */}
      <div>
        <label className="block text-xs text-gray-500 mb-2">Expectations</label>
        <div className="flex gap-1">
          {(["naive", "adaptive", "rational"] as const).map((type) => (
            <Tooltip
              key={type}
              content={
                type === "naive"
                  ? "Expect same as last period"
                  : type === "adaptive"
                  ? "Weighted average of past"
                  : "Use all available info"
              }
            >
              <button
                onClick={() => onChange({ ...value, expectationsType: type })}
                disabled={disabled}
                className={`
                  flex-1 px-2 py-1 text-xs border rounded capitalize transition-colors
                  ${
                    value.expectationsType === type
                      ? "bg-blue-500 text-white border-blue-500"
                      : "bg-white dark:bg-gray-800 border-gray-300 dark:border-gray-600"
                  }
                `}
              >
                {type}
              </button>
            </Tooltip>
          ))}
        </div>
      </div>

      {/* Stability Analysis */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700">
        {value.adjustmentSpeed > 0.8 && value.informationLag > 2 && (
          <span className="text-amber-600">⚠️ May exhibit cobweb oscillations</span>
        )}
        {value.adjustmentSpeed <= 0.5 && value.expectationsType === "rational" && (
          <span className="text-green-600">✓ Stable convergence expected</span>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Macro Parameters Widget
// =============================================================================

export interface MacroParametersWidgetProps {
  value: MacroParameters;
  onChange: (value: MacroParameters) => void;
  disabled?: boolean;
}

export const MacroParametersWidget = ({
  value,
  onChange,
  disabled = false,
}: MacroParametersWidgetProps) => {
  // Calculate misery index
  const miseryIndex = useMemo(() => {
    return value.inflationRate + value.unemploymentRate;
  }, [value.inflationRate, value.unemploymentRate]);

  // Calculate real interest rate (Fisher equation)
  const realInterestRate = useMemo(() => {
    return value.interestRate - value.inflationRate;
  }, [value.interestRate, value.inflationRate]);

  return (
    <div className="space-y-3 p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Macro Parameters
        </span>
        <Badge
          tone={miseryIndex > 10 ? "danger" : miseryIndex > 5 ? "warning" : "success"}
        >
          Misery: {miseryIndex.toFixed(1)}
        </Badge>
      </div>

      {/* GDP */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">GDP ($ billions)</label>
        <input
          type="number"
          value={value.gdp}
          onChange={(e) => onChange({ ...value, gdp: Number(e.target.value) })}
          disabled={disabled}
          min={0}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        />
      </div>

      {/* Key Rates in Grid */}
      <div className="grid grid-cols-2 gap-3">
        {/* Inflation */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Inflation</label>
            <span className="text-xs font-mono">{value.inflationRate.toFixed(1)}%</span>
          </div>
          <Slider
            min={-5}
            max={20}
            value={value.inflationRate}
            onChange={(e) => onChange({ ...value, inflationRate: Number(e.target.value) })}
            disabled={disabled}
          />
        </div>

        {/* Unemployment */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Unemployment</label>
            <span className="text-xs font-mono">{value.unemploymentRate.toFixed(1)}%</span>
          </div>
          <Slider
            min={0}
            max={25}
            value={value.unemploymentRate}
            onChange={(e) => onChange({ ...value, unemploymentRate: Number(e.target.value) })}
            disabled={disabled}
          />
        </div>

        {/* Interest Rate */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Interest Rate</label>
            <span className="text-xs font-mono">{value.interestRate.toFixed(2)}%</span>
          </div>
          <Slider
            min={0}
            max={15}
            value={value.interestRate}
            onChange={(e) => onChange({ ...value, interestRate: Number(e.target.value) })}
            disabled={disabled}
          />
        </div>

        {/* Exchange Rate */}
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-xs text-gray-500">Exchange Rate</label>
            <span className="text-xs font-mono">${value.exchangeRate.toFixed(2)}</span>
          </div>
          <Slider
            min={0.5}
            max={2}
            value={value.exchangeRate}
            onChange={(e) => onChange({ ...value, exchangeRate: Number(e.target.value) })}
            disabled={disabled}
          />
        </div>
      </div>

      {/* Money Supply */}
      <div>
        <label className="block text-xs text-gray-500 mb-1">Money Supply (M2, $ billions)</label>
        <input
          type="number"
          value={value.moneySupply}
          onChange={(e) => onChange({ ...value, moneySupply: Number(e.target.value) })}
          disabled={disabled}
          min={0}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded"
        />
      </div>

      {/* Derived Metrics */}
      <div className="text-xs text-gray-500 pt-2 border-t border-gray-200 dark:border-gray-700 grid grid-cols-2 gap-2">
        <div>Real Interest Rate: {realInterestRate.toFixed(2)}%</div>
        <div>Velocity (V = GDP/M): {(value.gdp / value.moneySupply).toFixed(2)}</div>
      </div>
    </div>
  );
};

// =============================================================================
// Exports
// =============================================================================

export const EconParameterWidgets = {
  SupplyDemandWidget,
  TaxSubsidyWidget,
  PriceControlWidget,
  TimeDelayWidget,
  MacroParametersWidget,
};

export default EconParameterWidgets;
