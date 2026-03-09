/**
 * Test File List Canvas Content
 * 
 * Test file browser for Test × File level.
 * Displays test files with coverage and status indicators.
 * 
 * @doc.type component
 * @doc.purpose Test file list with status and coverage
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  LinearProgress,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { CheckCircle, AlertCircle as Error, AlertTriangle as Warning, Hourglass as HourglassEmpty, Search, Bug as BugReport } from 'lucide-react';

interface TestFile {
    path: string;
    name: string;
    status: 'passed' | 'failed' | 'skipped' | 'running';
    tests: number;
    passed: number;
    failed: number;
    skipped: number;
    coverage: number;
    duration: number;
}

// Mock test files data
const MOCK_TEST_FILES: TestFile[] = [
    {
        path: '/src/components/Button.test.tsx',
        name: 'Button.test.tsx',
        status: 'passed',
        tests: 12,
        passed: 12,
        failed: 0,
        skipped: 0,
        coverage: 95,
        duration: 234,
    },
    {
        path: '/src/components/Input.test.tsx',
        name: 'Input.test.tsx',
        status: 'failed',
        tests: 8,
        passed: 6,
        failed: 2,
        skipped: 0,
        coverage: 78,
        duration: 189,
    },
    {
        path: '/src/components/Modal.test.tsx',
        name: 'Modal.test.tsx',
        status: 'passed',
        tests: 15,
        passed: 15,
        failed: 0,
        skipped: 0,
        coverage: 92,
        duration: 456,
    },
    {
        path: '/src/utils/helpers.test.ts',
        name: 'helpers.test.ts',
        status: 'passed',
        tests: 24,
        passed: 24,
        failed: 0,
        skipped: 0,
        coverage: 100,
        duration: 123,
    },
    {
        path: '/src/utils/validators.test.ts',
        name: 'validators.test.ts',
        status: 'skipped',
        tests: 10,
        passed: 0,
        failed: 0,
        skipped: 10,
        coverage: 0,
        duration: 0,
    },
    {
        path: '/src/services/api.test.ts',
        name: 'api.test.ts',
        status: 'failed',
        tests: 18,
        passed: 14,
        failed: 4,
        skipped: 0,
        coverage: 65,
        duration: 892,
    },
];

const getStatusIcon = (status: TestFile['status']) => {
    switch (status) {
        case 'passed':
            return <CheckCircle size={16} className="text-green-600" />;
        case 'failed':
            return <Error size={16} className="text-red-600" />;
        case 'skipped':
            return <Warning size={16} className="text-amber-600" />;
        case 'running':
            return <HourglassEmpty size={16} className="text-sky-600" />;
    }
};

const getStatusColor = (status: TestFile['status']) => {
    switch (status) {
        case 'passed':
            return 'success';
        case 'failed':
            return 'error';
        case 'skipped':
            return 'warning';
        case 'running':
            return 'info';
    }
};

const getCoverageColor = (coverage: number) => {
    if (coverage >= 80) return 'success';
    if (coverage >= 60) return 'warning';
    return 'error';
};

const TestFileItem = ({
    file,
    onClick
}: {
    file: TestFile;
    onClick: (path: string) => void;
}) => {
    return (
        <Box
            onClick={() => onClick(file.path)}
            className="p-4 mb-2 rounded border border-solid border-[rgba(0, 0, 0, 0.12)] cursor-pointer transition-all duration-200 hover:bg-[rgba(0,_0,_0,_0.02)] hover:border-blue-600"
        >
            <Box className="flex items-center mb-2">
                {getStatusIcon(file.status)}
                <Typography
                    variant="body2"
                    className="flex-1 font-medium ml-2 font-mono"
                >
                    {file.name}
                </Typography>
                <Chip
                    label={file.status.toUpperCase()}
                    size="small"
                    color={getStatusColor(file.status)}
                    className="ml-2"
                />
            </Box>

            <Box className="flex gap-4 mb-2">
                <Typography variant="caption" color="text.secondary">
                    Tests: {file.tests}
                </Typography>
                <Typography variant="caption" color="success.main">
                    Passed: {file.passed}
                </Typography>
                {file.failed > 0 && (
                    <Typography variant="caption" color="error.main">
                        Failed: {file.failed}
                    </Typography>
                )}
                {file.skipped > 0 && (
                    <Typography variant="caption" color="warning.main">
                        Skipped: {file.skipped}
                    </Typography>
                )}
                <Typography variant="caption" color="text.secondary">
                    {file.duration}ms
                </Typography>
            </Box>

            <Box>
                <Box className="flex items-center mb-1">
                    <Typography variant="caption" color="text.secondary" className="min-w-[80px]">
                        Coverage: {file.coverage}%
                    </Typography>
                    <LinearProgress
                        variant="determinate"
                        value={file.coverage}
                        color={getCoverageColor(file.coverage)}
                        className="flex-1 ml-2 h-[6px] rounded-xl"
                    />
                </Box>
            </Box>
        </Box>
    );
};

export const TestFileListCanvas = () => {
    const [testFiles] = useState<TestFile[]>(MOCK_TEST_FILES);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterStatus, setFilterStatus] = useState<TestFile['status'] | 'all'>('all');

    const filteredFiles = useMemo(() => {
        return testFiles.filter(file => {
            const matchesSearch = searchQuery === '' ||
                file.name.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesStatus = filterStatus === 'all' || file.status === filterStatus;
            return matchesSearch && matchesStatus;
        });
    }, [testFiles, searchQuery, filterStatus]);

    const stats = useMemo(() => {
        return testFiles.reduce(
            (acc, file) => ({
                total: acc.total + file.tests,
                passed: acc.passed + file.passed,
                failed: acc.failed + file.failed,
                skipped: acc.skipped + file.skipped,
            }),
            { total: 0, passed: 0, failed: 0, skipped: 0 }
        );
    }, [testFiles]);

    const hasContent = testFiles.length > 0;

    const handleFileClick = (path: string) => {
        console.log('Open test file:', path);
        // NOTE: Integrate with test editor
    };

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Run Tests',
                    icon: <BugReport />,
                    onClick: () => {
                        console.log('Run tests');
                    },
                },
                secondaryAction: {
                    label: 'Create Test File',
                    onClick: () => {
                        console.log('Create test file');
                    },
                },
            }}
        >
            <Box
                className="h-full w-full flex flex-col bg-white dark:bg-gray-900"
            >
                {/* Header with stats */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-[rgba(0,_0,_0,_0.02)]">
                    <Typography variant="h6" gutterBottom>
                        Test Suite
                    </Typography>
                    <Box className="flex gap-6">
                        <Typography variant="body2" color="text.secondary">
                            Total: {stats.total}
                        </Typography>
                        <Typography variant="body2" color="success.main">
                            Passed: {stats.passed}
                        </Typography>
                        <Typography variant="body2" color="error.main">
                            Failed: {stats.failed}
                        </Typography>
                        <Typography variant="body2" color="warning.main">
                            Skipped: {stats.skipped}
                        </Typography>
                    </Box>
                </Box>

                {/* Search and filters */}
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)]">
                    <TextField
                        fullWidth
                        size="small"
                        placeholder="Search test files..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        InputProps={{
                            startAdornment: <Search className="text-gray-500 dark:text-gray-400 mr-2" />,
                        }}
                        className="mb-2"
                    />
                    <Box className="flex gap-2">
                        {(['all', 'passed', 'failed', 'skipped'] as const).map((status) => (
                            <Chip
                                key={status}
                                label={status.charAt(0).toUpperCase() + status.slice(1)}
                                size="small"
                                onClick={() => setFilterStatus(status)}
                                color={filterStatus === status ? 'primary' : 'default'}
                                variant={filterStatus === status ? 'filled' : 'outlined'}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Test files list */}
                <Box className="flex-1 overflow-auto p-4">
                    {filteredFiles.map((file) => (
                        <TestFileItem key={file.path} file={file} onClick={handleFileClick} />
                    ))}
                    {filteredFiles.length === 0 && (
                        <Box className="text-center p-8">
                            <Typography color="text.secondary">
                                No test files match your filters
                            </Typography>
                        </Box>
                    )}
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default TestFileListCanvas;
