/**
 * Teacher / Author Dashboard Page
 *
 * Persona-specific dashboard for teachers and content authors. Surfaces
 * the authoring pipeline status, learner progress on authored content,
 * and upcoming review tasks — focused on the Teacher / Author workflow.
 *
 * @doc.type component
 * @doc.purpose Persona dashboard for teachers and content authors
 * @doc.layer product
 * @doc.pattern Page
 */

import { useQuery } from "@tanstack/react-query";
import { Card } from "../components/ui";
import { Spinner } from "@ghatana/design-system";

interface AuthoringStats {
  publishedModules: number;
  draftModules: number;
  pendingReview: number;
  totalLearnerEnrollments: number;
}

interface RecentActivity {
  id: string;
  type: "published" | "review_request" | "learner_feedback";
  title: string;
  timestamp: string;
}

interface LearnerProgressSummary {
  moduleTitle: string;
  enrolled: number;
  avgCompletion: number;
}

async function fetchAuthoringStats(): Promise<AuthoringStats> {
  const res = await fetch("/api/v1/author/stats", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch authoring stats");
  return res.json() as Promise<AuthoringStats>;
}

async function fetchRecentActivity(): Promise<RecentActivity[]> {
  const res = await fetch("/api/v1/author/activity?limit=10", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch recent activity");
  const data = await res.json() as { items: RecentActivity[] };
  return data.items;
}

async function fetchLearnerProgress(): Promise<LearnerProgressSummary[]> {
  const res = await fetch("/api/v1/author/learner-progress?limit=5", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch learner progress");
  const data = await res.json() as { items: LearnerProgressSummary[] };
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

function activityLabel(type: RecentActivity["type"]): string {
  switch (type) {
    case "published":
      return "Published";
    case "review_request":
      return "Review requested";
    case "learner_feedback":
      return "Learner feedback";
  }
}

export function TeacherDashboardPage(): React.ReactElement {
  const {
    data: stats,
    isLoading: statsLoading,
    isError: statsError,
  } = useQuery<AuthoringStats>({
    queryKey: ["author", "stats"],
    queryFn: fetchAuthoringStats,
  });

  const {
    data: activity,
    isLoading: activityLoading,
    isError: activityError,
  } = useQuery<RecentActivity[]>({
    queryKey: ["author", "activity"],
    queryFn: fetchRecentActivity,
  });

  const {
    data: progress,
    isLoading: progressLoading,
    isError: progressError,
  } = useQuery<LearnerProgressSummary[]>({
    queryKey: ["author", "learner-progress"],
    queryFn: fetchLearnerProgress,
  });

  return (
    <div className="space-y-8 p-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">
          Teacher / Author Dashboard
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Your content pipeline status, learner engagement, and pending tasks.
        </p>
      </header>

      {/* ── Authoring Stats ─────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Content Overview
        </h2>
        {statsLoading && <Spinner />}
        {statsError && (
          <p className="text-sm text-red-600">
            Failed to load authoring stats.
          </p>
        )}
        {stats && (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
            <StatCard label="Published Modules" value={stats.publishedModules} />
            <StatCard label="Drafts in Progress" value={stats.draftModules} />
            <StatCard label="Pending Review" value={stats.pendingReview} />
            <StatCard
              label="Total Enrollments"
              value={stats.totalLearnerEnrollments}
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
            href="/authoring/new"
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            + New Module
          </a>
          <a
            href="/authoring"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Continue Drafts
          </a>
          <a
            href="/analytics"
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            View Analytics
          </a>
        </div>
      </section>

      {/* ── Learner Progress ─────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Learner Progress — Top Modules
        </h2>
        {progressLoading && <Spinner />}
        {progressError && (
          <p className="text-sm text-red-600">
            Failed to load learner progress.
          </p>
        )}
        {progress && progress.length === 0 && (
          <p className="text-sm text-gray-500">No enrollments yet.</p>
        )}
        {progress && progress.length > 0 && (
          <div className="overflow-hidden rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500">
                    Module
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">
                    Enrolled
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wide text-gray-500">
                    Avg Completion
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white">
                {progress.map((row) => (
                  <tr key={row.moduleTitle}>
                    <td className="px-4 py-3 text-sm text-gray-800">
                      {row.moduleTitle}
                    </td>
                    <td className="px-4 py-3 text-right text-sm text-gray-600">
                      {row.enrolled}
                    </td>
                    <td className="px-4 py-3 text-right text-sm text-gray-600">
                      {row.avgCompletion}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── Recent Activity ──────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Recent Activity
        </h2>
        {activityLoading && <Spinner />}
        {activityError && (
          <p className="text-sm text-red-600">
            Failed to load recent activity.
          </p>
        )}
        {activity && activity.length === 0 && (
          <p className="text-sm text-gray-500">No recent activity.</p>
        )}
        {activity && activity.length > 0 && (
          <ul className="space-y-2">
            {activity.map((item) => (
              <li
                key={item.id}
                className="flex items-center justify-between rounded-md border border-gray-100 bg-white px-4 py-3 shadow-sm"
              >
                <div>
                  <span className="text-xs font-medium uppercase tracking-wide text-primary-600">
                    {activityLabel(item.type)}
                  </span>
                  <p className="mt-0.5 text-sm text-gray-800">{item.title}</p>
                </div>
                <time className="text-xs text-gray-400">{item.timestamp}</time>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
