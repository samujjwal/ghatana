/**
 * Team Members Management Component
 *
 * Component for managing team members - adding, removing,
 * reassigning, and viewing member details.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Box, Card, Button, Stack, DataGrid, Chip, Modal, TextField, Select } from '@ghatana/design-system';
import type { Person } from '@/types/org.types';

interface TeamMembersManagementProps {
  teamId: string;
  members: Person[];
  onAddMember?: (member: Person) => void;
  onRemoveMember?: (memberId: string) => void;
  onUpdateMember?: (memberId: string, updates: Partial<Person>) => void;
}

export function TeamMembersManagement({
  teamId,
  members,
  onAddMember,
  onRemoveMember,
  onUpdateMember,
}: TeamMembersManagementProps) {
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [selectedMember, setSelectedMember] = useState<Person | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const filteredMembers = members.filter((member) =>
    member.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    member.email.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const columns = [
    {
      field: 'name',
      headerName: 'Name',
      width: 200,
      renderCell: (params: any) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-neutral-100">{params.row.name}</p>
          <p className="text-xs text-slate-500 dark:text-neutral-400">{params.row.email}</p>
        </div>
      ),
    },
    {
      field: 'title',
      headerName: 'Title',
      width: 180,
    },
    {
      field: 'team',
      headerName: 'Team',
      width: 150,
    },
    {
      field: 'manager',
      headerName: 'Manager',
      width: 150,
    },
    {
      field: 'actions',
      headerName: 'Actions',
      width: 200,
      renderCell: (params: any) => (
        <Stack direction="row" spacing={1}>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setSelectedMember(params.row)}
          >
            Edit
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => onRemoveMember?.(params.row.id)}
            className="text-red-600"
          >
            Remove
          </Button>
        </Stack>
      ),
    },
  ];

  return (
    <Box>
      {/* Header with Search and Add */}
      <Card>
        <Box className="p-4 border-b">
          <div className="flex items-center justify-between gap-4">
            <TextField
              placeholder="Search members..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="flex-1"
            />
            <Button
              variant="primary"
              size="md"
              onClick={() => setIsAddModalOpen(true)}
            >
              Add Member
            </Button>
          </div>
        </Box>

        {/* Members Table */}
        <Box className="p-4">
          <DataGrid
            rows={filteredMembers}
            columns={columns}
            pageSize={10}
          />
        </Box>
      </Card>

      {/* Add Member Modal */}
      <Modal
        open={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        title="Add Team Member"
      >
        <Box className="p-6 space-y-4">
          <TextField
            label="Search Employee"
            placeholder="Start typing name or email..."
            fullWidth
          />

          <Select
            label="Role"
            options={[
              { value: 'ic1', label: 'Engineer I' },
              { value: 'ic2', label: 'Engineer II' },
              { value: 'senior', label: 'Senior Engineer' },
            ]}
            fullWidth
          />

          <TextField
            label="Allocation"
            type="number"
            placeholder="100"
            helperText="Percentage (0-100)"
            fullWidth
          />

          <Stack direction="row" spacing={2} className="pt-4">
            <Button
              variant="outline"
              fullWidth
              onClick={() => setIsAddModalOpen(false)}
            >
              Cancel
            </Button>
            <Button variant="primary" fullWidth>
              Add Member
            </Button>
          </Stack>
        </Box>
      </Modal>

      {/* Edit Member Modal */}
      <Modal
        open={!!selectedMember}
        onClose={() => setSelectedMember(null)}
        title="Edit Member"
      >
        {selectedMember && (
          <Box className="p-6 space-y-4">
            <TextField
              label="Name"
              value={selectedMember.name}
              disabled
              fullWidth
            />

            <TextField
              label="Title"
              value={selectedMember.title || ''}
              fullWidth
            />

            <Select
              label="Manager"
              value={selectedMember.manager || ''}
              options={[
                { value: 'manager-1', label: 'Sarah Manager' },
                { value: 'manager-2', label: 'John Lead' },
              ]}
              fullWidth
            />

            <Stack direction="row" spacing={2} className="pt-4">
              <Button
                variant="outline"
                fullWidth
                onClick={() => setSelectedMember(null)}
              >
                Cancel
              </Button>
              <Button variant="primary" fullWidth>
                Save Changes
              </Button>
            </Stack>
          </Box>
        )}
      </Modal>
    </Box>
  );
}
