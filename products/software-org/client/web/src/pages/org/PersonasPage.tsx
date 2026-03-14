/**
 * Persona Management Page
 *
 * Complete page for managing personas and roles in the organization.
 * Allows viewing, creating, editing, and deleting personas.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Grid, Button, Card, Stack, TextField, Chip } from '@ghatana/design-system';
import type { Role } from '@/types/org.types';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock roles data
 * TODO: Replace with API calls
 */
const mockRoles: Role[] = [
  {
    id: 'role-ic1',
    name: 'Software Engineer I',
    description: 'Entry-level software engineer',
    level: 'IC1',
    responsibilities: [
      'Write clean, maintainable code',
      'Participate in code reviews',
      'Work on assigned tasks',
    ],
    requiredSkills: ['Programming', 'Problem solving', 'Communication'],
    permissions: ['code:write', 'code:review:request'],
  },
  {
    id: 'role-ic2',
    name: 'Software Engineer II',
    description: 'Mid-level software engineer',
    level: 'IC2',
    responsibilities: [
      'Design and implement features',
      'Mentor junior engineers',
      'Lead technical discussions',
    ],
    requiredSkills: ['System design', 'Mentoring', 'Technical leadership'],
    permissions: ['code:write', 'code:review', 'design:contribute'],
  },
  {
    id: 'role-m1',
    name: 'Engineering Manager',
    description: 'Manages engineering team',
    level: 'M1',
    responsibilities: [
      'Manage team performance',
      'Conduct 1:1s and reviews',
      'Plan sprints and deliverables',
    ],
    requiredSkills: ['People management', 'Planning', 'Communication'],
    permissions: [
      'team:manage',
      'team:restructure',
      'approvals:leave',
      'performance:review',
    ],
  },
  {
    id: 'role-director',
    name: 'Director of Engineering',
    description: 'Oversees multiple engineering teams',
    level: 'M2',
    responsibilities: [
      'Set technical strategy',
      'Manage multiple teams',
      'Budget planning',
    ],
    requiredSkills: ['Strategic planning', 'Cross-team coordination', 'Budget management'],
    permissions: [
      'department:manage',
      'department:restructure',
      'budget:plan',
      'hiring:approve',
    ],
  },
];

export interface PersonasPageProps {
  /** Optional initial filter */
  initialFilter?: string;
}

/**
 * Personas Page Component
 *
 * Main page for persona and role management.
 *
 * @example
 * ```tsx
 * <OwnerLayout>
 *   <PersonasPage />
 * </OwnerLayout>
 * ```
 */
export function PersonasPage({ initialFilter }: PersonasPageProps = {}) {
  const { isOwner, isAdmin } = usePersona();
  const [searchQuery, setSearchQuery] = useState(initialFilter || '');
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);
  const [isCreating, setIsCreating] = useState(false);

  // Filter roles based on search
  const filteredRoles = mockRoles.filter(
    (role) =>
      role.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      role.level.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const canManage = isOwner || isAdmin;

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Personas & Roles</h1>
          <p className="text-slate-600 dark:text-neutral-400 mt-1">
            Manage role definitions and permissions
          </p>
        </div>

        {canManage && (
          <Button
            variant="primary"
            size="md"
            onClick={() => setIsCreating(true)}
          >
            Create New Role
          </Button>
        )}
      </div>

      {/* Stats Overview */}
      <Grid columns={4} gap={4}>
        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Total Roles</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{mockRoles.length}</p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">IC Roles</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
              {mockRoles.filter((r) => r.level.startsWith('IC')).length}
            </p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">Management Roles</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
              {mockRoles.filter((r) => r.level.startsWith('M')).length}
            </p>
          </Box>
        </Card>

        <Card>
          <Box className="p-4">
            <p className="text-sm text-slate-600 dark:text-neutral-400">People Assigned</p>
            <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">320</p>
          </Box>
        </Card>
      </Grid>

      {/* Search */}
      <Card>
        <Box className="p-4">
          <TextField
            placeholder="Search roles by name or level..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            fullWidth
          />
        </Box>
      </Card>

      {/* Role List */}
      <Grid columns={3} gap={4}>
        {/* Roles Grid */}
        <div className="col-span-2 space-y-4">
          {filteredRoles.map((role) => (
            <Card
              key={role.id}
              className={`cursor-pointer transition-all ${
                selectedRole?.id === role.id
                  ? 'ring-2 ring-blue-500'
                  : 'hover:shadow-md'
              }`}
              onClick={() => setSelectedRole(role)}
            >
              <Box className="p-4">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        {role.name}
                      </h3>
                      <Chip label={role.level} size="small" variant="outlined" />
                    </div>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mb-3">
                      {role.description}
                    </p>

                    {/* Responsibilities */}
                    <div className="mb-3">
                      <p className="text-xs font-semibold text-slate-700 mb-1">
                        Key Responsibilities:
                      </p>
                      <ul className="text-sm text-slate-600 space-y-1">
                        {role.responsibilities.slice(0, 2).map((resp, idx) => (
                          <li key={idx}>• {resp}</li>
                        ))}
                        {role.responsibilities.length > 2 && (
                          <li className="text-blue-600">
                            +{role.responsibilities.length - 2} more
                          </li>
                        )}
                      </ul>
                    </div>

                    {/* Skills */}
                    <div className="flex flex-wrap gap-1">
                      {role.requiredSkills.slice(0, 3).map((skill, idx) => (
                        <Chip
                          key={idx}
                          label={skill}
                          size="small"
                          variant="outlined"
                        />
                      ))}
                      {role.requiredSkills.length > 3 && (
                        <Chip
                          label={`+${role.requiredSkills.length - 3}`}
                          size="small"
                          variant="outlined"
                        />
                      )}
                    </div>
                  </div>

                  {/* Stats */}
                  <div className="text-right">
                    <p className="text-2xl font-bold text-slate-900 dark:text-neutral-100">42</p>
                    <p className="text-xs text-slate-500 dark:text-neutral-400">people</p>
                  </div>
                </div>
              </Box>
            </Card>
          ))}
        </div>

        {/* Detail Panel */}
        <div>
          {selectedRole ? (
            <Card>
              <Box className="p-4 space-y-4">
                <div>
                  <h3 className="text-lg font-bold text-slate-900 dark:text-neutral-100 mb-1">
                    {selectedRole.name}
                  </h3>
                  <Chip
                    label={selectedRole.level}
                    size="small"
                    variant="filled"
                  />
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Description
                  </h4>
                  <p className="text-sm text-slate-600 dark:text-neutral-400">
                    {selectedRole.description}
                  </p>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Responsibilities ({selectedRole.responsibilities.length})
                  </h4>
                  <ul className="text-sm text-slate-600 space-y-1">
                    {selectedRole.responsibilities.map((resp, idx) => (
                      <li key={idx}>• {resp}</li>
                    ))}
                  </ul>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Required Skills
                  </h4>
                  <div className="flex flex-wrap gap-1">
                    {selectedRole.requiredSkills.map((skill, idx) => (
                      <Chip
                        key={idx}
                        label={skill}
                        size="small"
                        variant="outlined"
                      />
                    ))}
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-semibold text-slate-700 mb-2">
                    Permissions ({selectedRole.permissions.length})
                  </h4>
                  <Stack spacing={1}>
                    {selectedRole.permissions.map((perm, idx) => (
                      <div
                        key={idx}
                        className="text-xs font-mono bg-slate-50 p-2 rounded"
                      >
                        {perm}
                      </div>
                    ))}
                  </Stack>
                </div>

                {canManage && (
                  <Stack spacing={2} className="pt-4 border-t">
                    <Button variant="primary" size="sm" fullWidth>
                      Edit Role
                    </Button>
                    <Button variant="outline" size="sm" fullWidth>
                      Duplicate Role
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      fullWidth
                      className="text-red-600 border-red-600"
                    >
                      Delete Role
                    </Button>
                  </Stack>
                )}
              </Box>
            </Card>
          ) : (
            <Card>
              <Box className="p-8 text-center text-slate-500 dark:text-neutral-400">
                <div className="text-4xl mb-4">👤</div>
                <p>Select a role to view details</p>
              </Box>
            </Card>
          )}
        </div>
      </Grid>
    </Box>
  );
}
