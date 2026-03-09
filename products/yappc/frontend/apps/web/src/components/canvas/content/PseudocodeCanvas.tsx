/**
 * Pseudocode Canvas Content
 * 
 * Pseudocode editor for Brainstorm × File level.
 * Algorithm design with structured pseudocode.
 * 
 * @doc.type component
 * @doc.purpose Pseudocode editor for algorithm design
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

interface PseudocodeBlock {
    id: string;
    title: string;
    complexity: 'O(1)' | 'O(log n)' | 'O(n)' | 'O(n log n)' | 'O(n²)' | 'O(2ⁿ)';
    content: string;
    category: 'algorithm' | 'data-structure' | 'function' | 'procedure';
    language?: string;
    tags?: string[];
}

// Mock pseudocode data
const MOCK_PSEUDOCODE: PseudocodeBlock[] = [
    {
        id: '1',
        title: 'Binary Search',
        complexity: 'O(log n)',
        category: 'algorithm',
        content: `FUNCTION binarySearch(array, target):
    left ← 0
    right ← length(array) - 1
    
    WHILE left ≤ right DO:
        mid ← floor((left + right) / 2)
        
        IF array[mid] = target THEN:
            RETURN mid
        ELSE IF array[mid] < target THEN:
            left ← mid + 1
        ELSE:
            right ← mid - 1
    END WHILE
    
    RETURN -1
END FUNCTION`,
        tags: ['search', 'divide-conquer'],
    },
    {
        id: '2',
        title: 'QuickSort',
        complexity: 'O(n log n)',
        category: 'algorithm',
        content: `FUNCTION quickSort(array, low, high):
    IF low < high THEN:
        pivotIndex ← partition(array, low, high)
        quickSort(array, low, pivotIndex - 1)
        quickSort(array, pivotIndex + 1, high)
    END IF
END FUNCTION

FUNCTION partition(array, low, high):
    pivot ← array[high]
    i ← low - 1
    
    FOR j ← low TO high - 1 DO:
        IF array[j] < pivot THEN:
            i ← i + 1
            SWAP array[i] WITH array[j]
        END IF
    END FOR
    
    SWAP array[i + 1] WITH array[high]
    RETURN i + 1
END FUNCTION`,
        tags: ['sorting', 'divide-conquer', 'in-place'],
    },
    {
        id: '3',
        title: 'Linked List Node',
        complexity: 'O(1)',
        category: 'data-structure',
        content: `CLASS Node:
    PROPERTIES:
        data: unknown
        next: Node | null
    
    CONSTRUCTOR(data):
        this.data ← data
        this.next ← null
    END CONSTRUCTOR
END CLASS

CLASS LinkedList:
    PROPERTIES:
        head: Node | null
        size: integer
    
    CONSTRUCTOR():
        this.head ← null
        this.size ← 0
    END CONSTRUCTOR
    
    METHOD append(data):
        newNode ← new Node(data)
        
        IF head IS null THEN:
            head ← newNode
        ELSE:
            current ← head
            WHILE current.next IS NOT null DO:
                current ← current.next
            END WHILE
            current.next ← newNode
        END IF
        
        size ← size + 1
    END METHOD
END CLASS`,
        tags: ['linked-list', 'dynamic'],
    },
    {
        id: '4',
        title: 'Breadth-First Search',
        complexity: 'O(n)',
        category: 'algorithm',
        content: `FUNCTION BFS(graph, startNode):
    visited ← new Set()
    queue ← new Queue()
    queue.enqueue(startNode)
    visited.add(startNode)
    
    WHILE NOT queue.isEmpty() DO:
        currentNode ← queue.dequeue()
        PROCESS(currentNode)
        
        FOR EACH neighbor IN graph.getNeighbors(currentNode) DO:
            IF neighbor NOT IN visited THEN:
                visited.add(neighbor)
                queue.enqueue(neighbor)
            END IF
        END FOR
    END WHILE
END FUNCTION`,
        tags: ['graph', 'traversal', 'queue'],
    },
    {
        id: '5',
        title: 'Memoized Fibonacci',
        complexity: 'O(n)',
        category: 'function',
        content: `GLOBAL memo ← new Map()

FUNCTION fibonacci(n):
    IF n ≤ 1 THEN:
        RETURN n
    END IF
    
    IF memo.has(n) THEN:
        RETURN memo.get(n)
    END IF
    
    result ← fibonacci(n - 1) + fibonacci(n - 2)
    memo.set(n, result)
    
    RETURN result
END FUNCTION`,
        tags: ['recursion', 'dynamic-programming', 'memoization'],
    },
    {
        id: '6',
        title: 'Hash Table Insert',
        complexity: 'O(1)',
        category: 'data-structure',
        content: `CLASS HashTable:
    PROPERTIES:
        buckets: Array[LinkedList]
        size: integer
        capacity: integer
    
    METHOD hash(key):
        RETURN hash_function(key) MOD capacity
    END METHOD
    
    METHOD insert(key, value):
        index ← hash(key)
        bucket ← buckets[index]
        
        // Check if key exists
        FOR EACH node IN bucket DO:
            IF node.key = key THEN:
                node.value ← value
                RETURN
            END IF
        END FOR
        
        // Insert new key-value pair
        bucket.append(new Node(key, value))
        size ← size + 1
        
        // Resize if load factor exceeded
        IF size / capacity > 0.75 THEN:
            resize()
        END IF
    END METHOD
END CLASS`,
        tags: ['hash-table', 'collision-handling'],
    },
];

const getComplexityColor = (complexity: PseudocodeBlock['complexity']) => {
    switch (complexity) {
        case 'O(1)':
            return '#4CAF50';
        case 'O(log n)':
            return '#8BC34A';
        case 'O(n)':
            return '#FFC107';
        case 'O(n log n)':
            return '#FF9800';
        case 'O(n²)':
            return '#FF5722';
        case 'O(2ⁿ)':
            return '#F44336';
    }
};

const getCategoryColor = (category: PseudocodeBlock['category']) => {
    switch (category) {
        case 'algorithm':
            return '#2196F3';
        case 'data-structure':
            return '#9C27B0';
        case 'function':
            return '#00BCD4';
        case 'procedure':
            return '#607D8B';
    }
};

export const PseudocodeCanvas = () => {
    const [pseudocodeBlocks] = useState<PseudocodeBlock[]>(MOCK_PSEUDOCODE);
    const [selectedBlock, setSelectedBlock] = useState<string | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterCategory, setFilterCategory] = useState<PseudocodeBlock['category'] | 'all'>('all');

    const filteredBlocks = useMemo(() => {
        return pseudocodeBlocks.filter(block => {
            const matchesSearch =
                searchQuery === '' ||
                block.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
                block.content.toLowerCase().includes(searchQuery.toLowerCase()) ||
                block.tags?.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()));

            const matchesCategory = filterCategory === 'all' || block.category === filterCategory;

            return matchesSearch && matchesCategory;
        });
    }, [pseudocodeBlocks, searchQuery, filterCategory]);

    const stats = useMemo(() => {
        return {
            total: pseudocodeBlocks.length,
            byCategory: {
                algorithm: pseudocodeBlocks.filter(b => b.category === 'algorithm').length,
                'data-structure': pseudocodeBlocks.filter(b => b.category === 'data-structure').length,
                function: pseudocodeBlocks.filter(b => b.category === 'function').length,
                procedure: pseudocodeBlocks.filter(b => b.category === 'procedure').length,
            },
        };
    }, [pseudocodeBlocks]);

    const hasContent = pseudocodeBlocks.length > 0;

    const selectedBlockData = pseudocodeBlocks.find(b => b.id === selectedBlock);

    return (
        <BaseCanvasContent
            hasContent={hasContent}
            emptyStateOverride={{
                primaryAction: {
                    label: 'Create Pseudocode',
                    onClick: () => {
                        console.log('Create Pseudocode');
                    },
                },
                secondaryAction: {
                    label: 'Import Template',
                    onClick: () => {
                        console.log('Import Template');
                    },
                },
            }}
        >
            <Box
                className="relative h-full w-full flex flex-col bg-[#fafafa]"
            >
                {/* Top toolbar */}
                <Box
                    className="z-[10] p-4 bg-white" style={{ borderBottom: '1px solid rgba(0 }} >
                    <Box className="flex gap-4 items-center mb-2">
                        <TextField
                            size="small"
                            placeholder="Search algorithms, data structures..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="flex-1"
                        />
                        <Button variant="outlined" size="small">
                            New Algorithm
                        </Button>
                        <Button variant="outlined" size="small">
                            Export
                        </Button>
                    </Box>

                    <Box className="flex gap-2 flex-wrap">
                        <Chip
                            label="All"
                            size="small"
                            onClick={() => setFilterCategory('all')}
                            color={filterCategory === 'all' ? 'primary' : 'default'}
                        />
                        {(['algorithm', 'data-structure', 'function', 'procedure'] as const).map(category => (
                            <Chip
                                key={category}
                                label={category}
                                size="small"
                                onClick={() => setFilterCategory(category)}
                                style={{ backgroundColor: filterCategory === category ? getCategoryColor(category) : undefined, color: filterCategory === category ? 'white' : undefined, alignItems: 'start' }}
                            />
                        ))}
                    </Box>
                </Box>

                {/* Content area */}
                <Box
                    className="flex-1 flex gap-4 overflow-hidden p-4"
                >
                    {/* List of pseudocode blocks */}
                    <Box
                        className="overflow-y-auto transition-all duration-300" style={{ width: selectedBlockData ? '35%' : '100%', backgroundColor: getCategoryColor(block.category), backgroundColor: getComplexityColor(block.complexity) }}
                    >
                        {filteredBlocks.length === 0 && (
                            <Box className="flex justify-center items-center h-full">
                                <Typography color="text.secondary">No pseudocode matches your search</Typography>
                            </Box>
                        )}

                        {filteredBlocks.map(block => (
                            <Paper
                                key={block.id}
                                elevation={selectedBlock === block.id ? 4 : 2}
                                onClick={() => setSelectedBlock(block.id === selectedBlock ? null : block.id)}
                                className="p-4 mb-3 cursor-pointer" style={{ border: selectedBlock === block.id ? `3px solid ${getCategoryColor(block.category)}` : '2px solid transparent' }}
                            >
                                <Box className="flex justify-between mb-2">
                                    <Typography variant="subtitle2" className="font-semibold text-[0.95rem]">
                                        {block.title}
                                    </Typography>
                                    <Chip
                                        label={block.complexity}
                                        size="small"
                                        className="h-[18px] text-[0.65rem]"
                                    />
                                </Box>

                                <Box className="flex gap-1 mb-2">
                                    <Chip
                                        label={block.category}
                                        size="small"
                                        className="h-[18px] text-white text-[0.65rem]" />
                                    {block.tags && block.tags.slice(0, 2).map(tag => (
                                        <Chip key={tag} label={tag} size="small" variant="outlined" className="h-[18px] text-[0.65rem]" />
                                    ))}
                                </Box>

                                <Typography
                                    variant="body2"
                                    className="text-xs whitespace-pre-wrap overflow-hidden text-ellipsis font-mono text-gray-500 dark:text-gray-400 line-clamp-2 line-clamp-3" >
                                    {block.content}
                                </Typography>
                            </Paper>
                        ))}
                    </Box>

                    {/* Detailed view */}
                    {selectedBlockData && (
                        <Box
                            className="overflow-y-auto w-[65%]"
                        >
                            <Paper
                                elevation={3}
                                className="h-full p-6"
                            >
                                <Box className="flex justify-between mb-4" style={{ alignItems: 'start' }} >
                                    <Typography variant="h6" className="font-semibold">
                                        {selectedBlockData.title}
                                    </Typography>
                                    <Box className="flex gap-2">
                                        <Button variant="outlined" size="small">
                                            Edit
                                        </Button>
                                        <Button variant="outlined" size="small">
                                            Convert to Code
                                        </Button>
                                    </Box>
                                </Box>

                                <Box className="flex gap-2 mb-4">
                                    <Chip
                                        label={selectedBlockData.complexity}
                                        size="small"
                                        className="font-semibold text-white" />
                                    <Chip
                                        label={selectedBlockData.category}
                                        size="small"
                                        className="text-white" style={{ backgroundColor: getCategoryColor(selectedBlockData.category), backgroundColor: getComplexityColor(selectedBlockData.complexity) }}
                                    />
                                    {selectedBlockData.tags?.map(tag => (
                                        <Chip key={tag} label={tag} size="small" variant="outlined" />
                                    ))}
                                </Box>

                                <Paper
                                    className="text-[0.85rem] whitespace-pre-wrap overflow-x-auto p-4 bg-[#1E1E1E] text-[#D4D4D4] font-mono leading-relaxed"
                                >
                                    {selectedBlockData.content}
                                </Paper>
                            </Paper>
                        </Box>
                    )}
                </Box>

                {/* Stats panel */}
                <Box
                    className="absolute rounded bottom-[16px] right-[16px] bg-white p-4 shadow min-w-[180px]"
                >
                    <Typography variant="subtitle2" gutterBottom className="font-semibold">
                        Library Stats
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Total: {stats.total}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Algorithms: {stats.byCategory.algorithm}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Data Structures: {stats.byCategory['data-structure']}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Functions: {stats.byCategory.function}
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                        Procedures: {stats.byCategory.procedure}
                    </Typography>
                </Box>

                {/* Legend */}
                <Box
                    className="absolute rounded bottom-[16px] left-[16px] bg-white p-3 shadow min-w-[150px]"
                >
                    <Typography variant="caption" className="font-semibold block mb-1">
                        Complexity
                    </Typography>
                    {(['O(1)', 'O(log n)', 'O(n)', 'O(n log n)', 'O(n²)', 'O(2ⁿ)'] as const).map(complexity => (
                        <Box key={complexity} className="flex items-center gap-2 mb-[2.4px]">
                            <Box
                                className="w-[16px] h-[16px] rounded" style={{ backgroundColor: getComplexityColor(complexity) }}
                            />
                            <Typography variant="caption" color="text.secondary" className="text-[0.7rem] font-mono">
                                {complexity}
                            </Typography>
                        </Box>
                    ))}
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default PseudocodeCanvas;
