/**
 * Test Editor Canvas Content
 * 
 * Test case editor for Test × Code level.
 * Write and execute tests inline.
 * 
 * @doc.type component
 * @doc.purpose Inline test case editor
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import {
  Box,
  Typography,
  Chip,
  Button,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';

interface TestCase {
    id: string;
    name: string;
    file: string;
    status: 'passing' | 'failing' | 'pending';
    code: string;
    error?: string;
    duration: number;
}

const MOCK_TESTS: TestCase[] = [
    {
        id: '1',
        name: 'should render button with correct text',
        file: 'Button.test.tsx',
        status: 'passing',
        duration: 12,
        code: `test('should render button with correct text', () => {
  render(<Button>Click me</Button>);
  expect(screen.getByText('Click me')).toBeInTheDocument();
});`,
    },
    {
        id: '2',
        name: 'should call onClick when clicked',
        file: 'Button.test.tsx',
        status: 'passing',
        duration: 8,
        code: `test('should call onClick when clicked', () => {
  const handleClick = jest.fn();
  render(<Button onClick={handleClick}>Click</Button>);
  fireEvent.click(screen.getByText('Click'));
  expect(handleClick).toHaveBeenCalledTimes(1);
});`,
    },
    {
        id: '3',
        name: 'should handle async validation',
        file: 'Form.test.tsx',
        status: 'failing',
        duration: 45,
        error: 'Expected "Email is required" to be in the document',
        code: `test('should handle async validation', async () => {
  render(<Form />);
  fireEvent.submit(screen.getByRole('button'));
  await waitFor(() => {
    expect(screen.getByText('Email is required')).toBeInTheDocument();
  });
});`,
    },
    {
        id: '4',
        name: 'should display modal on trigger',
        file: 'Modal.test.tsx',
        status: 'passing',
        duration: 18,
        code: `test('should display modal on trigger', () => {
  render(<Modal trigger={<button>Open</button>}>Content</Modal>);
  fireEvent.click(screen.getByText('Open'));
  expect(screen.getByText('Content')).toBeVisible();
});`,
    },
];

const getStatusColor = (status: TestCase['status']) => {
    switch (status) {
        case 'passing':
            return '#10B981';
        case 'failing':
            return '#EF4444';
        case 'pending':
            return '#F59E0B';
    }
};

export const TestEditorCanvas = () => {
    const [tests] = useState<TestCase[]>(MOCK_TESTS);
    const [selectedTest, setSelectedTest] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');

    const filteredTests = useMemo(() => {
        return tests.filter(
            t =>
                searchQuery === '' ||
                t.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                t.file.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [tests, searchQuery]);

    const stats = useMemo(() => {
        return {
            total: tests.length,
            passing: tests.filter(t => t.status === 'passing').length,
            failing: tests.filter(t => t.status === 'failing').length,
            pending: tests.filter(t => t.status === 'pending').length,
        };
    }, [tests]);

    const hasContent = tests.length > 0;

    const selectedTestData = tests.find(t => t.id === selectedTest);

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa]">
                <Box className="p-4 border-b border-solid border-b-[rgba(0,_0,_0,_0.12)] bg-white">
                    <Box className="flex gap-4 mb-2">
                        <TextField
                            size="small"
                            placeholder="Search tests..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            Run All
                        </Button>
                    </Box>
                </Box>

                <Box className="flex-1 flex gap-4 overflow-hidden p-4">
                    <Box className="overflow-y-auto transition-all duration-300" style={{ width: selectedTestData ? '35%' : '100%' }}>
                        {filteredTests.map(test => (
                            <Paper
                                key={test.id}
                                elevation={selectedTest === test.id ? 4 : 2}
                                onClick={() => setSelectedTest(test.id === selectedTest ? null : test.id)}
                                className="p-3 mb-2 cursor-pointer" style={{ border: selectedTest === test.id ? `3px solid ${getStatusColor(test.status)}` : '2px solid transparent' }}
                            >
                                <Box className="flex gap-2 mb-1">
                                    <Box
                                        className="rounded-full shrink-0 w-[16px] h-[16px]" style={{ backgroundColor: getStatusColor(test.status) }}
                                    />
                                    <Box className="flex-1">
                                        <Typography variant="body2" className="text-[0.85rem] mb-[2.4px]">
                                            {test.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="text-[0.7rem] font-mono">
                                            {test.file} • {test.duration}ms
                                        </Typography>
                                    </Box>
                                </Box>
                                {test.error && (
                                    <Typography variant="caption" color="error.main" className="text-[0.7rem]">
                                        {test.error}
                                    </Typography>
                                )}
                            </Paper>
                        ))}
                    </Box>

                    {selectedTestData && (
                        <Box className="flex flex-col w-[65%]">
                            <Paper elevation={3} className="p-4 mb-2">
                                <Box className="flex justify-between mb-2" >
                                    <Box>
                                        <Typography variant="subtitle2" className="font-semibold mb-1">
                                            {selectedTestData.name}
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary" className="font-mono">
                                            {selectedTestData.file}
                                        </Typography>
                                    </Box>
                                    <Chip
                                        label={selectedTestData.status}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getStatusColor(selectedTestData.status) }}
                                    />
                                </Box>
                                <Box className="flex gap-2">
                                    <Button variant="outlined" size="small">
                                        Run Test
                                    </Button>
                                    <Button variant="outlined" size="small">
                                        Debug
                                    </Button>
                                </Box>
                            </Paper>

                            <Paper elevation={3} className="flex-1 flex flex-col p-4">
                                <Typography variant="caption" className="font-semibold mb-2">
                                    Test Code
                                </Typography>
                                <Paper
                                    className="flex-1 text-[0.8rem] overflow-y-auto whitespace-pre-wrap p-4 bg-[#1E1E1E] text-[#D4D4D4] font-mono"
                                >
                                    {selectedTestData.code}
                                </Paper>
                                {selectedTestData.error && (
                                    <Paper className="p-3 bg-[#FEE2E2] mt-2">
                                        <Typography variant="caption" color="error.main" className="font-semibold block">
                                            Error:
                                        </Typography>
                                        <Typography variant="caption" color="error.main">
                                            {selectedTestData.error}
                                        </Typography>
                                    </Paper>
                                )}
                            </Paper>
                        </Box>
                    )}
                </Box>

                <Box
                    className="absolute rounded top-[80px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        Test Results
                    </Typography>
                    <Typography variant="caption" display="block">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('passing') }}>
                        Passing: {stats.passing}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('failing') }}>
                        Failing: {stats.failing}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('pending') }}>
                        Pending: {stats.pending}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default TestEditorCanvas;
