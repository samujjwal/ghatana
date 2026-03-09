/**
 * E2E Coverage Canvas Content
 * 
 * End-to-end test coverage for Test × System level.
 * System-wide test coverage visualization.
 * 
 * @doc.type component
 * @doc.purpose E2E test coverage dashboard
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface E2ETestSuite {
    id: string;
    name: string;
    feature: string;
    tests: number;
    passed: number;
    failed: number;
    coverage: number;
    duration: number;
    lastRun: Date;
}

const MOCK_SUITES: E2ETestSuite[] = [
    {
        id: '1',
        name: 'Authentication Flow',
        feature: 'Auth',
        tests: 24,
        passed: 22,
        failed: 2,
        coverage: 87,
        duration: 142,
        lastRun: new Date('2026-01-09T08:00:00'),
    },
    {
        id: '2',
        name: 'Checkout Process',
        feature: 'E-commerce',
        tests: 36,
        passed: 36,
        failed: 0,
        coverage: 95,
        duration: 218,
        lastRun: new Date('2026-01-09T07:30:00'),
    },
    {
        id: '3',
        name: 'Product Search',
        feature: 'Catalog',
        tests: 18,
        passed: 16,
        failed: 2,
        coverage: 78,
        duration: 89,
        lastRun: new Date('2026-01-09T06:45:00'),
    },
    {
        id: '4',
        name: 'User Dashboard',
        feature: 'Dashboard',
        tests: 28,
        passed: 28,
        failed: 0,
        coverage: 92,
        duration: 156,
        lastRun: new Date('2026-01-09T05:15:00'),
    },
    {
        id: '5',
        name: 'Payment Gateway',
        feature: 'Payments',
        tests: 42,
        passed: 38,
        failed: 4,
        coverage: 82,
        duration: 267,
        lastRun: new Date('2026-01-08T22:00:00'),
    },
];

const getCoverageColor = (coverage: number) => {
    if (coverage >= 90) return '#10B981';
    if (coverage >= 75) return '#F59E0B';
    return '#EF4444';
};

export const E2ECoverageCanvas = () => {
    const [suites] = useState<E2ETestSuite[]>(MOCK_SUITES);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredSuites = useMemo(() => {
        return suites.filter(
            suite =>
                searchQuery === '' ||
                suite.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                suite.feature.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [suites, searchQuery]);

    const stats = useMemo(() => {
        const total = suites.reduce((acc, s) => acc + s.tests, 0);
        const passed = suites.reduce((acc, s) => acc + s.passed, 0);
        const failed = suites.reduce((acc, s) => acc + s.failed, 0);
        const avgCoverage = Math.round(suites.reduce((acc, s) => acc + s.coverage, 0) / suites.length);

        return { total, passed, failed, avgCoverage, suites: suites.length };
    }, [suites]);

    const hasContent = suites.length > 0;

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <TextField
                        size="small"
                        placeholder="Search test suites..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full"
                    />
                </Box>

                <Box className="flex-1 overflow-y-auto p-4">
                    {filteredSuites.map(suite => (
                        <Paper key={suite.id} elevation={2} className="p-4 mb-3">
                            <Box className="flex justify-between mb-2" style={{ alignItems: 'start' }} >
                                <Box>
                                    <Typography variant="subtitle2" className="font-semibold">
                                        {suite.name}
                                    </Typography>
                                    <Chip label={suite.feature} size="small" variant="outlined" className="mt-1" />
                                </Box>
                                <Chip
                                    label={`${suite.coverage}%`}
                                    size="small"
                                    className="font-semibold text-white" style={{ backgroundColor: getCoverageColor(suite.coverage) }} />
                            </Box>

                            <Box className="flex gap-4 mb-2">
                                <Typography variant="caption" color="text.secondary">
                                    ✓ {suite.passed} passed
                                </Typography>
                                {suite.failed > 0 && (
                                    <Typography variant="caption" color="error.main">
                                        ✗ {suite.failed} failed
                                    </Typography>
                                )}
                                <Typography variant="caption" color="text.secondary">
                                    {suite.duration}s
                                </Typography>
                            </Box>

                            <Box className="w-full rounded overflow-hidden h-[8px] bg-[#E5E7EB]">
                                <Box
                                    style={{ width: `${suite.coverage }}
                                />
                            </Box>
                        </Paper>
                    ))}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        E2E Coverage
                    </Typography>
                    <Typography variant="caption" display="block">
                        Suites: {stats.suites}
                    </Typography>
                    <Typography variant="caption" display="block">
                        Tests: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" className="text-[#10B981]">
                        Passed: {stats.passed}
                    </Typography>
                    <Typography variant="caption" display="block" className="text-[#EF4444]">
                        Failed: {stats.failed}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getCoverageColor(stats.avgCoverage) }}>
                        Avg Coverage: {stats.avgCoverage}%
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default E2ECoverageCanvas;
