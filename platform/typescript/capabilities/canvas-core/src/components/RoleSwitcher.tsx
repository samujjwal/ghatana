/**
 * Role Switcher Component
 *
 * Allows users to select and manage active persona roles.
 * Supports multi-role selection for combined action sets.
 */

import React, { useState } from "react";
import { useAtom } from "jotai";
import { chromeActiveRolesAtom } from "../chrome";
import { hasCanvasConfig, getCanvasConfig } from "../core/canvas-config";

export const RoleSwitcher: React.FC = () => {
  const [activeRoles, setActiveRoles] = useAtom(chromeActiveRolesAtom);
  const [isOpen, setIsOpen] = useState(false);

  const roleConfigs = hasCanvasConfig()
    ? Object.values(getCanvasConfig().roles).map((r) => ({
        role: r.name,
        displayName: r.displayName,
        icon: r.icon,
        color: r.color,
      }))
    : [];

  const toggleRole = (role: string) => {
    if (activeRoles.includes(role)) {
      if (activeRoles.length > 1) {
        setActiveRoles(activeRoles.filter((r) => r !== role));
      }
    } else {
      setActiveRoles([...activeRoles, role]);
    }
  };

  const primaryRole = activeRoles[0];
  const primaryConfig = roleConfigs.find((c) => c.role === primaryRole) ?? {
    role: primaryRole ?? "",
    displayName: primaryRole ?? "Select Role",
    icon: "\u{1F464}",
    color: "#1976d2",
  };

  return (
    <div style={{ position: "relative" }}>
      <button
        onClick={() => setIsOpen(!isOpen)}
        style={{
          padding: "6px 12px",
          border: "1px solid #e0e0e0",
          borderRadius: "6px",
          background: "white",
          cursor: "pointer",
          fontSize: "13px",
          fontWeight: 500,
          display: "flex",
          alignItems: "center",
          gap: "6px",
        }}
        aria-label="Select roles"
      >
        <span>{primaryConfig.icon}</span>
        <span>{primaryConfig.displayName}</span>
        {activeRoles.length > 1 && (
          <span
            style={{
              fontSize: "11px",
              backgroundColor: "#e3f2fd",
              color: "#1976d2",
              padding: "2px 6px",
              borderRadius: "10px",
            }}
          >
            +{activeRoles.length - 1}
          </span>
        )}
        <span>▼</span>
      </button>

      {isOpen && (
        <div
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            right: 0,
            width: "280px",
            backgroundColor: "white",
            border: "1px solid #e0e0e0",
            borderRadius: "8px",
            boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            zIndex: 1000,
            padding: "8px",
          }}
        >
          <div
            style={{
              fontSize: "12px",
              fontWeight: 600,
              color: "#757575",
              padding: "8px",
              borderBottom: "1px solid #e0e0e0",
              marginBottom: "8px",
            }}
          >
            Active Roles ({activeRoles.length})
          </div>

          {roleConfigs.map((config) => {
            const isActive = activeRoles.includes(config.role);
            return (
              <button
                key={config.role}
                onClick={() => toggleRole(config.role)}
                style={{
                  width: "100%",
                  padding: "8px 12px",
                  border: "none",
                  borderRadius: "6px",
                  background: isActive ? "#e3f2fd" : "transparent",
                  cursor: "pointer",
                  fontSize: "13px",
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  marginBottom: "4px",
                  textAlign: "left",
                }}
                onMouseEnter={(e) => {
                  if (!isActive) {
                    e.currentTarget.style.background = "#f5f5f5";
                  }
                }}
                onMouseLeave={(e) => {
                  if (!isActive) {
                    e.currentTarget.style.background = "transparent";
                  }
                }}
              >
                <span style={{ fontSize: "16px" }}>{config.icon}</span>
                <span style={{ flex: 1 }}>{config.displayName}</span>
                {isActive && (
                  <span style={{ color: "#1976d2", fontSize: "16px" }}>✓</span>
                )}
              </button>
            );
          })}

          <div
            style={{
              fontSize: "11px",
              color: "#757575",
              padding: "8px",
              marginTop: "8px",
              borderTop: "1px solid #e0e0e0",
            }}
          >
            Select multiple roles to combine their actions
          </div>
        </div>
      )}
    </div>
  );
};
