import { useState, useMemo } from 'react';
import {
    Box,
    Card,
    Stack,
    Grid,
    Typography,
    Button,
    Chip,
    IconButton,
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
    Divider,
    TextField,
} from '@ghatana/ui';
import {
    Download as DownloadIcon,
    CreditCard as CreditCardIcon,
    CheckCircle as CheckCircleIcon,
    Warning as WarningIcon,
    TrendingUp as TrendingUpIcon,
    Receipt as ReceiptIcon,
    Close as CloseIcon,
} from '@ghatana/ui/icons';

/**
 * Subscription plan information
 */
export interface SubscriptionPlan {
    id: string;
    name: string;
    tier: 'free' | 'starter' | 'professional' | 'enterprise';
    price: number;
    billingCycle: 'monthly' | 'annual';
    features: string[];
    limits: {
        users: number;
        storage: number; // GB
        apiCalls: number;
    };
}

/**
 * Current subscription details
 */
export interface CurrentSubscription {
    plan: SubscriptionPlan;
    status: 'active' | 'trialing' | 'past_due' | 'canceled' | 'paused';
    currentPeriodStart: string;
    currentPeriodEnd: string;
    trialEndsAt?: string;
    canceledAt?: string;
    usage: {
        users: number;
        storage: number; // GB
        apiCalls: number;
    };
}

/**
 * Payment method information
 */
export interface PaymentMethod {
    id: string;
    type: 'card' | 'bank_account' | 'paypal';
    last4: string;
    brand?: string;
    expiryMonth?: number;
    expiryYear?: number;
    isDefault: boolean;
}

/**
 * Invoice information
 */
export interface Invoice {
    id: string;
    number: string;
    date: string;
    dueDate: string;
    amount: number;
    status: 'paid' | 'pending' | 'overdue' | 'failed';
    pdfUrl?: string;
    items: {
        description: string;
        quantity: number;
        unitPrice: number;
        amount: number;
    }[];
}

export interface BillingDashboardProps {
    currentSubscription?: CurrentSubscription;
    availablePlans?: SubscriptionPlan[];
    paymentMethods?: PaymentMethod[];
    invoices?: Invoice[];
    onUpgrade?: (planId: string) => void;
    onDowngrade?: (planId: string) => void;
    onCancelSubscription?: () => void;
    onAddPaymentMethod?: (method: PaymentMethod) => void;
    onSetDefaultPaymentMethod?: (methodId: string) => void;
    onDownloadInvoice?: (invoiceId: string) => void;
}

/**
 * Billing Dashboard Component
 *
 * Provides organization owners with:
 * - Current subscription overview
 * - Usage metrics vs limits
 * - Plan upgrade/downgrade flows
 * - Payment method management
 * - Invoice history and downloads
 */
export function BillingDashboard({
    currentSubscription: propSubscription,
    availablePlans: propPlans,
    paymentMethods: propPaymentMethods,
    invoices: propInvoices,
    onUpgrade,
    onDowngrade,
    onCancelSubscription,
    onAddPaymentMethod,
    onSetDefaultPaymentMethod,
    onDownloadInvoice,
}: BillingDashboardProps) {
    // Mock data for development
    const mockPlans: SubscriptionPlan[] = [
        {
            id: 'free',
            name: 'Free',
            tier: 'free',
            price: 0,
            billingCycle: 'monthly',
            features: [
                'Up to 10 users',
                '10 GB storage',
                '10,000 API calls/month',
                'Basic support',
            ],
            limits: {
                users: 10,
                storage: 10,
                apiCalls: 10000,
            },
        },
        {
            id: 'starter',
            name: 'Starter',
            tier: 'starter',
            price: 49,
            billingCycle: 'monthly',
            features: [
                'Up to 50 users',
                '50 GB storage',
                '100,000 API calls/month',
                'Email support',
                'Advanced analytics',
            ],
            limits: {
                users: 50,
                storage: 50,
                apiCalls: 100000,
            },
        },
        {
            id: 'professional',
            name: 'Professional',
            tier: 'professional',
            price: 199,
            billingCycle: 'monthly',
            features: [
                'Up to 250 users',
                '250 GB storage',
                '500,000 API calls/month',
                'Priority support',
                'Advanced analytics',
                'Custom integrations',
                'SSO',
            ],
            limits: {
                users: 250,
                storage: 250,
                apiCalls: 500000,
            },
        },
        {
            id: 'enterprise',
            name: 'Enterprise',
            tier: 'enterprise',
            price: 999,
            billingCycle: 'monthly',
            features: [
                'Unlimited users',
                '1 TB storage',
                '5,000,000 API calls/month',
                '24/7 dedicated support',
                'Advanced analytics',
                'Custom integrations',
                'SSO',
                'Custom SLA',
                'Dedicated account manager',
            ],
            limits: {
                users: 999999,
                storage: 1000,
                apiCalls: 5000000,
            },
        },
    ];

    const mockSubscription: CurrentSubscription = {
        plan: mockPlans[2],
        status: 'active',
        currentPeriodStart: '2025-11-11T00:00:00Z',
        currentPeriodEnd: '2025-12-11T00:00:00Z',
        usage: {
            users: 185,
            storage: 142,
            apiCalls: 325000,
        },
    };

    const mockPaymentMethods: PaymentMethod[] = [
        {
            id: 'pm-1',
            type: 'card',
            last4: '4242',
            brand: 'Visa',
            expiryMonth: 12,
            expiryYear: 2026,
            isDefault: true,
        },
        {
            id: 'pm-2',
            type: 'card',
            last4: '5555',
            brand: 'Mastercard',
            expiryMonth: 6,
            expiryYear: 2027,
            isDefault: false,
        },
    ];

    const mockInvoices: Invoice[] = [
        {
            id: 'inv-1',
            number: 'INV-2025-001',
            date: '2025-12-01T00:00:00Z',
            dueDate: '2025-12-11T00:00:00Z',
            amount: 199,
            status: 'paid',
            pdfUrl: '/invoices/inv-2025-001.pdf',
            items: [
                {
                    description: 'Professional Plan - December 2025',
                    quantity: 1,
                    unitPrice: 199,
                    amount: 199,
                },
            ],
        },
        {
            id: 'inv-2',
            number: 'INV-2025-002',
            date: '2025-11-01T00:00:00Z',
            dueDate: '2025-11-11T00:00:00Z',
            amount: 199,
            status: 'paid',
            pdfUrl: '/invoices/inv-2025-002.pdf',
            items: [
                {
                    description: 'Professional Plan - November 2025',
                    quantity: 1,
                    unitPrice: 199,
                    amount: 199,
                },
            ],
        },
        {
            id: 'inv-3',
            number: 'INV-2025-003',
            date: '2025-10-01T00:00:00Z',
            dueDate: '2025-10-11T00:00:00Z',
            amount: 199,
            status: 'paid',
            pdfUrl: '/invoices/inv-2025-003.pdf',
            items: [
                {
                    description: 'Professional Plan - October 2025',
                    quantity: 1,
                    unitPrice: 199,
                    amount: 199,
                },
            ],
        },
    ];

    const currentSubscription = propSubscription || mockSubscription;
    const availablePlans = propPlans || mockPlans;
    const paymentMethods = propPaymentMethods || mockPaymentMethods;
    const invoices = propInvoices || mockInvoices;

    const [upgradePlanId, setUpgradePlanId] = useState<string | null>(null);
    const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
    const [invoiceDialogOpen, setInvoiceDialogOpen] = useState(false);
    const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);

    const handleUpgradeClick = (planId: string) => {
        setUpgradePlanId(planId);
    };

    const handleConfirmUpgrade = () => {
        if (upgradePlanId && onUpgrade) {
            onUpgrade(upgradePlanId);
            setUpgradePlanId(null);
        }
    };

    const handleCancelUpgrade = () => {
        setUpgradePlanId(null);
    };

    const handleCancelSubscription = () => {
        if (onCancelSubscription) {
            onCancelSubscription();
            setCancelDialogOpen(false);
        }
    };

    const handleViewInvoice = (invoice: Invoice) => {
        setSelectedInvoice(invoice);
        setInvoiceDialogOpen(true);
    };

    const handleDownloadInvoice = (invoiceId: string) => {
        if (onDownloadInvoice) {
            onDownloadInvoice(invoiceId);
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active':
            case 'paid':
                return 'success';
            case 'trialing':
            case 'pending':
                return 'info';
            case 'past_due':
            case 'overdue':
                return 'warning';
            case 'canceled':
            case 'failed':
                return 'error';
            default:
                return 'default';
        }
    };

    const getTierColor = (tier: string) => {
        switch (tier) {
            case 'enterprise':
                return 'primary';
            case 'professional':
                return 'secondary';
            case 'starter':
                return 'info';
            default:
                return 'default';
        }
    };

    const formatDate = (dateString: string) => {
        return new Date(dateString).toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
        });
    };

    const formatCurrency = (amount: number) => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
        }).format(amount);
    };

    const getUsagePercentage = (used: number, limit: number) => {
        return Math.min((used / limit) * 100, 100);
    };

    const getUsageColor = (percentage: number) => {
        if (percentage >= 90) return 'error';
        if (percentage >= 70) return 'warning';
        return 'primary';
    };

    const upgradePlan = upgradePlanId ? availablePlans.find((p) => p.id === upgradePlanId) : null;

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Billing & Subscription
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Manage your subscription, payment methods, and invoices
                    </Typography>
                </Box>
            </Stack>

            {/* Current Subscription */}
            <Grid container spacing={3} sx={{ mb: 3 }}>
                <Grid item xs={12} md={8}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 3 }}>
                                <Box>
                                    <Stack direction="row" alignItems="center" spacing={2} sx={{ mb: 1 }}>
                                        <Typography variant="h6" sx={{ fontWeight: 600 }}>
                                            Current Plan: {currentSubscription.plan.name}
                                        </Typography>
                                        <Chip
                                            label={currentSubscription.status.toUpperCase()}
                                            size="small"
                                            color={getStatusColor(currentSubscription.status) as any}
                                        />
                                    </Stack>
                                    <Typography variant="body2" color="text.secondary">
                                        {formatCurrency(currentSubscription.plan.price)}/month •
                                        Renews {formatDate(currentSubscription.currentPeriodEnd)}
                                    </Typography>
                                </Box>
                                <Chip
                                    label={currentSubscription.plan.tier.toUpperCase()}
                                    color={getTierColor(currentSubscription.plan.tier) as any}
                                />
                            </Stack>

                            <Divider sx={{ mb: 3 }} />

                            <Typography variant="subtitle2" sx={{ mb: 2 }}>
                                Usage This Billing Cycle
                            </Typography>

                            <Stack spacing={3}>
                                {/* Users */}
                                <Box>
                                    <Stack direction="row" justifyContent="space-between" sx={{ mb: 1 }}>
                                        <Typography variant="body2">Users</Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {currentSubscription.usage.users} / {currentSubscription.plan.limits.users}
                                        </Typography>
                                    </Stack>
                                    <LinearProgress
                                        variant="determinate"
                                        value={getUsagePercentage(
                                            currentSubscription.usage.users,
                                            currentSubscription.plan.limits.users
                                        )}
                                        color={
                                            getUsageColor(
                                                getUsagePercentage(
                                                    currentSubscription.usage.users,
                                                    currentSubscription.plan.limits.users
                                                )
                                            ) as any
                                        }
                                    />
                                </Box>

                                {/* Storage */}
                                <Box>
                                    <Stack direction="row" justifyContent="space-between" sx={{ mb: 1 }}>
                                        <Typography variant="body2">Storage</Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {currentSubscription.usage.storage} GB / {currentSubscription.plan.limits.storage} GB
                                        </Typography>
                                    </Stack>
                                    <LinearProgress
                                        variant="determinate"
                                        value={getUsagePercentage(
                                            currentSubscription.usage.storage,
                                            currentSubscription.plan.limits.storage
                                        )}
                                        color={
                                            getUsageColor(
                                                getUsagePercentage(
                                                    currentSubscription.usage.storage,
                                                    currentSubscription.plan.limits.storage
                                                )
                                            ) as any
                                        }
                                    />
                                </Box>

                                {/* API Calls */}
                                <Box>
                                    <Stack direction="row" justifyContent="space-between" sx={{ mb: 1 }}>
                                        <Typography variant="body2">API Calls</Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {(currentSubscription.usage.apiCalls / 1000).toFixed(0)}K /{' '}
                                            {(currentSubscription.plan.limits.apiCalls / 1000).toFixed(0)}K
                                        </Typography>
                                    </Stack>
                                    <LinearProgress
                                        variant="determinate"
                                        value={getUsagePercentage(
                                            currentSubscription.usage.apiCalls,
                                            currentSubscription.plan.limits.apiCalls
                                        )}
                                        color={
                                            getUsageColor(
                                                getUsagePercentage(
                                                    currentSubscription.usage.apiCalls,
                                                    currentSubscription.plan.limits.apiCalls
                                                )
                                            ) as any
                                        }
                                    />
                                </Box>
                            </Stack>
                        </Box>
                    </Card>
                </Grid>

                {/* Quick Actions */}
                <Grid item xs={12} md={4}>
                    <Card>
                        <Box sx={{ p: 3 }}>
                            <Typography variant="subtitle2" sx={{ mb: 2 }}>
                                Quick Actions
                            </Typography>
                            <Stack spacing={2}>
                                <Button variant="outlined" fullWidth startIcon={<TrendingUpIcon />}>
                                    Upgrade Plan
                                </Button>
                                <Button variant="outlined" fullWidth startIcon={<CreditCardIcon />}>
                                    Update Payment Method
                                </Button>
                                <Button variant="outlined" fullWidth startIcon={<ReceiptIcon />}>
                                    View Invoices
                                </Button>
                                <Divider />
                                <Button
                                    variant="text"
                                    fullWidth
                                    color="error"
                                    onClick={() => setCancelDialogOpen(true)}
                                >
                                    Cancel Subscription
                                </Button>
                            </Stack>
                        </Box>
                    </Card>
                </Grid>
            </Grid>

            {/* Available Plans */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">Available Plans</Typography>
                </Box>
                <Box sx={{ p: 3 }}>
                    <Grid container spacing={3}>
                        {availablePlans.map((plan) => (
                            <Grid item xs={12} sm={6} md={3} key={plan.id}>
                                <Card
                                    variant="outlined"
                                    sx={{
                                        height: '100%',
                                        borderColor:
                                            plan.id === currentSubscription.plan.id ? 'primary.main' : 'divider',
                                        bgcolor:
                                            plan.id === currentSubscription.plan.id
                                                ? 'action.selected'
                                                : 'background.paper',
                                    }}
                                >
                                    <Box sx={{ p: 3 }}>
                                        <Stack spacing={2}>
                                            <Box>
                                                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                                                    {plan.name}
                                                </Typography>
                                                <Typography variant="h4" sx={{ fontWeight: 600, my: 1 }}>
                                                    {formatCurrency(plan.price)}
                                                </Typography>
                                                <Typography variant="caption" color="text.secondary">
                                                    per month
                                                </Typography>
                                            </Box>

                                            <Divider />

                                            <Stack spacing={1}>
                                                {plan.features.map((feature, index) => (
                                                    <Stack key={index} direction="row" spacing={1} alignItems="flex-start">
                                                        <CheckCircleIcon fontSize="small" color="success" sx={{ mt: 0.5 }} />
                                                        <Typography variant="body2">{feature}</Typography>
                                                    </Stack>
                                                ))}
                                            </Stack>

                                            {plan.id === currentSubscription.plan.id ? (
                                                <Chip label="Current Plan" color="primary" />
                                            ) : plan.price > currentSubscription.plan.price ? (
                                                <Button
                                                    variant="contained"
                                                    fullWidth
                                                    onClick={() => handleUpgradeClick(plan.id)}
                                                >
                                                    Upgrade
                                                </Button>
                                            ) : (
                                                <Button variant="outlined" fullWidth disabled>
                                                    Downgrade
                                                </Button>
                                            )}
                                        </Stack>
                                    </Box>
                                </Card>
                            </Grid>
                        ))}
                    </Grid>
                </Box>
            </Card>

            {/* Payment Methods */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography variant="h6">Payment Methods</Typography>
                        <Button variant="outlined" size="small">
                            Add Payment Method
                        </Button>
                    </Stack>
                </Box>
                <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
                    {paymentMethods.map((method) => (
                        <Box key={method.id} sx={{ p: 3 }}>
                            <Grid container alignItems="center" spacing={2}>
                                <Grid item xs>
                                    <Stack direction="row" alignItems="center" spacing={2}>
                                        <CreditCardIcon color="action" />
                                        <Box>
                                            <Typography variant="body1" sx={{ fontWeight: 600 }}>
                                                {method.brand} •••• {method.last4}
                                            </Typography>
                                            {method.expiryMonth && method.expiryYear && (
                                                <Typography variant="caption" color="text.secondary">
                                                    Expires {method.expiryMonth}/{method.expiryYear}
                                                </Typography>
                                            )}
                                        </Box>
                                        {method.isDefault && (
                                            <Chip label="Default" size="small" color="primary" />
                                        )}
                                    </Stack>
                                </Grid>
                                <Grid item>
                                    <Stack direction="row" spacing={1}>
                                        {!method.isDefault && (
                                            <Button
                                                variant="text"
                                                size="small"
                                                onClick={() => onSetDefaultPaymentMethod?.(method.id)}
                                            >
                                                Set as Default
                                            </Button>
                                        )}
                                        <Button variant="text" size="small" color="error">
                                            Remove
                                        </Button>
                                    </Stack>
                                </Grid>
                            </Grid>
                        </Box>
                    ))}
                </Stack>
            </Card>

            {/* Invoice History */}
            <Card>
                <Box sx={{ p: 3, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">Invoice History</Typography>
                </Box>
                <TableContainer>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableCell>Invoice</TableCell>
                                <TableCell>Date</TableCell>
                                <TableCell>Amount</TableCell>
                                <TableCell>Status</TableCell>
                                <TableCell align="right">Actions</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {invoices.map((invoice) => (
                                <TableRow key={invoice.id} hover>
                                    <TableCell>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {invoice.number}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>{formatDate(invoice.date)}</TableCell>
                                    <TableCell>{formatCurrency(invoice.amount)}</TableCell>
                                    <TableCell>
                                        <Chip
                                            label={invoice.status.toUpperCase()}
                                            size="small"
                                            color={getStatusColor(invoice.status) as any}
                                        />
                                    </TableCell>
                                    <TableCell align="right">
                                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                                            <Button
                                                variant="text"
                                                size="small"
                                                onClick={() => handleViewInvoice(invoice)}
                                            >
                                                View
                                            </Button>
                                            <IconButton
                                                size="small"
                                                onClick={() => handleDownloadInvoice(invoice.id)}
                                            >
                                                <DownloadIcon fontSize="small" />
                                            </IconButton>
                                        </Stack>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Card>

            {/* Upgrade Confirmation Dialog */}
            <Dialog open={!!upgradePlanId} onClose={handleCancelUpgrade} maxWidth="sm" fullWidth>
                {upgradePlan && (
                    <>
                        <DialogTitle>
                            Upgrade to {upgradePlan.name}
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={2}>
                                <Alert severity="info">
                                    You'll be charged {formatCurrency(upgradePlan.price)} starting from your next billing cycle.
                                </Alert>

                                <Box>
                                    <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                        New Plan Features:
                                    </Typography>
                                    <Stack spacing={0.5}>
                                        {upgradePlan.features.map((feature, index) => (
                                            <Stack key={index} direction="row" spacing={1} alignItems="flex-start">
                                                <CheckCircleIcon fontSize="small" color="success" sx={{ mt: 0.5 }} />
                                                <Typography variant="body2">{feature}</Typography>
                                            </Stack>
                                        ))}
                                    </Stack>
                                </Box>

                                <Divider />

                                <Stack spacing={1}>
                                    <Stack direction="row" justifyContent="space-between">
                                        <Typography variant="body2">Current Plan</Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {formatCurrency(currentSubscription.plan.price)}/month
                                        </Typography>
                                    </Stack>
                                    <Stack direction="row" justifyContent="space-between">
                                        <Typography variant="body2">New Plan</Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {formatCurrency(upgradePlan.price)}/month
                                        </Typography>
                                    </Stack>
                                    <Stack direction="row" justifyContent="space-between">
                                        <Typography variant="body2" color="text.secondary">
                                            Difference
                                        </Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }} color="primary">
                                            +{formatCurrency(upgradePlan.price - currentSubscription.plan.price)}/month
                                        </Typography>
                                    </Stack>
                                </Stack>
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={handleCancelUpgrade}>Cancel</Button>
                            <Button onClick={handleConfirmUpgrade} variant="contained">
                                Confirm Upgrade
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>

            {/* Cancel Subscription Dialog */}
            <Dialog open={cancelDialogOpen} onClose={() => setCancelDialogOpen(false)} maxWidth="sm" fullWidth>
                <DialogTitle>Cancel Subscription</DialogTitle>
                <DialogContent>
                    <Stack spacing={2}>
                        <Alert severity="warning">
                            <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                                Are you sure you want to cancel?
                            </Typography>
                            <Typography variant="body2">
                                Your subscription will remain active until {formatDate(currentSubscription.currentPeriodEnd)}.
                                After that, you'll lose access to:
                            </Typography>
                            <Stack spacing={0.5} sx={{ mt: 1 }}>
                                {currentSubscription.plan.features.map((feature, index) => (
                                    <Typography key={index} variant="body2">
                                        • {feature}
                                    </Typography>
                                ))}
                            </Stack>
                        </Alert>

                        <TextField
                            label="Reason for cancellation (optional)"
                            fullWidth
                            multiline
                            rows={3}
                            placeholder="Help us improve by sharing why you're canceling..."
                        />
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCancelDialogOpen(false)}>Keep Subscription</Button>
                    <Button onClick={handleCancelSubscription} variant="contained" color="error">
                        Cancel Subscription
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Invoice Detail Dialog */}
            <Dialog
                open={invoiceDialogOpen}
                onClose={() => setInvoiceDialogOpen(false)}
                maxWidth="md"
                fullWidth
            >
                {selectedInvoice && (
                    <>
                        <DialogTitle>
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Typography variant="h6">Invoice {selectedInvoice.number}</Typography>
                                <IconButton onClick={() => setInvoiceDialogOpen(false)}>
                                    <CloseIcon />
                                </IconButton>
                            </Stack>
                        </DialogTitle>
                        <DialogContent>
                            <Stack spacing={3}>
                                <Grid container spacing={2}>
                                    <Grid item xs={6}>
                                        <Typography variant="caption" color="text.secondary">
                                            Invoice Date
                                        </Typography>
                                        <Typography variant="body2">{formatDate(selectedInvoice.date)}</Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography variant="caption" color="text.secondary">
                                            Due Date
                                        </Typography>
                                        <Typography variant="body2">{formatDate(selectedInvoice.dueDate)}</Typography>
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography variant="caption" color="text.secondary">
                                            Status
                                        </Typography>
                                        <Chip
                                            label={selectedInvoice.status.toUpperCase()}
                                            size="small"
                                            color={getStatusColor(selectedInvoice.status) as any}
                                        />
                                    </Grid>
                                    <Grid item xs={6}>
                                        <Typography variant="caption" color="text.secondary">
                                            Total Amount
                                        </Typography>
                                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                            {formatCurrency(selectedInvoice.amount)}
                                        </Typography>
                                    </Grid>
                                </Grid>

                                <Divider />

                                <TableContainer>
                                    <Table size="small">
                                        <TableHead>
                                            <TableRow>
                                                <TableCell>Description</TableCell>
                                                <TableCell align="right">Qty</TableCell>
                                                <TableCell align="right">Unit Price</TableCell>
                                                <TableCell align="right">Amount</TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {selectedInvoice.items.map((item, index) => (
                                                <TableRow key={index}>
                                                    <TableCell>{item.description}</TableCell>
                                                    <TableCell align="right">{item.quantity}</TableCell>
                                                    <TableCell align="right">{formatCurrency(item.unitPrice)}</TableCell>
                                                    <TableCell align="right">{formatCurrency(item.amount)}</TableCell>
                                                </TableRow>
                                            ))}
                                            <TableRow>
                                                <TableCell colSpan={3} align="right" sx={{ fontWeight: 600 }}>
                                                    Total
                                                </TableCell>
                                                <TableCell align="right" sx={{ fontWeight: 600 }}>
                                                    {formatCurrency(selectedInvoice.amount)}
                                                </TableCell>
                                            </TableRow>
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </Stack>
                        </DialogContent>
                        <DialogActions>
                            <Button onClick={() => setInvoiceDialogOpen(false)}>Close</Button>
                            <Button
                                variant="contained"
                                startIcon={<DownloadIcon />}
                                onClick={() => handleDownloadInvoice(selectedInvoice.id)}
                            >
                                Download PDF
                            </Button>
                        </DialogActions>
                    </>
                )}
            </Dialog>
        </Box>
    );
}
