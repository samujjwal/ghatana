import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

interface TeamMember {
  id: string;
  name: string;
  avatar?: string;
}

interface Team {
  id: string;
  name: string;
  description: string;
  memberCount: number;
  members: TeamMember[];
  lead: string;
  status: 'active' | 'archived';
  createdAt: string;
}

type StatusFilter = 'all' | 'active' | 'archived';

// ============================================================================
// API
// ============================================================================

async function fetchTeams(search: string): Promise<Team[]> {
  const params = search ? `?q=${encodeURIComponent(search)}` : '';
  const res = await fetch(`/api/admin/teams${params}`, {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load teams');
  return res.json();
}

// ============================================================================
// Component
// ============================================================================

/**
 * TeamsPage — Team management.
 *
 * @doc.type component
 * @doc.purpose Team list with search, filter and grid layout
 * @doc.layer product
 */
const TeamsPage: React.FC = () => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');

  const { data: teams, isLoading, error } = useQuery<Team[]>({
    queryKey: ['admin-teams', search],
    queryFn: () => fetchTeams(search),
  });

  const filtered = teams?.filter((t) => {
    if (statusFilter !== 'all' && t.status !== statusFilter) return false;
    return true;
  });

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load teams: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-100">Teams</h1>
          <p className="text-sm text-zinc-400 mt-1">
            {filtered?.length ?? 0} team{filtered?.length !== 1 ? 's' : ''}
          </p>
        </div>
        <button className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors">
          Create Team
        </button>
      </div>

      {/* Search & Filter Bar */}
      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          placeholder="Search teams..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="flex-1 min-w-[200px] max-w-sm px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-sm placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
        />
        <div className="flex rounded-lg border border-zinc-800 overflow-hidden">
          {(['all', 'active', 'archived'] as StatusFilter[]).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-2 text-xs font-medium capitalize transition-colors ${
                statusFilter === s
                  ? 'bg-blue-600 text-white'
                  : 'bg-zinc-900 text-zinc-400 hover:text-zinc-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Team Grid */}
      {isLoading ? (
        <div className="flex items-center justify-center py-16">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered?.map((team) => (
            <div
              key={team.id}
              className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 hover:border-zinc-700 transition-colors cursor-pointer"
            >
              {/* Card Header */}
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-blue-600/20 text-blue-400 flex items-center justify-center text-sm font-bold">
                    {team.name.charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-zinc-100">{team.name}</h3>
                    <p className="text-xs text-zinc-500">Led by {team.lead}</p>
                  </div>
                </div>
                <span
                  className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                    team.status === 'active'
                      ? 'bg-emerald-900/30 text-emerald-400'
                      : 'bg-zinc-800 text-zinc-500'
                  }`}
                >
                  {team.status}
                </span>
              </div>

              {/* Description */}
              <p className="text-sm text-zinc-400 line-clamp-2 mb-4">{team.description}</p>

              {/* Footer */}
              <div className="flex items-center justify-between pt-3 border-t border-zinc-800">
                <div className="flex items-center gap-1">
                  {/* Member avatars */}
                  <div className="flex -space-x-2">
                    {team.members.slice(0, 4).map((m) => (
                      <div
                        key={m.id}
                        className="w-6 h-6 rounded-full bg-zinc-700 border-2 border-zinc-900 flex items-center justify-center text-[10px] text-zinc-300"
                        title={m.name}
                      >
                        {m.name.charAt(0)}
                      </div>
                    ))}
                    {team.memberCount > 4 && (
                      <div className="w-6 h-6 rounded-full bg-zinc-800 border-2 border-zinc-900 flex items-center justify-center text-[10px] text-zinc-400">
                        +{team.memberCount - 4}
                      </div>
                    )}
                  </div>
                </div>
                <span className="text-xs text-zinc-500">
                  {team.memberCount} member{team.memberCount !== 1 ? 's' : ''}
                </span>
              </div>
            </div>
          ))}

          {filtered?.length === 0 && (
            <div className="col-span-full py-16 text-center text-zinc-500">
              No teams found
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default TeamsPage;
