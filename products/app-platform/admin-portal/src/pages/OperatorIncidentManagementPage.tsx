import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { atom, useAtom } from 'jotai';

// ── Types ─────────────────────────────────────────────────────────────────────

type Severity = 'P1' | 'P2' | 'P3' | 'P4';
type IncidentStatus = 'INVESTIGATING' | 'IDENTIFIED' | 'MONITORING' | 'RESOLVED';

interface Incident {
  incidentId: string;
  title: string;
  severity: Severity;
  status: IncidentStatus;
  affectedTenants: string[];
  detectedAt: string;
  updatedAt: string;
  resolvedAt?: string;
  timeline: TimelineEntry[];
  communicationLog: CommunicationEntry[];
}

interface TimelineEntry {
  entryId: string;
  timestamp: string;
  authorId: string;
  authorName: string;
  status: IncidentStatus;
  note: string;
}

interface CommunicationEntry {
  commId: string;
  sentAt: string;
  channel: 'EMAIL' | 'SLACK' | 'WEBHOOK';
  audience: 'INTERNAL' | 'EXTERNAL';
  subject: string;
}

// ── Atoms ─────────────────────────────────────────────────────────────────────

const selectedIncidentAtom = atom<string | null>(null);
const statusFilterAtom = atom<IncidentStatus | 'ALL'>('ALL');
const severityFilterAtom = atom<Severity | 'ALL'>('ALL');

// ── API fetchers / mutators ───────────────────────────────────────────────────

const fetchIncidents = async (): Promise<Incident[]> => {
  const res = await fetch('/api/operator/incidents');
  if (!res.ok) throw new Error('Failed to fetch incidents');
  return res.json();
};

const postStatusUpdate = async ({
  incidentId, status, note,
}: { incidentId: string; status: IncidentStatus; note: string }) => {
  const res = await fetch(`/api/operator/incidents/${incidentId}/updates`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status, note }),
  });
  if (!res.ok) throw new Error('Failed to post update');
  return res.json();
};

const postPIRRequest = async (incidentId: string) => {
  const res = await fetch(`/api/operator/incidents/${incidentId}/pir`, { method: 'POST' });
  if (!res.ok) throw new Error('Failed to initiate PIR');
  return res.json();
};

// ── Sub-components ────────────────────────────────────────────────────────────

function SeverityBadge({ severity }: { severity: Severity }) {
  const styles: Record<Severity, string> = {
    P1: 'bg-red-600 text-white',
    P2: 'bg-orange-500 text-white',
    P3: 'bg-yellow-400 text-gray-900',
    P4: 'bg-gray-200 text-gray-700',
  };
  return (
    <span className={`text-xs font-bold px-2 py-0.5 rounded ${styles[severity]}`}>
      {severity}
    </span>
  );
}

function StatusBadge({ status }: { status: IncidentStatus }) {
  const styles: Record<IncidentStatus, string> = {
    INVESTIGATING: 'bg-red-100 text-red-700',
    IDENTIFIED:    'bg-orange-100 text-orange-700',
    MONITORING:    'bg-yellow-100 text-yellow-700',
    RESOLVED:      'bg-green-100 text-green-700',
  };
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${styles[status]}`}>
      {status}
    </span>
  );
}

function IncidentDetailDrawer({
  incidentId,
  onClose,
}: { incidentId: string; onClose: () => void }) {
  const qc = useQueryClient();
  const { data: incidents } = useQuery<Incident[]>({
    queryKey: ['incidents'],
    queryFn: fetchIncidents,
  });
  const incident = incidents?.find(i => i.incidentId === incidentId);

  const [note, setNote] = useState('');
  const [nextStatus, setNextStatus] = useState<IncidentStatus>('IDENTIFIED');

  const updateMutation = useMutation({
    mutationFn: postStatusUpdate,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['incidents'] }); setNote(''); },
  });
  const pirMutation = useMutation({
    mutationFn: () => postPIRRequest(incidentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['incidents'] }),
  });

  if (!incident) return null;

  const STATUSES: IncidentStatus[] = ['INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED'];

  return (
    <div className="fixed inset-y-0 right-0 w-[480px] bg-white shadow-xl border-l border-gray-200 z-30 flex flex-col">
      {/* Header */}
      <div className="flex items-start justify-between p-4 border-b border-gray-200">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
          <h3 className="font-semibold text-gray-900 leading-snug">{incident.title}</h3>
          <p className="text-xs text-gray-400 mt-0.5">
            Detected {new Date(incident.detectedAt).toLocaleString()}
          </p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 mt-0.5">
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-6">
        {/* Affected tenants */}
        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase mb-2">Affected Tenants</p>
          <div className="flex flex-wrap gap-1.5">
            {incident.affectedTenants.map(tid => (
              <span key={tid} className="text-xs bg-gray-100 text-gray-700 px-2 py-0.5 rounded">{tid}</span>
            ))}
          </div>
        </div>

        {/* Timeline */}
        <div>
          <p className="text-xs font-semibold text-gray-500 uppercase mb-2">Timeline</p>
          <div className="space-y-3">
            {incident.timeline.map((entry) => (
              <div key={entry.entryId} className="flex gap-3">
                <div className="flex-shrink-0 mt-0.5">
                  <div className="w-2 h-2 rounded-full bg-blue-400 mt-1.5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <StatusBadge status={entry.status} />
                    <span className="text-xs text-gray-400">
                      {new Date(entry.timestamp).toLocaleString()} · {entry.authorName}
                    </span>
                  </div>
                  <p className="text-sm text-gray-700 mt-0.5">{entry.note}</p>
                </div>
              </div>
            ))}
            {incident.timeline.length === 0 && (
              <p className="text-sm text-gray-400">No timeline entries yet</p>
            )}
          </div>
        </div>

        {/* Communication log */}
        {incident.communicationLog.length > 0 && (
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase mb-2">Communication Log</p>
            <div className="space-y-1.5">
              {incident.communicationLog.map(c => (
                <div key={c.commId} className="flex items-center justify-between text-xs text-gray-600 bg-gray-50 rounded px-3 py-2">
                  <span className="font-medium">{c.subject}</span>
                  <div className="flex items-center gap-2 text-gray-400">
                    <span>{c.channel}</span>
                    <span>·</span>
                    <span>{c.audience}</span>
                    <span>·</span>
                    <span>{new Date(c.sentAt).toLocaleTimeString()}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Post update form */}
      {incident.status !== 'RESOLVED' && (
        <div className="border-t border-gray-200 p-4 space-y-3 bg-gray-50">
          <p className="text-xs font-semibold text-gray-500 uppercase">Post Update</p>
          <select
            value={nextStatus}
            onChange={e => setNextStatus(e.target.value as IncidentStatus)}
            className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {STATUSES.filter(s => s !== incident.status).map(s => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="Describe what was found / done…"
            rows={3}
            className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
          <div className="flex gap-2">
            <button
              onClick={() => updateMutation.mutate({ incidentId, status: nextStatus, note })}
              disabled={!note.trim() || updateMutation.isPending}
              className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white text-sm font-medium py-2 rounded-lg"
            >
              {updateMutation.isPending ? 'Posting…' : 'Post Update'}
            </button>
            {incident.status === 'RESOLVED' || incident.resolvedAt ? null : (
              <button
                onClick={() => pirMutation.mutate()}
                disabled={pirMutation.isPending}
                className="text-sm text-gray-500 hover:text-gray-700 px-3 py-2 border border-gray-200 rounded-lg"
              >
                Open PIR
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function OperatorIncidentManagementPage() {
  const [selectedIncident, setSelectedIncident] = useAtom(selectedIncidentAtom);
  const [statusFilter, setStatusFilter] = useAtom(statusFilterAtom);
  const [severityFilter, setSeverityFilter] = useAtom(severityFilterAtom);

  const incidentsQuery = useQuery<Incident[]>({
    queryKey: ['incidents'],
    queryFn: fetchIncidents,
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
  });

  const incidents = incidentsQuery.data ?? [];

  const filtered = incidents.filter(i =>
    (statusFilter === 'ALL' || i.status === statusFilter) &&
    (severityFilter === 'ALL' || i.severity === severityFilter)
  );

  const counts = {
    P1: incidents.filter(i => i.severity === 'P1' && i.status !== 'RESOLVED').length,
    P2: incidents.filter(i => i.severity === 'P2' && i.status !== 'RESOLVED').length,
    open: incidents.filter(i => i.status !== 'RESOLVED').length,
  };

  const STATUSES: Array<IncidentStatus | 'ALL'> = ['ALL', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED'];
  const SEVERITIES: Array<Severity | 'ALL'> = ['ALL', 'P1', 'P2', 'P3', 'P4'];

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-semibold text-gray-900">Incident Management</h1>
            <p className="text-sm text-gray-500">Operator incident center · P1/P2 auto-notify tenants</p>
          </div>
          <div className="flex items-center gap-3">
            {counts.P1 > 0 && (
              <span className="bg-red-600 text-white text-xs font-bold px-2.5 py-1 rounded-full">
                {counts.P1} P1
              </span>
            )}
            {counts.P2 > 0 && (
              <span className="bg-orange-500 text-white text-xs font-bold px-2.5 py-1 rounded-full">
                {counts.P2} P2
              </span>
            )}
            <span className="text-sm text-gray-500">{counts.open} open</span>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white border-b border-gray-100 px-6 py-3 flex items-center gap-4">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">Status:</span>
          {STATUSES.map(s => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`text-xs px-2.5 py-1 rounded-full border ${
                statusFilter === s
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'border-gray-200 text-gray-600 hover:border-gray-300'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium text-gray-500">Severity:</span>
          {SEVERITIES.map(s => (
            <button
              key={s}
              onClick={() => setSeverityFilter(s)}
              className={`text-xs px-2.5 py-1 rounded-full border ${
                severityFilter === s
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'border-gray-200 text-gray-600 hover:border-gray-300'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Incidents list */}
      <div className="px-6 py-6 max-w-5xl mx-auto">
        <div className="bg-white border border-gray-200 rounded-lg divide-y divide-gray-50 overflow-hidden">
          {filtered.map(incident => (
            <div
              key={incident.incidentId}
              onClick={() => setSelectedIncident(incident.incidentId)}
              className="flex items-center gap-4 px-5 py-4 hover:bg-blue-50 cursor-pointer transition-colors"
            >
              <SeverityBadge severity={incident.severity} />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">{incident.title}</p>
                <p className="text-xs text-gray-400 mt-0.5">
                  {incident.affectedTenants.length} tenant{incident.affectedTenants.length !== 1 ? 's' : ''} ·
                  Detected {new Date(incident.detectedAt).toLocaleString()}
                </p>
              </div>
              <StatusBadge status={incident.status} />
              <div className="flex items-center gap-2 text-xs text-gray-400">
                <span>{incident.timeline.length} updates</span>
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </div>
          ))}
          {filtered.length === 0 && (
            <div className="py-12 text-center text-gray-400">
              <svg className="w-8 h-8 mx-auto mb-2 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              No incidents match the current filters
            </div>
          )}
        </div>
      </div>

      {/* Incident detail right-drawer */}
      {selectedIncident && (
        <IncidentDetailDrawer
          incidentId={selectedIncident}
          onClose={() => setSelectedIncident(null)}
        />
      )}
    </div>
  );
}
