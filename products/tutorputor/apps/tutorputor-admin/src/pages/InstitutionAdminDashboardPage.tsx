/**
 * Institution Admin Dashboard Page
 *
 * Persona-specific dashboard for institution administrators. Surfaces
 * institution-scoped learner and author statistics, user-role breakdown,
 * compliance posture, and subscription status — focused on the
 * Institution Admin workflow.
 *
 * @doc.type component
 * @doc.purpose Persona dashboard for institution administrators
 * @doc.layer product
 * @doc.pattern Page
 */

import { useQuery } from "@tanstack/react-query";
import { Card } from "../components/ui";
import { Spinner } from "@ghatana/design-system";

interface InstitutionStats {
  totalUsers: number;
  activeAuthors: number;
  activeLearners: number;
  publishedModules: number;
  pendingComplianceItems: number;
  subscriptionPlan: string;
  subscriptionStatus: "active" | "past_due" | "cancelled" | "trialing";
}

interface UserRoleBreakdown {
  role: string;
  count: number;
}

interface ComplianceItem {
  id: string;
  title: string;
  severity: "critical" | "warning" | "info";
  dueDate: string;
}

async function fetchInstitutionStats(): Promise<InstitutionStats> {
  const res = await fetch("/api/v1/institution/stats", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch institution stats");
  return res.json() as Promise<InstitutionStats>;
}

async function fetchUserRoleBreakdown(): Promise<UserRoleBreakdown[]> {
  const res = await fetch("/api/v1/institution/users/role-breakdown", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch user role breakdown");
  const data = await res.json() as { roles: UserRoleBreakdown[] };
  return data.roles;
}

async function fetchComplianceItems(): Promise<ComplianceItem[]> {
  const res = await fetch("/api/v1/institution/compliance/pending", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch compliance items");
  const data = await res.json() as { items: ComplianceItem[] };
  return data.items;
}

function StatCard({
  label,
  value,
}: {
  label: string;
  value: number | string;
}): React.ReactElement {
  return (
    <Card className="p-4">
      <p className="text-sm text-gray-500">{label}</p>
      <p className="mt-1 text-3xl font-semibold text-gray-900">{value}</p>
    </Card>
  );
}

function subscriptionBadge(
  status: InstitutionStats["subscriptionStatus"],
): React.ReactElement {
  const classes: Record<InstitutionStats["subscriptionStatus"], string> = {
    active: "bg-green-100 text-green-800",
    trialing: "bg-blue-100 text-blue-800",
    past_due: "bg-yellow-100 text-yellow-800",
    cancelled: "bg-red-100 text-red-800",
  };
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${classes[status]}`}
    >
      {status.replace("_", " ")}
    </span>
  );
}

function severityColor(severity: ComplianceItem["severity"]): string {
  switch (severity) {
    case "critical":
      return "text-red-700 bg-red-50 border-red-200";
    case "warning":
      return "text-yellow-700 bg-yellow-50 border-yellow-200";
    case "info":
      return "text-blue-700 bg-blue-50 border-blue-200";
  }
}

export function InstitutionAdminDashboardPage(): React.ReactElement {
  const {
    data: stats,
    isLoading: statsLoading,
    isError: statsError,
  } = useQuery<InstitutionStats>({
    queryKey: ["institution", "stats"],
    queryFn: fetchInstitutionStats,
  });

  const {
    data: roles,
    isLoading: rolesLoading,
    isError: rolesError,
  } = useQuery<UserRoleBreakdown[]>({
    queryKey: ["institution", "users", "role-breakdown"],
    queryFn: fetchUserRoleBreakdown,
  });

  const {
    data: compliance,
    isLoading: complianceLoading,
    isError: complianceError,
  } = useQuery<ComplianceItem[]>({
    queryKey: ["institution", "compliance"],
    queryFn: fetchComplianceItems,
  });

  return (
    <div className="space-y-8 p-6">
      <header className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            Institution Admin Dashboard
          </h1>
          <p className="mt-1 text-sm text-gray-500">
            User management, compliance posture, and subscription status for
            your institution.
          </p>
        </div>
        {stats && (
          <div className="text-right">
            <p className="text-xs text-gray-400">Subscription</p>
            <p className="mt-0.5 text-sm font-medium text-gray-700">
              {stats.subscriptionPlan}
            </p>
            {subscriptionBadge(stats.subscriptionStatus)}
          </div>
        )}
      </header>

      {/* ── Institution Stats ────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Institution Overview
        </h2>
        {statsLoading && <Spinner />}
        {statsError && (
          <p className="text-sm text-red-600">
            Failed to load institution stats.
          </p>
        )}
        {stats && (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-5">
            <StatCard label="Total Users" value={stats.totalUsers} />
            <StatCard label="Active Authors" value={stats.activeAuthors} />
            <StatCard label="Active Learners" value={stats.activeLearners} />
            <StatCard label="Published Modules" value={stats.publishedModules} />
            <StatCard
              label="Compliance Items"
              value={stats.pendingComplianceItems}
            />
          </div>
        )}
      </section>

      {/* ── Quick Actions ────────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Quick Actions
        </h2>
        <div className="flex flex-wrap gap-3">
          <a
            href="/users"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            Manage Users
          </a>
          <a
            href="/settings/compliance"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Compliance Centre
          </a>
          <a
            href="/settings"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Institution Settings
          </a>
        </div>
      </section>

      {/* ── User Role Breakdown ──────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Users by Role
        </h2>
        {rolesLoading && <Spinner />}
        {rolesError && (
          <p className="text-sm text-red-600">Failed to load role breakdown.</p>
        )}
        {roles && roles.length === 0 && (
          <p className="text-sm text-gray-500">No users found.</p>
        )}
        {roles && roles.length > 0 && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {roles.map((row) => (
              <Card key={row.role} className="p-3">
                <p className="text-sm font-medium capitalize text-gray-700">
                  {row.role}
                </p>
                <p className="mt-1 text-2xl font-semibold text-gray-900">
                  {row.count}
                </p>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* ── Pending Compliance Items ─────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Pending Compliance Items
        </h2>
        {complianceLoading && <Spinner />}
        {complianceError && (
          <p className="text-sm text-red-600">
            Failed to load compliance items.
          </p>
        )}
        {compliance && compliance.length === 0 && (
          <p className="text-sm text-green-700">
            No pending compliance items. You are in good standing.
          </p>
        )}
        {compliance && compliance.length > 0 && (
          <ul className="space-y-2">
            {compliance.map((item) => (
              <li
                key={item.id}
                className={`flex items-center justify-between rounded-md border px-4 py-3 ${severityColor(item.severity)}`}
              >
                <span className="text-sm font-medium">{item.title}</span>
                <span className="text-xs">Due {item.dueDate}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
