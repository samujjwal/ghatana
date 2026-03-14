import { useState, useMemo } from 'react';
import {
    Box,
    Card,
    Stack,
    Grid,
    Typography,
    Button,
    Chip,
    TextField,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Alert,
    LinearProgress,
    IconButton,
    Tabs,
    Tab,
} from '@ghatana/design-system';
import {
    Add as AddIcon,
    Edit as EditIcon,
    Check as CheckIcon,
    Close as CloseIcon,
    TrendingUp as TrendingUpIcon,
    TrendingDown as TrendingDownIcon,
    Remove as RemoveIcon,
} from '@ghatana/design-system/icons';

/**
 * Department budget allocation
 */
export interface DepartmentBudget {
    id: string;
    departmentName: string;
    departmentId: string;
    fiscalYear: number;
    allocated: number;
    spent: number;
    forecast: number;
    lastYearSpent?: number;
    category: 'engineering' | 'sales' | 'marketing' | 'operations' | 'hr' | 'other';
    status: 'draft' | 'pending_approval' | 'approved' | 'rejected';
    approvedBy?: string;
    approvedAt?: string;
}

/**
 * Budget allocation by category
 */
export interface BudgetCategory {
    id: string;
    name: string;
    allocated: number;
    spent: number;
    forecast: number;
}

/**
 * Budget approval request
 */
export interface BudgetApproval {
    id: string;
    departmentBudgetId: string;
    requestedBy: string;
    requestedAt: string;
    amount: number;
    reason: string;
    status: 'pending' | 'approved' | 'rejected';
    reviewedBy?: string;
    reviewedAt?: string;
    comments?: string;
}

export interface BudgetPlanningDashboardProps {
    fiscalYear?: number;
    departmentBudgets?: DepartmentBudget[];
    totalBudget?: number;
    onUpdateBudget?: (departmentId: string, allocation: number) => void;
    onApproveBudget?: (budgetId: string, comments?: string) => void;
    onRejectBudget?: (budgetId: string, comments: string) => void;
    onSubmitForApproval?: (budgetId: string) => void;
}

/**
 * Budget Planning Dashboard Component
 *
 * Provides organization owners with:
 * - Annual budget allocation by department
 * - Forecast vs actual tracking
 * - Budget approval workflow
 * - Historical comparison
 * - Category-based analysis
 */
export function BudgetPlanningDashboard({
    fiscalYear = 2025,
    departmentBudgets: propBudgets,
    totalBudget = 5000000,
    onUpdateBudget,
    onApproveBudget,
    onRejectBudget,
    onSubmitForApproval,
}: BudgetPlanningDashboardProps) {
    // Mock data for development
    const mockBudgets: DepartmentBudget[] = [
        {
            id: 'budget-1',
            departmentName: 'Engineering',
            departmentId: 'dept-1',
            fiscalYear: 2025,
            allocated: 2000000,
            spent: 1650000,
            forecast: 1950000,
            lastYearSpent: 1800000,
            category: 'engineering',
            status: 'approved',
            approvedBy: 'CFO',
            approvedAt: '2024-12-01T00:00:00Z',
        },
        {
            id: 'budget-2',
            departmentName: 'Sales',
            departmentId: 'dept-2',
            fiscalYear: 2025,
            allocated: 1200000,
            spent: 950000,
            forecast: 1150000,
            lastYearSpent: 1000000,
            category: 'sales',
            status: 'approved',
            approvedBy: 'CFO',
            approvedAt: '2024-12-01T00:00:00Z',
        },
        {
            id: 'budget-3',
            departmentName: 'Marketing',
            departmentId: 'dept-3',
            fiscalYear: 2025,
            allocated: 800000,
            spent: 620000,
            forecast: 780000,
            lastYearSpent: 700000,
            category: 'marketing',
            status: 'approved',
            approvedBy: 'CFO',
            approvedAt: '2024-12-01T00:00:00Z',
        },
        {
            id: 'budget-4',
            departmentName: 'Operations',
            departmentId: 'dept-4',
            fiscalYear: 2025,
            allocated: 600000,
            spent: 480000,
            forecast: 590000,
            lastYearSpent: 550000,
            category: 'operations',
            status: 'pending_approval',
        },
        {
            id: 'budget-5',
            departmentName: 'Human Resources',
            departmentId: 'dept-5',
            fiscalYear: 2025,
            allocated: 400000,
            spent: 310000,
            forecast: 380000,
            lastYearSpent: 350000,
            category: 'hr',
            status: 'draft',
        },
    ];

    const departmentBudgets = propBudgets || mockBudgets;

    const [activeTab, setActiveTab] = useState(0);
    const [editDialogOpen, setEditDialogOpen] = useState(false);
    const [selectedBudget, setSelectedBudget] = useState<DepartmentBudget | null>(null);
    const [editedAllocation, setEditedAllocation] = useState(0);
    const [approvalDialogOpen, setApprovalDialogOpen] = useState(false);
    const [approvalComments, setApprovalComments] = useState('');
    const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
    const [rejectComments, setRejectComments] = useState('');

    // Calculate totals
    const totalAllocated = useMemo(() => {
        return departmentBudgets.reduce((sum, b) => sum + b.allocated, 0);
    }, [departmentBudgets]);

    const totalSpent = useMemo(() => {
        return departmentBudgets.reduce((sum, b) => sum + b.spent, 0);
    }, [departmentBudgets]);

    const totalForecast = useMemo(() => {
        return departmentBudgets.reduce((sum, b) => sum + b.forecast, 0);
    }, [departmentBudgets]);

    const totalLastYear = useMemo(() => {
        return departmentBudgets.reduce((sum, b) => sum + (b.lastYearSpent || 0), 0);
    }, [departmentBudgets]);

    // Group by category
    const budgetsByCategory = useMemo(() => {
        const categories: Record<string, BudgetCategory> = {};

        departmentBudgets.forEach((budget) => {
            const category = budget.category;
            if (!categories[category]) {
                categories[category] = {
                    id: category,
                    name: category.charAt(0).toUpperCase() + category.slice(1),
                    allocated: 0,
                    spent: 0,
                    forecast: 0,
                };
            }
            categories[category].allocated += budget.allocated;
            categories[category].spent += budget.spent;
            categories[category].forecast += budget.forecast;
        });

        return Object.values(categories);
    }, [departmentBudgets]);

    const handleEditBudget = (budget: DepartmentBudget) => {
        setSelectedBudget(budget);
        setEditedAllocation(budget.allocated);
        setEditDialogOpen(true);
    };

    const handleSaveEdit = () => {
        if (selectedBudget && onUpdateBudget) {
            onUpdateBudget(selectedBudget.departmentId, editedAllocation);
            setEditDialogOpen(false);
            setSelectedBudget(null);
        }
    };

    const handleApprove = (budget: DepartmentBudget) => {
        setSelectedBudget(budget);
        setApprovalDialogOpen(true);
    };

    const handleConfirmApprove = () => {
        if (selectedBudget && onApproveBudget) {
            onApproveBudget(selectedBudget.id, approvalComments);
            setApprovalDialogOpen(false);
            setSelectedBudget(null);
            setApprovalComments('');
        }
    };

    const handleReject = (budget: DepartmentBudget) => {
        setSelectedBudget(budget);
        setRejectDialogOpen(true);
    };

    const handleConfirmReject = () => {
        if (selectedBudget && onRejectBudget && rejectComments.trim()) {
            onRejectBudget(selectedBudget.id, rejectComments);
            setRejectDialogOpen(false);
            setSelectedBudget(null);
            setRejectComments('');
        }
    };

    const handleSubmitForApproval = (budgetId: string) => {
        if (onSubmitForApproval) {
            onSubmitForApproval(budgetId);
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'approved':
                return 'success';
            case 'pending_approval':
                return 'warning';
            case 'rejected':
                return 'error';
            default:
                return 'default';
        }
    };

    const getCategoryColor = (category: string) => {
        switch (category) {
            case 'engineering':
                return 'primary';
            case 'sales':
                return 'secondary';
            case 'marketing':
                return 'info';
            case 'operations':
                return 'warning';
            case 'hr':
                return 'success';
            default:
                return 'default';
        }
    };

    const formatCurrency = (amount: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    const formatPercentage = (value: number) => {
        return `${value.toFixed(1)}%`;
    };

    const getSpentPercentage = (spent: number, allocated: number) => {
        return Math.min((spent / allocated) * 100, 100);
    };

    const getVariance = (actual: number, planned: number) => {
        return ((actual - planned) / planned) * 100;
    };

    const getVarianceColor = (variance: number) => {
        if (Math.abs(variance) < 5) return 'success';
        if (Math.abs(variance) < 10) return 'warning';
        return 'error';
    };

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Budget Planning FY{fiscalYear}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Manage annual budget allocation and approvals
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    <Button variant="outlined" startIcon={<AddIcon />}>
                        Add Department Budget
                    </Button>
                </Stack>
            </Stack>

            {/* Summary Cards */}
            <Grid container spacing={3} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={3}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Typography variant="caption" color="text.secondary">
                                Total Budget
                            </Typography>
                            <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                                {formatCurrency(totalBudget)}
                            </Typography>
                            <Typography variant="body2" color="text.secondary">
                                FY{fiscalYear}
                            </Typography>
                        </Box>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Typography variant="caption" color="text.secondary">
                                Allocated
                            </Typography>
                            <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                                {formatCurrency(totalAllocated)}
                            </Typography>
                            <Stack direction="row" alignItems="center" spacing={0.5}>
                                <Typography variant="body2" color="text.secondary">
                                    {formatPercentage((totalAllocated / totalBudget) * 100)} of budget
                                </Typography>
                            </Stack>
                        </Box>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Typography variant="caption" color="text.secondary">
                                Spent (YTD)
                            </Typography>
                            <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                                {formatCurrency(totalSpent)}
                            </Typography>
                            <Stack direction="row" alignItems="center" spacing={0.5}>
                                <Typography variant="body2" color="text.secondary">
                                    {formatPercentage((totalSpent / totalAllocated) * 100)} of allocated
                                </Typography>
                            </Stack>
                        </Box>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Typography variant="caption" color="text.secondary">
                                Forecast
                            </Typography>
                            <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                                {formatCurrency(totalForecast)}
                            </Typography>
                            <Stack direction="row" alignItems="center" spacing={0.5}>
                                {totalForecast > totalAllocated ? (
                                    <>
                                        <TrendingUpIcon fontSize="small" color="error" />
                                        <Typography variant="body2" color="error">
                                            {formatPercentage(getVariance(totalForecast, totalAllocated))} over
                                        </Typography>
                                    </>
                                ) : (
                                    <>
                                        <TrendingDownIcon fontSize="small" color="success" />
                                        <Typography variant="body2" color="success">
                                            {formatPercentage(Math.abs(getVariance(totalForecast, totalAllocated)))} under
                                        </Typography>
                                    </>
                                )}
                            </Stack>
                        </Box>
                    </Card>
                </Grid>
            </Grid>

            {/* Tabs */}
            <Card sx={{ mb: 3 }}>
                <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
                    <Tab label="By Department" />
                    <Tab label="By Category" />
                    <Tab label="Pending Approvals" />
                </Tabs>
            </Card>

            {/* Department View */}
            {activeTab === 0 && (
                <Card>
                    <TableContainer>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Department</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell align="right">Allocated</TableCell>
                                    <TableCell align="right">Spent (YTD)</TableCell>
                                    <TableCell align="right">Forecast</TableCell>
                                    <TableCell align="right">vs Last Year</TableCell>
                                    <TableCell align="right">Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {departmentBudgets.map((budget) => (
                                    <TableRow key={budget.id} hover>
                                        <TableCell>
                                            <Stack direction="row" alignItems="center" spacing={1}>
                                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                    {budget.departmentName}
                                                </Typography>
                                                <Chip
                                                    label={budget.category.toUpperCase()}
                                                    size="small"
                                                    color={getCategoryColor(budget.category) as any}
                                                    variant="outlined"
                                                />
                                            </Stack>
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                label={budget.status.replace('_', ' ').toUpperCase()}
                                                size="small"
                                                color={getStatusColor(budget.status) as any}
                                            />
                                        </TableCell>
                                        <TableCell align="right">
                                            <Box>
                                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                    {formatCurrency(budget.allocated)}
                                                </Typography>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={getSpentPercentage(budget.spent, budget.allocated)}
                                                    sx={{ mt: 0.5, width: 100 }}
                                                />
                                            </Box>
                                        </TableCell>
                                        <TableCell align="right">
                                            <Typography variant="body2">
                                                {formatCurrency(budget.spent)}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                                {formatPercentage(getSpentPercentage(budget.spent, budget.allocated))}
                                            </Typography>
                                        </TableCell>
                                        <TableCell align="right">
                                            <Typography variant="body2">
                                                {formatCurrency(budget.forecast)}
                                            </Typography>
                                            {budget.forecast > budget.allocated ? (
                                                <Stack direction="row" alignItems="center" spacing={0.5} justifyContent="flex-end">
                                                    <TrendingUpIcon fontSize="small" color="error" />
                                                    <Typography variant="caption" color="error">
                                                        {formatPercentage(getVariance(budget.forecast, budget.allocated))}
                                                    </Typography>
                                                </Stack>
                                            ) : (
                                                <Stack direction="row" alignItems="center" spacing={0.5} justifyContent="flex-end">
                                                    <TrendingDownIcon fontSize="small" color="success" />
                                                    <Typography variant="caption" color="success">
                                                        {formatPercentage(Math.abs(getVariance(budget.forecast, budget.allocated)))}
                                                    </Typography>
                                                </Stack>
                                            )}
                                        </TableCell>
                                        <TableCell align="right">
                                            {budget.lastYearSpent ? (
                                                <>
                                                    <Typography variant="body2">
                                                        {formatCurrency(budget.lastYearSpent)}
                                                    </Typography>
                                                    <Chip
                                                        label={`${getVariance(budget.allocated, budget.lastYearSpent) > 0 ? '+' : ''}${formatPercentage(getVariance(budget.allocated, budget.lastYearSpent))}`}
                                                        size="small"
                                                        color={getVarianceColor(getVariance(budget.allocated, budget.lastYearSpent)) as any}
                                                    />
                                                </>
                                            ) : (
                                                <Typography variant="caption" color="text.secondary">
                                                    N/A
                                                </Typography>
                                            )}
                                        </TableCell>
                                        <TableCell align="right">
                                            <Stack direction="row" spacing={1} justifyContent="flex-end">
                                                {budget.status === 'draft' && (
                                                    <>
                                                        <IconButton size="small" onClick={() => handleEditBudget(budget)}>
                                                            <EditIcon fontSize="small" />
                                                        </IconButton>
                                                        <Button
                                                            variant="text"
                                                            size="small"
                                                            onClick={() => handleSubmitForApproval(budget.id)}
                                                        >
                                                            Submit
                                                        </Button>
                                                    </>
                                                )}
                                                {budget.status === 'pending_approval' && (
                                                    <>
                                                        <IconButton
                                                            size="small"
                                                            color="success"
                                                            onClick={() => handleApprove(budget)}
                                                        >
                                                            <CheckIcon fontSize="small" />
                                                        </IconButton>
                                                        <IconButton
                                                            size="small"
                                                            color="error"
                                                            onClick={() => handleReject(budget)}
                                                        >
                                                            <CloseIcon fontSize="small" />
                                                        </IconButton>
                                                    </>
                                                )}
                                                {budget.status === 'approved' && (
                                                    <IconButton size="small" onClick={() => handleEditBudget(budget)}>
                                                        <EditIcon fontSize="small" />
                                                    </IconButton>
                                                )}
                                            </Stack>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Card>
            )}

            {/* Category View */}
            {activeTab === 1 && (
                <Grid container spacing={3}>
                    {budgetsByCategory.map((category) => (
                        <Grid item xs={12} sm={6} md={4} key={category.id}>
                            <Card>
                                <Box sx={{ p: 3 }}>
                                    <Stack spacing={2}>
                                        <Box>
                                            <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                                                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                                                    {category.name}
                                                </Typography>
                                                <Chip
                                                    label={category.id.toUpperCase()}
                                                    size="small"
                                                    color={getCategoryColor(category.id) as any}
                                                />
                                            </Stack>
                                        </Box>

                                        <Box>
                                            <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                                                <Typography variant="caption" color="text.secondary">
                                                    Allocated
                                                </Typography>
                                                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                                                    {formatCurrency(category.allocated)}
                                                </Typography>
                                            </Stack>
                                            <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.5 }}>
                                                <Typography variant="caption" color="text.secondary">
                                                    Spent
                                                </Typography>
                                                <Typography variant="caption">
                                                    {formatCurrency(category.spent)} ({formatPercentage((category.spent / category.allocated) * 100)})
                                                </Typography>
                                            </Stack>
                                            <Stack direction="row" justifyContent="space-between">
                                                <Typography variant="caption" color="text.secondary">
                                                    Forecast
                                                </Typography>
                                                <Typography variant="caption">
                                                    {formatCurrency(category.forecast)}
                                                </Typography>
                                            </Stack>
                                        </Box>

                                        <LinearProgress
                                            variant="determinate"
                                            value={getSpentPercentage(category.spent, category.allocated)}
                                            color={getCategoryColor(category.id) as any}
                                        />

                                        {category.forecast > category.allocated && (
                                            <Alert severity="warning" sx={{ py: 0.5 }}>
                                                <Typography variant="caption">
                                                    Forecast exceeds allocation by {formatCurrency(category.forecast - category.allocated)}
                                                </Typography>
                                            </Alert>
                                        )}
                                    </Stack>
                                </Box>
                            </Card>
                        </Grid>
                    ))}
                </Grid>
            )}

            {/* Pending Approvals View */}
            {activeTab === 2 && (
                <Card>
                    <TableContainer>
                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Department</TableCell>
                                    <TableCell align="right">Requested Amount</TableCell>
                                    <TableCell align="right">vs Last Year</TableCell>
                                    <TableCell>Submitted By</TableCell>
                                    <TableCell align="right">Actions</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {departmentBudgets
                                    .filter((b) => b.status === 'pending_approval')
                                    .map((budget) => (
                                        <TableRow key={budget.id} hover>
                                            <TableCell>
                                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                    {budget.departmentName}
                                                </Typography>
                                            </TableCell>
                                            <TableCell align="right">
                                                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                    {formatCurrency(budget.allocated)}
                                                </Typography>
                                            </TableCell>
                                            <TableCell align="right">
                                                {budget.lastYearSpent ? (
                                                    <Chip
                                                        label={`${getVariance(budget.allocated, budget.lastYearSpent) > 0 ? '+' : ''}${formatPercentage(getVariance(budget.allocated, budget.lastYearSpent))}`}
                                                        size="small"
                                                        color={getVarianceColor(getVariance(budget.allocated, budget.lastYearSpent)) as any}
                                                    />
                                                ) : (
                                                    <Typography variant="caption" color="text.secondary">
                                                        N/A
                                                    </Typography>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <Typography variant="body2">Department Manager</Typography>
                                            </TableCell>
                                            <TableCell align="right">
                                                <Stack direction="row" spacing={1} justifyContent="flex-end">
                                                    <Button
                                                        variant="contained"
                                                        size="small"
                                                        color="success"
                                                        startIcon={<CheckIcon />}
                                                        onClick={() => handleApprove(budget)}
                                                    >
                                                        Approve
                                                    </Button>
                                                    <Button
                                                        variant="outlined"
                                                        size="small"
                                                        color="error"
                                                        onClick={() => handleReject(budget)}
                                                    >
                                                        Reject
                                                    </Button>
                                                </Stack>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                            </TableBody>
                        </Table>
                    </TableContainer>
                    {departmentBudgets.filter((b) => b.status === 'pending_approval').length === 0 && (
                        <Box sx={{ p: 4, textAlign: 'center' }}>
                            <CheckIcon color="success" sx={{ fontSize: 48, mb: 2 }} />
                            <Typography variant="body1" color="text.secondary">
                                No pending budget approvals
                            </Typography>
                        </Box>
                    )}
                </Card>
            )}

            {/* Edit Budget Dialog */}
            <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)} maxWidth="sm" fullWidth>
                {selectedBudget && (
                    <>
                        <DialogTitle>
                            Edit Budget - {selectedBudget.departmentName}
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={3} sx={{ mt: 1 }}>
                                <TextField
                                    label="Budget Allocation"
                                    fullWidth
                                    type="number"
                                    value={editedAllocation}
                                    onChange={(e) => setEditedAllocation(Number(e.target.value))}
                                    InputProps={{
                                        startAdornment: <Typography sx={{ mr: 1 }}>$</Typography>,
                                    }}
                                />

                                <Alert severity="info">
                                    <Typography variant="body2">
                                        Current allocation: {formatCurrency(selectedBudget.allocated)}
                                    </Typography>
                                    {selectedBudget.lastYearSpent && (
                                        <Typography variant="body2">
                                            Last year: {formatCurrency(selectedBudget.lastYearSpent)}
                                        </Typography>
                                    )}
                                </Alert>
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
                            <Button onClick={handleSaveEdit} variant="contained">
                                Save Changes
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Approve Budget Dialog */}
            <Dialog open={approvalDialogOpen} onClose={() => setApprovalDialogOpen(false)} maxWidth="sm" fullWidth>
                {selectedBudget && (
                    <>
                        <DialogTitle>
                            Approve Budget - {selectedBudget.departmentName}
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={2}>
                                <Alert severity="success">
                                    <Typography variant="body2">
                                        You're about to approve a budget allocation of {formatCurrency(selectedBudget.allocated)} for{' '}
                                        {selectedBudget.departmentName}.
                                    </Typography>
                                </Alert>

                                <TextField
                                    label="Comments (optional)"
                                    fullWidth
                                    multiline
                                    rows={3}
                                    value={approvalComments}
                                    onChange={(e) => setApprovalComments(e.target.value)}
                                    placeholder="Add any notes or conditions..."
                                />
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setApprovalDialogOpen(false)}>Cancel</Button>
                            <Button onClick={handleConfirmApprove} variant="contained" color="success">
                                Approve Budget
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Reject Budget Dialog */}
            <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
                {selectedBudget && (
                    <>
                        <DialogTitle>
                            Reject Budget - {selectedBudget.departmentName}
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={2}>
                                <Alert severity="error">
                                    <Typography variant="body2">
                                        You're about to reject the budget request for {selectedBudget.departmentName}.
                                    </Typography>
                                </Alert>

                                <TextField
                                    label="Reason for Rejection"
                                    fullWidth
                                    required
                                    multiline
                                    rows={3}
                                    value={rejectComments}
                                    onChange={(e) => setRejectComments(e.target.value)}
                                    placeholder="Explain why this budget is being rejected..."
                                />
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setRejectDialogOpen(false)}>Cancel</Button>
                            <Button
                                onClick={handleConfirmReject}
                                variant="contained"
                                color="error"
                                disabled={!rejectComments.trim()}
                            >
                                Reject Budget
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>
        </Box>
    );
}
