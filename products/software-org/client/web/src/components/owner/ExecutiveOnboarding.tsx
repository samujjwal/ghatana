import { useState } from 'react';
import {
    Box,
    Card,
    Stack,
    Grid,
    Typography,
    TextField,
    Button,
    Stepper,
    Step,
    StepLabel,
    Alert,
    Chip,
    IconButton,
    LinearProgress,
    Avatar,
    Divider,
} from '@ghatana/design-system';
import {
    CheckCircle as CheckCircleIcon,
    Close as CloseIcon,
    Business as BusinessIcon,
    People as PeopleIcon,
    IntegrationInstructions as IntegrationIcon,
    Celebration as CelebrationIcon,
    Add as AddIcon,
    Delete as DeleteIcon,
} from '@ghatana/design-system/icons';

/**
 * Onboarding step configuration
 */
export interface OnboardingStep {
    id: string;
    label: string;
    description: string;
    optional?: boolean;
}

/**
 * Organization information for setup
 */
export interface OrganizationInfo {
    name: string;
    slug: string;
    industry: string;
    size: string;
    timezone: string;
    fiscalYearStart: string;
}

/**
 * Team member invitation
 */
export interface TeamInvitation {
    id: string;
    email: string;
    role: 'Owner' | 'Admin' | 'Manager' | 'IC';
    department?: string;
}

/**
 * Integration configuration
 */
export interface IntegrationConfig {
    id: string;
    name: string;
    type: 'slack' | 'github' | 'jira' | 'google' | 'microsoft';
    enabled: boolean;
    configured: boolean;
}

export interface ExecutiveOnboardingProps {
    currentUser?: {
        name: string;
        email: string;
    };
    onComplete?: (data: {
        organization: OrganizationInfo;
        invitations: TeamInvitation[];
        integrations: IntegrationConfig[];
    }) => void;
    onSkip?: () => void;
}

/**
 * Executive Onboarding Component
 *
 * Multi-step wizard for new organization owners:
 * - Welcome and introduction
 * - Organization setup and configuration
 * - Team member invitation
 * - Integration setup (optional)
 * - Completion and next steps
 */
export function ExecutiveOnboarding({
    currentUser = {
        name: 'John Smith',
        email: 'john.smith@acme.com',
    },
    onComplete,
    onSkip,
}: ExecutiveOnboardingProps) {
    const steps: OnboardingStep[] = [
        {
            id: 'welcome',
            label: 'Welcome',
            description: 'Get started with your organization',
        },
        {
            id: 'organization',
            label: 'Organization',
            description: 'Set up your organization details',
        },
        {
            id: 'team',
            label: 'Invite Team',
            description: 'Invite your team members',
            optional: true,
        },
        {
            id: 'integrations',
            label: 'Integrations',
            description: 'Connect your tools',
            optional: true,
        },
        {
            id: 'complete',
            label: 'Complete',
            description: 'You\'re all set!',
        },
    ];

    const [activeStep, setActiveStep] = useState(0);
    const [organization, setOrganization] = useState<OrganizationInfo>({
        name: '',
        slug: '',
        industry: '',
        size: '',
        timezone: 'America/New_York',
        fiscalYearStart: 'January',
    });

    const [invitations, setInvitations] = useState<TeamInvitation[]>([
        {
            id: '1',
            email: '',
            role: 'Admin',
            department: '',
        },
    ]);

    const [integrations, setIntegrations] = useState<IntegrationConfig[]>([
        {
            id: 'slack',
            name: 'Slack',
            type: 'slack',
            enabled: false,
            configured: false,
        },
        {
            id: 'github',
            name: 'GitHub',
            type: 'github',
            enabled: false,
            configured: false,
        },
        {
            id: 'jira',
            name: 'Jira',
            type: 'jira',
            enabled: false,
            configured: false,
        },
        {
            id: 'google',
            name: 'Google Workspace',
            type: 'google',
            enabled: false,
            configured: false,
        },
        {
            id: 'microsoft',
            name: 'Microsoft 365',
            type: 'microsoft',
            enabled: false,
            configured: false,
        },
    ]);

    const [errors, setErrors] = useState<Record<string, string>>({});

    const handleNext = () => {
        if (validateStep()) {
            setActiveStep((prev) => prev + 1);
            setErrors({});
        }
    };

    const handleBack = () => {
        setActiveStep((prev) => prev - 1);
        setErrors({});
    };

    const handleSkipStep = () => {
        setActiveStep((prev) => prev + 1);
        setErrors({});
    };

    const handleComplete = () => {
        if (onComplete) {
            onComplete({
                organization,
                invitations: invitations.filter((inv) => inv.email.trim() !== ''),
                integrations: integrations.filter((int) => int.enabled),
            });
        }
    };

    const validateStep = (): boolean => {
        const newErrors: Record<string, string> = {};

        if (activeStep === 1) {
            // Organization step
            if (!organization.name.trim()) {
                newErrors.name = 'Organization name is required';
            }
            if (!organization.slug.trim()) {
                newErrors.slug = 'Organization slug is required';
            } else if (!/^[a-z0-9-]+$/.test(organization.slug)) {
                newErrors.slug = 'Slug must be lowercase letters, numbers, and hyphens only';
            }
            if (!organization.industry.trim()) {
                newErrors.industry = 'Industry is required';
            }
            if (!organization.size.trim()) {
                newErrors.size = 'Organization size is required';
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleOrganizationChange = (field: keyof OrganizationInfo, value: string) => {
        setOrganization((prev) => ({ ...prev, [field]: value }));

        // Auto-generate slug from name
        if (field === 'name') {
            const slug = value
                .toLowerCase()
                .replace(/[^a-z0-9\s-]/g, '')
                .replace(/\s+/g, '-')
                .replace(/-+/g, '-')
                .replace(/^-|-$/g, '');
            setOrganization((prev) => ({ ...prev, slug }));
        }
    };

    const handleAddInvitation = () => {
        const newId = String(invitations.length + 1);
        setInvitations((prev) => [
            ...prev,
            {
                id: newId,
                email: '',
                role: 'IC',
                department: '',
            },
        ]);
    };

    const handleRemoveInvitation = (id: string) => {
        setInvitations((prev) => prev.filter((inv) => inv.id !== id));
    };

    const handleInvitationChange = (
        id: string,
        field: keyof TeamInvitation,
        value: string
    ) => {
        setInvitations((prev) =>
            prev.map((inv) => (inv.id === id ? { ...inv, [field]: value } : inv))
        );
    };

    const handleToggleIntegration = (id: string) => {
        setIntegrations((prev) =>
            prev.map((int) =>
                int.id === id
                    ? { ...int, enabled: !int.enabled, configured: !int.enabled }
                    : int
            )
        );
    };

    const getStepProgress = () => {
        return ((activeStep + 1) / steps.length) * 100;
    };

    return (
        <Box sx={{ p: 3, maxWidth: 1200, mx: 'auto' }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Welcome to Your Organization
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        Let's get you set up in just a few steps
                    </Typography>
                </Box>
                {onSkip && activeStep < steps.length - 1 && (
                    <Button variant="text" onClick={onSkip}>
                        Skip Setup
                    </Button>
                )}
            </Stack>

            {/* Progress Bar */}
            <Box sx={{ mb: 3 }}>
                <LinearProgress variant="determinate" value={getStepProgress()} sx={{ mb: 1 }} />
                <Typography variant="caption" color="text.secondary">
                    Step {activeStep + 1} of {steps.length}
                </Typography>
            </Box>

            {/* Stepper */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 3 }}>
                    <Stepper activeStep={activeStep}>
                        {steps.map((step) => (
                            <Step key={step.id}>
                                <StepLabel
                                    optional={
                                        step.optional ? (
                                            <Typography variant="caption">Optional</Typography>
                                        ) : null
                                    }
                                >
                                    {step.label}
                                </StepLabel>
                            </Step>
                        ))}
                    </Stepper>
                </Box>
            </Card>

            {/* Step Content */}
            <Card>
                <Box sx={{ p: 3 }}>
                    {/* Step 0: Welcome */}
                    {activeStep === 0 && (
                        <Stack spacing={3} alignItems="center" sx={{ textAlign: 'center', py: 4 }}>
                            <Avatar sx={{ width: 80, height: 80, bgcolor: 'primary.main', fontSize: 36 }}>
                                {currentUser.name.split(' ').map((n) => n[0]).join('')}
                            </Avatar>
                            <Box>
                                <Typography variant="h5" sx={{ fontWeight: 600, mb: 1 }}>
                                    Welcome, {currentUser.name}!
                                </Typography>
                                <Typography variant="body1" color="text.secondary">
                                    {currentUser.email}
                                </Typography>
                            </Box>
                            <Alert severity="info" sx={{ maxWidth: 600 }}>
                                <Typography variant="body2">
                                    You're about to create your organization on our platform. This wizard will guide you through:
                                </Typography>
                                <Stack spacing={0.5} sx={{ mt: 2 }}>
                                    <Typography variant="body2">• Setting up your organization details</Typography>
                                    <Typography variant="body2">• Inviting your team members</Typography>
                                    <Typography variant="body2">• Connecting your favorite tools</Typography>
                                    <Typography variant="body2">• Getting started with your first projects</Typography>
                                </Stack>
                            </Alert>
                            <Box>
                                <Typography variant="body2" color="text.secondary">
                                    This should take about 5 minutes to complete.
                                </Typography>
                            </Box>
                        </Stack>
                    )}

                    {/* Step 1: Organization Setup */}
                    {activeStep === 1 && (
                        <Stack spacing={3}>
                            <Box>
                                <Typography variant="h6" sx={{ mb: 1, fontWeight: 600 }}>
                                    Organization Details
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Tell us about your organization
                                </Typography>
                            </Box>

                            <Grid container spacing={3}>
                                <Grid item xs={12}>
                                    <TextField
                                        label="Organization Name"
                                        fullWidth
                                        required
                                        value={organization.name}
                                        onChange={(e) => handleOrganizationChange('name', e.target.value)}
                                        error={!!errors.name}
                                        helperText={errors.name || 'This will be visible to your team'}
                                        placeholder="Acme Corporation"
                                    />
                                </Grid>

                                <Grid item xs={12}>
                                    <TextField
                                        label="Organization Slug"
                                        fullWidth
                                        required
                                        value={organization.slug}
                                        onChange={(e) => handleOrganizationChange('slug', e.target.value)}
                                        error={!!errors.slug}
                                        helperText={
                                            errors.slug ||
                                            `Your organization URL: app.example.com/${organization.slug || 'your-org'}`
                                        }
                                        placeholder="acme-corp"
                                    />
                                </Grid>

                                <Grid item xs={12} sm={6}>
                                    <TextField
                                        select
                                        label="Industry"
                                        fullWidth
                                        required
                                        value={organization.industry}
                                        onChange={(e) => handleOrganizationChange('industry', e.target.value)}
                                        error={!!errors.industry}
                                        helperText={errors.industry}
                                        SelectProps={{
                                            native: true,
                                        }}
                                    >
                                        <option value="">Select Industry</option>
                                        <option value="technology">Technology</option>
                                        <option value="finance">Finance</option>
                                        <option value="healthcare">Healthcare</option>
                                        <option value="education">Education</option>
                                        <option value="retail">Retail</option>
                                        <option value="manufacturing">Manufacturing</option>
                                        <option value="other">Other</option>
                                    </TextField>
                                </Grid>

                                <Grid item xs={12} sm={6}>
                                    <TextField
                                        select
                                        label="Organization Size"
                                        fullWidth
                                        required
                                        value={organization.size}
                                        onChange={(e) => handleOrganizationChange('size', e.target.value)}
                                        error={!!errors.size}
                                        helperText={errors.size}
                                        SelectProps={{
                                            native: true,
                                        }}
                                    >
                                        <option value="">Select Size</option>
                                        <option value="1-10">1-10 employees</option>
                                        <option value="11-50">11-50 employees</option>
                                        <option value="51-200">51-200 employees</option>
                                        <option value="201-500">201-500 employees</option>
                                        <option value="501-1000">501-1000 employees</option>
                                        <option value="1001+">1001+ employees</option>
                                    </TextField>
                                </Grid>

                                <Grid item xs={12} sm={6}>
                                    <TextField
                                        select
                                        label="Timezone"
                                        fullWidth
                                        value={organization.timezone}
                                        onChange={(e) => handleOrganizationChange('timezone', e.target.value)}
                                        SelectProps={{
                                            native: true,
                                        }}
                                    >
                                        <option value="America/New_York">Eastern Time (ET)</option>
                                        <option value="America/Chicago">Central Time (CT)</option>
                                        <option value="America/Denver">Mountain Time (MT)</option>
                                        <option value="America/Los_Angeles">Pacific Time (PT)</option>
                                        <option value="Europe/London">London (GMT)</option>
                                        <option value="Europe/Paris">Paris (CET)</option>
                                        <option value="Asia/Tokyo">Tokyo (JST)</option>
                                        <option value="Asia/Singapore">Singapore (SGT)</option>
                                    </TextField>
                                </Grid>

                                <Grid item xs={12} sm={6}>
                                    <TextField
                                        select
                                        label="Fiscal Year Start"
                                        fullWidth
                                        value={organization.fiscalYearStart}
                                        onChange={(e) => handleOrganizationChange('fiscalYearStart', e.target.value)}
                                        SelectProps={{
                                            native: true,
                                        }}
                                    >
                                        <option value="January">January</option>
                                        <option value="February">February</option>
                                        <option value="March">March</option>
                                        <option value="April">April</option>
                                        <option value="May">May</option>
                                        <option value="June">June</option>
                                        <option value="July">July</option>
                                        <option value="August">August</option>
                                        <option value="September">September</option>
                                        <option value="October">October</option>
                                        <option value="November">November</option>
                                        <option value="December">December</option>
                                    </TextField>
                                </Grid>
                            </Grid>
                        </Stack>
                    )}

                    {/* Step 2: Team Invitations */}
                    {activeStep === 2 && (
                        <Stack spacing={3}>
                            <Box>
                                <Typography variant="h6" sx={{ mb: 1, fontWeight: 600 }}>
                                    Invite Your Team
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Send invitations to your team members (you can also do this later)
                                </Typography>
                            </Box>

                            <Stack spacing={2}>
                                {invitations.map((invitation, index) => (
                                    <Card key={invitation.id} variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Stack direction="row" alignItems="flex-start" spacing={2}>
                                                <Avatar sx={{ width: 40, height: 40, bgcolor: 'primary.main' }}>
                                                    {index + 1}
                                                </Avatar>
                                                <Grid container spacing={2} sx={{ flex: 1 }}>
                                                    <Grid item xs={12} sm={6}>
                                                        <TextField
                                                            label="Email Address"
                                                            fullWidth
                                                            type="email"
                                                            value={invitation.email}
                                                            onChange={(e) =>
                                                                handleInvitationChange(invitation.id, 'email', e.target.value)
                                                            }
                                                            placeholder="colleague@example.com"
                                                        />
                                                    </Grid>
                                                    <Grid item xs={12} sm={3}>
                                                        <TextField
                                                            select
                                                            label="Role"
                                                            fullWidth
                                                            value={invitation.role}
                                                            onChange={(e) =>
                                                                handleInvitationChange(invitation.id, 'role', e.target.value)
                                                            }
                                                            SelectProps={{
                                                                native: true,
                                                            }}
                                                        >
                                                            <option value="Owner">Owner</option>
                                                            <option value="Admin">Admin</option>
                                                            <option value="Manager">Manager</option>
                                                            <option value="IC">IC</option>
                                                        </TextField>
                                                    </Grid>
                                                    <Grid item xs={12} sm={3}>
                                                        <TextField
                                                            label="Department"
                                                            fullWidth
                                                            value={invitation.department}
                                                            onChange={(e) =>
                                                                handleInvitationChange(invitation.id, 'department', e.target.value)
                                                            }
                                                            placeholder="Engineering"
                                                        />
                                                    </Grid>
                                                </Grid>
                                                {invitations.length > 1 && (
                                                    <IconButton
                                                        onClick={() => handleRemoveInvitation(invitation.id)}
                                                        size="small"
                                                    >
                                                        <DeleteIcon />
                                                    </IconButton>
                                                )}
                                            </Stack>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>

                            <Button
                                variant="outlined"
                                startIcon={<AddIcon />}
                                onClick={handleAddInvitation}
                                sx={{ alignSelf: 'flex-start' }}
                            >
                                Add Another Team Member
                            </Button>

                            <Alert severity="info">
                                <Typography variant="body2">
                                    Team members will receive an email invitation to join your organization. They'll be able to
                                    access the platform once they accept the invitation.
                                </Typography>
                            </Alert>
                        </Stack>
                    )}

                    {/* Step 3: Integrations */}
                    {activeStep === 3 && (
                        <Stack spacing={3}>
                            <Box>
                                <Typography variant="h6" sx={{ mb: 1, fontWeight: 600 }}>
                                    Connect Your Tools
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                    Integrate with your existing workflow tools (optional)
                                </Typography>
                            </Box>

                            <Grid container spacing={2}>
                                {integrations.map((integration) => (
                                    <Grid item xs={12} sm={6} key={integration.id}>
                                        <Card
                                            variant="outlined"
                                            sx={{
                                                cursor: 'pointer',
                                                borderColor: integration.enabled ? 'primary.main' : 'divider',
                                                bgcolor: integration.enabled ? 'action.selected' : 'background.paper',
                                                '&:hover': {
                                                    borderColor: 'primary.main',
                                                    bgcolor: 'action.hover',
                                                },
                                            }}
                                            onClick={() => handleToggleIntegration(integration.id)}
                                        >
                                            <Box sx={{ p: 2 }}>
                                                <Stack direction="row" alignItems="center" spacing={2}>
                                                    <Avatar sx={{ bgcolor: integration.enabled ? 'primary.main' : 'action.disabled' }}>
                                                        <IntegrationIcon />
                                                    </Avatar>
                                                    <Box sx={{ flex: 1 }}>
                                                        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                                                            {integration.name}
                                                        </Typography>
                                                        <Typography variant="caption" color="text.secondary">
                                                            {integration.enabled ? 'Will be configured' : 'Click to enable'}
                                                        </Typography>
                                                    </Box>
                                                    {integration.enabled && (
                                                        <CheckCircleIcon color="primary" />
                                                    )}
                                                </Stack>
                                            </Box>
                                        </Card>
                                    </Grid>
                                ))}
                            </Grid>

                            <Alert severity="info">
                                <Typography variant="body2">
                                    Selected integrations will be set up after you complete the onboarding. You can configure
                                    additional integrations anytime from your settings.
                                </Typography>
                            </Alert>
                        </Stack>
                    )}

                    {/* Step 4: Complete */}
                    {activeStep === 4 && (
                        <Stack spacing={3} alignItems="center" sx={{ textAlign: 'center', py: 4 }}>
                            <CelebrationIcon color="primary" sx={{ fontSize: 80 }} />
                            <Box>
                                <Typography variant="h5" sx={{ fontWeight: 600, mb: 1 }}>
                                    You're All Set!
                                </Typography>
                                <Typography variant="body1" color="text.secondary">
                                    Your organization <strong>{organization.name}</strong> is ready to go.
                                </Typography>
                            </Box>

                            <Card variant="outlined" sx={{ maxWidth: 600, width: '100%' }}>
                                <Box sx={{ p: 3 }}>
                                    <Typography variant="subtitle2" sx={{ mb: 2 }}>
                                        Setup Summary
                                    </Typography>
                                    <Stack spacing={2} divider={<Divider />}>
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="body2" color="text.secondary">
                                                Organization
                                            </Typography>
                                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                {organization.name}
                                            </Typography>
                                        </Stack>
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="body2" color="text.secondary">
                                                URL
                                            </Typography>
                                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                app.example.com/{organization.slug}
                                            </Typography>
                                        </Stack>
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="body2" color="text.secondary">
                                                Team Invitations
                                            </Typography>
                                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                {invitations.filter((inv) => inv.email.trim() !== '').length} member(s)
                                            </Typography>
                                        </Stack>
                                        <Stack direction="row" justifyContent="space-between">
                                            <Typography variant="body2" color="text.secondary">
                                                Integrations
                                            </Typography>
                                            <Typography variant="body2" sx={{ fontWeight: 600 }}>
                                                {integrations.filter((int) => int.enabled).length} enabled
                                            </Typography>
                                        </Stack>
                                    </Stack>
                                </Box>
                            </Card>

                            <Alert severity="success" sx={{ maxWidth: 600 }}>
                                <Typography variant="body2" sx={{ fontWeight: 600, mb: 1 }}>
                                    What's Next?
                                </Typography>
                                <Stack spacing={0.5}>
                                    <Typography variant="body2">• Explore your dashboard</Typography>
                                    <Typography variant="body2">• Create your first department</Typography>
                                    <Typography variant="body2">• Set up your organization hierarchy</Typography>
                                    <Typography variant="body2">• Configure billing and subscription</Typography>
                                </Stack>
                            </Alert>
                        </Stack>
                    )}
                </Box>

                {/* Actions */}
                <Box sx={{ p: 3, borderTop: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
                    <Stack direction="row" justifyContent="space-between">
                        <Button
                            onClick={handleBack}
                            disabled={activeStep === 0}
                            variant="outlined"
                        >
                            Back
                        </Button>
                        <Stack direction="row" spacing={2}>
                            {activeStep > 0 && activeStep < steps.length - 1 && steps[activeStep].optional && (
                                <Button onClick={handleSkipStep} variant="text">
                                    Skip
                                </Button>
                            )}
                            {activeStep === steps.length - 1 ? (
                                <Button onClick={handleComplete} variant="contained">
                                    Go to Dashboard
                                </Button>
                            ) : (
                                <Button onClick={handleNext} variant="contained">
                                    {activeStep === 0 ? 'Get Started' : 'Continue'}
                                </Button>
                            )}
                        </Stack>
                    </Stack>
                </Box>
            </Card>
        </Box>
    );
}
