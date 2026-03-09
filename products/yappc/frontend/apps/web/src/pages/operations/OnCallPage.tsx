import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

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
  const res = await fetch('/api/oncall', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load on-call schedule');
  return res.json();
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
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load on-call schedule: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-100">On-Call Schedule</h1>
          <p className="text-sm text-zinc-400 mt-1">Manage on-call rotations and escalation policies</p>
        </div>
        <button className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors">
          Edit Schedule
        </button>
      </div>

      {/* Current On-Call */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Primary */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <h2 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">Primary On-Call</h2>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-emerald-600/20 flex items-center justify-center text-lg font-bold text-emerald-400">
              {data.current.primary.name.charAt(0)}
            </div>
            <div>
              <p className="text-sm font-semibold text-zinc-100">{data.current.primary.name}</p>
              <p className="text-xs text-zinc-500">{data.current.primary.email}</p>
              <p className="text-xs text-zinc-600 mt-1">{data.current.primary.phone}</p>
            </div>
          </div>
          <div className="mt-4 pt-3 border-t border-zinc-800 flex justify-between">
            <span className="text-xs text-zinc-500">Shift: {data.current.shiftStart}</span>
            <span className="text-xs text-zinc-500">Until: {data.current.shiftEnd}</span>
          </div>
        </div>

        {/* Secondary */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
          <div className="flex items-center gap-2 mb-4">
            <span className="w-2 h-2 rounded-full bg-amber-400" />
            <h2 className="text-xs font-semibold text-zinc-400 uppercase tracking-wider">Secondary On-Call</h2>
          </div>
          <div className="flex items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-amber-600/20 flex items-center justify-center text-lg font-bold text-amber-400">
              {data.current.secondary.name.charAt(0)}
            </div>
            <div>
              <p className="text-sm font-semibold text-zinc-100">{data.current.secondary.name}</p>
              <p className="text-xs text-zinc-500">{data.current.secondary.email}</p>
              <p className="text-xs text-zinc-600 mt-1">{data.current.secondary.phone}</p>
            </div>
          </div>
          <div className="mt-4 pt-3 border-t border-zinc-800 flex justify-between">
            <span className="text-xs text-zinc-500">Backup responder</span>
            <span className="text-xs text-zinc-500">Escalation: 15 min</span>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-zinc-800">
        {(['schedule', 'escalation'] as TabType[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2.5 text-sm font-medium capitalize transition-colors border-b-2 ${
              activeTab === tab
                ? 'text-blue-400 border-blue-400'
                : 'text-zinc-500 border-transparent hover:text-zinc-300'
            }`}
          >
            {tab === 'schedule' ? 'Rotation Schedule' : 'Escalation Policies'}
          </button>
        ))}
      </div>

      {/* Schedule Table */}
      {activeTab === 'schedule' && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl overflow-hidden">
          <table className="w-full">
            <thead className="bg-zinc-800/50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-400 uppercase tracking-wider">Person</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-400 uppercase tracking-wider">Role</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-400 uppercase tracking-wider">Start</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-400 uppercase tracking-wider">End</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-zinc-400 uppercase tracking-wider">Contact</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-800">
              {data.upcoming.map((entry) => (
                <tr key={entry.id} className="hover:bg-zinc-800/30 transition-colors">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <div className="w-7 h-7 rounded-full bg-zinc-800 flex items-center justify-center text-xs text-zinc-400">
                        {entry.person.name.charAt(0)}
                      </div>
                      <span className="text-sm font-medium text-zinc-200">{entry.person.name}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${
                        entry.type === 'primary'
                          ? 'bg-emerald-900/30 text-emerald-400'
                          : 'bg-amber-900/30 text-amber-400'
                      }`}
                    >
                      {entry.type}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-zinc-400">{entry.startDate}</td>
                  <td className="px-4 py-3 text-sm text-zinc-400">{entry.endDate}</td>
                  <td className="px-4 py-3 text-sm text-zinc-500">{entry.person.email}</td>
                </tr>
              ))}
              {data.upcoming.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-4 py-12 text-center text-zinc-500">
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
            <div key={level.level} className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-3">
                  <span
                    className={`w-8 h-8 rounded-lg flex items-center justify-center text-sm font-bold ${
                      level.level === 1
                        ? 'bg-red-600/20 text-red-400'
                        : level.level === 2
                          ? 'bg-amber-600/20 text-amber-400'
                          : 'bg-blue-600/20 text-blue-400'
                    }`}
                  >
                    L{level.level}
                  </span>
                  <div>
                    <h3 className="text-sm font-semibold text-zinc-200">{level.name}</h3>
                    <p className="text-xs text-zinc-500">Timeout: {level.timeout}</p>
                  </div>
                </div>
                <div className="flex gap-2">
                  {level.notificationChannels.map((ch) => (
                    <span
                      key={ch}
                      className="px-2 py-0.5 bg-zinc-800 text-zinc-400 text-xs rounded"
                    >
                      {ch}
                    </span>
                  ))}
                </div>
              </div>
              <div className="flex flex-wrap gap-3 pt-3 border-t border-zinc-800">
                {level.contacts.map((contact) => (
                  <div key={contact.id} className="flex items-center gap-2 bg-zinc-800/60 rounded-lg px-3 py-2">
                    <div className="w-6 h-6 rounded-full bg-zinc-700 flex items-center justify-center text-[10px] text-zinc-300">
                      {contact.name.charAt(0)}
                    </div>
                    <div>
                      <p className="text-xs font-medium text-zinc-300">{contact.name}</p>
                      <p className="text-[10px] text-zinc-500">{contact.phone}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}

          {data.escalationPolicies.length === 0 && (
            <div className="py-12 text-center text-zinc-500">
              No escalation policies configured
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default OnCallPage;
