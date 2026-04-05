import React from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low';
type IncidentStatus = 'investigating' | 'identified' | 'monitoring' | 'resolved';

interface Responder {
  id: string;
  name: string;
  avatarUrl: string;
  role: string;
  joinedAt: string;
}

interface TimelineEvent {
  id: string;
  timestamp: string;
  actor: string;
  action: string;
  detail: string;
}

interface IncidentData {
  id: string;
  title: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  summary: string;
  startedAt: string;
  updatedAt: string;
  commander: string;
  responders: Responder[];
  timeline: TimelineEvent[];
  affectedServices: string[];
}

const authHeaders = (): Record<string, string> => ({
  Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}`,
  'Content-Type': 'application/json',
});

const SEVERITY_COLORS: Record<IncidentSeverity, string> = {
  critical: 'bg-red-600 text-white',
  high: 'bg-orange-600 text-white',
  medium: 'bg-yellow-600 text-black',
  low: 'bg-blue-600 text-white',
};

const STATUS_COLORS: Record<IncidentStatus, string> = {
  investigating: 'bg-red-500/20 text-red-400 border-red-500/30',
  identified: 'bg-orange-500/20 text-orange-400 border-orange-500/30',
  monitoring: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  resolved: 'bg-green-500/20 text-green-400 border-green-500/30',
};

/**
 * WarRoomPage — Incident war room with live timeline and responder management.
 *
 * @doc.type component
 * @doc.purpose Incident war room for coordinated incident response
 * @doc.layer product
 */
const WarRoomPage: React.FC = () => {
  const { incidentId } = useParams<{ incidentId: string }>();
  const queryClient = useQueryClient();

  const { data: incident, isLoading, error } = useQuery<IncidentData>({
    queryKey: ['warroom', incidentId],
    queryFn: async () => {
      const res = await fetch(`/api/warroom/${incidentId}`, { headers: authHeaders() });
      if (!res.ok) throw new Error('Failed to load incident');
      return res.json() as Promise<IncidentData>;
    },
    enabled: !!incidentId,
    refetchInterval: 10_000,
  });

  const escalateMutation = useMutation<void, Error>({
    mutationFn: async () => {
      const res = await fetch(`/api/warroom/${incidentId}/escalate`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Escalation failed');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['warroom', incidentId] });
    },
  });

  const resolveMutation = useMutation<void, Error>({
    mutationFn: async () => {
      const res = await fetch(`/api/warroom/${incidentId}/resolve`, {
        method: 'POST',
        headers: authHeaders(),
      });
      if (!res.ok) throw new Error('Resolution failed');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['warroom', incidentId] });
    },
  });

  const formatTimestamp = (ts: string) =>
    new Date(ts).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });

  const formatDuration = (start: string) => {
    const ms = Date.now() - new Date(start).getTime();
    const mins = Math.floor(ms / 60_000);
    if (mins < 60) return `${mins}m`;
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m`;
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load incident'}
        </div>
      </div>
    );
  }

  const isResolved = incident?.status === 'resolved';

  return (
    <div className="p-6 space-y-6">
      {/* Summary banner */}
      <div className={`rounded-lg border p-5 ${
        incident?.severity === 'critical'
          ? 'bg-red-950/40 border-red-800'
          : 'bg-zinc-900 border-zinc-800'
      }`}>
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <div className="flex items-center gap-3">
              <span className={`px-2 py-0.5 text-xs font-bold rounded uppercase ${SEVERITY_COLORS[incident?.severity ?? 'medium']}`}>
                {incident?.severity}
              </span>
              <span className={`px-2.5 py-0.5 text-xs font-medium rounded border ${STATUS_COLORS[incident?.status ?? 'investigating']}`}>
                {incident?.status}
              </span>
              {!isResolved && incident?.startedAt && (
                <span className="text-xs text-zinc-500">Duration: {formatDuration(incident.startedAt)}</span>
              )}
            </div>
            <h1 className="text-2xl font-bold text-zinc-100">{incident?.title}</h1>
            <p className="text-sm text-zinc-400 max-w-2xl">{incident?.summary}</p>
            {(incident?.affectedServices ?? []).length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-1">
                {incident?.affectedServices.map((svc) => (
                  <span key={svc} className="px-2 py-0.5 text-xs bg-zinc-800 text-zinc-400 rounded">{svc}</span>
                ))}
              </div>
            )}
          </div>
          <div className="flex gap-2 shrink-0">
            <button
              onClick={() => escalateMutation.mutate()}
              disabled={isResolved || escalateMutation.isPending}
              className="px-4 py-2 bg-orange-600 text-white text-sm font-medium rounded-lg hover:bg-orange-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              {escalateMutation.isPending ? 'Escalating…' : 'Escalate'}
            </button>
            <button
              onClick={() => resolveMutation.mutate()}
              disabled={isResolved || resolveMutation.isPending}
              className="px-4 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-500 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              {resolveMutation.isPending ? 'Resolving…' : 'Resolve'}
            </button>
          </div>
        </div>
        <div className="flex items-center gap-4 mt-3 text-xs text-zinc-500">
          <span>Commander: <span className="text-zinc-300">{incident?.commander}</span></span>
          <span>Started: {incident?.startedAt ? formatTimestamp(incident.startedAt) : '—'}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Live timeline */}
        <div className="lg:col-span-2">
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg">
            <div className="px-5 py-3 border-b border-zinc-800 flex items-center justify-between">
              <h2 className="text-sm font-semibold text-zinc-200">Live Timeline</h2>
              <span className="flex items-center gap-1.5 text-xs text-zinc-500">
                <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                Auto-refreshing
              </span>
            </div>
            <div className="p-5 max-h-[500px] overflow-y-auto">
              {(incident?.timeline ?? []).length === 0 ? (
                <p className="text-sm text-zinc-500 text-center py-8">No timeline events yet.</p>
              ) : (
                <div className="relative">
                  <div className="absolute left-3 top-2 bottom-2 w-px bg-zinc-800" />
                  <div className="space-y-4">
                    {incident?.timeline.map((evt) => (
                      <div key={evt.id} className="flex gap-4 relative">
                        <div className="w-6 h-6 rounded-full bg-zinc-800 border-2 border-zinc-700 shrink-0 flex items-center justify-center z-10">
                          <span className="w-2 h-2 rounded-full bg-blue-500" />
                        </div>
                        <div className="min-w-0 pb-1">
                          <div className="flex items-baseline gap-2 flex-wrap">
                            <span className="text-sm font-medium text-zinc-200">{evt.actor}</span>
                            <span className="text-xs text-zinc-500">{evt.action}</span>
                            <span className="text-xs text-zinc-600">{formatTimestamp(evt.timestamp)}</span>
                          </div>
                          {evt.detail && (
                            <p className="text-sm text-zinc-400 mt-0.5">{evt.detail}</p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Responders list */}
        <div>
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg">
            <div className="px-5 py-3 border-b border-zinc-800">
              <h2 className="text-sm font-semibold text-zinc-200">
                Responders ({incident?.responders.length ?? 0})
              </h2>
            </div>
            <div className="p-4 space-y-3">
              {(incident?.responders ?? []).length === 0 ? (
                <p className="text-sm text-zinc-500 text-center py-4">No responders yet.</p>
              ) : (
                incident?.responders.map((r) => (
                  <div key={r.id} className="flex items-center gap-3 p-2 rounded-lg hover:bg-zinc-800/50">
                    <img src={r.avatarUrl} alt={r.name} className="w-8 h-8 rounded-full bg-zinc-800" />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-zinc-200 truncate">{r.name}</p>
                      <p className="text-xs text-zinc-500">{r.role}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default WarRoomPage;
