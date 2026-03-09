/**
 * Domain Parameter Widgets Index
 * 
 * Barrel export for all domain-specific parameter widgets.
 * Provides factory function to get widgets based on simulation domain.
 * 
 * @doc.type module
 * @doc.purpose Domain widget exports and factory
 * @doc.layer product
 * @doc.pattern Factory
 */

// =============================================================================
// Re-exports
// =============================================================================

export * from "./PhysicsParameterWidgets";
export * from "./ChemistryParameterWidgets";
export * from "./BioMedParameterWidgets";
export * from "./EconParameterWidgets";
export * from "./MathEngineeringWidgets";
export * from "./CSDiscreteWidgets";

// =============================================================================
// Widget Collections
// =============================================================================

import { PhysicsParameterWidgets } from "./PhysicsParameterWidgets";
import { ChemistryParameterWidgets } from "./ChemistryParameterWidgets";
import { BioMedParameterWidgets } from "./BioMedParameterWidgets";
import { EconParameterWidgets } from "./EconParameterWidgets";
import { MathEngineeringWidgets } from "./MathEngineeringWidgets";
import { CSDiscreteWidgets } from "./CSDiscreteWidgets";

// =============================================================================
// Domain Types (matching contracts/v1/simulation/types.ts)
// =============================================================================

export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ECONOMICS"
  | "ENGINEERING"
  | "MATHEMATICS";

// =============================================================================
// Widget Registry
// =============================================================================

/**
 * Registry of available widgets by domain.
 * Maps each domain to its available widget components.
 */
export const DomainWidgetRegistry = {
  CS_DISCRETE: {
    ArrayVisualization: CSDiscreteWidgets.ArrayVisualization,
    GraphEditor: CSDiscreteWidgets.GraphEditor,
    AlgorithmConfig: CSDiscreteWidgets.AlgorithmConfig,
    CodeSnippetWidget: CSDiscreteWidgets.CodeSnippetWidget,
    StackQueueVisualization: CSDiscreteWidgets.StackQueueVisualization,
  },
  PHYSICS: {
    VectorEditor: PhysicsParameterWidgets.VectorInput,
    ForceArrowWidget: PhysicsParameterWidgets.VectorInput,
    MassSlider: PhysicsParameterWidgets.MassInput,
    GravityControl: PhysicsParameterWidgets.GravityWidget,
    SpringEditor: PhysicsParameterWidgets.SpringConstants,
  },
  CHEMISTRY: {
    MoleculeSelector: ChemistryParameterWidgets.ElementPicker,
    BondEditor: ChemistryParameterWidgets.BondEditor,
    ReactionConditionsPanel: ChemistryParameterWidgets.ReactionConditionsWidget,
    AtomPropertyEditor: ChemistryParameterWidgets.ElementPicker,
  },
  BIOLOGY: {
    CompartmentModelEditor: BioMedParameterWidgets.CompartmentVolumeWidget,
    GeneExpressionWidget: BioMedParameterWidgets.GeneExpressionWidget,
    EnzymeKineticsWidget: BioMedParameterWidgets.GeneExpressionWidget,
  },
  MEDICINE: {
    CompartmentModelEditor: BioMedParameterWidgets.CompartmentVolumeWidget,
    PKPDModelWidget: BioMedParameterWidgets.PKCompartmentWidget,
    EpidemiologyWidget: BioMedParameterWidgets.SIRModelWidget,
  },
  ECONOMICS: {
    SupplyDemandWidget: EconParameterWidgets.SupplyDemandWidget,
    TaxSubsidyWidget: EconParameterWidgets.TaxSubsidyWidget,
    PriceControlWidget: EconParameterWidgets.PriceControlWidget,
    TimeDelayWidget: EconParameterWidgets.TimeDelayWidget,
    MacroParametersWidget: EconParameterWidgets.MacroParametersWidget,
  },
  ENGINEERING: {
    CircuitEditor: MathEngineeringWidgets.CircuitEditor,
    GeometryShapeEditor: MathEngineeringWidgets.GeometryShapeEditor,
    FunctionPlotter: MathEngineeringWidgets.FunctionPlotter,
  },
  MATHEMATICS: {
    MatrixEditor: MathEngineeringWidgets.MatrixEditor,
    ComplexNumberWidget: MathEngineeringWidgets.ComplexNumberWidget,
    FunctionPlotter: MathEngineeringWidgets.FunctionPlotter,
    GeometryShapeEditor: MathEngineeringWidgets.GeometryShapeEditor,
  },
} as const;

// =============================================================================
// Type Exports
// =============================================================================

export type DomainWidgets<D extends SimulationDomain> = typeof DomainWidgetRegistry[D];

export type WidgetName<D extends SimulationDomain> = keyof DomainWidgets<D>;

// =============================================================================
// Factory Function
// =============================================================================

/**
 * Get all available widgets for a specific domain.
 * 
 * @param domain - The simulation domain
 * @returns Object containing all widgets for that domain
 * 
 * @example
 * ```tsx
 * const widgets = getDomainWidgets("PHYSICS");
 * // widgets = { VectorEditor, ForceArrowWidget, MassSlider, ... }
 * 
 * <widgets.VectorEditor value={vector} onChange={setVector} />
 * ```
 */
export function getDomainWidgets<D extends SimulationDomain>(
  domain: D
): DomainWidgets<D> {
  return DomainWidgetRegistry[domain] as DomainWidgets<D>;
}

/**
 * Get a specific widget for a domain.
 * 
 * @param domain - The simulation domain
 * @param widgetName - The name of the widget
 * @returns The widget component
 * 
 * @example
 * ```tsx
 * const VectorEditor = getWidget("PHYSICS", "VectorEditor");
 * <VectorEditor value={vector} onChange={setVector} />
 * ```
 */
export function getWidget<D extends SimulationDomain, W extends WidgetName<D>>(
  domain: D,
  widgetName: W
): DomainWidgets<D>[W] {
  const domainRegistry = DomainWidgetRegistry[domain];
  return domainRegistry[widgetName as keyof typeof domainRegistry] as DomainWidgets<D>[W];
}

/**
 * Get widget names available for a domain.
 * Useful for dynamic widget rendering.
 * 
 * @param domain - The simulation domain
 * @returns Array of widget names
 */
export function getWidgetNames<D extends SimulationDomain>(
  domain: D
): Array<WidgetName<D>> {
  return Object.keys(DomainWidgetRegistry[domain]) as Array<WidgetName<D>>;
}

/**
 * Check if a domain has a specific widget.
 * 
 * @param domain - The simulation domain
 * @param widgetName - The widget name to check
 * @returns true if the widget exists for the domain
 */
export function hasWidget(domain: SimulationDomain, widgetName: string): boolean {
  return widgetName in DomainWidgetRegistry[domain];
}

// =============================================================================
// Widget Metadata
// =============================================================================

export interface WidgetMetadata {
  name: string;
  displayName: string;
  description: string;
  category: "primary" | "secondary" | "advanced";
  domains: SimulationDomain[];
}

/**
 * Metadata for all available widgets.
 * Useful for building dynamic UIs and documentation.
 */
export const WidgetMetadata: WidgetMetadata[] = [
  // CS Discrete
  {
    name: "ArrayVisualization",
    displayName: "Array Visualization",
    description: "Interactive array with indices, pointers, and highlighting",
    category: "primary",
    domains: ["CS_DISCRETE"],
  },
  {
    name: "GraphEditor",
    displayName: "Graph Editor",
    description: "Interactive node-edge graph editor with weights",
    category: "primary",
    domains: ["CS_DISCRETE"],
  },
  {
    name: "AlgorithmConfig",
    displayName: "Algorithm Configuration",
    description: "Configure sorting/searching algorithm parameters",
    category: "secondary",
    domains: ["CS_DISCRETE"],
  },
  {
    name: "CodeSnippetWidget",
    displayName: "Code Snippet",
    description: "Display code with line highlighting",
    category: "secondary",
    domains: ["CS_DISCRETE"],
  },
  {
    name: "StackQueueVisualization",
    displayName: "Stack/Queue",
    description: "Visualize stack (LIFO) or queue (FIFO) operations",
    category: "primary",
    domains: ["CS_DISCRETE"],
  },

  // Physics
  {
    name: "VectorEditor",
    displayName: "Vector Editor",
    description: "Edit 2D/3D vectors with magnitude and direction",
    category: "primary",
    domains: ["PHYSICS", "ENGINEERING"],
  },
  {
    name: "ForceArrowWidget",
    displayName: "Force Arrow",
    description: "Visual force vector with direction and magnitude",
    category: "primary",
    domains: ["PHYSICS"],
  },
  {
    name: "MassSlider",
    displayName: "Mass Control",
    description: "Adjust object mass with preset common values",
    category: "secondary",
    domains: ["PHYSICS"],
  },
  {
    name: "GravityControl",
    displayName: "Gravity Control",
    description: "Set gravitational acceleration with presets",
    category: "secondary",
    domains: ["PHYSICS"],
  },
  {
    name: "SpringEditor",
    displayName: "Spring Properties",
    description: "Configure spring constant and damping",
    category: "advanced",
    domains: ["PHYSICS"],
  },

  // Chemistry
  {
    name: "MoleculeSelector",
    displayName: "Molecule Selector",
    description: "Select molecules by formula or SMILES notation",
    category: "primary",
    domains: ["CHEMISTRY"],
  },
  {
    name: "BondEditor",
    displayName: "Bond Editor",
    description: "Configure chemical bond properties",
    category: "primary",
    domains: ["CHEMISTRY"],
  },
  {
    name: "ReactionConditionsPanel",
    displayName: "Reaction Conditions",
    description: "Set temperature, pressure, catalyst, solvent",
    category: "secondary",
    domains: ["CHEMISTRY"],
  },
  {
    name: "AtomPropertyEditor",
    displayName: "Atom Properties",
    description: "Edit atom element and charge",
    category: "secondary",
    domains: ["CHEMISTRY"],
  },

  // Biology/Medicine
  {
    name: "CompartmentModelEditor",
    displayName: "Compartment Model",
    description: "Multi-compartment pharmacokinetic model",
    category: "primary",
    domains: ["BIOLOGY", "MEDICINE"],
  },
  {
    name: "GeneExpressionWidget",
    displayName: "Gene Expression",
    description: "Configure gene promoter and expression levels",
    category: "primary",
    domains: ["BIOLOGY"],
  },
  {
    name: "PKPDModelWidget",
    displayName: "PK/PD Model",
    description: "Pharmacokinetic/pharmacodynamic parameters",
    category: "primary",
    domains: ["MEDICINE"],
  },
  {
    name: "EpidemiologyWidget",
    displayName: "Epidemiology Model",
    description: "SIR/SEIR disease spread parameters",
    category: "primary",
    domains: ["MEDICINE"],
  },
  {
    name: "EnzymeKineticsWidget",
    displayName: "Enzyme Kinetics",
    description: "Michaelis-Menten enzyme parameters",
    category: "advanced",
    domains: ["BIOLOGY"],
  },

  // Economics
  {
    name: "SupplyDemandWidget",
    displayName: "Supply & Demand",
    description: "Interactive supply/demand curves with equilibrium",
    category: "primary",
    domains: ["ECONOMICS"],
  },
  {
    name: "TaxSubsidyWidget",
    displayName: "Tax/Subsidy",
    description: "Configure tax or subsidy intervention",
    category: "secondary",
    domains: ["ECONOMICS"],
  },
  {
    name: "PriceControlWidget",
    displayName: "Price Control",
    description: "Set price floor or ceiling",
    category: "secondary",
    domains: ["ECONOMICS"],
  },
  {
    name: "TimeDelayWidget",
    displayName: "Market Dynamics",
    description: "Configure market adjustment and expectations",
    category: "advanced",
    domains: ["ECONOMICS"],
  },
  {
    name: "MacroParametersWidget",
    displayName: "Macro Parameters",
    description: "GDP, inflation, unemployment, interest rates",
    category: "primary",
    domains: ["ECONOMICS"],
  },

  // Math/Engineering
  {
    name: "GeometryShapeEditor",
    displayName: "Geometry Editor",
    description: "Draw and edit geometric shapes",
    category: "primary",
    domains: ["MATHEMATICS", "ENGINEERING"],
  },
  {
    name: "FunctionPlotter",
    displayName: "Function Plotter",
    description: "Plot mathematical functions",
    category: "primary",
    domains: ["MATHEMATICS", "ENGINEERING"],
  },
  {
    name: "MatrixEditor",
    displayName: "Matrix Editor",
    description: "Edit matrices with determinant calculation",
    category: "primary",
    domains: ["MATHEMATICS"],
  },
  {
    name: "ComplexNumberWidget",
    displayName: "Complex Number",
    description: "Edit complex numbers with polar form",
    category: "secondary",
    domains: ["MATHEMATICS"],
  },
  {
    name: "CircuitEditor",
    displayName: "Circuit Editor",
    description: "Build electrical circuit diagrams",
    category: "primary",
    domains: ["ENGINEERING"],
  },
];

/**
 * Get widget metadata for a specific domain.
 * 
 * @param domain - The simulation domain
 * @returns Array of widget metadata for that domain
 */
export function getWidgetMetadata(domain: SimulationDomain): WidgetMetadata[] {
  return WidgetMetadata.filter((w) => w.domains.includes(domain));
}

/**
 * Get widget metadata by category for a domain.
 * 
 * @param domain - The simulation domain
 * @param category - The widget category
 * @returns Array of widget metadata matching domain and category
 */
export function getWidgetsByCategory(
  domain: SimulationDomain,
  category: WidgetMetadata["category"]
): WidgetMetadata[] {
  return WidgetMetadata.filter(
    (w) => w.domains.includes(domain) && w.category === category
  );
}
