/**
 * Smart Context Bar Component
 *
 * Context-aware action bar that adapts based on:
 * - Current semantic layer
 * - Current lifecycle phase
 * - Active persona roles
 * - Selected elements
 */

import React, { useState, useMemo } from "react";
import { useAtomValue } from "jotai";
import {
  chromeSemanticLayerAtom,
  chromeCurrentPhaseAtom,
  chromeActiveRolesAtom,
  chromeAvailableActionsAtom,
  Action,
  Z_INDEX,
} from "../chrome";

interface SmartContextBarProps {
  selection?: "frame" | "element" | "none";
  position?: { x: number; y: number };
}

export const SmartContextBar: React.FC<SmartContextBarProps> = ({
  position = { x: 100, y: 100 },
}) => {
  const layer = useAtomValue(chromeSemanticLayerAtom);
  const phase = useAtomValue(chromeCurrentPhaseAtom);
  const roles = useAtomValue(chromeActiveRolesAtom);
  const availableActions = useAtomValue(chromeAvailableActionsAtom);
  const [showMore, setShowMore] = useState(false);

  // Use resolved actions from atom (already prioritized and deduplicated)
  const allActions = useMemo(() => {
    return availableActions || [];
  }, [availableActions]);

  // Show top 4 actions + More button
  const primaryActions = useMemo(() => allActions.slice(0, 4), [allActions]);
  const moreActions = useMemo(() => allActions.slice(4), [allActions]);

  return (
    <div
      className="smart-context-bar"
      style={{
        position: "absolute",
        top: `${position.y}px`,
        left: `${position.x}px`,
        display: "flex",
        flexDirection: "column",
        gap: "4px",
        zIndex: Z_INDEX.CONTEXT_BAR,
      }}
    >
      {/* Context Header */}
      <div
        style={{
          fontSize: "11px",
          color: "#757575",
          padding: "4px 8px",
          backgroundColor: "#f5f5f5",
          borderRadius: "6px",
          display: "flex",
          gap: "8px",
        }}
      >
        <span>Layer: {layer}</span>
        <span>•</span>
        <span>Phase: {phase}</span>
        <span>•</span>
        <span>Roles: {roles.length}</span>
      </div>

      {/* Primary Actions */}
      <div
        style={{
          display: "flex",
          gap: "4px",
          padding: "8px",
          backgroundColor: "#ffffff",
          borderRadius: "8px",
          boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
        }}
        role="toolbar"
        aria-label="Context actions"
      >
        {primaryActions.map((action) => (
          <button
            key={action.id}
            onClick={action.handler}
            style={{
              padding: "8px 12px",
              border: "none",
              borderRadius: "6px",
              background: "transparent",
              cursor: "pointer",
              fontSize: "14px",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              transition: "background 0.2s",
              minWidth: "60px",
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
            <span style={{ fontSize: "20px" }}>{action.icon}</span>
            <span style={{ fontSize: "11px", color: "#616161" }}>
              {action.label}
            </span>
          </button>
        ))}

        {moreActions.length > 0 && (
          <button
            onClick={() => setShowMore(!showMore)}
            style={{
              padding: "8px 12px",
              border: "none",
              borderRadius: "6px",
              background: showMore ? "#e3f2fd" : "transparent",
              cursor: "pointer",
              fontSize: "14px",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "4px",
              transition: "background 0.2s",
              minWidth: "60px",
            }}
            onMouseEnter={(e) => {
              if (!showMore) {
                e.currentTarget.style.background = "#f5f5f5";
              }
            }}
            onMouseLeave={(e) => {
              if (!showMore) {
                e.currentTarget.style.background = "transparent";
              }
            }}
            aria-label="More actions"
          >
            <span style={{ fontSize: "20px" }}>⋯</span>
            <span style={{ fontSize: "11px", color: "#616161" }}>More</span>
          </button>
        )}
      </div>

      {/* More Actions Dropdown */}
      {showMore && moreActions.length > 0 && (
        <div
          style={{
            padding: "8px",
            backgroundColor: "#ffffff",
            borderRadius: "8px",
            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            maxHeight: "400px",
            overflowY: "auto",
          }}
        >
          {/* Group by category */}
          {["layer", "phase", "role", "universal"].map((category) => {
            const categoryActions = moreActions.filter(
              (a) => a.category === category,
            );
            if (categoryActions.length === 0) return null;

            return (
              <div key={category} style={{ marginBottom: "8px" }}>
                <div
                  style={{
                    fontSize: "11px",
                    fontWeight: 600,
                    color: "#757575",
                    padding: "4px 8px",
                    textTransform: "capitalize",
                  }}
                >
                  {category} Actions
                </div>
                {categoryActions.map((action) => (
                  <button
                    key={action.id}
                    onClick={action.handler}
                    style={{
                      width: "100%",
                      padding: "8px 12px",
                      border: "none",
                      borderRadius: "6px",
                      background: "transparent",
                      cursor: "pointer",
                      fontSize: "13px",
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      textAlign: "left",
                      transition: "background 0.2s",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.background = "#f5f5f5";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.background = "transparent";
                    }}
                    aria-label={`${action.label} (${action.shortcut})`}
                  >
                    <span style={{ fontSize: "16px" }}>{action.icon}</span>
                    <span style={{ flex: 1 }}>{action.label}</span>
                    <span
                      style={{
                        fontSize: "11px",
                        color: "#9e9e9e",
                        fontFamily: "monospace",
                      }}
                    >
                      {action.shortcut}
                    </span>
                  </button>
                ))}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
