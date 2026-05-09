import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { yappcApi } from '@/lib/api/client';
import { Button } from '../../components/ui/Button';

// ============================================================================
// Types
// ============================================================================

interface OnCallPerson {
  id: string;
  name: string;
  email: string;
  phone: string;
  avatarUrl?: string;
}

interface RotationEntry {
  id: string;
  person: OnCallPerson;
  startDate: string;
  endDate: string;
  type: 'primary' | 'secondary';
}

interface EscalationLevel {
  level: number;
  name: string;
  timeout: string;
  contacts: OnCallPerson[];
  notificationChannels: string[];
}

interface OnCallSchedule {
  current: {
    primary: OnCallPerson;
    secondary: OnCallPerson;
    shiftStart: string;
    shiftEnd: string;
  };
  upcoming: RotationEntry[];
  escalationPolicies: EscalationLevel[];
}

type TabType = 'schedule' | 'escalation';

// ============================================================================
// API
// ============================================================================

async function fetchOnCallSchedule(): Promise<OnCallSchedule> {
  return yappcApi.operations.getOnCallSchedule<OnCallSchedule>();
}

// ============================================================================
// Component
// ============================================================================

/**
 * OnCallPage — On-call rotation schedule.
 *
 * @doc.type component
 * @doc.purpose Current on-call, rotation table, and escalation policies
 * @doc.layer product
 */
const OnCallPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabType>('schedule');

  const { data, isLoading, error } = useQuery<OnCallSchedule>({
    queryKey: ['oncall-schedule'],
    queryFn: fetchOnCallSchedule,
  });

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
          Failed to load on-call schedule: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-fg-muted">On-Call Schedule</h1>
          <p className="text-sm text-fg-muted mt-1">Manage on-call rotations and escalation policies</p>
        </div>
        <Button className="px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors">
          Edit Schedule
        </Button>
      </div>

      {/* Current On-Call */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Primary */}
        <div className="bg-surface border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <h2 className="text-xs font-semibold text-fg-muted uppercase tracking-wider">Primary On-Call</h2>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-emerald-600/20 flex items-center justify-center text-lg font-bold text-emerald-400">
              {data.current.primary.name.charAt(0)}
            </div>
            <div>
              <p className="text-sm font-semibold text-fg-muted">{data.current.primary.name}</p>
              <p className="text-xs text-fg-muted">{data.current.primary.email}</p>
              <p className="text-xs text-fg-muted mt-1">{data.current.primary.phone}</p>
            </div>
          </div>
          <div className="mt-4 pt-3 border-t border-border flex justify-between">
            <span className="text-xs text-fg-muted">Shift: {data.current.shiftStart}</span>
            <span className="text-xs text-fg-muted">Until: {data.current.shiftEnd}</span>
          </div>
        </div>

        {/* Secondary */}
        <div className="bg-surface border border-border rounded-xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full bg-warning-bg" />
            <h2 className="text-xs font-semibold text-fg-muted uppercase tracking-wider">Secondary On-Call</h2>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-warning-bg/20 flex items-center justify-center text-lg font-bold text-warning-color">
              {data.current.secondary.name.charAt(0)}
            </div>
            <div>
              <p className="text-sm font-semibold text-fg-muted">{data.current.secondary.name}</p>
              <p className="text-xs text-fg-muted">{data.current.secondary.email}</p>
              <p className="text-xs text-fg-muted mt-1">{data.current.secondary.phone}</p>
            </div>
          </div>
          <div className="mt-4 pt-3 border-t border-border flex justify-between">
            <span className="text-xs text-fg-muted">Backup responder</span>
            <span className="text-xs text-fg-muted">Escalation: 15 min</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border">
        {(['schedule', 'escalation'] as TabType[]).map((tab) => (
          <Button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2.5 text-sm font-medium capitalize transition-colors border-b-2 ${
              activeTab === tab
                ? 'text-info-color border-info-border'
                : 'text-fg-muted border-transparent hover:text-fg-muted'
            }`}
          >
            {tab === 'schedule' ? 'Rotation Schedule' : 'Escalation Policies'}
          </Button>
        ))}
      </div>

      {/* Schedule Table */}
      {activeTab === 'schedule' && (
        <div className="bg-surface border border-border rounded-xl overflow-hidden">
          <table className="w-full">
            <thead className="bg-surface/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Person</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Role</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Start</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">End</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Contact</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {data.upcoming.map((entry) => (
                <tr key={entry.id} className="hover:bg-surface/30 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-7 h-7 rounded-full bg-surface flex items-center justify-center text-xs text-fg-muted">
                        {entry.person.name.charAt(0)}
                      </div>
                      <span className="text-sm font-medium text-fg-muted">{entry.person.name}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${
                        entry.type === 'primary'
                          ? 'bg-emerald-900/30 text-emerald-400'
                          : 'bg-warning-bg/30 text-warning-color'
                      }`}
                    >
                      {entry.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-fg-muted">{entry.startDate}</td>
                  <td className="px-4 py-3 text-sm text-fg-muted">{entry.endDate}</td>
                  <td className="px-4 py-3 text-sm text-fg-muted">{entry.person.email}</td>
                </tr>
              ))}
              {data.upcoming.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-fg-muted">
                    No upcoming rotations scheduled
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Escalation Policies */}
      {activeTab === 'escalation' && (
        <div className="space-y-4">
          {data.escalationPolicies.map((level) => (
            <div key={level.level} className="bg-surface border border-border rounded-xl p-5">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-3">
                  <span
                    className={`w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold ${
                      level.level === 1
                        ? 'bg-destructive-bg/20 text-destructive'
                        : level.level === 2
                          ? 'bg-warning-bg/20 text-warning-color'
                          : 'bg-primary/20 text-info-color'
                    }`}
                  >
                    L{level.level}
                  </span>
                  <div>
                    <h3 className="text-sm font-semibold text-fg-muted">{level.name}</h3>
                    <p className="text-xs text-fg-muted">Timeout: {level.timeout}</p>
                  </div>
                </div>
                <div className="flex gap-2">
                  {level.notificationChannels.map((ch) => (
                    <span
                      key={ch}
                      className="px-2 py-0.5 bg-surface text-fg-muted text-xs rounded"
                    >
                      {ch}
                    </span>
                  ))}
                </div>
              </div>
              <div className="flex flex-wrap gap-3 pt-3 border-t border-border">
                {level.contacts.map((contact) => (
                  <div key={contact.id} className="flex items-center gap-2 bg-surface/60 rounded-lg px-3 py-2">
                    <div className="w-6 h-6 rounded-full bg-surface-muted flex items-center justify-center text-[10px] text-fg-muted">
                      {contact.name.charAt(0)}
                    </div>
                    <div>
                      <p className="text-xs font-medium text-fg-muted">{contact.name}</p>
                      <p className="text-[10px] text-fg-muted">{contact.phone}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}

          {data.escalationPolicies.length === 0 && (
            <div className="py-12 text-center text-fg-muted">
              No escalation policies configured
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default OnCallPage;
