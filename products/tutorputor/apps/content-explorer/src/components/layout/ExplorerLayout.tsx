import { Outlet, NavLink } from "react-router-dom";
import {
  Compass,
  Sparkles,
  Eye,
  BarChart3,
  Star,
  Settings,
  Film,
  FlaskConical,
  ClipboardList,
  LayoutTemplate,
} from "lucide-react";
import { clsx } from "clsx";

const NAV_ITEMS = [
  { to: "/explore", icon: Compass, label: "Explore" },
  { to: "/generate", icon: Sparkles, label: "Generate" },
  { to: "/quality", icon: Star, label: "Quality" },
  { to: "/analytics", icon: BarChart3, label: "Analytics" },
];

const STUDIO_NAV_ITEMS = [
  { to: "/animate", icon: Film, label: "Animation Editor" },
  { to: "/simulate", icon: FlaskConical, label: "Simulations" },
  { to: "/evidence", icon: ClipboardList, label: "Evidence" },
  { to: "/templates", icon: LayoutTemplate, label: "Templates" },
];

export function ExplorerLayout() {
  return (
    <div className="flex h-screen bg-background">
      {/* Sidebar */}
      <aside className="flex w-60 flex-shrink-0 flex-col border-r border-border bg-card">
        {/* Logo / brand */}
        <div className="flex h-14 items-center gap-2 border-b border-border px-4">
          <Eye className="h-5 w-5 text-primary" aria-hidden />
          <span className="text-sm font-semibold tracking-tight">Content Explorer</span>
        </div>

        {/* Navigation */}
        <nav className="flex-1 overflow-y-auto p-2" aria-label="Main navigation">
          <ul className="space-y-0.5">
            {NAV_ITEMS.map(({ to, icon: Icon, label }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  className={({ isActive }) =>
                    clsx(
                      "flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "text-muted-foreground hover:bg-muted hover:text-foreground",
                    )
                  }
                >
                  <Icon className="h-4 w-4" aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>

          {/* Studio tools section */}
          <div className="mt-4 mb-1 px-3">
            <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground/60">
              Studio
            </span>
          </div>
          <ul className="space-y-0.5">
            {STUDIO_NAV_ITEMS.map(({ to, icon: Icon, label }) => (
              <li key={to}>
                <NavLink
                  to={to}
                  className={({ isActive }) =>
                    clsx(
                      "flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                      isActive
                        ? "bg-primary/10 text-primary"
                        : "text-muted-foreground hover:bg-muted hover:text-foreground",
                    )
                  }
                >
                  <Icon className="h-4 w-4" aria-hidden />
                  {label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        {/* Settings link */}
        <div className="border-t border-border p-2">
          <NavLink
            to="/settings"
            className="flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <Settings className="h-4 w-4" aria-hidden />
            Settings
          </NavLink>
        </div>
      </aside>

      {/* Main content area */}
      <main className="flex flex-1 flex-col overflow-hidden">
        <Outlet />
      </main>
    </div>
  );
}
