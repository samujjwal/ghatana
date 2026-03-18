/**
 * Mobile Shell Layout
 * 
 * Mobile-optimized layout for Capacitor app.
 * Touch-first design with bottom navigation.
 */

import { useState } from 'react';
import { NavLink } from "react-router";

import { RouteErrorBoundary } from "../../components/route/ErrorBoundary";

const mobileNavItems = [
  { key: "dashboard", label: "Dashboard", icon: "🏠", path: "/mobile/overview" },
  { key: "projects", label: "Projects", icon: "📁", path: "/mobile/projects" },
  { key: "notifications", label: "Alerts", icon: "🔔", path: "/mobile/notifications" },
  { key: "settings", label: "Settings", icon: "⚙️", path: "/mobile/settings" }
];

/**
 *
 */
export function Layout({ children }: { children: React.ReactNode }) {
  const [filterDrawerOpen, setFilterDrawerOpen] = useState(false);
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        backgroundColor: "var(--bg-default)",
        color: "var(--text-primary)"
      }}
    >
      {/* Mobile Header */}
      <header
        style={{
          padding: "1rem",
          backgroundColor: "var(--primary-color)",
          color: "white",
          textAlign: "center",
          boxShadow: "var(--shadow-md)"
        }}
      >
        <h1 className="m-0 text-xl font-semibold">
          <button
            data-testid="mobile-menu-button"
            aria-label="Open menu"
            onClick={() => setFilterDrawerOpen(true)}
            className="bg-transparent border-0 text-white text-xl font-semibold"
          >
            YAPPC Mobile
          </button>
        </h1>
      </header>

      {/* Main Content */}
      <main
        style={{
          flex: 1,
          padding: "1rem",
          paddingBottom: "80px", // Space for bottom nav
          overflow: "auto",
          backgroundColor: "var(--bg-default)"
        }}
      >
        {children}
      </main>

      {/* Bottom Navigation */}
      <nav
        style={{
          position: "fixed",
          bottom: 0,
          left: 0,
          right: 0,
          display: "flex",
          backgroundColor: "var(--bg-paper)",
          borderTop: "1px solid var(--divider)",
          boxShadow: "var(--shadow-lg)",
          paddingBottom: "env(safe-area-inset-bottom)"
        }}
      >
        {mobileNavItems.map((item) => (
          <NavLink
            key={item.key}
            to={item.path}
            data-testid={`bottom-nav-${item.key}`}
            style={({ isActive }) => ({
              flex: 1,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              padding: "0.75rem 0.5rem",
              textDecoration: "none",
              color: isActive ? "var(--primary-color)" : "var(--text-secondary)",
              fontSize: "0.75rem",
              fontWeight: isActive ? 600 : 400,
              transition: "var(--transition-fast)"
            })}
          >
            <span className="text-xl mb-1">
              {item.icon}
            </span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Simple Drawer for mobile menu (test helper) */}
      {filterDrawerOpen && (
        <div
          data-testid="mobile-drawer"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            bottom: 0,
            width: "80%",
            backgroundColor: "var(--bg-paper)",
            zIndex: 2000,
            boxShadow: "var(--shadow-lg)",
            padding: "1rem",
            color: "var(--text-primary)"
          }}
        >
          <button
            data-testid="mobile-drawer-close"
            onClick={() => setFilterDrawerOpen(false)}
            style={{
              display: "block",
              marginLeft: "auto",
              backgroundColor: "transparent",
              border: "none",
              color: "var(--text-primary)",
              cursor: "pointer"
            }}
          >
            Close
          </button>
          <div style={{ marginTop: "1rem" }}>
            <ul style={{ listStyle: "none", padding: 0 }}>
              <li><a data-testid="mobile-project-card" href="/app/projects" style={{ color: "var(--primary-color)" }}>Projects</a></li>
              <li><a data-testid="mobile-dashboard" href="/" style={{ color: "var(--primary-color)" }}>Dashboard</a></li>
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 *
 */
export function ErrorBoundary() {
  return (
    <RouteErrorBoundary
      title="Mobile App Error"
      message="Unable to load the mobile application layout."
    />
  );
}