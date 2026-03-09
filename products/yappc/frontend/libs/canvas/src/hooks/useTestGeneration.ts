/**
 * useTestGeneration Hook
 * 
 * React hook for managing test generation workflow.
 * Integrates with AIRequirementsService for test case generation.
 * Follows Journey 4.1 (QA - Test Generation) from YAPPC_USER_JOURNEYS.md.
 * 
 * Features:
 * - Generate test cases from selected nodes
 * - Run tests (mock execution with CI pipeline)
 * - Track test status and results
 * - Manage test approval workflow
 * 
 * @doc.type hook
 * @doc.purpose Test generation state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';
import { useNodes } from '@xyflow/react';
import { AIRequirementsService } from '@ghatana/yappc-ai';

import type { Node } from '@xyflow/react';
import type { GeneratedTestCase } from '@ghatana/yappc-ai';
import type { TestType, TestStatus } from '../components/TestGenToolbar';
import type { TestCaseWithApproval } from '../components/TestResultsPanel';

/**
 * useTestGeneration options
 */
export interface UseTestGenerationOptions {
    /** Callback when tests are generated */
    onTestsGenerated?: (tests: GeneratedTestCase[]) => void;
    /** Callback when tests start running */
    onTestsRunning?: () => void;
    /** Callback when tests complete */
    onTestsComplete?: (passed: number, failed: number) => void;
    /** Callback when test fails */
    onTestFailed?: (testId: string, error: string) => void;
    /** Callback to update edge status */
    onEdgeStatusUpdate?: (edgeIds: string[], status: 'running' | 'passed' | 'failed' | 'idle') => void;
}

/**
 * useTestGeneration result
 */
export interface UseTestGenerationResult {
    /** Currently selected nodes */
    selectedNodes: Node[];
    /** Generated test cases */
    testCases: TestCaseWithApproval[];
    /** Test execution status */
    testStatus: TestStatus;
    /** Test coverage percentage (0-100) */
    coverage: number;
    /** Loading state */
    loading: boolean;
    /** Generate tests for selected nodes */
    generateTests: (testType: TestType) => Promise<void>;
    /** Run approved tests */
    runTests: () => Promise<void>;
    /** Approve tests by IDs */
    approveTests: (testIds: string[]) => void;
    /** Reject tests by IDs */
    rejectTests: (testIds: string[]) => void;
    /** Clear all tests */
    clearTests: () => void;
}

/**
 * useTestGeneration hook
 */
export const useTestGeneration = (
    options: UseTestGenerationOptions = {}
): UseTestGenerationResult => {
    const {
        onTestsGenerated,
        onTestsRunning,
        onTestsComplete,
        onTestFailed,
        onEdgeStatusUpdate,
    } = options;

    const nodes = useNodes();
    const [testCases, setTestCases] = useState<TestCaseWithApproval[]>([]);
    const [testStatus, setTestStatus] = useState<TestStatus>('pending');
    const [loading, setLoading] = useState(false);

    // Get selected nodes
    const selectedNodes = useMemo(() => {
        return nodes.filter((node) => node.selected);
    }, [nodes]);

    // Calculate test coverage (mock calculation)
    const coverage = useMemo(() => {
        if (testCases.length === 0) return 0;
        const approvedCount = testCases.filter(tc => tc.approved).length;
        return Math.round((approvedCount / testCases.length) * 100);
    }, [testCases]);

    /**
     * Generate tests from selected nodes
     */
    const generateTests = useCallback(async (testType: TestType) => {
        if (selectedNodes.length === 0) {
            console.warn('No nodes selected for test generation');
            return;
        }

        setLoading(true);
        setTestStatus('pending');

        try {
            // Initialize AI service
            const aiService = new AIRequirementsService();

            // Generate tests for each selected node
            const allTests: GeneratedTestCase[] = [];

            for (const node of selectedNodes) {
                const nodeData = node.data as { label?: string; description?: string } | undefined;
                const request = {
                    requirementId: node.id,
                    title: nodeData?.label || node.id,
                    description: nodeData?.description || `Test ${node.type} node`,
                    count: 3, // Generate 3 test cases per node
                    testType,
                };

                const result = await aiService.generateTestCases(request);

                if (result.success && result.data) {
                    allTests.push(...result.data);
                }
            }

            // Convert to test cases with approval state
            const testsWithApproval: TestCaseWithApproval[] = allTests.map(test => ({
                ...test,
                approved: false,
                rejected: false,
            }));

            setTestCases(testsWithApproval);
            onTestsGenerated?.(allTests);
        } catch (error) {
            console.error('Failed to generate tests:', error);
        } finally {
            setLoading(false);
        }
    }, [selectedNodes, onTestsGenerated]);

    /**
     * Run approved tests (mock execution)
     */
    const runTests = useCallback(async () => {
        const approvedTests = testCases.filter(tc => tc.approved && !tc.rejected);

        if (approvedTests.length === 0) {
            console.warn('No approved tests to run');
            return;
        }

        setTestStatus('running');
        onTestsRunning?.();

        // Set all edges connected to selected nodes to "running" state
        const selectedNodeIds = selectedNodes.map(n => n.id);
        onEdgeStatusUpdate?.(selectedNodeIds, 'running');

        try {
            // Mock test execution with random pass/fail
            const executedTests = [...testCases];
            let passedCount = 0;
            let failedCount = 0;

            for (const test of approvedTests) {
                // Simulate test execution delay
                await new Promise(resolve => setTimeout(resolve, 500));

                const testIndex = executedTests.findIndex(t => t.id === test.id);
                if (testIndex !== -1) {
                    // 80% pass rate for demo
                    const passed = Math.random() > 0.2;

                    executedTests[testIndex] = {
                        ...executedTests[testIndex],
                        executed: true,
                        passed,
                    };

                    if (passed) {
                        passedCount++;
                    } else {
                        failedCount++;
                        onTestFailed?.(test.id, 'Test assertion failed');
                    }
                }
            }

            setTestCases(executedTests);
            const finalStatus = failedCount > 0 ? 'failed' : 'passed';
            setTestStatus(finalStatus);

            // Update edges with final test status
            onEdgeStatusUpdate?.(selectedNodeIds, finalStatus);

            onTestsComplete?.(passedCount, failedCount);
        } catch (error) {
            console.error('Failed to run tests:', error);
            setTestStatus('failed');
        }
    }, [testCases, onTestsRunning, onTestsComplete, onTestFailed]);

    /**
     * Approve tests by IDs
     */
    const approveTests = useCallback((testIds: string[]) => {
        setTestCases(prev =>
            prev.map(test =>
                testIds.includes(test.id)
                    ? { ...test, approved: true, rejected: false }
                    : test
            )
        );
    }, []);

    /**
     * Reject tests by IDs
     */
    const rejectTests = useCallback((testIds: string[]) => {
        setTestCases(prev =>
            prev.map(test =>
                testIds.includes(test.id)
                    ? { ...test, approved: false, rejected: true }
                    : test
            )
        );
    }, []);

    /**
     * Clear all tests
     */
    const clearTests = useCallback(() => {
        setTestCases([]);
        setTestStatus('pending');
    }, []);

    return {
        selectedNodes,
        testCases,
        testStatus,
        coverage,
        loading,
        generateTests,
        runTests,
        approveTests,
        rejectTests,
        clearTests,
    };
};
