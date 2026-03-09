/**
 * Step Palette Component
 * 
 * Domain-aware palette for dragging actions onto the timeline.
 * Provides categorized action blocks that can be added to steps.
 * 
 * @doc.type component
 * @doc.purpose Domain-specific action palette for simulation authoring
 * @doc.layer product
 * @doc.pattern Palette
 */

import { useState, useCallback } from "react";
import { Button, Badge, Tooltip } from "@ghatana/ui";

// =============================================================================
// Local Types (avoiding external contract dependencies)
// =============================================================================

export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "ECONOMICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ENGINEERING"
  | "MATHEMATICS";

export type SimEntityId = string;

export interface SimAction {
  action: string;
  target?: SimEntityId;
  params?: Record<string, unknown>;
  duration?: number;
  easing?: string;
}

// =============================================================================
// Types
// =============================================================================

export interface ActionDefinition {
  action: string;
  label: string;
  icon: string;
  description: string;
  defaultDuration: number;
  requiredParams: string[];
  optionalParams: string[];
  category: string;
}

export interface StepPaletteProps {
  domain: SimulationDomain;
  onActionAdd: (action: Partial<SimAction>) => void;
  onDragStart?: (action: ActionDefinition, e: React.DragEvent) => void;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

// =============================================================================
// Action Definitions by Domain
// =============================================================================

const COMMON_ACTIONS: ActionDefinition[] = [
  {
    action: "ANNOTATE",
    label: "Annotate",
    icon: "💬",
    description: "Add explanatory text or callout",
    defaultDuration: 1000,
    requiredParams: ["text"],
    optionalParams: ["targetId", "position"],
    category: "Visual",
  },
  {
    action: "HIGHLIGHT",
    label: "Highlight",
    icon: "✨",
    description: "Highlight one or more entities",
    defaultDuration: 500,
    requiredParams: ["targetIds"],
    optionalParams: ["style"],
    category: "Visual",
  },
  {
    action: "CREATE_ENTITY",
    label: "Create Entity",
    icon: "➕",
    description: "Add a new entity to the simulation",
    defaultDuration: 300,
    requiredParams: ["entity"],
    optionalParams: [],
    category: "Basic",
  },
  {
    action: "REMOVE_ENTITY",
    label: "Remove Entity",
    icon: "🗑️",
    description: "Remove an entity from the simulation",
    defaultDuration: 300,
    requiredParams: ["targetId"],
    optionalParams: [],
    category: "Basic",
  },
  {
    action: "MOVE",
    label: "Move",
    icon: "↔️",
    description: "Move an entity to a new position",
    defaultDuration: 500,
    requiredParams: ["targetId", "toX", "toY"],
    optionalParams: ["toZ", "easing"],
    category: "Basic",
  },
  {
    action: "SET_VALUE",
    label: "Set Value",
    icon: "✏️",
    description: "Change a property of an entity",
    defaultDuration: 300,
    requiredParams: ["targetId", "value"],
    optionalParams: ["property"],
    category: "Basic",
  },
];

const CS_DISCRETE_ACTIONS: ActionDefinition[] = [
  {
    action: "COMPARE",
    label: "Compare",
    icon: "⚖️",
    description: "Compare two elements",
    defaultDuration: 600,
    requiredParams: ["leftId", "rightId"],
    optionalParams: ["result"],
    category: "Algorithm",
  },
  {
    action: "SWAP",
    label: "Swap",
    icon: "🔄",
    description: "Swap two elements",
    defaultDuration: 500,
    requiredParams: ["id1", "id2"],
    optionalParams: ["easing"],
    category: "Algorithm",
  },
];

const PHYSICS_ACTIONS: ActionDefinition[] = [
  {
    action: "SET_INITIAL_VELOCITY",
    label: "Set Velocity",
    icon: "🚀",
    description: "Set initial velocity of a body",
    defaultDuration: 100,
    requiredParams: ["targetId", "vx", "vy"],
    optionalParams: [],
    category: "Motion",
  },
  {
    action: "APPLY_FORCE",
    label: "Apply Force",
    icon: "💨",
    description: "Apply a force to a body",
    defaultDuration: 100,
    requiredParams: ["targetId", "fx", "fy"],
    optionalParams: ["duration"],
    category: "Forces",
  },
  {
    action: "SET_GRAVITY",
    label: "Set Gravity",
    icon: "⬇️",
    description: "Set the global gravity vector",
    defaultDuration: 100,
    requiredParams: ["x", "y"],
    optionalParams: [],
    category: "Forces",
  },
  {
    action: "CONNECT_SPRING",
    label: "Connect Spring",
    icon: "🔗",
    description: "Connect two bodies with a spring",
    defaultDuration: 300,
    requiredParams: ["anchorId", "attachId", "stiffness", "damping"],
    optionalParams: ["restLength"],
    category: "Connections",
  },
  {
    action: "RELEASE",
    label: "Release",
    icon: "🎈",
    description: "Release a fixed body",
    defaultDuration: 100,
    requiredParams: ["targetId"],
    optionalParams: [],
    category: "Motion",
  },
];

const CHEMISTRY_ACTIONS: ActionDefinition[] = [
  {
    action: "CREATE_BOND",
    label: "Create Bond",
    icon: "🔗",
    description: "Create a chemical bond between atoms",
    defaultDuration: 500,
    requiredParams: ["atom1Id", "atom2Id", "bondOrder"],
    optionalParams: ["bondType"],
    category: "Bonds",
  },
  {
    action: "BREAK_BOND",
    label: "Break Bond",
    icon: "✂️",
    description: "Break a chemical bond",
    defaultDuration: 500,
    requiredParams: ["bondId"],
    optionalParams: ["homolytic"],
    category: "Bonds",
  },
  {
    action: "REARRANGE",
    label: "Rearrange",
    icon: "🔀",
    description: "Rearrange molecular structure",
    defaultDuration: 800,
    requiredParams: ["moleculeId"],
    optionalParams: ["type"],
    category: "Reactions",
  },
  {
    action: "SET_REACTION_CONDITIONS",
    label: "Set Conditions",
    icon: "🌡️",
    description: "Set reaction conditions (temp, pressure, etc.)",
    defaultDuration: 300,
    requiredParams: [],
    optionalParams: ["temperature", "pressure", "solvent", "catalyst", "ph"],
    category: "Reactions",
  },
  {
    action: "HIGHLIGHT_ATOMS",
    label: "Highlight Atoms",
    icon: "⚛️",
    description: "Highlight specific atoms in a molecule",
    defaultDuration: 400,
    requiredParams: ["atomIds"],
    optionalParams: ["style"],
    category: "Visual",
  },
  {
    action: "DISPLAY_FORMULA",
    label: "Show Formula",
    icon: "🧪",
    description: "Display a chemical formula",
    defaultDuration: 300,
    requiredParams: ["formula", "position"],
    optionalParams: ["style"],
    category: "Visual",
  },
  {
    action: "SHOW_ENERGY_PROFILE",
    label: "Energy Profile",
    icon: "📈",
    description: "Show reaction energy profile",
    defaultDuration: 500,
    requiredParams: ["profileId"],
    optionalParams: ["animate"],
    category: "Reactions",
  },
];

const BIOLOGY_ACTIONS: ActionDefinition[] = [
  {
    action: "DIFFUSE",
    label: "Diffuse",
    icon: "〰️",
    description: "Passive diffusion of molecule",
    defaultDuration: 1000,
    requiredParams: ["molecule", "fromId", "toId", "rate"],
    optionalParams: [],
    category: "Transport",
  },
  {
    action: "TRANSPORT",
    label: "Transport",
    icon: "🚚",
    description: "Active/facilitated transport",
    defaultDuration: 800,
    requiredParams: ["molecule", "fromId", "toId", "transporterType"],
    optionalParams: ["atpCost"],
    category: "Transport",
  },
  {
    action: "TRANSCRIBE",
    label: "Transcribe",
    icon: "📝",
    description: "Transcribe gene to mRNA",
    defaultDuration: 1500,
    requiredParams: ["geneId", "mRnaId"],
    optionalParams: [],
    category: "Gene Expression",
  },
  {
    action: "TRANSLATE",
    label: "Translate",
    icon: "🔤",
    description: "Translate mRNA to protein",
    defaultDuration: 2000,
    requiredParams: ["mRnaId", "proteinId"],
    optionalParams: [],
    category: "Gene Expression",
  },
  {
    action: "METABOLISE",
    label: "Metabolise",
    icon: "⚗️",
    description: "Enzymatic metabolic reaction",
    defaultDuration: 1000,
    requiredParams: ["enzymeId", "substrateId", "productId"],
    optionalParams: ["rate"],
    category: "Metabolism",
  },
  {
    action: "GROW_DIVIDE",
    label: "Cell Division",
    icon: "🧫",
    description: "Cell growth and division",
    defaultDuration: 2000,
    requiredParams: ["cellId", "phase"],
    optionalParams: [],
    category: "Cell Cycle",
  },
];

const MEDICINE_ACTIONS: ActionDefinition[] = [
  {
    action: "ABSORB",
    label: "Absorb",
    icon: "💊",
    description: "Drug absorption into compartment",
    defaultDuration: 500,
    requiredParams: ["doseId", "compartmentId", "rate"],
    optionalParams: [],
    category: "Pharmacokinetics",
  },
  {
    action: "ELIMINATE",
    label: "Eliminate",
    icon: "🚿",
    description: "Drug elimination from body",
    defaultDuration: 500,
    requiredParams: ["compartmentId", "rate", "route"],
    optionalParams: [],
    category: "Pharmacokinetics",
  },
  {
    action: "SPREAD_DISEASE",
    label: "Disease Spread",
    icon: "🦠",
    description: "Epidemiological disease spread",
    defaultDuration: 1000,
    requiredParams: ["agentId", "beta", "gamma"],
    optionalParams: [],
    category: "Epidemiology",
  },
  {
    action: "SIGNAL",
    label: "Signal",
    icon: "📡",
    description: "Cell signaling event",
    defaultDuration: 400,
    requiredParams: ["signalId", "targetId", "response"],
    optionalParams: [],
    category: "Signaling",
  },
];

const ECONOMICS_ACTIONS: ActionDefinition[] = [
  {
    action: "SET_STOCK_VALUE",
    label: "Set Stock",
    icon: "📦",
    description: "Set value of a stock variable",
    defaultDuration: 200,
    requiredParams: ["stockId", "value"],
    optionalParams: [],
    category: "Stocks & Flows",
  },
  {
    action: "UPDATE_FLOW_RATE",
    label: "Update Flow",
    icon: "🌊",
    description: "Update flow rate between stocks",
    defaultDuration: 200,
    requiredParams: ["flowId", "rate"],
    optionalParams: ["equation"],
    category: "Stocks & Flows",
  },
  {
    action: "SPAWN_AGENT",
    label: "Spawn Agent",
    icon: "👤",
    description: "Create a new agent in ABM",
    defaultDuration: 300,
    requiredParams: ["agentType", "position", "state"],
    optionalParams: ["behavior"],
    category: "Agents",
  },
  {
    action: "DISPLAY_CHART",
    label: "Show Chart",
    icon: "📊",
    description: "Display a data visualization",
    defaultDuration: 500,
    requiredParams: ["chartType", "data", "position"],
    optionalParams: ["title", "axes"],
    category: "Analytics",
  },
];

// Domain to actions map
const DOMAIN_ACTIONS: Record<SimulationDomain, ActionDefinition[]> = {
  CS_DISCRETE: [...COMMON_ACTIONS, ...CS_DISCRETE_ACTIONS],
  PHYSICS: [...COMMON_ACTIONS, ...PHYSICS_ACTIONS],
  CHEMISTRY: [...COMMON_ACTIONS, ...CHEMISTRY_ACTIONS],
  BIOLOGY: [...COMMON_ACTIONS, ...BIOLOGY_ACTIONS],
  MEDICINE: [...COMMON_ACTIONS, ...MEDICINE_ACTIONS],
  ECONOMICS: [...COMMON_ACTIONS, ...ECONOMICS_ACTIONS],
  ENGINEERING: COMMON_ACTIONS,
  MATHEMATICS: COMMON_ACTIONS,
};

// =============================================================================
// Helper Functions
// =============================================================================

function groupByCategory(actions: ActionDefinition[]): Record<string, ActionDefinition[]> {
  return actions.reduce((groups, action) => {
    const category = action.category;
    if (!groups[category]) {
      groups[category] = [];
    }
    groups[category].push(action);
    return groups;
  }, {} as Record<string, ActionDefinition[]>);
}

// Category order for consistent display
const CATEGORY_ORDER = [
  "Basic",
  "Algorithm",
  "Forces",
  "Motion",
  "Connections",
  "Bonds",
  "Reactions",
  "Transport",
  "Gene Expression",
  "Metabolism",
  "Cell Cycle",
  "Pharmacokinetics",
  "Epidemiology",
  "Signaling",
  "Stocks & Flows",
  "Agents",
  "Analytics",
  "Visual",
];

function sortCategories(categories: string[]): string[] {
  return categories.sort((a, b) => {
    const indexA = CATEGORY_ORDER.indexOf(a);
    const indexB = CATEGORY_ORDER.indexOf(b);
    if (indexA === -1 && indexB === -1) return a.localeCompare(b);
    if (indexA === -1) return 1;
    if (indexB === -1) return -1;
    return indexA - indexB;
  });
}

// =============================================================================
// Component
// =============================================================================

export const StepPalette = ({
  domain,
  onActionAdd,
  onDragStart,
  collapsed = false,
  onToggleCollapse,
}: StepPaletteProps) => {
  const [searchQuery, setSearchQuery] = useState("");
  const [expandedCategories, setExpandedCategories] = useState<Set<string>>(
    new Set(["Basic", "Algorithm"])
  );
  
  const actions = DOMAIN_ACTIONS[domain] || COMMON_ACTIONS;
  const groupedActions = groupByCategory(actions);
  const categories = sortCategories(Object.keys(groupedActions));
  
  // Filter actions by search
  const filteredGroups = searchQuery
    ? Object.entries(groupedActions).reduce((acc, [cat, acts]) => {
        const filtered = acts.filter(
          (a) =>
            a.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
            a.action.toLowerCase().includes(searchQuery.toLowerCase()) ||
            a.description.toLowerCase().includes(searchQuery.toLowerCase())
        );
        if (filtered.length > 0) acc[cat] = filtered;
        return acc;
      }, {} as Record<string, ActionDefinition[]>)
    : groupedActions;
  
  const toggleCategory = useCallback((category: string) => {
    setExpandedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(category)) {
        next.delete(category);
      } else {
        next.add(category);
      }
      return next;
    });
  }, []);
  
  const handleActionClick = useCallback(
    (actionDef: ActionDefinition) => {
      onActionAdd({
        action: actionDef.action,
        duration: actionDef.defaultDuration,
        easing: "easeInOut",
      } as Partial<SimAction>);
    },
    [onActionAdd]
  );
  
  const handleDragStart = useCallback(
    (actionDef: ActionDefinition, e: React.DragEvent) => {
      e.dataTransfer.setData("application/json", JSON.stringify(actionDef));
      e.dataTransfer.effectAllowed = "copy";
      onDragStart?.(actionDef, e);
    },
    [onDragStart]
  );
  
  if (collapsed) {
    return (
      <div className="w-12 bg-gray-100 dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col items-center py-2">
        <Tooltip content="Expand Palette" placement="right">
          <Button
            size="sm"
            variant="ghost"
            onClick={onToggleCollapse}
            aria-label="Expand palette"
          >
            ➡️
          </Button>
        </Tooltip>
        
        <div className="mt-4 space-y-2">
          {categories.slice(0, 5).map((cat) => (
            <Tooltip key={cat} content={cat} placement="right">
              <div className="w-8 h-8 flex items-center justify-center text-lg bg-white dark:bg-gray-700 rounded cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-600">
                {groupedActions[cat]?.[0]?.icon || "📦"}
              </div>
            </Tooltip>
          ))}
        </div>
      </div>
    );
  }
  
  return (
    <div className="w-64 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col h-full">
      {/* Header */}
      <div className="px-3 py-2 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm text-gray-900 dark:text-white">Actions</span>
          <Badge variant="soft">{actions.length}</Badge>
        </div>
        
        {onToggleCollapse && (
          <Button size="sm" variant="ghost" onClick={onToggleCollapse}>
            ⬅️
          </Button>
        )}
      </div>
      
      {/* Search */}
      <div className="px-3 py-2 border-b border-gray-200 dark:border-gray-700">
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search actions..."
          className="w-full px-2 py-1 text-sm border border-gray-300 dark:border-gray-600 rounded focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:text-white"
        />
      </div>
      
      {/* Categories */}
      <div className="flex-1 overflow-y-auto">
        {sortCategories(Object.keys(filteredGroups)).map((category) => {
          const categoryActions = filteredGroups[category];
          const isExpanded = expandedCategories.has(category) || !!searchQuery;
          
          return (
            <div key={category} className="border-b border-gray-100 dark:border-gray-700">
              <button
                onClick={() => toggleCategory(category)}
                className="w-full px-3 py-2 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <span className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
                  {category}
                </span>
                <span className="text-xs text-gray-400">
                  {isExpanded ? "▼" : "▶"} {categoryActions.length}
                </span>
              </button>
              
              {isExpanded && (
                <div className="px-2 pb-2 space-y-1">
                  {categoryActions.map((actionDef) => (
                    <div
                      key={actionDef.action}
                      draggable
                      onDragStart={(e) => handleDragStart(actionDef, e)}
                      onClick={() => handleActionClick(actionDef)}
                      className="flex items-center gap-2 px-2 py-1.5 rounded cursor-pointer bg-gray-50 dark:bg-gray-900 hover:bg-blue-50 dark:hover:bg-blue-900/20 hover:border-blue-200 border border-transparent transition-colors"
                    >
                      <span className="text-base">{actionDef.icon}</span>
                      <div className="flex-1 min-w-0">
                        <div className="text-sm font-medium text-gray-900 dark:text-white truncate">
                          {actionDef.label}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
                          {actionDef.description}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
        
        {Object.keys(filteredGroups).length === 0 && (
          <div className="px-4 py-8 text-center text-gray-500">
            <p className="text-sm">No actions match your search.</p>
          </div>
        )}
      </div>
      
      {/* Footer hint */}
      <div className="px-3 py-2 text-xs text-gray-400 border-t border-gray-200 dark:border-gray-700">
        💡 Drag actions to timeline or click to add
      </div>
    </div>
  );
};

export default StepPalette;
