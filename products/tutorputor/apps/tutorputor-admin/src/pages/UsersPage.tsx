import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Card, Badge } from "../components/ui";
import {
  Button,
  TextField,
  Spinner,
  ResponsiveTable,
  PullToRefresh,
  ResponsiveImage,
} from "@ghatana/design-system";
import { useAuth } from "../hooks/useAuth";
import type {
  UserSummary,
  PaginatedResult,
} from "@ghatana/tutorputor-contracts/v1/types";

export function UsersPage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("");
  const [showImportModal, setShowImportModal] = useState(false);

  const { data, isLoading, error } = useQuery({
    queryKey: ["users", tenantId, search, roleFilter],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (search) params.set("search", search);
      if (roleFilter) params.set("role", roleFilter);
      params.set("limit", "50");

      const res = await fetch(`/admin/api/v1/users?${params}`);
      if (!res.ok) throw new Error("Failed to fetch users");
      return res.json() as Promise<PaginatedResult<UserSummary>>;
    },
  });

  const updateRoleMutation = useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: string }) => {
      const res = await fetch(`/admin/api/v1/users/${userId}/role`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role }),
      });
      if (!res.ok) throw new Error("Failed to update role");
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
    },
  });

  const roleColors: Record<
    string,
    "default" | "secondary" | "destructive" | "outline"
  > = {
    admin: "destructive",
    teacher: "default",
    creator: "secondary",
    student: "outline",
  };

  const handleRefresh = async () => {
    await queryClient.fetchQuery({
      queryKey: ["users", tenantId, search, roleFilter],
    });
  };

  return (
    <PullToRefresh onRefresh={handleRefresh}>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
              Users
            </h1>
            <p className="text-gray-600 dark:text-gray-400">
              Manage users in your organization
            </p>
          </div>
          <div className="flex gap-3">
            <Button variant="outline" onClick={() => setShowImportModal(true)}>
              Import Users
            </Button>
            <Button>Invite User</Button>
          </div>
        </div>

        {/* Filters */}
        <Card className="p-4">
          <div className="flex gap-4">
            <div className="flex-1">
              <TextField
                placeholder="Search by name or email..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            <select
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800"
            >
              <option value="">All Roles</option>
              <option value="admin">Admin</option>
              <option value="teacher">Teacher</option>
              <option value="creator">Creator</option>
              <option value="student">Student</option>
            </select>
          </div>
        </Card>

        {/* Users Table */}
        <Card className="overflow-hidden">
          {error ? (
            <div className="p-6 text-center text-red-600">
              Failed to load users. Please try again.
            </div>
          ) : (
            <ResponsiveTable
              data={data?.items || []}
              getRowKey={(user) => user.id}
              isLoading={isLoading}
              columns={[
                {
                  header: "User",
                  accessor: (user) => (
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-primary-100 dark:bg-primary-900 overflow-hidden flex items-center justify-center shrink-0">
                        {user.avatarUrl ? (
                          <ResponsiveImage
                            src={user.avatarUrl}
                            alt={user.displayName}
                            className="w-full h-full object-cover"
                            lazy
                          />
                        ) : (
                          <span className="text-primary-600 dark:text-primary-400 font-medium">
                            {user.displayName.charAt(0).toUpperCase()}
                          </span>
                        )}
                      </div>
                      <div>
                        <p className="font-medium text-gray-900 dark:text-white">
                          {user.displayName}
                        </p>
                        <p className="text-sm text-gray-500">{user.email}</p>
                      </div>
                    </div>
                  ),
                },
                {
                  header: "Role",
                  accessor: (user) => (
                    <Badge variant={roleColors[user.role] || "outline"}>
                      {user.role}
                    </Badge>
                  ),
                },
                {
                  header: "Status",
                  accessor: () => (
                    <span className="inline-flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-green-500"></span>
                      <span className="text-sm text-gray-600 dark:text-gray-400">
                        Active
                      </span>
                    </span>
                  ),
                  hideOnMobile: true,
                },
                {
                  header: "SSO",
                  accessor: () => (
                    <span className="text-sm text-gray-500">Google</span>
                  ),
                  hideOnMobile: true,
                },
                {
                  header: "Actions",
                  accessor: (user) => (
                    <select
                      className="text-sm border border-gray-300 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-800"
                      value={user.role}
                      onChange={(e) =>
                        updateRoleMutation.mutate({
                          userId: user.id,
                          role: e.target.value,
                        })
                      }
                      onClick={(e) => e.stopPropagation()}
                      disabled={updateRoleMutation.isPending}
                    >
                      <option value="student">Student</option>
                      <option value="teacher">Teacher</option>
                      <option value="creator">Creator</option>
                      <option value="admin">Admin</option>
                    </select>
                  ),
                },
              ]}
              mobileCardRenderer={(user) => (
                <div className="space-y-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-primary-100 dark:bg-primary-900 flex items-center justify-center">
                      <span className="text-primary-600 dark:text-primary-400 font-medium">
                        {user.displayName.charAt(0).toUpperCase()}
                      </span>
                    </div>
                    <div className="flex-1">
                      <p className="font-medium text-gray-900 dark:text-white">
                        {user.displayName}
                      </p>
                      <p className="text-sm text-gray-500">{user.email}</p>
                    </div>
                    <Badge variant={roleColors[user.role] || "outline"}>
                      {user.role}
                    </Badge>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="inline-flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-green-500"></span>
                      <span className="text-gray-600 dark:text-gray-400">
                        Active
                      </span>
                    </span>
                    <select
                      className="text-sm border border-gray-300 dark:border-gray-600 rounded px-2 py-1 bg-white dark:bg-gray-800"
                      value={user.role}
                      onChange={(e) =>
                        updateRoleMutation.mutate({
                          userId: user.id,
                          role: e.target.value,
                        })
                      }
                      onClick={(e) => e.stopPropagation()}
                      disabled={updateRoleMutation.isPending}
                    >
                      <option value="student">Student</option>
                      <option value="teacher">Teacher</option>
                      <option value="creator">Creator</option>
                      <option value="admin">Admin</option>
                    </select>
                  </div>
                </div>
              )}
            />
          )}

          {/* Pagination */}
          {data && data.totalCount > 0 && (
            <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-700 flex items-center justify-between">
              <p className="text-sm text-gray-500">
                Showing {data.items.length} of {data.totalCount} users
              </p>
              {data.hasMore && (
                <Button variant="outline" size="sm">
                  Load More
                </Button>
              )}
            </div>
          )}
        </Card>

        {/* Import Modal */}
        {showImportModal && (
          <ImportUsersModal onClose={() => setShowImportModal(false)} />
        )}
      </div>
    </PullToRefresh>
  );
}

function ImportUsersModal({ onClose }: { onClose: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [parsedUsers, setParsedUsers] = useState<
    Array<{ email: string; role: string; displayName: string }>
  >([]);
  const [parseError, setParseError] = useState<string>("");
  const queryClient = useQueryClient();

  const importMutation = useMutation({
    mutationFn: async (
      users: Array<{ email: string; role: string; displayName: string }>,
    ) => {
      const res = await fetch("/admin/api/v1/users/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ users }),
      });
      if (!res.ok) throw new Error("Import failed");
      return res.json();
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      onClose();
    },
  });

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (!selectedFile) return;

    setFile(selectedFile);
    setParseError("");
    setParsedUsers([]);

    try {
      const text = await selectedFile.text();
      const lines = text.split("\n").filter((line) => line.trim());

      if (lines.length < 2) {
        setParseError("File must have at least a header row and one data row");
        return;
      }

      // Parse CSV (simple implementation)
      const headers = lines[0].split(",").map((h) => h.trim().toLowerCase());
      const emailIdx = headers.indexOf("email");
      const nameIdx = headers.findIndex(
        (h) => h.includes("name") || h.includes("display"),
      );
      const roleIdx = headers.indexOf("role");

      if (emailIdx === -1) {
        setParseError('CSV must have an "email" column');
        return;
      }

      const users = lines.slice(1).map((line, idx) => {
        const values = line.split(",").map((v) => v.trim());
        const email = values[emailIdx];
        const displayName =
          nameIdx >= 0 ? values[nameIdx] : email.split("@")[0];
        const role =
          roleIdx >= 0 && values[roleIdx]
            ? values[roleIdx].toLowerCase()
            : "student";

        if (!email || !email.includes("@")) {
          throw new Error(`Invalid email at row ${idx + 2}: ${email}`);
        }

        return { email, displayName, role };
      });

      setParsedUsers(users);
    } catch (error) {
      setParseError(
        error instanceof Error ? error.message : "Failed to parse CSV",
      );
    }
  };

  const handleSubmit = () => {
    if (parsedUsers.length === 0) return;
    importMutation.mutate(parsedUsers);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/50" onClick={onClose}></div>
      <Card className="relative z-10 w-full max-w-md p-6">
        <h2 className="text-xl font-bold mb-4">Import Users</h2>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-2">CSV File</label>
            <input
              type="file"
              accept=".csv"
              onChange={handleFileChange}
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg"
            />
            <p className="mt-1 text-xs text-gray-500">
              CSV should have columns: email (required), displayName (optional),
              role (optional)
            </p>
          </div>

          {parseError && (
            <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
              <p className="text-sm text-red-600 dark:text-red-400">
                {parseError}
              </p>
            </div>
          )}

          {parsedUsers.length > 0 && (
            <div className="p-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg">
              <p className="text-sm text-green-600 dark:text-green-400 font-medium">
                ✓ Parsed {parsedUsers.length} users successfully
              </p>
              <div className="mt-2 max-h-32 overflow-y-auto text-xs text-gray-600 dark:text-gray-400">
                {parsedUsers.slice(0, 5).map((u, i) => (
                  <div key={i}>
                    {u.email} - {u.displayName} ({u.role})
                  </div>
                ))}
                {parsedUsers.length > 5 && (
                  <div>... and {parsedUsers.length - 5} more</div>
                )}
              </div>
            </div>
          )}

          {importMutation.error && (
            <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
              <p className="text-sm text-red-600 dark:text-red-400">
                Import failed. Please try again.
              </p>
            </div>
          )}
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={parsedUsers.length === 0 || importMutation.isPending}
          >
            {importMutation.isPending
              ? "Importing..."
              : `Import ${parsedUsers.length} Users`}
          </Button>
        </div>
      </Card>
    </div>
  );
}
