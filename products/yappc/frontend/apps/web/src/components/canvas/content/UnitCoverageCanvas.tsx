/**
 * Unit Coverage Canvas Content
 * 
 * Unit test coverage for Test × Component level.
 * Component-level test coverage metrics.
 * 
 * @doc.type component
 * @doc.purpose Unit test coverage visualization
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

interface ComponentCoverage {
    id: string;
    name: string;
    path: string;
    statements: number;
    branches: number;
    functions: number;
    lines: number;
    tests: number;
}

const MOCK_COVERAGE: ComponentCoverage[] = [
    {
        id: '1',
        name: 'Button.tsx',
        path: 'components/atoms',
        statements: 100,
        branches: 95,
        functions: 100,
        lines: 100,
        tests: 12,
    },
    {
        id: '2',
        name: 'Modal.tsx',
        path: 'components/molecules',
        statements: 92,
        branches: 88,
        functions: 95,
        lines: 93,
        tests: 18,
    },
    {
        id: '3',
        name: 'DataGrid.tsx',
        path: 'components/organisms',
        statements: 78,
        branches: 65,
        functions: 82,
        lines: 76,
        tests: 24,
    },
    {
        id: '4',
        name: 'useAuth.ts',
        path: 'hooks',
        statements: 95,
        branches: 90,
        functions: 100,
        lines: 96,
        tests: 8,
    },
    {
        id: '5',
        name: 'formatDate.ts',
        path: 'utils',
        statements: 100,
        branches: 100,
        functions: 100,
        lines: 100,
        tests: 6,
    },
    {
        id: '6',
        name: 'Cart.tsx',
        path: 'features/cart',
        statements: 85,
        branches: 78,
        functions: 88,
        lines: 84,
        tests: 16,
    },
];

const getCoverageColor = (coverage: number) => {
    if (coverage >= 90) return '#10B981';
    if (coverage >= 75) return '#F59E0B';
    return '#EF4444';
};

export const UnitCoverageCanvas = () => {
    const [coverage] = useState<ComponentCoverage[]>(MOCK_COVERAGE);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredCoverage = useMemo(() => {
        return coverage.filter(
            c =>
                searchQuery === '' ||
                c.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                c.path.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [coverage, searchQuery]);

    const stats = useMemo(() => {
        const avgStatements = Math.round(coverage.reduce((acc, c) => acc + c.statements, 0) / coverage.length);
        const avgBranches = Math.round(coverage.reduce((acc, c) => acc + c.branches, 0) / coverage.length);
        const avgFunctions = Math.round(coverage.reduce((acc, c) => acc + c.functions, 0) / coverage.length);
        const avgLines = Math.round(coverage.reduce((acc, c) => acc + c.lines, 0) / coverage.length);
        const totalTests = coverage.reduce((acc, c) => acc + c.tests, 0);

        return { avgStatements, avgBranches, avgFunctions, avgLines, totalTests, components: coverage.length };
    }, [coverage]);

    const hasContent = coverage.length > 0;

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <TextField
                        size="small"
                        placeholder="Search components..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full"
                    />
                </Box>

                <Box className="flex-1 overflow-y-auto p-4">
                    {filteredCoverage.map(c => (
                        <Paper key={c.id} elevation={2} className="p-4 mb-3">
                            <Box className="flex justify-between mb-2" style={{ alignItems: 'start' }} >
                                <Box>
                                    <Typography variant="subtitle2" className="font-semibold font-mono">
                                        {c.name}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                        {c.path}
                                    </Typography>
                                </Box>
                                <Chip label={`${c.tests} tests`} size="small" variant="outlined" />
                            </Box>

                            <Box className="grid gap-2" >
                                <Box>
                                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                        Statements
                                    </Typography>
                                    <Typography
                                        variant="body2"
                                        className="font-semibold text-[0.9rem]" style={{ color: getCoverageColor(c.statements), gridTemplateColumns: 'repeat(4 }} >
                                        {c.statements}%
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                        Branches
                                    </Typography>
                                    <Typography
                                        variant="body2"
                                        className="font-semibold text-[0.9rem]" style={{ color: getCoverageColor(c.branches) }} >
                                        {c.branches}%
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                        Functions
                                    </Typography>
                                    <Typography
                                        variant="body2"
                                        className="font-semibold text-[0.9rem]" style={{ color: getCoverageColor(c.functions) }} >
                                        {c.functions}%
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography variant="caption" color="text.secondary" className="text-[0.7rem]">
                                        Lines
                                    </Typography>
                                    <Typography
                                        variant="body2"
                                        className="font-semibold text-[0.9rem]" style={{ color: getCoverageColor(c.lines) }} >
                                        {c.lines}%
                                    </Typography>
                                </Box>
                            </Box>
                        </Paper>
                    ))}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Unit Coverage
                    </Typography>
                    <Typography variant="caption" display="block">
                        Components: {stats.components}
                    </Typography>
                    <Typography variant="caption" display="block">
                        Tests: {stats.totalTests}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getCoverageColor(stats.avgStatements) }}>
                        Statements: {stats.avgStatements}%
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getCoverageColor(stats.avgBranches) }}>
                        Branches: {stats.avgBranches}%
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getCoverageColor(stats.avgFunctions) }}>
                        Functions: {stats.avgFunctions}%
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getCoverageColor(stats.avgLines) }}>
                        Lines: {stats.avgLines}%
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default UnitCoverageCanvas;
