/**
 * Test Results Panel Component
 * 
 * Displays generated test cases with approval/rejection controls.
 * Follows Journey 4.1 Step 2 (QA - Test Case Review) from YAPPC_USER_JOURNEYS.md.
 * 
 * Features:
 * - List of suggested test cases
 * - Approve/Reject individual tests
 * - Approve All action
 * - Test case details (steps, expected results)
 * - Execution status per test
 * 
 * @doc.type component
 * @doc.purpose Test results panel for QA review workflow
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState } from 'react';
import { Box, Button, Checkbox, Chip, Dialog, DialogTitle, DialogContent, DialogActions, IconButton, InteractiveList as List, ListItem, ListItemButton, ListItemText, Surface as Paper, Tooltip, Typography } from '@ghatana/ui';
import { CheckCircle as ApproveIcon, XCircle as RejectIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon, Info as InfoIcon, X as CloseIcon } from 'lucide-react';

import type { GeneratedTestCase } from '@ghatana/yappc-ai';

/**
 * Test case with approval state
 */
export interface TestCaseWithApproval extends GeneratedTestCase {
    approved: boolean;
    rejected: boolean;
    executed?: boolean;
    passed?: boolean;
}

/**
 * TestResultsPanel props
 */
export interface TestResultsPanelProps {
    /** Generated test cases */
    testCases: TestCaseWithApproval[];
    /** Callback when tests are approved */
    onApprove: (testIds: string[]) => void;
    /** Callback when tests are rejected */
    onReject: (testIds: string[]) => void;
    /** Callback when panel closes */
    onClose: () => void;
    /** Loading state */
    loading?: boolean;
    /** Open state */
    open: boolean;
}

/**
 * TestResultsPanel Component
 */
export const TestResultsPanel: React.FC<TestResultsPanelProps> = ({
    testCases,
    onApprove,
    onReject,
    onClose,
    loading = false,
    open,
}) => {
    const [expandedTestId, setExpandedTestId] = useState<string | null>(null);
    const [selectedTestIds, setSelectedTestIds] = useState<string[]>([]);

    // Toggle test expansion
    const toggleExpand = (testId: string) => {
        setExpandedTestId(expandedTestId === testId ? null : testId);
    };

    // Toggle test selection
    const toggleSelect = (testId: string) => {
        setSelectedTestIds(prev =>
            prev.includes(testId)
                ? prev.filter(id => id !== testId)
                : [...prev, testId]
        );
    };

    // Select/deselect all
    const toggleSelectAll = () => {
        if (selectedTestIds.length === testCases.length) {
            setSelectedTestIds([]);
        } else {
            setSelectedTestIds(testCases.map(tc => tc.id));
        }
    };

    // Approve selected tests
    const handleApproveSelected = () => {
        onApprove(selectedTestIds);
        setSelectedTestIds([]);
    };

    // Reject selected tests
    const handleRejectSelected = () => {
        onReject(selectedTestIds);
        setSelectedTestIds([]);
    };

    // Approve all tests
    const handleApproveAll = () => {
        onApprove(testCases.map(tc => tc.id));
    };

    // Get test status color
    const getTestStatusColor = (test: TestCaseWithApproval) => {
        if (test.rejected) return '#f44336'; // Red
        if (test.approved) return '#4caf50'; // Green
        if (test.executed) {
            return test.passed ? '#4caf50' : '#f44336';
        }
        return '#9e9e9e'; // Gray
    };

    // Get test status label
    const getTestStatusLabel = (test: TestCaseWithApproval) => {
        if (test.rejected) return 'Rejected';
        if (test.approved) return 'Approved';
        if (test.executed) {
            return test.passed ? 'Passed' : 'Failed';
        }
        return 'Pending';
    };

    return (
        <Dialog
            open={open}
            onClose={onClose}
            size="md"
            fullWidth
            PaperProps={{
                sx: {
                    height: '70vh',
                    maxHeight: '700px',
                },
            }}
        >
            <DialogTitle>
                <Box className="flex justify-between items-center">
                    <Typography as="h6">Suggested Test Cases</Typography>
                    <IconButton onClick={onClose} size="sm">
                        <CloseIcon />
                    </IconButton>
                </Box>
                <Typography as="p" className="text-sm" color="text.secondary" className="mt-1">
                    Review and approve test cases generated from selected nodes
                </Typography>
            </DialogTitle>

            <DialogContent dividers>
                {loading ? (
                    <Box className="flex justify-center items-center min-h-[200px]">
                        <Typography>Generating test cases...</Typography>
                    </Box>
                ) : testCases.length === 0 ? (
                    <Box className="flex justify-center items-center min-h-[200px]">
                        <Typography color="text.secondary">No test cases generated</Typography>
                    </Box>
                ) : (
                    <List>
                        {testCases.map((test) => {
                            const isExpanded = expandedTestId === test.id;
                            const isSelected = selectedTestIds.includes(test.id);
                            const statusColor = getTestStatusColor(test);
                            const statusLabel = getTestStatusLabel(test);

                            return (
                                <Paper
                                    key={test.id}
                                    variant="raised"
                                    className="mb-2"
                                    style={{ border: `2px solid ${isSelected ? '#1976d2' : '#e0e0e0'}` }}
                                >
                                    <ListItem
                                        disablePadding
                                        secondaryAction={
                                            <Box className="flex gap-1 items-center">
                                                <Chip
                                                    label={statusLabel}
                                                    size="sm"
                                                    style={{
                                                        backgroundColor: `${statusColor}20`,
                                                        color: statusColor,
                                                        fontWeight: 600,
                                                    }}
                                                />
                                                <IconButton
                                                    edge="end"
                                                    onClick={() => toggleExpand(test.id)}
                                                    size="sm"
                                                >
                                                    {isExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                                </IconButton>
                                            </Box>
                                        }
                                    >
                                        <ListItemButton onClick={() => toggleSelect(test.id)} dense>
                                            <Checkbox
                                                edge="start"
                                                checked={isSelected}
                                                disableRipple
                                            />
                                            <ListItemText
                                                primary={test.name}
                                                secondary={
                                                    <Box>
                                                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                            {test.description}
                                                        </Typography>
                                                        <Chip
                                                            label={test.type}
                                                            size="sm"
                                                            className="ml-2 h-[18px] text-[0.65rem]"
                                                        />
                                                    </Box>
                                                }
                                            />
                                        </ListItemButton>
                                    </ListItem>

                                    {/* Expanded Details */}
                                    {isExpanded && (
                                        <Box className="p-4 pt-0 bg-[#f5f5f5]">
                                            {/* Test Steps */}
                                            <Typography as="p" className="text-sm font-medium mb-2">
                                                Test Steps:
                                            </Typography>
                                            <List dense className="pl-4">
                                                {test.steps.map((step) => (
                                                    <ListItem key={step.number} disablePadding>
                                                        <ListItemText
                                                            primary={`${step.number}. ${step.description}`}
                                                            secondary={`Expected: ${step.expectedOutcome}`}
                                                            primaryTypographyProps={{ variant: 'body2' }}
                                                            secondaryTypographyProps={{
                                                                variant: 'caption',
                                                                color: 'text.secondary',
                                                            }}
                                                        />
                                                    </ListItem>
                                                ))}
                                            </List>

                                            {/* Expected Result */}
                                            <Box className="mt-2 p-2 rounded bg-white">
                                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                                    Expected Result:
                                                </Typography>
                                                <Typography as="p" className="text-sm">{test.expectedResult}</Typography>
                                            </Box>
                                        </Box>
                                    )}
                                </Paper>
                            );
                        })}
                    </List>
                )}
            </DialogContent>

            <DialogActions className="justify-between px-6 py-4">
                <Box>
                    <Checkbox
                        checked={selectedTestIds.length === testCases.length && testCases.length > 0}
                        indeterminate={selectedTestIds.length > 0 && selectedTestIds.length < testCases.length}
                        onChange={toggleSelectAll}
                    />
                    <Typography as="span" className="text-xs text-gray-500" component="span">
                        {selectedTestIds.length} of {testCases.length} selected
                    </Typography>
                </Box>

                <Box className="flex gap-2">
                    <Tooltip title="Reject selected tests">
                        <span>
                            <Button
                                startIcon={<RejectIcon />}
                                onClick={handleRejectSelected}
                                disabled={selectedTestIds.length === 0}
                                tone="danger"
                            >
                                Reject
                            </Button>
                        </span>
                    </Tooltip>
                    <Tooltip title="Approve selected tests">
                        <span>
                            <Button
                                startIcon={<ApproveIcon />}
                                onClick={handleApproveSelected}
                                disabled={selectedTestIds.length === 0}
                                tone="success"
                            >
                                Approve Selected
                            </Button>
                        </span>
                    </Tooltip>
                    <Button
                        variant="solid"
                        onClick={handleApproveAll}
                        disabled={testCases.length === 0}
                    >
                        Approve All
                    </Button>
                </Box>
            </DialogActions>
        </Dialog>
    );
};
