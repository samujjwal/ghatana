/**
 * Test Generation Toolbar Component
 * 
 * Toolbar for QA Engineer to generate and execute tests from canvas flows.
 * Follows Journey 4.1 (QA - Test Generation) from YAPPC_USER_JOURNEYS.md.
 * 
 * Features:
 * - Test Gen button (requires selection)
 * - Test type dropdown (unit, integration, e2e, acceptance)
 * - Run Tests button
 * - Test coverage indicator
 * 
 * @doc.type component
 * @doc.purpose Test generation toolbar for QA workflow
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from 'react';
import { Box, Button, ToggleButtonGroup as ButtonGroup, Menu, MenuItem, Tooltip, Typography, Chip } from '@ghatana/ui';
import { Bug as TestIcon, Play as RunIcon, FlaskConical as GenerateIcon, ChevronDown as ExpandMoreIcon, CheckCircle as PassIcon, AlertCircle as FailIcon } from 'lucide-react';

import type { Node } from '@xyflow/react';

/**
 * Test type options
 */
export type TestType = 'unit' | 'integration' | 'e2e' | 'acceptance';

/**
 * Test status for visualization
 */
export type TestStatus = 'pending' | 'running' | 'passed' | 'failed';

/**
 * TestGenToolbar props
 */
export interface TestGenToolbarProps {
    /** Currently selected nodes */
    selectedNodes: Node[];
    /** Callback when Test Gen button clicked */
    onGenerateTests: (testType: TestType) => void;
    /** Callback when Run Tests button clicked */
    onRunTests: () => void;
    /** Current test type */
    testType?: TestType;
    /** Callback when test type changes */
    onTestTypeChange?: (testType: TestType) => void;
    /** Test execution status */
    testStatus?: TestStatus;
    /** Test coverage percentage (0-100) */
    coverage?: number;
    /** Disabled state */
    disabled?: boolean;
}

/**
 * Test type options with labels and descriptions
 */
const TEST_TYPE_OPTIONS: Array<{
    value: TestType;
    label: string;
    description: string;
}> = [
        { value: 'unit', label: 'Unit Tests', description: 'Test individual functions/methods' },
        { value: 'integration', label: 'Integration Tests', description: 'Test component interactions' },
        { value: 'e2e', label: 'E2E Tests', description: 'Test complete user flows' },
        { value: 'acceptance', label: 'Acceptance Tests', description: 'Test requirements compliance' },
    ];

/**
 * TestGenToolbar Component
 */
export const TestGenToolbar: React.FC<TestGenToolbarProps> = ({
    selectedNodes,
    onGenerateTests,
    onRunTests,
    testType = 'integration',
    onTestTypeChange,
    testStatus = 'pending',
    coverage = 0,
    disabled = false,
}) => {
    const [typeAnchorEl, setTypeAnchorEl] = React.useState<null | HTMLElement>(null);
    const typeMenuOpen = Boolean(typeAnchorEl);

    // Check if test generation is possible
    const canGenerate = useMemo(() => {
        return selectedNodes.length > 0 && !disabled;
    }, [selectedNodes.length, disabled]);

    // Check if tests can be run
    const canRun = useMemo(() => {
        return !disabled && testStatus !== 'running';
    }, [disabled, testStatus]);

    // Get status color and icon
    const statusInfo = useMemo(() => {
        switch (testStatus) {
            case 'passed':
                return { color: '#4caf50', icon: <PassIcon />, label: 'Passed' };
            case 'failed':
                return { color: '#f44336', icon: <FailIcon />, label: 'Failed' };
            case 'running':
                return { color: '#2196f3', icon: null, label: 'Running...' };
            default:
                return { color: '#9e9e9e', icon: null, label: 'Pending' };
        }
    }, [testStatus]);

    // Get coverage color
    const coverageColor = useMemo(() => {
        if (coverage >= 80) return '#4caf50'; // Green
        if (coverage >= 60) return '#ff9800'; // Orange
        return '#f44336'; // Red
    }, [coverage]);

    const handleTypeMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
        setTypeAnchorEl(event.currentTarget);
    };

    const handleTypeMenuClose = () => {
        setTypeAnchorEl(null);
    };

    const handleTypeSelect = (type: TestType) => {
        onTestTypeChange?.(type);
        handleTypeMenuClose();
    };

    const currentTestTypeOption = TEST_TYPE_OPTIONS.find(opt => opt.value === testType);

    return (
        <Box
            className="flex flex-col gap-2 rounded bg-white p-3 border border-solid border-[#e0e0e0] min-w-[280px]"
            style={{ boxShadow: '0 2px 8px rgba(0, 0, 0, 0.12)' }}
        >
            {/* Header */}
            <Box className="flex items-center gap-2 mb-1">
                <TestIcon className="text-xl text-[#1976d2]" />
                <Typography as="p" className="text-sm font-medium font-semibold">
                    Test Generation
                </Typography>
            </Box>

            {/* Selection Info */}
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                {selectedNodes.length === 0
                    ? 'Select nodes to generate tests'
                    : `${selectedNodes.length} node${selectedNodes.length > 1 ? 's' : ''} selected`}
            </Typography>

            {/* Test Type Selector */}
            <Box>
                <Tooltip title={currentTestTypeOption?.description || ''}>
                    <Button
                        size="sm"
                        variant="outlined"
                        fullWidth
                        onClick={handleTypeMenuOpen}
                        endIcon={<ExpandMoreIcon />}
                        className="justify-between normal-case"
                    >
                        {currentTestTypeOption?.label || 'Select Type'}
                    </Button>
                </Tooltip>
                <Menu
                    anchorEl={typeAnchorEl}
                    open={typeMenuOpen}
                    onClose={handleTypeMenuClose}
                    anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
                    transformOrigin={{ vertical: 'top', horizontal: 'left' }}
                >
                    {TEST_TYPE_OPTIONS.map((option) => (
                        <MenuItem
                            key={option.value}
                            onClick={() => handleTypeSelect(option.value)}
                            selected={option.value === testType}
                        >
                            <Box>
                                <Typography as="p" className="text-sm">{option.label}</Typography>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                    {option.description}
                                </Typography>
                            </Box>
                        </MenuItem>
                    ))}
                </Menu>
            </Box>

            {/* Action Buttons */}
            <ButtonGroup orientation="vertical" fullWidth>
                <Tooltip
                    title={
                        !canGenerate
                            ? 'Select nodes to generate tests'
                            : 'Generate test cases using AI'
                    }
                >
                    <span>
                        <Button
                            size="sm"
                            variant="solid"
                            startIcon={<GenerateIcon />}
                            onClick={() => onGenerateTests(testType)}
                            disabled={!canGenerate}
                            className="normal-case"
                        >
                            Generate Tests
                        </Button>
                    </span>
                </Tooltip>

                <Tooltip
                    title={
                        !canRun
                            ? testStatus === 'running'
                                ? 'Tests are running...'
                                : 'Generate tests first'
                            : 'Run generated tests'
                    }
                >
                    <span>
                        <Button
                            size="sm"
                            variant="outlined"
                            startIcon={testStatus === 'running' ? null : <RunIcon />}
                            onClick={onRunTests}
                            disabled={!canRun}
                            className="normal-case"
                        >
                            {testStatus === 'running' ? 'Running...' : 'Run Tests'}
                        </Button>
                    </span>
                </Tooltip>
            </ButtonGroup>

            {/* Status & Coverage */}
            <Box className="flex justify-between items-center mt-1">
                {/* Test Status */}
                <Box className="flex items-center gap-1">
                    {statusInfo.icon && (
                        <Box style={{ color: statusInfo.color }}>{statusInfo.icon}</Box>
                    )}
                    <Typography as="span" className="text-xs text-gray-500" style={{ color: statusInfo.color }}>
                        {statusInfo.label}
                    </Typography>
                </Box>

                {/* Coverage Badge */}
                {coverage > 0 && (
                    <Chip
                        label={`${coverage}% coverage`}
                        size="sm"
                        style={{
                            backgroundColor: `${coverageColor}20`,
                            color: coverageColor,
                            fontWeight: 600,
                            fontSize: '0.7rem',
                            height: 20,
                        }}
                    />
                )}
            </Box>
        </Box>
    );
};
