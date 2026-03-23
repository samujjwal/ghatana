/**
 * Chrome UI Components and State for Ghatana Canvas
 *
 * Comprehensive Chrome UI system providing the outer shell of the canvas application.
 * Based on YAPPC Canvas UX Design Specification v1.0
 *
 * @doc.type component
 * @doc.purpose Canvas chrome UI system
 * @doc.layer presentation
 * @doc.pattern CALM (Composable, Accessible, Layered, Minimal)
 */

import React, { ReactNode } from "react";
import { atom, useAtom, useAtomValue } from "jotai";
import { RoleSwitcher } from "./components/RoleSwitcher";
import { getCanvasConfig, hasCanvasConfig } from "./core/canvas-config";

// ============================================================================
// TYPES & CONSTANTS
// ============================================================================

export type SemanticLayer =
  | "architecture" // 0.1x - 0.5x  (System design, high-level flows)
  | "design" // 0.5x - 1.0x  (Component design, wireframes)
  | "component" // 1.0x - 2.0x  (Detailed components, interactions)
  | "implementation" // 2.0x - 5.0x  (Code, logic, data structures)
  | "detail"; // 5.0x+        (Line-by-line code, debugging)

export type LeftPanelType =
  | "outline"
  | "layers"
  | "palette"
  | "tasks"
  | "minimap"
  | null;

export type Action = {
  id: string;
  label: string;
  icon: string;
  shortcut?: string;
  category: "layer" | "phase" | "role" | "universal" | "selection";
  handler: () => void | Promise<void>;
};

export interface PersonaConfig {
  role: string;
  displayName: string;
  icon: string;
  color: string;
  quickActions: Action[];
}

export interface PhaseColors {
  primary: string;
  background: string;
  text: string;
}

export const Z_INDEX = {
  BACKGROUND: 0,
  GRID: 1,
  FRAMES: 10,
  EDGES: 15,
  ELEMENTS: 20,
  ANNOTATIONS: 30,
  SELECTION: 40,
  PORTALS: 50,
  CONTEXT_BAR: 100,
  LEFT_RAIL: 200,
  LEFT_PANE: 210,
  INSPECTOR: 220,
  MINIMAP: 230,
  ZOOM_HUD: 240,
  COMMAND_PALETTE: 1000,
  MODAL: 1100,
  TOAST: 1200,
  TOOLTIP: 1300,
} as const;

// ============================================================================
// STATE ATOMS
// ============================================================================

export const chromeCalmModeAtom = atom<boolean>(false);
export const chromeLeftRailVisibleAtom = atom<boolean>(true);
export const chromeLeftPanelAtom = atom<LeftPanelType>(null);
export const chromeInspectorVisibleAtom = atom<boolean>(false);
export const chromeMinimapVisibleAtom = atom<boolean>(false);
export const chromeCurrentPhaseAtom = atom<string>("");
export const chromeZoomLevelAtom = atom<number>(1.0);
export const chromeActiveLayersAtom = atom<number>(3);
export const chromeCollaboratorsAtom = atom<
  Array<{ id: string; name: string; color: string }>
>([{ id: "1", name: "You", color: "#1976d2" }]);
export const chromeSemanticLayerAtom = atom<SemanticLayer>("architecture");
export const chromeActiveRolesAtom = atom<string[]>([]);
export const chromeAvailableActionsAtom = atom<Action[]>([]);

// ============================================================================
// TOP BAR COMPONENT
// ============================================================================

interface TopBarProps {
  projectName?: string;
  onProjectChange?: () => void;
  onSearch?: () => void;
  onShare?: () => void;
  onSettings?: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  projectName = "Alpha",
  onProjectChange,
  onSearch,
  onShare,
  onSettings,
}) => {
  const phase = useAtomValue(chromeCurrentPhaseAtom);
  const collaborators = useAtomValue(chromeCollaboratorsAtom);
  const phaseColor = hasCanvasConfig()
    ? (getCanvasConfig().phases[phase]?.color ?? { primary: "#1976d2", background: "#e3f2fd", text: "#0d47a1" })
    : { primary: "#1976d2", background: "#e3f2fd", text: "#0d47a1" };

  return (
    <div
      className="canvas-top-bar"
      style={{
        height: "56px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "0 16px",
        borderBottom: "1px solid #e0e0e0",
        backgroundColor: "#ffffff",
        zIndex: Z_INDEX.LEFT_RAIL,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <button
          onClick={onProjectChange}
          style={{
            padding: "8px 12px",
            border: "1px solid #e0e0e0",
            borderRadius: "6px",
            background: "white",
            cursor: "pointer",
            fontSize: "14px",
            fontWeight: 500,
          }}
          aria-label="Select project"
        >
          Project: {projectName} ▼
        </button>
        <div
          style={{
            padding: "6px 12px",
            borderRadius: "6px",
            backgroundColor: phaseColor.background,
            color: phaseColor.text,
            fontSize: "13px",
            fontWeight: 600,
          }}
          aria-label={`Current phase: ${phase}`}
        >
          Phase: {phase}
        </div>
        <RoleSwitcher />
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
          {collaborators.map((user) => (
            <div
              key={user.id}
              style={{
                width: "32px",
                height: "32px",
                borderRadius: "50%",
                backgroundColor: user.color,
                color: "white",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "12px",
                fontWeight: 600,
              }}
              title={user.name}
            >
              {user.name.charAt(0)}
            </div>
          ))}
        </div>
        <button
          onClick={onSearch}
          style={{
            padding: "8px",
            border: "none",
            background: "transparent",
            cursor: "pointer",
            fontSize: "18px",
          }}
          aria-label="Search (Cmd+K)"
          title="Search (Cmd+K)"
        >
          🔍
        </button>
        <button
          onClick={onShare}
          style={{
            padding: "8px 16px",
            border: "1px solid #1976d2",
            borderRadius: "6px",
            background: "white",
            color: "#1976d2",
            cursor: "pointer",
            fontSize: "14px",
            fontWeight: 500,
          }}
          aria-label="Share"
        >
          Share
        </button>
        <button
          onClick={onSettings}
          style={{
            padding: "8px",
            border: "none",
            background: "transparent",
            cursor: "pointer",
            fontSize: "18px",
          }}
          aria-label="Settings"
        >
          ⚙️
        </button>
      </div>
    </div>
  );
};

// ============================================================================
// LEFT RAIL COMPONENT
// ============================================================================

interface LeftRailProps {
  visible?: boolean;
}

export const LeftRail: React.FC<LeftRailProps> = ({ visible = true }) => {
  const [activePanel, setActivePanel] = useAtom(chromeLeftPanelAtom);

  const togglePanel = (panel: LeftPanelType) => {
    setActivePanel(activePanel === panel ? null : panel);
  };

  if (!visible) return null;

  const railItems = [
    { id: "outline" as const, icon: "☰", label: "Outline", shortcut: "Cmd+1" },
    { id: "layers" as const, icon: "📚", label: "Layers", shortcut: "Cmd+2" },
    { id: "palette" as const, icon: "🎨", label: "Palette", shortcut: "Cmd+3" },
    { id: "tasks" as const, icon: "✓", label: "Tasks", shortcut: "Cmd+4" },
    { id: "minimap" as const, icon: "🗺️", label: "Minimap", shortcut: "Cmd+5" },
  ];

  return (
    <div
      className="canvas-left-rail"
      style={{
        width: "56px",
        height: "100%",
        backgroundColor: "#f5f5f5",
        borderRight: "1px solid #e0e0e0",
        display: "flex",
        flexDirection: "column",
        padding: "8px 0",
        gap: "4px",
        zIndex: Z_INDEX.LEFT_RAIL,
      }}
    >
      {railItems.map((item) => (
        <button
          key={item.id}
          onClick={() => togglePanel(item.id)}
          style={{
            width: "40px",
            height: "40px",
            margin: "0 8px",
            border: "none",
            borderRadius: "6px",
            background: activePanel === item.id ? "#e3f2fd" : "transparent",
            cursor: "pointer",
            fontSize: "20px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            transition: "background 0.2s",
          }}
          aria-label={`${item.label} (${item.shortcut})`}
          title={`${item.label} (${item.shortcut})`}
        >
          {item.icon}
        </button>
      ))}
    </div>
  );
};

// ============================================================================
// LEFT PANEL COMPONENT
// ============================================================================

interface LeftPanelProps {
  type: LeftPanelType;
  onClose: () => void;
  children?: React.ReactNode;
}

export const LeftPanel: React.FC<LeftPanelProps> = ({ type, onClose }) => {
  if (!type) return null;

  const panelTitles: Record<NonNullable<LeftPanelType>, string> = {
    outline: "Outline",
    layers: "Layers",
    palette: "Palette",
    tasks: "Tasks",
    minimap: "Minimap",
  };

  // Dynamically import and render the appropriate panel component
  // Note: In production, these would be lazy-loaded for better performance
  const renderPanelContent = () => {
    switch (type) {
      case "outline":
        // OutlinePanel component would be rendered here
        return (
          <div style={{ padding: "16px" }}>
            <p style={{ color: "#757575", fontSize: "14px" }}>
              Outline panel - Import OutlinePanel component to enable
            </p>
          </div>
        );
      case "layers":
        // LayersPanel component would be rendered here
        return (
          <div style={{ padding: "16px" }}>
            <p style={{ color: "#757575", fontSize: "14px" }}>
              Layers panel - Import LayersPanel component to enable
            </p>
          </div>
        );
      case "palette":
        // PalettePanel component would be rendered here
        return (
          <div style={{ padding: "16px" }}>
            <p style={{ color: "#757575", fontSize: "14px" }}>
              Palette panel - Import PalettePanel component to enable
            </p>
          </div>
        );
      case "tasks":
        // TasksPanel component would be rendered here
        return (
          <div style={{ padding: "16px" }}>
            <p style={{ color: "#757575", fontSize: "14px" }}>
              Tasks panel - Import TasksPanel component to enable
            </p>
          </div>
        );
      case "minimap":
        // MinimapPanel component would be rendered here
        return (
          <div style={{ padding: "16px" }}>
            <p style={{ color: "#757575", fontSize: "14px" }}>
              Minimap panel - Import MinimapPanel component to enable
            </p>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div
      className="canvas-left-panel"
      style={{
        width: "320px",
        height: "100%",
        backgroundColor: "#ffffff",
        borderRight: "1px solid #e0e0e0",
        display: "flex",
        flexDirection: "column",
        zIndex: Z_INDEX.LEFT_PANE,
      }}
    >
      {/* Panel Header with Close Button */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "12px 16px",
          borderBottom: "1px solid #e0e0e0",
        }}
      >
        <h3 style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>
          {panelTitles[type]}
        </h3>
        <button
          onClick={onClose}
          style={{
            border: "none",
            background: "transparent",
            cursor: "pointer",
            fontSize: "20px",
            padding: "4px",
            color: "#757575",
          }}
          aria-label={`Close ${panelTitles[type]} panel`}
        >
          ×
        </button>
      </div>
      {/* Panel Content */}
      <div style={{ flex: 1, overflow: "auto" }}>{renderPanelContent()}</div>
    </div>
  );
};

// ============================================================================
// CONTEXT BAR COMPONENT
// ============================================================================

interface ContextBarProps {
  selection?: "frame" | "element" | "none";
  position?: { x: number; y: number };
}

export const ContextBar: React.FC<ContextBarProps> = ({
  selection = "none",
  position = { x: 0, y: 0 },
}) => {
  const actions = {
    frame: [
      { icon: "✏️", label: "Rename", shortcut: "F2" },
      { icon: "🎨", label: "Color", shortcut: "C" },
      { icon: "📋", label: "Duplicate", shortcut: "Cmd+D" },
      { icon: "🗑️", label: "Delete", shortcut: "Del" },
    ],
    element: [
      { icon: "↑", label: "Bring Forward", shortcut: "Cmd+]" },
      { icon: "↓", label: "Send Backward", shortcut: "Cmd+[" },
      { icon: "🔗", label: "Connect", shortcut: "L" },
      { icon: "📋", label: "Copy", shortcut: "Cmd+C" },
      { icon: "🗑️", label: "Delete", shortcut: "Del" },
    ],
    none: [
      { icon: "➕", label: "Add Frame", shortcut: "F" },
      { icon: "🎨", label: "Draw", shortcut: "P" },
      { icon: "📝", label: "Text", shortcut: "T" },
      { icon: "🔍", label: "Search", shortcut: "Cmd+K" },
    ],
  };

  return (
    <div
      className="canvas-context-bar"
      style={{
        position: "absolute",
        top: `${position.y}px`,
        left: `${position.x}px`,
        display: "flex",
        gap: "4px",
        padding: "8px",
        backgroundColor: "#ffffff",
        borderRadius: "8px",
        boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
        zIndex: Z_INDEX.CONTEXT_BAR,
      }}
      role="toolbar"
      aria-label="Context actions"
    >
      {actions[selection].map((action, index) => (
        <button
          key={index}
          style={{
            padding: "8px 12px",
            border: "none",
            borderRadius: "6px",
            background: "transparent",
            cursor: "pointer",
            fontSize: "14px",
            display: "flex",
            alignItems: "center",
            gap: "6px",
            transition: "background 0.2s",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = "#f5f5f5";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "transparent";
          }}
          aria-label={`${action.label} (${action.shortcut})`}
          title={`${action.label} (${action.shortcut})`}
        >
          <span>{action.icon}</span>
          <span style={{ fontSize: "13px" }}>{action.label}</span>
        </button>
      ))}
    </div>
  );
};

// ============================================================================
// ZOOM HUD COMPONENT
// ============================================================================

export const ZoomHUD: React.FC = () => {
  const zoomLevel = useAtomValue(chromeZoomLevelAtom);
  const activeLayers = useAtomValue(chromeActiveLayersAtom);

  const getSemanticLevel = (zoom: number): string => {
    if (zoom < 0.5) return "Architecture View";
    if (zoom < 1.0) return "Frame View";
    if (zoom < 2.0) return "Element View";
    if (zoom < 5.0) return "Detail View";
    return "Code View";
  };

  return (
    <div
      className="canvas-zoom-hud"
      style={{
        position: "fixed",
        bottom: "24px",
        left: "24px",
        width: "200px",
        padding: "12px 16px",
        backgroundColor: "#ffffff",
        borderRadius: "8px",
        boxShadow: "0 2px 8px rgba(0,0,0,0.1)",
        zIndex: Z_INDEX.ZOOM_HUD,
      }}
      role="status"
      aria-live="polite"
    >
      <div style={{ fontSize: "14px", fontWeight: 600, marginBottom: "4px" }}>
        🔍 {getSemanticLevel(zoomLevel)}
      </div>
      <div style={{ fontSize: "12px", color: "#757575" }}>
        Zoom: {Math.round(zoomLevel * 100)}%
      </div>
      <div style={{ fontSize: "12px", color: "#757575" }}>
        Layers: {activeLayers} active
      </div>
    </div>
  );
};

// ============================================================================
// CHROME LAYOUT COMPONENT
// ============================================================================

interface CanvasChromeLayoutProps {
  children: ReactNode;
  calmMode?: boolean;
  leftRailVisible?: boolean;
  inspectorVisible?: boolean;
  minimapVisible?: boolean;
  projectName?: string;
  onProjectChange?: () => void;
  showTopBar?: boolean;
}

export const CanvasChromeLayout: React.FC<CanvasChromeLayoutProps> = ({
  children,
  calmMode = false,
  leftRailVisible = true,
  inspectorVisible = false,
  minimapVisible = false,
  projectName,
  onProjectChange,
  showTopBar = true,
}) => {
  const [leftPanel, setLeftPanel] = useAtom(chromeLeftPanelAtom);

  return (
    <div
      className="canvas-chrome-layout"
      style={{
        width: "100%",
        height: "100vh",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
      data-calm-mode={calmMode}
      data-left-rail-visible={leftRailVisible}
      data-inspector-visible={inspectorVisible}
      data-minimap-visible={minimapVisible}
    >
      {showTopBar && (
        <TopBar
          projectName={projectName}
          onProjectChange={onProjectChange}
          onSearch={() => console.log("Search")}
          onShare={() => console.log("Share")}
          onSettings={() => console.log("Settings")}
        />
      )}
      <div style={{ flex: 1, display: "flex", overflow: "hidden" }}>
        <LeftRail visible={leftRailVisible} />
        <LeftPanel type={leftPanel} onClose={() => setLeftPanel(null)} />
        <div style={{ flex: 1, position: "relative", overflow: "hidden" }}>
          {children}
          <ZoomHUD />
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

export function getPhaseColors(phase: string): PhaseColors {
  if (!hasCanvasConfig()) {
    return { primary: "#1976d2", background: "#e3f2fd", text: "#0d47a1" };
  }
  const config = getCanvasConfig();
  const phaseConfig = config.phases[phase];
  return phaseConfig?.color ?? { primary: "#1976d2", background: "#e3f2fd", text: "#0d47a1" };
}

// Command Provider (placeholder)
interface CanvasCommandProviderProps {
  children: ReactNode;
}

export const CanvasCommandProvider: React.FC<CanvasCommandProviderProps> = ({
  children,
}) => {
  return <>{children}</>;
};

// Command Hooks (placeholders)
export interface CanvasCommandSet {
  executeCommand: (command: string, ...args: unknown[]) => void;
  getAvailableCommands: () => string[];
}

export const useCanvasCommands = (): CanvasCommandSet => ({
  executeCommand: (command: string, ...args: unknown[]) => {
    console.log(`Executing command: ${command}`, args);
  },
  getAvailableCommands: () => [],
});

// Onboarding Tour (placeholder)
interface OnboardingTourProps {
  children?: ReactNode;
}

export const OnboardingTour: React.FC<OnboardingTourProps> = ({ children }) => {
  return <>{children}</>;
};

// Feature Hints Manager (placeholder)
interface FeatureHintsManagerProps {
  children?: ReactNode;
}

export const FeatureHintsManager: React.FC<FeatureHintsManagerProps> = ({
  children,
}) => {
  return <>{children}</>;
};

// Telemetry Hooks (placeholders)
export const useCanvasTelemetry = () => ({
  trackEvent: (event: string, properties?: Record<string, unknown>) => {
    console.log(`Canvas telemetry: ${event}`, properties);
  },
});

export const useABTest = () => ({
  getVariant: (testName: string) =>
    testName === "calm-mode-default" ? "enabled" : "control",
  isInTestGroup: (testName: string, group: string) =>
    group === "enabled" && testName === "calm-mode-default",
  isVariant: (variant: string) => variant === "enabled",
});
