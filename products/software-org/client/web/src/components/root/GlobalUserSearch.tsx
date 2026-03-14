/**
 * Global User Search Component
 *
 * Search and manage users across all tenants.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    Box,
    Card,
    Typography,
    TextField,
    Button,
    Chip,
    Stack,
    Select,
    MenuItem,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    FormControl,
    InputLabel,
    Checkbox,
    FormControlLabel,
} from '@ghatana/design-system';
import { rootApi, type RootUserSearchResult } from '@/services/api';
import { useRoles, useCreateRoleAssignment, type RoleResponse } from '@/hooks';

type GlobalUser = RootUserSearchResult & { status?: string };

export const GlobalUserSearch: React.FC = () => {
    const [query, setQuery] = useState('');
    const [tenantFilter, setTenantFilter] = useState<string>('all');
    const [roleDialogOpen, setRoleDialogOpen] = useState(false);
    const [selectedUser, setSelectedUser] = useState<GlobalUser | null>(null);
    const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
    const queryClient = useQueryClient();

    const { data: users = [], isLoading } = useQuery<GlobalUser[]>({
        queryKey: ['/api/v1/root/users/search', { q: query }],
        queryFn: async () => {
            if (query.length < 2) return [];
            return rootApi.searchUsers(query);
        },
        enabled: query.length >= 2,
    });

    const { data: tenants = [] } = useQuery<Array<{ id: string; key: string; name: string }>>({
        queryKey: ['/api/v1/root/tenants'],
        queryFn: async () => {
            const res = await fetch('/api/v1/root/tenants');
            if (!res.ok) throw new Error('Failed to fetch tenants');
            return res.json();
        },
    });

    const { data: rolesData } = useRoles({ tenantId: tenantFilter !== 'all' ? tenantFilter : undefined });
    const roles = rolesData?.data ?? [];

    const suspendMutation = useMutation({
        mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
            return rootApi.suspendUser(id, reason);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['/api/v1/root/users/search'] });
        },
    });

    const roleAssignmentMutation = useCreateRoleAssignment();

    const handleSuspend = (id: string) => {
        if (confirm('Are you sure you want to suspend this user globally?')) {
            suspendMutation.mutate({ id, reason: 'Admin action' });
        }
    };

    const handleOpenRoleDialog = (user: GlobalUser) => {
        setSelectedUser(user);
        setSelectedRoles([]);
        setRoleDialogOpen(true);
    };

    const handleAssignRoles = async () => {
        if (!selectedUser || selectedRoles.length === 0) return;

        try {
            for (const roleId of selectedRoles) {
                await roleAssignmentMutation.mutateAsync({
                    userId: selectedUser.id,
                    roleId,
                    tenantId: tenantFilter !== 'all' ? tenantFilter : tenants[0]?.id ?? '',
                });
            }
            setRoleDialogOpen(false);
            alert(`Successfully assigned ${selectedRoles.length} role(s) to ${selectedUser.name}`);
        } catch (error) {
            console.error('Failed to assign roles:', error);
            alert('Failed to assign roles. Please try again.');
        }
    };

    const handleToggleRole = (roleId: string) => {
        setSelectedRoles((prev) =>
            prev.includes(roleId) ? prev.filter((id) => id !== roleId) : [...prev, roleId]
        );
    };

    const filteredUsers = users.filter((user) => {
        if (tenantFilter === 'all') return true;
        return user.workspaces.some((ws) => {
            const tenant = tenants.find((t) => t.name === ws.name);
            return tenant?.id === tenantFilter;
        });
    });

    return (
        <Box>
            <Box className="mb-6 space-y-4">
                <Box className="flex gap-4">
                    <TextField
                        placeholder="Search users by name or email..."
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        className="flex-1 max-w-xl"
                        helperText="Enter at least 2 characters to search"
                    />
                    <FormControl className="min-w-[200px]">
                        <InputLabel>Tenant Filter</InputLabel>
                        <Select
                            value={tenantFilter}
                            onChange={(e) => setTenantFilter(e.target.value as string)}
                            label="Tenant Filter"
                        >
                            <MenuItem value="all">All Tenants</MenuItem>
                            {tenants.map((tenant) => (
                                <MenuItem key={tenant.id} value={tenant.id}>
                                    {tenant.name} ({tenant.key})
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                </Box>
                {tenantFilter !== 'all' && (
                    <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                        Viewing users in: {tenants.find((t) => t.id === tenantFilter)?.name}
                    </Typography>
                )}
            </Box>

            <Stack spacing={2}>
                {filteredUsers.map((user) => (
                    <Card key={user.id} className="p-4">
                        <Box className="flex justify-between items-center">
                            <Box>
                                <Typography variant="h6" className="font-medium text-slate-900 dark:text-neutral-100">
                                    {user.name}
                                </Typography>
                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                    {user.email}
                                </Typography>
                                <Box className="mt-2 flex gap-2">
                                    {user.workspaces.map((ws, idx) => (
                                        <Chip key={idx} label={ws.name} size="small" variant="outlined" />
                                    ))}
                                </Box>
                            </Box>
                            <Box className="flex items-center gap-3">
                                {user.status === 'suspended' ? (
                                    <Chip label="SUSPENDED" color="error" />
                                ) : (
                                    <>
                                        <Button
                                            variant="outlined"
                                            color="primary"
                                            size="small"
                                            onClick={() => handleOpenRoleDialog(user)}
                                        >
                                            Assign Roles
                                        </Button>
                                        <Button
                                            variant="outlined"
                                            color="error"
                                            size="small"
                                            onClick={() => handleSuspend(user.id)}
                                        >
                                            Suspend User
                                        </Button>
                                    </>
                                )}
                                <Button variant="outlined" size="small">
                                    View Activity
                                </Button>
                            </Box>
                        </Box>
                    </Card>
                ))}
                {query.length >= 2 && filteredUsers.length === 0 && !isLoading && (
                    <Typography variant="body1" className="text-center text-slate-500 py-8">
                        No users found matching "{query}" {tenantFilter !== 'all' && 'in this tenant'}
                    </Typography>
                )}
            </Stack>

            {/* Role Assignment Dialog */}
            <Dialog open={roleDialogOpen} onClose={() => setRoleDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>
                    Assign Roles to {selectedUser?.name}
                </DialogTitle>
                <DialogContent>
                    <Typography variant="body2" className="mb-4 text-slate-600 dark:text-neutral-400">
                        Select roles to assign to this user.
                        {tenantFilter !== 'all' && (
                            <> Roles will be assigned for tenant: {tenants.find((t) => t.id === tenantFilter)?.name}</>
                        )}
                    </Typography>
                    <Box className="space-y-2">
                        {roles.map((role: RoleResponse) => (
                            <FormControlLabel
                                key={role.id}
                                control={
                                    <Checkbox
                                        checked={selectedRoles.includes(role.id)}
                                        onChange={() => handleToggleRole(role.id)}
                                    />
                                }
                                label={
                                    <Box>
                                        <Typography variant="subtitle2">{role.name}</Typography>
                                        {role.description && (
                                            <Typography variant="caption" className="text-slate-500">
                                                {role.description}
                                            </Typography>
                                        )}
                                    </Box>
                                }
                            />
                        ))}
                        {roles.length === 0 && (
                            <Typography variant="body2" className="text-slate-500 py-4 text-center">
                                {tenantFilter === 'all'
                                    ? 'Select a tenant to view available roles'
                                    : 'No roles available for this tenant'}
                            </Typography>
                        )}
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setRoleDialogOpen(false)} variant="text">
                        Cancel
                    </Button>
                    <Button
                        onClick={handleAssignRoles}
                        variant="contained"
                        color="primary"
                        disabled={selectedRoles.length === 0 || roleAssignmentMutation.isPending}
                    >
                        {roleAssignmentMutation.isPending ? 'Assigning...' : 'Assign Roles'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
