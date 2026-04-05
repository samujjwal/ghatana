import React, { useState } from 'react';

// ============================================================================
// Types
// ============================================================================

type Role = 'admin' | 'developer' | 'viewer';
type InviteStatus = 'pending' | 'accepted' | 'expired';

interface TeamMember {
  id: string;
  email: string;
  name: string;
  role: Role;
  status: InviteStatus;
  invitedAt: string;
}

// ============================================================================
// Mock data
// ============================================================================

const INITIAL_MEMBERS: TeamMember[] = [
  { id: '1', email: 'alice@ghatana.ai', name: 'Alice Chen', role: 'admin', status: 'accepted', invitedAt: '2026-03-01' },
  { id: '2', email: 'bob@ghatana.ai', name: 'Bob Smith', role: 'developer', status: 'accepted', invitedAt: '2026-03-02' },
  { id: '3', email: 'carol@ghatana.ai', name: 'Carol Davis', role: 'developer', status: 'pending', invitedAt: '2026-03-08' },
  { id: '4', email: 'dan@ghatana.ai', name: 'Dan Wilson', role: 'viewer', status: 'pending', invitedAt: '2026-03-09' },
];

const ROLE_BADGES: Record<Role, string> = {
  admin: 'bg-purple-100 text-purple-700',
  developer: 'bg-blue-100 text-blue-700',
  viewer: 'bg-gray-100 text-gray-700',
};

const STATUS_BADGES: Record<InviteStatus, { label: string; className: string }> = {
  accepted: { label: 'Active', className: 'bg-green-100 text-green-700' },
  pending: { label: 'Pending', className: 'bg-yellow-100 text-yellow-700' },
  expired: { label: 'Expired', className: 'bg-red-100 text-red-700' },
};

// ============================================================================
// Component
// ============================================================================

const TeamInvitePage: React.FC = () => {
  const [members, setMembers] = useState(INITIAL_MEMBERS);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Role>('developer');
  const [bulkMode, setBulkMode] = useState(false);
  const [bulkEmails, setBulkEmails] = useState('');

  const invite = (): void => {
    if (!email.trim()) return;
    const newMember: TeamMember = {
      id: String(Date.now()),
      email: email.trim(),
      name: email.split('@')[0],
      role,
      status: 'pending',
      invitedAt: new Date().toISOString().slice(0, 10),
    };
    setMembers((prev) => [...prev, newMember]);
    setEmail('');
  };

  const bulkInvite = (): void => {
    const emails = bulkEmails.split(/[\n,;]/).map((e) => e.trim()).filter(Boolean);
    const newMembers = emails.map((e, i) => ({
      id: String(Date.now() + i),
      email: e,
      name: e.split('@')[0],
      role,
      status: 'pending' as InviteStatus,
      invitedAt: new Date().toISOString().slice(0, 10),
    }));
    setMembers((prev) => [...prev, ...newMembers]);
    setBulkEmails('');
  };

  const remove = (id: string): void => {
    setMembers((prev) => prev.filter((m) => m.id !== id));
  };

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="mx-auto max-w-4xl">
        <h1 className="mb-2 text-3xl font-bold text-gray-900">Team Invitations</h1>
        <p className="mb-8 text-gray-600">Invite teammates to collaborate on your project.</p>

        {/* Invite Form */}
        <div className="mb-8 rounded-lg border bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">Send Invitations</h2>
            <button
              onClick={() => setBulkMode(!bulkMode)}
              className="text-sm text-blue-600 hover:underline"
            >
              {bulkMode ? 'Single invite' : 'Bulk invite'}
            </button>
          </div>

          {bulkMode ? (
            <div className="space-y-3">
              <textarea
                value={bulkEmails}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setBulkEmails(e.target.value)}
                rows={4}
                placeholder="Enter emails separated by commas or newlines..."
                className="w-full rounded-lg border px-4 py-2 text-sm focus:border-blue-500 focus:outline-none"
              />
              <div className="flex items-center gap-3">
                <select
                  value={role}
                  onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setRole(e.target.value as Role)}
                  className="rounded-lg border px-3 py-2 text-sm"
                >
                  <option value="developer">Developer</option>
                  <option value="admin">Admin</option>
                  <option value="viewer">Viewer</option>
                </select>
                <button onClick={bulkInvite} className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
                  Send Invites
                </button>
              </div>
            </div>
          ) : (
            <div className="flex gap-3">
              <input
                type="email"
                value={email}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEmail(e.target.value)}
                placeholder="teammate@company.com"
                onKeyDown={(e) => e.key === 'Enter' && invite()}
                className="flex-1 rounded-lg border px-4 py-2 text-sm focus:border-blue-500 focus:outline-none"
              />
              <select
                value={role}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setRole(e.target.value as Role)}
                className="rounded-lg border px-3 py-2 text-sm"
              >
                <option value="developer">Developer</option>
                <option value="admin">Admin</option>
                <option value="viewer">Viewer</option>
              </select>
              <button onClick={invite} className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700">
                Invite
              </button>
            </div>
          )}
        </div>

        {/* Team List */}
        <div className="rounded-lg border bg-white shadow-sm">
          <div className="border-b px-5 py-3">
            <h2 className="text-sm font-semibold text-gray-700">
              Team Members ({members.length})
            </h2>
          </div>
          <div className="divide-y">
            {members.map((m) => (
              <div key={m.id} className="flex items-center gap-4 px-5 py-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gray-200 text-sm font-bold text-gray-600">
                  {m.name.charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-gray-900">{m.name}</p>
                  <p className="text-xs text-gray-500">{m.email}</p>
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium capitalize ${ROLE_BADGES[m.role]}`}>
                  {m.role}
                </span>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGES[m.status].className}`}>
                  {STATUS_BADGES[m.status].label}
                </span>
                <button
                  onClick={() => remove(m.id)}
                  className="text-gray-400 hover:text-red-500"
                  title="Remove member"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Summary */}
        <div className="mt-6 flex items-center justify-between text-sm text-gray-500">
          <span>
            {members.filter((m) => m.status === 'accepted').length} active ·{' '}
            {members.filter((m) => m.status === 'pending').length} pending
          </span>
          <button className="rounded-md bg-blue-600 px-6 py-2 text-sm font-medium text-white hover:bg-blue-700">
            Continue to Setup →
          </button>
        </div>
      </div>
    </div>
  );
};

export default TeamInvitePage;
