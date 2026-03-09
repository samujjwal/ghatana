import React, { useState } from 'react';
import {
    Box,
    Typography,
    Card,
    CardContent,
    Chip,
    Button,
    TextField,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Avatar,
    LinearProgress,
    Alert,
    Tabs,
    Tab,
    MenuItem,
    Select,
    FormControl,
    InputLabel,
    IconButton,
    Stepper,
    Step,
    StepLabel,
    StepContent,
} from '@ghatana/ui';

// Types
export type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'cancelled';
export type RequestType = 'budget' | 'headcount' | 'promotion' | 'policy' | 'project' | 'other';
export type ApproverAction = 'approve' | 'reject';

export interface Approver {
    id: string;
    name: string;
    email: string;
    avatar?: string;
    level: number;
    status: 'pending' | 'approved' | 'rejected' | 'skipped';
    actionDate?: string;
    comment?: string;
}

export interface ApprovalRequest {
    id: string;
    title: string;
    description: string;
    type: RequestType;
    status: ApprovalStatus;
    requestedBy: {
        id: string;
        name: string;
        email: string;
        avatar?: string;
    };
    requestedAt: string;
    completedAt?: string;
    approvers: Approver[];
    currentLevel: number;
    totalLevels: number;
    attachments?: Array<{
        id: string;
        name: string;
        size: number;
        uploadedAt: string;
    }>;
    comments: Array<{
        id: string;
        userId: string;
        userName: string;
        userAvatar?: string;
        message: string;
        timestamp: string;
    }>;
}

export interface ApprovalWorkflowProps {
    onSubmitRequest?: (request: Omit<ApprovalRequest, 'id' | 'status' | 'requestedAt' | 'currentLevel' | 'comments'>) => void;
    onApprove?: (requestId: string, approverId: string, comment?: string) => void;
    onReject?: (requestId: string, approverId: string, comment: string) => void;
    onCancel?: (requestId: string) => void;
    onAddComment?: (requestId: string, comment: string) => void;
    onUploadAttachment?: (requestId: string, file: File) => void;
    currentUserId?: string;
}

// Mock data
const mockRequests: ApprovalRequest[] = [
    {
        id: '1',
        title: 'Q1 2024 Marketing Budget Increase',
        description: 'Request to increase marketing budget by $50,000 for new digital campaign targeting enterprise customers.',
        type: 'budget',
        status: 'pending',
        requestedBy: {
            id: 'user1',
            name: 'Sarah Johnson',
            email: 'sarah.johnson@company.com',
        },
        requestedAt: '2024-01-15T10:30:00Z',
        approvers: [
            {
                id: 'approver1',
                name: 'Michael Chen',
                email: 'michael.chen@company.com',
                level: 1,
                status: 'approved',
                actionDate: '2024-01-15T14:20:00Z',
                comment: 'Approved. Campaign metrics look promising.',
            },
            {
                id: 'approver2',
                name: 'Jennifer Lee',
                email: 'jennifer.lee@company.com',
                level: 2,
                status: 'pending',
            },
            {
                id: 'approver3',
                name: 'Robert Taylor',
                email: 'robert.taylor@company.com',
                level: 3,
                status: 'pending',
            },
        ],
        currentLevel: 2,
        totalLevels: 3,
        attachments: [
            {
                id: 'att1',
                name: 'Campaign_Proposal.pdf',
                size: 2457600,
                uploadedAt: '2024-01-15T10:30:00Z',
            },
            {
                id: 'att2',
                name: 'Budget_Breakdown.xlsx',
                size: 1048576,
                uploadedAt: '2024-01-15T10:31:00Z',
            },
        ],
        comments: [
            {
                id: 'c1',
                userId: 'approver1',
                userName: 'Michael Chen',
                message: 'What is the expected ROI for this campaign?',
                timestamp: '2024-01-15T11:00:00Z',
            },
            {
                id: 'c2',
                userId: 'user1',
                userName: 'Sarah Johnson',
                message: 'We expect 3:1 ROI based on similar campaigns. Details in the proposal.',
                timestamp: '2024-01-15T11:30:00Z',
            },
        ],
    },
    {
        id: '2',
        title: 'New Senior Engineer Position - Data Platform',
        description: 'Request to hire a Senior Data Engineer for the platform team to support growing data infrastructure needs.',
        type: 'headcount',
        status: 'approved',
        requestedBy: {
            id: 'user2',
            name: 'David Park',
            email: 'david.park@company.com',
        },
        requestedAt: '2024-01-10T09:00:00Z',
        completedAt: '2024-01-12T16:45:00Z',
        approvers: [
            {
                id: 'approver4',
                name: 'Lisa Wang',
                email: 'lisa.wang@company.com',
                level: 1,
                status: 'approved',
                actionDate: '2024-01-10T15:30:00Z',
                comment: 'Approved. Critical for Q1 roadmap.',
            },
            {
                id: 'approver5',
                name: 'James Martinez',
                email: 'james.martinez@company.com',
                level: 2,
                status: 'approved',
                actionDate: '2024-01-12T16:45:00Z',
                comment: 'Approved. Budget allocated.',
            },
        ],
        currentLevel: 2,
        totalLevels: 2,
        attachments: [
            {
                id: 'att3',
                name: 'Job_Description.pdf',
                size: 512000,
                uploadedAt: '2024-01-10T09:00:00Z',
            },
        ],
        comments: [],
    },
    {
        id: '3',
        title: 'Promotion: Emily White to Engineering Manager',
        description: 'Request to promote Emily White from Senior Engineer to Engineering Manager based on outstanding performance and leadership.',
        type: 'promotion',
        status: 'rejected',
        requestedBy: {
            id: 'user3',
            name: 'Alex Thompson',
            email: 'alex.thompson@company.com',
        },
        requestedAt: '2024-01-08T13:00:00Z',
        completedAt: '2024-01-09T10:15:00Z',
        approvers: [
            {
                id: 'approver6',
                name: 'Rachel Green',
                email: 'rachel.green@company.com',
                level: 1,
                status: 'rejected',
                actionDate: '2024-01-09T10:15:00Z',
                comment: 'Emily needs 6 more months of technical leadership experience. Let\'s revisit in Q3.',
            },
        ],
        currentLevel: 1,
        totalLevels: 2,
        attachments: [],
        comments: [
            {
                id: 'c3',
                userId: 'user3',
                userName: 'Alex Thompson',
                message: 'Emily has been mentoring 3 junior engineers and leading the API redesign project.',
                timestamp: '2024-01-08T14:00:00Z',
            },
        ],
    },
];

const requestTypeLabels: Record<RequestType, string> = {
    budget: 'Budget',
    headcount: 'Headcount',
    promotion: 'Promotion',
    policy: 'Policy',
    project: 'Project',
    other: 'Other',
};

const requestTypeColors: Record<RequestType, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    budget: 'warning',
    headcount: 'info',
    promotion: 'success',
    policy: 'secondary',
    project: 'primary',
    other: 'secondary',
};

const statusColors: Record<ApprovalStatus, 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'error',
    cancelled: 'secondary',
};

const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const formatTimestamp = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;
    return date.toLocaleDateString();
};

export const ApprovalWorkflow: React.FC<ApprovalWorkflowProps> = ({
    onSubmitRequest,
    onApprove,
    onReject,
    onCancel,
    onAddComment,
    onUploadAttachment,
    currentUserId = 'approver2',
}) => {
    const [requests] = useState<ApprovalRequest[]>(mockRequests);
    const [selectedTab, setSelectedTab] = useState(0);
    const [selectedRequest, setSelectedRequest] = useState<ApprovalRequest | null>(null);
    const [detailDialogOpen, setDetailDialogOpen] = useState(false);
    const [createDialogOpen, setCreateDialogOpen] = useState(false);
    const [actionDialogOpen, setActionDialogOpen] = useState(false);
    const [actionType, setActionType] = useState<ApproverAction>('approve');
    const [actionComment, setActionComment] = useState('');
    const [newComment, setNewComment] = useState('');

    // Form state
    const [formTitle, setFormTitle] = useState('');
    const [formDescription, setFormDescription] = useState('');
    const [formType, setFormType] = useState<RequestType>('budget');
    const [formApprovers, setFormApprovers] = useState<Approver[]>([]);

    const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
        setSelectedTab(newValue);
    };

    const handleViewDetails = (request: ApprovalRequest) => {
        setSelectedRequest(request);
        setDetailDialogOpen(true);
    };

    const handleCloseDetail = () => {
        setDetailDialogOpen(false);
        setSelectedRequest(null);
        setNewComment('');
    };

    const handleOpenCreate = () => {
        setCreateDialogOpen(true);
    };

    const handleCloseCreate = () => {
        setCreateDialogOpen(false);
        setFormTitle('');
        setFormDescription('');
        setFormType('budget');
        setFormApprovers([]);
    };

    const handleSubmitRequest = () => {
        if (onSubmitRequest && formTitle && formDescription && formApprovers.length > 0) {
            onSubmitRequest({
                title: formTitle,
                description: formDescription,
                type: formType,
                requestedBy: {
                    id: currentUserId,
                    name: 'Current User',
                    email: 'current.user@company.com',
                },
                approvers: formApprovers,
                totalLevels: formApprovers.length,
                attachments: [],
            });
        }
        handleCloseCreate();
    };

    const handleOpenAction = (type: ApproverAction) => {
        setActionType(type);
        setActionComment('');
        setActionDialogOpen(true);
    };

    const handleCloseAction = () => {
        setActionDialogOpen(false);
        setActionComment('');
    };

    const handleConfirmAction = () => {
        if (!selectedRequest) return;

        const currentApprover = selectedRequest.approvers.find(
            (a) => a.id === currentUserId && a.status === 'pending'
        );

        if (!currentApprover) return;

        if (actionType === 'approve') {
            onApprove?.(selectedRequest.id, currentApprover.id, actionComment);
        } else {
            if (actionComment.trim()) {
                onReject?.(selectedRequest.id, currentApprover.id, actionComment);
            }
        }

        handleCloseAction();
        handleCloseDetail();
    };

    const handleAddComment = () => {
        if (!selectedRequest || !newComment.trim()) return;
        onAddComment?.(selectedRequest.id, newComment);
        setNewComment('');
    };

    const handleCancelRequest = () => {
        if (!selectedRequest) return;
        onCancel?.(selectedRequest.id);
        handleCloseDetail();
    };

    const getFilteredRequests = () => {
        switch (selectedTab) {
            case 0: // Pending
                return requests.filter((r) => r.status === 'pending');
            case 1: // Approved
                return requests.filter((r) => r.status === 'approved');
            case 2: // Rejected
                return requests.filter((r) => r.status === 'rejected');
            case 3: // My Requests
                return requests.filter((r) => r.requestedBy.id === currentUserId);
            default:
                return requests;
        }
    };

    const canTakeAction = (request: ApprovalRequest): boolean => {
        return request.approvers.some(
            (a) => a.id === currentUserId && a.status === 'pending'
        );
    };

    const getProgressPercentage = (request: ApprovalRequest): number => {
        const approvedCount = request.approvers.filter((a) => a.status === 'approved').length;
        return (approvedCount / request.totalLevels) * 100;
    };

    const filteredRequests = getFilteredRequests();

    return (
        <Box>
            {/* Header */}
            <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Box>
                    <Typography variant="h4" className="dark:text-white">
                        Approval Workflow
                    </Typography>
                    <Typography variant="body2" className="dark:text-gray-400">
                        Manage approval requests and workflows
                    </Typography>
                </Box>
                <Button variant="contained" color="primary" onClick={handleOpenCreate}>
                    New Request
                </Button>
            </Box>

            {/* Tabs */}
            <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
                <Tabs value={selectedTab} onChange={handleTabChange}>
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                Pending
                                <Chip
                                    label={requests.filter((r) => r.status === 'pending').length}
                                    size="small"
                                    color="warning"
                                />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                Approved
                                <Chip
                                    label={requests.filter((r) => r.status === 'approved').length}
                                    size="small"
                                    color="success"
                                />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                Rejected
                                <Chip
                                    label={requests.filter((r) => r.status === 'rejected').length}
                                    size="small"
                                    color="error"
                                />
                            </Box>
                        }
                    />
                    <Tab
                        label={
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                My Requests
                                <Chip
                                    label={requests.filter((r) => r.requestedBy.id === currentUserId).length}
                                    size="small"
                                />
                            </Box>
                        }
                    />
                </Tabs>
            </Box>

            {/* Request List */}
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {filteredRequests.length === 0 ? (
                    <Alert severity="info">No approval requests found.</Alert>
                ) : (
                    filteredRequests.map((request) => (
                        <Card key={request.id} className="dark:bg-gray-800">
                            <CardContent>
                                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', mb: 2 }}>
                                    <Box sx={{ flex: 1 }}>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                                            <Typography variant="h6" className="dark:text-white">
                                                {request.title}
                                            </Typography>
                                            <Chip
                                                label={requestTypeLabels[request.type]}
                                                size="small"
                                                color={requestTypeColors[request.type]}
                                            />
                                            <Chip
                                                label={request.status.charAt(0).toUpperCase() + request.status.slice(1)}
                                                size="small"
                                                color={statusColors[request.status]}
                                            />
                                        </Box>
                                        <Typography variant="body2" className="dark:text-gray-400" sx={{ mb: 1 }}>
                                            {request.description.length > 150
                                                ? `${request.description.substring(0, 150)}...`
                                                : request.description}
                                        </Typography>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                <Avatar
                                                    src={request.requestedBy.avatar}
                                                    sx={{ width: 24, height: 24 }}
                                                >
                                                    {request.requestedBy.name.charAt(0)}
                                                </Avatar>
                                                <Typography variant="caption" className="dark:text-gray-400">
                                                    {request.requestedBy.name}
                                                </Typography>
                                            </Box>
                                            <Typography variant="caption" className="dark:text-gray-400">
                                                {formatTimestamp(request.requestedAt)}
                                            </Typography>
                                            {request.attachments && request.attachments.length > 0 && (
                                                <Typography variant="caption" className="dark:text-gray-400">
                                                    📎 {request.attachments.length} attachment{request.attachments.length > 1 ? 's' : ''}
                                                </Typography>
                                            )}
                                            {request.comments.length > 0 && (
                                                <Typography variant="caption" className="dark:text-gray-400">
                                                    💬 {request.comments.length} comment{request.comments.length > 1 ? 's' : ''}
                                                </Typography>
                                            )}
                                        </Box>
                                    </Box>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 1 }}>
                                        <Button
                                            variant="outlined"
                                            size="small"
                                            onClick={() => handleViewDetails(request)}
                                        >
                                            View Details
                                        </Button>
                                        {canTakeAction(request) && (
                                            <Chip label="Action Required" size="small" color="warning" />
                                        )}
                                    </Box>
                                </Box>

                                {/* Progress Bar */}
                                <Box sx={{ mb: 1 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Approval Progress
                                        </Typography>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Level {request.currentLevel} of {request.totalLevels}
                                        </Typography>
                                    </Box>
                                    <LinearProgress
                                        variant="determinate"
                                        value={getProgressPercentage(request)}
                                        color={request.status === 'rejected' ? 'error' : 'primary'}
                                    />
                                </Box>

                                {/* Approver Chips */}
                                <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                                    {request.approvers.map((approver) => (
                                        <Chip
                                            key={approver.id}
                                            avatar={
                                                <Avatar src={approver.avatar}>
                                                    {approver.name.charAt(0)}
                                                </Avatar>
                                            }
                                            label={`${approver.name} (L${approver.level})`}
                                            size="small"
                                            color={
                                                approver.status === 'approved'
                                                    ? 'success'
                                                    : approver.status === 'rejected'
                                                        ? 'error'
                                                        : approver.status === 'skipped'
                                                            ? 'secondary'
                                                            : 'default'
                                            }
                                            variant={approver.status === 'pending' ? 'outlined' : 'filled'}
                                        />
                                    ))}
                                </Box>
                            </CardContent>
                        </Card>
                    ))
                )}
            </Box>

            {/* Detail Dialog */}
            <Dialog
                open={detailDialogOpen}
                onClose={handleCloseDetail}
                maxWidth="md"
                fullWidth
            >
                {selectedRequest && (
                    <>
                        <DialogTitle className="dark:bg-gray-800 dark:text-white">
                            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <Typography variant="h6">{selectedRequest.title}</Typography>
                                <Box sx={{ display: 'flex', gap: 1 }}>
                                    <Chip
                                        label={requestTypeLabels[selectedRequest.type]}
                                        size="small"
                                        color={requestTypeColors[selectedRequest.type]}
                                    />
                                    <Chip
                                        label={selectedRequest.status.charAt(0).toUpperCase() + selectedRequest.status.slice(1)}
                                        size="small"
                                        color={statusColors[selectedRequest.status]}
                                    />
                                </Box>
                            </Box>
                        </DialogTitle>
                        <DialogContent className="dark:bg-gray-800">
                            <Box sx={{ mt: 2 }}>
                                {/* Request Info */}
                                <Box sx={{ mb: 3 }}>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                        Description
                                    </Typography>
                                    <Typography variant="body2" className="dark:text-gray-400">
                                        {selectedRequest.description}
                                    </Typography>
                                </Box>

                                <Box sx={{ display: 'flex', gap: 4, mb: 3 }}>
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Requested By
                                        </Typography>
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
                                            <Avatar
                                                src={selectedRequest.requestedBy.avatar}
                                                sx={{ width: 32, height: 32 }}
                                            >
                                                {selectedRequest.requestedBy.name.charAt(0)}
                                            </Avatar>
                                            <Box>
                                                <Typography variant="body2" className="dark:text-white">
                                                    {selectedRequest.requestedBy.name}
                                                </Typography>
                                                <Typography variant="caption" className="dark:text-gray-400">
                                                    {selectedRequest.requestedBy.email}
                                                </Typography>
                                            </Box>
                                        </Box>
                                    </Box>
                                    <Box>
                                        <Typography variant="caption" className="dark:text-gray-400">
                                            Requested At
                                        </Typography>
                                        <Typography variant="body2" className="dark:text-white" sx={{ mt: 0.5 }}>
                                            {new Date(selectedRequest.requestedAt).toLocaleString()}
                                        </Typography>
                                    </Box>
                                    {selectedRequest.completedAt && (
                                        <Box>
                                            <Typography variant="caption" className="dark:text-gray-400">
                                                Completed At
                                            </Typography>
                                            <Typography variant="body2" className="dark:text-white" sx={{ mt: 0.5 }}>
                                                {new Date(selectedRequest.completedAt).toLocaleString()}
                                            </Typography>
                                        </Box>
                                    )}
                                </Box>

                                {/* Approval Chain */}
                                <Box sx={{ mb: 3 }}>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 2 }}>
                                        Approval Chain
                                    </Typography>
                                    <Stepper orientation="vertical" activeStep={selectedRequest.currentLevel - 1}>
                                        {selectedRequest.approvers.map((approver, index) => (
                                            <Step key={approver.id} completed={approver.status === 'approved'}>
                                                <StepLabel
                                                    error={approver.status === 'rejected'}
                                                    optional={
                                                        approver.actionDate && (
                                                            <Typography variant="caption">
                                                                {formatTimestamp(approver.actionDate)}
                                                            </Typography>
                                                        )
                                                    }
                                                >
                                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                                        <Avatar
                                                            src={approver.avatar}
                                                            sx={{ width: 28, height: 28 }}
                                                        >
                                                            {approver.name.charAt(0)}
                                                        </Avatar>
                                                        <Box>
                                                            <Typography variant="body2" className="dark:text-white">
                                                                {approver.name}
                                                            </Typography>
                                                            <Typography variant="caption" className="dark:text-gray-400">
                                                                {approver.email}
                                                            </Typography>
                                                        </Box>
                                                    </Box>
                                                </StepLabel>
                                                <StepContent>
                                                    {approver.comment && (
                                                        <Box
                                                            sx={{
                                                                bgcolor: 'background.paper',
                                                                p: 1.5,
                                                                borderRadius: 1,
                                                                mb: 1,
                                                            }}
                                                            className="dark:bg-gray-700"
                                                        >
                                                            <Typography variant="body2" className="dark:text-gray-300">
                                                                {approver.comment}
                                                            </Typography>
                                                        </Box>
                                                    )}
                                                    {approver.status === 'pending' && approver.id === currentUserId && (
                                                        <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
                                                            <Button
                                                                variant="contained"
                                                                color="success"
                                                                size="small"
                                                                onClick={() => handleOpenAction('approve')}
                                                            >
                                                                Approve
                                                            </Button>
                                                            <Button
                                                                variant="outlined"
                                                                color="error"
                                                                size="small"
                                                                onClick={() => handleOpenAction('reject')}
                                                            >
                                                                Reject
                                                            </Button>
                                                        </Box>
                                                    )}
                                                </StepContent>
                                            </Step>
                                        ))}
                                    </Stepper>
                                </Box>

                                {/* Attachments */}
                                {selectedRequest.attachments && selectedRequest.attachments.length > 0 && (
                                    <Box sx={{ mb: 3 }}>
                                        <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                            Attachments
                                        </Typography>
                                        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                                            {selectedRequest.attachments.map((attachment) => (
                                                <Box
                                                    key={attachment.id}
                                                    sx={{
                                                        display: 'flex',
                                                        justifyContent: 'space-between',
                                                        alignItems: 'center',
                                                        p: 1.5,
                                                        bgcolor: 'background.paper',
                                                        borderRadius: 1,
                                                    }}
                                                    className="dark:bg-gray-700"
                                                >
                                                    <Box>
                                                        <Typography variant="body2" className="dark:text-white">
                                                            📎 {attachment.name}
                                                        </Typography>
                                                        <Typography variant="caption" className="dark:text-gray-400">
                                                            {formatFileSize(attachment.size)} • {formatTimestamp(attachment.uploadedAt)}
                                                        </Typography>
                                                    </Box>
                                                    <Button size="small" variant="outlined">
                                                        Download
                                                    </Button>
                                                </Box>
                                            ))}
                                        </Box>
                                    </Box>
                                )}

                                {/* Comments */}
                                <Box>
                                    <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                        Comments
                                    </Typography>
                                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mb: 2 }}>
                                        {selectedRequest.comments.length === 0 ? (
                                            <Typography variant="body2" className="dark:text-gray-400">
                                                No comments yet
                                            </Typography>
                                        ) : (
                                            selectedRequest.comments.map((comment) => (
                                                <Box
                                                    key={comment.id}
                                                    sx={{
                                                        display: 'flex',
                                                        gap: 1.5,
                                                        p: 1.5,
                                                        bgcolor: 'background.paper',
                                                        borderRadius: 1,
                                                    }}
                                                    className="dark:bg-gray-700"
                                                >
                                                    <Avatar
                                                        src={comment.userAvatar}
                                                        sx={{ width: 32, height: 32 }}
                                                    >
                                                        {comment.userName.charAt(0)}
                                                    </Avatar>
                                                    <Box sx={{ flex: 1 }}>
                                                        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                                            <Typography variant="body2" className="dark:text-white" sx={{ fontWeight: 600 }}>
                                                                {comment.userName}
                                                            </Typography>
                                                            <Typography variant="caption" className="dark:text-gray-400">
                                                                {formatTimestamp(comment.timestamp)}
                                                            </Typography>
                                                        </Box>
                                                        <Typography variant="body2" className="dark:text-gray-300">
                                                            {comment.message}
                                                        </Typography>
                                                    </Box>
                                                </Box>
                                            ))
                                        )}
                                    </Box>

                                    {/* Add Comment */}
                                    <Box sx={{ display: 'flex', gap: 1 }}>
                                        <TextField
                                            fullWidth
                                            size="small"
                                            placeholder="Add a comment..."
                                            value={newComment}
                                            onChange={(e) => setNewComment(e.target.value)}
                                            multiline
                                            rows={2}
                                        />
                                        <Button
                                            variant="contained"
                                            onClick={handleAddComment}
                                            disabled={!newComment.trim()}
                                        >
                                            Send
                                        </Button>
                                    </Box>
                                </Box>
                            </Box>
                        </DialogContent>
                        <DialogActions className="dark:bg-gray-800">
                            {selectedRequest.status === 'pending' &&
                                selectedRequest.requestedBy.id === currentUserId && (
                                    <Button color="error" onClick={handleCancelRequest}>
                                        Cancel Request
                                    </Button>
                                )}
                            <Button onClick={handleCloseDetail}>Close</Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Create Request Dialog */}
            <Dialog open={createDialogOpen} onClose={handleCloseCreate} maxWidth="sm" fullWidth>
                <DialogTitle className="dark:bg-gray-800 dark:text-white">
                    New Approval Request
                </DialogTitle>
                <DialogContent className="dark:bg-gray-800">
                    <Box sx={{ mt: 2, display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <TextField
                            fullWidth
                            label="Title"
                            value={formTitle}
                            onChange={(e) => setFormTitle(e.target.value)}
                            required
                        />
                        <TextField
                            fullWidth
                            label="Description"
                            value={formDescription}
                            onChange={(e) => setFormDescription(e.target.value)}
                            multiline
                            rows={4}
                            required
                        />
                        <FormControl fullWidth>
                            <InputLabel>Request Type</InputLabel>
                            <Select
                                value={formType}
                                label="Request Type"
                                onChange={(e) => setFormType(e.target.value as RequestType)}
                            >
                                {Object.entries(requestTypeLabels).map(([value, label]) => (
                                    <MenuItem key={value} value={value}>
                                        {label}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <Box>
                            <Typography variant="subtitle2" className="dark:text-gray-300" sx={{ mb: 1 }}>
                                Approval Chain
                            </Typography>
                            <Typography variant="caption" className="dark:text-gray-400" sx={{ mb: 1, display: 'block' }}>
                                Add approvers in order (Level 1, Level 2, etc.)
                            </Typography>
                            {formApprovers.map((approver, index) => (
                                <Box key={index} sx={{ display: 'flex', gap: 1, mb: 1, alignItems: 'center' }}>
                                    <Chip
                                        label={`Level ${approver.level}: ${approver.name}`}
                                        onDelete={() => {
                                            setFormApprovers(formApprovers.filter((_, i) => i !== index));
                                        }}
                                    />
                                </Box>
                            ))}
                            <Alert severity="info" sx={{ mt: 1 }}>
                                In a real implementation, you would select approvers from a user directory.
                            </Alert>
                        </Box>
                    </Box>
                </DialogContent>
                <DialogActions className="dark:bg-gray-800">
                    <Button onClick={handleCloseCreate}>Cancel</Button>
                    <Button
                        variant="contained"
                        onClick={handleSubmitRequest}
                        disabled={!formTitle || !formDescription || formApprovers.length === 0}
                    >
                        Submit Request
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Action Dialog (Approve/Reject) */}
            <Dialog open={actionDialogOpen} onClose={handleCloseAction} maxWidth="sm" fullWidth>
                <DialogTitle className="dark:bg-gray-800 dark:text-white">
                    {actionType === 'approve' ? 'Approve Request' : 'Reject Request'}
                </DialogTitle>
                <DialogContent className="dark:bg-gray-800">
                    <Box sx={{ mt: 2 }}>
                        <TextField
                            fullWidth
                            label={actionType === 'approve' ? 'Comment (Optional)' : 'Reason for Rejection'}
                            value={actionComment}
                            onChange={(e) => setActionComment(e.target.value)}
                            multiline
                            rows={4}
                            required={actionType === 'reject'}
                        />
                    </Box>
                </DialogContent>
                <DialogActions className="dark:bg-gray-800">
                    <Button onClick={handleCloseAction}>Cancel</Button>
                    <Button
                        variant="contained"
                        color={actionType === 'approve' ? 'success' : 'error'}
                        onClick={handleConfirmAction}
                        disabled={actionType === 'reject' && !actionComment.trim()}
                    >
                        {actionType === 'approve' ? 'Approve' : 'Reject'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    );
};
