/**
 * Audit Log Page
 *
 * Complete audit trail for all organizational changes.
 * Shows timeline view, diff comparison, and rollback capabilities.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Grid, Card, Button, Stack, TextField, Chip, Select } from '@ghatana/ui';
import type { AuditEntry } from '@/types/org.types';
import { usePersona } from '@/hooks/usePersona';
import { ForensicsDrillDown } from '@/components/audit/ForensicsDrillDown';
import { RelatedEventsPanel } from '@/components/audit/RelatedEventsPanel';

/**
 * Mock audit entries
 */
const mockAuditEntries: AuditEntry[] = [
  {
    id: 'audit-1',
    timestamp: new Date('2025-11-30T10:30:00'),
    action: 'org:restructure:approved',
    actor: 'owner-1',
    target: {
      type: 'department',
      id: 'dept-eng',
      name: 'Engineering Department',
    },
    changes: {
      name: { before: 'Engineering', after: 'Engineering Department' },
      headcount: { before: 145, after: 150 },
    },
    metadata: {
      approvalId: 'proposal-1',
      reason: 'Q2 reorganization',
    },
  },
  {
    id: 'audit-2',
    timestamp: new Date('2025-11-29T14:15:00'),
    action: 'team:created',
    actor: 'manager-1',
    target: {
      type: 'team',
      id: 'team-devops',
      name: 'DevOps Team',
    },
    changes: {
      name: { before: null, after: 'DevOps Team' },
      manager: { before: null, after: 'manager-2' },
    },
  },
  {
    id: 'audit-3',
    timestamp: new Date('2025-11-28T09:20:00'),
    action: 'role:updated',
    actor: 'admin-1',
    target: {
      type: 'role',
      id: 'role-ic2',
      name: 'Software Engineer II',
    },
    changes: {
      permissions: {
        before: ['code:write', 'code:review'],
        after: ['code:write', 'code:review', 'design:contribute'],
      },
    },
  },
  {
    id: 'audit-4',
    timestamp: new Date('2025-11-27T16:45:00'),
    action: 'person:moved',
    actor: 'manager-1',
    target: {
      type: 'person',
      id: 'person-123',
      name: 'John Doe',
    },
    changes: {
      team: { before: 'team-frontend', after: 'team-backend' },
      role: { before: 'role-ic1', after: 'role-ic2' },
    },
    metadata: {
      reason: 'Team rebalancing',
    },
  },
];

type FilterType = 'all' | 'restructure' | 'team' | 'role' | 'person';
type TimeRange = '24h' | '7d' | '30d' | 'all';

export function AuditPage() {
  const { isOwner, isAdmin } = usePersona();
  const [searchQuery, setSearchQuery] = useState('');
  const [filterType, setFilterType] = useState<FilterType>('all');
  const [timeRange, setTimeRange] = useState<TimeRange>('30d');
  const [selectedEntry, setSelectedEntry] = useState<AuditEntry | null>(null);
  const [forensicsMode, setForensicsMode] = useState(false);

  const canViewFull = isOwner || isAdmin;

  // Filter entries
  const filteredEntries = mockAuditEntries.filter((entry) => {
    // Text search
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      if (
        !entry.action.toLowerCase().includes(query) &&
        !entry.target.name.toLowerCase().includes(query)
      ) {
        return false;
      }
    }

    // Type filter
    if (filterType !== 'all') {
      if (!entry.action.includes(filterType)) {
        return false;
      }
    }

    // Time range filter
    const now = new Date();
    const entryTime = new Date(entry.timestamp);
    const diffHours = (now.getTime() - entryTime.getTime()) / (1000 * 60 * 60);

    if (timeRange === '24h' && diffHours > 24) return false;
    if (timeRange === '7d' && diffHours > 24 * 7) return false;
    if (timeRange === '30d' && diffHours > 24 * 30) return false;

    return true;
  });

  const getActionColor = (action: string) => {
    if (action.includes('created')) return 'success';
    if (action.includes('deleted')) return 'error';
    if (action.includes('updated') || action.includes('moved')) return 'warning';
    if (action.includes('approved')) return 'primary';
    return 'default';
  };

  const getActionIcon = (action: string) => {
    if (action.includes('created')) return '➕';
    if (action.includes('deleted')) return '🗑️';
    if (action.includes('updated')) return '✏️';
    if (action.includes('moved')) return '↔️';
    if (action.includes('approved')) return '✅';
    return '📝';
  };

  const formatTimestamp = (timestamp: Date) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMinutes = Math.floor(
      (now.getTime() - date.getTime()) / (1000 * 60)
    );

    if (diffMinutes < 60) return `${diffMinutes}m ago`;
    if (diffMinutes < 60 * 24) return `${Math.floor(diffMinutes / 60)}h ago`;
    return date.toLocaleDateString();
  };

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Audit Log</h1>
          <p className="text-slate-600 dark:text-neutral-400 mt-1">
            Complete history of organizational changes
          </p>
        </div>

        {canViewFull && (
          <Stack direction="row" spacing={2}>
            <Button variant="outline" size="md">
              Export CSV
            </Button>
            <Button variant="outline" size="md">
              Generate Report
            </Button>
          </Stack>
        )}
      </div>

      {/* Stats */}
      <Grid columns={4} gap={4}>
        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Total Changes</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
              {mockAuditEntries.length}
            </p>
            <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Last 30 days</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Restructures</p>
            <p className="text-2xl font-bold text-blue-600">5</p>
            <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">This month</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Team Changes</p>
            <p className="text-2xl font-bold text-green-600">12</p>
            <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">This month</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">People Moved</p>
            <p className="text-2xl font-bold text-orange-600">8</p>
            <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">This month</p>
          </Box>
        </Card>
      </Grid>

      {/* Filters */}
      <Card>
        <Box className="p-4">
          <Grid columns={4} gap={4}>
            <TextField
              placeholder="Search by action or name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              fullWidth
            />

            <Select
              label="Change Type"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as FilterType)}
              options={[
                { value: 'all', label: 'All Changes' },
                { value: 'restructure', label: 'Restructures' },
                { value: 'team', label: 'Team Changes' },
                { value: 'role', label: 'Role Changes' },
                { value: 'person', label: 'People Changes' },
              ]}
            />

            <Select
              label="Time Range"
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value as TimeRange)}
              options={[
                { value: '24h', label: 'Last 24 hours' },
                { value: '7d', label: 'Last 7 days' },
                { value: '30d', label: 'Last 30 days' },
                { value: 'all', label: 'All time' },
              ]}
            />

            <Button variant="outline" size="md" fullWidth>
              Clear Filters
            </Button>
          </Grid>
        </Box>
      </Card>

      {/* Audit Entries */}
      <Grid columns={3} gap={4}>
        {/* Timeline */}
        <div className="col-span-2">
          <Card>
            <Box className="p-4">
              <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                Change Timeline ({filteredEntries.length})
              </h3>

              {filteredEntries.length === 0 ? (
                <div className="py-12 text-center text-slate-500 dark:text-neutral-400">
                  <div className="text-4xl mb-4">🔍</div>
                  <p>No audit entries found</p>
                </div>
              ) : (
                <Stack spacing={3}>
                  {filteredEntries.map((entry) => (
                    <div
                      key={entry.id}
                      className={`
                        p-4 border rounded-lg cursor-pointer transition-all
                        ${selectedEntry?.id === entry.id
                          ? 'border-blue-500 bg-blue-50'
                          : 'border-slate-200 hover:border-slate-300 hover:shadow-sm'
                        }
                      `}
                      onClick={() => setSelectedEntry(entry)}
                    >
                      <div className="flex items-start gap-3">
                        {/* Icon */}
                        <div className="text-2xl">
                          {getActionIcon(entry.action)}
                        </div>

                        {/* Content */}
                        <div className="flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <span className="font-medium text-slate-900 dark:text-neutral-100">
                              {entry.action.replace(/:/g, ' ')}
                            </span>
                            <Chip
                              label={entry.target.type}
                              size="small"
                              variant="outlined"
                            />
                          </div>

                          <p className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                            {entry.target.name}
                          </p>

                          {/* Changes summary */}
                          <div className="text-xs text-slate-500 dark:text-neutral-400">
                            {Object.keys(entry.changes).length} field(s) changed
                          </div>
                        </div>

                        {/* Timestamp */}
                        <div className="text-right">
                          <p className="text-xs text-slate-500 dark:text-neutral-400">
                            {formatTimestamp(entry.timestamp)}
                          </p>
                        </div>
                      </div>
                    </div>
                  ))}
                </Stack>
              )}
            </Box>
          </Card>
        </div>

        {/* Detail Panel */}
        <div>
          {selectedEntry ? (
            forensicsMode ? (
              <ForensicsDrillDown
                entry={selectedEntry}
                onClose={() => setForensicsMode(false)}
                onRevert={(id) => {
                  alert(`Revert change: ${id}`);
                  setForensicsMode(false);
                }}
                onLockUser={(userId) => {
                  alert(`Lock user: ${userId}`);
                }}
                onEscalate={(id) => {
                  alert(`Escalate event: ${id}`);
                }}
                onMarkReviewed={(id) => {
                  alert(`Marked as reviewed: ${id}`);
                  setForensicsMode(false);
                }}
              />
            ) : (
              <Stack spacing={3}>
                <Card>
                  <Box className="p-4 space-y-4">
                    <div>
                      <div className="flex items-center gap-2 mb-2">
                        <span className="text-2xl">
                          {getActionIcon(selectedEntry.action)}
                        </span>
                        <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                          {selectedEntry.action.replace(/:/g, ' ')}
                        </h3>
                      </div>
                      <Chip
                        label={selectedEntry.target.type}
                        color={getActionColor(selectedEntry.action)}
                        size="small"
                      />
                    </div>

                    <div>
                      <h4 className="text-sm font-semibold text-slate-700 mb-1">
                        Target
                      </h4>
                      <p className="text-sm text-slate-900 dark:text-neutral-100">
                        {selectedEntry.target.name}
                      </p>
                      <p className="text-xs text-slate-500 dark:text-neutral-400">
                        ID: {selectedEntry.target.id}
                      </p>
                    </div>

                    <div>
                      <h4 className="text-sm font-semibold text-slate-700 mb-1">
                        Timestamp
                      </h4>
                      <p className="text-sm text-slate-900 dark:text-neutral-100">
                        {new Date(selectedEntry.timestamp).toLocaleString()}
                      </p>
                    </div>

                    <div>
                      <h4 className="text-sm font-semibold text-slate-700 mb-2">
                        Changes ({Object.keys(selectedEntry.changes).length})
                      </h4>
                      <Stack spacing={2}>
                        {Object.entries(selectedEntry.changes).map(
                          ([field, change]) => (
                            <div
                              key={field}
                              className="p-3 bg-slate-50 rounded border border-slate-200 dark:border-neutral-600"
                            >
                              <p className="text-xs font-semibold text-slate-700 mb-2">
                                {field}
                              </p>
                              <div className="space-y-1">
                                <div className="flex items-center gap-2">
                                  <span className="text-xs text-red-600">−</span>
                                  <span className="text-xs text-slate-600 dark:text-neutral-400">
                                    {JSON.stringify(change.before)}
                                  </span>
                                </div>
                                <div className="flex items-center gap-2">
                                  <span className="text-xs text-green-600">+</span>
                                  <span className="text-xs text-slate-900 font-medium">
                                    {JSON.stringify(change.after)}
                                  </span>
                                </div>
                              </div>
                            </div>
                          )
                        )}
                      </Stack>
                    </div>

                    {selectedEntry.metadata && (
                      <div>
                        <h4 className="text-sm font-semibold text-slate-700 mb-2">
                          Metadata
                        </h4>
                        <div className="p-3 bg-slate-50 rounded text-xs font-mono">
                          {JSON.stringify(selectedEntry.metadata, null, 2)}
                        </div>
                      </div>
                    )}

                    {canViewFull && (
                      <Stack spacing={2} className="pt-4 border-t">
                        <Button
                          variant="primary"
                          size="sm"
                          fullWidth
                          onClick={() => setForensicsMode(true)}
                        >
                          🔍 Deep Forensics Analysis
                        </Button>
                        <Button variant="outline" size="sm" fullWidth>
                          View Full Diff
                        </Button>
                        <Button variant="outline" size="sm" fullWidth>
                          Compare Versions
                        </Button>
                        {selectedEntry.action.includes('restructure') && (
                          <Button
                            variant="outline"
                            size="sm"
                            fullWidth
                            className="text-orange-600 border-orange-600"
                          >
                            Rollback Changes
                          </Button>
                        )}
                      </Stack>
                    )}
                  </Box>
                </Card>

                {/* Related Events Panel */}
                <RelatedEventsPanel
                  currentEntry={selectedEntry}
                  allEntries={mockAuditEntries}
                  onEventClick={(entry) => {
                    setSelectedEntry(entry);
                    setForensicsMode(false);
                  }}
                />
              </Stack>
            )
          ) : (
            <Card>
              <Box className="p-8 text-center text-slate-500 dark:text-neutral-400">
                <div className="text-4xl mb-4">📋</div>
                <p>Select an entry to view details</p>
              </Box>
            </Card>
          )}
        </div>
      </Grid>
    </Box>
  );
}

