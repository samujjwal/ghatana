import React, { Suspense } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { atom, useAtom } from "jotai";
import { Navigate, Route, Routes, useNavigate, useLocation } from "react-router-dom";

// ---------------------------------------------------------------------------
// Auth atom (JWT + role claims)
// ---------------------------------------------------------------------------

interface AuthState {
  token: string | null;
  userId: string | null;
  roles: string[];        // e.g. ["PLATFORM_ADMIN", "KYC_REVIEWER"]
  displayName: string | null;
}

export const authAtom = atom<AuthState>({
  token: null,
  userId: null,
  roles: [],
  displayName: null,
});

// ---------------------------------------------------------------------------
// Query client
// ---------------------------------------------------------------------------

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
});

// ---------------------------------------------------------------------------
// Role-based access
// ---------------------------------------------------------------------------

const ROLE_MENU_ACCESS: Record<string, string[]> = {
  PLATFORM_ADMIN:      ["config", "health", "users", "roles", "api-keys", "plugins", "audit"],
  KYC_REVIEWER:        ["audit"],
  COMPLIANCE_OFFICER:  ["audit", "users"],
  PLUGIN_MANAGER:      ["plugins"],
  READ_ONLY:           ["health", "audit"],
};

function canAccess(roles: string[], page: string): boolean {
  return roles.some((r) => ROLE_MENU_ACCESS[r]?.includes(page));
}

// ---------------------------------------------------------------------------
// Nav items
// ---------------------------------------------------------------------------

interface NavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path: string;
}

const NAV_ITEMS: NavItem[] = [
  { id: "health",   label: "System Health",       icon: <HeartIcon />,   path: "/health" },
  { id: "config",   label: "Configuration",        icon: <CogIcon />,     path: "/config" },
  { id: "users",    label: "Users",                icon: <UsersIcon />,   path: "/users" },
  { id: "roles",    label: "Roles & Permissions",  icon: <ShieldIcon />,  path: "/roles" },
  { id: "api-keys", label: "API Keys",             icon: <KeyIcon />,     path: "/api-keys" },
  { id: "plugins",  label: "Plugin Registry",      icon: <PuzzleIcon />,  path: "/plugins" },
  { id: "audit",    label: "Audit Logs",           icon: <ClipboardIcon />, path: "/audit" },
];

// ---------------------------------------------------------------------------
// Sidebar
// ---------------------------------------------------------------------------

interface SidebarProps {
  collapsed: boolean;
  onToggle: () => void;
}

function Sidebar({ collapsed, onToggle }: SidebarProps) {
  const [auth, setAuth] = useAtom(authAtom);
  const location = useLocation();
  const navigate = useNavigate();

  const visibleItems = NAV_ITEMS.filter((item) => canAccess(auth.roles, item.id));

  function handleLogout() {
    setAuth({ token: null, userId: null, roles: [], displayName: null });
    navigate("/login");
  }

  return (
    <aside
      className={`flex flex-col bg-slate-900 text-white transition-all duration-200
                  ${collapsed ? "w-16" : "w-60"} min-h-screen`}
    >
      {/* Logo row */}
      <div className="flex items-center h-16 px-3 border-b border-slate-700">
        {!collapsed && (
          <span className="text-lg font-bold tracking-tight text-white mr-auto">
            Ghatana <span className="text-blue-400">Admin</span>
          </span>
        )}
        <button
          onClick={onToggle}
          className="ml-auto p-1.5 rounded hover:bg-slate-700 transition-colors"
          aria-label="Toggle sidebar"
        >
          {collapsed
            ? <ChevronRightIcon className="h-5 w-5 text-slate-300" />
            : <ChevronLeftIcon  className="h-5 w-5 text-slate-300" />}
        </button>
      </div>

      {/* Nav links */}
      <nav className="flex-1 py-4 space-y-1 px-2">
        {visibleItems.map((item) => {
          const active = location.pathname.startsWith(item.path);
          return (
            <button
              key={item.id}
              onClick={() => navigate(item.path)}
              className={`w-full flex items-center gap-3 px-2 py-2 rounded-lg text-sm transition-colors
                ${active
                  ? "bg-blue-600 text-white"
                  : "text-slate-300 hover:bg-slate-700 hover:text-white"}`}
              title={collapsed ? item.label : undefined}
            >
              <span className="flex-shrink-0 h-5 w-5">{item.icon}</span>
              {!collapsed && <span className="truncate">{item.label}</span>}
            </button>
          );
        })}
      </nav>

      {/* User footer */}
      <div className="border-t border-slate-700 p-3">
        <div className={`flex items-center gap-2 ${collapsed ? "justify-center" : ""}`}>
          <div className="h-8 w-8 rounded-full bg-blue-500 flex items-center justify-center text-xs font-bold flex-shrink-0">
            {auth.displayName?.[0]?.toUpperCase() ?? "?"}
          </div>
          {!collapsed && (
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate">{auth.displayName ?? "Unknown"}</p>
              <p className="text-xs text-slate-400 truncate">{auth.roles[0] ?? ""}</p>
            </div>
          )}
        </div>
        {!collapsed && (
          <button
            onClick={handleLogout}
            className="mt-2 w-full text-xs text-slate-400 hover:text-white transition-colors text-left px-1"
          >
            Sign out
          </button>
        )}
      </div>
    </aside>
  );
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

interface HeaderProps {
  title: string;
}

function Header({ title }: HeaderProps) {
  const [auth] = useAtom(authAtom);
  return (
    <header className="h-16 bg-white border-b border-gray-200 flex items-center px-6 justify-between">
      <h1 className="text-lg font-semibold text-gray-800">{title}</h1>
      <div className="flex items-center gap-2 text-sm text-gray-500">
        <span>{auth.displayName}</span>
        <div className="h-2 w-2 rounded-full bg-green-400" title="Connected" />
      </div>
    </header>
  );
}

// ---------------------------------------------------------------------------
// RequireAuth
// ---------------------------------------------------------------------------

function RequireAuth({ children, page }: { children: React.ReactNode; page: string }) {
  const [auth] = useAtom(authAtom);
  if (!auth.token) return <Navigate to="/login" replace />;
  if (!canAccess(auth.roles, page)) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <p className="text-2xl font-bold text-gray-700">403</p>
          <p className="text-gray-500 mt-1">You do not have permission to access this page.</p>
        </div>
      </div>
    );
  }
  return <>{children}</>;
}

// ---------------------------------------------------------------------------
// Page title helper
// ---------------------------------------------------------------------------

function pageTitleFor(pathname: string): string {
  const item = NAV_ITEMS.find((n) => pathname.startsWith(n.path));
  return item?.label ?? "Admin Portal";
}

// ---------------------------------------------------------------------------
// Lazy pages (loaded via dynamic import so bundle chunks correctly)
// ---------------------------------------------------------------------------

const SystemHealthDashboardPage   = React.lazy(() => import("./SystemHealthDashboardPage"));
const ConfigurationManagementPage = React.lazy(() => import("./ConfigurationManagementPage"));
const UserManagementPage          = React.lazy(() => import("./UserManagementPage"));
const RolePermissionManagementPage = React.lazy(() => import("./RolePermissionManagementPage"));
const ApiKeyManagementPage        = React.lazy(() => import("./ApiKeyManagementPage"));
const PluginRegistryPage          = React.lazy(() => import("./PluginRegistryPage"));
const AuditLogExplorerPage        = React.lazy(() => import("./AuditLogExplorerPage"));

// ---------------------------------------------------------------------------
// Login page (minimal – real auth via K-01)
// ---------------------------------------------------------------------------

function LoginPage() {
  const [auth, setAuth] = useAtom(authAtom);
  const navigate = useNavigate();
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);

  async function login(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      if (!res.ok) { setError("Invalid credentials"); return; }
      const { token, userId, roles, displayName } = await res.json();
      setAuth({ token, userId, roles, displayName });
      navigate("/health");
    } catch {
      setError("Connection error. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  if (auth.token) return <Navigate to="/health" replace />;

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center">
      <div className="bg-white rounded-2xl shadow-xl p-8 w-full max-w-sm space-y-6">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-gray-900">Ghatana Admin</h2>
          <p className="text-sm text-gray-500 mt-1">Sign in to your account</p>
        </div>
        {error && (
          <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-sm text-red-700">
            {error}
          </div>
        )}
        <form onSubmit={login} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Username</label>
            <input
              type="text" required value={username} onChange={(e) => setUsername(e.target.value)}
              className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="username"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Password</label>
            <input
              type="password" required value={password} onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full border border-gray-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              autoComplete="current-password"
            />
          </div>
          <button
            type="submit" disabled={loading}
            className="w-full bg-blue-600 text-white rounded-lg py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors"
          >
            {loading ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shell layout
// ---------------------------------------------------------------------------

function ShellLayout() {
  const [collapsed, setCollapsed] = React.useState(false);
  const location = useLocation();

  return (
    <div className="flex min-h-screen bg-gray-50">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header title={pageTitleFor(location.pathname)} />
        <main className="flex-1 overflow-auto p-6">
          <Suspense fallback={<PageLoader />}>
            <Routes>
              <Route index element={<Navigate to="/health" replace />} />
              <Route path="/health"   element={<RequireAuth page="health"><SystemHealthDashboardPage /></RequireAuth>} />
              <Route path="/config"   element={<RequireAuth page="config"><ConfigurationManagementPage /></RequireAuth>} />
              <Route path="/users"    element={<RequireAuth page="users"><UserManagementPage /></RequireAuth>} />
              <Route path="/roles"    element={<RequireAuth page="roles"><RolePermissionManagementPage /></RequireAuth>} />
              <Route path="/api-keys" element={<RequireAuth page="api-keys"><ApiKeyManagementPage /></RequireAuth>} />
              <Route path="/plugins"  element={<RequireAuth page="plugins"><PluginRegistryPage /></RequireAuth>} />
              <Route path="/audit"    element={<RequireAuth page="audit"><AuditLogExplorerPage /></RequireAuth>} />
            </Routes>
          </Suspense>
        </main>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Root app
// ---------------------------------------------------------------------------

export function AdminPortalApp() {
  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/*"     element={<ShellLayout />} />
      </Routes>
    </QueryClientProvider>
  );
}

export default AdminPortalApp;

// ---------------------------------------------------------------------------
// Minimal page loader
// ---------------------------------------------------------------------------

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-64 text-gray-400">
      <svg className="animate-spin h-6 w-6 mr-2" viewBox="0 0 24 24" fill="none">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8z" />
      </svg>
      Loading…
    </div>
  );
}

// ---------------------------------------------------------------------------
// Icon stubs (Heroicons-compatible shapes)
// ---------------------------------------------------------------------------

function HeartIcon()     { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" /></svg>; }
function CogIcon()       { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>; }
function UsersIcon()     { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z" /></svg>; }
function ShieldIcon()    { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" /></svg>; }
function KeyIcon()       { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z" /></svg>; }
function PuzzleIcon()    { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z" /></svg>; }
function ClipboardIcon() { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" /></svg>; }
function ChevronLeftIcon(props: { className?: string })  { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" className={props.className}><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>; }
function ChevronRightIcon(props: { className?: string }) { return <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" className={props.className}><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg>; }
