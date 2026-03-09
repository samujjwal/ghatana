/**
 * Workspace Admin Dashboard Component
 *
 * Comprehensive workspace management with:
 * - Member list with role management
 * - Invite/remove members
 * - Persona assignment
 * - Audit log integration
 * - Activity monitoring
 *
 * @doc.type component
 * @doc.purpose Workspace administration and governance
 * @doc.layer product
 * @doc.pattern Dashboard Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, Typography, Card, CardContent, Tabs, Tab, Table, TableBody, TableCell, TableHead, TableRow, IconButton, Button, Chip, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Select, MenuItem, FormControl, InputLabel, Tooltip, Alert, Spinner as CircularProgress, Divider, Surface as Paper, Avatar, Badge } from '@ghatana/ui';
import { UserPlus as InviteIcon, Trash2 as DeleteIcon, Pencil as EditIcon, Shield as SecurityIcon, History as HistoryIcon, Users as PeopleIcon, Settings as SettingsIcon, RefreshCw as RefreshIcon, Settings as AdminIcon, CheckCircle as ActiveIcon, Ban as InactiveIcon } from 'lucide-react';
import { useAtomValue } from 'jotai';
import {
    currentMembershipAtom,
    hasPermissionAtom,
} from '@/stores/user.store';
import type {
    WorkspaceMember,
    PersonaType,
    Workspace,
    WorkspacePermission,
} from '@ghatana/yappc-auth/rbac';
import {
    WorkspaceRole,
    ROLE_PERMISSIONS,
    hasPermission,
    canManageRole,
} from '@ghatana/yappc-auth/rbac';
import { AuditTrail } from '../audit';

// ============================================================================
// Types
// ============================================================================

interface WorkspaceAdminDashboardProps {
    workspace: Workspace;
    members: WorkspaceMember[];
    isLoading?: boolean;
    error?: string;
    onInviteMember?: (email: string, role: WorkspaceRole, personas: PersonaType[]) => Promise<void>;
    onRemoveMember?: (userId: string) => Promise<void>;
    onUpdateRole?: (userId: string, role: WorkspaceRole) => Promise<void>;
    onUpdatePersonas?: (userId: string, personas: PersonaType[]) => Promise<void>;
    onRefresh?: () => void;
}

interface MemberRowProps {
    member: WorkspaceMember;
    currentUserRole: WorkspaceRole;
    onEdit: (member: WorkspaceMember) => void;
    onRemove: (userId: string) => void;
    canManage: boolean;
}

interface InviteDialogProps {
    open: boolean;
    onClose: () => void;
    onInvite: (email: string, role: WorkspaceRole, personas: PersonaType[]) => void;
    isLoading?: boolean;
}

interface EditMemberDialogProps {
    open: boolean;
    member: WorkspaceMember | null;
    currentUserRole: WorkspaceRole;
    onClose: () => void;
    onSave: (userId: string, role: WorkspaceRole, personas: PersonaType[]) => void;
    isLoading?: boolean;
}

type TabValue = 'members' | 'permissions' | 'audit' | 'settings';

// ============================================================================
// Constants
// ============================================================================

const ROLE_DISPLAY: Record<WorkspaceRole, { label: string; color: 'error' | 'warning' | 'primary' | 'default' }> = {
    [WorkspaceRole.OWNER]: { label: 'Owner', color: 'error' },
    [WorkspaceRole.ADMIN]: { label: 'Admin', color: 'warning' },
    [WorkspaceRole.MEMBER]: { label: 'Member', color: 'primary' },
    [WorkspaceRole.VIEWER]: { label: 'Viewer', color: 'default' },
};

/**
 * Persona category for grouping in UI
 */
type PersonaCategory = 'execution' | 'governance' | 'strategic' | 'operations' | 'administrative';

interface PersonaDisplayInfo {
    label: string;
    description: string;
    category: PersonaCategory;
    color: string;
}

const PERSONA_CATEGORY_INFO: Record<PersonaCategory, { name: string; color: string }> = {
    execution: { name: 'Execution', color: '#2196F3' },
    governance: { name: 'Governance', color: '#F44336' },
    strategic: { name: 'Strategic', color: '#9C27B0' },
    operations: { name: 'Operations', color: '#4CAF50' },
    administrative: { name: 'Admin', color: '#607D8B' },
};

const PERSONA_DISPLAY: Record<PersonaType, PersonaDisplayInfo> = {
    // Execution
    'developer': { label: 'Developer', description: 'Technical implementation', category: 'execution', color: '#2196F3' },
    'tech-lead': { label: 'Tech Lead', description: 'Technical leadership', category: 'execution', color: '#9C27B0' },
    'devops-engineer': { label: 'DevOps Engineer', description: 'Infrastructure & CI/CD', category: 'execution', color: '#4CAF50' },
    'qa-engineer': { label: 'QA Engineer', description: 'Testing & quality', category: 'execution', color: '#FF9800' },
    'sre': { label: 'SRE', description: 'Site reliability engineering', category: 'execution', color: '#00BCD4' },
    // Governance
    'security-engineer': { label: 'Security Engineer', description: 'Security & compliance', category: 'governance', color: '#F44336' },
    'compliance-officer': { label: 'Compliance Officer', description: 'Regulatory & audit', category: 'governance', color: '#795548' },
    'architect': { label: 'Architect', description: 'System architecture', category: 'governance', color: '#673AB7' },
    // Strategic
    'product-manager': { label: 'Product Manager', description: 'Product strategy', category: 'strategic', color: '#E91E63' },
    'product-owner': { label: 'Product Owner', description: 'Backlog & sprint', category: 'strategic', color: '#FF5722' },
    'program-manager': { label: 'Program Manager', description: 'Program coordination', category: 'strategic', color: '#3F51B5' },
    'business-analyst': { label: 'Business Analyst', description: 'Requirements analysis', category: 'strategic', color: '#009688' },
    'engineering-manager': { label: 'Engineering Manager', description: 'Team management', category: 'strategic', color: '#607D8B' },
    'executive': { label: 'Executive', description: 'Executive overview', category: 'strategic', color: '#1A237E' },
    // Operations
    'release-manager': { label: 'Release Manager', description: 'Release coordination', category: 'operations', color: '#8BC34A' },
    'infrastructure-architect': { label: 'Infra Architect', description: 'Infrastructure design', category: 'operations', color: '#00ACC1' },
    'customer-success': { label: 'Customer Success', description: 'Customer readiness', category: 'operations', color: '#FFC107' },
    'support-lead': { label: 'Support Lead', description: 'Support operations', category: 'operations', color: '#FF7043' },
    // Administrative
    'workspace-admin': { label: 'Workspace Admin', description: 'Workspace management', category: 'administrative', color: '#546E7A' },
    'stakeholder': { label: 'Stakeholder', description: 'Read-only viewer', category: 'administrative', color: '#78909C' },
};

/**
 * Get personas grouped by category for UI display
 */
function getPersonasByCategory(): Record<PersonaCategory, PersonaType[]> {
    const result: Record<PersonaCategory, PersonaType[]> = {
        execution: [],
        governance: [],
        strategic: [],
        operations: [],
        administrative: [],
    };
    (Object.keys(PERSONA_DISPLAY) as PersonaType[]).forEach((persona) => {
        result[PERSONA_DISPLAY[persona].category].push(persona);
    });
    return result;
}

// ============================================================================
// Sub-Components
// ============================================================================

/**
 * Individual member row in the members table.
 */
const MemberRow: React.FC<MemberRowProps> = ({
    member,
    currentUserRole,
    onEdit,
    onRemove,
    canManage,
}) => {
    const isActive = member.isActive;

    return (
        <TableRow hover>
            <TableCell>
                <Box className="flex items-center gap-4">
                    <Badge
                        overlap="circular"
                        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                        badgeContent={
                            isActive ? (
                                <ActiveIcon className="w-[12px] h-[12px] text-green-600" />
                            ) : (
                                <InactiveIcon className="w-[12px] h-[12px] text-gray-400 dark:text-gray-600" />
                            )
                        }
                    >
                        <Avatar className="w-[36px] h-[36px]">
                            {member.email.charAt(0).toUpperCase()}
                        </Avatar>
                    </Badge>
                    <Box>
                        <Typography as="p" className="text-sm" fontWeight="medium">
                            {member.userId}
                        </Typography>
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            {member.email}
                        </Typography>
                    </Box>
                </Box>
            </TableCell>
            <TableCell>
                <Chip
                    label={ROLE_DISPLAY[member.role].label}
                    color={ROLE_DISPLAY[member.role].color}
                    size="sm"
                />
            </TableCell>
            <TableCell>
                <Box className="flex gap-1 flex-wrap">
                    {member.personas.map((persona: PersonaType) => (
                        <Chip
                            key={persona}
                            label={PERSONA_DISPLAY[persona]?.label ?? persona}
                            size="sm"
                            variant="outlined"
                        />
                    ))}
                </Box>
            </TableCell>
            <TableCell>
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    {new Date(member.joinedAt).toLocaleDateString()}
                </Typography>
            </TableCell>
            <TableCell align="right">
                {canManage && canManageRole(currentUserRole, member.role) && (
                    <>
                        <Tooltip title="Edit member">
                            <IconButton size="sm" onClick={() => onEdit(member)}>
                                <EditIcon size={16} />
                            </IconButton>
                        </Tooltip>
                        {member.role !== 'owner' && (
                            <Tooltip title="Remove member">
                                <IconButton
                                    size="sm"
                                    tone="danger"
                                    onClick={() => onRemove(member.userId)}
                                >
                                    <DeleteIcon size={16} />
                                </IconButton>
                            </Tooltip>
                        )}
                    </>
                )}
            </TableCell>
        </TableRow>
    );
};

/**
 * Dialog for inviting new members.
 */
const InviteDialog: React.FC<InviteDialogProps> = ({
    open,
    onClose,
    onInvite,
    isLoading,
}) => {
    const [email, setEmail] = useState('');
    const [role, setRole] = useState<WorkspaceRole>(WorkspaceRole.MEMBER);
    const [personas, setPersonas] = useState<PersonaType[]>(['developer']);

    const personasByCategory = useMemo(() => getPersonasByCategory(), []);

    const handleSubmit = () => {
        if (email.trim()) {
            onInvite(email.trim(), role, personas);
            setEmail('');
            setRole(WorkspaceRole.MEMBER);
            setPersonas(['developer']);
        }
    };

    const handleClose = () => {
        setEmail('');
        setRole(WorkspaceRole.MEMBER);
        setPersonas(['developer']);
        onClose();
    };

    const togglePersona = (persona: PersonaType) => {
        setPersonas((prev) =>
            prev.includes(persona) ? prev.filter((p) => p !== persona) : [...prev, persona]
        );
    };

    return (
        <Dialog open={open} onClose={handleClose} size="md" fullWidth>
            <DialogTitle>
                <Box className="flex items-center gap-2">
                    <InviteIcon />
                    Invite Member
                </Box>
            </DialogTitle>
            <DialogContent>
                <Box className="flex flex-col gap-4 mt-2">
                    <TextField
                        label="Email Address"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        fullWidth
                        autoFocus
                        disabled={isLoading}
                    />
                    <FormControl fullWidth>
                        <InputLabel>Role</InputLabel>
                        <Select
                            value={role}
                            onChange={(e) => setRole(e.target.value as WorkspaceRole)}
                            label="Role"
                            disabled={isLoading}
                        >
                            <MenuItem value={WorkspaceRole.VIEWER}>Viewer - Read-only access</MenuItem>
                            <MenuItem value={WorkspaceRole.MEMBER}>Member - Standard access</MenuItem>
                            <MenuItem value={WorkspaceRole.ADMIN}>Admin - Full management</MenuItem>
                        </Select>
                    </FormControl>

                    {/* Personas grouped by category */}
                    <Box>
                        <Typography as="p" className="mt-2 text-sm font-medium" gutterBottom>
                            Assign Personas ({personas.length} selected)
                        </Typography>
                        {(Object.keys(personasByCategory) as PersonaCategory[]).map((category) => (
                            <Box key={category} className="mb-4">
                                <Typography
                                    as="span"
                                    className="text-xs font-semibold uppercase"
                                    style={{ color: PERSONA_CATEGORY_INFO[category].color }}
                                >
                                    {PERSONA_CATEGORY_INFO[category].name}
                                </Typography>
                                <Box className="flex flex-wrap gap-1 mt-1">
                                    {personasByCategory[category].map((persona) => (
                                        <Chip
                                            key={persona}
                                            label={PERSONA_DISPLAY[persona].label}
                                            onClick={() => togglePersona(persona)}
                                            variant={personas.includes(persona) ? 'filled' : 'outlined'}
                                            color={personas.includes(persona) ? 'primary' : 'default'}
                                            size="sm"
                                            style={{ borderColor: personas.includes(persona)
                                                    ? PERSONA_DISPLAY[persona].color
                                                    : undefined, backgroundColor: personas.includes(persona)
                                                    ? PERSONA_DISPLAY[persona].color
                                                    : undefined }}
                                        />
                                    ))}
                                </Box>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} disabled={isLoading}>
                    Cancel
                </Button>
                <Button
                    onClick={handleSubmit}
                    variant="solid"
                    disabled={!email.trim() || personas.length === 0 || isLoading}
                    startIcon={isLoading ? <CircularProgress size={16} /> : <InviteIcon />}
                >
                    Send Invite
                </Button>
            </DialogActions>
        </Dialog>
    );
};

/**
 * Dialog for editing member role and personas.
 */
const EditMemberDialog: React.FC<EditMemberDialogProps> = ({
    open,
    member,
    currentUserRole,
    onClose,
    onSave,
    isLoading,
}) => {
    const [role, setRole] = useState<WorkspaceRole>(member?.role ?? WorkspaceRole.MEMBER);
    const [personas, setPersonas] = useState<PersonaType[]>(member?.personas ?? ['developer']);

    const personasByCategory = useMemo(() => getPersonasByCategory(), []);

    React.useEffect(() => {
        if (member) {
            setRole(member.role);
            setPersonas(member.personas);
        }
    }, [member]);

    const togglePersona = (persona: PersonaType) => {
        setPersonas((prev) =>
            prev.includes(persona) ? prev.filter((p) => p !== persona) : [...prev, persona]
        );
    };

    const handleSubmit = () => {
        if (member) {
            onSave(member.userId, role, personas);
        }
    };

    const availableRoles = useMemo(() => {
        // Can only assign roles equal to or lower than current user's role
        const roleOrder: WorkspaceRole[] = [WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.MEMBER, WorkspaceRole.VIEWER];
        const currentIndex = roleOrder.indexOf(currentUserRole);
        return roleOrder.slice(currentIndex === 0 ? 1 : currentIndex); // Can't make others owner
    }, [currentUserRole]);

    if (!member) return null;

    return (
        <Dialog open={open} onClose={onClose} size="md" fullWidth>
            <DialogTitle>
                <Box className="flex items-center gap-2">
                    <EditIcon />
                    Edit Member: {member.email}
                </Box>
            </DialogTitle>
            <DialogContent>
                <Box className="flex flex-col gap-4 mt-2">
                    <FormControl fullWidth>
                        <InputLabel>Role</InputLabel>
                        <Select
                            value={role}
                            onChange={(e) => setRole(e.target.value as WorkspaceRole)}
                            label="Role"
                            disabled={isLoading || member.role === WorkspaceRole.OWNER}
                        >
                            {availableRoles.map((r) => (
                                <MenuItem key={r} value={r}>
                                    {ROLE_DISPLAY[r].label}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    {/* Personas grouped by category */}
                    <Box>
                        <Typography as="p" className="mt-2 text-sm font-medium" gutterBottom>
                            Assign Personas ({personas.length} selected)
                        </Typography>
                        {(Object.keys(personasByCategory) as PersonaCategory[]).map((category) => (
                            <Box key={category} className="mb-4">
                                <Typography
                                    as="span"
                                    className="text-xs font-semibold uppercase"
                                    style={{ color: PERSONA_CATEGORY_INFO[category].color }}
                                >
                                    {PERSONA_CATEGORY_INFO[category].name}
                                </Typography>
                                <Box className="flex flex-wrap gap-1 mt-1">
                                    {personasByCategory[category].map((persona) => (
                                        <Chip
                                            key={persona}
                                            label={PERSONA_DISPLAY[persona].label}
                                            onClick={() => togglePersona(persona)}
                                            variant={personas.includes(persona) ? 'filled' : 'outlined'}
                                            color={personas.includes(persona) ? 'primary' : 'default'}
                                            size="sm"
                                            disabled={isLoading}
                                            style={{ borderColor: personas.includes(persona)
                                                    ? PERSONA_DISPLAY[persona].color
                                                    : undefined, backgroundColor: personas.includes(persona)
                                                    ? PERSONA_DISPLAY[persona].color
                                                    : undefined }}
                                        />
                                    ))}
                                </Box>
                            </Box>
                        ))}
                    </Box>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={isLoading}>
                    Cancel
                </Button>
                <Button
                    onClick={handleSubmit}
                    variant="solid"
                    disabled={isLoading || personas.length === 0}
                    startIcon={isLoading ? <CircularProgress size={16} /> : null}
                >
                    Save Changes
                </Button>
            </DialogActions>
        </Dialog>
    );
};

/**
 * Permission matrix showing role permissions.
 */
const PermissionMatrix: React.FC = () => {
    const roles: WorkspaceRole[] = [WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.MEMBER, WorkspaceRole.VIEWER];

    // Group permissions by resource
    const permissionsByResource = useMemo(() => {
        const groups: Record<string, WorkspacePermission[]> = {};
        const allPermissions = ROLE_PERMISSIONS[WorkspaceRole.OWNER]; // Owner has all
        allPermissions.forEach((perm: WorkspacePermission) => {
            const [resource] = perm.split(':');
            if (!groups[resource]) groups[resource] = [];
            groups[resource].push(perm);
        });
        return groups;
    }, []);

    return (
        <Card>
            <CardContent>
                <Typography as="h6" gutterBottom>
                    Permission Matrix
                </Typography>
                <Table size="sm">
                    <TableHead>
                        <TableRow>
                            <TableCell>Permission</TableCell>
                            {roles.map((role) => (
                                <TableCell key={role} align="center">
                                    {ROLE_DISPLAY[role].label}
                                </TableCell>
                            ))}
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {Object.entries(permissionsByResource).map(([resource, permissions]) => (
                            <React.Fragment key={resource}>
                                <TableRow>
                                    <TableCell
                                        colSpan={5}
                                        className="font-bold capitalize bg-gray-100 dark:bg-gray-800"
                                    >
                                        {resource}
                                    </TableCell>
                                </TableRow>
                                {permissions.map((perm: WorkspacePermission) => (
                                    <TableRow key={perm}>
                                        <TableCell className="pl-8">
                                            <Typography as="p" className="text-sm">{perm.split(':')[1]}</Typography>
                                        </TableCell>
                                        {roles.map((role) => (
                                            <TableCell key={role} align="center">
                                                {hasPermission(role, perm) ? (
                                                    <ActiveIcon className="text-green-600 text-lg" />
                                                ) : (
                                                    <InactiveIcon className="text-gray-400 dark:text-gray-600 text-lg" />
                                                )}
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))}
                            </React.Fragment>
                        ))}
                    </TableBody>
                </Table>
            </CardContent>
        </Card>
    );
};

/**
 * Workspace settings panel.
 */
const WorkspaceSettings: React.FC<{ workspace: Workspace }> = ({ workspace }) => {
    return (
        <Card>
            <CardContent>
                <Typography as="h6" gutterBottom>
                    Workspace Settings
                </Typography>
                <Box className="flex flex-col gap-4">
                    <Box>
                        <Typography as="p" className="text-sm font-medium" color="text.secondary">
                            Workspace ID
                        </Typography>
                        <Typography as="p" className="text-sm">{workspace.id}</Typography>
                    </Box>
                    <Box>
                        <Typography as="p" className="text-sm font-medium" color="text.secondary">
                            Name
                        </Typography>
                        <Typography as="p" className="text-sm">{workspace.name}</Typography>
                    </Box>
                    {workspace.description && (
                        <Box>
                            <Typography as="p" className="text-sm font-medium" color="text.secondary">
                                Description
                            </Typography>
                            <Typography as="p" className="text-sm">{workspace.description}</Typography>
                        </Box>
                    )}
                    <Box>
                        <Typography as="p" className="text-sm font-medium" color="text.secondary">
                            Created
                        </Typography>
                        <Typography as="p" className="text-sm">
                            {new Date(workspace.createdAt).toLocaleString()}
                        </Typography>
                    </Box>
                    <Divider />
                    <Box>
                        <Typography as="p" className="text-sm font-medium" color="text.secondary" gutterBottom>
                            Settings
                        </Typography>
                        <Box className="flex flex-col gap-2">
                            <Typography as="p" className="text-sm">
                                AI Enabled: {workspace.settings?.aiEnabled ? 'Yes' : 'No'}
                            </Typography>
                            <Typography as="p" className="text-sm">
                                Audit Enabled: {workspace.settings?.auditEnabled ? 'Yes' : 'No'}
                            </Typography>
                            <Typography as="p" className="text-sm">
                                Require Approval for Requirements: {workspace.settings?.requireApprovalForRequirements ? 'Yes' : 'No'}
                            </Typography>
                        </Box>
                    </Box>
                </Box>
            </CardContent>
        </Card>
    );
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Main Workspace Admin Dashboard component.
 */
export const WorkspaceAdminDashboard: React.FC<WorkspaceAdminDashboardProps> = ({
    workspace,
    members,
    isLoading = false,
    error,
    onInviteMember,
    onRemoveMember,
    onUpdateRole,
    onUpdatePersonas,
    onRefresh,
}) => {
    const [activeTab, setActiveTab] = useState<TabValue>('members');
    const [inviteDialogOpen, setInviteDialogOpen] = useState(false);
    const [editMember, setEditMember] = useState<WorkspaceMember | null>(null);
    const [actionLoading, setActionLoading] = useState(false);

    const currentMembership = useAtomValue(currentMembershipAtom);
    const checkPermission = useAtomValue(hasPermissionAtom);

    const currentUserRole = currentMembership?.role ?? WorkspaceRole.VIEWER;
    const canManageMembers = checkPermission('member:invite') || checkPermission('member:update-role');

    const handleInvite = useCallback(
        async (email: string, role: WorkspaceRole, personas: PersonaType[]) => {
            if (!onInviteMember) return;
            setActionLoading(true);
            try {
                await onInviteMember(email, role, personas);
                setInviteDialogOpen(false);
            } finally {
                setActionLoading(false);
            }
        },
        [onInviteMember]
    );

    const handleRemove = useCallback(
        async (userId: string) => {
            if (!onRemoveMember) return;
            if (!window.confirm('Are you sure you want to remove this member?')) return;
            setActionLoading(true);
            try {
                await onRemoveMember(userId);
            } finally {
                setActionLoading(false);
            }
        },
        [onRemoveMember]
    );

    const handleSaveEdit = useCallback(
        async (userId: string, role: WorkspaceRole, personas: PersonaType[]) => {
            setActionLoading(true);
            try {
                if (onUpdateRole && editMember && editMember.role !== role) {
                    await onUpdateRole(userId, role);
                }
                if (onUpdatePersonas) {
                    await onUpdatePersonas(userId, personas);
                }
                setEditMember(null);
            } finally {
                setActionLoading(false);
            }
        },
        [onUpdateRole, onUpdatePersonas, editMember]
    );

    const sortedMembers = useMemo(() => {
        const roleOrder: WorkspaceRole[] = [WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.MEMBER, WorkspaceRole.VIEWER];
        return [...members].sort((a, b) => roleOrder.indexOf(a.role) - roleOrder.indexOf(b.role));
    }, [members]);

    return (
        <Box className="h-full flex flex-col">
            {/* Header */}
            <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                <Box className="flex items-center justify-between">
                    <Box className="flex items-center gap-4">
                        <AdminIcon className="text-blue-600 text-[32px]" />
                        <Box>
                            <Typography as="h5">{workspace.name}</Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                Workspace Administration
                            </Typography>
                        </Box>
                    </Box>
                    <Box className="flex gap-2">
                        {onRefresh && (
                            <Tooltip title="Refresh">
                                <IconButton onClick={onRefresh} disabled={isLoading}>
                                    <RefreshIcon />
                                </IconButton>
                            </Tooltip>
                        )}
                        {canManageMembers && (
                            <Button
                                variant="solid"
                                startIcon={<InviteIcon />}
                                onClick={() => setInviteDialogOpen(true)}
                                disabled={isLoading}
                            >
                                Invite Member
                            </Button>
                        )}
                    </Box>
                </Box>
            </Box>

            {/* Error Alert */}
            {error && (
                <Alert severity="error" className="m-4">
                    {error}
                </Alert>
            )}

            {/* Tabs */}
            <Tabs
                value={activeTab}
                onChange={(_, v) => setActiveTab(v)}
                className="px-4 border-gray-200 dark:border-gray-700 border-b" >
                <Tab
                    label={`Members (${members.length})`}
                    value="members"
                    icon={<PeopleIcon />}
                    iconPosition="start"
                />
                <Tab
                    label="Permissions"
                    value="permissions"
                    icon={<SecurityIcon />}
                    iconPosition="start"
                />
                <Tab
                    label="Audit Log"
                    value="audit"
                    icon={<HistoryIcon />}
                    iconPosition="start"
                />
                <Tab
                    label="Settings"
                    value="settings"
                    icon={<SettingsIcon />}
                    iconPosition="start"
                />
            </Tabs>

            {/* Tab Content */}
            <Box className="flex-1 overflow-auto p-4">
                {isLoading && (
                    <Box className="flex justify-center p-8">
                        <CircularProgress />
                    </Box>
                )}

                {!isLoading && activeTab === 'members' && (
                    <Paper>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Member</TableCell>
                                    <TableCell>Role</TableCell>
                                    <TableCell>Personas</TableCell>
                                    <TableCell>Joined</TableCell>
                                    <TableCell align="right">Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {sortedMembers.map((member) => (
                                    <MemberRow
                                        key={member.userId}
                                        member={member}
                                        currentUserRole={currentUserRole}
                                        onEdit={setEditMember}
                                        onRemove={handleRemove}
                                        canManage={canManageMembers}
                                    />
                                ))}
                            </TableBody>
                        </Table>
                    </Paper>
                )}

                {!isLoading && activeTab === 'permissions' && <PermissionMatrix />}

                {!isLoading && activeTab === 'audit' && (
                    <AuditTrail workflowId={workspace.id} />
                )}

                {!isLoading && activeTab === 'settings' && (
                    <WorkspaceSettings workspace={workspace} />
                )}
            </Box>

            {/* Dialogs */}
            <InviteDialog
                open={inviteDialogOpen}
                onClose={() => setInviteDialogOpen(false)}
                onInvite={handleInvite}
                isLoading={actionLoading}
            />
            <EditMemberDialog
                open={!!editMember}
                member={editMember}
                currentUserRole={currentUserRole}
                onClose={() => setEditMember(null)}
                onSave={handleSaveEdit}
                isLoading={actionLoading}
            />
        </Box>
    );
};

export default WorkspaceAdminDashboard;
