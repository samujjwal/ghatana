/**
 * Workspace Admin Dashboard Refactored Components
 * 
 * Split from the large WorkspaceAdminDashboard.tsx component into focused,
 * manageable pieces for better maintainability and performance.
 * 
 * @doc.type component
 * @doc.purpose Refactored workspace admin components
 * @doc.layer product
 * @doc.pattern Component Refactoring
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, Typography, Card, CardContent, Tabs, Tab, Table, TableBody, TableCell, TableHead, TableRow, IconButton, Button, Chip, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Select, MenuItem, FormControl, InputLabel, Tooltip, Alert, Spinner as CircularProgress, Divider, Surface as Paper, Avatar, Badge } from '@ghatana/ui';
import { Plus as AddIcon, Pencil as EditIcon, Trash2 as DeleteIcon, UserPlus as InviteIcon, Shield as SecurityIcon, BarChart3 as AssessmentIcon, Settings as SettingsIcon, Mail as MailIcon, Settings as AdminIcon, Eye as ViewerIcon, Pencil as EditorIcon } from 'lucide-react';

// Types
interface WorkspaceMember {
  id: string;
  userId: string;
  name: string;
  email: string;
  avatar?: string;
  role: 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';
  status: 'active' | 'pending' | 'inactive';
  joinedAt: string;
  lastActive: string;
}

interface AuditLogEntry {
  id: string;
  action: string;
  userId: string;
  userName: string;
  timestamp: string;
  details: string;
  severity: 'low' | 'medium' | 'high';
}

interface SecurityAlert {
  id: string;
  type: string;
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  timestamp: string;
  resolved: boolean;
}

// ============================================================================
// Member Management Component
// ============================================================================

interface MemberManagementProps {
  members: WorkspaceMember[];
  onInviteMember: (email: string, role: string) => void;
  onRemoveMember: (memberId: string) => void;
  onUpdateMemberRole: (memberId: string, role: string) => void;
  loading?: boolean;
}

export const MemberManagement: React.FC<MemberManagementProps> = ({
  members,
  onInviteMember,
  onRemoveMember,
  onUpdateMemberRole,
  loading = false,
}) => {
  const [inviteDialogOpen, setInviteDialogOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('EDITOR');

  const handleInvite = useCallback(() => {
    if (inviteEmail && inviteRole) {
      onInviteMember(inviteEmail, inviteRole);
      setInviteDialogOpen(false);
      setInviteEmail('');
      setInviteRole('EDITOR');
    }
  }, [inviteEmail, inviteRole, onInviteMember]);

  const getRoleIcon = (role: string) => {
    switch (role) {
      case 'OWNER':
        return <AdminIcon />;
      case 'ADMIN':
        return <SecurityIcon />;
      case 'EDITOR':
        return <EditorIcon />;
      case 'VIEWER':
        return <ViewerIcon />;
      default:
        return <ViewerIcon />;
    }
  };

  const getRoleColor = (role: string) => {
    switch (role) {
      case 'OWNER':
        return 'error';
      case 'ADMIN':
        return 'warning';
      case 'EDITOR':
        return 'info';
      case 'VIEWER':
        return 'default';
      default:
        return 'default';
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'active':
        return 'success';
      case 'pending':
        return 'warning';
      case 'inactive':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Box>
      {/* Header */}
      <Box className="flex justify-between items-center mb-4">
        <Typography as="h6">Workspace Members</Typography>
        <Button
          variant="solid"
          startIcon={<InviteIcon />}
          onClick={() => setInviteDialogOpen(true)}
        >
          Invite Member
        </Button>
      </Box>

      {/* Members Table */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Member</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Joined</TableCell>
              <TableCell>Last Active</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {members.map((member) => (
              <TableRow key={member.id}>
                <TableCell>
                  <Box className="flex items-center gap-4">
                    <Avatar src={member.avatar} alt={member.name}>
                      {member.name.charAt(0)}
                    </Avatar>
                    <Box>
                      <Typography as="p" className="text-sm" fontWeight="medium">
                        {member.name}
                      </Typography>
                      <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                        {member.email}
                      </Typography>
                    </Box>
                  </Box>
                </TableCell>
                <TableCell>
                  <Chip
                    icon={getRoleIcon(member.role)}
                    label={member.role}
                    color={getRoleColor(member.role) as unknown}
                    size="sm"
                  />
                </TableCell>
                <TableCell>
                  <Chip
                    label={member.status}
                    color={getStatusColor(member.status) as unknown}
                    size="sm"
                  />
                </TableCell>
                <TableCell>
                  <Typography as="p" className="text-sm">
                    {new Date(member.joinedAt).toLocaleDateString()}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography as="p" className="text-sm">
                    {new Date(member.lastActive).toLocaleDateString()}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Tooltip title="Change Role">
                    <IconButton
                      size="sm"
                      onClick={() => onUpdateMemberRole(member.id, member.role)}
                    >
                      <EditIcon />
                    </IconButton>
                  </Tooltip>
                  <Tooltip title="Remove Member">
                    <IconButton
                      size="sm"
                      tone="danger"
                      onClick={() => onRemoveMember(member.id)}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* Invite Dialog */}
      <Dialog open={inviteDialogOpen} onClose={() => setInviteDialogOpen(false)}>
        <DialogTitle>Invite New Member</DialogTitle>
        <DialogContent>
          <Box className="pt-4">
            <TextField
              fullWidth
              label="Email Address"
              type="email"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              margin="normal"
            />
            <FormControl fullWidth margin="normal">
              <InputLabel>Role</InputLabel>
              <Select
                value={inviteRole}
                onChange={(e) => setInviteRole(e.target.value)}
                label="Role"
              >
                <MenuItem value="VIEWER">Viewer</MenuItem>
                <MenuItem value="EDITOR">Editor</MenuItem>
                <MenuItem value="ADMIN">Admin</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setInviteDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleInvite} variant="solid">
            Send Invitation
          </Button>
        </DialogActions>
      </Dialog>

      {/* Loading State */}
      {loading && (
        <Box className="flex justify-center p-4">
          <CircularProgress />
        </Box>
      )}
    </Box>
  );
};

// ============================================================================
// Security Settings Component
// ============================================================================

interface SecuritySettingsProps {
  settings: {
    twoFactorAuth: boolean;
    sessionTimeout: number;
    ipWhitelist: string[];
    auditLogging: boolean;
  };
  onUpdateSettings: (settings: unknown) => void;
  loading?: boolean;
}

export const SecuritySettings: React.FC<SecuritySettingsProps> = ({
  settings,
  onUpdateSettings,
  loading = false,
}) => {
  const [localSettings, setLocalSettings] = useState(settings);

  const handleChange = useCallback((field: string, value: unknown) => {
    setLocalSettings(prev => ({ ...prev, [field]: value }));
  }, []);

  const handleSave = useCallback(() => {
    onUpdateSettings(localSettings);
  }, [localSettings, onUpdateSettings]);

  return (
    <Box>
      <Typography as="h6" mb={2}>
        Security Settings
      </Typography>

      <Box className="flex flex-col gap-6">
        {/* Two-Factor Authentication */}
        <Card>
          <CardContent>
            <Box className="flex justify-between items-center">
              <Box>
                <Typography as="p" className="text-lg font-medium">Two-Factor Authentication</Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                  Require 2FA for all workspace members
                </Typography>
              </Box>
              <Button
                variant={localSettings.twoFactorAuth ? 'contained' : 'outlined'}
                onClick={() => handleChange('twoFactorAuth', !localSettings.twoFactorAuth)}
              >
                {localSettings.twoFactorAuth ? 'Enabled' : 'Disabled'}
              </Button>
            </Box>
          </CardContent>
        </Card>

        {/* Session Timeout */}
        <Card>
          <CardContent>
            <Typography as="p" className="text-lg font-medium" mb={2}>
              Session Timeout
            </Typography>
            <TextField
              fullWidth
              type="number"
              label="Timeout (minutes)"
              value={localSettings.sessionTimeout}
              onChange={(e) => handleChange('sessionTimeout', parseInt(e.target.value))}
              helperText="Users will be logged out after this period of inactivity"
            />
          </CardContent>
        </Card>

        {/* Audit Logging */}
        <Card>
          <CardContent>
            <Box className="flex justify-between items-center">
              <Box>
                <Typography as="p" className="text-lg font-medium">Audit Logging</Typography>
                <Typography as="p" className="text-sm" color="text.secondary">
                  Log all workspace activities for compliance
                </Typography>
              </Box>
              <Button
                variant={localSettings.auditLogging ? 'contained' : 'outlined'}
                onClick={() => handleChange('auditLogging', !localSettings.auditLogging)}
              >
                {localSettings.auditLogging ? 'Enabled' : 'Disabled'}
              </Button>
            </Box>
          </CardContent>
        </Card>

        {/* Save Button */}
        <Box className="flex justify-end">
          <Button
            variant="solid"
            onClick={handleSave}
            disabled={loading}
            startIcon={loading ? <CircularProgress size={16} /> : null}
          >
            Save Settings
          </Button>
        </Box>
      </Box>
    </Box>
  );
};

// ============================================================================
// Audit Log Component
// ============================================================================

interface AuditLogProps {
  logs: AuditLogEntry[];
  loading?: boolean;
  onExport?: () => void;
}

export const AuditLog: React.FC<AuditLogProps> = ({
  logs,
  loading = false,
  onExport,
}) => {
  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'high':
        return 'error';
      case 'medium':
        return 'warning';
      case 'low':
        return 'info';
      default:
        return 'default';
    }
  };

  const filteredLogs = useMemo(() => {
    return logs.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
  }, [logs]);

  return (
    <Box>
      {/* Header */}
      <Box className="flex justify-between items-center mb-4">
        <Typography as="h6">Audit Log</Typography>
        <Button
          variant="outlined"
          startIcon={<AssessmentIcon />}
          onClick={onExport}
        >
          Export Log
        </Button>
      </Box>

      {/* Logs List */}
      <Box className="overflow-auto max-h-[400px]">
        {filteredLogs.map((log) => (
          <Card key={log.id} className="mb-2">
            <CardContent className="py-4">
              <Box className="flex justify-between items-start">
                <Box className="flex-1">
                  <Box className="flex items-center gap-2 mb-2">
                    <Typography as="p" className="text-sm" fontWeight="medium">
                      {log.userName}
                    </Typography>
                    <Typography as="p" className="text-sm" color="text.secondary">
                      {log.action}
                    </Typography>
                    <Chip
                      label={log.severity}
                      color={getSeverityColor(log.severity) as unknown}
                      size="sm"
                    />
                  </Box>
                  <Typography as="p" className="text-sm" color="text.secondary">
                    {log.details}
                  </Typography>
                  <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    {new Date(log.timestamp).toLocaleString()}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        ))}
      </Box>

      {/* Loading State */}
      {loading && (
        <Box className="flex justify-center p-4">
          <CircularProgress />
        </Box>
      )}

      {/* Empty State */}
      {!loading && logs.length === 0 && (
        <Box className="text-center py-8">
          <Typography color="text.secondary">
            No audit logs available
          </Typography>
        </Box>
      )}
    </Box>
  );
};

// ============================================================================
// Security Alerts Component
// ============================================================================

interface SecurityAlertsProps {
  alerts: SecurityAlert[];
  onResolveAlert: (alertId: string) => void;
  loading?: boolean;
}

export const SecurityAlerts: React.FC<SecurityAlertsProps> = ({
  alerts,
  onResolveAlert,
  loading = false,
}) => {
  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical':
        return 'error';
      case 'high':
        return 'error';
      case 'medium':
        return 'warning';
      case 'low':
        return 'info';
      default:
        return 'default';
    }
  };

  const activeAlerts = useMemo(() => {
    return alerts.filter(alert => !alert.resolved).sort((a, b) => {
      const severityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
      return (severityOrder[b.severity as keyof typeof severityOrder] || 0) - 
             (severityOrder[a.severity as keyof typeof severityOrder] || 0);
    });
  }, [alerts]);

  return (
    <Box>
      {/* Header */}
      <Box className="flex justify-between items-center mb-4">
        <Typography as="h6">
          Security Alerts
          <Badge badgeContent={activeAlerts.length} tone="danger" className="ml-2">
            <SecurityIcon />
          </Badge>
        </Typography>
      </Box>

      {/* Alerts List */}
      <Box className="overflow-auto max-h-[400px]">
        {activeAlerts.map((alert) => (
          <Alert
            key={alert.id}
            severity={getSeverityColor(alert.severity) as unknown}
            action={
              <Button
                size="sm"
                onClick={() => onResolveAlert(alert.id)}
              >
                Resolve
              </Button>
            }
            className="mb-2"
          >
            <Typography as="p" className="text-sm" fontWeight="medium">
              {alert.type}
            </Typography>
            <Typography as="p" className="text-sm">
              {alert.message}
            </Typography>
            <Typography as="span" className="text-xs text-gray-500">
              {new Date(alert.timestamp).toLocaleString()}
            </Typography>
          </Alert>
        ))}
      </Box>

      {/* Loading State */}
      {loading && (
        <Box className="flex justify-center p-4">
          <CircularProgress />
        </Box>
      )}

      {/* Empty State */}
      {!loading && activeAlerts.length === 0 && (
        <Box className="text-center py-8">
          <Typography color="text.secondary">
            No active security alerts
          </Typography>
        </Box>
      )}
    </Box>
  );
};

// ============================================================================
// Refactored Workspace Admin Dashboard
// ============================================================================

interface WorkspaceAdminDashboardRefactoredProps {
  workspaceId: string;
}

export const WorkspaceAdminDashboardRefactored: React.FC<WorkspaceAdminDashboardRefactoredProps> = ({
  workspaceId,
}) => {
  const [activeTab, setActiveTab] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Mock data - in real implementation, this would come from API
  const [members] = useState<WorkspaceMember[]>([
    {
      id: '1',
      userId: 'user1',
      name: 'John Doe',
      email: 'john@example.com',
      role: 'OWNER',
      status: 'active',
      joinedAt: '2024-01-01',
      lastActive: '2024-01-15',
    },
    {
      id: '2',
      userId: 'user2',
      name: 'Jane Smith',
      email: 'jane@example.com',
      role: 'ADMIN',
      status: 'active',
      joinedAt: '2024-01-05',
      lastActive: '2024-01-14',
    },
  ]);

  const [auditLogs] = useState<AuditLogEntry[]>([
    {
      id: '1',
      action: 'User Login',
      userId: 'user1',
      userName: 'John Doe',
      timestamp: '2024-01-15T10:00:00Z',
      details: 'User logged in from IP 192.168.1.1',
      severity: 'low',
    },
  ]);

  const [securityAlerts] = useState<SecurityAlert[]>([
    {
      id: '1',
      type: 'Failed Login Attempt',
      severity: 'medium',
      message: 'Multiple failed login attempts detected',
      timestamp: '2024-01-15T09:30:00Z',
      resolved: false,
    },
  ]);

  const handleTabChange = useCallback((event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  }, []);

  const handleInviteMember = useCallback((email: string, role: string) => {
    setLoading(true);
    // API call to invite member
    setTimeout(() => {
      setLoading(false);
    }, 1000);
  }, []);

  const handleRemoveMember = useCallback((memberId: string) => {
    setLoading(true);
    // API call to remove member
    setTimeout(() => {
      setLoading(false);
    }, 1000);
  }, []);

  const handleUpdateMemberRole = useCallback((memberId: string, role: string) => {
    setLoading(true);
    // API call to update member role
    setTimeout(() => {
      setLoading(false);
    }, 1000);
  }, []);

  const handleResolveAlert = useCallback((alertId: string) => {
    setLoading(true);
    // API call to resolve alert
    setTimeout(() => {
      setLoading(false);
    }, 1000);
  }, []);

  return (
    <Box className="p-6">
      {/* Header */}
      <Box className="mb-6">
        <Typography as="h4" gutterBottom>
          Workspace Administration
        </Typography>
        <Typography as="p" color="text.secondary">
          Manage workspace members, security settings, and monitor activity
        </Typography>
      </Box>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}

      {/* Tabs */}
      <Box className="border-gray-200 dark:border-gray-700 border-b" >
        <Tabs value={activeTab} onChange={handleTabChange}>
          <Tab label="Members" icon={<PersonAdd />} />
          <Tab label="Security" icon={<SecurityIcon />} />
          <Tab label="Audit Log" icon={<AssessmentIcon />} />
          <Tab label="Alerts" icon={<SettingsIcon />} />
        </Tabs>
      </Box>

      {/* Tab Content */}
      <Box className="mt-4">
        {activeTab === 0 && (
          <MemberManagement
            members={members}
            onInviteMember={handleInviteMember}
            onRemoveMember={handleRemoveMember}
            onUpdateMemberRole={handleUpdateMemberRole}
            loading={loading}
          />
        )}
        {activeTab === 1 && (
          <SecuritySettings
            settings={{
              twoFactorAuth: false,
              sessionTimeout: 60,
              ipWhitelist: [],
              auditLogging: true,
            }}
            onUpdateSettings={() => {}}
            loading={loading}
          />
        )}
        {activeTab === 2 && (
          <AuditLog
            logs={auditLogs}
            loading={loading}
            onExport={() => {}}
          />
        )}
        {activeTab === 3 && (
          <SecurityAlerts
            alerts={securityAlerts}
            onResolveAlert={handleResolveAlert}
            loading={loading}
          />
        )}
      </Box>
    </Box>
  );
};
