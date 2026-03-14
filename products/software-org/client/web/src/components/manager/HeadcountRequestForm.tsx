import React, { useState } from 'react';
import {
    Box,
    Card,
    Button,
    Stack,
    Typography,
    TextField,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Grid,
    Chip,
    Alert,
    RadioGroup,
    FormControlLabel,
    Radio,
    Divider,
} from '@ghatana/design-system';

/**
 * Team utilization metrics
 */
export interface TeamUtilizationMetrics {
    currentTeamSize: number;
    averageUtilization: number; // Percentage
    overallocatedMembers: number;
    underutilizedMembers: number;
    upcomingProjects: number;
    projectedCapacityGap: number; // Percentage
}

/**
 * Headcount request form data
 */
export interface HeadcountRequestFormData {
    numberOfHeadcount: number;
    role: string;
    seniority: 'junior' | 'mid' | 'senior' | 'staff' | 'principal';
    startDate: string;
    duration: 'permanent' | 'contract';
    contractMonths?: number;
    justification: string;
    businessImpact: string;
    estimatedSalary: number;
    recruitingUrgency: 'low' | 'medium' | 'high' | 'critical';
}

/**
 * Props for HeadcountRequestForm
 */
interface HeadcountRequestFormProps {
    teamMetrics?: TeamUtilizationMetrics;
    onSubmit?: (data: HeadcountRequestFormData) => void;
    onCancel?: () => void;
}

/**
 * Headcount Request Form
 *
 * Form for requesting additional team members:
 * - Auto-filled utilization data
 * - Role and seniority selection
 * - Justification builder with templates
 * - Budget impact preview
 * - Urgency level
 * - Creates approval request
 */
export const HeadcountRequestForm: React.FC<HeadcountRequestFormProps> = ({
    teamMetrics: initialTeamMetrics,
    onSubmit,
    onCancel,
}) => {
    // Mock metrics if none provided
    const mockTeamMetrics: TeamUtilizationMetrics = initialTeamMetrics || {
        currentTeamSize: 8,
        averageUtilization: 92,
        overallocatedMembers: 3,
        underutilizedMembers: 0,
        upcomingProjects: 2,
        projectedCapacityGap: 35,
    };

    // Salary ranges by seniority (in thousands)
    const salaryRanges: Record<HeadcountRequestFormData['seniority'], { min: number; max: number }> = {
        junior: { min: 80, max: 110 },
        mid: { min: 110, max: 150 },
        senior: { min: 150, max: 200 },
        staff: { min: 200, max: 270 },
        principal: { min: 270, max: 350 },
    };

    // Justification templates
    const justificationTemplates = {
        capacity: `Our team is currently operating at ${mockTeamMetrics.averageUtilization}% average utilization, with ${mockTeamMetrics.overallocatedMembers} members overallocated. We have ${mockTeamMetrics.upcomingProjects} upcoming projects that will require an estimated ${mockTeamMetrics.projectedCapacityGap}% additional capacity. Without additional headcount, we risk project delays, team burnout, and inability to meet our Q2 commitments.`,
        growth: `To support our company's growth objectives and scale our engineering capacity, we need to expand the team. Current workload analysis shows we are at capacity and cannot take on additional initiatives without compromising quality or timelines.`,
        specialization: `We require specialized expertise that is currently missing from our team. This role will fill a critical gap in our technical capabilities and enable us to deliver on strategic initiatives that are currently blocked.`,
        backfill: `This is a backfill request to replace a team member who recently departed. The role is critical to maintaining our current velocity and project commitments.`,
    };

    const [formData, setFormData] = useState<HeadcountRequestFormData>({
        numberOfHeadcount: 1,
        role: '',
        seniority: 'mid',
        startDate: '',
        duration: 'permanent',
        contractMonths: undefined,
        justification: '',
        businessImpact: '',
        estimatedSalary: salaryRanges.mid.min,
        recruitingUrgency: 'medium',
    });

    const [useTemplate, setUseTemplate] = useState<string>('');

    // Apply template
    const handleApplyTemplate = (templateKey: string) => {
        const template = justificationTemplates[templateKey as keyof typeof justificationTemplates];
        if (template) {
            setFormData({ ...formData, justification: template });
            setUseTemplate(templateKey);
        }
    };

    // Calculate annual cost
    const calculateAnnualCost = (): number => {
        if (formData.duration === 'permanent') {
            return formData.estimatedSalary * 1000 * formData.numberOfHeadcount;
        } else {
            const months = formData.contractMonths || 12;
            return (formData.estimatedSalary * 1000 * (months / 12)) * formData.numberOfHeadcount;
        }
    };

    // Calculate total cost (including benefits for permanent)
    const calculateTotalCost = (): number => {
        const baseCost = calculateAnnualCost();
        if (formData.duration === 'permanent') {
            // Add 30% for benefits, taxes, equipment, etc.
            return baseCost * 1.3;
        }
        return baseCost;
    };

    // Update salary when seniority changes
    const handleSeniorityChange = (seniority: HeadcountRequestFormData['seniority']) => {
        setFormData({
            ...formData,
            seniority,
            estimatedSalary: salaryRanges[seniority].min,
        });
    };

    // Validation
    const isValid = (): boolean => {
        return (
            formData.numberOfHeadcount > 0 &&
            formData.role.trim().length > 0 &&
            formData.startDate.length > 0 &&
            formData.justification.length > 50 &&
            formData.businessImpact.length > 20 &&
            (formData.duration === 'permanent' || (formData.contractMonths || 0) > 0)
        );
    };

    // Submit
    const handleSubmit = () => {
        if (isValid()) {
            onSubmit?.(formData);
        }
    };

    return (
        <Box>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Typography variant="h5">Request Additional Headcount</Typography>
                <Stack direction="row" spacing={1}>
                    <Button variant="outlined" onClick={onCancel}>
                        Cancel
                    </Button>
                    <Button variant="contained" onClick={handleSubmit} disabled={!isValid()}>
                        Submit Request
                    </Button>
                </Stack>
            </Stack>

            {!isValid() && (
                <Alert severity="info" sx={{ mb: 3 }}>
                    Please complete all required fields: number of headcount, role, start date, justification (50+ chars), and business impact.
                </Alert>
            )}

            {/* Team Metrics */}
            <Card sx={{ p: 3, mb: 3, bgcolor: 'action.hover' }}>
                <Typography variant="h6" gutterBottom>
                    Current Team Metrics
                </Typography>
                <Grid container spacing={2}>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Team Size
                        </Typography>
                        <Typography variant="h5">{mockTeamMetrics.currentTeamSize}</Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Avg Utilization
                        </Typography>
                        <Typography variant="h5" color={mockTeamMetrics.averageUtilization > 90 ? 'error' : 'inherit'}>
                            {mockTeamMetrics.averageUtilization}%
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Overallocated
                        </Typography>
                        <Typography variant="h5" color="error">
                            {mockTeamMetrics.overallocatedMembers}
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Capacity Gap
                        </Typography>
                        <Typography variant="h5" color="warning.main">
                            {mockTeamMetrics.projectedCapacityGap}%
                        </Typography>
                    </Grid>
                </Grid>
                <Alert severity="warning" sx={{ mt: 2 }}>
                    Based on current metrics, your team is {mockTeamMetrics.averageUtilization > 90 ? 'operating above capacity' : 'at healthy capacity'}. {mockTeamMetrics.upcomingProjects} upcoming projects will require additional resources.
                </Alert>
            </Card>

            {/* Request Details */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    Request Details
                </Typography>

                <Grid container spacing={3}>
                    <Grid item xs={12} sm={6}>
                        <FormControl fullWidth required>
                            <InputLabel>Number of Headcount</InputLabel>
                            <Select
                                value={formData.numberOfHeadcount}
                                label="Number of Headcount"
                                onChange={(e) =>
                                    setFormData({ ...formData, numberOfHeadcount: Number(e.target.value) })
                                }
                            >
                                {[1, 2, 3, 4, 5].map((num) => (
                                    <MenuItem key={num} value={num}>
                                        {num} {num === 1 ? 'person' : 'people'}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>

                    <Grid item xs={12} sm={6}>
                        <TextField
                            fullWidth
                            required
                            label="Role/Position"
                            placeholder="e.g., Senior Software Engineer"
                            value={formData.role}
                            onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                        />
                    </Grid>

                    <Grid item xs={12} sm={6}>
                        <FormControl fullWidth required>
                            <InputLabel>Seniority Level</InputLabel>
                            <Select
                                value={formData.seniority}
                                label="Seniority Level"
                                onChange={(e) =>
                                    handleSeniorityChange(e.target.value as HeadcountRequestFormData['seniority'])
                                }
                            >
                                <MenuItem value="junior">Junior (L3)</MenuItem>
                                <MenuItem value="mid">Mid-Level (L4)</MenuItem>
                                <MenuItem value="senior">Senior (L5)</MenuItem>
                                <MenuItem value="staff">Staff (L6)</MenuItem>
                                <MenuItem value="principal">Principal (L7)</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>

                    <Grid item xs={12} sm={6}>
                        <TextField
                            fullWidth
                            required
                            label="Desired Start Date"
                            type="date"
                            value={formData.startDate}
                            onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                            InputLabelProps={{ shrink: true }}
                        />
                    </Grid>

                    <Grid item xs={12}>
                        <FormControl component="fieldset">
                            <Typography variant="subtitle2" gutterBottom>
                                Duration
                            </Typography>
                            <RadioGroup
                                row
                                value={formData.duration}
                                onChange={(e: { target: { value: unknown } }) =>
                                    setFormData({ ...formData, duration: e.target.value as 'permanent' | 'contract' })
                                }
                            >
                                <FormControlLabel value="permanent" control={<Radio />} label="Permanent" />
                                <FormControlLabel value="contract" control={<Radio />} label="Contract" />
                            </RadioGroup>
                        </FormControl>
                        {formData.duration === 'contract' && (
                            <TextField
                                sx={{ mt: 1 }}
                                label="Contract Duration (months)"
                                type="number"
                                value={formData.contractMonths || ''}
                                onChange={(e) =>
                                    setFormData({ ...formData, contractMonths: Number(e.target.value) })
                                }
                                inputProps={{ min: 1, max: 24 }}
                                size="small"
                            />
                        )}
                    </Grid>

                    <Grid item xs={12} sm={6}>
                        <FormControl fullWidth>
                            <InputLabel>Recruiting Urgency</InputLabel>
                            <Select
                                value={formData.recruitingUrgency}
                                label="Recruiting Urgency"
                                onChange={(e) =>
                                    setFormData({
                                        ...formData,
                                        recruitingUrgency: e.target.value as HeadcountRequestFormData['recruitingUrgency'],
                                    })
                                }
                            >
                                <MenuItem value="low">Low - Fill within 6 months</MenuItem>
                                <MenuItem value="medium">Medium - Fill within 3 months</MenuItem>
                                <MenuItem value="high">High - Fill within 1 month</MenuItem>
                                <MenuItem value="critical">Critical - Fill immediately</MenuItem>
                            </Select>
                        </FormControl>
                    </Grid>
                </Grid>
            </Card>

            {/* Justification */}
            <Card sx={{ p: 3, mb: 3 }}>
                <Typography variant="h6" gutterBottom>
                    Justification
                </Typography>

                <Stack spacing={2}>
                    <Box>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Use a template (optional):
                        </Typography>
                        <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ gap: 1 }}>
                            <Chip
                                label="Capacity Constraints"
                                onClick={() => handleApplyTemplate('capacity')}
                                color={useTemplate === 'capacity' ? 'primary' : 'default'}
                                clickable
                            />
                            <Chip
                                label="Growth Objectives"
                                onClick={() => handleApplyTemplate('growth')}
                                color={useTemplate === 'growth' ? 'primary' : 'default'}
                                clickable
                            />
                            <Chip
                                label="Specialized Expertise"
                                onClick={() => handleApplyTemplate('specialization')}
                                color={useTemplate === 'specialization' ? 'primary' : 'default'}
                                clickable
                            />
                            <Chip
                                label="Backfill"
                                onClick={() => handleApplyTemplate('backfill')}
                                color={useTemplate === 'backfill' ? 'primary' : 'default'}
                                clickable
                            />
                        </Stack>
                    </Box>

                    <TextField
                        fullWidth
                        required
                        label="Detailed Justification"
                        placeholder="Explain why this headcount is needed (minimum 50 characters)..."
                        value={formData.justification}
                        onChange={(e) => setFormData({ ...formData, justification: e.target.value })}
                        multiline
                        rows={6}
                        helperText={`${formData.justification.length} / 50 characters minimum`}
                    />

                    <TextField
                        fullWidth
                        required
                        label="Business Impact"
                        placeholder="What will happen if this request is not approved? What opportunities will be missed?"
                        value={formData.businessImpact}
                        onChange={(e) => setFormData({ ...formData, businessImpact: e.target.value })}
                        multiline
                        rows={4}
                        helperText={`${formData.businessImpact.length} / 20 characters minimum`}
                    />
                </Stack>
            </Card>

            {/* Budget Impact */}
            <Card sx={{ p: 3 }}>
                <Typography variant="h6" gutterBottom>
                    Budget Impact
                </Typography>

                <Grid container spacing={3}>
                    <Grid item xs={12} sm={6}>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                            Estimated Salary (per person)
                        </Typography>
                        <Stack direction="row" spacing={2} alignItems="center">
                            <TextField
                                type="number"
                                value={formData.estimatedSalary}
                                onChange={(e) =>
                                    setFormData({ ...formData, estimatedSalary: Number(e.target.value) })
                                }
                                inputProps={{
                                    min: salaryRanges[formData.seniority].min,
                                    max: salaryRanges[formData.seniority].max,
                                    step: 5,
                                }}
                                InputProps={{
                                    startAdornment: <Typography sx={{ mr: 1 }}>$</Typography>,
                                    endAdornment: <Typography sx={{ ml: 1 }}>k/year</Typography>,
                                }}
                                size="small"
                            />
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                            Range: ${salaryRanges[formData.seniority].min}k - ${salaryRanges[formData.seniority].max}k
                        </Typography>
                    </Grid>

                    <Grid item xs={12} sm={6}>
                        <Divider orientation="vertical" />
                    </Grid>
                </Grid>

                <Divider sx={{ my: 2 }} />

                <Grid container spacing={2}>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Annual Salary Cost
                        </Typography>
                        <Typography variant="h6">${(calculateAnnualCost() / 1000).toFixed(0)}k</Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            {formData.duration === 'permanent' ? 'Benefits & Overhead' : 'Contract Overhead'}
                        </Typography>
                        <Typography variant="h6">
                            ${((calculateTotalCost() - calculateAnnualCost()) / 1000).toFixed(0)}k
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Total {formData.duration === 'permanent' ? 'Annual' : 'Contract'} Cost
                        </Typography>
                        <Typography variant="h6" color="primary">
                            ${(calculateTotalCost() / 1000).toFixed(0)}k
                        </Typography>
                    </Grid>
                    <Grid item xs={6} sm={3}>
                        <Typography variant="body2" color="text.secondary">
                            Per Person
                        </Typography>
                        <Typography variant="body2">
                            ${(calculateTotalCost() / formData.numberOfHeadcount / 1000).toFixed(0)}k
                        </Typography>
                    </Grid>
                </Grid>

                {formData.duration === 'contract' && formData.contractMonths && (
                    <Alert severity="info" sx={{ mt: 2 }}>
                        Contract duration: {formData.contractMonths} months. Total cost: ${(calculateTotalCost() / 1000).toFixed(0)}k
                    </Alert>
                )}
            </Card>
        </Box>
    );
};
