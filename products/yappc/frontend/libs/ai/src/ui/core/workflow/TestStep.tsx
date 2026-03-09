/**
 * Test Generation Step Component
 *
 * Fifth step in the AI-powered workflow wizard.
 * Handles AI test generation and test running.
 *
 * @doc.type component
 * @doc.purpose Test generation workflow step
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { Box, Surface as Paper, Typography, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Chip, LinearProgress, Alert, Checkbox, Collapse, IconButton } from '@ghatana/ui';
import { FlaskConical as TestIcon, CheckCircle as PassIcon, XCircle as FailIcon, Hourglass as PendingIcon, Play as RunIcon, Sparkles as AIIcon, Check as CheckIcon, ChevronDown as ExpandMoreIcon, ChevronUp as ExpandLessIcon } from 'lucide-react';

export interface TestStepProps {
    codeData: { files: { path: string; status: string }[] };
    value: TestStepData;
    onChange: (data: TestStepData) => void;
    onComplete: (data: TestStepData) => void;
    onBack?: () => void;
    isLoading?: boolean;
    error?: string | null;
}

export interface TestStepData {
    tests: GeneratedTest[];
    coveragePercent: number;
    status: 'generating' | 'ready' | 'running' | 'complete';
}

export interface GeneratedTest {
    id: string;
    name: string;
    filePath: string;
    type: 'unit' | 'integration' | 'e2e';
    status: 'pending' | 'running' | 'passed' | 'failed' | 'skipped';
    code: string;
    errorMessage?: string;
    duration?: number;
    isSelected: boolean;
}

export const TestStep: React.FC<TestStepProps> = ({
    codeData: _codeData,
    value,
    onChange,
    onComplete,
    onBack,
    isLoading = false,
    error = null,
}) => {
    void _codeData; // Used for test generation context
    const [isGenerating, setIsGenerating] = useState(false);
    const [isRunning, setIsRunning] = useState(false);
    const [expandedTests, setExpandedTests] = useState<Set<string>>(new Set());

    const generateTests = useCallback(async () => {
        setIsGenerating(true);
        try {
            await new Promise((resolve) => setTimeout(resolve, 1500));

            const mockTests: GeneratedTest[] = [
                {
                    id: 'test-1',
                    name: 'Login component renders correctly',
                    filePath: 'src/components/Auth/__tests__/Login.test.tsx',
                    type: 'unit',
                    status: 'pending',
                    code: `import { render, screen } from '@testing-library/react';
import { Login } from '../Login';

describe('Login', () => {
    it('renders login form', () => {
        render(<Login />);
        expect(screen.getByPlaceholderText('Email')).toBeInTheDocument();
        expect(screen.getByPlaceholderText('Password')).toBeInTheDocument();
    });
});`,
                    isSelected: true,
                },
                {
                    id: 'test-2',
                    name: 'useAuth hook handles login',
                    filePath: 'src/hooks/__tests__/useAuth.test.ts',
                    type: 'unit',
                    status: 'pending',
                    code: `import { renderHook, act } from '@testing-library/react';
import { useAuth } from '../useAuth';

describe('useAuth', () => {
    it('handles successful login', async () => {
        const { result } = renderHook(() => useAuth());
        await act(async () => {
            await result.current.login('test@example.com', 'password');
        });
        expect(result.current.user).toBeDefined();
    });
});`,
                    isSelected: true,
                },
            ];

            onChange({
                ...value,
                tests: mockTests,
                status: 'ready',
                coveragePercent: 0,
            });
        } finally {
            setIsGenerating(false);
        }
    }, [value, onChange]);

    const runTests = useCallback(async () => {
        setIsRunning(true);
        onChange({ ...value, status: 'running' });

        try {
            for (let i = 0; i < value.tests.length; i++) {
                if (!value.tests[i].isSelected) continue;

                const newTests = [...value.tests];
                newTests[i] = { ...newTests[i], status: 'running' };
                onChange({ ...value, tests: newTests });

                await new Promise((resolve) => setTimeout(resolve, 1000));

                // Simulate test result
                const passed = Math.random() > 0.2;
                newTests[i] = {
                    ...newTests[i],
                    status: passed ? 'passed' : 'failed',
                    duration: Math.round(Math.random() * 500 + 100),
                    errorMessage: passed ? undefined : 'Expected true but got false',
                };
                onChange({ ...value, tests: newTests });
            }

            const passedCount = value.tests.filter((t) => t.status === 'passed').length;
            onChange({
                ...value,
                status: 'complete',
                coveragePercent: Math.round((passedCount / value.tests.length) * 100),
            });
        } finally {
            setIsRunning(false);
        }
    }, [value, onChange]);

    const toggleTest = useCallback((id: string) => {
        const newTests = value.tests.map((t) =>
            t.id === id ? { ...t, isSelected: !t.isSelected } : t
        );
        onChange({ ...value, tests: newTests });
    }, [value, onChange]);

    const toggleExpand = useCallback((id: string) => {
        setExpandedTests((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    }, []);

    const getStatusIcon = (status: string) => {
        switch (status) {
            case 'passed':
                return <PassIcon tone="success" />;
            case 'failed':
                return <FailIcon tone="danger" />;
            case 'running':
                return <PendingIcon tone="warning" />;
            default:
                return <PendingIcon color="disabled" />;
        }
    };

    const selectedCount = value.tests.filter((t) => t.isSelected).length;
    const passedCount = value.tests.filter((t) => t.status === 'passed').length;
    const failedCount = value.tests.filter((t) => t.status === 'failed').length;

    return (
        <Box className="p-6">
            <Typography as="h5" gutterBottom className="flex items-center gap-2">
                <TestIcon tone="primary" />
                Test Generation
            </Typography>

            {error && <Alert severity="error" className="mb-4">{error}</Alert>}

            {(isLoading || isGenerating) && (
                <Box className="mb-6">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                        <AIIcon size={16} className="mr-1 align-middle" />
                        Generating tests...
                    </Typography>
                    <LinearProgress />
                </Box>
            )}

            {value.tests.length === 0 && !isGenerating && (
                <Paper variant="outlined" className="p-6 text-center mb-4">
                    <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
                        No tests generated yet. Click to generate tests for your code.
                    </Typography>
                    <Button
                        variant="solid"
                        startIcon={<AIIcon />}
                        onClick={generateTests}
                    >
                        Generate Tests
                    </Button>
                </Paper>
            )}

            {value.tests.length > 0 && (
                <>
                    <Paper variant="outlined" className="p-4 mb-4">
                        <Box className="flex flex-wrap gap-4">
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Tests</Typography>
                                <Typography as="h6">{value.tests.length}</Typography>
                            </Box>
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Passed</Typography>
                                <Typography as="h6" color="success.main">{passedCount}</Typography>
                            </Box>
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Failed</Typography>
                                <Typography as="h6" color="error.main">{failedCount}</Typography>
                            </Box>
                            <Box>
                                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">Coverage</Typography>
                                <Typography as="h6">{value.coveragePercent}%</Typography>
                            </Box>
                        </Box>
                    </Paper>

                    <Paper variant="outlined" className="mb-4">
                        <List>
                            {value.tests.map((test) => (
                                <React.Fragment key={test.id}>
                                    <ListItem>
                                        <Checkbox
                                            checked={test.isSelected}
                                            onChange={() => toggleTest(test.id)}
                                            disabled={isRunning}
                                        />
                                        <ListItemIcon className="min-w-[36px]">
                                            {getStatusIcon(test.status)}
                                        </ListItemIcon>
                                        <ListItemText
                                            primary={test.name}
                                            secondary={
                                                <Box className="flex gap-2 items-center">
                                                    <Chip size="sm" label={test.type} className="h-[20px]" />
                                                    {test.duration && (
                                                        <Typography as="span" className="text-xs text-gray-500">
                                                            {test.duration}ms
                                                        </Typography>
                                                    )}
                                                </Box>
                                            }
                                        />
                                        <IconButton onClick={() => toggleExpand(test.id)}>
                                            {expandedTests.has(test.id) ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                                        </IconButton>
                                    </ListItem>
                                    <Collapse in={expandedTests.has(test.id)}>
                                        <Box className="p-4 bg-gray-50" >
                                            {test.errorMessage && (
                                                <Alert severity="error" className="mb-2">
                                                    {test.errorMessage}
                                                </Alert>
                                            )}
                                            <Box
                                                component="pre"
                                                className="p-4 rounded overflow-auto text-xs bg-[#1e1e1e] text-[#d4d4d4] font-mono"
                                            >
                                                {test.code}
                                            </Box>
                                        </Box>
                                    </Collapse>
                                </React.Fragment>
                            ))}
                        </List>
                    </Paper>
                </>
            )}

            <Box className="flex justify-between gap-4">
                {onBack && <Button onClick={onBack} disabled={isLoading || isRunning}>Back</Button>}
                <Box className="grow" />
                {value.tests.length > 0 && (
                    <Button
                        startIcon={<RunIcon />}
                        onClick={runTests}
                        disabled={isRunning || selectedCount === 0}
                    >
                        Run Tests ({selectedCount})
                    </Button>
                )}
                <Button
                    variant="solid"
                    onClick={() => onComplete(value)}
                    disabled={isLoading || isRunning}
                    endIcon={<CheckIcon />}
                >
                    Continue
                </Button>
            </Box>
        </Box>
    );
};

export default TestStep;
