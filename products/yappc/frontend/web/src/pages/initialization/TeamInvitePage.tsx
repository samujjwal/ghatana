import React, { useState } from 'react';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { Select } from '../../components/ui/Select';
import { Textarea } from '../../components/ui/Textarea';
import { useTranslation } from '@ghatana/i18n';

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
  admin: 'bg-info-bg text-info-color',
  developer: 'bg-info-bg text-info-color',
  viewer: 'bg-surface-muted text-fg',
};

const STATUS_BADGES: Record<InviteStatus, { label: string; className: string }> = {
  accepted: { label: 'Active', className: 'bg-success-bg text-success-color' },
  pending: { label: 'Pending', className: 'bg-warning-bg text-warning-color' },
  expired: { label: 'Expired', className: 'bg-destructive-bg text-destructive' },
};

// ============================================================================
// Component
// ============================================================================

const TeamInvitePage: React.FC = () => {
  const { t } = useTranslation('common');
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
    <div className="min-h-screen bg-surface-muted p-6">
      <div className="mx-auto max-w-4xl">
        <h1 className="mb-2 text-3xl font-bold text-fg">Team Invitations</h1>
        <p className="mb-8 text-fg-muted">Invite teammates to collaborate on your project.</p>

        {/* Invite Form */}
        <div className="mb-8 rounded-lg border bg-white p-5 shadow-sm">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-fg">Send Invitations</h2>
            <Button
              variant="link"
              size="sm"
              onClick={() => setBulkMode(!bulkMode)}
              className="text-sm text-info-color hover:underline"
            >
              {bulkMode ? 'Single invite' : 'Bulk invite'}
            </Button>
          </div>

          {bulkMode ? (
            <div className="space-y-3">
              <Textarea
                value={bulkEmails}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setBulkEmails(e.target.value)}
                rows={4}
                placeholder={t('teamInvite.emailPlaceholder')}
                className="w-full rounded-lg border px-4 py-2 text-sm focus:border-info-border focus:outline-none"
              />
              <div className="flex items-center gap-3">
                <Select
                  value={role}
                  onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setRole(e.target.value as Role)}
                  className="rounded-lg border px-3 py-2 text-sm"
                >
                  <option value="developer">{t('teamInvite.role.developer')}</option>
                  <option value="admin">{t('teamInvite.role.admin')}</option>
                  <option value="viewer">{t('teamInvite.role.viewer')}</option>
                </Select>
                <Button onClick={bulkInvite} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg">
                  Send Invites
                </Button>
              </div>
            </div>
          ) : (
            <div className="flex gap-3">
              <Input
                type="email"
                value={email}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEmail(e.target.value)}
                placeholder={t('teamInvite.singleEmailPlaceholder')}
                onKeyDown={(e) => e.key === 'Enter' && invite()}
                className="flex-1 rounded-lg border px-4 py-2 text-sm focus:border-info-border focus:outline-none"
              />
              <Select
                value={role}
                onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setRole(e.target.value as Role)}
                className="rounded-lg border px-3 py-2 text-sm"
              >
                <option value="developer">{t('teamInvite.role.developer')}</option>
                <option value="admin">{t('teamInvite.role.admin')}</option>
                <option value="viewer">{t('teamInvite.role.viewer')}</option>
              </Select>
              <Button onClick={invite} className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg">
                Invite
              </Button>
            </div>
          )}
        </div>

        {/* Team List */}
        <div className="rounded-lg border bg-white shadow-sm">
          <div className="border-b px-5 py-3">
            <h2 className="text-sm font-semibold text-fg">
              Team Members ({members.length})
            </h2>
          </div>
          <div className="divide-y">
            {members.map((m) => (
              <div key={m.id} className="flex items-center gap-4 px-5 py-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-surface-muted text-sm font-bold text-fg-muted">
                  {m.name.charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-fg">{m.name}</p>
                  <p className="text-xs text-fg-muted">{m.email}</p>
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium capitalize ${ROLE_BADGES[m.role]}`}>
                  {m.role}
                </span>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_BADGES[m.status].className}`}>
                  {STATUS_BADGES[m.status].label}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => remove(m.id)}
                  className="text-fg-muted hover:text-destructive"
                  aria-label={t('teamInvite.removeMember', { name: m.name })}
                >
                  ✕
                </Button>
              </div>
            ))}
          </div>
        </div>

        {/* Summary */}
        <div className="mt-6 flex items-center justify-between text-sm text-fg-muted">
          <span>
            {members.filter((m) => m.status === 'accepted').length} active ·{' '}
            {members.filter((m) => m.status === 'pending').length} pending
          </span>
          <Button className="rounded-md bg-primary px-6 py-2 text-sm font-medium text-white hover:bg-info-bg">
            Continue to Setup →
          </Button>
        </div>
      </div>
    </div>
  );
};

export default TeamInvitePage;
